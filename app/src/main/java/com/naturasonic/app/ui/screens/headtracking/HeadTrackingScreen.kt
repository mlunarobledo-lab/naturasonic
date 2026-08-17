package com.naturasonic.app.ui.screens.headtracking

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.naturasonic.app.audio.HeadTrackingState
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeadTrackingScreen(
    onNavigateBack: () -> Unit,
    viewModel: HeadTrackingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Enfoque direccional") },
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
                .verticalScroll(rememberScrollState())
        ) {
            if (!state.sensorAvailable) {
                UnavailableCard()
                Spacer(Modifier.height(24.dp))
                InfoCard()
                return@Column
            }

            EnableToggleCard(
                enabled = state.enabled,
                onToggle = viewModel::setEnabled
            )

            Spacer(Modifier.height(24.dp))

            if (state.enabled) {
                CompassIndicator(
                    azimuthDeg = state.azimuthDeg,
                    isActive = state.trackingState is HeadTrackingState.Active
                )

                Spacer(Modifier.height(16.dp))

                CalibrationCard(
                    trackingState = state.trackingState,
                    onCalibrate = viewModel::calibrate
                )

                Spacer(Modifier.height(24.dp))

                SensitivityCard(
                    sensitivity = state.sensitivity,
                    onSensitivityChange = viewModel::setSensitivity
                )

                Spacer(Modifier.height(16.dp))

                AngleReadout(state.azimuthDeg, state.pitchDeg)
            }

            Spacer(Modifier.height(24.dp))

            InfoCard()
        }
    }
}

@Composable
private fun EnableToggleCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.MyLocation,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Enfoque direccional",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (enabled) "Activo" else "Desactivado",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun CompassIndicator(azimuthDeg: Float, isActive: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val onSurface = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f

            drawCircle(
                color = outline.copy(alpha = 0.3f),
                radius = radius,
                center = center,
                style = Stroke(width = 2f)
            )

            drawCircle(
                color = outline.copy(alpha = 0.15f),
                radius = radius * 0.66f,
                center = center,
                style = Stroke(width = 1f)
            )

            drawCircle(
                color = outline.copy(alpha = 0.15f),
                radius = radius * 0.33f,
                center = center,
                style = Stroke(width = 1f)
            )

            drawLine(
                color = outline.copy(alpha = 0.2f),
                start = Offset(center.x, center.y - radius),
                end = Offset(center.x, center.y + radius),
                strokeWidth = 1f
            )
            drawLine(
                color = outline.copy(alpha = 0.2f),
                start = Offset(center.x - radius, center.y),
                end = Offset(center.x + radius, center.y),
                strokeWidth = 1f
            )

            drawCircle(
                color = onSurface.copy(alpha = 0.5f),
                radius = 4f,
                center = center
            )

            if (isActive) {
                val angleRad = Math.toRadians(-azimuthDeg.toDouble() + 90.0)
                val pointerLength = radius * 0.85f
                val endX = center.x + pointerLength * cos(angleRad).toFloat()
                val endY = center.y - pointerLength * sin(angleRad).toFloat()

                drawLine(
                    color = primary,
                    start = center,
                    end = Offset(endX, endY),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round
                )

                drawCircle(
                    color = primary,
                    radius = 8f,
                    center = Offset(endX, endY)
                )
            }
        }
    }
}

@Composable
private fun CalibrationCard(
    trackingState: HeadTrackingState,
    onCalibrate: () -> Unit
) {
    val isCalibrating = trackingState is HeadTrackingState.Calibrating

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (isCalibrating) "Apunta el telefono hacia la fuente de sonido"
                else "Pulsa para recalibrar el centro de atencion",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onCalibrate,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Adjust, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isCalibrating) "Calibrando..." else "Calibrar centro")
            }
        }
    }
}

@Composable
private fun SensitivityCard(sensitivity: Float, onSensitivityChange: (Float) -> Unit) {
    Text(
        "Sensibilidad",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(4.dp))
    Text(
        "Controla cuanto se atenuan las frecuencias al girar la cabeza",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Sutil", style = MaterialTheme.typography.labelSmall)
        Slider(
            value = sensitivity,
            onValueChange = onSensitivityChange,
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        Text("Intenso", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AngleReadout(azimuthDeg: Float, pitchDeg: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Azimut", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${azimuthDeg.toInt()}°",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Inclinacion", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${pitchDeg.toInt()}°",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun UnavailableCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Tu dispositivo no tiene sensor de rotacion. " +
                    "El enfoque direccional requiere un sensor de movimiento (giroscopio + acelerometro).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
                "Como funciona?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "El enfoque direccional usa los sensores de movimiento de tu telefono " +
                    "para detectar hacia donde miras. Cuando giras la cabeza, las frecuencias " +
                    "altas se atenuan fuera del eje de atencion, simulando el filtrado natural " +
                    "de la cabeza humana. Esto mejora la claridad de los sonidos frente a ti " +
                    "en entornos ruidosos. Calibra el centro apuntando hacia la fuente de sonido.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
