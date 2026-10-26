package com.masterclock.app.logic

import org.junit.Test
import org.junit.Assert.*

class ByoyomiTest {

    private fun japanese(periods: Int, periodMs: Long) = PlayerSettings(mode = TimerMode.BYOYOMI_JAPANESE, byoyomiPeriods = periods, byoyomiTimeMs = periodMs)

    private fun tick(p: PlayerState, delta: Long, s: PlayerSettings, fb: FlagBehavior = FlagBehavior.FLAG) =
        tickPlayer(p, delta, s, ChessClockSettings(main = s, flagBehavior = fb))

    @Test
    fun `main time running out begins the first period with what the tick ran past`() {
        val next = tick(PlayerState(timeRemainingMs = 200, byoyomiPeriodsRemaining = 3), 1_000, japanese(3, 30_000))
        assertTrue(next.isInByoyomi)
        assertEquals(3, next.byoyomiPeriodsRemaining)
        assertEquals(29_200, next.timeRemainingMs)
    }

    @Test
    fun `a long tick runs through several periods at once`() {
        val p = PlayerState(timeRemainingMs = 10_000, isInByoyomi = true, byoyomiPeriodsRemaining = 5)
        // 10s left of this period, then two whole periods of 30s, then 5s into the next.
        val next = tick(p, 75_000, japanese(5, 30_000))
        assertEquals(2, next.byoyomiPeriodsRemaining)
        assertEquals(25_000, next.timeRemainingMs)
        assertFalse(next.isOutOfTime)
    }

    @Test
    fun `periods of nothing flag at once instead of overflowing the stack`() {
        val next = tick(PlayerState(timeRemainingMs = 100, byoyomiPeriodsRemaining = 1_000_000), 200, japanese(1_000_000, 0))
        assertTrue(next.isOutOfTime)
    }

    @Test
    fun `no periods is no byoyomi`() {
        val next = tick(PlayerState(timeRemainingMs = 100, byoyomiPeriodsRemaining = 0), 200, japanese(0, 30_000))
        assertTrue(next.isOutOfTime)
    }

    @Test
    fun `the last period running out counts the overrun under NEGATIVE`() {
        val p = PlayerState(timeRemainingMs = 500, isInByoyomi = true, byoyomiPeriodsRemaining = 1)
        val next = tick(p, 2_000, japanese(3, 30_000), FlagBehavior.NEGATIVE)
        assertTrue(next.isOutOfTime && next.isNegative)
        assertEquals(1_500, next.timeRemainingMs)
    }

    @Test
    fun `a progressive goal saturates rather than wrapping negative`() {
        val s = PlayerSettings(mode = TimerMode.BYOYOMI_PROGRESSIVE, byoyomiPeriods = 5, byoyomiProgression = Int.MAX_VALUE, byoyomiTimeMs = 60_000)
        val p = PlayerState(timeRemainingMs = 10_000, isInByoyomi = true, movesRemainingInPeriod = 1, currentByoyomiMovesGoal = 5)
        val after = computePostMoveState(ChessClockState(players = listOf(p, PlayerState(0)), activePlayer = 1), 1, 0, ChessClockSettings(main = s), s).players[0]
        assertEquals(MAX_BYOYOMI_COUNT, after.movesRemainingInPeriod)
    }

    @Test
    fun `a Canadian block of no moves is a block of one`() {
        val s = PlayerSettings(mode = TimerMode.BYOYOMI_CANADIAN, byoyomiPeriods = 0, byoyomiTimeMs = 60_000)
        val p = PlayerState(timeRemainingMs = 10_000, isInByoyomi = true, movesRemainingInPeriod = 1)
        val after = computePostMoveState(ChessClockState(players = listOf(p, PlayerState(0)), activePlayer = 1), 1, 0, ChessClockSettings(main = s), s).players[0]
        assertEquals(1, after.movesRemainingInPeriod)
    }

    @Test
    fun `a byoyomi shortcut names its periods`() {
        val s = ChessClockSettings(main = PlayerSettings(mode = TimerMode.BYOYOMI_JAPANESE, initialTimeMs = 600_000, byoyomiPeriods = 5, byoyomiTimeMs = 30_000))
        val targets = buildShortcutTargets(history = listOf(GameLog(startTime = 7, settings = s)), presets = emptyList())
        assertEquals("10 min + 5×30s", targets.first().label)
    }
}
