package com.naturasonic.app.ui.screens.onboarding

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.naturasonic.app.R
import com.naturasonic.app.bluetooth.BluetoothCompatibility

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (state.currentPage) {
                0 -> WelcomePage(onNext = viewModel::nextPage)
                1 -> BluetoothCheckPage(
                    compatibility = state.btCompatibility,
                    onCheck = viewModel::checkBluetooth,
                    onNext = viewModel::nextPage
                )
                2 -> DisclaimerPage(
                    accepted = state.disclaimerAccepted,
                    onAccept = viewModel::acceptDisclaimer,
                    onFinish = {
                        viewModel.completeOnboarding()
                        onComplete()
                    }
                )
            }
        }
    }
}

@Composable
private fun WelcomePage(onNext: () -> Unit) {
    Icon(
        Icons.Default.Hearing,
        contentDescription = null,
        modifier = Modifier.size(96.dp),
        tint = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(24.dp))
    Text(
        stringResource(R.string.onboarding_welcome_title),
        style = MaterialTheme.typography.displayLarge,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.onboarding_welcome_subtitle),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(48.dp))
    Button(
        onClick = onNext,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text("Comenzar", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun BluetoothCheckPage(
    compatibility: BluetoothCompatibility,
    onCheck: () -> Unit,
    onNext: () -> Unit
) {
    LaunchedEffect(Unit) { onCheck() }

    Icon(
        Icons.Default.Bluetooth,
        contentDescription = null,
        modifier = Modifier.size(72.dp),
        tint = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(24.dp))
    Text(
        stringResource(R.string.onboarding_bt_check_title),
        style = MaterialTheme.typography.headlineLarge,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(24.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (compatibility) {
                BluetoothCompatibility.LE_AUDIO_SUPPORTED,
                BluetoothCompatibility.ASHA_SUPPORTED ->
                    MaterialTheme.colorScheme.primaryContainer
                BluetoothCompatibility.CLASSIC_ONLY ->
                    MaterialTheme.colorScheme.tertiaryContainer
                BluetoothCompatibility.NOT_SUPPORTED ->
                    MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (compatibility) {
                    BluetoothCompatibility.LE_AUDIO_SUPPORTED,
                    BluetoothCompatibility.ASHA_SUPPORTED -> Icons.Default.CheckCircle
                    BluetoothCompatibility.CLASSIC_ONLY -> Icons.Default.Warning
                    BluetoothCompatibility.NOT_SUPPORTED -> Icons.Default.Info
                },
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = when (compatibility) {
                    BluetoothCompatibility.LE_AUDIO_SUPPORTED ->
                        stringResource(R.string.onboarding_bt_compatible, "Bluetooth LE Audio")
                    BluetoothCompatibility.ASHA_SUPPORTED ->
                        stringResource(R.string.onboarding_bt_compatible, "ASHA")
                    BluetoothCompatibility.CLASSIC_ONLY ->
                        stringResource(R.string.onboarding_bt_classic_warning)
                    BluetoothCompatibility.NOT_SUPPORTED ->
                        "Bluetooth no disponible. Verifica que esté activado."
                },
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }

    Spacer(Modifier.height(32.dp))

    Button(
        onClick = onNext,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text("Continuar", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun DisclaimerPage(
    accepted: Boolean,
    onAccept: () -> Unit,
    onFinish: () -> Unit
) {
    Icon(
        Icons.Default.Info,
        contentDescription = null,
        modifier = Modifier.size(72.dp),
        tint = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(24.dp))
    Text(
        stringResource(R.string.disclaimer_psap_title),
        style = MaterialTheme.typography.headlineLarge,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(16.dp))

    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.disclaimer_psap_body),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(20.dp)
        )
    }

    Spacer(Modifier.height(24.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = accepted, onCheckedChange = { if (it) onAccept() })
        Spacer(Modifier.width(8.dp))
        Text(
            "He leído y acepto el aviso legal",
            style = MaterialTheme.typography.bodyLarge
        )
    }

    Spacer(Modifier.height(24.dp))

    Button(
        onClick = onFinish,
        enabled = accepted,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text("Empezar a usar NaturaSonic", style = MaterialTheme.typography.titleMedium)
    }
}
