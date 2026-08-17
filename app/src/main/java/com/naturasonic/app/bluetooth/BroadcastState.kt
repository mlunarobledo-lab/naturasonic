package com.naturasonic.app.bluetooth

sealed class BroadcastState {
    data object Idle : BroadcastState()
    data object Starting : BroadcastState()
    data class Active(val broadcastId: Int) : BroadcastState()
    data object Stopping : BroadcastState()
    data class Error(val reason: String) : BroadcastState()
    data class Unsupported(val reason: String) : BroadcastState()
}

enum class BroadcastCapability {
    SUPPORTED,
    API_TOO_LOW,
    BLUETOOTH_NOT_AVAILABLE,
    BLUETOOTH_OFF,
    HARDWARE_NOT_SUPPORTED,
    PROFILE_UNAVAILABLE
}
