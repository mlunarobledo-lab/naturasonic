package com.naturasonic.app.ui.screens.audiosharing

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.naturasonic.app.bluetooth.BroadcastCapability
import com.naturasonic.app.bluetooth.BroadcastState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSharingScreen(
    onNavigateBack: () -> Unit,
    viewModel: AudioSharingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var broadcastCode by remember { mutableStateOf("") }
    var showCode by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compartir audio") },
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
            BroadcastStatusCard(state.broadcastState, state.capability)

            Spacer(Modifier.height(24.dp))

            when {
                state.capability == BroadcastCapability.SUPPORTED -> {
                    BroadcastControls(
                        broadcastState = state.broadcastState,
                        broadcastCode = broadcastCode,
                        showCode = showCode,
                        onCodeChange = { broadcastCode = it },
                        onToggleShowCode = { showCode = !showCode },
                        onToggleBroadcast = { viewModel.toggleBroadcast(broadcastCode) }
                    )
                }
                state.capability == BroadcastCapability.BLUETOOTH_OFF -> {
                    RetryCard(
                        message = "Activa Bluetooth para compartir audio.",
                        onRetry = viewModel::retryConnection
                    )
                }
                state.capability == BroadcastCapability.PROFILE_UNAVAILABLE ||
                    state.capability == BroadcastCapability.HARDWARE_NOT_SUPPORTED -> {
                    UnsupportedCard(
                        reason = "Tu dispositivo no soporta broadcast LE Audio (Auracast). " +
                            "Se requiere un chipset Bluetooth 5.2 o superior con soporte LE Audio."
                    )
                }
                state.capability == BroadcastCapability.API_TOO_LOW -> {
                    UnsupportedCard(
                        reason = "La transmisión de audio requiere Android 13 o superior. " +
                            "Tu dispositivo usa una versión anterior."
                    )
                }
                else -> {
                    UnsupportedCard(reason = "Bluetooth no disponible en este dispositivo.")
                }
            }

            Spacer(Modifier.height(24.dp))

            InfoCard()
        }
    }
}

@Composable
private fun BroadcastStatusCard(
    broadcastState: BroadcastState,
    capability: BroadcastCapability
) {
    val isActive = broadcastState is BroadcastState.Active
    val isTransitioning = broadcastState is BroadcastState.Starting ||
        broadcastState is BroadcastState.Stopping

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isActive -> MaterialTheme.colorScheme.primaryContainer
                broadcastState is BroadcastState.Error -> MaterialTheme.colorScheme.errorContainer
                capability != BroadcastCapability.SUPPORTED -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isTransitioning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            if (isActive) Icons.Default.CellTower
                            else Icons.AutoMirrored.Filled.BluetoothSearching,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = when {
                                isActive -> MaterialTheme.colorScheme.onPrimaryContainer
                                broadcastState is BroadcastState.Error ->
                                    MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = when (broadcastState) {
                            is BroadcastState.Idle -> "Listo para transmitir"
                            is BroadcastState.Starting -> "Iniciando transmisión..."
                            is BroadcastState.Active -> "Transmitiendo"
                            is BroadcastState.Stopping -> "Deteniendo..."
                            is BroadcastState.Error -> "Error"
                            is BroadcastState.Unsupported -> "No disponible"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isActive -> MaterialTheme.colorScheme.onPrimaryContainer
                            broadcastState is BroadcastState.Error ->
                                MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            when (broadcastState) {
                is BroadcastState.Active -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "ID de canal: ${broadcastState.broadcastId}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                is BroadcastState.Error -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        broadcastState.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                }
                is BroadcastState.Unsupported -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        broadcastState.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun BroadcastControls(
    broadcastState: BroadcastState,
    broadcastCode: String,
    showCode: Boolean,
    onCodeChange: (String) -> Unit,
    onToggleShowCode: () -> Unit,
    onToggleBroadcast: () -> Unit
) {
    val isActive = broadcastState is BroadcastState.Active
    val isTransitioning = broadcastState is BroadcastState.Starting ||
        broadcastState is BroadcastState.Stopping

    Text(
        "Clave de seguridad (opcional)",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(8.dp))

    OutlinedTextField(
        value = broadcastCode,
        onValueChange = { if (it.length <= 16) onCodeChange(it) },
        label = { Text("Código de acceso") },
        placeholder = { Text("4-16 caracteres") },
        enabled = !isActive && !isTransitioning,
        singleLine = true,
        visualTransformation = if (showCode) VisualTransformation.None
            else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = onToggleShowCode) {
                Icon(
                    if (showCode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (showCode) "Ocultar" else "Mostrar"
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
        supportingText = {
            Text("Los receptores necesitarán este código para escuchar tu transmisión")
        }
    )

    Spacer(Modifier.height(24.dp))

    Button(
        onClick = onToggleBroadcast,
        enabled = !isTransitioning &&
            (broadcastCode.isEmpty() || broadcastCode.length >= 4),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = if (isActive) ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error
        ) else ButtonDefaults.buttonColors()
    ) {
        Icon(
            Icons.Default.CellTower,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (isActive) "Detener transmisión" else "Iniciar transmisión",
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun UnsupportedCard(reason: String) {
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
                reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RetryCard(message: String, onRetry: () -> Unit) {
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
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Reintentar")
            }
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
                "¿Cómo funciona?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "La transmisión LE Audio (Auracast) envía el audio procesado por NaturaSonic " +
                    "a múltiples dispositivos receptores compatibles. Los receptores " +
                    "necesitan un dispositivo con Bluetooth 5.2+ y Android 13 o superior. " +
                    "Si configuras una clave de seguridad, solo quienes la conozcan " +
                    "podrán escuchar tu transmisión.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
