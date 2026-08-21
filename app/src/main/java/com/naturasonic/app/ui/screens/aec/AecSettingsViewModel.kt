package com.naturasonic.app.ui.screens.aec

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naturasonic.app.audio.AudioSessionManager
import com.naturasonic.app.audio.OboeAudioEngine
import com.naturasonic.app.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AecMode(val value: Int, val label: String, val description: String) {
    OFF(0, "Desactivado", "Sin cancelación de eco. El audio pasa sin filtrar."),
    SOFTWARE(1, "Software (NLMS)", "Filtro adaptativo propio. Funciona en cualquier dispositivo."),
    SYSTEM(2, "Sistema (Android API)", "Usa el AcousticEchoCanceler del sistema operativo.");

    companion object {
        fun fromValue(value: Int): AecMode =
            entries.firstOrNull { it.value == value } ?: OFF
    }
}

data class AecUiState(
    val selectedMode: AecMode = AecMode.OFF,
    val isSystemAecAvailable: Boolean = false
)

@HiltViewModel
class AecSettingsViewModel @Inject constructor(
    private val audioEngine: OboeAudioEngine,
    private val audioSessionManager: AudioSessionManager,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(AecUiState())
    val uiState: StateFlow<AecUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val savedMode = userPreferences.aecMode.first()
            val isAvailable = audioSessionManager.isAecAvailable
            var mode = AecMode.fromValue(savedMode)
            if (mode == AecMode.SYSTEM && !isAvailable) {
                mode = AecMode.SOFTWARE
                userPreferences.setAecMode(mode.value)
            }
            _uiState.value = AecUiState(
                selectedMode = mode,
                isSystemAecAvailable = isAvailable
            )
            applyMode(mode)
        }
    }

    fun selectMode(mode: AecMode) {
        if (mode == AecMode.SYSTEM && !_uiState.value.isSystemAecAvailable) return
        _uiState.value = _uiState.value.copy(selectedMode = mode)
        applyMode(mode)
        viewModelScope.launch {
            userPreferences.setAecMode(mode.value)
        }
    }

    private fun applyMode(mode: AecMode) {
        audioEngine.setAecMode(mode.value)
        val sessionId = audioEngine.getAudioSessionId()
        if (mode == AecMode.SYSTEM && sessionId != 0) {
            audioSessionManager.setAecEnabled(sessionId, true)
        } else {
            audioSessionManager.setAecEnabled(0, false)
        }
    }
}
