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
import com.naturasonic.app.audio.OboeAudioEngine
import com.naturasonic.app.audio.VolumeProtection
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

    private val binder = AudioBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var volumeTickJob: Job? = null
    private var detectionJob: Job? = null
    private var profileObserverJob: Job? = null

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
            volumeProtection.onListeningStarted()
            startVolumeProtectionTick()
            startDetectionLoop()
            startProfileObserver()
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

    private fun stopAudio() {
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
                delay(DETECTION_INTERVAL_MS)
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
        serviceScope.cancel()
        audioEngine.destroy()
        audioSessionManager.release()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.naturasonic.START_AUDIO"
        const val ACTION_STOP = "com.naturasonic.STOP_AUDIO"
        const val NOTIFICATION_ID = 1001
        private const val DETECTION_INTERVAL_MS = 1000L
    }
}
