package com.naturasonic.app.ui.screens.anccore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naturasonic.app.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AncCoreUiState(
    val enabled: Boolean = false,
    val cancellationGain: Float = 0.5f,
    val lpEnabled: Boolean = true,
    val hpEnabled: Boolean = true,
    val lpCutoff: Float = 200f,
    val hpCutoff: Float = 4000f
)

@HiltViewModel
class AncCoreViewModel @Inject constructor(
    private val userPreferences: UserPreferences
) : ViewModel() {

    val uiState = combine(
        userPreferences.ancPhaseEnabled,
        userPreferences.ancCancellationGain,
        userPreferences.ancLpEnabled,
        userPreferences.ancHpEnabled,
        userPreferences.ancLpCutoff,
        userPreferences.ancHpCutoff
    ) { values ->
        AncCoreUiState(
            enabled = values[0] as Boolean,
            cancellationGain = values[1] as Float,
            lpEnabled = values[2] as Boolean,
            hpEnabled = values[3] as Boolean,
            lpCutoff = values[4] as Float,
            hpCutoff = values[5] as Float
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AncCoreUiState())

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setAncPhaseEnabled(enabled) }
    }

    fun setCancellationGain(gain: Float) {
        viewModelScope.launch { userPreferences.setAncCancellationGain(gain) }
    }

    fun setLpEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setAncLpEnabled(enabled) }
    }

    fun setHpEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setAncHpEnabled(enabled) }
    }

    fun setLpCutoff(cutoffHz: Float) {
        viewModelScope.launch { userPreferences.setAncLpCutoff(cutoffHz) }
    }

    fun setHpCutoff(cutoffHz: Float) {
        viewModelScope.launch { userPreferences.setAncHpCutoff(cutoffHz) }
    }
}
