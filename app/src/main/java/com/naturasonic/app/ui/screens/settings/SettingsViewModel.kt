package com.naturasonic.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naturasonic.app.audio.AudioModeManager
import com.naturasonic.app.audio.OboeAudioEngine
import com.naturasonic.app.billing.BillingManager
import com.naturasonic.app.data.local.entity.AudioMode
import com.naturasonic.app.data.local.entity.AudioProfile
import com.naturasonic.app.data.preferences.UserPreferences
import com.naturasonic.app.data.repository.AudioProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class SettingsUiState(
    val profiles: List<AudioProfile> = emptyList(),
    val eqBands: FloatArray = FloatArray(10) { 0f },
    val alertDetectionEnabled: Boolean = true,
    val isPremium: Boolean = false,
    val currentMode: AudioMode = AudioMode.CONVERSATION
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SettingsUiState) return false
        return profiles == other.profiles &&
               eqBands.contentEquals(other.eqBands) &&
               alertDetectionEnabled == other.alertDetectionEnabled &&
               isPremium == other.isPremium &&
               currentMode == other.currentMode
    }

    override fun hashCode(): Int {
        var result = profiles.hashCode()
        result = 31 * result + eqBands.contentHashCode()
        result = 31 * result + alertDetectionEnabled.hashCode()
        result = 31 * result + isPremium.hashCode()
        result = 31 * result + currentMode.hashCode()
        return result
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val audioEngine: OboeAudioEngine,
    private val audioProfileRepository: AudioProfileRepository,
    private val modeManager: AudioModeManager,
    private val billingManager: BillingManager,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _eqBands = MutableStateFlow(FloatArray(10) { 0f })

    val uiState: StateFlow<SettingsUiState> = combine(
        audioProfileRepository.getAllProfiles(),
        _eqBands,
        userPreferences.alertDetectionEnabled,
        billingManager.isPremium,
        userPreferences.currentMode
    ) { profiles, eqBands, alertEnabled, isPremium, modeKey ->
        SettingsUiState(
            profiles = profiles,
            eqBands = eqBands,
            alertDetectionEnabled = alertEnabled,
            isPremium = isPremium,
            currentMode = AudioMode.fromKey(modeKey)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    init {
        viewModelScope.launch {
            val selectedId = userPreferences.selectedProfileId.first()
            val modeKey = userPreferences.currentMode.first()
            val mode = AudioMode.fromKey(modeKey)

            val profile = if (selectedId > 0) {
                audioProfileRepository.getById(selectedId)
            } else {
                null
            } ?: audioProfileRepository.getDefaultProfile(mode)

            if (profile != null) {
                val bands = try {
                    Json.decodeFromString<List<Float>>(profile.eqBands).toFloatArray()
                } catch (_: Exception) {
                    FloatArray(10) { 0f }
                }
                _eqBands.value = bands
            }
        }
    }

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

    fun saveProfile(name: String) {
        viewModelScope.launch {
            val modeKey = userPreferences.currentMode.first()
            val bandsJson = Json.encodeToString(_eqBands.value.toList())
            val profileId = audioProfileRepository.save(
                AudioProfile(
                    name = name,
                    mode = modeKey,
                    eqBands = bandsJson,
                    amplificationLevel = audioEngine.nativeHandle.let { 0.5f },
                    noiseSuppressionEnabled = true,
                    aecEnabled = true
                )
            )
            userPreferences.setSelectedProfileId(profileId)
        }
    }

    fun loadProfile(profile: AudioProfile) {
        viewModelScope.launch {
            val bands = try {
                Json.decodeFromString<List<Float>>(profile.eqBands).toFloatArray()
            } catch (_: Exception) {
                FloatArray(10) { 0f }
            }
            _eqBands.value = bands
            modeManager.applyProfile(profile, 0)
            userPreferences.setSelectedProfileId(profile.id)
        }
    }

    fun deleteProfile(profile: AudioProfile) {
        viewModelScope.launch {
            val selectedId = userPreferences.selectedProfileId.first()
            audioProfileRepository.delete(profile)
            if (profile.id == selectedId) {
                val modeKey = userPreferences.currentMode.first()
                val mode = AudioMode.fromKey(modeKey)
                val fallback = audioProfileRepository.getDefaultProfile(mode)
                if (fallback != null) {
                    loadProfile(fallback)
                }
            }
        }
    }
}
