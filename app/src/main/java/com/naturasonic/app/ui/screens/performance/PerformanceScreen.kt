package com.naturasonic.app.ui.screens.performance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.naturasonic.app.performance.DetectionStats
import com.naturasonic.app.performance.DspStats
import com.naturasonic.app.performance.MemoryStats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceScreen(
    onNavigateBack: () -> Unit,
    viewModel: PerformanceViewModel = hiltViewModel()
) {
    val dsp by viewModel.dspStats.collectAsState()
    val detection by viewModel.detectionStats.collectAsState()
    val memory by viewModel.memoryStats.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rendimiento") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            DspStatsCard(dsp)
            DetectionStatsCard(detection)
            MemoryStatsCard(memory)

            Text(
                "Las métricas se actualizan cada segundo mientras el audio está activo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DspStatsCard(stats: DspStats) {
    MetricsCard(title = "Latencia DSP (AudioProcessor + Limiter)") {
        MetricRow("Min", "%.1f µs".format(stats.minUs))
        MetricRow("Max", "%.1f µs".format(stats.maxUs))
        MetricRow("Promedio", "%.1f µs".format(stats.avgUs))
        MetricRow("Frames procesados", "%,d".format(stats.frameCount))
    }
}

@Composable
private fun DetectionStatsCard(stats: DetectionStats) {
    MetricsCard(title = "Detección YAMNet (por ciclo)") {
        MetricRow("JNI buffer copy", "%.1f ms".format(stats.jniCopyMs))
        MetricRow("Resample 48→16kHz", "%.1f ms".format(stats.resampleMs))
        MetricRow("Clasificación TFLite", "%.1f ms".format(stats.classifyMs))
        MetricRow("Total", "%.1f ms".format(stats.totalMs))
    }
}

@Composable
private fun MemoryStatsCard(stats: MemoryStats) {
    MetricsCard(title = "Memoria") {
        MetricRow("Heap nativo", "%.1f MB".format(stats.nativeHeapMb))
        MetricRow("Heap Java", "%.1f / %.1f MB".format(stats.javaHeapMb, stats.javaMaxMb))
    }
}

@Composable
private fun MetricsCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
