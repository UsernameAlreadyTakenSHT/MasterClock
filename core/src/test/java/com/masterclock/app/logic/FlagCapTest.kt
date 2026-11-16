package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers [ChessClockSettings.flagCapMs]: how far a clock runs past zero under NEGATIVE and REVERSE. */
class FlagCapTest {

    private fun settings(fb: FlagBehavior, capMs: Long) = ChessClockSettings(
        main = PlayerSettings(mode = TimerMode.SUDDEN_DEATH, initialTimeMs = 60_000),
        flagBehavior = fb,
        flagCapMs = capMs,
        tripleBeepTimeUp = true,
    )

    private fun running(p1Ms: Long) = ChessClockState(listOf(PlayerState(p1Ms), PlayerState(60_000)), activePlayer = 1)

    /** Runs the clock [totalMs] on, a second at a time as the app does, and returns every tick. */
    private fun run(start: ChessClockState, settings: ChessClockSettings, totalMs: Long): List<ClockTick> {
        val ticks = mutableListOf<ClockTick>()
        var state = start
        var left = totalMs
        while (left > 0) {
            val tick = tickClock(state, minOf(1_000, left), settings)
            ticks += tick
            state = tick.state
            left -= 1_000
        }
        return ticks
    }

    @Test
    fun `under REVERSE the overrun climbs to the cap and stops there`() {
        val s = settings(FlagBehavior.REVERSE, 10_000)
        val ticks = run(running(2_000), s, 30_000)
        val p1 = ticks.last().state.players[0]
        assertTrue(p1.isOutOfTime)
        assertEquals(10_000, p1.timeRemainingMs)
        assertFalse(p1.isNegative)
    }

    @Test
    fun `under NEGATIVE the overrun goes down to minus the cap and stops there`() {
        val s = settings(FlagBehavior.NEGATIVE, 10_000)
        val ticks = run(running(2_000), s, 30_000)
        val p1 = ticks.last().state.players[0]
        assertTrue(p1.isOutOfTime)
        assertTrue(p1.isNegative)
        assertEquals(10_000, p1.timeRemainingMs)
    }

    @Test
    fun `the game goes on at the cap, and the cap is heard once`() {
        for (fb in listOf(FlagBehavior.NEGATIVE, FlagBehavior.REVERSE)) {
            val s = settings(fb, 10_000)
            val ticks = run(running(2_000), s, 30_000)
            assertEquals(fb.name, 1, ticks.count { it.capReached })
            assertFalse(fb.name, isGameOver(ticks.last().state, fb, 2))
            assertFalse(fb.name, ticks.last().state.isPaused)
            val atCap = ticks.first { it.capReached }
            assertTrue(fb.name, ClockSound.TRIPLE_BEEP in tickSounds(atCap, s, -1))
            // Silent with the time-up sound off, as the flag itself is.
            assertFalse(fb.name, ClockSound.TRIPLE_BEEP in tickSounds(atCap, s.copy(tripleBeepTimeUp = false), -1))
        }
    }

    @Test
    fun `no cap, no limit`() {
        val s = settings(FlagBehavior.REVERSE, 0)
        val ticks = run(running(2_000), s, 30_000)
        assertEquals(28_000, ticks.last().state.players[0].timeRemainingMs)
        assertTrue(ticks.none { it.capReached })
    }

    @Test
    fun `the cap means nothing under FREEZE and FLAG`() {
        for (fb in listOf(FlagBehavior.FREEZE, FlagBehavior.FLAG)) {
            val s = settings(fb, 10_000)
            val state = ChessClockState(listOf(PlayerState(0, isOutOfTime = true), PlayerState(60_000)), activePlayer = 1)
            assertSame(fb.name, state, state.capOverrun(s))
        }
    }

    @Test
    fun `a clock with time left is not an overrun to cap`() {
        val s = settings(FlagBehavior.REVERSE, 10_000)
        val state = running(45_000)
        assertSame(state, state.capOverrun(s))
    }

    @Test
    fun `an arbiter's penalty stops at the cap`() {
        for (fb in listOf(FlagBehavior.NEGATIVE, FlagBehavior.REVERSE)) {
            val s = settings(fb, 10_000)
            val penalised = arbiterAdjustTime(running(5_000), 1, -60_000, s)
            assertTrue(fb.name, penalised.players[0].isOutOfTime)
            assertEquals(fb.name, 10_000, penalised.players[0].timeRemainingMs)
        }
    }

    @Test
    fun `time given back takes a capped clock off its cap`() {
        val s = settings(FlagBehavior.REVERSE, 10_000)
        val capped = run(running(2_000), s, 30_000).last().state
        val relieved = arbiterAdjustTime(capped, 1, 4_000, s)
        assertEquals(6_000, relieved.players[0].timeRemainingMs)
        assertTrue(relieved.players[0].isOutOfTime)
    }

    @Test
    fun `a preset brings its cap with its flag behaviour, and a built-in leaves the player's`() {
        val mine = ChessClockSettings(flagBehavior = FlagBehavior.NEGATIVE, flagCapMs = 30_000)
        val scrabble = ChessClockSettings(flagBehavior = FlagBehavior.REVERSE, flagCapMs = 600_000)
        val taken = applyPresetTimeControl(mine, scrabble)
        assertEquals(FlagBehavior.REVERSE, taken.flagBehavior)
        assertEquals(600_000, taken.flagCapMs)
        val kept = applyPresetTimeControl(mine, ChessClockSettings(), keepFlagBehavior = true)
        assertEquals(FlagBehavior.NEGATIVE, kept.flagBehavior)
        assertEquals(30_000, kept.flagCapMs)
    }
}
