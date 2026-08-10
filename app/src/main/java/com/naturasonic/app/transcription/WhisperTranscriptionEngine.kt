package com.naturasonic.app.transcription

import android.content.Context
import com.naturasonic.app.audio.OboeAudioEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhisperTranscriptionEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val oboeEngine: OboeAudioEngine,
    val modelManager: GgmlModelManager
) {
    private val _currentText = MutableStateFlow("")
    val currentText: StateFlow<String> = _currentText.asStateFlow()

    private val _isModelReady = MutableStateFlow(false)
    val isModelReady: StateFlow<Boolean> = _isModelReady.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _isTranscribing = MutableStateFlow(false)
    val isTranscribing: StateFlow<Boolean> = _isTranscribing.asStateFlow()

    private val _selectedModel = MutableStateFlow(WhisperModel.TINY)
    val selectedModel: StateFlow<WhisperModel> = _selectedModel.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default)
    private var pollingJob: Job? = null

    private fun getModelDir(): File = File(context.filesDir, "models/whisper")

    fun isModelDownloaded(model: WhisperModel = _selectedModel.value): Boolean {
        return modelManager.isModelInStorage(model)
    }

    fun selectModel(model: WhisperModel) {
        _selectedModel.value = model
        scope.launch {
            initializeModel(model)
        }
    }

    suspend fun downloadModel(
        model: WhisperModel = _selectedModel.value,
        onProgress: (Float) -> Unit = {}
    ) {
        withContext(Dispatchers.IO) {
            val destDir = getModelDir()
            destDir.mkdirs()
            val destFile = File(destDir, model.fileName)

            try {
                val connection = URL(model.url).openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.connect()
                val totalSize = connection.contentLength.toFloat()
                var downloadedSize = 0L

                connection.inputStream.use { input ->
                    FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedSize += bytesRead
                            val progress = if (totalSize > 0) downloadedSize / totalSize else 0f
                            _downloadProgress.value = progress
                            onProgress(progress)
                        }
                    }
                }
                _downloadProgress.value = 1f
                initializeModel(model)
            } catch (e: Exception) {
                destFile.delete()
                _downloadProgress.value = 0f
                throw e
            }
        }
    }

    suspend fun initializeModel(model: WhisperModel = _selectedModel.value) {
        releaseContext()

        val path = modelManager.ensureModel(model)
        if (path == null) {
            _isModelReady.value = false
            return
        }

        val handle = oboeEngine.nativeHandle
        if (handle == 0L) {
            _isModelReady.value = false
            return
        }

        val ok = nativeInitWhisper(handle, path)
        _isModelReady.value = ok
    }

    fun startTranscription() {
        val handle = oboeEngine.nativeHandle
        if (handle == 0L || !_isModelReady.value) return

        _isTranscribing.value = true
        _currentText.value = ""
        nativeStartWhisperCapture(handle)
        startTextPolling()
    }

    fun stopTranscription(): String {
        _isTranscribing.value = false
        pollingJob?.cancel()
        pollingJob = null

        val handle = oboeEngine.nativeHandle
        if (handle == 0L) return _currentText.value

        val finalText = nativeStopWhisperCapture(handle)
        if (finalText.isNotBlank()) {
            _currentText.value = finalText.trim()
        }
        return _currentText.value
    }

    fun release() {
        pollingJob?.cancel()
        pollingJob = null
        _isTranscribing.value = false
        releaseContext()
    }

    private fun releaseContext() {
        val handle = oboeEngine.nativeHandle
        if (handle != 0L) {
            nativeReleaseWhisper(handle)
        }
        _isModelReady.value = false
    }

    private fun startTextPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive && _isTranscribing.value) {
                val handle = oboeEngine.nativeHandle
                if (handle != 0L) {
                    val text = nativeGetWhisperText(handle)
                    if (text.isNotBlank()) {
                        _currentText.value = text.trim()
                    }
                }
                delay(150)
            }
        }
    }

    private external fun nativeInitWhisper(engineHandle: Long, modelPath: String): Boolean
    private external fun nativeStartWhisperCapture(engineHandle: Long)
    private external fun nativeStopWhisperCapture(engineHandle: Long): String
    private external fun nativeGetWhisperText(engineHandle: Long): String
    private external fun nativeReleaseWhisper(engineHandle: Long)

    companion object {
        init {
            System.loadLibrary("naturasonic")
        }
    }
}

enum class WhisperModel(
    val fileName: String,
    val displayName: String,
    val sizeMb: Int,
    val url: String
) {
    TINY(
        fileName = "ggml-tiny.bin",
        displayName = "Tiny (~75 MB)",
        sizeMb = 75,
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin"
    ),
    BASE(
        fileName = "ggml-base.bin",
        displayName = "Base (~142 MB)",
        sizeMb = 142,
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin"
    )
}
