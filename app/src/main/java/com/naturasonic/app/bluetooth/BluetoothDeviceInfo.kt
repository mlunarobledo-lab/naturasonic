package com.naturasonic.app.bluetooth

data class BluetoothDeviceInfo(
    val name: String,
    val address: String,
    val type: BluetoothType,
    val isConnected: Boolean = false
)

enum class BluetoothType {
    LE_AUDIO,
    ASHA,
    CLASSIC,
    UNKNOWN
}

enum class BluetoothCompatibility {
    LE_AUDIO_SUPPORTED,
    ASHA_SUPPORTED,
    CLASSIC_ONLY,
    NOT_SUPPORTED
}
