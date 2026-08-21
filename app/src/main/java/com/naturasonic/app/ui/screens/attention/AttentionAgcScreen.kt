package com.naturasonic.app.ui.screens.attention

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.naturasonic.app.audio.AttentionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttentionAgcScreen(
    onNavigateBack: () -> Unit,
    viewModel: AttentionAgcViewModel = hiltViewModel()
) {
    val enabled by viewModel.enabled.collectAsState()
    val speechBoost by viewModel.speechBoostDb.collectAsState()
    val alertAttenuation by viewModel.alertAttenuationDb.collectAsState()
    val attentionState by viewModel.attentionState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajuste inteligente") },
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
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            EnableToggleCard(enabled = enabled, onToggle = viewModel::setEnabled)

            if (enabled) {
                Spacer(Modifier.height(16.dp))

                StateIndicatorCard(state = attentionState)

                Spacer(Modifier.height(16.dp))

                SpeechBoostCard(
                    boostDb = speechBoost,
                    onBoostChange = viewModel::setSpeechBoostDb
                )

                Spacer(Modifier.height(16.dp))

                AlertAttenuationCard(
                    attenuationDb = alertAttenuation,
                    onAttenuationChange = viewModel::setAlertAttenuationDb
                )
            }

            Spacer(Modifier.height(16.dp))

            DisclaimerCard()

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun EnableToggleCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Ajuste inteligente",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Adapta la ecualizacion automaticamente segun lo que el audio necesita",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun StateIndicatorCard(state: AttentionState) {
    val stateColor by animateColorAsState(
        targetValue = when (state) {
            AttentionState.IDLE -> MaterialTheme.colorScheme.outline
            AttentionState.SPEECH -> Color(0xFF4CAF50)
            AttentionState.ALERT -> Color(0xFFF44336)
        },
        label = "stateColor"
    )

    val stateLabel = when (state) {
        AttentionState.IDLE -> "En espera"
        AttentionState.SPEECH -> "Voz detectada"
        AttentionState.ALERT -> "Alerta detectada"
    }

    val stateDescription = when (state) {
        AttentionState.IDLE -> "Sin ajustes activos — escuchando"
        AttentionState.SPEECH -> "Reforzando frecuencias de voz (1-4 kHz)"
        AttentionState.ALERT -> "Atenuando ruido de fondo para destacar la alerta"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = stateColor.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(16.dp)) {
                        drawCircle(color = stateColor)
                    }
                }
            }
            Column {
                Text(
                    stateLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stateDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SpeechBoostCard(boostDb: Float, onBoostChange: (Float) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Refuerzo de voz",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Cuanto se amplifican las frecuencias del habla (1-4 kHz) cuando se detecta voz",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Slider(
                    value = boostDb,
                    onValueChange = onBoostChange,
                    valueRange = 1f..6f,
                    steps = 9,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${String.format("%.1f", boostDb)} dB",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 8.dp),
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun AlertAttenuationCard(attenuationDb: Float, onAttenuationChange: (Float) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Atenuacion de fondo",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Cuanto se reduce el ruido de fondo cuando se detecta una alerta critica",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Slider(
                    value = attenuationDb,
                    onValueChange = onAttenuationChange,
                    valueRange = 1f..8f,
                    steps = 13,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "-${String.format("%.1f", attenuationDb)} dB",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 8.dp),
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun DisclaimerCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Text(
            "El ajuste inteligente usa los motores de transcripcion y deteccion de alertas " +
                    "para adaptar la ecualizacion en tiempo real. Funciona mejor cuando ambos " +
                    "motores estan activos. Este dispositivo es un PSAP, no un dispositivo medico.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
            textAlign = TextAlign.Center
        )
    }
}
