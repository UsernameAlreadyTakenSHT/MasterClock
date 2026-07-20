package com.masterclock.app.ui.navigation

import androidx.navigation3.runtime.NavKey

class Navigator(val state: NavigationState) {
    /**
     * @param replace When true, swaps the current top entry for [route] instead of pushing on top of
     * it. Used for switching tabs within the same screen (e.g. Settings categories) so Back always
     * exits that screen directly instead of stepping back through every previously-visited tab. See
     * AUDIT.md (back button bug fix, 2026-07-19).
     */
    fun navigate(route: NavKey, replace: Boolean = false) {
        if (route in state.backStacks.keys) {
            state.topLevelRoute = route
        } else {
            val stack = state.backStacks[state.topLevelRoute] ?: return
            if (replace) stack.removeLastOrNull()
            stack.add(route)
        }
    }

    fun goBack(): Boolean {
        val currentStack = state.backStacks[state.topLevelRoute] ?: return false
        val currentRoute = currentStack.lastOrNull() ?: return false

        if (currentRoute == state.topLevelRoute) {
            if (state.topLevelRoute != state.startRoute) {
                state.topLevelRoute = state.startRoute
                return true
            }
            return false
        } else {
            currentStack.removeLastOrNull()
            return true
        }
    }

}
