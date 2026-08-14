package com.naturasonic.app.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.naturasonic.app.data.local.dao.AudioProfileDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ProfileSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val audioProfileDao: AudioProfileDao,
    private val cloudSyncApi: CloudSyncApi
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val unsynced = audioProfileDao.getUnsyncedProfiles()
        if (unsynced.isEmpty()) {
            Log.d(TAG, "No pending profiles to sync")
            return Result.success()
        }

        Log.d(TAG, "Syncing ${unsynced.size} profiles")
        val uploaded = cloudSyncApi.uploadProfiles(unsynced)
        if (!uploaded) {
            Log.w(TAG, "Upload failed, will retry")
            return Result.retry()
        }

        audioProfileDao.markAsSynced(unsynced.map { it.id })
        Log.d(TAG, "Sync complete: ${unsynced.size} profiles marked synced")
        return Result.success()
    }

    companion object {
        private const val TAG = "ProfileSyncWorker"
        const val WORK_NAME = "profile_sync"
    }
}
