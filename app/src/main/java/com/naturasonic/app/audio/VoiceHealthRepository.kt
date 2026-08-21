package com.naturasonic.app.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class VoiceMetricsData(
    val pitchHz: Float = 0f,
    val jitterPercent: Float = 0f,
    val shimmerPercent: Float = 0f,
    val isVoiced: Boolean = false
)

@Singleton
class VoiceHealthRepository @Inject constructor(
    private val audioEngine: OboeAudioEngine
) {
    private val _metrics = MutableStateFlow(VoiceMetricsData())
    val metrics: StateFlow<VoiceMetricsData> = _metrics.asStateFlow()

    private val _history = MutableStateFlow<List<VoiceMetricsData>>(emptyList())
    val history: StateFlow<List<VoiceMetricsData>> = _history.asStateFlow()

    private var pollingJob: Job? = null

    fun startPolling(scope: CoroutineScope) {
        pollingJob?.cancel()
        audioEngine.startVoiceAnalyzer()
        pollingJob = scope.launch {
            while (isActive) {
                val raw = audioEngine.getVoiceMetrics()
                if (raw != null && raw.size >= 4) {
                    val data = VoiceMetricsData(
                        pitchHz = raw[0],
                        jitterPercent = raw[1],
                        shimmerPercent = raw[2],
                        isVoiced = raw[3] > 0.5f
                    )
                    _metrics.value = data
                    if (data.isVoiced) {
                        val current = _history.value.toMutableList()
                        current.add(data)
                        if (current.size > MAX_HISTORY) current.removeAt(0)
                        _history.value = current
                    }
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        audioEngine.stopVoiceAnalyzer()
    }

    companion object {
        private const val POLL_INTERVAL_MS = 500L
        private const val MAX_HISTORY = 60
    }
}
