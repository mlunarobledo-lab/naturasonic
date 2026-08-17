package com.naturasonic.app.ui.screens.audiosharing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naturasonic.app.bluetooth.BroadcastCapability
import com.naturasonic.app.bluetooth.BroadcastState
import com.naturasonic.app.bluetooth.BluetoothConnectionState
import com.naturasonic.app.bluetooth.BluetoothAudioManager
import com.naturasonic.app.bluetooth.LeAudioBroadcastManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AudioSharingUiState(
    val broadcastState: BroadcastState = BroadcastState.Idle,
    val capability: BroadcastCapability = BroadcastCapability.API_TOO_LOW,
    val btConnectionState: BluetoothConnectionState = BluetoothConnectionState.NoDevice,
    val broadcastCode: String = ""
)

@HiltViewModel
class AudioSharingViewModel @Inject constructor(
    private val broadcastManager: LeAudioBroadcastManager,
    private val bluetoothAudioManager: BluetoothAudioManager
) : ViewModel() {

    val uiState: StateFlow<AudioSharingUiState> = combine(
        broadcastManager.broadcastState,
        broadcastManager.capability,
        bluetoothAudioManager.connectionState
    ) { bState, cap, btState ->
        AudioSharingUiState(
            broadcastState = bState,
            capability = cap,
            btConnectionState = btState
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AudioSharingUiState())

    init {
        broadcastManager.checkBroadcastSupport()
        broadcastManager.connectProfile()
    }

    fun toggleBroadcast(broadcastCode: String) {
        val current = broadcastManager.broadcastState.value
        if (current is BroadcastState.Active) {
            broadcastManager.stopBroadcast()
        } else if (current is BroadcastState.Idle) {
            broadcastManager.startBroadcast(broadcastCode.ifBlank { null })
        }
    }

    fun retryConnection() {
        broadcastManager.checkBroadcastSupport()
        broadcastManager.connectProfile()
    }

    override fun onCleared() {
        super.onCleared()
        if (broadcastManager.broadcastState.value is BroadcastState.Active) {
            broadcastManager.stopBroadcast()
        }
    }
}
