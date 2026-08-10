package com.naturasonic.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothAudioManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _connectedDevices = MutableStateFlow<List<BluetoothDeviceInfo>>(emptyList())
    val connectedDevices: StateFlow<List<BluetoothDeviceInfo>> = _connectedDevices.asStateFlow()

    private val _compatibility = MutableStateFlow(BluetoothCompatibility.NOT_SUPPORTED)
    val compatibility: StateFlow<BluetoothCompatibility> = _compatibility.asStateFlow()

    fun checkCompatibility(): BluetoothCompatibility {
        val adapter = bluetoothAdapter ?: return BluetoothCompatibility.NOT_SUPPORTED

        val result = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                adapter.isLeAudioSupported == android.bluetooth.BluetoothStatusCodes.FEATURE_SUPPORTED ->
                BluetoothCompatibility.LE_AUDIO_SUPPORTED

            isAshaSupported() ->
                BluetoothCompatibility.ASHA_SUPPORTED

            adapter.isEnabled ->
                BluetoothCompatibility.CLASSIC_ONLY

            else ->
                BluetoothCompatibility.NOT_SUPPORTED
        }
        _compatibility.value = result
        return result
    }

    @SuppressLint("MissingPermission")
    fun refreshConnectedDevices() {
        val adapter = bluetoothAdapter ?: return
        val devices = mutableListOf<BluetoothDeviceInfo>()

        bluetoothManager?.getConnectedDevices(BluetoothProfile.HEADSET)?.forEach { device ->
            devices.add(device.toDeviceInfo(isConnected = true))
        }

        adapter.bondedDevices?.forEach { device ->
            if (devices.none { it.address == device.address }) {
                devices.add(device.toDeviceInfo(isConnected = false))
            }
        }

        _connectedDevices.value = devices
    }

    fun observeConnectionChanges(): Flow<BluetoothDeviceInfo> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent?.getParcelableExtra(
                        BluetoothDevice.EXTRA_DEVICE,
                        BluetoothDevice::class.java
                    )
                } else {
                    @Suppress("DEPRECATION")
                    intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }

                device?.let {
                    val isConnected = intent?.action == BluetoothDevice.ACTION_ACL_CONNECTED
                    trySend(it.toDeviceInfo(isConnected))
                    refreshConnectedDevices()
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        context.registerReceiver(receiver, filter)

        awaitClose { context.unregisterReceiver(receiver) }
    }

    private fun isAshaSupported(): Boolean {
        return try {
            bluetoothAdapter?.getProfileProxy(
                context,
                object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {}
                    override fun onServiceDisconnected(profile: Int) {}
                },
                BluetoothProfile.HEARING_AID
            ) ?: false
        } catch (_: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.toDeviceInfo(isConnected: Boolean): BluetoothDeviceInfo {
        val btType = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                type == BluetoothDevice.DEVICE_TYPE_LE -> BluetoothType.LE_AUDIO
            type == BluetoothDevice.DEVICE_TYPE_LE -> BluetoothType.ASHA
            type == BluetoothDevice.DEVICE_TYPE_CLASSIC -> BluetoothType.CLASSIC
            type == BluetoothDevice.DEVICE_TYPE_DUAL -> BluetoothType.LE_AUDIO
            else -> BluetoothType.UNKNOWN
        }
        return BluetoothDeviceInfo(
            name = name ?: "Dispositivo desconocido",
            address = address,
            type = btType,
            isConnected = isConnected
        )
    }
}
