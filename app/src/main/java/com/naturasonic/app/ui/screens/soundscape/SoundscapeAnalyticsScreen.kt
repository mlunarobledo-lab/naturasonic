package com.naturasonic.app.ui.screens.soundscape

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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

private val ColorSafe = Color(0xFF4CAF50)
private val ColorCaution = Color(0xFFFFC107)
private val ColorDanger = Color(0xFFF44336)
private val ColorDbaLine = Color(0xFF2196F3)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundscapeAnalyticsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SoundscapeViewModel = hiltViewModel()
) {
    val snapshot by viewModel.snapshot.collectAsState()
    val dbaHistory by viewModel.dbaHistory.collectAsState()
    val dosimetryEnabled by viewModel.dosimetryEnabled.collectAsState()
    val calibrationOffset by viewModel.calibrationOffset.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paisaje sonoro") },
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

            EnableToggle(dosimetryEnabled, viewModel::setDosimetryEnabled)

            if (dosimetryEnabled) {
                InstantDbaCard(snapshot.instantDba, snapshot.leq, snapshot.peakDba)

                if (dbaHistory.size >= 2) {
                    DbaTrendCard(dbaHistory)
                }

                DoseCard(
                    label = "Dosis OSHA",
                    dosePercent = snapshot.doseOshaPercent,
                    twa = snapshot.twaOsha,
                    limit = "90 dBA / 8h",
                    exchangeRate = "5 dB"
                )

                DoseCard(
                    label = "Dosis NIOSH",
                    dosePercent = snapshot.doseNioshPercent,
                    twa = snapshot.twaNiosh,
                    limit = "85 dBA / 8h",
                    exchangeRate = "3 dB"
                )

                CalibrationCard(calibrationOffset, viewModel::setCalibrationOffset)

                SessionInfoCard(snapshot.elapsedMinutes)

                DisclaimerCard()
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EnableToggle(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled)
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Dosimetria activa", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Mide ruido ambiental en dBA", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun InstantDbaCard(dba: Float, leq: Float, peak: Float) {
    val dbaColor = when {
        dba < 70f -> ColorSafe
        dba < 85f -> ColorCaution
        else -> ColorDanger
    }
    val levelText = when {
        dba < 50f -> "Silencioso"
        dba < 70f -> "Moderado"
        dba < 85f -> "Alto"
        dba < 100f -> "Muy alto"
        else -> "Peligroso"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (dba > -100f) "%.1f".format(dba) else "--",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 56.sp),
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = dbaColor
            )
            Text(
                "dBA",
                style = MaterialTheme.typography.titleLarge,
                color = dbaColor.copy(alpha = 0.7f)
            )
            Text(
                levelText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = dbaColor
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricChip("Leq", if (leq > -100f) "%.1f".format(leq) else "--", "dBA")
                MetricChip("Pico", if (peak > -100f) "%.1f".format(peak) else "--", "dBA")
            }
        }
    }
}

@Composable
private fun MetricChip(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(2.dp))
            Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DbaTrendCard(history: List<Float>) {
    val outline = MaterialTheme.colorScheme.outline
    val dangerZone = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Tendencia dBA", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Ultimos ${history.size} puntos (${history.size / 2}s)",
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
                val pad = 4f

                val minDba = 30f
                val maxDba = 110f
                val range = maxDba - minDba

                // 85 dBA danger threshold line
                val dangerY = h - ((85f - minDba) / range) * (h - pad * 2) - pad
                drawRect(
                    color = dangerZone,
                    topLeft = Offset(0f, 0f),
                    size = Size(w, dangerY)
                )
                drawLine(
                    color = ColorDanger.copy(alpha = 0.4f),
                    start = Offset(0f, dangerY),
                    end = Offset(w, dangerY),
                    strokeWidth = 1.5f
                )

                // Grid lines at 50, 70, 90 dBA
                for (gridDb in listOf(50f, 70f, 90f)) {
                    val y = h - ((gridDb - minDba) / range) * (h - pad * 2) - pad
                    drawLine(
                        color = outline.copy(alpha = 0.15f),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f
                    )
                }

                // dBA trend line
                if (history.size >= 2) {
                    val path = Path()
                    val stepX = (w - pad * 2) / (history.size - 1).coerceAtLeast(1)

                    history.forEachIndexed { i, dba ->
                        val x = pad + i * stepX
                        val normalized = ((dba - minDba) / range).coerceIn(0f, 1f)
                        val y = h - normalized * (h - pad * 2) - pad

                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }

                    drawPath(
                        path = path,
                        color = ColorDbaLine,
                        style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("30 dBA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("85 dBA", style = MaterialTheme.typography.labelSmall, color = ColorDanger.copy(alpha = 0.6f))
                Text("110 dBA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DoseCard(
    label: String,
    dosePercent: Float,
    twa: Float,
    limit: String,
    exchangeRate: String
) {
    val doseColor = when {
        dosePercent < 50f -> ColorSafe
        dosePercent < 100f -> ColorCaution
        else -> ColorDanger
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "%.1f%%".format(dosePercent),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = doseColor
                )
            }
            Spacer(Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { (dosePercent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = doseColor,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Limite: $limit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (twa > 0f) {
                    Text(
                        "TWA: %.1f dBA".format(twa),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                "Tasa de intercambio: $exchangeRate",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun CalibrationCard(offset: Float, onOffsetChange: (Float) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Calibracion del microfono", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Offset: %.0f dB".format(offset),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace
            )
            Slider(
                value = offset,
                onValueChange = onOffsetChange,
                valueRange = 60f..120f,
                steps = 59,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Los microfonos de smartphone no estan calibrados. " +
                    "Ajusta el offset comparando con un sonometro de referencia.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SessionInfoCard(elapsedMinutes: Float) {
    val hours = (elapsedMinutes / 60).toInt()
    val mins = (elapsedMinutes % 60).toInt()
    val secs = ((elapsedMinutes * 60) % 60).toInt()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Tiempo de medicion", style = MaterialTheme.typography.bodyLarge)
            Text(
                "%02d:%02d:%02d".format(hours, mins, secs),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DisclaimerCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Aviso PSAP",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "NaturaSonic es un amplificador de sonido personal (PSAP), " +
                    "no un dispositivo medico. Las mediciones de dBA son aproximadas " +
                    "y dependen de la calibracion del microfono de tu dispositivo. " +
                    "No sustituyen un sonometro profesional certificado. " +
                    "Los umbrales OSHA/NIOSH se muestran como referencia informativa.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
