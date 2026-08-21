package com.naturasonic.app.ui.screens.voicehealth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naturasonic.app.audio.VoiceHealthRepository
import com.naturasonic.app.audio.VoiceMetricsData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class VoiceHealthViewModel @Inject constructor(
    private val repository: VoiceHealthRepository
) : ViewModel() {

    val metrics: StateFlow<VoiceMetricsData> = repository.metrics
    val history: StateFlow<List<VoiceMetricsData>> = repository.history

    init {
        repository.startPolling(viewModelScope)
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopPolling()
    }
}
