package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers [fideMoveTotals] and [adjustFideMoveCount]. */
class FideMoveTotalsTest {

    private fun fide(vararg counts: Int) = PlayerSettings(mode = TimerMode.FIDE_PERIODS, fidePeriods = counts.map { FidePeriod(movesToNext = it) })

    @Test
    fun `moves left count down to the period's total`() {
        val s = fide(40, 20, 0)
        val settings = ChessClockSettings(main = s)
        assertEquals(15, fideMovesLeft(PlayerState(0, moveCount = 25), s, settings))
        assertEquals(0, fideMovesLeft(PlayerState(0, moveCount = 45), s, settings))
        assertEquals(10, fideMovesLeft(PlayerState(0, moveCount = 50, currentPeriodIndex = 1), s, settings))
    }

    @Test
    fun `no moves left in the last period, one ending on its time, or without the counter`() {
        val s = fide(40, 0, 20)
        val settings = ChessClockSettings(main = s)
        assertNull(fideMovesLeft(PlayerState(0, currentPeriodIndex = 2), s, settings))
        assertNull(fideMovesLeft(PlayerState(0, currentPeriodIndex = 1), s, settings))
        assertNull(fideMovesLeft(PlayerState(0), s, settings.copy(forcedMoveCounter = false)))
        assertNull(fideMovesLeft(PlayerState(0), PlayerSettings(), settings))
    }

    @Test
    fun `a period added gives the one before it a count, and has none itself`() {
        assertEquals(listOf(40, 0), withPeriodAdded(listOf(FidePeriod(movesToNext = 0))).map { it.movesToNext })
        assertEquals(listOf(40, 60, 0), withPeriodAdded(listOf(FidePeriod(movesToNext = 40), FidePeriod(movesToNext = 0))).map { it.movesToNext })
    }

    @Test
    fun `a period added counts from the totals the clock uses`() {
        val after = withPeriodAdded(listOf(FidePeriod(movesToNext = 40), FidePeriod(movesToNext = 20), FidePeriod(movesToNext = 0)))
        assertEquals(listOf(40, 60, 80, 0), fideMoveTotals(after))
    }

    @Test
    fun `a last period that already has a count keeps it`() {
        assertEquals(listOf(30, 0), withPeriodAdded(listOf(FidePeriod(movesToNext = 30))).map { it.movesToNext })
    }

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
    fun `the arbiter's move taken back undoes the period it started`() {
        val s = PlayerSettings(mode = TimerMode.FIDE_PERIODS, fidePeriods = periods(40, 0))
        val settings = ChessClockSettings(main = s, forcedMoveCounter = true)
        val p = PlayerState(timeRemainingMs = 60_000, moveCount = 39)
        val there = adjustFideMoveCount(p, 1, s, settings)
        val back = adjustFideMoveCount(there, -1, s, settings)
        assertEquals(39, back.moveCount)
        assertEquals(0, back.currentPeriodIndex)
        assertEquals(60_000, back.timeRemainingMs)
        assertEquals(0, adjustFideMoveCount(p.copy(moveCount = 0), -1, s, settings).moveCount)
    }

    @Test
    fun `a period the arbiter starts brings its own bonus for the move to come`() {
        val s = PlayerSettings(mode = TimerMode.FIDE_PERIODS, fidePeriods = listOf(
            FidePeriod(timeMs = 7_200_000, incrementMs = 0, movesToNext = 40, isFischer = true),
            FidePeriod(timeMs = 3_600_000, incrementMs = 0, movesToNext = 60, isFischer = true),
            FidePeriod(timeMs = 900_000, incrementMs = 30_000, movesToNext = 0, isFischer = true),
        ))
        val settings = ChessClockSettings(main = s, forcedMoveCounter = true)
        val p = PlayerState(timeRemainingMs = 100_000, moveCount = 59, currentPeriodIndex = 1)
        assertEquals(100_000 + 900_000 + 30_000, adjustFideMoveCount(p, 1, s, settings).timeRemainingMs)
    }

    @Test
    fun `a flagged player the arbiter brings back is no longer marked as flagged`() {
        val out = PlayerState(timeRemainingMs = 0, isOutOfTime = true, hasFlagged = true)
        val back = out.arbiterCredited(60_000, FlagBehavior.FLAG)
        assertFalse(back.isOutOfTime || back.hasFlagged)
    }

    @Test
    fun `a saved FIDE game keeps its own move counter rule, and another control leaves the switch`() {
        val fide = PlayerSettings(mode = TimerMode.FIDE_PERIODS)
        val saved = ChessClockSettings(main = fide, p1Custom = fide, forcedMoveCounter = false)
        assertFalse(restoreSavedTimeControl(ChessClockSettings(forcedMoveCounter = true), saved).forcedMoveCounter)
        val fischer = ChessClockSettings(main = PlayerSettings(mode = TimerMode.FISCHER), forcedMoveCounter = false)
        assertTrue(applyPresetTimeControl(ChessClockSettings(forcedMoveCounter = true), fischer).forcedMoveCounter)
    }

    @Test
    fun `the label counts a period's moves from the last period a count ended`() {
        val s = ChessClockSettings(main = PlayerSettings(mode = TimerMode.FIDE_PERIODS, fidePeriods = listOf(
            FidePeriod(timeMs = 7_200_000, incrementMs = 0, movesToNext = 40), FidePeriod(timeMs = 1_800_000, incrementMs = 0, movesToNext = 0),
            FidePeriod(timeMs = 3_600_000, incrementMs = 0, movesToNext = 20), FidePeriod(timeMs = 900_000, incrementMs = 0, movesToNext = 0),
        )))
        assertEquals("120'/40 + 30' + 60'/20 + 15'", timeControlLabel(s))
    }

    @Test
    fun `the arbiter's move brings a player flagged under FREEZE back with the period's time`() {
        val s = PlayerSettings(mode = TimerMode.FIDE_PERIODS, fidePeriods = periods(40, 0))
        val settings = ChessClockSettings(main = s, forcedMoveCounter = true, flagBehavior = FlagBehavior.FREEZE)
        val out = PlayerState(timeRemainingMs = 0, moveCount = 39, isOutOfTime = true, hasFlagged = true)
        val next = adjustFideMoveCount(out, 1, s, settings)
        assertEquals(1, next.currentPeriodIndex)
        assertEquals(600_000, next.timeRemainingMs)
        assertFalse(next.isOutOfTime || next.hasFlagged)
    }

    @Test
    fun `a flag under NEGATIVE or REVERSE ends when the arbiter's time pays it off`() {
        listOf(FlagBehavior.NEGATIVE, FlagBehavior.REVERSE).forEach { fb ->
            val over = PlayerState(timeRemainingMs = 10_000, isOutOfTime = true, isNegative = fb == FlagBehavior.NEGATIVE, hasFlagged = true)
            val back = over.arbiterCredited(60_000, fb)
            assertFalse(fb.name, back.isOutOfTime || back.hasFlagged)
        }
    }

    @Test
    fun `a flag before move 40 ends when move 40 pays the overrun off`() {
        val s = PlayerSettings(mode = TimerMode.FIDE_PERIODS, fidePeriods = periods(40, 0))
        val settings = ChessClockSettings(main = s, forcedMoveCounter = true, flagBehavior = FlagBehavior.NEGATIVE)
        val over = PlayerState(timeRemainingMs = 20_000, moveCount = 39, isOutOfTime = true, isNegative = true, hasFlagged = true)
        val after = computePostMoveState(ChessClockState(players = listOf(over, PlayerState(0)), activePlayer = 1), 1, 0, settings, s).players[0]
        assertFalse(after.isOutOfTime || after.hasFlagged)
    }
}
