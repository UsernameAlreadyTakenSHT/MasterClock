package com.masterclock.app.logic

import org.junit.Assert.*
import org.junit.Test

/** The moment a clock reaches zero, in the modes that handle it on their own; see [ClockInvariantsTest]. */
class ClockZeroTest {

    @Test
    fun `an Overtime move clock at exactly zero with no reserve is out at once`() {
        val s = ChessClockSettings(main = PlayerSettings(mode = TimerMode.MOVE_TIMER_OVERTIME))
        val after = tickPlayer(PlayerState(timeRemainingMs = 1_000, secondaryTimeMs = 0), 1_000, s.main, s)
        assertTrue(after.isOutOfTime)
    }

    @Test
    fun `Accelerate does not accelerate the overrun`() {
        val p = PlayerSettings(mode = TimerMode.FAST_MOVE, fastMoveMode = FastMoveType.ACCELERATE, fastMoveGracePeriodMs = 0, fastMoveFastPeriodMs = 0, fastMoveFullAccelRate = 1f)
        val s = ChessClockSettings(main = p, flagBehavior = FlagBehavior.REVERSE)
        val after = tickPlayer(PlayerState(timeRemainingMs = 7_000, initialTotalTimeMs = 17_000), 10_000, p, s)
        // At double speed 7s last 3.5s, and the other 6.5s of the tick are the overrun.
        assertEquals(6_500, after.timeRemainingMs)
        assertTrue(after.isOutOfTime)
    }
}
