package com.naturasonic.app.ui.screens.headtracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naturasonic.app.audio.HeadTrackingManager
import com.naturasonic.app.audio.HeadTrackingState
import com.naturasonic.app.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HeadTrackingUiState(
    val trackingState: HeadTrackingState = HeadTrackingState.Disabled,
    val enabled: Boolean = false,
    val sensitivity: Float = 0.6f,
    val azimuthDeg: Float = 0f,
    val pitchDeg: Float = 0f,
    val sensorAvailable: Boolean = true
)

@HiltViewModel
class HeadTrackingViewModel @Inject constructor(
    private val headTrackingManager: HeadTrackingManager,
    private val userPreferences: UserPreferences
) : ViewModel() {

    val uiState: StateFlow<HeadTrackingUiState> = combine(
        headTrackingManager.state,
        userPreferences.headTrackingEnabled,
        userPreferences.headTrackingSensitivity,
        headTrackingManager.azimuthDeg
    ) { state, enabled, sensitivity, azimuth ->
        HeadTrackingUiState(
            trackingState = state,
            enabled = enabled,
            sensitivity = sensitivity,
            azimuthDeg = azimuth,
            pitchDeg = headTrackingManager.pitchDeg.value,
            sensorAvailable = headTrackingManager.isSensorAvailable
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HeadTrackingUiState(
        sensorAvailable = headTrackingManager.isSensorAvailable
    ))

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setHeadTrackingEnabled(enabled)
            if (enabled) {
                headTrackingManager.start()
            } else {
                headTrackingManager.stop()
            }
        }
    }

    fun setSensitivity(sensitivity: Float) {
        viewModelScope.launch {
            userPreferences.setHeadTrackingSensitivity(sensitivity)
        }
    }

    fun calibrate() {
        headTrackingManager.calibrate()
    }

    override fun onCleared() {
        super.onCleared()
        headTrackingManager.stop()
    }
}
