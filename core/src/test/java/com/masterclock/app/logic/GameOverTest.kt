package com.masterclock.app.logic

import org.junit.Test
import org.junit.Assert.*

/** Covers [isGameOver], which decides whether TalkBack may still tell the active player it is their turn. */
class GameOverTest {

    private fun state(vararg outOfTime: Boolean, phasesFinished: Boolean = false) = ChessClockState(
        players = outOfTime.map { PlayerState(timeRemainingMs = if (it) 0 else 60_000, isOutOfTime = it) },
        activePlayer = 1,
        isPaused = false,
        phasesFinished = phasesFinished,
    )

    @Test
    fun `a game nobody has flagged is not over`() {
        assertFalse(isGameOver(state(false, false), FlagBehavior.FREEZE, 2))
    }

    @Test
    fun `a flag under FREEZE ends the game`() {
        assertTrue(isGameOver(state(true, false), FlagBehavior.FREEZE, 2))
    }

    @Test
    fun `a flag under NEGATIVE or REVERSE does not, play goes on`() {
        assertFalse(isGameOver(state(true, false), FlagBehavior.NEGATIVE, 2))
        assertFalse(isGameOver(state(true, false), FlagBehavior.REVERSE, 2))
    }

    @Test
    fun `under FLAG the game is over only when every clock has run out`() {
        assertFalse(isGameOver(state(true, false, false), FlagBehavior.FLAG, 3))
        // One player left, with time: they play on. It was the end of the game, and with two players
        // that made the first flag the end -- FREEZE under another name.
        assertFalse(isGameOver(state(true, true, false), FlagBehavior.FLAG, 3))
        assertFalse(isGameOver(state(true, false), FlagBehavior.FLAG, 2))
        assertTrue(isGameOver(state(true, true).copy(firstToFlag = 1), FlagBehavior.FLAG, 2))
        assertTrue(isGameOver(state(true, true, true).copy(firstToFlag = 2), FlagBehavior.FLAG, 3))
    }

    @Test
    fun `under FLAG only the first clock to run out is flagged`() {
        val both = state(true, true).copy(firstToFlag = 1)
        assertTrue(isFlagged(both, 1, FlagBehavior.FLAG))
        // Out of time after the other had lost on it: stopped at zero, and not marked.
        assertFalse(isFlagged(both, 2, FlagBehavior.FLAG))
        assertFalse(isFlagged(state(true, false).copy(firstToFlag = 1), 2, FlagBehavior.FLAG))
        // Under every other behaviour whoever is out is flagged.
        for (other in listOf(FlagBehavior.FREEZE, FlagBehavior.NEGATIVE, FlagBehavior.REVERSE)) {
            assertTrue(other.name, isFlagged(both, 1, other))
            assertTrue(other.name, isFlagged(both, 2, other))
        }
    }

    @Test
    fun `a PHASES game that ran through its phases is over whatever the flag behaviour`() {
        assertTrue(isGameOver(state(true, false, phasesFinished = true), FlagBehavior.NEGATIVE, 2))
    }

    @Test
    fun `the table's clock run out is over whatever the flag behaviour`() {
        // Everyone out with nobody first: CHRONO's countdown, which stopped only under FREEZE and FLAG.
        assertTrue(isGameOver(state(true, true), FlagBehavior.NEGATIVE, 2))
        assertTrue(isGameOver(state(true, true), FlagBehavior.REVERSE, 2))
        assertFalse(isGameOver(state(true, true).copy(firstToFlag = 1), FlagBehavior.NEGATIVE, 2))
    }

    @Test
    fun `under FLAG a flagged player keeps their seat in the turn order`() {
        // As at a chess clock: the player still in presses to the flagged one, whose press starts
        // their clock again. Skipped, they left the other with a clock no press could stop.
        assertEquals(1, nextPlayerInTurn(2, state(true, false).players, 2, FlagBehavior.FLAG))
        assertEquals(2, nextPlayerInTurn(1, state(true, false).players, 2, FlagBehavior.FLAG))
        assertEquals(2, nextPlayerInTurn(1, state(false, true, false).players, 3, FlagBehavior.FLAG))
    }
}
