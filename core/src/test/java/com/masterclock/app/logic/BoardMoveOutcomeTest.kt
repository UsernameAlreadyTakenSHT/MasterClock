package com.masterclock.app.logic

import org.junit.Test
import org.junit.Assert.*

/**
 * Exercises [boardMoveOutcome], the decision a move reported by a linked board goes through before
 * it reaches the clock.
 *
 * The case worth protecting is [BoardMoveOutcome.HoldForNextPress]: before it existed, a board move
 * with auto-switch off was written to the log as a BOARD_MOVE event and nothing ever read it, so the
 * move count stayed at zero and an exported PGN was a column of "???" even with a board connected.
 */
class BoardMoveOutcomeTest {

    @Test
    fun `auto-switch on presses the clock straight away`() {
        val outcome = boardMoveOutcome("e2e4", activePlayer = 1, isPaused = false, autoSwitchOnBoardMove = true)
        assertEquals(BoardMoveOutcome.SwitchNow("e2e4"), outcome)
    }

    @Test
    fun `auto-switch off holds the notation for the player's own press`() {
        val outcome = boardMoveOutcome("e2e4", activePlayer = 1, isPaused = false, autoSwitchOnBoardMove = false)
        assertEquals(BoardMoveOutcome.HoldForNextPress("e2e4"), outcome)
    }

    @Test
    fun `a paused clock is never resumed by the board`() {
        val outcome = boardMoveOutcome("e2e4", activePlayer = 1, isPaused = true, autoSwitchOnBoardMove = true)
        assertEquals(BoardMoveOutcome.HoldForNextPress("e2e4"), outcome)
    }

    @Test
    fun `no active player means there is no move to attach the notation to`() {
        val outcome = boardMoveOutcome("e2e4", activePlayer = null, isPaused = false, autoSwitchOnBoardMove = true)
        assertEquals(BoardMoveOutcome.NoGameRunning("e2e4"), outcome)
    }

    @Test
    fun `a board reporting before the clock has ever started is not a move`() {
        val outcome = boardMoveOutcome("e2e4", activePlayer = null, isPaused = false, autoSwitchOnBoardMove = false)
        assertEquals(BoardMoveOutcome.NoGameRunning("e2e4"), outcome)
    }

    @Test
    fun `the notation is carried through untouched`() {
        // Boards differ on notation: coordinate, SAN, or a vendor's own string. Whatever arrives is
        // what the PGN will show, so nothing here may reinterpret it.
        listOf("e2e4", "Nf3", "O-O", "e8=Q+", "0000").forEach { notation ->
            val held = boardMoveOutcome(notation, activePlayer = 2, isPaused = false, autoSwitchOnBoardMove = false)
            assertEquals(BoardMoveOutcome.HoldForNextPress(notation), held)

            val switched = boardMoveOutcome(notation, activePlayer = 2, isPaused = false, autoSwitchOnBoardMove = true)
            assertEquals(BoardMoveOutcome.SwitchNow(notation), switched)
        }
    }
}
