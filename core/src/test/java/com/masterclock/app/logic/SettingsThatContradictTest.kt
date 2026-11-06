package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Accelerate's arithmetic, and the clock given settings that cannot all hold. */
class SettingsThatContradictTest {

    private fun accelerate(rate: Float, grace: Long = 0, fullAt: Long = 3_600_000) = PlayerSettings(
        mode = TimerMode.FAST_MOVE, fastMoveMode = FastMoveType.ACCELERATE,
        fastMoveAccelRate = rate, fastMoveGracePeriodMs = grace, fastMoveFastPeriodMs = fullAt,
    )

    private fun run(s: PlayerSettings, tickMs: Long, ticks: Int): PlayerState {
        val settings = ChessClockSettings(main = s)
        var p = PlayerState(1_000_000, initialTotalTimeMs = 1_000_000)
        repeat(ticks) { p = tickPlayer(p, tickMs, s, settings) }
        return p
    }

    @Test
    fun `no part of the acceleration is lost to a coarse or a fine tick`() {
        assertEquals(1_000_000L - 11_500, run(accelerate(0.15f), 100, 100).timeRemainingMs)
        assertEquals(1_000_000L - 11_500, run(accelerate(0.15f), 10, 1_000).timeRemainingMs)
    }

    @Test
    fun `the default rates come out exact as before`() {
        assertEquals(1_000_000L - 15_000, run(accelerate(0.5f), 100, 100).timeRemainingMs)
    }

    @Test
    fun `full speed never starts inside the grace period`() {
        val s = accelerate(0.5f, grace = 10_000, fullAt = 5_000).copy(fastMoveFullAccelRate = 2f)
        assertEquals(1_000_000L - 8_000, run(s, 100, 80).timeRemainingMs)
    }

    @Test
    fun `a floor above the clock does not raise it`() {
        val s = PlayerSettings(mode = TimerMode.FAST_MOVE, fastMoveMode = FastMoveType.SHRINK, fastMoveShrinkDecrementMs = 5_000, fastMoveShrinkFloorMs = 60_000)
        val settings = ChessClockSettings(main = s)
        val state = ChessClockState(listOf(PlayerState(25_000, initialTotalTimeMs = 30_000), PlayerState(30_000)), activePlayer = 1)
        assertEquals(30_000, computePostMoveState(state, 1, 5_000, settings, s).players[0].timeRemainingMs)
    }

    @Test
    fun `a floor below the clock still holds it`() {
        val s = PlayerSettings(mode = TimerMode.FAST_MOVE, fastMoveMode = FastMoveType.SHRINK, fastMoveShrinkDecrementMs = 20_000, fastMoveShrinkFloorMs = 15_000)
        val settings = ChessClockSettings(main = s)
        val state = ChessClockState(listOf(PlayerState(25_000, initialTotalTimeMs = 30_000), PlayerState(30_000)), activePlayer = 1)
        assertEquals(15_000, computePostMoveState(state, 1, 5_000, settings, s).players[0].timeRemainingMs)
    }

    private fun overtimeCue(p: PlayerState, delta: Long): TickCue {
        val s = PlayerSettings(mode = TimerMode.MOVE_TIMER_OVERTIME, moveTimeMs = 30_000)
        val state = ChessClockState(listOf(p, PlayerState(30_000)), activePlayer = 1)
        return tickClock(state, delta, ChessClockSettings(main = s)).cue!!
    }

    @Test
    fun `Overtime's voice counts the reserve behind the move`() {
        val cue = overtimeCue(PlayerState(11_000, secondaryTimeMs = 60_000), 2_000)
        assertEquals(71_000L to 69_000L, cue.timeLeft)
    }

    @Test
    fun `Overtime's voice counts down the reserve once the move is spent`() {
        val cue = overtimeCue(PlayerState(0, secondaryTimeMs = 11_000), 2_000)
        assertEquals(11_000L to 9_000L, cue.timeLeft)
    }

    @Test
    fun `a count of no moves starts from one`() {
        val settings = ChessClockSettings(main = PlayerSettings(mode = TimerMode.MOVE_COUNTS_DOWN, maxMoves = 0))
        val state = initialClockState(settings, ClockSim.RANDOM_ROLL)
        assertEquals(1, state.players[0].moveCount)
        assertFalse(state.players[0].isOutOfTime)
        assertEquals("1 moves", timeControlLabel(settings))
    }
}
