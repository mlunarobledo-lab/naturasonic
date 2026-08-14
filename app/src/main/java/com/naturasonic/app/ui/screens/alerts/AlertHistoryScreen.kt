package com.naturasonic.app.ui.screens.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.naturasonic.app.data.local.entity.AlertEvent
import com.naturasonic.app.data.local.entity.AlertSoundClass
import com.naturasonic.app.ui.screens.home.icon
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertHistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: AlertHistoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial de alertas") },
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
        ) {
            FilterChipsRow(
                selectedFilter = state.selectedFilter,
                onFilterSelected = viewModel::setFilter
            )

            if (state.isEmpty) {
                EmptyState(
                    hasFilter = state.selectedFilter != null,
                    filterName = state.selectedFilter?.displayName
                )
            } else {
                AlertList(alerts = state.alerts)
            }
        }
    }
}

@Composable
private fun FilterChipsRow(
    selectedFilter: AlertSoundClass?,
    onFilterSelected: (AlertSoundClass?) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedFilter == null,
                onClick = { onFilterSelected(null) },
                label = { Text("Todos") }
            )
        }
        items(AlertSoundClass.entries) { soundClass ->
            FilterChip(
                selected = selectedFilter == soundClass,
                onClick = {
                    onFilterSelected(if (selectedFilter == soundClass) null else soundClass)
                },
                label = { Text(soundClass.chipLabel()) }
            )
        }
    }
}

@Composable
private fun AlertList(alerts: Map<LocalDate, List<AlertEvent>>) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        alerts.forEach { (date, events) ->
            item(key = "header_$date") {
                Text(
                    text = date.formatRelative(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            items(events, key = { it.id }) { event ->
                AlertHistoryItem(event = event)
            }
        }
    }
}

@Composable
private fun AlertHistoryItem(event: AlertEvent) {
    val soundClass = AlertSoundClass.fromKey(event.soundClass)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.errorContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (soundClass != null) {
                    Icon(
                        imageVector = soundClass.icon(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = soundClass?.displayName ?: event.soundClass,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Confianza: ${(event.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formatTime(event.detectedAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyState(hasFilter: Boolean, filterName: String?) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.NotificationsOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (hasFilter) "No hay alertas de tipo \"$filterName\""
                       else "Aún no se han detectado alertas",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun AlertSoundClass.chipLabel(): String = when (this) {
    AlertSoundClass.SIREN -> "Sirena"
    AlertSoundClass.DOORBELL -> "Timbre"
    AlertSoundClass.BABY_CRY -> "Bebé"
    AlertSoundClass.SMOKE_ALARM -> "Alarma humo"
    AlertSoundClass.CAR_HORN -> "Claxon"
    AlertSoundClass.GLASS_BREAK -> "Cristal"
    AlertSoundClass.DOG_BARK -> "Ladrido"
}

private fun LocalDate.formatRelative(): String {
    val today = LocalDate.now()
    return when (this) {
        today -> "Hoy"
        today.minusDays(1) -> "Ayer"
        else -> format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}

private fun formatTime(epochMillis: Long): String {
    val time = Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
    return time.format(DateTimeFormatter.ofPattern("HH:mm"))
}
