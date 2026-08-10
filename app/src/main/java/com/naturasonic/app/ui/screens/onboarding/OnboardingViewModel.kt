package com.naturasonic.app.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naturasonic.app.bluetooth.BluetoothAudioManager
import com.naturasonic.app.bluetooth.BluetoothCompatibility
import com.naturasonic.app.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val currentPage: Int = 0,
    val btCompatibility: BluetoothCompatibility = BluetoothCompatibility.NOT_SUPPORTED,
    val disclaimerAccepted: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val bluetoothManager: BluetoothAudioManager
) : ViewModel() {

    val onboardingCompleted = userPreferences.onboardingCompleted

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun checkBluetooth() {
        val compat = bluetoothManager.checkCompatibility()
        _uiState.value = _uiState.value.copy(btCompatibility = compat)
    }

    fun acceptDisclaimer() {
        _uiState.value = _uiState.value.copy(disclaimerAccepted = true)
        viewModelScope.launch {
            userPreferences.setDisclaimerAccepted(true)
        }
    }

    fun nextPage() {
        _uiState.value = _uiState.value.copy(currentPage = _uiState.value.currentPage + 1)
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            userPreferences.setOnboardingCompleted(true)
        }
    }
}
