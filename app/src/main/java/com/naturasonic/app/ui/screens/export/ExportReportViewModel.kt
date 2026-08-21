package com.naturasonic.app.ui.screens.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naturasonic.app.export.ReportSummary
import com.naturasonic.app.export.WellnessReportGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ExportUiState(
    val summary: ReportSummary? = null,
    val isGenerating: Boolean = false,
    val generatedFile: File? = null,
    val error: String? = null
)

@HiltViewModel
class ExportReportViewModel @Inject constructor(
    private val reportGenerator: WellnessReportGenerator
) : ViewModel() {

    private val _state = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    init {
        loadSummary()
    }

    private fun loadSummary() {
        viewModelScope.launch {
            val summary = reportGenerator.getSummary()
            _state.value = _state.value.copy(summary = summary)
        }
    }

    fun generateReport() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isGenerating = true, error = null)
            try {
                val file = reportGenerator.generatePdf()
                _state.value = _state.value.copy(isGenerating = false, generatedFile = file)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isGenerating = false,
                    error = "Error al generar el reporte: ${e.message}"
                )
            }
        }
    }

    fun createShareIntent(file: File) = reportGenerator.createShareIntent(file)

    fun clearGeneratedFile() {
        _state.value = _state.value.copy(generatedFile = null)
    }
}
