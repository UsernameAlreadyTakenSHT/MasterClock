package com.masterclock.app.logic

import org.junit.Test
import org.junit.Assert.*

/** FIDE periods as a FIDE clock -- the DGT2500's manual is the reference -- runs them. */
class FidePeriodsTest {

    // 90' for 40 moves, then 30', with 30s a move from move 1.
    private val classical = listOf(FidePeriod(timeMs = 5_400_000, incrementMs = 30_000, movesToNext = 40), FidePeriod(timeMs = 1_800_000, incrementMs = 30_000, movesToNext = 0))
    private val s = PlayerSettings(mode = TimerMode.FIDE_PERIODS, fidePeriods = classical)
    private val settings = ChessClockSettings(main = s)

    private fun press(p: PlayerState, spentMs: Long = 0) =
        computePostMoveState(ChessClockState(players = listOf(p, PlayerState(0)), activePlayer = 1), 1, spentMs, settings, s).players[0]

    @Test
    fun `the bonus is on the clock before move 1`() {
        val p = PlayerState(timeRemainingMs = 5_400_000 + 30_000)
        // After move 1, played in 10s, the bonus for move 2 comes on.
        assertEquals(5_450_000, press(p.copy(timeRemainingMs = p.timeRemainingMs - 10_000)).timeRemainingMs)
    }

    @Test
    fun `move 40 adds the next period and the bonus for move 41`() {
        val after = press(PlayerState(timeRemainingMs = 60_000, moveCount = 39))
        assertEquals(1, after.currentPeriodIndex)
        assertEquals(60_000 + 1_800_000 + 30_000, after.timeRemainingMs)
    }

    @Test
    fun `a bonus starting with a period starts with that period's first move`() {
        // The DGT manual's example: 30s from move 61, the count set to 60 in the period before.
        val periods = listOf(FidePeriod(timeMs = 600_000, incrementMs = 0, movesToNext = 60, isFischer = false), FidePeriod(timeMs = 0, incrementMs = 30_000, movesToNext = 0))
        val bonusLater = PlayerSettings(mode = TimerMode.FIDE_PERIODS, fidePeriods = periods)
        val after = computePostMoveState(ChessClockState(players = listOf(PlayerState(timeRemainingMs = 100_000, moveCount = 59), PlayerState(0)), activePlayer = 1), 1, 0, ChessClockSettings(main = bonusLater), bonusLater).players[0]
        assertEquals(130_000, after.timeRemainingMs)
    }

    @Test
    fun `a middle period with no move count ends on its time even with the move counter on`() {
        val periods = listOf(FidePeriod(timeMs = 60_000, movesToNext = 0), FidePeriod(timeMs = 120_000, movesToNext = 0))
        val noCount = PlayerSettings(mode = TimerMode.FIDE_PERIODS, fidePeriods = periods)
        val next = tickPlayer(PlayerState(timeRemainingMs = 100), 300, noCount, ChessClockSettings(main = noCount))
        assertEquals(1, next.currentPeriodIndex)
        assertEquals(119_800, next.timeRemainingMs)
        assertFalse(next.isOutOfTime)
    }

    @Test
    fun `with the move counter on, a period's time running out is a flag`() {
        val next = tickPlayer(PlayerState(timeRemainingMs = 100, moveCount = 30), 300, s, settings)
        assertTrue(next.isOutOfTime)
        assertEquals(0, next.currentPeriodIndex)
    }
}
