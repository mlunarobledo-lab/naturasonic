package com.naturasonic.app.ui.screens.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naturasonic.app.data.preferences.UserPreferences
import com.naturasonic.app.security.SecurityManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SecurityUiState(
    val lockEnabled: Boolean = false,
    val lockTimeout: Int = 1,
    val canAuthenticate: Boolean = false
)

@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val securityManager: SecurityManager
) : ViewModel() {

    val uiState = combine(
        userPreferences.securityLockEnabled,
        userPreferences.securityLockTimeout
    ) { enabled, timeout ->
        SecurityUiState(
            lockEnabled = enabled,
            lockTimeout = timeout,
            canAuthenticate = securityManager.canAuthenticate()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SecurityUiState())

    fun setLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setSecurityLockEnabled(enabled)
        }
    }

    fun setLockTimeout(minutes: Int) {
        viewModelScope.launch {
            userPreferences.setSecurityLockTimeout(minutes)
        }
    }

    fun lockNow() {
        securityManager.lockNow()
    }
}
