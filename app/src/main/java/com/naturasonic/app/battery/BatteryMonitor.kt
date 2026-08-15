package com.naturasonic.app.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class BatteryState(
    val level: Int = 100,
    val isCharging: Boolean = false,
    val temperature: Float = 0f
)

@Singleton
class BatteryMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _batteryState = MutableStateFlow(BatteryState())
    val batteryState: StateFlow<BatteryState> = _batteryState.asStateFlow()

    private var receiver: BroadcastReceiver? = null

    fun startMonitoring() {
        if (receiver != null) return

        val stickyIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (stickyIntent != null) {
            updateFromIntent(stickyIntent)
        }

        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent != null) {
                    updateFromIntent(intent)
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    fun stopMonitoring() {
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: IllegalArgumentException) { }
            receiver = null
        }
    }

    private fun updateFromIntent(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)

        val pct = if (scale > 0) (level * 100) / scale else 100
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        _batteryState.value = BatteryState(
            level = pct,
            isCharging = charging,
            temperature = temp / 10f
        )
    }
}
