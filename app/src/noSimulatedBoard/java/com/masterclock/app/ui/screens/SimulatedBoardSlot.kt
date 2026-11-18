package com.masterclock.app.ui.screens

import androidx.compose.runtime.Composable
import com.masterclock.app.logic.ChessTimerViewModel
import com.masterclock.app.logic.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Where the debug build has its simulated boards, and every other build has nothing: no board, no
 * state but idle, nothing drawn. The board's screen is written once and asks this; which of the two
 * `SimulatedBoardSlot`s answers is the build's doing, in `app/build.gradle.kts`.
 *
 * So no code of the simulator is in an app anyone installs -- not hidden behind a test, absent.
 */
@Suppress("UNUSED_PARAMETER")
object SimulatedBoardSlot {
    val connectionState: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Idle)
    val lastMove: StateFlow<String?> = MutableStateFlow(null)

    fun disconnect() {}

    @Composable
    fun Chips(viewModel: ChessTimerViewModel, enabled: Boolean, beforeConnect: () -> Unit) {}
}
