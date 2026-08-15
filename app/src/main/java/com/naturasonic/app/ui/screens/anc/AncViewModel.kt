package com.naturasonic.app.ui.screens.anc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naturasonic.app.audio.OboeAudioEngine
import com.naturasonic.app.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class NoiseGateMode(val value: Int, val label: String, val description: String) {
    OFF(0, "Desactivado", "Todo el audio pasa sin filtrar. Escuchas el entorno tal cual."),
    VOICE_FOCUS(1, "Enfoque de voz", "Atenúa el ruido de fondo y prioriza las voces humanas."),
    AGGRESSIVE(2, "Cancelación agresiva", "Silencia casi todo lo que no sea voz. Máximo aislamiento.");

    companion object {
        fun fromValue(value: Int): NoiseGateMode =
            entries.firstOrNull { it.value == value } ?: OFF
    }
}

data class AncUiState(
    val selectedMode: NoiseGateMode = NoiseGateMode.OFF
)

@HiltViewModel
class AncViewModel @Inject constructor(
    private val audioEngine: OboeAudioEngine,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(AncUiState())
    val uiState: StateFlow<AncUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val savedMode = userPreferences.noiseGateMode.first()
            val mode = NoiseGateMode.fromValue(savedMode)
            _uiState.value = AncUiState(selectedMode = mode)
            audioEngine.setNoiseGateMode(mode.value)
        }
    }

    fun selectMode(mode: NoiseGateMode) {
        _uiState.value = _uiState.value.copy(selectedMode = mode)
        audioEngine.setNoiseGateMode(mode.value)
        viewModelScope.launch {
            userPreferences.setNoiseGateMode(mode.value)
        }
    }
}
