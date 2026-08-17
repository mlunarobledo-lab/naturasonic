package com.naturasonic.app.audio

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HeadTrackingManager @Inject constructor(
    @ApplicationContext private val context: Context
) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val _state = MutableStateFlow<HeadTrackingState>(HeadTrackingState.Disabled)
    val state: StateFlow<HeadTrackingState> = _state.asStateFlow()

    private val _azimuthDeg = MutableStateFlow(0f)
    val azimuthDeg: StateFlow<Float> = _azimuthDeg.asStateFlow()

    private val _pitchDeg = MutableStateFlow(0f)
    val pitchDeg: StateFlow<Float> = _pitchDeg.asStateFlow()

    private var referenceAzimuth = 0f
    private var referencePitch = 0f
    private var isCalibrated = false

    private var lastUpdateNanos = 0L
    private var isListening = false

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    val isSensorAvailable: Boolean get() = rotationSensor != null

    fun start() {
        if (rotationSensor == null) {
            _state.value = HeadTrackingState.SensorUnavailable
            return
        }
        if (isListening) return

        sensorManager?.registerListener(
            this, rotationSensor, SensorManager.SENSOR_DELAY_GAME
        )
        isListening = true
        isCalibrated = false
        _state.value = HeadTrackingState.Calibrating
        Log.i(TAG, "Head tracking sensor registered")
    }

    fun stop() {
        if (!isListening) return
        sensorManager?.unregisterListener(this)
        isListening = false
        isCalibrated = false
        _state.value = HeadTrackingState.Disabled
        _azimuthDeg.value = 0f
        _pitchDeg.value = 0f
        Log.i(TAG, "Head tracking sensor unregistered")
    }

    fun calibrate() {
        if (!isListening) return
        _state.value = HeadTrackingState.Calibrating
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        val now = System.nanoTime()
        if (now - lastUpdateNanos < THROTTLE_INTERVAL_NS) return
        lastUpdateNanos = now

        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientation)

        val rawAzimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
        val rawPitchDeg = Math.toDegrees(orientation[1].toDouble()).toFloat()

        val currentState = _state.value
        if (currentState is HeadTrackingState.Calibrating) {
            referenceAzimuth = rawAzimuthDeg
            referencePitch = rawPitchDeg
            isCalibrated = true
            val state = HeadTrackingState.Active(0f, 0f)
            _state.value = state
            _azimuthDeg.value = 0f
            _pitchDeg.value = 0f
            Log.i(TAG, "Calibrated: azimuth=${referenceAzimuth}, pitch=${referencePitch}")
            return
        }

        if (!isCalibrated) return

        var deltaAzimuth = rawAzimuthDeg - referenceAzimuth
        if (deltaAzimuth > 180f) deltaAzimuth -= 360f
        if (deltaAzimuth < -180f) deltaAzimuth += 360f
        val deltaPitch = rawPitchDeg - referencePitch

        _azimuthDeg.value = deltaAzimuth
        _pitchDeg.value = deltaPitch
        _state.value = HeadTrackingState.Active(deltaAzimuth, deltaPitch)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        private const val TAG = "HeadTrackingMgr"
        private const val THROTTLE_INTERVAL_NS = 20_000_000L // 50Hz = 20ms
    }
}
