package com.naturasonic.app.performance

import android.os.Debug
import com.naturasonic.app.audio.OboeAudioEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class DspStats(
    val minUs: Float = 0f,
    val maxUs: Float = 0f,
    val avgUs: Float = 0f,
    val frameCount: Long = 0
)

data class DetectionStats(
    val jniCopyMs: Float = 0f,
    val resampleMs: Float = 0f,
    val classifyMs: Float = 0f,
    val totalMs: Float = 0f
)

data class MemoryStats(
    val nativeHeapMb: Float = 0f,
    val javaHeapMb: Float = 0f,
    val javaMaxMb: Float = 0f
)

@Singleton
class PerformanceTracker @Inject constructor(
    private val audioEngine: OboeAudioEngine
) {
    private val _dspStats = MutableStateFlow(DspStats())
    val dspStats: StateFlow<DspStats> = _dspStats.asStateFlow()

    private val _detectionStats = MutableStateFlow(DetectionStats())
    val detectionStats: StateFlow<DetectionStats> = _detectionStats.asStateFlow()

    private val _memoryStats = MutableStateFlow(MemoryStats())
    val memoryStats: StateFlow<MemoryStats> = _memoryStats.asStateFlow()

    fun refreshDspStats() {
        val raw = audioEngine.getLatencyStats() ?: return
        _dspStats.value = DspStats(
            minUs = raw[0],
            maxUs = raw[1],
            avgUs = raw[2],
            frameCount = raw[3].toLong()
        )
    }

    fun reportDetectionTiming(jniCopyNs: Long, resampleNs: Long, classifyNs: Long) {
        _detectionStats.value = DetectionStats(
            jniCopyMs = jniCopyNs / 1_000_000f,
            resampleMs = resampleNs / 1_000_000f,
            classifyMs = classifyNs / 1_000_000f,
            totalMs = (jniCopyNs + resampleNs + classifyNs) / 1_000_000f
        )
    }

    fun refreshMemoryStats() {
        val runtime = Runtime.getRuntime()
        _memoryStats.value = MemoryStats(
            nativeHeapMb = Debug.getNativeHeapAllocatedSize() / (1024f * 1024f),
            javaHeapMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024f * 1024f),
            javaMaxMb = runtime.maxMemory() / (1024f * 1024f)
        )
    }
}
