package com.naturasonic.app.ui.screens.watchdog

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.naturasonic.app.audio.DspWatchdogManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DspWatchdogScreen(
    onNavigateBack: () -> Unit,
    viewModel: DspWatchdogViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Motor de audio") },
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
                text = "Supervisa el estado del motor de audio nativo y reinicia automaticamente ante fallos detectados",
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
                            "Watchdog activo",
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

            HealthIndicatorCard(state)

            Spacer(Modifier.height(16.dp))

            StatsCard(state)

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = viewModel::resetStats,
                    modifier = Modifier.weight(1f),
                    enabled = state.enabled
                ) {
                    Text("Resetear contadores")
                }

                Button(
                    onClick = viewModel::forceRestart,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Forzar reinicio")
                }
            }

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
                        text = "El watchdog verifica cada 2 segundos que el motor de audio " +
                            "esta respondiendo. Si detecta que el callback de audio no se " +
                            "ejecuta por mas de 500ms en 2 verificaciones consecutivas, " +
                            "reinicia el motor automaticamente y restaura todas las " +
                            "preferencias activas.\n\n" +
                            "Los reinicios internos del motor usan backoff exponencial " +
                            "(100ms a 5s) con un maximo de 5 intentos para evitar ciclos " +
                            "de reinicio descontrolados.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HealthIndicatorCard(state: DspWatchdogUiState) {
    val healthColor = when (state.healthLevel) {
        DspWatchdogManager.HealthLevel.GOOD -> Color(0xFF4CAF50)
        DspWatchdogManager.HealthLevel.WARNING -> Color(0xFFFFC107)
        DspWatchdogManager.HealthLevel.CRITICAL -> Color(0xFFF44336)
    }

    val healthLabel = when (state.healthLevel) {
        DspWatchdogManager.HealthLevel.GOOD -> "Saludable"
        DspWatchdogManager.HealthLevel.WARNING -> "Advertencia"
        DspWatchdogManager.HealthLevel.CRITICAL -> "Critico"
    }

    val engineLabel = if (state.isEngineRunning) "Motor activo" else "Motor detenido"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(healthColor),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.3f))
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = healthLabel,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = healthColor
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = engineLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatsCard(state: DspWatchdogUiState) {
    val callbackAgeMs = if (state.lastCallbackNs > 0) {
        (System.nanoTime() - state.lastCallbackNs) / 1_000_000
    } else 0L

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Estadisticas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(16.dp))

            StatRow("Ultimo callback", if (state.lastCallbackNs > 0) "${callbackAgeMs}ms" else "-")
            StatRow("Underruns / Overruns", "${state.xRunCount}")
            StatRow("Reinicios del motor", "${state.restartCount}")
            StatRow("Errores consecutivos", "${state.consecutiveErrors}")
            StatRow("Reinicios del watchdog", "${state.watchdogRestarts}")
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp
        )
    }
}
