package com.naturasonic.app.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VolumeProtection @Inject constructor() {

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _showWarning = MutableStateFlow(false)
    val showWarning: StateFlow<Boolean> = _showWarning.asStateFlow()

    private var continuousListeningStartMs: Long = 0L
    private var isListening = false

    fun onListeningStarted() {
        if (!isListening) {
            continuousListeningStartMs = System.currentTimeMillis()
            isListening = true
            _isLocked.value = false
        }
    }

    fun onListeningStopped() {
        isListening = false
        continuousListeningStartMs = 0L
    }

    fun tick(): Float {
        if (!isListening) return MAX_VOLUME_DB

        val elapsed = System.currentTimeMillis() - continuousListeningStartMs
        if (elapsed >= LOCKOUT_DURATION_MS && !_isLocked.value) {
            _isLocked.value = true
            _showWarning.value = true
        }

        return if (_isLocked.value) LOCKED_VOLUME_DB else MAX_VOLUME_DB
    }

    fun dismissWarning() {
        _showWarning.value = false
    }

    fun resetLock() {
        _isLocked.value = false
        continuousListeningStartMs = System.currentTimeMillis()
    }

    companion object {
        const val MAX_VOLUME_DB = 85f
        const val LOCKED_VOLUME_DB = 70f
        const val LOCKOUT_DURATION_MS = 3_600_000L
    }
}
