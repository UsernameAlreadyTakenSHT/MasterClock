package com.masterclock.app.logic

import org.junit.Test
import org.junit.Assert.*

/**
 * REVERSE marks an overrun with nothing but the flag: the clock counts the overrun up, and what
 * credits time pays it off first. The staged modes and the post-move credits read only
 * isNegative, so under REVERSE an overrun froze at 0:00 or was taken for time left.
 */
class ReverseOverrunTest {

    private fun reverse(s: PlayerSettings) = ChessClockSettings(main = s, flagBehavior = FlagBehavior.REVERSE)

    private fun press(p: PlayerState, s: PlayerSettings) =
        computePostMoveState(ChessClockState(players = listOf(p, PlayerState(60_000)), activePlayer = 1), 1, 1_000, reverse(s), s).players[0]

    @Test
    fun `the last byoyomi period running out counts the overrun up`() {
        val s = PlayerSettings(mode = TimerMode.BYOYOMI_JAPANESE, byoyomiTimeMs = 30_000, byoyomiPeriods = 1)
        var p = tickPlayer(PlayerState(timeRemainingMs = 500, isInByoyomi = true, byoyomiPeriodsRemaining = 1), 1_000, s, reverse(s))
        assertTrue(p.isOutOfTime)
        assertEquals(500, p.timeRemainingMs)
        p = tickPlayer(p, 1_000, s, reverse(s))
        // It used to sit at 0:00.
        assertEquals(1_500, p.timeRemainingMs)
    }

    @Test
    fun `a FIDE period flag counts up, and the next period pays the overrun off`() {
        val s = PlayerSettings(mode = TimerMode.FIDE_PERIODS, fidePeriods = listOf(FidePeriod(timeMs = 60_000, incrementMs = 0, movesToNext = 2), FidePeriod(timeMs = 600_000, incrementMs = 0, movesToNext = 0)))
        var p = tickPlayer(PlayerState(timeRemainingMs = 500, moveCount = 1), 1_000, s, reverse(s))
        assertTrue(p.isOutOfTime)
        p = tickPlayer(p, 4_500, s, reverse(s))
        assertEquals(5_000, p.timeRemainingMs)
        // Move 2 reaches the next period: its ten minutes, less the overrun, and back in the game.
        // It used to add the whole period to a clock still marked as flagged.
        val after = press(p, s)
        assertEquals(595_000, after.timeRemainingMs)
        assertFalse(after.isOutOfTime)
    }

    @Test
    fun `a Fischer increment pays a REVERSE overrun off`() {
        val s = PlayerSettings(mode = TimerMode.FISCHER, incrementMs = 2_000)
        val after = press(PlayerState(timeRemainingMs = 5_000, isOutOfTime = true), s)
        assertEquals(3_000, after.timeRemainingMs)
        assertTrue(after.isOutOfTime)
    }

    @Test
    fun `a move timer starts afresh after a REVERSE overrun`() {
        val s = PlayerSettings(mode = TimerMode.MOVE_TIMER_STANDARD, moveTimeMs = 30_000)
        val after = press(PlayerState(timeRemainingMs = 4_000, isOutOfTime = true), s)
        assertEquals(30_000, after.timeRemainingMs)
        assertFalse(after.isOutOfTime)
    }
}
