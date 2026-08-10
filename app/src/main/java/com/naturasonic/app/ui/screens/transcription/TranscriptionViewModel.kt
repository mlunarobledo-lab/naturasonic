package com.naturasonic.app.ui.screens.transcription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naturasonic.app.billing.BillingManager
import com.naturasonic.app.billing.PremiumFeature
import com.naturasonic.app.billing.hasAccess
import com.naturasonic.app.data.local.dao.TranscriptionDao
import com.naturasonic.app.data.local.entity.TranscriptionEntry
import com.naturasonic.app.data.preferences.UserPreferences
import com.naturasonic.app.transcription.ModelState
import com.naturasonic.app.transcription.VoskTranscriptionEngine
import com.naturasonic.app.transcription.WhisperModel
import com.naturasonic.app.transcription.WhisperTranscriptionEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SelectedEngine { VOSK, WHISPER }

data class TranscriptionUiState(
    val isTranscribing: Boolean = false,
    val currentText: String = "",
    val isModelReady: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val subtitleColorIndex: Int = 0,
    val history: List<TranscriptionEntry> = emptyList(),
    val selectedEngine: SelectedEngine = SelectedEngine.VOSK,
    val isPremium: Boolean = false,
    val whisperModelReady: Boolean = false,
    val whisperDownloading: Boolean = false,
    val whisperDownloadProgress: Float = 0f,
    val selectedWhisperModel: WhisperModel = WhisperModel.TINY,
    val whisperModelState: ModelState = ModelState.Uninitialized
)

@HiltViewModel
class TranscriptionViewModel @Inject constructor(
    private val voskEngine: VoskTranscriptionEngine,
    private val whisperEngine: WhisperTranscriptionEngine,
    private val billingManager: BillingManager,
    private val transcriptionDao: TranscriptionDao,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _isDownloading = MutableStateFlow(false)
    private val _selectedEngine = MutableStateFlow(SelectedEngine.VOSK)
    private val _whisperDownloading = MutableStateFlow(false)

    val uiState: StateFlow<TranscriptionUiState> = combine(
        isTranscribingFlow(),
        currentTextFlow(),
        modelReadyFlow(),
        _isDownloading,
        downloadProgressFlow(),
        userPreferences.subtitleColor,
        transcriptionDao.getRecent(20),
        _selectedEngine,
        billingManager.isPremium,
        whisperEngine.isModelReady,
        _whisperDownloading,
        whisperEngine.downloadProgress,
        whisperEngine.selectedModel,
        whisperEngine.modelManager.state
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val engine = values[7] as SelectedEngine
        val modelState = values[13] as ModelState

        TranscriptionUiState(
            isTranscribing = values[0] as Boolean,
            currentText = values[1] as String,
            isModelReady = values[2] as Boolean,
            isDownloading = values[3] as Boolean,
            downloadProgress = values[4] as Float,
            subtitleColorIndex = values[5] as Int,
            history = values[6] as List<TranscriptionEntry>,
            selectedEngine = engine,
            isPremium = values[8] as Boolean,
            whisperModelReady = values[9] as Boolean,
            whisperDownloading = values[10] as Boolean,
            whisperDownloadProgress = values[11] as Float,
            selectedWhisperModel = values[12] as WhisperModel,
            whisperModelState = modelState
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TranscriptionUiState())

    init {
        if (voskEngine.isModelDownloaded()) {
            voskEngine.initializeModel()
        }
        viewModelScope.launch {
            if (whisperEngine.isModelDownloaded()) {
                whisperEngine.initializeModel()
            }
        }
    }

    private fun isTranscribingFlow() = combine(
        voskEngine.isTranscribing, whisperEngine.isTranscribing, _selectedEngine
    ) { vosk, whisper, engine ->
        if (engine == SelectedEngine.WHISPER) whisper else vosk
    }

    private fun currentTextFlow() = combine(
        voskEngine.currentText, whisperEngine.currentText, _selectedEngine
    ) { vosk, whisper, engine ->
        if (engine == SelectedEngine.WHISPER) whisper else vosk
    }

    private fun modelReadyFlow() = combine(
        voskEngine.isModelReady, whisperEngine.isModelReady, _selectedEngine
    ) { vosk, whisper, engine ->
        if (engine == SelectedEngine.WHISPER) whisper else vosk
    }

    private fun downloadProgressFlow() = combine(
        voskEngine.downloadProgress, whisperEngine.downloadProgress, _selectedEngine
    ) { vosk, whisper, engine ->
        if (engine == SelectedEngine.WHISPER) whisper else vosk
    }

    fun selectEngine(engine: SelectedEngine) {
        if (engine == SelectedEngine.WHISPER && !billingManager.hasAccess(PremiumFeature.WHISPER_ENGINE)) {
            return
        }
        stopCurrentTranscription()
        _selectedEngine.value = engine
    }

    fun downloadModel() {
        val engine = _selectedEngine.value
        viewModelScope.launch {
            if (engine == SelectedEngine.WHISPER) {
                _whisperDownloading.value = true
                try {
                    whisperEngine.downloadModel()
                } catch (_: Exception) { }
                _whisperDownloading.value = false
            } else {
                _isDownloading.value = true
                try {
                    voskEngine.downloadModel()
                    voskEngine.initializeModel()
                } catch (_: Exception) { }
                _isDownloading.value = false
            }
        }
    }

    fun toggleTranscription() {
        val isActive = if (_selectedEngine.value == SelectedEngine.WHISPER) {
            whisperEngine.isTranscribing.value
        } else {
            voskEngine.isTranscribing.value
        }

        if (isActive) {
            stopTranscription()
        } else {
            startTranscription()
        }
    }

    private fun startTranscription() {
        if (_selectedEngine.value == SelectedEngine.WHISPER) {
            whisperEngine.startTranscription()
        } else {
            voskEngine.startTranscription()
        }
    }

    private fun stopTranscription() {
        val engine = _selectedEngine.value
        val text: String
        val engineKey: String

        if (engine == SelectedEngine.WHISPER) {
            text = whisperEngine.stopTranscription()
            engineKey = "WHISPER"
        } else {
            text = voskEngine.currentText.value
            voskEngine.stopTranscription()
            engineKey = "VOSK"
        }

        if (text.isNotBlank()) {
            viewModelScope.launch {
                transcriptionDao.insert(
                    TranscriptionEntry(
                        text = text,
                        language = "es",
                        durationMs = 0,
                        engine = engineKey
                    )
                )
            }
        }
    }

    private fun stopCurrentTranscription() {
        if (voskEngine.isTranscribing.value) voskEngine.stopTranscription()
        if (whisperEngine.isTranscribing.value) whisperEngine.stopTranscription()
    }

    fun selectWhisperModel(model: WhisperModel) {
        whisperEngine.selectModel(model)
    }

    fun setSubtitleColor(index: Int) {
        viewModelScope.launch {
            userPreferences.setSubtitleColor(index)
        }
    }

    override fun onCleared() {
        super.onCleared()
        voskEngine.stopTranscription()
        whisperEngine.release()
    }
}
