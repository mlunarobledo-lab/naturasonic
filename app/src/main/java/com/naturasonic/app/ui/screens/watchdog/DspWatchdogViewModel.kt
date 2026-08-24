package com.naturasonic.app.ui.screens.watchdog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naturasonic.app.audio.DspWatchdogManager
import com.naturasonic.app.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DspWatchdogUiState(
    val enabled: Boolean = true,
    val lastCallbackNs: Long = 0,
    val xRunCount: Int = 0,
    val restartCount: Int = 0,
    val consecutiveErrors: Int = 0,
    val isEngineRunning: Boolean = false,
    val healthLevel: DspWatchdogManager.HealthLevel = DspWatchdogManager.HealthLevel.GOOD,
    val watchdogRestarts: Int = 0
)

@HiltViewModel
class DspWatchdogViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val watchdogManager: DspWatchdogManager
) : ViewModel() {

    val uiState: StateFlow<DspWatchdogUiState> = combine(
        userPreferences.dspWatchdogEnabled,
        watchdogManager.state
    ) { enabled, wState ->
        DspWatchdogUiState(
            enabled = enabled,
            lastCallbackNs = wState.lastCallbackNs,
            xRunCount = wState.xRunCount,
            restartCount = wState.restartCount,
            consecutiveErrors = wState.consecutiveErrors,
            isEngineRunning = wState.isEngineRunning,
            healthLevel = wState.healthLevel,
            watchdogRestarts = wState.watchdogRestarts
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DspWatchdogUiState())

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setDspWatchdogEnabled(enabled)
        }
    }

    fun forceRestart() {
        viewModelScope.launch {
            watchdogManager.forceRestart()
        }
    }

    fun resetStats() {
        watchdogManager.resetStats()
    }
}
