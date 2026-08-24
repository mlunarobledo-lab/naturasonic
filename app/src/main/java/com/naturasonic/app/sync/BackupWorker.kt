package com.naturasonic.app.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.naturasonic.app.data.local.dao.AlertEventDao
import com.naturasonic.app.data.local.dao.AudioProfileDao
import com.naturasonic.app.data.local.dao.AudiogramDao
import com.naturasonic.app.data.local.dao.DosimetrySampleDao
import com.naturasonic.app.data.local.dao.TranscriptionDao
import com.naturasonic.app.data.local.dao.VoiceMetricsDao
import com.naturasonic.app.data.preferences.UserPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val audioProfileDao: AudioProfileDao,
    private val transcriptionDao: TranscriptionDao,
    private val alertEventDao: AlertEventDao,
    private val audiogramDao: AudiogramDao,
    private val voiceMetricsDao: VoiceMetricsDao,
    private val dosimetrySampleDao: DosimetrySampleDao,
    private val backupCryptoManager: BackupCryptoManager,
    private val cloudSyncApi: CloudSyncApi,
    private val userPreferences: UserPreferences
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting encrypted backup")

        val payload = BackupPayload(
            profiles = audioProfileDao.getAllForBackup(),
            transcriptions = transcriptionDao.getAllForBackup(),
            alerts = alertEventDao.getAllForBackup(),
            audiograms = audiogramDao.getAllForBackup(),
            voiceMetrics = voiceMetricsDao.getAllForBackup(),
            dosimetrySamples = dosimetrySampleDao.getAllForBackup()
        )

        val json = Json.encodeToString(payload)
        Log.d(TAG, "Payload serialized: ${json.length} chars")

        val encrypted = backupCryptoManager.encrypt(json.toByteArray(Charsets.UTF_8))
        Log.d(TAG, "Payload encrypted: ${encrypted.size} bytes")

        val uploaded = cloudSyncApi.uploadBackup(encrypted)
        if (!uploaded) {
            Log.w(TAG, "Upload failed, will retry")
            return Result.retry()
        }

        userPreferences.setLastBackupTimestamp(System.currentTimeMillis())
        Log.d(TAG, "Backup complete")
        return Result.success()
    }

    companion object {
        private const val TAG = "BackupWorker"
        const val WORK_NAME = "encrypted_backup"
    }
}
