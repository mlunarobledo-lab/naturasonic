package com.naturasonic.app.ui.screens.audiogram

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naturasonic.app.audio.OboeAudioEngine
import com.naturasonic.app.audiogram.AudiogramCalibration
import com.naturasonic.app.audiogram.ToneGenerator
import com.naturasonic.app.data.local.dao.AudiogramDao
import com.naturasonic.app.data.local.entity.AudiogramRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

enum class TestEar { LEFT, RIGHT }

enum class TestPhase { INTRO, TESTING, RESULTS }

data class AudiogramUiState(
    val phase: TestPhase = TestPhase.INTRO,
    val currentFrequencyIndex: Int = 0,
    val currentEar: TestEar = TestEar.LEFT,
    val currentDbHl: Float = 40f,
    val isPlaying: Boolean = false,
    val leftThresholds: FloatArray = FloatArray(6) { 0f },
    val rightThresholds: FloatArray = FloatArray(6) { 0f },
    val previousRecord: AudiogramRecord? = null,
    val applied: Boolean = false
) {
    val totalSteps: Int get() = 12
    val currentStep: Int get() {
        val earOffset = if (currentEar == TestEar.RIGHT) 6 else 0
        return earOffset + currentFrequencyIndex + 1
    }
    val currentFrequencyLabel: String get() = AudiogramCalibration.frequencyLabel(currentFrequencyIndex)
    val currentEarLabel: String get() = if (currentEar == TestEar.LEFT) "Oído izquierdo" else "Oído derecho"
    val isNormalLeft: Boolean get() = AudiogramCalibration.isNormalHearing(leftThresholds)
    val isNormalRight: Boolean get() = AudiogramCalibration.isNormalHearing(rightThresholds)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudiogramUiState) return false
        return phase == other.phase &&
               currentFrequencyIndex == other.currentFrequencyIndex &&
               currentEar == other.currentEar &&
               currentDbHl == other.currentDbHl &&
               isPlaying == other.isPlaying &&
               leftThresholds.contentEquals(other.leftThresholds) &&
               rightThresholds.contentEquals(other.rightThresholds) &&
               previousRecord == other.previousRecord &&
               applied == other.applied
    }

    override fun hashCode(): Int {
        var result = phase.hashCode()
        result = 31 * result + currentFrequencyIndex
        result = 31 * result + currentEar.hashCode()
        result = 31 * result + currentDbHl.hashCode()
        result = 31 * result + isPlaying.hashCode()
        result = 31 * result + leftThresholds.contentHashCode()
        result = 31 * result + rightThresholds.contentHashCode()
        result = 31 * result + (previousRecord?.hashCode() ?: 0)
        result = 31 * result + applied.hashCode()
        return result
    }
}

@HiltViewModel
class AudiogramViewModel @Inject constructor(
    private val audiogramDao: AudiogramDao,
    private val audioEngine: OboeAudioEngine
) : ViewModel() {

    private val toneGenerator = ToneGenerator()
    private val _uiState = MutableStateFlow(AudiogramUiState())
    val uiState: StateFlow<AudiogramUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val latest = audiogramDao.getLatestActive()
            _uiState.value = _uiState.value.copy(previousRecord = latest)
        }
    }

    fun startTest() {
        _uiState.value = _uiState.value.copy(
            phase = TestPhase.TESTING,
            currentFrequencyIndex = 0,
            currentEar = TestEar.LEFT,
            currentDbHl = 40f,
            isPlaying = false,
            leftThresholds = FloatArray(6) { 0f },
            rightThresholds = FloatArray(6) { 0f },
            applied = false
        )
    }

    fun setVolume(dbHl: Float) {
        val clamped = dbHl.coerceIn(0f, 80f)
        _uiState.value = _uiState.value.copy(currentDbHl = clamped)
        if (_uiState.value.isPlaying) {
            val freq = ToneGenerator.TEST_FREQUENCIES[_uiState.value.currentFrequencyIndex]
            val amplitude = ToneGenerator.dbHlToLinearAmplitude(clamped)
            toneGenerator.play(freq, amplitude)
        }
    }

    fun togglePlayback() {
        val state = _uiState.value
        if (state.isPlaying) {
            toneGenerator.stop()
            _uiState.value = state.copy(isPlaying = false)
        } else {
            val freq = ToneGenerator.TEST_FREQUENCIES[state.currentFrequencyIndex]
            val amplitude = ToneGenerator.dbHlToLinearAmplitude(state.currentDbHl)
            toneGenerator.play(freq, amplitude)
            _uiState.value = state.copy(isPlaying = true)
        }
    }

    fun confirmThreshold() {
        toneGenerator.stop()
        val state = _uiState.value

        if (state.currentEar == TestEar.LEFT) {
            val updated = state.leftThresholds.copyOf()
            updated[state.currentFrequencyIndex] = state.currentDbHl
            _uiState.value = state.copy(leftThresholds = updated, isPlaying = false)
        } else {
            val updated = state.rightThresholds.copyOf()
            updated[state.currentFrequencyIndex] = state.currentDbHl
            _uiState.value = state.copy(rightThresholds = updated, isPlaying = false)
        }

        advanceToNext()
    }

    private fun advanceToNext() {
        val state = _uiState.value
        val nextFreqIndex = state.currentFrequencyIndex + 1

        if (nextFreqIndex < 6) {
            _uiState.value = state.copy(
                currentFrequencyIndex = nextFreqIndex,
                currentDbHl = 40f,
                isPlaying = false
            )
        } else if (state.currentEar == TestEar.LEFT) {
            _uiState.value = state.copy(
                currentEar = TestEar.RIGHT,
                currentFrequencyIndex = 0,
                currentDbHl = 40f,
                isPlaying = false
            )
        } else {
            _uiState.value = state.copy(phase = TestPhase.RESULTS, isPlaying = false)
            saveRecord()
        }
    }

    private fun saveRecord() {
        viewModelScope.launch {
            val state = _uiState.value
            audiogramDao.deactivateAll()
            val record = AudiogramRecord(
                leftThresholds = Json.encodeToString(state.leftThresholds.toList()),
                rightThresholds = Json.encodeToString(state.rightThresholds.toList()),
                isActive = true
            )
            audiogramDao.insert(record)
        }
    }

    fun applyToEqualizer() {
        val state = _uiState.value
        val gains = AudiogramCalibration.computeEqGains(state.leftThresholds, state.rightThresholds)
        audioEngine.applyProfile(gains, 0.6f, noiseGateMode = 1)
        _uiState.value = state.copy(applied = true)
    }

    override fun onCleared() {
        toneGenerator.release()
        super.onCleared()
    }
}
