package com.naturasonic.app.sync

import com.naturasonic.app.data.preferences.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

data class BackupState(
    val autoBackupEnabled: Boolean = false,
    val lastBackupTimestamp: Long = 0L
)

@Singleton
class CloudBackupManager @Inject constructor(
    private val syncManager: SyncManager,
    private val userPreferences: UserPreferences
) {
    val state: Flow<BackupState> = combine(
        userPreferences.autoBackupEnabled,
        userPreferences.lastBackupTimestamp
    ) { enabled, timestamp ->
        BackupState(autoBackupEnabled = enabled, lastBackupTimestamp = timestamp)
    }

    fun backupNow() {
        syncManager.scheduleBackup()
    }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        userPreferences.setAutoBackupEnabled(enabled)
        if (enabled) {
            syncManager.schedulePeriodicBackup()
        } else {
            syncManager.cancelPeriodicBackup()
        }
    }
}
