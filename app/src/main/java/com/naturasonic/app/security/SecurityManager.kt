package com.naturasonic.app.security

import android.content.Context
import androidx.biometric.BiometricManager
import com.naturasonic.app.data.preferences.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferences
) {
    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked

    private var backgroundTimestamp: Long = 0L

    fun canAuthenticate(): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun onAppBackgrounded() {
        backgroundTimestamp = System.currentTimeMillis()
    }

    suspend fun onAppResumed() {
        val lockEnabled = userPreferences.securityLockEnabled.first()
        if (!lockEnabled) {
            _isLocked.value = false
            return
        }

        if (backgroundTimestamp == 0L) return

        val timeoutMinutes = userPreferences.securityLockTimeout.first()
        val timeoutMs = timeoutMinutes * 60_000L
        val elapsed = System.currentTimeMillis() - backgroundTimestamp

        if (elapsed >= timeoutMs) {
            _isLocked.value = true
        }
    }

    fun unlock() {
        _isLocked.value = false
        backgroundTimestamp = 0L
    }

    fun lockNow() {
        _isLocked.value = true
    }
}
