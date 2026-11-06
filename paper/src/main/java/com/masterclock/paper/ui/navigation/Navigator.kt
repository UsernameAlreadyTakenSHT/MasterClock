package com.masterclock.paper.ui.navigation

import androidx.navigation3.runtime.NavKey

class Navigator(val state: NavigationState) {
    fun navigate(route: NavKey) {
        if (route in state.backStacks.keys) {
            state.topLevelRoute = route
        } else {
            val stack = state.backStacks[state.topLevelRoute] ?: return
            // Already showing it: a second tap before the screen had changed -- easy on E Ink, which
            // gives no feedback until it redraws -- pushed the same screen twice, and the first Back
            // then seemed to do nothing.
            if (stack.lastOrNull() == route) return
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
