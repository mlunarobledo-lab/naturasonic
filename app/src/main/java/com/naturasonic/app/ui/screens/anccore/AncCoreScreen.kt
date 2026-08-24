package com.naturasonic.app.ui.screens.anccore

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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AncCoreScreen(
    onNavigateBack: () -> Unit,
    viewModel: AncCoreViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cancelacion activa") },
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
            Text(
                text = "Reduce ruido de fondo sustrayendo frecuencias seleccionadas por inversion de fase",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Cancelacion activa",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Switch(
                            checked = state.enabled,
                            onCheckedChange = viewModel::setEnabled
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "Intensidad de cancelacion",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${(state.cancellationGain * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Slider(
                        value = state.cancellationGain,
                        onValueChange = viewModel::setCancellationGain,
                        valueRange = 0f..1f,
                        enabled = state.enabled
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            FilterCard(
                title = "Filtro pasa-bajas (graves)",
                description = "Reduce rumble de trafico, HVAC, zumbido electrico",
                enabled = state.lpEnabled,
                onEnabledChange = viewModel::setLpEnabled,
                cutoffHz = state.lpCutoff,
                onCutoffChange = viewModel::setLpCutoff,
                cutoffRange = 50f..500f,
                masterEnabled = state.enabled,
                formatCutoff = { "${it.roundToInt()} Hz" }
            )

            Spacer(Modifier.height(16.dp))

            FilterCard(
                title = "Filtro pasa-altos (agudos)",
                description = "Reduce siseo de ventiladores, electronica, viento",
                enabled = state.hpEnabled,
                onEnabledChange = viewModel::setHpEnabled,
                cutoffHz = state.hpCutoff,
                onCutoffChange = viewModel::setHpCutoff,
                cutoffRange = 2000f..8000f,
                masterEnabled = state.enabled,
                formatCutoff = { "${(it / 1000f).let { k -> if (k == k.toLong().toFloat()) "${k.toLong()} kHz" else "${"%.1f".format(k)} kHz" }}" }
            )

            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Como funciona",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Los filtros aislan bandas de frecuencia con ruido tipico " +
                            "(graves: trafico, ventilacion; agudos: siseo, viento). " +
                            "La senal aislada se invierte 180 grados y se resta del " +
                            "audio original — cancelacion destructiva.\n\n" +
                            "El rango vocal (200 Hz - 4 kHz) pasa intacto, " +
                            "preservando la inteligibilidad del habla.\n\n" +
                            "Nota: esta es reduccion de ruido por procesamiento de " +
                            "senal digital, no cancelacion acustica de ondas en el aire.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterCard(
    title: String,
    description: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    cutoffHz: Float,
    onCutoffChange: (Float) -> Unit,
    cutoffRange: ClosedFloatingPointRange<Float>,
    masterEnabled: Boolean,
    formatCutoff: (Float) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    enabled = masterEnabled
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "Frecuencia de corte: ${formatCutoff(cutoffHz)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Slider(
                value = cutoffHz,
                onValueChange = onCutoffChange,
                valueRange = cutoffRange,
                enabled = masterEnabled && enabled
            )
        }
    }
}
