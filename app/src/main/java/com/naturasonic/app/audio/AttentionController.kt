package com.naturasonic.app.audio

import com.naturasonic.app.detection.DetectedAlert
import com.naturasonic.app.detection.SoundAlertDetector
import com.naturasonic.app.transcription.WhisperTranscriptionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class AttentionState { IDLE, SPEECH, ALERT }

@Singleton
class AttentionController @Inject constructor(
    private val audioEngine: OboeAudioEngine,
    private val whisperEngine: WhisperTranscriptionEngine,
    private val alertDetector: SoundAlertDetector
) {
    private val _state = MutableStateFlow(AttentionState.IDLE)
    val state: StateFlow<AttentionState> = _state.asStateFlow()

    private var observerJob: Job? = null
    private var speechBoostDb = 3.0f
    private var alertAttenuationDb = 4.0f

    private val currentOffsets = FloatArray(NUM_BANDS)
    private var lastAlertTimestamp = 0L

    fun start(scope: CoroutineScope, boostDb: Float, attenuationDb: Float) {
        stop()
        speechBoostDb = boostDb
        alertAttenuationDb = attenuationDb
        audioEngine.setAttentionAgcEnabled(true)

        observerJob = scope.launch {
            combine(
                whisperEngine.isTranscribing,
                alertDetector.latestAlert
            ) { transcribing, alert ->
                Pair(transcribing, alert)
            }.collect { (isTranscribing, latestAlert) ->
                val now = System.currentTimeMillis()

                if (latestAlert != null && (now - latestAlert.timestamp) < ALERT_TIMEOUT_MS) {
                    lastAlertTimestamp = latestAlert.timestamp
                }

                val alertActive = (now - lastAlertTimestamp) < ALERT_TIMEOUT_MS && lastAlertTimestamp > 0

                val newState = when {
                    alertActive -> AttentionState.ALERT
                    isTranscribing -> AttentionState.SPEECH
                    else -> AttentionState.IDLE
                }

                if (newState != _state.value) {
                    _state.value = newState
                }

                applyOffsets(newState)
            }
        }
    }

    fun stop() {
        observerJob?.cancel()
        observerJob = null
        _state.value = AttentionState.IDLE
        currentOffsets.fill(0f)
        audioEngine.setAttentionAgcEnabled(false)
    }

    fun updateParams(boostDb: Float, attenuationDb: Float) {
        speechBoostDb = boostDb
        alertAttenuationDb = attenuationDb
        applyOffsets(_state.value)
    }

    val isActive: Boolean get() = observerJob?.isActive == true

    private fun applyOffsets(state: AttentionState) {
        val targetOffsets = FloatArray(NUM_BANDS)

        when (state) {
            AttentionState.SPEECH -> {
                targetOffsets[3] = speechBoostDb * 0.7f  // 1kHz
                targetOffsets[4] = speechBoostDb          // 2kHz (peak intelligibility)
                targetOffsets[5] = speechBoostDb * 0.8f  // 4kHz
            }
            AttentionState.ALERT -> {
                for (i in 0 until 3) {
                    targetOffsets[i] = -alertAttenuationDb  // 125-500Hz attenuate
                }
                for (i in 6 until NUM_BANDS) {
                    targetOffsets[i] = -alertAttenuationDb  // 6kHz-12kHz attenuate
                }
            }
            AttentionState.IDLE -> { }
        }

        for (i in 0 until NUM_BANDS) {
            currentOffsets[i] = currentOffsets[i] + (targetOffsets[i] - currentOffsets[i]) * SMOOTHING_FACTOR
            if (kotlin.math.abs(currentOffsets[i]) < 0.05f) {
                currentOffsets[i] = 0f
            }
        }

        audioEngine.setAttentionGainOffsets(currentOffsets.copyOf())
    }

    companion object {
        private const val NUM_BANDS = 10
        private const val ALERT_TIMEOUT_MS = 3000L
        private const val SMOOTHING_FACTOR = 0.3f
    }
}
