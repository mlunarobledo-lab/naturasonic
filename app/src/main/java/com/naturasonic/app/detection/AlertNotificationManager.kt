package com.naturasonic.app.detection

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.naturasonic.app.MainActivity
import com.naturasonic.app.NaturaSonicApp
import com.naturasonic.app.R
import com.naturasonic.app.data.local.entity.AlertSoundClass
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun showAlertNotification(alertClass: AlertSoundClass, confidence: Float) {
        val pendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val confidencePercent = (confidence * 100).toInt()

        val notification = NotificationCompat.Builder(context, NaturaSonicApp.CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.notification_alert_title))
            .setContentText("${alertClass.displayName} ($confidencePercent%)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0))
            .build()

        notificationManager.notify(NOTIFICATION_ID_BASE + alertClass.ordinal, notification)
    }

    companion object {
        private const val NOTIFICATION_ID_BASE = 2000
    }
}
