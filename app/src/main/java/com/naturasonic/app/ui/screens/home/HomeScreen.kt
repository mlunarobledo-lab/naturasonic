package com.naturasonic.app.ui.screens.home

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Nature
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Doorbell
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.naturasonic.app.R
import com.naturasonic.app.data.local.entity.AlertSoundClass
import com.naturasonic.app.data.local.entity.AudioMode
import com.naturasonic.app.detection.DetectedAlert
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToTranscription: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAlertHistory: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showHealthNote by remember { mutableStateOf(false) }
    var showAlertCard by remember { mutableStateOf(false) }
    var currentAlert by remember { mutableStateOf<DetectedAlert?>(null) }

    LaunchedEffect(state.latestAlert?.timestamp) {
        state.latestAlert?.let {
            currentAlert = it
            showAlertCard = true
            delay(5000L)
            showAlertCard = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "NaturaSonic",
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                actions = {
                    if (state.isEcoActive) {
                        Text(
                            "ECO",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            imageVector = when {
                                state.isBatteryCharging -> Icons.Default.BatteryChargingFull
                                state.batteryLevel > 80 -> Icons.Default.BatteryFull
                                state.batteryLevel > 40 -> Icons.Default.BatteryStd
                                state.batteryLevel > 15 -> Icons.Default.Battery2Bar
                                else -> Icons.Default.BatteryAlert
                            },
                            contentDescription = null,
                            tint = when {
                                state.isBatteryCharging -> MaterialTheme.colorScheme.primary
                                state.batteryLevel <= 15 -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "${state.batteryLevel}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.batteryLevel <= 15) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = if (state.isBluetoothConnected)
                            Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                        contentDescription = null,
                        tint = if (state.isBluetoothConnected)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.nav_settings))
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            PowerButton(
                isActive = state.isAudioActive,
                onClick = viewModel::toggleAudio
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = if (state.isAudioActive) "Amplificación activa" else "Toca para activar",
                style = MaterialTheme.typography.titleMedium,
                color = if (state.isAudioActive) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (state.isAudioActive && state.isAlertDetectionActive) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                MaterialTheme.colorScheme.tertiary,
                                CircleShape
                            )
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Detección de alertas activa",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            AnimatedVisibility(
                visible = showAlertCard,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                currentAlert?.let { alert ->
                    SoundAlertCard(alert = alert)
                }
            }

            Spacer(Modifier.height(16.dp))

            VolumeControl(
                volume = state.volume,
                isLocked = state.isVolumeLocked,
                onVolumeChange = viewModel::setVolume
            )

            Spacer(Modifier.height(32.dp))

            Text(
                "Modo de escucha",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            ModeSelector(
                currentMode = state.currentMode,
                isPremium = state.isPremium,
                onModeSelected = viewModel::setMode
            )

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionCard(
                    icon = Icons.Default.Subtitles,
                    label = stringResource(R.string.nav_transcription),
                    onClick = onNavigateToTranscription,
                    modifier = Modifier.weight(1f)
                )
                ActionCard(
                    icon = Icons.Default.History,
                    label = "Historial alertas",
                    onClick = onNavigateToAlertHistory,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            ActionCard(
                icon = Icons.Default.LocalHospital,
                label = stringResource(R.string.health_note_title),
                onClick = { showHealthNote = true },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            FilledTonalButton(
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("geo:0,0?q=otorrinolaringólogo+audiólogo+cerca+de+mí")
                    )
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        val webIntent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://www.google.com/maps/search/otorrinolaringólogo+audiólogo+cerca+de+mí")
                        )
                        context.startActivity(webIntent)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(Icons.Default.LocalHospital, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Buscar especialista cercano")
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showHealthNote) {
        AlertDialog(
            onDismissRequest = { showHealthNote = false },
            icon = { Icon(Icons.Default.Hearing, contentDescription = null) },
            title = { Text(stringResource(R.string.health_note_title)) },
            text = { Text(stringResource(R.string.health_note_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showHealthNote = false
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("geo:0,0?q=otorrinolaringólogo+audiólogo+cerca+de+mí")
                    )
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        val webIntent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://www.google.com/maps/search/otorrinolaringólogo+audiólogo+cerca+de+mí")
                        )
                        context.startActivity(webIntent)
                    }
                }) {
                    Text(stringResource(R.string.health_note_find_specialist))
                }
            },
            dismissButton = {
                TextButton(onClick = { showHealthNote = false }) {
                    Text("Cerrar")
                }
            }
        )
    }

    if (state.showVolumeWarning) {
        AlertDialog(
            onDismissRequest = viewModel::dismissVolumeWarning,
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.volume_warning_title)) },
            text = { Text(stringResource(R.string.volume_warning_body)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissVolumeWarning) {
                    Text("Entendido")
                }
            }
        )
    }
}

@Composable
private fun PowerButton(isActive: Boolean, onClick: () -> Unit) {
    val containerColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.surfaceVariant,
        label = "power_color"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.onPrimary
                      else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "power_content"
    )

    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(120.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Icon(
            Icons.Default.Power,
            contentDescription = if (isActive) "Desactivar" else "Activar",
            modifier = Modifier.size(56.dp)
        )
    }
}

@Composable
private fun VolumeControl(volume: Float, isLocked: Boolean, onVolumeChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Volumen", style = MaterialTheme.typography.titleMedium)
            Text(
                "${(volume * 100).toInt()}%",
                style = MaterialTheme.typography.bodyLarge,
                color = if (isLocked) MaterialTheme.colorScheme.error
                       else MaterialTheme.colorScheme.onSurface
            )
        }
        Slider(
            value = volume,
            onValueChange = onVolumeChange,
            enabled = !isLocked,
            modifier = Modifier.fillMaxWidth()
        )
        if (isLocked) {
            Text(
                "Volumen limitado a 70 dB por protección auditiva",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ModeSelector(
    currentMode: AudioMode,
    isPremium: Boolean,
    onModeSelected: (AudioMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ModeChip(
            icon = Icons.Default.Hearing,
            label = "Conversación",
            isSelected = currentMode == AudioMode.CONVERSATION,
            onClick = { onModeSelected(AudioMode.CONVERSATION) },
            modifier = Modifier.weight(1f)
        )
        ModeChip(
            icon = Icons.Default.MusicNote,
            label = "Entretenim.",
            isSelected = currentMode == AudioMode.ENTERTAINMENT,
            onClick = { onModeSelected(AudioMode.ENTERTAINMENT) },
            modifier = Modifier.weight(1f)
        )
        ModeChip(
            icon = Icons.Default.Nature,
            label = "Exterior",
            isSelected = currentMode == AudioMode.OUTDOOR,
            onClick = { onModeSelected(AudioMode.OUTDOOR) },
            modifier = Modifier.weight(1f)
        )
        ModeChip(
            icon = Icons.Default.HeadsetMic,
            label = "Remoto",
            isSelected = currentMode == AudioMode.REMOTE_MIC,
            enabled = isPremium,
            onClick = { onModeSelected(AudioMode.REMOTE_MIC) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ModeChip(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        onClick = { if (enabled) onClick() },
        modifier = modifier.height(80.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp),
                tint = if (!enabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                       else if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
                color = if (!enabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(80.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SoundAlertCard(alert: DetectedAlert) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = alert.soundClass.icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    alert.soundClass.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Confianza: ${(alert.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

internal fun AlertSoundClass.icon(): ImageVector = when (this) {
    AlertSoundClass.SIREN -> Icons.Default.Campaign
    AlertSoundClass.DOORBELL -> Icons.Default.Doorbell
    AlertSoundClass.BABY_CRY -> Icons.Default.ChildCare
    AlertSoundClass.SMOKE_ALARM -> Icons.Default.LocalFireDepartment
    AlertSoundClass.CAR_HORN -> Icons.Default.DirectionsCar
    AlertSoundClass.GLASS_BREAK -> Icons.Default.Warning
    AlertSoundClass.DOG_BARK -> Icons.Default.Pets
}
