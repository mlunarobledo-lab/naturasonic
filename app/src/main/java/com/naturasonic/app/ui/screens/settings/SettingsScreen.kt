package com.naturasonic.app.ui.screens.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.HearingDisabled
import androidx.compose.material.icons.filled.NoiseAware
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.naturasonic.app.R
import com.naturasonic.app.billing.BillingManager

private val EQ_LABELS = listOf("125", "250", "500", "1K", "2K", "4K", "6K", "8K", "10K", "12K")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPerformance: () -> Unit = {},
    onNavigateToAudiogram: () -> Unit = {},
    onNavigateToAnc: () -> Unit = {},
    onNavigateToEco: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showDisclaimer by remember { mutableStateOf(false) }
    var showSaveProfile by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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

            SectionHeader(Icons.Default.Equalizer, stringResource(R.string.settings_equalizer))
            Spacer(Modifier.height(12.dp))

            EqualizerCard(
                bands = state.eqBands,
                onBandChange = viewModel::setEqBand
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { showSaveProfile = true }) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Guardar perfil")
                }
            }

            Spacer(Modifier.height(24.dp))

            SectionHeader(null, stringResource(R.string.settings_profiles))
            Spacer(Modifier.height(12.dp))

            if (state.profiles.isEmpty()) {
                Text(
                    "No hay perfiles guardados",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.profiles.forEach { profile ->
                    ProfileCard(
                        profile = profile,
                        onLoad = { viewModel.loadProfile(profile) },
                        onDelete = { viewModel.deleteProfile(profile) }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(24.dp))

            SectionHeader(null, "Detección de alertas")
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Activar detección de sonidos", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = state.alertDetectionEnabled,
                    onCheckedChange = viewModel::setAlertDetection
                )
            }

            Spacer(Modifier.height(24.dp))

            if (!state.isPremium) {
                PremiumCard(
                    onUpgrade = {
                        val activity = context as? Activity
                        if (activity != null) {
                            BillingManager(context).apply {
                                initialize()
                                launchPurchaseFlow(activity)
                            }
                        }
                    }
                )
                Spacer(Modifier.height(24.dp))
            }

            TextButton(onClick = onNavigateToEco) {
                Icon(Icons.Default.EnergySavingsLeaf, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Modo eco")
            }

            Spacer(Modifier.height(8.dp))

            TextButton(onClick = onNavigateToAnc) {
                Icon(Icons.Default.NoiseAware, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Control de ruido")
            }

            Spacer(Modifier.height(8.dp))

            TextButton(onClick = onNavigateToAudiogram) {
                Icon(Icons.Default.HearingDisabled, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Calibrar mi audición")
            }

            Spacer(Modifier.height(8.dp))

            TextButton(onClick = onNavigateToPerformance) {
                Icon(Icons.Default.Speed, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Rendimiento")
            }

            Spacer(Modifier.height(8.dp))

            TextButton(onClick = { showDisclaimer = true }) {
                Icon(Icons.Default.Info, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_disclaimer))
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "NaturaSonic v1.0.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showDisclaimer) {
        AlertDialog(
            onDismissRequest = { showDisclaimer = false },
            title = { Text(stringResource(R.string.disclaimer_psap_title)) },
            text = { Text(stringResource(R.string.disclaimer_psap_body)) },
            confirmButton = {
                TextButton(onClick = { showDisclaimer = false }) {
                    Text("Cerrar")
                }
            }
        )
    }

    if (showSaveProfile) {
        var profileName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveProfile = false },
            title = { Text("Guardar perfil de ecualización") },
            text = {
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text("Nombre del perfil") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (profileName.isNotBlank()) {
                            viewModel.saveProfile(profileName)
                            showSaveProfile = false
                        }
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveProfile = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector?, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun EqualizerCard(bands: FloatArray, onBandChange: (Int, Float) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            bands.forEachIndexed { index, value ->
                if (index < EQ_LABELS.size) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            EQ_LABELS[index],
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.width(40.dp)
                        )
                        Slider(
                            value = value,
                            onValueChange = { onBandChange(index, it) },
                            valueRange = -12f..12f,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${value.toInt()} dB",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(48.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: com.naturasonic.app.data.local.entity.AudioProfile,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onLoad,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(profile.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    profile.mode,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar")
            }
        }
    }
}

@Composable
private fun PremiumCard(onUpgrade: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                modifier = Modifier.height(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.premium_title),
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.premium_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onUpgrade,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Suscribirme")
            }
        }
    }
}
