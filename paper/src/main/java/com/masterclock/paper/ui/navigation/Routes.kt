package com.masterclock.paper.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The E-Ink app's screens: the clock, the Modes page, and what the app is and owes. */
@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Timer : Route
    @Serializable
    data object Settings : Route
    @Serializable
    data object About : Route
}
