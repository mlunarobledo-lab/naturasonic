package com.naturasonic.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.naturasonic.app.MainActivity
import com.naturasonic.app.NaturaSonicApp
import com.naturasonic.app.R
import com.naturasonic.app.audio.AudioModeManager
import com.naturasonic.app.audio.AudioSessionManager
import com.naturasonic.app.audio.AttentionController
import com.naturasonic.app.audio.DosimetryManager
import com.naturasonic.app.audio.HeadTrackingManager
import com.naturasonic.app.audio.HeadTrackingState
import com.naturasonic.app.audio.DspWatchdogManager
import com.naturasonic.app.audio.OboeAudioEngine
import com.naturasonic.app.audio.VolumeProtection
import com.naturasonic.app.battery.BatteryMonitor
import com.naturasonic.app.battery.EcoModeManager
import com.naturasonic.app.bluetooth.BluetoothAudioManager
import com.naturasonic.app.bluetooth.BluetoothConnectionState
import com.naturasonic.app.bluetooth.BroadcastState
import com.naturasonic.app.bluetooth.LeAudioBroadcastManager
import com.naturasonic.app.data.local.entity.AudioMode
import com.naturasonic.app.data.preferences.UserPreferences
import com.naturasonic.app.data.repository.AudioProfileRepository
import com.naturasonic.app.detection.SoundAlertDetector
import com.naturasonic.app.performance.PerformanceTracker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AudioService : Service() {

    @Inject lateinit var audioEngine: OboeAudioEngine
    @Inject lateinit var audioSessionManager: AudioSessionManager
    @Inject lateinit var volumeProtection: VolumeProtection
    @Inject lateinit var alertDetector: SoundAlertDetector
    @Inject lateinit var audioProfileRepository: AudioProfileRepository
    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var modeManager: AudioModeManager
    @Inject lateinit var performanceTracker: PerformanceTracker
    @Inject lateinit var bluetoothAudioManager: BluetoothAudioManager
    @Inject lateinit var batteryMonitor: BatteryMonitor
    @Inject lateinit var ecoModeManager: EcoModeManager
    @Inject lateinit var leAudioBroadcastManager: LeAudioBroadcastManager
    @Inject lateinit var headTrackingManager: HeadTrackingManager
    @Inject lateinit var dosimetryManager: DosimetryManager
    @Inject lateinit var attentionController: AttentionController
    @Inject lateinit var dspWatchdogManager: DspWatchdogManager

    private val binder = AudioBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var volumeTickJob: Job? = null
    private var detectionJob: Job? = null
    private var profileObserverJob: Job? = null
    private var btMonitorJob: Job? = null
    private var headTrackingJob: Job? = null
    private var aecObserverJob: Job? = null
    private var dosimetryObserverJob: Job? = null
    private var attentionObserverJob: Job? = null
    private var transientLimiterObserverJob: Job? = null
    private var ancPhaseObserverJob: Job? = null
    private var watchdogObserverJob: Job? = null
    private var watchdogRestartJob: Job? = null

    inner class AudioBinder : Binder() {
        fun getService(): AudioService = this@AudioService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startAudio()
            ACTION_STOP -> stopAudio()
        }
        return START_STICKY
    }

    private fun startAudio() {
        startForeground()
        audioEngine.create()
        val started = audioEngine.start()
        if (started) {
            batteryMonitor.startMonitoring()
            ecoModeManager.startObserving()
            volumeProtection.onListeningStarted()
            startVolumeProtectionTick()
            startDetectionLoop()
            startProfileObserver()
            startBluetoothMonitor()
            leAudioBroadcastManager.checkBroadcastSupport()
            leAudioBroadcastManager.connectProfile()
            startHeadTrackingObserver()
            startAecObserver()
            startDosimetryObserver()
            startAttentionObserver()
            startTransientLimiterObserver()
            startAncPhaseObserver()
            startWatchdogObserver()
            audioEngine.startVoiceAnalyzer()
        }
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun startProfileObserver() {
        profileObserverJob?.cancel()
        profileObserverJob = serviceScope.launch {
            combine(
                userPreferences.selectedProfileId,
                userPreferences.currentMode
            ) { profileId, modeKey -> Pair(profileId, modeKey) }
                .debounce(30)
                .collect { (profileId, modeKey) ->
                    val mode = AudioMode.fromKey(modeKey)

                    val profile = if (profileId > 0) {
                        audioProfileRepository.getById(profileId)
                    } else {
                        null
                    } ?: audioProfileRepository.getDefaultProfile(mode)

                    if (profile != null) {
                        modeManager.applyProfile(profile, 0)
                    }

                    val config = modeManager.getModeConfig(mode)
                    if (config.alertDetectionEnabled) {
                        alertDetector.start()
                    } else {
                        alertDetector.stop()
                    }
                }
        }
    }

    private fun startHeadTrackingObserver() {
        headTrackingJob?.cancel()
        headTrackingJob = serviceScope.launch {
            combine(
                userPreferences.headTrackingEnabled,
                headTrackingManager.state,
                userPreferences.headTrackingSensitivity
            ) { enabled, state, sensitivity ->
                Triple(enabled, state, sensitivity)
            }.collect { (enabled, state, sensitivity) ->
                audioEngine.setHeadTrackingEnabled(enabled)
                if (enabled) {
                    if (!headTrackingManager.isSensorAvailable) return@collect
                    if (state is HeadTrackingState.Disabled) {
                        headTrackingManager.start()
                    }
                    if (state is HeadTrackingState.Active) {
                        audioEngine.setHeadTrackingAngles(
                            state.azimuthDeg,
                            state.pitchDeg,
                            sensitivity
                        )
                    }
                } else {
                    headTrackingManager.stop()
                }
            }
        }
    }

    private fun startDosimetryObserver() {
        dosimetryObserverJob?.cancel()
        dosimetryObserverJob = serviceScope.launch {
            combine(
                userPreferences.dosimetryEnabled,
                userPreferences.calibrationOffset
            ) { enabled, offset -> Pair(enabled, offset) }
                .collect { (enabled, offset) ->
                    audioEngine.setCalibrationOffset(offset)
                    if (enabled && !dosimetryManager.isActive) {
                        dosimetryManager.startSession(serviceScope)
                    } else if (!enabled && dosimetryManager.isActive) {
                        dosimetryManager.stopSession()
                    }
                }
        }
    }

    private fun startAttentionObserver() {
        attentionObserverJob?.cancel()
        attentionObserverJob = serviceScope.launch {
            combine(
                userPreferences.attentionAgcEnabled,
                userPreferences.speechBoostDb,
                userPreferences.alertAttenuationDb
            ) { enabled, boost, attenuation ->
                Triple(enabled, boost, attenuation)
            }.collect { (enabled, boost, attenuation) ->
                if (enabled && !attentionController.isActive) {
                    attentionController.start(serviceScope, boost, attenuation)
                } else if (enabled && attentionController.isActive) {
                    attentionController.updateParams(boost, attenuation)
                } else if (!enabled && attentionController.isActive) {
                    attentionController.stop()
                }
            }
        }
    }

    private fun startTransientLimiterObserver() {
        transientLimiterObserverJob?.cancel()
        transientLimiterObserverJob = serviceScope.launch {
            combine(
                userPreferences.transientLimiterEnabled,
                userPreferences.transientLimiterThreshold
            ) { enabled, threshold -> Pair(enabled, threshold) }
                .collect { (enabled, threshold) ->
                    audioEngine.setTransientLimiterEnabled(enabled)
                    audioEngine.setTransientLimiterThreshold(threshold)
                }
        }
    }

    private fun startAecObserver() {
        aecObserverJob?.cancel()
        aecObserverJob = serviceScope.launch {
            userPreferences.aecMode.collect { mode ->
                audioEngine.setAecMode(mode)
                val sessionId = audioEngine.getAudioSessionId()
                if (mode == 2 && sessionId != 0) {
                    audioSessionManager.setAecEnabled(sessionId, true)
                } else {
                    audioSessionManager.setAecEnabled(0, false)
                }
            }
        }
    }

    private fun startAncPhaseObserver() {
        ancPhaseObserverJob?.cancel()
        ancPhaseObserverJob = serviceScope.launch {
            combine(
                userPreferences.ancPhaseEnabled,
                userPreferences.ancCancellationGain,
                userPreferences.ancLpEnabled,
                userPreferences.ancHpEnabled,
                userPreferences.ancLpCutoff,
                userPreferences.ancHpCutoff
            ) { values ->
                AncPhaseParams(
                    enabled = values[0] as Boolean,
                    gain = values[1] as Float,
                    lpEnabled = values[2] as Boolean,
                    hpEnabled = values[3] as Boolean,
                    lpCutoff = values[4] as Float,
                    hpCutoff = values[5] as Float
                )
            }.collect { params ->
                audioEngine.setAncPhaseEnabled(params.enabled)
                audioEngine.setAncCancellationGain(params.gain)
                audioEngine.setAncLpEnabled(params.lpEnabled)
                audioEngine.setAncHpEnabled(params.hpEnabled)
                audioEngine.setAncLpCutoff(params.lpCutoff)
                audioEngine.setAncHpCutoff(params.hpCutoff)
            }
        }
    }

    private data class AncPhaseParams(
        val enabled: Boolean,
        val gain: Float,
        val lpEnabled: Boolean,
        val hpEnabled: Boolean,
        val lpCutoff: Float,
        val hpCutoff: Float
    )

    private fun startWatchdogObserver() {
        watchdogObserverJob?.cancel()
        watchdogObserverJob = serviceScope.launch {
            userPreferences.dspWatchdogEnabled.collect { enabled ->
                if (enabled) {
                    dspWatchdogManager.startMonitoring(serviceScope)
                } else {
                    dspWatchdogManager.stopMonitoring()
                }
            }
        }
        watchdogRestartJob?.cancel()
        watchdogRestartJob = serviceScope.launch {
            dspWatchdogManager.restartEvent.collect {
                reapplyAllPreferences()
            }
        }
    }

    private suspend fun reapplyAllPreferences() {
        val profileId = userPreferences.selectedProfileId.first()
        val modeKey = userPreferences.currentMode.first()
        val mode = AudioMode.fromKey(modeKey)
        val profile = if (profileId > 0) {
            audioProfileRepository.getById(profileId)
        } else null ?: audioProfileRepository.getDefaultProfile(mode)
        if (profile != null) {
            modeManager.applyProfile(profile, 0)
        }
        audioEngine.setHeadTrackingEnabled(userPreferences.headTrackingEnabled.first())
        audioEngine.setAecMode(userPreferences.aecMode.first())
        audioEngine.setTransientLimiterEnabled(userPreferences.transientLimiterEnabled.first())
        audioEngine.setTransientLimiterThreshold(userPreferences.transientLimiterThreshold.first())
        audioEngine.setAncPhaseEnabled(userPreferences.ancPhaseEnabled.first())
        audioEngine.setAncCancellationGain(userPreferences.ancCancellationGain.first())
        audioEngine.setAncLpEnabled(userPreferences.ancLpEnabled.first())
        audioEngine.setAncHpEnabled(userPreferences.ancHpEnabled.first())
        audioEngine.setAncLpCutoff(userPreferences.ancLpCutoff.first())
        audioEngine.setAncHpCutoff(userPreferences.ancHpCutoff.first())
    }

    private fun stopAudio() {
        audioEngine.stopVoiceAnalyzer()
        watchdogRestartJob?.cancel()
        watchdogObserverJob?.cancel()
        dspWatchdogManager.stopMonitoring()
        ancPhaseObserverJob?.cancel()
        audioEngine.setAncPhaseEnabled(false)
        transientLimiterObserverJob?.cancel()
        audioEngine.setTransientLimiterEnabled(false)
        attentionObserverJob?.cancel()
        attentionController.stop()
        dosimetryObserverJob?.cancel()
        dosimetryManager.stopSession()
        aecObserverJob?.cancel()
        audioEngine.setAecMode(0)
        headTrackingJob?.cancel()
        headTrackingManager.stop()
        audioEngine.setHeadTrackingEnabled(false)
        if (leAudioBroadcastManager.broadcastState.value is BroadcastState.Active) {
            leAudioBroadcastManager.stopBroadcast()
        }
        leAudioBroadcastManager.disconnectProfile()
        btMonitorJob?.cancel()
        bluetoothAudioManager.stopMonitoring()
        batteryMonitor.stopMonitoring()
        profileObserverJob?.cancel()
        detectionJob?.cancel()
        volumeTickJob?.cancel()
        volumeProtection.onListeningStopped()
        alertDetector.release()
        audioSessionManager.release()
        audioEngine.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startVolumeProtectionTick() {
        volumeTickJob?.cancel()
        volumeTickJob = serviceScope.launch {
            while (true) {
                val limitDb = volumeProtection.tick()
                audioEngine.setVolumeLimitDb(limitDb)
                delay(1000)
            }
        }
    }

    private fun startDetectionLoop() {
        detectionJob?.cancel()
        detectionJob = serviceScope.launch {
            alertDetector.loadModel()
            delay(1000)
            while (isActive) {
                if (alertDetector.isRunning.value) {
                    val t0 = System.nanoTime()
                    val buffer = audioEngine.getYamnetAudioBuffer()
                    val jniCopyNs = System.nanoTime() - t0
                    if (buffer != null) {
                        val timing = alertDetector.processAudio48kHz(buffer)
                        if (timing != null) {
                            performanceTracker.reportDetectionTiming(
                                jniCopyNs = jniCopyNs,
                                resampleNs = timing.resampleNs,
                                classifyNs = timing.classifyNs
                            )
                        }
                    }
                }
                performanceTracker.refreshDspStats()
                performanceTracker.refreshMemoryStats()
                delay(ecoModeManager.getDetectionIntervalMs())
            }
        }
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun startBluetoothMonitor() {
        bluetoothAudioManager.startMonitoring()
        btMonitorJob?.cancel()
        btMonitorJob = serviceScope.launch {
            bluetoothAudioManager.connectionState
                .debounce(200)
                .collect { state ->
                    when (state) {
                        is BluetoothConnectionState.Connected -> {
                            audioEngine.setOutputMuted(false)
                        }
                        is BluetoothConnectionState.Disconnected,
                        is BluetoothConnectionState.BluetoothOff -> {
                            audioEngine.setOutputMuted(true)
                        }
                        is BluetoothConnectionState.NoDevice -> { }
                    }
                }
        }
    }

    private fun startForeground() {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NaturaSonicApp.CHANNEL_AUDIO_SERVICE)
            .setContentTitle(getString(R.string.notification_audio_service_title))
            .setContentText(getString(R.string.notification_audio_service_text))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        audioEngine.stopVoiceAnalyzer()
        watchdogRestartJob?.cancel()
        watchdogObserverJob?.cancel()
        dspWatchdogManager.stopMonitoring()
        ancPhaseObserverJob?.cancel()
        transientLimiterObserverJob?.cancel()
        attentionObserverJob?.cancel()
        attentionController.stop()
        dosimetryObserverJob?.cancel()
        dosimetryManager.stopSession()
        headTrackingJob?.cancel()
        headTrackingManager.stop()
        if (leAudioBroadcastManager.broadcastState.value is BroadcastState.Active) {
            leAudioBroadcastManager.stopBroadcast()
        }
        leAudioBroadcastManager.disconnectProfile()
        bluetoothAudioManager.stopMonitoring()
        serviceScope.cancel()
        audioEngine.destroy()
        audioSessionManager.release()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.naturasonic.START_AUDIO"
        const val ACTION_STOP = "com.naturasonic.STOP_AUDIO"
        const val NOTIFICATION_ID = 1001
    }
}
