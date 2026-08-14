package com.naturasonic.app.detection

import com.naturasonic.app.data.local.entity.AlertSoundClass

object AlertVibrationPatterns {

    fun getPattern(alertClass: AlertSoundClass): LongArray = when (alertClass) {
        // Emergency: 3 long pulses
        AlertSoundClass.SIREN -> longArrayOf(0, 500, 200, 500, 200, 500)
        // Visitor: ding-dong
        AlertSoundClass.DOORBELL -> longArrayOf(0, 150, 100, 150)
        // Urgent care: 4 rapid taps
        AlertSoundClass.BABY_CRY -> longArrayOf(0, 100, 80, 100, 80, 100, 80, 100)
        // Critical: 2 long sustained
        AlertSoundClass.SMOKE_ALARM -> longArrayOf(0, 800, 200, 800)
        // Traffic: honk-honk
        AlertSoundClass.CAR_HORN -> longArrayOf(0, 300, 150, 300)
        // Security: crack + sustain
        AlertSoundClass.GLASS_BREAK -> longArrayOf(0, 100, 50, 500)
        // Animal: woof-woof-woof
        AlertSoundClass.DOG_BARK -> longArrayOf(0, 150, 100, 150, 100, 150)
    }
}
