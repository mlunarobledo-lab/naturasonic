package com.naturasonic.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.Configuration
import com.naturasonic.app.data.repository.AudioProfileRepository
import com.naturasonic.app.detection.AppLifecycleTracker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class NaturaSonicApp : Application(), Configuration.Provider {

    @Inject lateinit var audioProfileRepository: AudioProfileRepository
    @Inject lateinit var lifecycleTracker: AppLifecycleTracker
    @Inject lateinit var workerFactory: androidx.hilt.work.HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(lifecycleTracker)
        createNotificationChannels()
        CoroutineScope(Dispatchers.IO).launch {
            audioProfileRepository.ensureDefaultProfiles()
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val audioChannel = NotificationChannel(
            CHANNEL_AUDIO_SERVICE,
            getString(R.string.notification_channel_audio),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
        }

        val alertChannel = NotificationChannel(
            CHANNEL_ALERTS,
            getString(R.string.notification_channel_alerts),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
        }

        manager.createNotificationChannels(listOf(audioChannel, alertChannel))
    }

    companion object {
        const val CHANNEL_AUDIO_SERVICE = "audio_service"
        const val CHANNEL_ALERTS = "alerts"
    }
}
