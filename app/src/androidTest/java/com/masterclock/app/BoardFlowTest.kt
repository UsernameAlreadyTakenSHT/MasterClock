package com.masterclock.app

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.masterclock.app.logic.ChessTimerViewModel
import com.masterclock.app.logic.FlavorConfig
import com.masterclock.app.logic.GameEvent
import com.masterclock.app.logic.GamePhase
import com.masterclock.app.logic.TimerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * A move from a board, from where the board's screen hands it over -- recordBoardMove, the callback
 * every connection calls -- to the clock and the log, in the real ViewModel.
 *
 * Everything before that point is pinned in `core` against frames built to each protocol. No board
 * has been near this project, so what a real one sends is still unknown; what the app does with a
 * move once it has one is not.
 */
class BoardFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val viewModel get() = ViewModelProvider(rule.activity)[ChessTimerViewModel::class.java]

    private fun events(): List<GameEvent> = viewModel.gameHistory.value.flatMap { it.events }

    private fun movesNoted(notation: String) = events().filter { it.eventType == "MOVE" && it.moveNotation == notation }

    private fun startTheClock() {
        rule.runOnUiThread { viewModel.startOrSwitch(1) }
        rule.waitUntil(TIMEOUT_MS) { viewModel.uiState.value.activePlayer != null && !viewModel.uiState.value.isPaused }
    }

    private fun board(notation: String) = rule.runOnUiThread { viewModel.recordBoardMove(notation) }

    /**
     * Last Games is read back from the database only when a game ends, so the game being played is
     * not in [ChessTimerViewModel.gameHistory] yet: it is ended here, and waited for.
     */
    private fun endTheGameAndWaitFor(found: () -> Boolean) {
        rule.runOnUiThread { viewModel.reset() }
        rule.waitUntil(TIMEOUT_MS) { found() }
    }

    private fun settings(change: (com.masterclock.app.logic.ChessClockSettings) -> com.masterclock.app.logic.ChessClockSettings) =
        rule.runOnUiThread { viewModel.updateSettings(change(viewModel.settings.value)) }

    @Before
    fun startFromTheDefaults() {
        assumeTrue(FlavorConfig.hasBoards() && FlavorConfig.keepsGameHistory())
        rule.waitUntil(TIMEOUT_MS) { viewModel.isReady.value }
        rule.runOnUiThread {
            viewModel.resetAllSettings()
            viewModel.reset()
            // Last Games outlives a run, and a move a previous one left there would be read as this one's.
            viewModel.clearAllLogs()
        }
        rule.waitUntil(TIMEOUT_MS) { viewModel.gameHistory.value.isEmpty() }
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(hasContentDescription(rule.activity.getString(R.string.timer_start))).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun aMoveOnTheBoardPassesTheTurnAndGoesInTheLog() {
        settings { it.copy(autoSwitchOnBoardMove = true) }
        startTheClock()
        val mover = viewModel.uiState.value.activePlayer

        board("e4")

        rule.waitUntil(TIMEOUT_MS) { viewModel.uiState.value.activePlayer != mover }
        endTheGameAndWaitFor { movesNoted("e4").isNotEmpty() }
    }

    @Test
    fun withoutAutoSwitchTheNextPressCarriesTheMove() {
        settings { it.copy(autoSwitchOnBoardMove = false) }
    @Test
    fun aCastleReportedRookFirstPassesTheTurnOnceAndIsLoggedAsTheKingsMove() {
        settings { it.copy(autoSwitchOnBoardMove = true) }
        startTheClock()
        val mover = viewModel.uiState.value.activePlayer

        board("h1f1")
        rule.waitUntil(TIMEOUT_MS) { viewModel.uiState.value.activePlayer != mover }
        val next = viewModel.uiState.value.activePlayer
        board("e1g1")
        rule.waitForIdle()
        assertEquals("the king's half passed the turn back", next, viewModel.uiState.value.activePlayer)

        endTheGameAndWaitFor { movesNoted("e1g1").isNotEmpty() }
        assertTrue("the rook's move is still in the log", events().none { it.moveNotation == "h1f1" })
    }

    @Test
    fun withoutAutoSwitchACastleRookFirstIsHeldAsTheKingsMove() {
        settings { it.copy(autoSwitchOnBoardMove = false) }
        startTheClock()
        val mover = viewModel.uiState.value.activePlayer!!

        board("a1d1")
        board("e1c1")
        rule.runOnUiThread { viewModel.startOrSwitch(mover) }

        endTheGameAndWaitFor { movesNoted("e1c1").isNotEmpty() }
        assertTrue("the rook's move is still in the log", events().none { it.moveNotation == "a1d1" })
    }

        startTheClock()
        val mover = viewModel.uiState.value.activePlayer!!

        board("Nf3")
        rule.waitForIdle()
        assertEquals(mover, viewModel.uiState.value.activePlayer)

        rule.runOnUiThread { viewModel.startOrSwitch(mover) }
        endTheGameAndWaitFor { movesNoted("Nf3").isNotEmpty() }
    }

    @Test
    fun aMoveHeldForAPressStaysInItsOwnGame() {
        settings { it.copy(autoSwitchOnBoardMove = false) }
        startTheClock()
        board("Qh5")

        // A new time control is a new game, as the dialog before it says.
        settings { it.copy(main = it.main.copy(initialTimeMs = it.main.initialTimeMs + 60_000)) }
        rule.waitUntil(TIMEOUT_MS) { viewModel.uiState.value.activePlayer == null }
        startTheClock()
        rule.runOnUiThread { viewModel.startOrSwitch(viewModel.uiState.value.activePlayer!!) }
        endTheGameAndWaitFor { events().any { it.eventType == "MOVE" } }

        assertTrue(movesNoted("Qh5").isEmpty())
    }

    @Test
    fun inPhasesAMoveIsNotedAndSkipsNothing() {
        // Two phases, so that a move taken for a tap would have somewhere to skip to.
        val phases = listOf(GamePhase(timeMs = 60_000), GamePhase(timeMs = 60_000))
        settings { it.copy(autoSwitchOnBoardMove = true, main = it.main.copy(mode = TimerMode.PHASES, phases = phases)) }
        rule.waitUntil(TIMEOUT_MS) { viewModel.settings.value.main.mode == TimerMode.PHASES }
        rule.runOnUiThread { viewModel.resume() }
        rule.waitUntil(TIMEOUT_MS) { viewModel.uiState.value.activePlayer != null && !viewModel.uiState.value.isPaused }
        val phase = viewModel.uiState.value.players[0].currentPhaseIndex

        board("d4")
        rule.waitForIdle()

        val after = viewModel.uiState.value
        assertEquals(phase, after.players[0].currentPhaseIndex)
        assertTrue(!after.isPaused && !after.players[0].isInInterPhasePause)
        endTheGameAndWaitFor { events().any { it.eventType == "BOARD_MOVE" && it.moveNotation == "d4" } }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
