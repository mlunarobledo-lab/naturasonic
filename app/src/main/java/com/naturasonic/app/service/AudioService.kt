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
import com.naturasonic.app.audio.AudioSessionManager
import com.naturasonic.app.audio.OboeAudioEngine
import com.naturasonic.app.audio.VolumeProtection
import com.naturasonic.app.detection.SoundAlertDetector
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AudioService : Service() {

    @Inject lateinit var audioEngine: OboeAudioEngine
    @Inject lateinit var audioSessionManager: AudioSessionManager
    @Inject lateinit var volumeProtection: VolumeProtection
    @Inject lateinit var alertDetector: SoundAlertDetector

    private val binder = AudioBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var volumeTickJob: Job? = null
    private var detectionJob: Job? = null

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
        }
    }

    private fun stopAudio() {
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
                    val buffer = audioEngine.getYamnetAudioBuffer()
                    if (buffer != null) {
                        alertDetector.processAudio48kHz(buffer)
                    }
                }
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
