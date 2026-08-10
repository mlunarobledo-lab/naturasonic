package com.naturasonic.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naturasonic.app.audio.OboeAudioEngine
import com.naturasonic.app.billing.BillingManager
import com.naturasonic.app.data.local.dao.AudioProfileDao
import com.naturasonic.app.data.local.entity.AudioMode
import com.naturasonic.app.data.local.entity.AudioProfile
import com.naturasonic.app.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class SettingsUiState(
    val profiles: List<AudioProfile> = emptyList(),
    val eqBands: FloatArray = FloatArray(10) { 0f },
    val alertDetectionEnabled: Boolean = true,
    val isPremium: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SettingsUiState) return false
        return profiles == other.profiles &&
               eqBands.contentEquals(other.eqBands) &&
               alertDetectionEnabled == other.alertDetectionEnabled &&
               isPremium == other.isPremium
    }

    override fun hashCode(): Int {
        var result = profiles.hashCode()
        result = 31 * result + eqBands.contentHashCode()
        result = 31 * result + alertDetectionEnabled.hashCode()
        result = 31 * result + isPremium.hashCode()
        return result
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val audioEngine: OboeAudioEngine,
    private val audioProfileDao: AudioProfileDao,
    private val billingManager: BillingManager,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _eqBands = MutableStateFlow(FloatArray(10) { 0f })

    val uiState: StateFlow<SettingsUiState> = combine(
        audioProfileDao.getAllProfiles(),
        _eqBands,
        userPreferences.alertDetectionEnabled,
        billingManager.isPremium
    ) { profiles, eqBands, alertEnabled, isPremium ->
        SettingsUiState(
            profiles = profiles,
            eqBands = eqBands,
            alertDetectionEnabled = alertEnabled,
            isPremium = isPremium
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setEqBand(index: Int, value: Float) {
        val bands = _eqBands.value.copyOf()
        bands[index] = value.coerceIn(-12f, 12f)
        _eqBands.value = bands
        audioEngine.setEqBands(bands)
    }

    fun setAlertDetection(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setAlertDetectionEnabled(enabled)
        }
    }

    fun saveProfile(name: String, mode: AudioMode) {
        viewModelScope.launch {
            val bandsJson = Json.encodeToString(_eqBands.value.toList())
            audioProfileDao.insert(
                AudioProfile(
                    name = name,
                    mode = mode.key,
                    eqBands = bandsJson,
                    amplificationLevel = 0.5f,
                    noiseSuppressionEnabled = true,
                    aecEnabled = true
                )
            )
        }
    }

    fun loadProfile(profile: AudioProfile) {
        try {
            val bands = Json.decodeFromString<List<Float>>(profile.eqBands).toFloatArray()
            _eqBands.value = bands
            audioEngine.setEqBands(bands)
            audioEngine.setAmplification(profile.amplificationLevel)
            audioEngine.setNoiseSuppressionEnabled(profile.noiseSuppressionEnabled)
        } catch (_: Exception) { }
    }

    fun deleteProfile(profile: AudioProfile) {
        viewModelScope.launch {
            audioProfileDao.delete(profile)
        }
    }
}
