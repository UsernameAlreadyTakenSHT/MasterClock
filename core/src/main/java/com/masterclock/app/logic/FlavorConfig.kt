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
     * Complete only, beside the game logs and the More tab that manages them. Standard and Lite
     * used to show statistics too, while offering no way to clear the history behind them or to
     * set how long it is kept -- both of which live in the More tab.
     */
    fun hasStatistics(): Boolean {
        return currentFlavor == AppFlavor.COMPLETE
    }

    /**
     * Whether games are recorded at all. Only where something reads them back: Complete's logs and
     * statistics, and the Last Games tab and launcher shortcuts that Standard and Lite share with
     * it. Mini and the E-Ink build recorded every game into a history no screen of theirs shows.
     */
    fun keepsGameHistory(): Boolean = hasPresets()

    /**
     * Whether this build can talk to an electronic board.
     *
     * `app/src/complete/.../BluetoothBoardScreen.kt` is the only file in either module that touches
     * the three transports, and the three reduced flavours plus paper strip every Bluetooth
     * permission and the usb.host feature from their manifests. This is what lets
     * ChessTimerViewModel avoid building the transports at all in those builds -- it used to
     * construct all three eagerly and combine their flows in init, so every launch of every build
     * resolved a BluetoothAdapter, obtained a UsbManager and registered a USB broadcast receiver
     * for hardware it had no permission to reach and no screen to reach it from.
     */
    fun hasBoards(): Boolean {
        return currentFlavor == AppFlavor.COMPLETE
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
