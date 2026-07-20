package com.masterclock.app.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Timer : Route
    @Serializable
    data class Settings(val category: String = "MODES") : Route
    @Serializable
    data object CoinToss : Route
    @Serializable
    data object DiceRoll : Route
    @Serializable
    data object StopPrecision : Route
    @Serializable
    data object BlindfoldTrainer : Route
    @Serializable
    data object KnightPath : Route
    @Serializable
    data object NameSquare : Route
    @Serializable
    data object GameLogs : Route
    @Serializable
    data object Chess960 : Route
    @Serializable
    data object BluetoothBoard : Route
    @Serializable
    data object Rules : Route
    @Serializable
    data object ModeGuide : Route
    @Serializable
    data class QRShare(val payload: String) : Route
    @Serializable
    data object QRReceive : Route
    @Serializable
    data object Presets : Route
    @Serializable
    data object ShortStraw : Route
    @Serializable
    data object RandomCard : Route
    @Serializable
    data object Scoreboard : Route
    @Serializable
    data object Notebook : Route
    @Serializable
    data object OmniTimer : Route
}
