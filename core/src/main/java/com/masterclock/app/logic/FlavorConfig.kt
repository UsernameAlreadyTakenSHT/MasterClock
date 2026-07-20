package com.masterclock.app.logic

enum class AppFlavor {
    COMPLETE, STANDARD, LIGHT, EXTRA_LIGHT, E_INK
}

object FlavorConfig {
    var currentFlavor: AppFlavor = AppFlavor.COMPLETE

    fun isEInk(): Boolean = currentFlavor == AppFlavor.E_INK

    fun hasMoreTab(): Boolean {
        return currentFlavor != AppFlavor.EXTRA_LIGHT && currentFlavor != AppFlavor.E_INK
    }

    fun hasArbitre(): Boolean {
        return currentFlavor != AppFlavor.EXTRA_LIGHT && currentFlavor != AppFlavor.E_INK
    }

    fun hasAdvancedSettings(): Boolean {
        return currentFlavor != AppFlavor.EXTRA_LIGHT && currentFlavor != AppFlavor.E_INK
    }

    fun hasPresets(): Boolean {
        return currentFlavor != AppFlavor.EXTRA_LIGHT && currentFlavor != AppFlavor.E_INK
    }

    fun isModeAllowed(mode: TimerMode): Boolean {
        if (currentFlavor == AppFlavor.E_INK) {
            return mode in listOf(TimerMode.SUDDEN_DEATH, TimerMode.FISHER, TimerMode.MOVE_TIMER_STANDARD)
        }
        if (currentFlavor == AppFlavor.EXTRA_LIGHT) {
            return mode in listOf(TimerMode.SUDDEN_DEATH, TimerMode.FISHER, TimerMode.MOVE_TIMER_STANDARD)
        }
        return true
    }
}
