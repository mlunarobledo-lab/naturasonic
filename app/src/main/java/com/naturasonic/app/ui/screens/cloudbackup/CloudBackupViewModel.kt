package com.naturasonic.app.ui.screens.cloudbackup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.naturasonic.app.sync.BackupState
import com.naturasonic.app.sync.BackupWorker
import com.naturasonic.app.sync.CloudBackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class BackupStatus { IDLE, IN_PROGRESS, SUCCESS, ERROR }

@HiltViewModel
class CloudBackupViewModel @Inject constructor(
    private val cloudBackupManager: CloudBackupManager,
    private val workManager: WorkManager
) : ViewModel() {

    val backupState: StateFlow<BackupState> = cloudBackupManager.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BackupState())

    private val _backupStatus = MutableStateFlow(BackupStatus.IDLE)
    val backupStatus: StateFlow<BackupStatus> = _backupStatus.asStateFlow()

    init {
        observeBackupWork()
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            cloudBackupManager.setAutoBackupEnabled(enabled)
        }
    }

    fun backupNow() {
        _backupStatus.value = BackupStatus.IN_PROGRESS
        cloudBackupManager.backupNow()
    }

    private fun observeBackupWork() {
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(BackupWorker.WORK_NAME)
                .collect { workInfos ->
                    val info = workInfos.firstOrNull() ?: return@collect
                    _backupStatus.value = when (info.state) {
                        WorkInfo.State.RUNNING -> BackupStatus.IN_PROGRESS
                        WorkInfo.State.SUCCEEDED -> BackupStatus.SUCCESS
                        WorkInfo.State.FAILED -> BackupStatus.ERROR
                        else -> _backupStatus.value
                    }
                }
        }
    }
}
