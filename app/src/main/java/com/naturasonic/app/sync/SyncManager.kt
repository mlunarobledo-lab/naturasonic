package com.naturasonic.app.sync

import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    private val workManager: WorkManager
) {
    fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<ProfileSyncWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(
            ProfileSyncWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
        Log.d("SyncManager", "Profile sync scheduled")
    }

    fun scheduleBackup() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(
            BackupWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
        Log.d("SyncManager", "Manual backup scheduled")
    }

    fun schedulePeriodicBackup() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_BACKUP_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
        Log.d("SyncManager", "Periodic backup scheduled (24h)")
    }

    fun cancelPeriodicBackup() {
        workManager.cancelUniqueWork(PERIODIC_BACKUP_NAME)
        Log.d("SyncManager", "Periodic backup cancelled")
    }

    companion object {
        private const val PERIODIC_BACKUP_NAME = "periodic_encrypted_backup"
    }
}
