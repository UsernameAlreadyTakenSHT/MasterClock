package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Covers [fideMoveTotals] and [adjustFideMoveCount]. */
class FideMoveTotalsTest {

    private fun periods(vararg counts: Int) = counts.map { FidePeriod(timeMs = 600_000, movesToNext = it) }

    @Test
    fun `totals from the start of the game are read as they are`() {
        assertEquals(listOf(40, 60, 0), fideMoveTotals(periods(40, 60, 0)))
    }

    @Test
    fun `a count typed per period is read as moves within it`() {
        assertEquals(listOf(40, 60, 0), fideMoveTotals(periods(40, 20, 0)))
        assertEquals(listOf(40, 80, 0), fideMoveTotals(periods(40, 40, 0)))
    }

    @Test
    fun `a period ending on time leaves the count before it in place`() {
        assertEquals(listOf(40, 0, 60), fideMoveTotals(periods(40, 0, 20)))
    }

    @Test
    fun `the third period of 2h-40 + 1h-20 typed per period comes at move 60, not 41`() {
        val s = PlayerSettings(mode = TimerMode.FIDE_PERIODS, fidePeriods = listOf(
            FidePeriod(timeMs = 7_200_000, movesToNext = 40), FidePeriod(timeMs = 3_600_000, movesToNext = 20), FidePeriod(timeMs = 1_800_000, movesToNext = 0),
        ))
        val settings = ChessClockSettings(main = s, forcedMoveCounter = true)
        val inSecond = PlayerState(timeRemainingMs = 3_000_000, currentPeriodIndex = 1, moveCount = 40)
        val after41 = computePostMoveState(ChessClockState(players = listOf(inSecond, PlayerState(0)), activePlayer = 1), 1, 0, settings, s).players[0]
        assertEquals(1, after41.currentPeriodIndex)
        val at59 = inSecond.copy(moveCount = 59)
        val after60 = computePostMoveState(ChessClockState(players = listOf(at59, PlayerState(0)), activePlayer = 1), 1, 0, settings, s).players[0]
        assertEquals(2, after60.currentPeriodIndex)
    }

    @Test
    fun `the arbiter's move that reaches the count starts the next period`() {
        val s = PlayerSettings(mode = TimerMode.FIDE_PERIODS, fidePeriods = periods(40, 0))
        val settings = ChessClockSettings(main = s, forcedMoveCounter = true)
        val p = PlayerState(timeRemainingMs = 60_000, moveCount = 39)
        val next = adjustFideMoveCount(p, 1, s, settings)
        assertEquals(40, next.moveCount)
        assertEquals(1, next.currentPeriodIndex)
        assertEquals(660_000, next.timeRemainingMs)
    }

    @Test
    fun `the arbiter's move taken back leaves the period and the clock`() {
        val s = PlayerSettings(mode = TimerMode.FIDE_PERIODS, fidePeriods = periods(40, 0))
        val settings = ChessClockSettings(main = s, forcedMoveCounter = true)
        val p = PlayerState(timeRemainingMs = 60_000, moveCount = 40, currentPeriodIndex = 1)
        val back = adjustFideMoveCount(p, -1, s, settings)
        assertEquals(39, back.moveCount)
        assertEquals(1, back.currentPeriodIndex)
        assertEquals(60_000, back.timeRemainingMs)
        assertEquals(0, adjustFideMoveCount(p.copy(moveCount = 0), -1, s, settings).moveCount)
    }

    @Test
    fun `a flagged player the arbiter brings back is no longer marked as flagged`() {
        val out = PlayerState(timeRemainingMs = 0, isOutOfTime = true, hasFlagged = true)
        val back = out.arbiterCredited(60_000, FlagBehavior.FLAG)
        assertFalse(back.isOutOfTime || back.hasFlagged)
    }

    @Test
    fun `a saved game keeps its own move counter rule`() {
        val saved = ChessClockSettings(forcedMoveCounter = false)
        assertFalse(restoreSavedTimeControl(ChessClockSettings(forcedMoveCounter = true), saved).forcedMoveCounter)
    }
}
