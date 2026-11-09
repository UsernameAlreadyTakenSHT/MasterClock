package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test

/** Berserk as Lichess words it: half the time, no increment, 1+2 apart, never at 0+1 or 0+2. */
class BerserkTest {

    private fun settings(s: PlayerSettings, allow: Boolean = true, firstMove: Boolean = false) =
        ChessClockSettings(main = s, allowBerserk = allow, fischerFideFirstMove = firstMove)

    private fun fischer(minutes: Long, incrementSeconds: Long) =
        PlayerSettings(mode = TimerMode.FISCHER, initialTimeMs = minutes * 60_000, incrementMs = incrementSeconds * 1000)

    private fun start(settings: ChessClockSettings) = initialClockState(settings, ClockSim.RANDOM_ROLL)

    @After
    fun restoreFlavor() {
        FlavorConfig.currentFlavor = AppFlavor.COMPLETE
    }

    @Test
    fun `only the builds with a Behaviour tab offer it`() {
        val s = settings(fischer(3, 2))
        listOf(AppFlavor.COMPLETE, AppFlavor.STANDARD).forEach { flavor ->
            FlavorConfig.currentFlavor = flavor
            assertTrue("$flavor should offer it", canBerserk(start(s), 1, s))
        }
        listOf(AppFlavor.LITE, AppFlavor.MINI, AppFlavor.E_INK).forEach { flavor ->
            FlavorConfig.currentFlavor = flavor
            assertFalse("$flavor has no switch for it", canBerserk(start(s), 1, s))
            assertEquals(start(s), goBerserk(start(s), 1, s))
        }
    }

    @Test
    fun `it is refused unless the clock can pay all of it`() {
        // 3+2 with the first move's bonus: going Berserk costs 1:30 and the 2s advance. A clock that
        // had run down to 1:31 paid 1:30.999 and kept a millisecond.
        val p = fischer(3, 2)
        val s = settings(p, firstMove = true)
        val runDown = start(s).let { it.copy(players = it.players.mapIndexed { i, pl -> if (i == 1) pl.copy(timeRemainingMs = 91_000) else pl }) }
        assertFalse(canBerserk(runDown, 2, s))
        val enough = runDown.copy(players = runDown.players.mapIndexed { i, pl -> if (i == 1) pl.copy(timeRemainingMs = 93_000) else pl })
        assertEquals(1_000, goBerserk(enough, 2, s).players[1].timeRemainingMs)
    }

    @Test
    fun `a log opened mid-game starts from the time control, not from the interruption`() {
        val s = settings(PlayerSettings(mode = TimerMode.SUDDEN_DEATH, initialTimeMs = 600_000))
        val timeControl = start(s)
        val berserked = goBerserk(timeControl, 1, s)
        // Not started: the clock as it stands, the Berserk taken back.
        assertEquals(listOf(600_000L, 600_000L), gameStartPlayers(berserked, s) { timeControl }.map { it.timeRemainingMs })
        // Under way: a saved clock resumed with no log of its own, at 1:00 after a Berserk.
        val resumed = berserked.copy(activePlayer = 2, players = berserked.players.map { it.copy(timeRemainingMs = 60_000, moveCount = 12) })
        assertEquals(listOf(600_000L, 600_000L), gameStartPlayers(resumed, s) { timeControl }.map { it.timeRemainingMs })
    }

    @Test
    fun `the question says what it costs`() {
        // The dialog's text follows this: it promised half the time at 1+2, which keeps it.
        assertTrue(berserkHalvesTheTime(settings(fischer(3, 2)), 1))
        assertFalse(berserkHalvesTheTime(settings(fischer(1, 2)), 1))
    }

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
