package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a time control is called, what a period gains, and which clock a move goes down with. */
class LabelsAndLogTest {

    private fun label(s: PlayerSettings) = timeControlLabel(ChessClockSettings(main = s))

    @Test
    fun `a random range under a minute is read in seconds`() {
        assertEquals("Random 30-90s", label(PlayerSettings(mode = TimerMode.RANDOM, randomMinTimeMs = 30_000, randomMaxTimeMs = 90_000)))
        assertEquals("Random 1-10 min", label(PlayerSettings(mode = TimerMode.RANDOM, randomMinTimeMs = 60_000, randomMaxTimeMs = 600_000)))
    }

    @Test
    fun `a period's seconds are in its label`() {
        val s = PlayerSettings(mode = TimerMode.FIDE_PERIODS, fidePeriods = listOf(FidePeriod(timeMs = 90_000, movesToNext = 40, incrementMs = 0), FidePeriod(timeMs = 60_000, movesToNext = 0, incrementMs = 0)))
        assertEquals("1'30/40 + 1'", label(s))
    }

    @Test
    fun `clocks that do not run down plainly say what they are`() {
        assertEquals("Hourglass 5 min", label(PlayerSettings(mode = TimerMode.HOURGLASS, initialTimeMs = 300_000)))
        assertEquals("Chrono Countdown 5 min", label(PlayerSettings(mode = TimerMode.CHRONO_COUNTDOWN, initialTimeMs = 300_000)))
        assertEquals("Accelerate 5 min", label(PlayerSettings(mode = TimerMode.FAST_MOVE, fastMoveMode = FastMoveType.ACCELERATE, initialTimeMs = 300_000)))
        assertEquals("Shrink 5 min", label(PlayerSettings(mode = TimerMode.FAST_MOVE, fastMoveMode = FastMoveType.SHRINK, initialTimeMs = 300_000)))
    }

    @Test
    fun `a default FIDE control has no count on its last period`() {
        assertEquals(0, PlayerSettings().fidePeriods.last().movesToNext)
    }

    @Test
    fun `a period added to a delay control is a delay period`() {
        val delay = FidePeriod(isFischer = false, hasDelay = true, incrementMs = 30_000, movesToNext = 0)
        val added = withPeriodAdded(listOf(delay)).last()
        assertFalse(added.isFischer)
        assertTrue(added.hasDelay)
        assertEquals(30_000, added.incrementMs)
    }

    @Test
    fun `a period added to a plain control gives no bonus`() {
        val added = withPeriodAdded(listOf(FidePeriod(isFischer = false, hasDelay = false, movesToNext = 0))).last()
        assertFalse(added.isFischer || added.hasDelay)
    }

    @Test
    fun `the period left last has no count to reach`() {
        val left = withPeriodRemoved(listOf(FidePeriod(movesToNext = 40), FidePeriod(movesToNext = 60), FidePeriod(movesToNext = 0)), 2)
        assertEquals(listOf(40, 0), left.map { it.movesToNext })
    }

    @Test
    fun `removing a middle period keeps the others`() {
        val left = withPeriodRemoved(listOf(FidePeriod(movesToNext = 40), FidePeriod(movesToNext = 60), FidePeriod(movesToNext = 0)), 1)
        assertEquals(listOf(40, 0), left.map { it.movesToNext })
    }

    @Test
    fun `a Hidden reveal ends with the move`() {
        val s = PlayerSettings(mode = TimerMode.HIDDEN)
        val state = ChessClockState(listOf(PlayerState(60_000, revealTimeUntilMs = 2_000), PlayerState(60_000)), activePlayer = 1)
        assertEquals(0, computePostMoveState(state, 1, 1_000, ChessClockSettings(main = s), s).players[0].revealTimeUntilMs)
    }

    @Test
    fun `a move goes down with the clock that was running out`() {
        val fb = FlagBehavior.FREEZE
        val chrono = ChessClockState(listOf(PlayerState(0), PlayerState(0)), activePlayer = 1, globalTimeMs = 42_000)
        assertEquals(42_000L, loggedMoveTimeMs(chrono, chrono.players[0], TimerMode.CHRONO_COUNTDOWN, fb))
        val global = PlayerState(5_000, secondaryTimeMs = 3_000)
        assertEquals(3_000L, loggedMoveTimeMs(chrono, global, TimerMode.MOVE_TIMER_GLOBAL, fb))
    }

    @Test
    fun `a move count has no clock to log, and an overrun no time left`() {
        val state = ChessClockState(listOf(PlayerState(0), PlayerState(0)), activePlayer = 1)
        assertNull(loggedMoveTimeMs(state, state.players[0], TimerMode.MOVE_COUNTS_DOWN, FlagBehavior.FREEZE))
        assertNull(loggedMoveTimeMs(state, state.players[0], TimerMode.MOVE_COUNTS_UP, FlagBehavior.FREEZE))
        val over = PlayerState(5_000, isOutOfTime = true, isNegative = true)
        assertEquals(0L, loggedMoveTimeMs(state, over, TimerMode.SUDDEN_DEATH, FlagBehavior.NEGATIVE))
    }
}
