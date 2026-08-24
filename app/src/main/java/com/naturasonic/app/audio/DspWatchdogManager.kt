package com.naturasonic.app.audio

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DspWatchdogManager @Inject constructor(
    private val audioEngine: OboeAudioEngine
) {

    enum class HealthLevel { GOOD, WARNING, CRITICAL }

    data class WatchdogState(
        val lastCallbackNs: Long = 0,
        val xRunCount: Int = 0,
        val restartCount: Int = 0,
        val consecutiveErrors: Int = 0,
        val isEngineRunning: Boolean = false,
        val healthLevel: HealthLevel = HealthLevel.GOOD,
        val watchdogRestarts: Int = 0
    )

    private val _state = MutableStateFlow(WatchdogState())
    val state: StateFlow<WatchdogState> = _state.asStateFlow()

    private val _restartEvent = MutableSharedFlow<Unit>(replay = 0)
    val restartEvent: SharedFlow<Unit> = _restartEvent.asSharedFlow()

    private var monitorJob: Job? = null
    private var staleCheckCount = 0

    fun startMonitoring(scope: CoroutineScope) {
        monitorJob?.cancel()
        staleCheckCount = 0
        monitorJob = scope.launch {
            while (isActive) {
                pollAndEvaluate()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        staleCheckCount = 0
    }

    private suspend fun pollAndEvaluate() {
        val stats = audioEngine.getWatchdogStats() ?: return

        val lastCallbackNs = stats[0]
        val xRunCount = stats[1].toInt()
        val restartCount = stats[2].toInt()
        val consecutiveErrors = stats[3].toInt()
        val isRunning = stats[4] == 1L

        val nowNs = System.nanoTime()
        val callbackAgeMs = if (lastCallbackNs > 0) (nowNs - lastCallbackNs) / 1_000_000 else 0

        val health = when {
            consecutiveErrors >= MAX_CONSECUTIVE_ERRORS ||
                (isRunning && lastCallbackNs > 0 && callbackAgeMs > STALL_THRESHOLD_MS) ->
                HealthLevel.CRITICAL
            xRunCount > XRUN_WARNING_THRESHOLD || restartCount > 0 ->
                HealthLevel.WARNING
            else -> HealthLevel.GOOD
        }

        var watchdogRestarts = _state.value.watchdogRestarts

        if (isRunning && lastCallbackNs > 0 && callbackAgeMs > STALL_THRESHOLD_MS) {
            staleCheckCount++
            if (staleCheckCount >= STALE_CHECKS_BEFORE_RESTART) {
                Log.w(TAG, "Forcing engine restart after ${callbackAgeMs}ms stall")
                audioEngine.stop()
                audioEngine.start()
                watchdogRestarts++
                staleCheckCount = 0
                _restartEvent.emit(Unit)
            }
        } else {
            staleCheckCount = 0
        }

        _state.value = WatchdogState(
            lastCallbackNs = lastCallbackNs,
            xRunCount = xRunCount,
            restartCount = restartCount,
            consecutiveErrors = consecutiveErrors,
            isEngineRunning = isRunning,
            healthLevel = health,
            watchdogRestarts = watchdogRestarts
        )
    }

    suspend fun forceRestart(): Boolean {
        audioEngine.stop()
        val started = audioEngine.start()
        if (started) {
            _state.value = _state.value.copy(
                watchdogRestarts = _state.value.watchdogRestarts + 1
            )
            _restartEvent.emit(Unit)
        }
        return started
    }

    fun resetStats() {
        audioEngine.resetWatchdog()
        _state.value = WatchdogState(isEngineRunning = _state.value.isEngineRunning)
    }

    companion object {
        private const val TAG = "DspWatchdog"
        const val POLL_INTERVAL_MS = 2000L
        const val STALL_THRESHOLD_MS = 500L
        const val MAX_CONSECUTIVE_ERRORS = 5
        const val XRUN_WARNING_THRESHOLD = 10
        const val STALE_CHECKS_BEFORE_RESTART = 2
    }
}
