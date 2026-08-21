package com.naturasonic.app.ui.screens.voicehealth

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.naturasonic.app.audio.VoiceMetricsData

private val ColorNormal = Color(0xFF4CAF50)
private val ColorAlert = Color(0xFFFFC107)
private val ColorCritical = Color(0xFFF44336)
private val ColorJitterLine = Color(0xFF2196F3)
private val ColorShimmerLine = Color(0xFFFF9800)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceHealthScreen(
    onNavigateBack: () -> Unit,
    viewModel: VoiceHealthViewModel = hiltViewModel()
) {
    val metrics by viewModel.metrics.collectAsState()
    val history by viewModel.history.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Salud vocal") },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            PitchIndicator(metrics)

            MetricBar(
                label = "Jitter",
                value = metrics.jitterPercent,
                unit = "%",
                normalMax = 1f,
                alertMax = 2f,
                maxValue = 5f,
                isVoiced = metrics.isVoiced
            )

            MetricBar(
                label = "Shimmer",
                value = metrics.shimmerPercent,
                unit = "%",
                normalMax = 3f,
                alertMax = 6f,
                maxValue = 15f,
                isVoiced = metrics.isVoiced
            )

            if (history.isNotEmpty()) {
                TrendCard(history)
            }

            LegendCard()

            InfoCard()

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PitchIndicator(metrics: VoiceMetricsData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (metrics.isVoiced)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (metrics.isVoiced) Icons.Default.Mic else Icons.Default.MicOff,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (metrics.isVoiced)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    if (metrics.isVoiced) "Voz detectada" else "Sin voz detectada",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (metrics.isVoiced)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (metrics.isVoiced) {
                    Text(
                        "Frecuencia fundamental: ${metrics.pitchHz.toInt()} Hz",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricBar(
    label: String,
    value: Float,
    unit: String,
    normalMax: Float,
    alertMax: Float,
    maxValue: Float,
    isVoiced: Boolean
) {
    val barColor = when {
        !isVoiced -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        value < normalMax -> ColorNormal
        value < alertMax -> ColorAlert
        else -> ColorCritical
    }
    val statusText = when {
        !isVoiced -> "—"
        value < normalMax -> "Normal"
        value < alertMax -> "Alerta"
        else -> "Critico"
    }
    val statusColor = when {
        !isVoiced -> MaterialTheme.colorScheme.onSurfaceVariant
        value < normalMax -> ColorNormal
        value < alertMax -> ColorAlert
        else -> ColorCritical
    }
    val alpha = if (isVoiced) 1f else 0.4f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (isVoiced) "%.2f".format(value) else "—",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = statusColor.copy(alpha = alpha)
                    )
                    if (isVoiced) {
                        Spacer(Modifier.width(2.dp))
                        Text(
                            unit,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
            ) {
                val barWidth = size.width
                val barHeight = size.height
                val radius = CornerRadius(barHeight / 2f)

                drawRoundRect(
                    color = trackColor,
                    size = Size(barWidth, barHeight),
                    cornerRadius = radius
                )

                val fillFraction = (value / maxValue).coerceIn(0f, 1f)
                if (fillFraction > 0f) {
                    drawRoundRect(
                        color = barColor,
                        size = Size(barWidth * fillFraction, barHeight),
                        cornerRadius = radius
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                statusText,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor.copy(alpha = alpha)
            )
        }
    }
}

@Composable
private fun TrendCard(history: List<VoiceMetricsData>) {
    val outline = MaterialTheme.colorScheme.outline

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Tendencia",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Ultimos ${history.size} puntos de medicion",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                val w = size.width
                val h = size.height
                val padding = 4f

                val maxJitter = 5f
                val maxShimmer = 15f

                drawLine(
                    color = outline.copy(alpha = 0.2f),
                    start = Offset(0f, h * 0.5f),
                    end = Offset(w, h * 0.5f),
                    strokeWidth = 1f
                )

                if (history.size >= 2) {
                    val jitterPath = Path()
                    val shimmerPath = Path()
                    val stepX = (w - padding * 2) / (history.size - 1).coerceAtLeast(1)

                    history.forEachIndexed { i, point ->
                        val x = padding + i * stepX
                        val jY = h - (point.jitterPercent / maxJitter).coerceIn(0f, 1f) * (h - padding * 2) - padding
                        val sY = h - (point.shimmerPercent / maxShimmer).coerceIn(0f, 1f) * (h - padding * 2) - padding

                        if (i == 0) {
                            jitterPath.moveTo(x, jY)
                            shimmerPath.moveTo(x, sY)
                        } else {
                            jitterPath.lineTo(x, jY)
                            shimmerPath.lineTo(x, sY)
                        }
                    }

                    drawPath(
                        path = jitterPath,
                        color = ColorJitterLine,
                        style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                    )
                    drawPath(
                        path = shimmerPath,
                        color = ColorShimmerLine,
                        style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                LegendDot(ColorJitterLine, "Jitter")
                Spacer(Modifier.width(24.dp))
                LegendDot(ColorShimmerLine, "Shimmer")
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(8.dp)) {
            drawCircle(color = color)
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun LegendCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Umbrales de referencia",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            ThresholdRow(ColorNormal, "Normal", "Jitter < 1%  ·  Shimmer < 3%")
            ThresholdRow(ColorAlert, "Alerta", "Jitter 1-2%  ·  Shimmer 3-6%")
            ThresholdRow(ColorCritical, "Critico", "Jitter > 2%  ·  Shimmer > 6%")
        }
    }
}

@Composable
private fun ThresholdRow(color: Color, label: String, detail: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(10.dp)) {
            drawRoundRect(color = color, cornerRadius = CornerRadius(2f))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.width(60.dp)
        )
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Que significan estas metricas?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Jitter mide la variabilidad del periodo de tu voz — " +
                    "cuanto cambia la velocidad a la que vibran tus cuerdas vocales " +
                    "de un ciclo a otro. Valores altos indican inestabilidad vocal.\n\n" +
                    "Shimmer mide la variabilidad de volumen entre ciclos " +
                    "consecutivos de tu voz. Valores elevados pueden indicar " +
                    "fatiga vocal o tension en las cuerdas vocales.\n\n" +
                    "Estas metricas son indicativas y no constituyen un diagnostico " +
                    "medico. Consulta a un fonoaudiologo para evaluacion profesional.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
