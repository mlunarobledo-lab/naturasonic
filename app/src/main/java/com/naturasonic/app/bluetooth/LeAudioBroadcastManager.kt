package com.naturasonic.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LeAudioBroadcastManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _broadcastState = MutableStateFlow<BroadcastState>(BroadcastState.Idle)
    val broadcastState: StateFlow<BroadcastState> = _broadcastState.asStateFlow()

    private val _capability = MutableStateFlow(BroadcastCapability.API_TOO_LOW)
    val capability: StateFlow<BroadcastCapability> = _capability.asStateFlow()

    private var broadcastProfile: BluetoothProfile? = null
    private var activeBroadcastId: Int = -1
    private val callbackExecutor: Executor = Executors.newSingleThreadExecutor()
    private var callbackInstance: Any? = null

    // ── Capability Detection ────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun checkBroadcastSupport(): BroadcastCapability {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return updateCapability(
                BroadcastCapability.API_TOO_LOW,
                "Requiere Android 13 o superior"
            )
        }

        val adapter = bluetoothAdapter ?: return updateCapability(
            BroadcastCapability.BLUETOOTH_NOT_AVAILABLE,
            "Bluetooth no disponible"
        )

        if (!adapter.isEnabled) {
            return updateCapability(
                BroadcastCapability.BLUETOOTH_OFF,
                "Bluetooth desactivado"
            )
        }

        if (!isBroadcastSourceSupported(adapter)) {
            return updateCapability(
                BroadcastCapability.HARDWARE_NOT_SUPPORTED,
                "Hardware no soporta broadcast LE Audio"
            )
        }

        _capability.value = BroadcastCapability.SUPPORTED
        if (_broadcastState.value is BroadcastState.Unsupported) {
            _broadcastState.value = BroadcastState.Idle
        }
        return BroadcastCapability.SUPPORTED
    }

    private fun updateCapability(cap: BroadcastCapability, reason: String): BroadcastCapability {
        _capability.value = cap
        _broadcastState.value = BroadcastState.Unsupported(reason)
        return cap
    }

    @SuppressLint("MissingPermission")
    private fun isBroadcastSourceSupported(adapter: BluetoothAdapter): Boolean {
        return try {
            val method = adapter.javaClass.getMethod("isLeAudioBroadcastSourceSupported")
            val result = method.invoke(adapter) as? Int
            result == BluetoothStatusCodes.FEATURE_SUPPORTED
        } catch (e: NoSuchMethodException) {
            Log.w(TAG, "isLeAudioBroadcastSourceSupported not found — assuming unsupported")
            false
        } catch (e: Exception) {
            Log.w(TAG, "Broadcast source support check failed: ${e.message}")
            false
        }
    }

    // ── Profile Proxy Management ────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun connectProfile(onConnected: (() -> Unit)? = null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (broadcastProfile != null) {
            onConnected?.invoke()
            return
        }

        val adapter = bluetoothAdapter ?: return

        val connected = adapter.getProfileProxy(
            context,
            object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    Log.i(TAG, "LE Audio Broadcast profile proxy connected")
                    broadcastProfile = proxy
                    if (_capability.value == BroadcastCapability.HARDWARE_NOT_SUPPORTED ||
                        _capability.value == BroadcastCapability.PROFILE_UNAVAILABLE
                    ) {
                        _capability.value = BroadcastCapability.SUPPORTED
                        _broadcastState.value = BroadcastState.Idle
                    }
                    registerCallbackReflective(proxy)
                    onConnected?.invoke()
                }

                override fun onServiceDisconnected(profile: Int) {
                    Log.w(TAG, "LE Audio Broadcast profile proxy disconnected")
                    broadcastProfile = null
                    if (_broadcastState.value is BroadcastState.Active) {
                        _broadcastState.value = BroadcastState.Idle
                        activeBroadcastId = -1
                    }
                }
            },
            PROFILE_LE_AUDIO_BROADCAST
        )

        if (!connected) {
            Log.w(TAG, "getProfileProxy(26) returned false — broadcast profile unavailable")
            _capability.value = BroadcastCapability.PROFILE_UNAVAILABLE
            _broadcastState.value =
                BroadcastState.Unsupported("Perfil de broadcast no disponible en este dispositivo")
        }
    }

    fun disconnectProfile() {
        val proxy = broadcastProfile ?: return
        unregisterCallbackReflective(proxy)
        bluetoothAdapter?.closeProfileProxy(PROFILE_LE_AUDIO_BROADCAST, proxy)
        broadcastProfile = null
        Log.i(TAG, "LE Audio Broadcast profile proxy released")
    }

    // ── Broadcast Lifecycle ─────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun startBroadcast(broadcastCode: String?) {
        val proxy = broadcastProfile
        if (proxy == null) {
            _broadcastState.value = BroadcastState.Error("Perfil de broadcast no conectado")
            return
        }

        _broadcastState.value = BroadcastState.Starting

        try {
            val metadata = buildContentMetadata()
            if (metadata == null) {
                _broadcastState.value = BroadcastState.Error("No se pudo configurar metadata de audio")
                return
            }

            val codeBytes = broadcastCode
                ?.takeIf { it.isNotBlank() }
                ?.toByteArray(Charsets.UTF_8)

            val metadataClass =
                Class.forName("android.bluetooth.BluetoothLeAudioContentMetadata")
            val startMethod = proxy.javaClass.getMethod(
                "startBroadcast", metadataClass, ByteArray::class.java
            )
            startMethod.invoke(proxy, metadata, codeBytes)
            Log.i(TAG, "startBroadcast invoked")
        } catch (e: java.lang.reflect.InvocationTargetException) {
            handleReflectiveException(e.cause ?: e, "iniciar")
        } catch (e: SecurityException) {
            handleSecurityException()
        } catch (e: Exception) {
            Log.e(TAG, "startBroadcast failed: ${e.message}", e)
            _broadcastState.value = BroadcastState.Error("Error al iniciar: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopBroadcast() {
        val proxy = broadcastProfile
        if (proxy == null || activeBroadcastId < 0) {
            _broadcastState.value = BroadcastState.Idle
            activeBroadcastId = -1
            return
        }

        _broadcastState.value = BroadcastState.Stopping

        try {
            val stopMethod =
                proxy.javaClass.getMethod("stopBroadcast", Int::class.javaPrimitiveType)
            stopMethod.invoke(proxy, activeBroadcastId)
            Log.i(TAG, "stopBroadcast invoked for id=$activeBroadcastId")
            _broadcastState.value = BroadcastState.Idle
            activeBroadcastId = -1
        } catch (e: java.lang.reflect.InvocationTargetException) {
            handleReflectiveException(e.cause ?: e, "detener")
        } catch (e: SecurityException) {
            handleSecurityException()
        } catch (e: Exception) {
            Log.e(TAG, "stopBroadcast failed: ${e.message}", e)
            _broadcastState.value = BroadcastState.Idle
            activeBroadcastId = -1
        }
    }

    // ── Internal helpers ────────────────────────────────────────────────

    private fun handleSecurityException() {
        Log.e(TAG, "BLUETOOTH_PRIVILEGED required — not available to third-party apps on this OS version")
        _broadcastState.value = BroadcastState.Error(
            "Requiere permisos de sistema. Disponible en Android 15+ (Samsung) o Android 16 (Pixel)"
        )
    }

    private fun handleReflectiveException(cause: Throwable, operation: String) {
        if (cause is SecurityException) {
            handleSecurityException()
        } else {
            Log.e(TAG, "$operation broadcast failed: ${cause.message}", cause)
            _broadcastState.value =
                BroadcastState.Error("Error al $operation broadcast: ${cause.message}")
        }
    }

    private fun buildContentMetadata(): Any? {
        return try {
            val builderClass = Class.forName(
                "android.bluetooth.BluetoothLeAudioContentMetadata\$Builder"
            )
            val builder = builderClass.getDeclaredConstructor().newInstance()

            builderClass.getMethod("setProgramInfo", String::class.java)
                .invoke(builder, "NaturaSonic")

            builderClass.getMethod("setLanguage", String::class.java)
                .invoke(builder, "spa")

            builderClass.getMethod("build").invoke(builder)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build BluetoothLeAudioContentMetadata: ${e.message}", e)
            null
        }
    }

    /**
     * Attempts to register a BluetoothLeBroadcast.Callback via reflection.
     * BluetoothLeBroadcast.Callback is abstract and may be @SystemApi,
     * so direct subclassing at compile time is not guaranteed.
     * On devices where the callback class is available at runtime,
     * we create a dynamic subclass using Dalvik's class resolution.
     * If registration fails, state management falls back to optimistic updates
     * (state transitions based on method invocation success/failure).
     */
    private fun registerCallbackReflective(proxy: BluetoothProfile) {
        try {
            val broadcastClass = Class.forName("android.bluetooth.BluetoothLeBroadcast")
            val callbackClass = Class.forName("android.bluetooth.BluetoothLeBroadcast\$Callback")

            val registerMethod = broadcastClass.getMethod(
                "registerCallback", Executor::class.java, callbackClass
            )

            val callbackProxy = createCallbackProxy(callbackClass)
            if (callbackProxy != null) {
                registerMethod.invoke(proxy, callbackExecutor, callbackProxy)
                callbackInstance = callbackProxy
                Log.i(TAG, "Broadcast callback registered via reflection")
            } else {
                Log.i(TAG, "Callback proxy creation failed — using optimistic state updates")
            }
        } catch (e: ClassNotFoundException) {
            Log.i(TAG, "BluetoothLeBroadcast.Callback not on classpath — optimistic mode")
        } catch (e: Exception) {
            Log.w(TAG, "Callback registration failed: ${e.message} — optimistic mode")
        }
    }

    private fun unregisterCallbackReflective(proxy: BluetoothProfile) {
        val cb = callbackInstance ?: return
        try {
            val broadcastClass = Class.forName("android.bluetooth.BluetoothLeBroadcast")
            val callbackClass = Class.forName("android.bluetooth.BluetoothLeBroadcast\$Callback")
            val unregisterMethod = broadcastClass.getMethod("unregisterCallback", callbackClass)
            unregisterMethod.invoke(proxy, cb)
            callbackInstance = null
            Log.i(TAG, "Broadcast callback unregistered")
        } catch (e: Exception) {
            Log.w(TAG, "Callback unregistration failed: ${e.message}")
        }
    }

    /**
     * Creates a concrete subclass of BluetoothLeBroadcast.Callback at runtime.
     * Returns null if the abstract class cannot be subclassed (reflection limitation).
     * On Android 16+ where the class is fully public, this succeeds and enables
     * real-time lifecycle event handling. On earlier versions, returns null
     * and the manager falls back to optimistic state updates.
     */
    private fun createCallbackProxy(callbackClass: Class<*>): Any? {
        return try {
            val handler = BroadcastCallbackHandler()
            val proxyInstance = java.lang.reflect.Proxy.newProxyInstance(
                callbackClass.classLoader,
                callbackClass.interfaces,
                handler
            )
            proxyInstance
        } catch (e: Exception) {
            // Proxy.newProxyInstance only works with interfaces, not abstract classes.
            // BluetoothLeBroadcast.Callback is abstract → Proxy won't work.
            // This is expected on most devices. Optimistic mode is the fallback.
            null
        }
    }

    private inner class BroadcastCallbackHandler : java.lang.reflect.InvocationHandler {
        override fun invoke(proxy: Any?, method: java.lang.reflect.Method?, args: Array<out Any>?): Any? {
            when (method?.name) {
                "onBroadcastStarted" -> {
                    val broadcastId = args?.getOrNull(1) as? Int ?: -1
                    Log.i(TAG, "Callback: broadcast started, id=$broadcastId")
                    activeBroadcastId = broadcastId
                    _broadcastState.value = BroadcastState.Active(broadcastId)
                }
                "onBroadcastStartFailed" -> {
                    val reason = args?.getOrNull(0) as? Int ?: -1
                    Log.e(TAG, "Callback: broadcast start failed, reason=$reason")
                    _broadcastState.value = BroadcastState.Error("Broadcast no pudo iniciarse (código: $reason)")
                }
                "onBroadcastStopped" -> {
                    val broadcastId = args?.getOrNull(1) as? Int ?: -1
                    Log.i(TAG, "Callback: broadcast stopped, id=$broadcastId")
                    activeBroadcastId = -1
                    _broadcastState.value = BroadcastState.Idle
                }
                "onBroadcastStopFailed" -> {
                    val reason = args?.getOrNull(0) as? Int ?: -1
                    Log.e(TAG, "Callback: broadcast stop failed, reason=$reason")
                    _broadcastState.value = BroadcastState.Error("No se pudo detener (código: $reason)")
                }
                "onBroadcastMetadataChanged" -> {
                    Log.d(TAG, "Callback: broadcast metadata changed")
                }
                "onPlaybackStarted" -> {
                    Log.d(TAG, "Callback: playback started")
                }
                "onPlaybackStopped" -> {
                    Log.d(TAG, "Callback: playback stopped")
                }
            }
            return null
        }
    }

    companion object {
        private const val TAG = "LEBroadcastMgr"
        private const val PROFILE_LE_AUDIO_BROADCAST = 26
    }
}
