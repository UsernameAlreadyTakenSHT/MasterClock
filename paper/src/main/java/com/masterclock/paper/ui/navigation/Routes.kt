package com.masterclock.paper.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The E-Ink app's two screens: the clock and the Modes page. */
@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Timer : Route
    @Serializable
    data object Settings : Route
}
