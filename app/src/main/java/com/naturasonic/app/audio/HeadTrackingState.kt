package com.naturasonic.app.audio

sealed class HeadTrackingState {
    data object Disabled : HeadTrackingState()
    data object Calibrating : HeadTrackingState()
    data class Active(val azimuthDeg: Float, val pitchDeg: Float) : HeadTrackingState()
    data object SensorUnavailable : HeadTrackingState()
}
