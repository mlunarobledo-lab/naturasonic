package com.naturasonic.app.ui.screens.home

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.naturasonic.app.audio.AudioModeManager
import com.naturasonic.app.audio.OboeAudioEngine
import com.naturasonic.app.audio.VolumeProtection
import com.naturasonic.app.battery.BatteryMonitor
import com.naturasonic.app.battery.EcoModeManager
import com.naturasonic.app.billing.BillingManager
import com.naturasonic.app.bluetooth.BluetoothAudioManager
import com.naturasonic.app.data.local.entity.AudioMode
import com.naturasonic.app.data.preferences.UserPreferences
import com.naturasonic.app.detection.DetectedAlert
import com.naturasonic.app.detection.SoundAlertDetector
import com.naturasonic.app.service.AudioService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isAudioActive: Boolean = false,
    val currentMode: AudioMode = AudioMode.CONVERSATION,
    val volume: Float = 0.5f,
    val isVolumeLocked: Boolean = false,
    val showVolumeWarning: Boolean = false,
    val latestAlert: DetectedAlert? = null,
    val isAlertDetectionActive: Boolean = false,
    val isBluetoothConnected: Boolean = false,
    val isPremium: Boolean = false,
    val batteryLevel: Int = 100,
    val isBatteryCharging: Boolean = false,
    val isEcoActive: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val application: Application,
    private val audioEngine: OboeAudioEngine,
    private val modeManager: AudioModeManager,
    private val volumeProtection: VolumeProtection,
    private val bluetoothManager: BluetoothAudioManager,
    private val alertDetector: SoundAlertDetector,
    private val billingManager: BillingManager,
    private val userPreferences: UserPreferences,
    private val batteryMonitor: BatteryMonitor,
    private val ecoModeManager: EcoModeManager
) : AndroidViewModel(application) {

    private val _isAudioActive = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> = combine(
        _isAudioActive,
        userPreferences.currentMode,
        userPreferences.masterVolume,
        volumeProtection.isLocked,
        volumeProtection.showWarning,
        alertDetector.latestAlert,
        billingManager.isPremium,
        alertDetector.isRunning,
        batteryMonitor.batteryState,
        ecoModeManager.isEcoActive
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val battery = values[8] as com.naturasonic.app.battery.BatteryState
        HomeUiState(
            isAudioActive = values[0] as Boolean,
            currentMode = AudioMode.fromKey(values[1] as String),
            volume = values[2] as Float,
            isVolumeLocked = values[3] as Boolean,
            showVolumeWarning = values[4] as Boolean,
            latestAlert = values[5] as? DetectedAlert,
            isAlertDetectionActive = values[7] as Boolean,
            isBluetoothConnected = bluetoothManager.connectedDevices.value.any { it.isConnected },
            isPremium = values[6] as Boolean,
            batteryLevel = battery.level,
            isBatteryCharging = battery.isCharging,
            isEcoActive = values[9] as Boolean
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    init {
        billingManager.initialize()
        bluetoothManager.refreshConnectedDevices()
    }

    fun toggleAudio() {
        viewModelScope.launch {
            if (_isAudioActive.value) {
                stopAudio()
            } else {
                startAudio()
            }
        }
    }

    private fun startAudio() {
        val intent = Intent(application, AudioService::class.java).apply {
            action = AudioService.ACTION_START
        }
        application.startForegroundService(intent)
        _isAudioActive.value = true
    }

    private fun stopAudio() {
        val intent = Intent(application, AudioService::class.java).apply {
            action = AudioService.ACTION_STOP
        }
        application.startService(intent)
        _isAudioActive.value = false
    }

    fun setMode(mode: AudioMode) {
        viewModelScope.launch {
            userPreferences.setCurrentMode(mode.key)
            modeManager.applyMode(mode, 0)
        }
    }

    fun setVolume(volume: Float) {
        viewModelScope.launch {
            userPreferences.setMasterVolume(volume)
            audioEngine.setAmplification(volume)
        }
    }

    fun dismissVolumeWarning() {
        volumeProtection.dismissWarning()
    }

    override fun onCleared() {
        super.onCleared()
        billingManager.release()
    }
}
