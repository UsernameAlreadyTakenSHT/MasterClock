package com.masterclock.app

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.masterclock.app.logic.BoardFrameLog
import com.masterclock.app.logic.BoardProtocol
import com.masterclock.app.logic.ChessTimerViewModel
import com.masterclock.app.logic.FlavorConfig
import com.masterclock.app.logic.SimulatedBoard
import com.masterclock.app.ui.screens.SimulatedBoardSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * A game played on a simulated board of each make, from the bytes that make sends to the clock and
 * to Last Games, in the real ViewModel: the whole of a connected board but its radio.
 *
 * [BoardFlowTest] starts where a move is already a move. This starts a step before the app can tell
 * there is one.
 */
class SimulatedBoardFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val viewModel get() = ViewModelProvider(rule.activity)[ChessTimerViewModel::class.java]

    /** The debug build's simulated board, writing to the game's log as the screen has it do. */
    private val board get() = SimulatedBoardSlot.manager.also { it.frameLog = viewModel.boardFrameLog }

    private fun movesLogged(): List<String> =
        viewModel.gameHistory.value.flatMap { it.events }.filter { it.eventType == "MOVE" }.mapNotNull { it.moveNotation }

    private fun startAfresh() {
        rule.runOnUiThread {
            board.disconnect()
            viewModel.resetAllSettings()
            viewModel.reset()
            viewModel.clearAllLogs()
            viewModel.boardFrameLog.clear()
        }
        rule.waitUntil(TIMEOUT_MS) { viewModel.gameHistory.value.isEmpty() }
        rule.runOnUiThread { viewModel.updateSettings(viewModel.settings.value.copy(autoSwitchOnBoardMove = true)) }
        rule.waitUntil(TIMEOUT_MS) { viewModel.settings.value.autoSwitchOnBoardMove }
        rule.runOnUiThread { viewModel.startOrSwitch(1) }
        rule.waitUntil(TIMEOUT_MS) { viewModel.uiState.value.activePlayer != null && !viewModel.uiState.value.isPaused }
    }

    /** Waits for the game's last move to come out of the board, then ends the game so that it is in Last Games. */
    private fun playedOut() {
        rule.waitUntil(GAME_TIMEOUT_MS) { board.lastMove.value == SimulatedBoard.DEMO_GAME.last() }
        rule.waitForIdle()
        rule.runOnUiThread { viewModel.reset() }
        rule.waitUntil(TIMEOUT_MS) { movesLogged().size >= SimulatedBoard.DEMO_GAME.size }
    }

    private fun play(make: BoardProtocol) {
        startAfresh()
        val starter = viewModel.uiState.value.activePlayer
        rule.runOnUiThread {
            board.connect(make, FRAME_EVERY_MS) { viewModel.recordBoardMove(it) }
        }
        rule.waitUntil(GAME_TIMEOUT_MS) { board.lastMove.value == SimulatedBoard.DEMO_GAME.last() }
        rule.waitForIdle()
        // Sixteen moves, sixteen turns passed: the clock is back with whoever it started with.
        assertEquals(make.name, starter, viewModel.uiState.value.activePlayer)
        playedOut()
        assertEquals(make.name, SimulatedBoard.DEMO_GAME, movesLogged())
    }

    @Before
    fun aBuildWithBoards() {
        assumeTrue(FlavorConfig.hasBoards() && FlavorConfig.keepsGameHistory())
        rule.waitUntil(TIMEOUT_MS) { viewModel.isReady.value }
        rule.waitUntil(TIMEOUT_MS) { viewModel.isHistoryLoaded.value }
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(hasContentDescription(rule.activity.getString(R.string.timer_start))).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun aGameOnEachMakePassesEveryTurnAndIsLoggedMoveForMove() {
        SimulatedBoard.makes.forEach { play(it) }
    }

    @Test
    fun whatASimulatedBoardSaidIsInTheLogAndPlaysBackAsTheSameGame() {
        startAfresh()
        rule.runOnUiThread {
            board.connect(SimulatedBoard.makes.first(), FRAME_EVERY_MS) { viewModel.recordBoardMove(it) }
        }
        playedOut()
        val file = viewModel.boardFrameLog.text("test")
        assertTrue(viewModel.boardFrameLog.count.value > SimulatedBoard.DEMO_GAME.size)

        // The file, as a player would send it, played back into a new game.
        startAfresh()
        var accepted = false
        rule.runOnUiThread {
            accepted = board.replay(BoardFrameLog.parse(file)) { viewModel.recordBoardMove(it) }
        }
        assertTrue(accepted)
        playedOut()
        assertEquals(SimulatedBoard.DEMO_GAME, movesLogged())
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val GAME_TIMEOUT_MS = 90_000L
        /** Fast, and still slower than two presses a hand could make. */
        const val FRAME_EVERY_MS = 120L
    }
}
