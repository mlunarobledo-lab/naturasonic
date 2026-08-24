package com.naturasonic.app.ui.screens.wdrc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naturasonic.app.audio.OboeAudioEngine
import com.naturasonic.app.data.local.dao.AudiogramDao
import com.naturasonic.app.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class WdrcPreset(val value: Int, val label: String, val description: String) {
    SPEECH(0, "Conversación", "Refuerza frecuencias vocales (500–4000 Hz). Ideal para diálogos."),
    MUSIC(1, "Música", "Compresión suave y uniforme. Preserva la dinámica musical."),
    LOUD_ENV(2, "Entorno ruidoso", "Compresión agresiva en todas las bandas. Máxima protección."),
    CUSTOM(3, "Audiograma", "Parámetros derivados de tu test auditivo personalizado.");

    companion object {
        fun fromValue(value: Int): WdrcPreset =
            entries.firstOrNull { it.value == value } ?: SPEECH
    }
}

data class WdrcUiState(
    val enabled: Boolean = false,
    val preset: WdrcPreset = WdrcPreset.SPEECH,
    val makeupGainDb: Float = 6.0f,
    val activeGains: FloatArray = FloatArray(10) { 0f },
    val hasAudiogram: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WdrcUiState) return false
        return enabled == other.enabled && preset == other.preset &&
                makeupGainDb == other.makeupGainDb &&
                activeGains.contentEquals(other.activeGains) &&
                hasAudiogram == other.hasAudiogram
    }

    override fun hashCode(): Int {
        var result = enabled.hashCode()
        result = 31 * result + preset.hashCode()
        result = 31 * result + makeupGainDb.hashCode()
        result = 31 * result + activeGains.contentHashCode()
        result = 31 * result + hasAudiogram.hashCode()
        return result
    }
}

@HiltViewModel
class WdrcViewModel @Inject constructor(
    private val audioEngine: OboeAudioEngine,
    private val userPreferences: UserPreferences,
    private val audiogramDao: AudiogramDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(WdrcUiState())
    val uiState: StateFlow<WdrcUiState> = _uiState.asStateFlow()

    private var gainPollingJob: Job? = null

    init {
        viewModelScope.launch {
            val enabled = userPreferences.wdrcEnabled.first()
            val makeupGainDb = userPreferences.wdrcMakeupGainDb.first()
            val presetValue = userPreferences.wdrcPreset.first()
            val audiogram = audiogramDao.getLatestActive()
            _uiState.value = WdrcUiState(
                enabled = enabled,
                preset = WdrcPreset.fromValue(presetValue),
                makeupGainDb = makeupGainDb,
                hasAudiogram = audiogram != null
            )
            if (enabled) startGainPolling()
        }
    }

    fun setEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(enabled = enabled)
        viewModelScope.launch { userPreferences.setWdrcEnabled(enabled) }
        if (enabled) startGainPolling() else stopGainPolling()
    }

    fun setPreset(preset: WdrcPreset) {
        if (preset == WdrcPreset.CUSTOM && !_uiState.value.hasAudiogram) return
        _uiState.value = _uiState.value.copy(preset = preset)
        viewModelScope.launch { userPreferences.setWdrcPreset(preset.value) }
        if (preset == WdrcPreset.CUSTOM) {
            applyAudiogram()
        }
    }

    fun setMakeupGainDb(gainDb: Float) {
        val clamped = gainDb.coerceIn(0f, 24f)
        _uiState.value = _uiState.value.copy(makeupGainDb = clamped)
        viewModelScope.launch { userPreferences.setWdrcMakeupGainDb(clamped) }
    }

    fun applyAudiogram() {
        viewModelScope.launch {
            val audiogram = audiogramDao.getLatestActive() ?: return@launch
            val leftThresholds = audiogram.leftThresholds.split(",")
                .mapNotNull { it.trim().toFloatOrNull() }
            val rightThresholds = audiogram.rightThresholds.split(",")
                .mapNotNull { it.trim().toFloatOrNull() }
            val avgThresholds = FloatArray(minOf(leftThresholds.size, rightThresholds.size)) { i ->
                (leftThresholds[i] + rightThresholds[i]) / 2f
            }
            if (avgThresholds.isNotEmpty()) {
                audioEngine.applyWdrcAudiogramProfile(avgThresholds)
                userPreferences.setWdrcPreset(WdrcPreset.CUSTOM.value)
                _uiState.value = _uiState.value.copy(preset = WdrcPreset.CUSTOM)
            }
        }
    }

    private fun startGainPolling() {
        gainPollingJob?.cancel()
        gainPollingJob = viewModelScope.launch {
            while (isActive) {
                val gains = audioEngine.getWdrcActiveGains()
                if (gains != null && gains.size == 10) {
                    _uiState.value = _uiState.value.copy(activeGains = gains)
                }
                delay(200)
            }
        }
    }

    private fun stopGainPolling() {
        gainPollingJob?.cancel()
        gainPollingJob = null
    }

    override fun onCleared() {
        stopGainPolling()
        super.onCleared()
    }
}
