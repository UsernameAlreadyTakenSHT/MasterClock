package com.masterclock.app.logic

enum class AppFlavor {
    COMPLETE, STANDARD, LITE, MINI, E_INK
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

    /**
     * Deliberately wider than [hasMoreTab]: the game-log screen it complements lives behind the
     * More tab and is therefore COMPLETE-only, but statistics are worth having anywhere games are
     * recorded at all.
     */
    fun hasStatistics(): Boolean {
        return currentFlavor != AppFlavor.MINI && currentFlavor != AppFlavor.E_INK
    }

    fun hasOmni(): Boolean {
        return currentFlavor == AppFlavor.COMPLETE || currentFlavor == AppFlavor.STANDARD || currentFlavor == AppFlavor.LITE
    }

    fun isModeAllowed(mode: TimerMode): Boolean {
        if (currentFlavor == AppFlavor.E_INK) {
            return mode in listOf(TimerMode.SUDDEN_DEATH, TimerMode.FISCHER, TimerMode.MOVE_TIMER_STANDARD)
        }
        if (currentFlavor == AppFlavor.MINI) {
            return mode in listOf(TimerMode.SUDDEN_DEATH, TimerMode.FISCHER, TimerMode.MOVE_TIMER_STANDARD)
        }
        return true
    }
}
