package com.naturasonic.app.ui.screens.tinnitus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naturasonic.app.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TinnitusSoundType(val value: Int, val label: String, val description: String) {
    WHITE(0, "Ruido blanco", "Todas las frecuencias a igual volumen"),
    PINK(1, "Ruido rosa", "Suave y natural, ideal para relajación"),
    BROWN(2, "Ruido marrón", "Grave y profundo, como cascada lejana"),
    PURE_TONE(3, "Tono puro", "Frecuencia única sinusoidal"),
    NOTCH(4, "Notch therapy", "Ruido rosa sin la frecuencia del tinnitus")
}

data class TinnitusUiState(
    val enabled: Boolean = false,
    val soundType: TinnitusSoundType = TinnitusSoundType.WHITE,
    val volume: Float = 0.3f,
    val frequencyHz: Float = 4000f,
    val timerMinutes: Int = 0
)

@HiltViewModel
class TinnitusViewModel @Inject constructor(
    private val userPreferences: UserPreferences
) : ViewModel() {

    val uiState = combine(
        userPreferences.tinnitusEnabled,
        userPreferences.tinnitusSoundType,
        userPreferences.tinnitusVolume,
        userPreferences.tinnitusFrequencyHz,
        userPreferences.tinnitusTimerMinutes
    ) { enabled, soundType, volume, freqHz, timerMinutes ->
        TinnitusUiState(
            enabled = enabled,
            soundType = TinnitusSoundType.entries.firstOrNull { it.value == soundType }
                ?: TinnitusSoundType.WHITE,
            volume = volume,
            frequencyHz = freqHz,
            timerMinutes = timerMinutes
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TinnitusUiState())

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setTinnitusEnabled(enabled) }
    }

    fun setSoundType(type: TinnitusSoundType) {
        viewModelScope.launch { userPreferences.setTinnitusSoundType(type.value) }
    }

    fun setVolume(volume: Float) {
        viewModelScope.launch { userPreferences.setTinnitusVolume(volume) }
    }

    fun setFrequencyHz(freqHz: Float) {
        viewModelScope.launch { userPreferences.setTinnitusFrequencyHz(freqHz) }
    }

    fun setTimerMinutes(minutes: Int) {
        viewModelScope.launch { userPreferences.setTinnitusTimerMinutes(minutes) }
    }
}
