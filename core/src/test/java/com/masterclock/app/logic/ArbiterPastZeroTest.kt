package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A penalty larger than the clock, on the clocks that do not simply run out. */
class ArbiterPastZeroTest {

    private fun game(p: PlayerState) = ChessClockState(listOf(p, PlayerState(60_000)), activePlayer = 1, isPaused = true)

    @Test
    fun `a stopwatch stops at zero`() {
        for (fb in FlagBehavior.entries) {
            val settings = ChessClockSettings(main = PlayerSettings(mode = TimerMode.CHRONO_COUNTUP), flagBehavior = fb)
            val after = arbiterAdjustTime(game(PlayerState(0)).copy(globalTimeMs = 20_000), 1, -60_000, settings)
            assertEquals(fb.name, 0L, after.globalTimeMs)
            assertTrue(fb.name, after.players.none { it.isOutOfTime })
        }
    }

    @Test
    fun `a countdown past zero is the table's clock running out`() {
        for (fb in FlagBehavior.entries) {
            val settings = ChessClockSettings(main = PlayerSettings(mode = TimerMode.CHRONO_COUNTDOWN), flagBehavior = fb)
            val after = arbiterAdjustTime(game(PlayerState(0)).copy(globalTimeMs = 20_000), 1, -60_000, settings)
            assertNull(fb.name, after.firstToFlag)
            assertTrue(fb.name, isGameOver(after, fb, 2))
        }
    }

    @Test
    fun `a delay shields no one from a penalty, and survives it`() {
        val s = PlayerSettings(
            mode = TimerMode.FIDE_PERIODS,
            fidePeriods = listOf(
                FidePeriod(timeMs = 10_000, incrementMs = 5_000, movesToNext = 0, isFischer = false, hasDelay = true),
                FidePeriod(timeMs = 30_000, incrementMs = 5_000, movesToNext = 0, isFischer = false, hasDelay = true),
            ),
        )
        val after = arbiterAdjustTime(game(PlayerState(10_000, delayRemainingMs = 5_000)), 1, -15_000, ChessClockSettings(main = s))
        assertEquals(1, after.players[0].currentPeriodIndex)
        // All fifteen seconds came off: five past the first period, off the next. Ticked through the
        // delay, it would have stopped the penalty at ten and left the second period whole.
        assertEquals(25_000, after.players[0].timeRemainingMs)
        assertEquals(5_000, after.players[0].delayRemainingMs)
    }

    private fun fideWithDelay(vararg periods: FidePeriod) = PlayerSettings(mode = TimerMode.FIDE_PERIODS, fidePeriods = periods.toList())

    @Test
    fun `a player the penalty flags keeps no delay`() {
        val s = fideWithDelay(FidePeriod(timeMs = 10_000, incrementMs = 5_000, movesToNext = 0, hasDelay = true))
        val settings = ChessClockSettings(main = s, flagBehavior = FlagBehavior.NEGATIVE, forcedMoveCounter = false)
        val after = arbiterAdjustTime(game(PlayerState(10_000, delayRemainingMs = 3_000)), 1, -15_000, settings)
        assertTrue(after.players[0].isOutOfTime)
        // Kept, the overrun stood still for those three seconds after Resume.
        assertEquals(0, after.players[0].delayRemainingMs)
    }

    @Test
    fun `a waiting player taken into the next period gets that period's delay`() {
        val waiting = { p: PlayerState -> ChessClockState(listOf(PlayerState(60_000), p), activePlayer = 1, isPaused = true) }
        val toFischer = fideWithDelay(
            FidePeriod(timeMs = 10_000, incrementMs = 5_000, movesToNext = 0, hasDelay = true),
            FidePeriod(timeMs = 30_000, incrementMs = 30_000, movesToNext = 0, isFischer = true),
        )
        val fischer = arbiterAdjustTime(waiting(PlayerState(10_000, delayRemainingMs = 5_000)), 2, -15_000, ChessClockSettings(main = toFischer, forcedMoveCounter = false))
        assertEquals(1, fischer.players[1].currentPeriodIndex)
        assertEquals("a Fischer period has no delay", 0, fischer.players[1].delayRemainingMs)

        val toDelay = fideWithDelay(
            FidePeriod(timeMs = 10_000, incrementMs = 0, movesToNext = 0, isFischer = false),
            FidePeriod(timeMs = 30_000, incrementMs = 10_000, movesToNext = 0, isFischer = false, hasDelay = true),
        )
        val delayed = arbiterAdjustTime(waiting(PlayerState(10_000)), 2, -15_000, ChessClockSettings(main = toDelay, forcedMoveCounter = false))
        assertEquals(1, delayed.players[1].currentPeriodIndex)
        assertEquals(10_000, delayed.players[1].delayRemainingMs)
    }

    @Test
    fun `Gong takes no flag`() {
        val s = PlayerSettings(mode = TimerMode.GONG)
        val after = arbiterAdjustTime(game(PlayerState(20_000)), 1, -60_000, ChessClockSettings(main = s))
        assertFalse(after.players[0].isOutOfTime)
        assertEquals(0, after.players[0].timeRemainingMs)
    }

    @Test
    fun `byoyomi is entered, not skipped`() {
        val s = PlayerSettings(mode = TimerMode.BYOYOMI_JAPANESE, byoyomiPeriods = 5, byoyomiTimeMs = 30_000)
        val after = arbiterAdjustTime(game(PlayerState(20_000, byoyomiPeriodsRemaining = 5)), 1, -30_000, ChessClockSettings(main = s))
        assertFalse(after.players[0].isOutOfTime)
        assertTrue(after.players[0].isInByoyomi)
    }

    @Test
    fun `a period gone on its time is the next one`() {
        val s = PlayerSettings(mode = TimerMode.FIDE_PERIODS, fidePeriods = listOf(FidePeriod(timeMs = 60_000), FidePeriod(timeMs = 30_000)))
        val settings = ChessClockSettings(main = s, forcedMoveCounter = false)
        val after = arbiterAdjustTime(game(PlayerState(20_000)), 1, -30_000, settings)
        assertFalse(after.players[0].isOutOfTime)
        assertEquals(1, after.players[0].currentPeriodIndex)
    }

    @Test
    fun `a sudden death still flags`() {
        val after = arbiterAdjustTime(game(PlayerState(20_000)), 1, -30_000, ChessClockSettings(main = PlayerSettings(mode = TimerMode.SUDDEN_DEATH)))
        assertTrue(after.players[0].isOutOfTime)
    }

    @Test
    fun `a board move on a clock that takes no presses is only noted`() {
        val gong = ChessClockSettings(main = PlayerSettings(mode = TimerMode.GONG))
        assertFalse(gong.takesMovePresses(1))
        assertEquals(BoardMoveOutcome.NoteOnly("e2e4", 1), boardMoveOutcome("e2e4", 1, false, true, takesPresses = false))
        assertTrue(ChessClockSettings(main = PlayerSettings(mode = TimerMode.FISCHER)).takesMovePresses(1))
    }
}
