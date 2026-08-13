package com.naturasonic.app.audio

import com.naturasonic.app.data.local.entity.AudioMode
import com.naturasonic.app.data.local.entity.AudioProfile
import com.naturasonic.app.data.preferences.UserPreferences
import com.naturasonic.app.data.repository.AudioProfileRepository
import com.naturasonic.app.detection.SoundAlertDetector
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

data class ModeConfig(
    val amplification: Float,
    val aecEnabled: Boolean,
    val nsEnabled: Boolean,
    val alertDetectionEnabled: Boolean,
    val eqPreset: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ModeConfig) return false
        return amplification == other.amplification &&
               aecEnabled == other.aecEnabled &&
               nsEnabled == other.nsEnabled &&
               alertDetectionEnabled == other.alertDetectionEnabled &&
               eqPreset.contentEquals(other.eqPreset)
    }

    override fun hashCode(): Int {
        var result = amplification.hashCode()
        result = 31 * result + aecEnabled.hashCode()
        result = 31 * result + nsEnabled.hashCode()
        result = 31 * result + alertDetectionEnabled.hashCode()
        result = 31 * result + eqPreset.contentHashCode()
        return result
    }
}

@Singleton
class AudioModeManager @Inject constructor(
    private val audioEngine: OboeAudioEngine,
    private val audioSessionManager: AudioSessionManager,
    private val alertDetector: SoundAlertDetector,
    private val userPreferences: UserPreferences,
    private val audioProfileRepository: AudioProfileRepository
) {
    fun getModeConfig(mode: AudioMode): ModeConfig = when (mode) {
        AudioMode.CONVERSATION -> ModeConfig(
            amplification = 0.7f,
            aecEnabled = true,
            nsEnabled = true,
            alertDetectionEnabled = true,
            eqPreset = floatArrayOf(2f, 3f, 4f, 4f, 3f, 2f, 0f, 0f, 0f, 0f)
        )
        AudioMode.ENTERTAINMENT -> ModeConfig(
            amplification = 0.5f,
            aecEnabled = false,
            nsEnabled = false,
            alertDetectionEnabled = false,
            eqPreset = floatArrayOf(3f, 2f, 0f, 1f, 2f, 3f, 4f, 3f, 2f, 1f)
        )
        AudioMode.OUTDOOR -> ModeConfig(
            amplification = 0.6f,
            aecEnabled = false,
            nsEnabled = true,
            alertDetectionEnabled = true,
            eqPreset = floatArrayOf(1f, 2f, 3f, 3f, 2f, 1f, 0f, 0f, 0f, 0f)
        )
        AudioMode.REMOTE_MIC -> ModeConfig(
            amplification = 0.9f,
            aecEnabled = true,
            nsEnabled = true,
            alertDetectionEnabled = false,
            eqPreset = floatArrayOf(4f, 5f, 5f, 5f, 4f, 3f, 2f, 1f, 0f, 0f)
        )
    }

    suspend fun applyMode(mode: AudioMode, audioSessionId: Int) {
        val profile = audioProfileRepository.getDefaultProfile(mode)
        if (profile != null) {
            applyProfile(profile, audioSessionId)
            userPreferences.setSelectedProfileId(profile.id)
        } else {
            val config = getModeConfig(mode)
            audioEngine.setAmplification(config.amplification)
            audioEngine.setEqBands(config.eqPreset)
            audioEngine.setNoiseSuppressionEnabled(config.nsEnabled)
            audioSessionManager.setAecEnabled(audioSessionId, config.aecEnabled)
        }

        val config = getModeConfig(mode)
        if (config.alertDetectionEnabled) {
            alertDetector.start()
        } else {
            alertDetector.stop()
        }
    }

    fun applyProfile(profile: AudioProfile, audioSessionId: Int) {
        val bands = try {
            Json.decodeFromString<List<Float>>(profile.eqBands).toFloatArray()
        } catch (_: Exception) {
            FloatArray(10) { 0f }
        }
        audioEngine.setEqBands(bands)
        audioEngine.setAmplification(profile.amplificationLevel)
        audioEngine.setNoiseSuppressionEnabled(profile.noiseSuppressionEnabled)
        audioSessionManager.setAecEnabled(audioSessionId, profile.aecEnabled)
    }

    suspend fun getCurrentMode(): AudioMode {
        val modeKey = userPreferences.currentMode.first()
        return AudioMode.fromKey(modeKey)
    }
}
