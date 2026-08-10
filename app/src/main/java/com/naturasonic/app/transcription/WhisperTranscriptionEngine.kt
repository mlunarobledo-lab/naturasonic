package com.naturasonic.app.transcription

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhisperTranscriptionEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var ctxPtr: Long = 0L

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

    private val resampleBuffer = mutableListOf<Float>()

    private fun getModelDir(): File = File(context.filesDir, "models/whisper")

    fun isModelDownloaded(model: WhisperModel = _selectedModel.value): Boolean {
        val modelFile = File(getModelDir(), model.fileName)
        return modelFile.exists() && modelFile.length() > 0
    }

    fun selectModel(model: WhisperModel) {
        _selectedModel.value = model
        if (isModelDownloaded(model)) {
            initializeModel(model)
        } else {
            releaseContext()
            _isModelReady.value = false
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

    fun initializeModel(model: WhisperModel = _selectedModel.value) {
        releaseContext()
        val modelFile = File(getModelDir(), model.fileName)
        if (!modelFile.exists()) return

        ctxPtr = nativeInit(modelFile.absolutePath)
        _isModelReady.value = ctxPtr != 0L
    }

    fun processAudioBuffer(buffer: FloatArray) {
        if (!_isTranscribing.value || ctxPtr == 0L) return

        val resampled = resample48to16(buffer)
        synchronized(resampleBuffer) {
            resampleBuffer.addAll(resampled.toList())

            if (resampleBuffer.size >= SEGMENT_SAMPLES) {
                val segment = resampleBuffer.take(SEGMENT_SAMPLES).toFloatArray()
                resampleBuffer.clear()

                val text = nativeTranscribe(ctxPtr, segment)
                if (text.isNotBlank()) {
                    _currentText.value = text.trim()
                }
            }
        }
    }

    fun startTranscription() {
        _isTranscribing.value = true
        _currentText.value = ""
        synchronized(resampleBuffer) {
            resampleBuffer.clear()
        }
    }

    fun stopTranscription(): String {
        _isTranscribing.value = false

        synchronized(resampleBuffer) {
            if (resampleBuffer.size >= MIN_SAMPLES && ctxPtr != 0L) {
                val remaining = resampleBuffer.toFloatArray()
                resampleBuffer.clear()
                val text = nativeTranscribe(ctxPtr, remaining)
                if (text.isNotBlank()) {
                    _currentText.value = text.trim()
                }
            }
            resampleBuffer.clear()
        }

        return _currentText.value
    }

    fun release() {
        stopTranscription()
        releaseContext()
    }

    private fun releaseContext() {
        if (ctxPtr != 0L) {
            nativeFree(ctxPtr)
            ctxPtr = 0L
        }
        _isModelReady.value = false
    }

    private external fun nativeInit(modelPath: String): Long
    private external fun nativeTranscribe(ctxPtr: Long, audioData: FloatArray): String
    private external fun nativeFree(ctxPtr: Long)

    companion object {
        private const val WHISPER_SAMPLE_RATE = 16000
        private const val SEGMENT_SECONDS = 10
        private const val SEGMENT_SAMPLES = WHISPER_SAMPLE_RATE * SEGMENT_SECONDS
        private const val MIN_SAMPLES = WHISPER_SAMPLE_RATE

        init {
            System.loadLibrary("whisper_jni")
        }
    }
}

private fun resample48to16(input: FloatArray): FloatArray {
    val ratio = 3
    val outputSize = input.size / ratio
    if (outputSize == 0) return floatArrayOf()

    val output = FloatArray(outputSize)
    for (i in output.indices) {
        val srcIdx = i * ratio
        var sum = 0f
        var count = 0
        for (j in 0 until ratio) {
            if (srcIdx + j < input.size) {
                sum += input[srcIdx + j]
                count++
            }
        }
        output[i] = sum / count
    }
    return output
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
