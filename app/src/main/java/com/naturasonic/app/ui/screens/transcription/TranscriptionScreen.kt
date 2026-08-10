package com.naturasonic.app.ui.screens.transcription

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.naturasonic.app.R
import com.naturasonic.app.ui.theme.SubtitleBlack
import com.naturasonic.app.ui.theme.SubtitleWhite
import com.naturasonic.app.ui.theme.SubtitleYellow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptionScreen(
    onNavigateBack: () -> Unit,
    viewModel: TranscriptionViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_transcription)) },
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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))

            EngineSelector(
                selected = state.selectedEngine,
                isPremium = state.isPremium,
                onSelect = viewModel::selectEngine
            )

            Spacer(Modifier.height(16.dp))

            if (state.selectedEngine == SelectedEngine.WHISPER) {
                WhisperContent(
                    state = state,
                    onDownload = viewModel::downloadModel,
                    onToggleTranscription = viewModel::toggleTranscription,
                    onSetSubtitleColor = viewModel::setSubtitleColor
                )
            } else {
                VoskContent(
                    state = state,
                    onDownload = viewModel::downloadModel,
                    onToggleTranscription = viewModel::toggleTranscription,
                    onSetSubtitleColor = viewModel::setSubtitleColor
                )
            }

            Spacer(Modifier.height(24.dp))

            if (state.history.isNotEmpty()) {
                Text(
                    "Historial reciente",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.history) { entry ->
                        HistoryCard(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun EngineSelector(
    selected: SelectedEngine,
    isPremium: Boolean,
    onSelect: (SelectedEngine) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        FilterChip(
            selected = selected == SelectedEngine.VOSK,
            onClick = { onSelect(SelectedEngine.VOSK) },
            label = { Text(stringResource(R.string.transcription_engine_vosk)) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
        FilterChip(
            selected = selected == SelectedEngine.WHISPER,
            onClick = { onSelect(SelectedEngine.WHISPER) },
            label = { Text(stringResource(R.string.transcription_engine_whisper)) },
            enabled = isPremium,
            leadingIcon = if (!isPremium) {
                { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp)) }
            } else null,
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        )
    }
}

@Composable
private fun VoskContent(
    state: TranscriptionUiState,
    onDownload: () -> Unit,
    onToggleTranscription: () -> Unit,
    onSetSubtitleColor: (Int) -> Unit
) {
    if (!state.isModelReady && !state.isDownloading) {
        ModelDownloadCard(
            title = stringResource(R.string.transcription_download_model),
            sizeMb = "50",
            onDownload = onDownload
        )
    } else if (state.isDownloading) {
        DownloadProgressCard(progress = state.downloadProgress)
    } else {
        TranscriptionControls(
            text = state.currentText,
            colorIndex = state.subtitleColorIndex,
            isTranscribing = state.isTranscribing,
            onToggle = onToggleTranscription,
            onSetColor = onSetSubtitleColor
        )
    }
}

@Composable
private fun WhisperContent(
    state: TranscriptionUiState,
    onDownload: () -> Unit,
    onToggleTranscription: () -> Unit,
    onSetSubtitleColor: (Int) -> Unit
) {
    if (!state.whisperModelReady && !state.whisperDownloading) {
        ModelDownloadCard(
            title = stringResource(R.string.transcription_whisper_download),
            sizeMb = state.selectedWhisperModel.sizeMb.toString(),
            onDownload = onDownload
        )
    } else if (state.whisperDownloading) {
        DownloadProgressCard(progress = state.whisperDownloadProgress)
    } else {
        TranscriptionControls(
            text = state.currentText,
            colorIndex = state.subtitleColorIndex,
            isTranscribing = state.isTranscribing,
            onToggle = onToggleTranscription,
            onSetColor = onSetSubtitleColor
        )
    }
}

@Composable
private fun TranscriptionControls(
    text: String,
    colorIndex: Int,
    isTranscribing: Boolean,
    onToggle: () -> Unit,
    onSetColor: (Int) -> Unit
) {
    SubtitleDisplay(text = text, colorIndex = colorIndex, isTranscribing = isTranscribing)
    Spacer(Modifier.height(16.dp))
    SubtitleColorPicker(selected = colorIndex, onSelect = onSetColor)
    Spacer(Modifier.height(24.dp))
    TranscribeButton(isTranscribing = isTranscribing, onClick = onToggle)
}

@Composable
private fun ModelDownloadCard(title: String, sizeMb: String, onDownload: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.CloudDownload,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.transcription_model_size, sizeMb),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Descargar")
            }
        }
    }
}

@Composable
private fun DownloadProgressCard(progress: Float) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Descargando modelo de voz...", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SubtitleDisplay(text: String, colorIndex: Int, isTranscribing: Boolean) {
    val subtitleColor = when (colorIndex) {
        0 -> SubtitleWhite
        1 -> SubtitleBlack
        2 -> SubtitleYellow
        else -> SubtitleWhite
    }
    val bgColor = when (colorIndex) {
        0 -> Color.Black.copy(alpha = 0.8f)
        1 -> Color.White.copy(alpha = 0.9f)
        2 -> Color.Black.copy(alpha = 0.8f)
        else -> Color.Black.copy(alpha = 0.8f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(bgColor, RoundedCornerShape(16.dp))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        if (text.isNotBlank()) {
            Text(
                text = text,
                color = subtitleColor,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineMedium
            )
        } else if (isTranscribing) {
            Text(
                "Escuchando...",
                color = subtitleColor.copy(alpha = 0.5f),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                "Presiona el botón para transcribir",
                color = subtitleColor.copy(alpha = 0.3f),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SubtitleColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Color: ", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(8.dp))

        listOf(
            "Blanco" to SubtitleWhite,
            "Negro" to SubtitleBlack,
            "Amarillo" to SubtitleYellow
        ).forEachIndexed { index, (_, color) ->
            val isSelected = selected == index
            FilledIconButton(
                onClick = { onSelect(index) },
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary
                                    else color.copy(alpha = 0.5f)
                )
            ) {
                if (isSelected) {
                    Text("✓", color = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun TranscribeButton(isTranscribing: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isTranscribing) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(
            if (isTranscribing) Icons.Default.MicOff else Icons.Default.Mic,
            contentDescription = null
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (isTranscribing) stringResource(R.string.transcription_stop)
            else stringResource(R.string.transcription_start),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun HistoryCard(entry: com.naturasonic.app.data.local.entity.TranscriptionEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(entry.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    entry.engine,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                formatDate(entry.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
