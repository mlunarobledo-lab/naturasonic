package com.naturasonic.app.ui.screens.eco

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naturasonic.app.battery.BatteryMonitor
import com.naturasonic.app.battery.BatteryState
import com.naturasonic.app.battery.EcoModeManager
import com.naturasonic.app.battery.EcoReason
import com.naturasonic.app.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EcoUiState(
    val manualEnabled: Boolean = false,
    val autoActivateEnabled: Boolean = true,
    val threshold: Int = 20,
    val isEcoActive: Boolean = false,
    val reason: EcoReason = EcoReason.INACTIVE,
    val battery: BatteryState = BatteryState()
)

@HiltViewModel
class EcoViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val ecoModeManager: EcoModeManager,
    private val batteryMonitor: BatteryMonitor
) : ViewModel() {

    val uiState: StateFlow<EcoUiState> = combine(
        userPreferences.ecoModeEnabled,
        userPreferences.ecoModeAutoActivate,
        userPreferences.ecoModeThreshold,
        ecoModeManager.isEcoActive,
        ecoModeManager.reason,
        batteryMonitor.batteryState
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        EcoUiState(
            manualEnabled = values[0] as Boolean,
            autoActivateEnabled = values[1] as Boolean,
            threshold = values[2] as Int,
            isEcoActive = values[3] as Boolean,
            reason = values[4] as EcoReason,
            battery = values[5] as BatteryState
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EcoUiState())

    fun setManualEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setEcoModeEnabled(enabled) }
    }

    fun setAutoActivateEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setEcoModeAutoActivate(enabled) }
    }

    fun setThreshold(threshold: Int) {
        viewModelScope.launch { userPreferences.setEcoModeThreshold(threshold) }
    }
}
