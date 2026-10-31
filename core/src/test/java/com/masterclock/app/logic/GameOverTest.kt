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
    fun `under FLAG the game is over only when one player is left`() {
        assertFalse(isGameOver(state(true, false, false), FlagBehavior.FLAG, 3))
        assertTrue(isGameOver(state(true, true, false), FlagBehavior.FLAG, 3))
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

    private val globalShared = PlayerSettings(mode = TimerMode.MOVE_TIMER_GLOBAL_SHARED)

    @Test
    fun `the turn passes on from a player Global Shared's move clock eliminated`() {
        val settings = ChessClockSettings(main = globalShared, numberOfPlayers = 3, flagBehavior = FlagBehavior.FLAG)
        assertTrue(turnPassesFromEliminated(state(true, false, false), settings, TimerMode.MOVE_TIMER_GLOBAL_SHARED))
        assertFalse(turnPassesFromEliminated(state(false, true, false), settings, TimerMode.MOVE_TIMER_GLOBAL_SHARED))
        assertFalse(turnPassesFromEliminated(state(true, true, false), settings, TimerMode.MOVE_TIMER_GLOBAL_SHARED)) // game over
    }

    @Test
    fun `the turn stays put on the table's own clock and outside FLAG`() {
        val settings = ChessClockSettings(main = globalShared, numberOfPlayers = 3, flagBehavior = FlagBehavior.FLAG)
        assertFalse(turnPassesFromEliminated(state(true, false, false), settings, TimerMode.MOVE_TIMER_SHARED))
        assertFalse(turnPassesFromEliminated(state(true, false, false), settings.copy(flagBehavior = FlagBehavior.NEGATIVE), TimerMode.SUDDEN_DEATH))
        assertFalse(turnPassesFromEliminated(state(true, false), settings.copy(numberOfPlayers = 2), TimerMode.SUDDEN_DEATH))
    }
}
