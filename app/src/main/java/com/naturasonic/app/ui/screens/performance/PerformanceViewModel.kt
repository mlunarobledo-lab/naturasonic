package com.naturasonic.app.ui.screens.performance

import androidx.lifecycle.ViewModel
import com.naturasonic.app.performance.DetectionStats
import com.naturasonic.app.performance.DspStats
import com.naturasonic.app.performance.MemoryStats
import com.naturasonic.app.performance.PerformanceTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PerformanceViewModel @Inject constructor(
    private val tracker: PerformanceTracker
) : ViewModel() {
    val dspStats: StateFlow<DspStats> = tracker.dspStats
    val detectionStats: StateFlow<DetectionStats> = tracker.detectionStats
    val memoryStats: StateFlow<MemoryStats> = tracker.memoryStats
}
