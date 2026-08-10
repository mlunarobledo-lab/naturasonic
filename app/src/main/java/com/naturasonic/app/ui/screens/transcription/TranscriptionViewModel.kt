package com.naturasonic.app.ui.screens.transcription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naturasonic.app.data.local.dao.TranscriptionDao
import com.naturasonic.app.data.local.entity.TranscriptionEntry
import com.naturasonic.app.data.preferences.UserPreferences
import com.naturasonic.app.transcription.VoskTranscriptionEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TranscriptionUiState(
    val isTranscribing: Boolean = false,
    val currentText: String = "",
    val isModelReady: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val subtitleColorIndex: Int = 0,
    val history: List<TranscriptionEntry> = emptyList()
)

@HiltViewModel
class TranscriptionViewModel @Inject constructor(
    private val voskEngine: VoskTranscriptionEngine,
    private val transcriptionDao: TranscriptionDao,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _isDownloading = MutableStateFlow(false)

    val uiState: StateFlow<TranscriptionUiState> = combine(
        voskEngine.isTranscribing,
        voskEngine.currentText,
        voskEngine.isModelReady,
        _isDownloading,
        voskEngine.downloadProgress,
        userPreferences.subtitleColor,
        transcriptionDao.getRecent(20)
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        TranscriptionUiState(
            isTranscribing = values[0] as Boolean,
            currentText = values[1] as String,
            isModelReady = values[2] as Boolean,
            isDownloading = values[3] as Boolean,
            downloadProgress = values[4] as Float,
            subtitleColorIndex = values[5] as Int,
            history = values[6] as List<TranscriptionEntry>
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TranscriptionUiState())

    init {
        if (voskEngine.isModelDownloaded()) {
            voskEngine.initializeModel()
        }
    }

    fun downloadModel() {
        viewModelScope.launch {
            _isDownloading.value = true
            try {
                voskEngine.downloadModel()
                voskEngine.initializeModel()
            } catch (_: Exception) { }
            _isDownloading.value = false
        }
    }

    fun toggleTranscription() {
        if (voskEngine.isTranscribing.value) {
            stopTranscription()
        } else {
            startTranscription()
        }
    }

    private fun startTranscription() {
        voskEngine.startTranscription()
    }

    private fun stopTranscription() {
        val text = voskEngine.currentText.value
        voskEngine.stopTranscription()
        if (text.isNotBlank()) {
            viewModelScope.launch {
                transcriptionDao.insert(
                    TranscriptionEntry(
                        text = text,
                        language = "es",
                        durationMs = 0,
                        engine = "VOSK"
                    )
                )
            }
        }
    }

    fun setSubtitleColor(index: Int) {
        viewModelScope.launch {
            userPreferences.setSubtitleColor(index)
        }
    }

    override fun onCleared() {
        super.onCleared()
        voskEngine.stopTranscription()
    }
}
