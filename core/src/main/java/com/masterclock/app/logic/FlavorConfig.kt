package com.masterclock.app.logic

enum class AppFlavor {
    COMPLETE, STANDARD, LIGHT, MINI, E_INK
}

object FlavorConfig {
    var currentFlavor: AppFlavor = AppFlavor.COMPLETE

    fun isEInk(): Boolean = currentFlavor == AppFlavor.E_INK

    fun hasMoreTab(): Boolean {
        return currentFlavor == AppFlavor.COMPLETE
    }

    fun hasFullSettingsTabs(): Boolean {
        return currentFlavor == AppFlavor.COMPLETE || currentFlavor == AppFlavor.STANDARD
    }

    fun hasArbitre(): Boolean {
        return currentFlavor != AppFlavor.MINI && currentFlavor != AppFlavor.E_INK
    }

    fun hasPresets(): Boolean {
        return currentFlavor != AppFlavor.MINI && currentFlavor != AppFlavor.E_INK
    }

    fun hasOmni(): Boolean {
        return currentFlavor == AppFlavor.COMPLETE || currentFlavor == AppFlavor.STANDARD || currentFlavor == AppFlavor.LIGHT
    }

    fun isModeAllowed(mode: TimerMode): Boolean {
        if (currentFlavor == AppFlavor.E_INK) {
            return mode in listOf(TimerMode.SUDDEN_DEATH, TimerMode.FISHER, TimerMode.MOVE_TIMER_STANDARD)
        }
        if (currentFlavor == AppFlavor.MINI) {
            return mode in listOf(TimerMode.SUDDEN_DEATH, TimerMode.FISHER, TimerMode.MOVE_TIMER_STANDARD)
        }
        return true
    }
}
