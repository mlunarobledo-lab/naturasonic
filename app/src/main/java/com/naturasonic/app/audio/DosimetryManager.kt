package com.naturasonic.app.audio

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.naturasonic.app.MainActivity
import com.naturasonic.app.NaturaSonicApp
import com.naturasonic.app.R
import com.naturasonic.app.data.local.dao.DosimetrySampleDao
import com.naturasonic.app.data.local.entity.DosimetrySample
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log2
import kotlin.math.pow

data class DosimetrySnapshot(
    val instantDba: Float = 0f,
    val leq: Float = 0f,
    val peakDba: Float = 0f,
    val doseOshaPercent: Float = 0f,
    val doseNioshPercent: Float = 0f,
    val twaOsha: Float = 0f,
    val twaNiosh: Float = 0f,
    val elapsedMinutes: Float = 0f
)

@Singleton
class DosimetryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioEngine: OboeAudioEngine,
    private val dosimetrySampleDao: DosimetrySampleDao
) {
    private val _snapshot = MutableStateFlow(DosimetrySnapshot())
    val snapshot: StateFlow<DosimetrySnapshot> = _snapshot.asStateFlow()

    private val _dbaHistory = MutableStateFlow<List<Float>>(emptyList())
    val dbaHistory: StateFlow<List<Float>> = _dbaHistory.asStateFlow()

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private var pollingJob: Job? = null
    private var sessionStartTime = 0L
    private var sessionDay = 0
    private var oshaAccumulatedDose = 0.0
    private var nioshAccumulatedDose = 0.0
    private var persistCounter = 0
    private var alertedOsha50 = false
    private var alertedOsha80 = false
    private var alertedOsha100 = false
    private var alertedNiosh50 = false
    private var alertedNiosh80 = false
    private var alertedNiosh100 = false

    fun startSession(scope: CoroutineScope) {
        pollingJob?.cancel()
        audioEngine.startDosimetry()
        resetSession()

        pollingJob = scope.launch {
            while (isActive) {
                checkMidnightReset()

                val raw = audioEngine.getDosimetryData()
                if (raw != null && raw.size >= 3) {
                    val dba = raw[0]
                    val leq = raw[1]
                    val peak = raw[2]

                    accumulateDose(dba)
                    checkDoseAlerts()

                    val elapsedMs = System.currentTimeMillis() - sessionStartTime
                    val elapsedMinutes = elapsedMs / 60_000f
                    val elapsedHours = elapsedMs / 3_600_000.0

                    _snapshot.value = DosimetrySnapshot(
                        instantDba = dba,
                        leq = leq,
                        peakDba = peak,
                        doseOshaPercent = (oshaAccumulatedDose * 100).toFloat(),
                        doseNioshPercent = (nioshAccumulatedDose * 100).toFloat(),
                        twaOsha = computeTwa(oshaAccumulatedDose, elapsedHours, OSHA_PEL, OSHA_EXCHANGE),
                        twaNiosh = computeTwa(nioshAccumulatedDose, elapsedHours, NIOSH_REL, NIOSH_EXCHANGE),
                        elapsedMinutes = elapsedMinutes
                    )

                    if (dba > -100f) {
                        val current = _dbaHistory.value.toMutableList()
                        current.add(dba)
                        if (current.size > MAX_HISTORY_POINTS) current.removeAt(0)
                        _dbaHistory.value = current
                    }

                    persistCounter++
                    if (persistCounter >= PERSIST_EVERY_N) {
                        persistCounter = 0
                        dosimetrySampleDao.insert(
                            DosimetrySample(
                                instantDba = dba,
                                leq = leq,
                                doseOsha = (oshaAccumulatedDose * 100).toFloat(),
                                doseNiosh = (nioshAccumulatedDose * 100).toFloat()
                            )
                        )
                    }
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stopSession() {
        pollingJob?.cancel()
        pollingJob = null
        audioEngine.stopDosimetry()
    }

    fun setCalibrationOffset(offsetDb: Float) {
        audioEngine.setCalibrationOffset(offsetDb)
    }

    suspend fun getHistorySince(sinceMs: Long): List<DosimetrySample> {
        return dosimetrySampleDao.getSince(sinceMs)
    }

    val isActive: Boolean get() = pollingJob?.isActive == true

    private fun resetSession() {
        sessionStartTime = System.currentTimeMillis()
        sessionDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        oshaAccumulatedDose = 0.0
        nioshAccumulatedDose = 0.0
        persistCounter = 0
        alertedOsha50 = false
        alertedOsha80 = false
        alertedOsha100 = false
        alertedNiosh50 = false
        alertedNiosh80 = false
        alertedNiosh100 = false
        _dbaHistory.value = emptyList()
    }

    private fun checkMidnightReset() {
        val currentDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        if (currentDay != sessionDay) {
            resetSession()
            audioEngine.startDosimetry()
        }
    }

    private fun accumulateDose(dba: Float) {
        if (dba < DOSE_THRESHOLD_DBA) return

        val intervalHours = POLL_INTERVAL_MS / 3_600_000.0

        val oshaAllowed = 8.0 / 2.0.pow((dba - OSHA_PEL) / OSHA_EXCHANGE)
        oshaAccumulatedDose += intervalHours / oshaAllowed

        val nioshAllowed = 8.0 / 2.0.pow((dba - NIOSH_REL) / NIOSH_EXCHANGE)
        nioshAccumulatedDose += intervalHours / nioshAllowed
    }

    private fun computeTwa(dose: Double, elapsedHours: Double, criterion: Double, exchange: Double): Float {
        if (elapsedHours < 1.0 / 60.0 || dose <= 0.0) return 0f
        val ratio = dose * 8.0 / elapsedHours
        if (ratio <= 0.0) return 0f
        return (criterion + exchange * log2(ratio)).toFloat()
    }

    private fun checkDoseAlerts() {
        val oshaPercent = oshaAccumulatedDose * 100
        val nioshPercent = nioshAccumulatedDose * 100

        if (oshaPercent >= 100 && !alertedOsha100) {
            alertedOsha100 = true
            sendDoseNotification("OSHA", 100)
        } else if (oshaPercent >= 80 && !alertedOsha80) {
            alertedOsha80 = true
            sendDoseNotification("OSHA", 80)
        } else if (oshaPercent >= 50 && !alertedOsha50) {
            alertedOsha50 = true
            sendDoseNotification("OSHA", 50)
        }

        if (nioshPercent >= 100 && !alertedNiosh100) {
            alertedNiosh100 = true
            sendDoseNotification("NIOSH", 100)
        } else if (nioshPercent >= 80 && !alertedNiosh80) {
            alertedNiosh80 = true
            sendDoseNotification("NIOSH", 80)
        } else if (nioshPercent >= 50 && !alertedNiosh50) {
            alertedNiosh50 = true
            sendDoseNotification("NIOSH", 50)
        }
    }

    private fun sendDoseNotification(standard: String, percent: Int) {
        val pendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = when (percent) {
            100 -> "Limite de exposicion $standard alcanzado"
            else -> "Dosis de ruido $standard al $percent%"
        }
        val text = when (percent) {
            100 -> "Has alcanzado el 100% de la dosis permitida ($standard). Protege tu audicion."
            80 -> "Tu exposicion al ruido se acerca al limite $standard. Considera reducir la exposicion."
            else -> "Tu dosis de ruido diaria ($standard) esta al $percent%."
        }

        val notification = NotificationCompat.Builder(context, NaturaSonicApp.CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .build()

        val notifId = NOTIFICATION_ID_BASE + (if (standard == "OSHA") 0 else 3) + when (percent) {
            50 -> 0
            80 -> 1
            else -> 2
        }
        notificationManager.notify(notifId, notification)
    }

    companion object {
        private const val POLL_INTERVAL_MS = 500L
        private const val MAX_HISTORY_POINTS = 600
        private const val PERSIST_EVERY_N = 60
        private const val DOSE_THRESHOLD_DBA = 80f
        private const val NOTIFICATION_ID_BASE = 3000

        const val OSHA_PEL = 90.0
        const val OSHA_EXCHANGE = 5.0
        const val NIOSH_REL = 85.0
        const val NIOSH_EXCHANGE = 3.0
    }
}
