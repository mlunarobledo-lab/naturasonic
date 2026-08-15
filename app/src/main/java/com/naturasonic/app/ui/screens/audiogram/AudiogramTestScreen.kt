package com.naturasonic.app.ui.screens.audiogram

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.naturasonic.app.audiogram.AudiogramCalibration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiogramTestScreen(
    onNavigateBack: () -> Unit,
    viewModel: AudiogramViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calibración auditiva") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (state.phase) {
                TestPhase.INTRO -> IntroContent(
                    previousRecord = state.previousRecord,
                    onStartTest = { viewModel.startTest() }
                )
                TestPhase.TESTING -> TestingContent(
                    state = state,
                    onVolumeChange = { viewModel.setVolume(it) },
                    onTogglePlay = { viewModel.togglePlayback() },
                    onConfirm = { viewModel.confirmThreshold() }
                )
                TestPhase.RESULTS -> ResultsContent(
                    state = state,
                    onApply = { viewModel.applyToEqualizer() },
                    onRetake = { viewModel.startTest() }
                )
            }
        }
    }
}

@Composable
private fun IntroContent(
    previousRecord: com.naturasonic.app.data.local.entity.AudiogramRecord?,
    onStartTest: () -> Unit
) {
    Spacer(Modifier.height(24.dp))

    Text(
        text = "Ajusta tu amplificación",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )

    Spacer(Modifier.height(16.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = "Esta prueba NO es un audiograma clínico. Es una herramienta de auto-ajuste para personalizar tu amplificación. Si sospechas pérdida auditiva, consulta a un especialista.",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }

    Spacer(Modifier.height(24.dp))

    Text(
        text = "Vas a escuchar tonos a 6 frecuencias distintas. En cada una, ajusta el volumen hasta que apenas puedas escuchar el tono. Así la app sabrá qué frecuencias necesitas amplificar más.",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(16.dp))

    Text(
        text = "Se recomienda usar auriculares para resultados más precisos.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )

    if (previousRecord != null) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Ya tienes una calibración guardada. Puedes re-hacer el test para actualizarla.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
    }

    Spacer(Modifier.height(32.dp))

    Button(
        onClick = onStartTest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Empezar test")
    }
}

@Composable
private fun TestingContent(
    state: AudiogramUiState,
    onVolumeChange: (Float) -> Unit,
    onTogglePlay: () -> Unit,
    onConfirm: () -> Unit
) {
    LinearProgressIndicator(
        progress = { state.currentStep.toFloat() / state.totalSteps.toFloat() },
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(Modifier.height(8.dp))

    Text(
        text = "Paso ${state.currentStep} de ${state.totalSteps}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(Modifier.height(24.dp))

    Text(
        text = state.currentEarLabel,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )

    Spacer(Modifier.height(8.dp))

    Text(
        text = state.currentFrequencyLabel,
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )

    Spacer(Modifier.height(32.dp))

    IconButton(
        onClick = onTogglePlay,
        modifier = Modifier.size(72.dp)
    ) {
        Icon(
            imageVector = if (state.isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = if (state.isPlaying) "Detener" else "Reproducir",
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }

    Text(
        text = if (state.isPlaying) "Reproduciendo..." else "Toca para escuchar el tono",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(Modifier.height(32.dp))

    Text(
        text = "Volumen: ${state.currentDbHl.toInt()} dB",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )

    Spacer(Modifier.height(8.dp))

    Slider(
        value = state.currentDbHl,
        onValueChange = onVolumeChange,
        valueRange = 0f..80f,
        steps = 15,
        modifier = Modifier.fillMaxWidth()
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("0 dB", style = MaterialTheme.typography.labelSmall)
        Text("80 dB", style = MaterialTheme.typography.labelSmall)
    }

    Spacer(Modifier.height(16.dp))

    Text(
        text = "Ajusta el volumen hasta que apenas escuches el tono, luego confirma.",
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(Modifier.height(24.dp))

    Button(
        onClick = onConfirm,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Check, contentDescription = null)
        Text("  Confirmar umbral", fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ResultsContent(
    state: AudiogramUiState,
    onApply: () -> Unit,
    onRetake: () -> Unit
) {
    Text(
        text = "Resultados",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )

    Spacer(Modifier.height(16.dp))

    AudiogramChart(
        leftThresholds = state.leftThresholds,
        rightThresholds = state.rightThresholds,
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
    )

    Spacer(Modifier.height(16.dp))

    val freqLabels = (0 until 6).map { AudiogramCalibration.frequencyLabel(it) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Umbrales detectados", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Freq", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                Text("Izq", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                Text("Der", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            }

            for (i in 0 until 6) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(freqLabels[i], Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${state.leftThresholds[i].toInt()} dB",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "${state.rightThresholds[i].toInt()} dB",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(8.dp))

    if (state.isNormalLeft && state.isNormalRight) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Tu audición está dentro del rango normal. La calibración aplicará ajustes mínimos.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    Spacer(Modifier.height(24.dp))

    if (state.applied) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Calibración aplicada a tu ecualizador.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    } else {
        Button(
            onClick = onApply,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Aplicar a mi ecualizador")
        }
    }

    Spacer(Modifier.height(12.dp))

    OutlinedButton(
        onClick = onRetake,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Repetir test")
    }
}

@Composable
private fun AudiogramChart(
    leftThresholds: FloatArray,
    rightThresholds: FloatArray,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurface

    Canvas(modifier = modifier) {
        val chartLeft = 50f
        val chartTop = 20f
        val chartRight = size.width - 20f
        val chartBottom = size.height - 30f
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop

        for (i in 0..4) {
            val y = chartTop + chartHeight * i / 4f
            val db = i * 20
            drawLine(outlineColor, Offset(chartLeft, y), Offset(chartRight, y), strokeWidth = 1f)
            drawContext.canvas.nativeCanvas.drawText(
                "${db}",
                10f, y + 5f,
                android.graphics.Paint().apply {
                    color = textColor.hashCode()
                    textSize = 24f
                }
            )
        }

        val freqLabels = listOf("250", "500", "1K", "2K", "4K", "8K")
        for (i in 0 until 6) {
            val x = chartLeft + chartWidth * i / 5f
            drawLine(outlineColor, Offset(x, chartTop), Offset(x, chartBottom), strokeWidth = 1f)
            drawContext.canvas.nativeCanvas.drawText(
                freqLabels[i],
                x - 10f, chartBottom + 22f,
                android.graphics.Paint().apply {
                    color = textColor.hashCode()
                    textSize = 22f
                }
            )
        }

        drawThresholdLine(leftThresholds, chartLeft, chartTop, chartWidth, chartHeight, primaryColor, 3f)
        drawThresholdLine(rightThresholds, chartLeft, chartTop, chartWidth, chartHeight, errorColor, 3f)
    }
}

private fun DrawScope.drawThresholdLine(
    thresholds: FloatArray,
    chartLeft: Float,
    chartTop: Float,
    chartWidth: Float,
    chartHeight: Float,
    color: Color,
    strokeWidth: Float
) {
    val points = thresholds.mapIndexed { i, db ->
        val x = chartLeft + chartWidth * i / 5f
        val y = chartTop + chartHeight * (db.coerceIn(0f, 80f) / 80f)
        Offset(x, y)
    }

    for (i in 0 until points.size - 1) {
        drawLine(color, points[i], points[i + 1], strokeWidth = strokeWidth)
    }

    points.forEach { point ->
        drawCircle(color, radius = 6f, center = point)
    }
}
