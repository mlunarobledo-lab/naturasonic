package com.naturasonic.app.battery

import com.naturasonic.app.data.preferences.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EcoModeManager @Inject constructor(
    private val batteryMonitor: BatteryMonitor,
    private val userPreferences: UserPreferences
) {
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _isEcoActive = MutableStateFlow(false)
    val isEcoActive: StateFlow<Boolean> = _isEcoActive.asStateFlow()

    private val _reason = MutableStateFlow(EcoReason.INACTIVE)
    val reason: StateFlow<EcoReason> = _reason.asStateFlow()

    private var autoActivated = false

    fun startObserving() {
        scope.launch {
            combine(
                batteryMonitor.batteryState,
                userPreferences.ecoModeEnabled,
                userPreferences.ecoModeAutoActivate,
                userPreferences.ecoModeThreshold
            ) { battery, manualEnabled, autoEnabled, threshold ->
                EcoInput(battery, manualEnabled, autoEnabled, threshold)
            }.collect { input ->
                evaluate(input)
            }
        }
    }

    private fun evaluate(input: EcoInput) {
        val (battery, manualEnabled, autoEnabled, threshold) = input

        if (manualEnabled) {
            _isEcoActive.value = true
            _reason.value = EcoReason.MANUAL
            return
        }

        if (!autoEnabled) {
            _isEcoActive.value = false
            _reason.value = EcoReason.INACTIVE
            autoActivated = false
            return
        }

        if (!autoActivated && battery.level <= threshold && !battery.isCharging) {
            autoActivated = true
        }

        if (autoActivated && (battery.level > threshold + HYSTERESIS || battery.isCharging)) {
            autoActivated = false
        }

        if (autoActivated) {
            _isEcoActive.value = true
            _reason.value = EcoReason.AUTO_BATTERY
        } else {
            _isEcoActive.value = false
            _reason.value = EcoReason.INACTIVE
        }
    }

    fun getDetectionIntervalMs(): Long = if (_isEcoActive.value) ECO_DETECTION_INTERVAL_MS else NORMAL_DETECTION_INTERVAL_MS

    fun getWhisperPollingMs(): Long = if (_isEcoActive.value) ECO_WHISPER_POLLING_MS else NORMAL_WHISPER_POLLING_MS

    companion object {
        private const val HYSTERESIS = 3
        const val NORMAL_DETECTION_INTERVAL_MS = 1000L
        const val ECO_DETECTION_INTERVAL_MS = 3000L
        const val NORMAL_WHISPER_POLLING_MS = 150L
        const val ECO_WHISPER_POLLING_MS = 500L
    }
}

enum class EcoReason {
    INACTIVE,
    MANUAL,
    AUTO_BATTERY
}

private data class EcoInput(
    val battery: BatteryState,
    val manualEnabled: Boolean,
    val autoEnabled: Boolean,
    val threshold: Int
)
