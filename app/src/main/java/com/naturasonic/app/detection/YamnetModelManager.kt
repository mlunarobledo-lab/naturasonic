package com.naturasonic.app.detection

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed interface YamnetModelState {
    data object Uninitialized : YamnetModelState
    data class Copying(val progress: Float) : YamnetModelState
    data class Ready(val modelPath: String) : YamnetModelState
    data class Error(val message: String) : YamnetModelState
}

@Singleton
class YamnetModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _state = MutableStateFlow<YamnetModelState>(YamnetModelState.Uninitialized)
    val state: StateFlow<YamnetModelState> = _state.asStateFlow()

    private fun getModelDir(): File = File(context.filesDir, MODEL_DIR)

    fun isModelInStorage(): Boolean {
        val file = File(getModelDir(), MODEL_FILENAME)
        return file.exists() && file.length() > 0
    }

    suspend fun ensureModel(): String? {
        val destFile = File(getModelDir(), MODEL_FILENAME)

        if (destFile.exists() && destFile.length() > 0) {
            _state.value = YamnetModelState.Ready(destFile.absolutePath)
            return destFile.absolutePath
        }

        return extractFromAssets(destFile)
    }

    private suspend fun extractFromAssets(destFile: File): String? = withContext(Dispatchers.IO) {
        val assetPath = "$ASSET_DIR/$MODEL_FILENAME"

        val totalBytes = try {
            context.assets.openFd(assetPath).use { it.length }
        } catch (_: FileNotFoundException) {
            _state.value = YamnetModelState.Uninitialized
            return@withContext null
        } catch (_: Exception) {
            ESTIMATED_SIZE_BYTES
        }

        _state.value = YamnetModelState.Copying(0f)

        try {
            destFile.parentFile?.mkdirs()

            context.assets.open(assetPath).buffered(BUFFER_SIZE).use { input ->
                FileOutputStream(destFile).buffered(BUFFER_SIZE).use { output ->
                    var bytesCopied = 0L
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesCopied += bytesRead
                        val progress = if (totalBytes > 0) {
                            (bytesCopied.toFloat() / totalBytes).coerceAtMost(1f)
                        } else 0f
                        _state.value = YamnetModelState.Copying(progress)
                    }
                }
            }

            _state.value = YamnetModelState.Ready(destFile.absolutePath)
            destFile.absolutePath
        } catch (e: Exception) {
            destFile.delete()
            _state.value = YamnetModelState.Error(e.message ?: "Error al preparar modelo YAMNet")
            null
        }
    }

    fun resetState() {
        _state.value = YamnetModelState.Uninitialized
    }

    companion object {
        private const val BUFFER_SIZE = 16384
        private const val MODEL_DIR = "models/yamnet"
        private const val MODEL_FILENAME = "yamnet.tflite"
        private const val ASSET_DIR = "models/yamnet"
        private const val ESTIMATED_SIZE_BYTES = 3_500_000L
    }
}
