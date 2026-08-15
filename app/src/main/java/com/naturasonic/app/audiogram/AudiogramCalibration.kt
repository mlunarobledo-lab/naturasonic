package com.naturasonic.app.audiogram

object AudiogramCalibration {

    private val testFrequencies = floatArrayOf(250f, 500f, 1000f, 2000f, 4000f, 8000f)

    private val eqCenterFreqs = floatArrayOf(
        125f, 250f, 500f, 1000f, 2000f, 4000f, 6000f, 8000f, 10000f, 12000f
    )

    fun computeEqGains(leftThresholds: FloatArray, rightThresholds: FloatArray): FloatArray {
        require(leftThresholds.size == 6 && rightThresholds.size == 6)

        val avgThresholds = FloatArray(6) { i ->
            (leftThresholds[i] + rightThresholds[i]) / 2f
        }

        val allBandThresholds = interpolateToAllBands(avgThresholds)

        return FloatArray(10) { i ->
            halfGain(allBandThresholds[i])
        }
    }

    fun computeEqGainsForEar(thresholds: FloatArray): FloatArray {
        require(thresholds.size == 6)

        val allBandThresholds = interpolateToAllBands(thresholds)

        return FloatArray(10) { i ->
            halfGain(allBandThresholds[i])
        }
    }

    private fun interpolateToAllBands(thresholds: FloatArray): FloatArray {
        val t250 = thresholds[0]
        val t500 = thresholds[1]
        val t1000 = thresholds[2]
        val t2000 = thresholds[3]
        val t4000 = thresholds[4]
        val t8000 = thresholds[5]

        return floatArrayOf(
            t250,                           // 125 Hz: extrapolación plana desde 250
            t250,                           // 250 Hz: directo
            t500,                           // 500 Hz: directo
            t1000,                          // 1000 Hz: directo
            t2000,                          // 2000 Hz: directo
            t4000,                          // 4000 Hz: directo
            (t4000 + t8000) / 2f,           // 6000 Hz: interpolación lineal
            t8000,                          // 8000 Hz: directo
            t8000,                          // 10000 Hz: extrapolación plana
            t8000                           // 12000 Hz: extrapolación plana
        )
    }

    private fun halfGain(thresholdDbHl: Float): Float {
        val gain = thresholdDbHl * 0.5f
        return gain.coerceIn(0f, 12f)
    }

    fun frequencyLabel(index: Int): String {
        if (index < 0 || index >= testFrequencies.size) return ""
        val freq = testFrequencies[index]
        return if (freq >= 1000f) {
            "${(freq / 1000f).toInt()} kHz"
        } else {
            "${freq.toInt()} Hz"
        }
    }

    fun isNormalHearing(thresholds: FloatArray): Boolean {
        return thresholds.all { it <= 10f }
    }
}
