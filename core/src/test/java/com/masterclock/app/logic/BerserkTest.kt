package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Berserk as Lichess words it: half the time, no increment, 1+2 apart, never at 0+1 or 0+2. */
class BerserkTest {

    private fun settings(s: PlayerSettings, allow: Boolean = true, firstMove: Boolean = false) =
        ChessClockSettings(main = s, allowBerserk = allow, fischerFideFirstMove = firstMove)

    private fun fischer(minutes: Long, incrementSeconds: Long) =
        PlayerSettings(mode = TimerMode.FISCHER, initialTimeMs = minutes * 60_000, incrementMs = incrementSeconds * 1000)

    private fun start(settings: ChessClockSettings) = initialClockState(settings, ClockSim.RANDOM_ROLL)

    @Test
    fun `it is off unless asked for`() {
        val s = settings(fischer(3, 2), allow = false)
        assertFalse(canBerserk(start(s), 1, s))
    }

    @Test
    fun `it costs half the time, and only the player who chose it`() {
        val s = settings(PlayerSettings(mode = TimerMode.SUDDEN_DEATH, initialTimeMs = 600_000))
        val after = goBerserk(start(s), 1, s)
        assertEquals(300_000, after.players[0].timeRemainingMs)
        assertEquals(600_000, after.players[1].timeRemainingMs)
    }

    @Test
    fun `it cancels the increment`() {
        val p = fischer(3, 2)
        val s = settings(p)
        val after = computePostMoveState(goBerserk(start(s), 1, s).copy(activePlayer = 1), 1, 0, s, p)
        assertEquals(90_000, after.players[0].timeRemainingMs)
    }

    @Test
    fun `the other player keeps theirs`() {
        val p = fischer(3, 2)
        val s = settings(p)
        val after = computePostMoveState(goBerserk(start(s), 1, s).copy(activePlayer = 2), 2, 0, s, p)
        assertEquals(182_000, after.players[1].timeRemainingMs)
    }

    @Test
    fun `one plus two keeps its minute and loses its increment`() {
        val p = fischer(1, 2)
        val s = settings(p)
        val turned = goBerserk(start(s), 1, s)
        assertEquals(60_000, turned.players[0].timeRemainingMs)
        assertEquals(60_000, computePostMoveState(turned.copy(activePlayer = 1), 1, 0, s, p).players[0].timeRemainingMs)
    }

    @Test
    fun `no zero start`() {
        val s = settings(fischer(0, 1))
        assertFalse(canBerserk(start(s), 1, s))
    }

    @Test
    fun `a bonus before the first move goes with the increment`() {
        val s = settings(fischer(3, 2), firstMove = true)
        assertEquals(90_000, goBerserk(start(s), 1, s).players[0].timeRemainingMs)
    }

    @Test
    fun `not after the first move, and not twice`() {
        val p = fischer(3, 2)
        val s = settings(p)
        assertFalse(canBerserk(computePostMoveState(start(s).copy(activePlayer = 1), 1, 0, s, p), 1, s))
        assertFalse(canBerserk(goBerserk(start(s), 1, s), 1, s))
    }

    @Test
    fun `the second player can still go once the first has moved`() {
        val p = fischer(3, 2)
        val s = settings(p)
        assertTrue(canBerserk(computePostMoveState(start(s).copy(activePlayer = 1), 1, 0, s, p).copy(activePlayer = 2), 2, s))
    }

    @Test
    fun `on their own turn it comes off what is left`() {
        val p = fischer(3, 2)
        val s = settings(p)
        val running = start(s).let { it.copy(activePlayer = 1, players = it.players.mapIndexed { i, x -> if (i == 0) x.copy(timeRemainingMs = 170_000) else x }) }
        assertEquals(80_000, goBerserk(running, 1, s).players[0].timeRemainingMs)
    }

    @Test
    fun `a clock with no time of its player's own has none to give`() {
        val s = settings(PlayerSettings(mode = TimerMode.CHRONO_COUNTDOWN, initialTimeMs = 600_000))
        assertFalse(canBerserk(start(s), 1, s))
    }

    @Test
    fun `the clock it left is the one a replay starts from`() {
        val s = settings(fischer(3, 2), firstMove = true)
        val before = start(s)
        val after = goBerserk(before, 1, s)
        assertEquals(before.players[0], after.players[0].beforeBerserk(s.main, s))
    }

    @Test
    fun `a saved game remembers it`() {
        val s = settings(fischer(3, 2))
        assertTrue(goBerserk(start(s), 1, s).players[0].toProxy().toState().hasBerserked)
    }
}
