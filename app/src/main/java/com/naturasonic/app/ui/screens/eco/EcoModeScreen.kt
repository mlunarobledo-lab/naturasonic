package com.naturasonic.app.ui.screens.eco

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
import com.naturasonic.app.battery.EcoReason

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcoModeScreen(
    onNavigateBack: () -> Unit,
    viewModel: EcoViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Modo eco") },
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
            StatusCard(
                isActive = state.isEcoActive,
                reason = state.reason,
                batteryLevel = state.battery.level,
                isCharging = state.battery.isCharging
            )

            Spacer(Modifier.height(24.dp))

            Text(
                "Activación manual",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Forzar modo eco ahora",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = state.manualEnabled,
                    onCheckedChange = viewModel::setManualEnabled
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "Activación automática",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Activar al bajar de batería",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = state.autoActivateEnabled,
                    onCheckedChange = viewModel::setAutoActivateEnabled
                )
            }

            if (state.autoActivateEnabled) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Umbral de batería: ${state.threshold}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = state.threshold.toFloat(),
                    onValueChange = { viewModel.setThreshold(it.toInt()) },
                    valueRange = 5f..50f,
                    steps = 8,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("5%", style = MaterialTheme.typography.labelSmall)
                    Text("50%", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "¿Qué hace el modo eco?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Reduce la frecuencia de detección de alertas (de 1s a 3s) y " +
                            "la velocidad de actualización de la transcripción (de 150ms a 500ms). " +
                            "El audio y la amplificación siguen funcionando con normalidad.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    isActive: Boolean,
    reason: EcoReason,
    batteryLevel: Int,
    isCharging: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.tertiaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (isActive) "Modo eco activo" else "Modo eco inactivo",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive)
                        MaterialTheme.colorScheme.onTertiaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                buildString {
                    append("Batería: $batteryLevel%")
                    if (isCharging) append(" (cargando)")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (isActive)
                    MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isActive) {
                Spacer(Modifier.height(4.dp))
                Text(
                    when (reason) {
                        EcoReason.MANUAL -> "Activado manualmente"
                        EcoReason.AUTO_BATTERY -> "Activado por batería baja"
                        EcoReason.INACTIVE -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}
