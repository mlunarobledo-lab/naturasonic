package com.naturasonic.app.audio

import com.naturasonic.app.data.local.entity.AudioMode
import com.naturasonic.app.data.preferences.UserPreferences
import com.naturasonic.app.detection.SoundAlertDetector
import kotlinx.coroutines.flow.first
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
    private val userPreferences: UserPreferences
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

    fun applyMode(mode: AudioMode, audioSessionId: Int) {
        val config = getModeConfig(mode)
        audioEngine.setAmplification(config.amplification)
        audioEngine.setEqBands(config.eqPreset)
        audioEngine.setNoiseSuppressionEnabled(config.nsEnabled)

        if (config.aecEnabled) {
            audioSessionManager.setAecEnabled(audioSessionId, true)
        } else {
            audioSessionManager.setAecEnabled(audioSessionId, false)
        }

        if (config.alertDetectionEnabled) {
            alertDetector.start()
        } else {
            alertDetector.stop()
        }
    }

    suspend fun getCurrentMode(): AudioMode {
        val modeKey = userPreferences.currentMode.first()
        return AudioMode.fromKey(modeKey)
    }
}
