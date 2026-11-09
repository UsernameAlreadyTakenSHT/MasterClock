package com.masterclock.app.logic

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a game left running is kept alive in the background, and what its notification says. */
class BackgroundClockTest {

    @After
    fun restoreFlavor() {
        FlavorConfig.currentFlavor = AppFlavor.COMPLETE
    }

    private val asked = ChessClockSettings(pauseOnBackground = false, keepRunningInBackground = true)

    @Test
    fun `only when asked, with the pause off, where the Behaviour tab is`() {
        assertFalse(ChessClockSettings().runsInBackground())
        assertFalse(asked.copy(pauseOnBackground = true).runsInBackground())
        for (flavor in listOf(AppFlavor.COMPLETE, AppFlavor.STANDARD)) {
            FlavorConfig.currentFlavor = flavor
            assertTrue(flavor.name, asked.runsInBackground())
        }
        for (flavor in listOf(AppFlavor.LITE, AppFlavor.MINI, AppFlavor.E_INK)) {
            FlavorConfig.currentFlavor = flavor
            assertFalse(flavor.name, asked.runsInBackground())
        }
    }

    private fun game(vararg players: PlayerState, active: Int? = 1, paused: Boolean = false) =
        ChessClockState(players = players.toList(), activePlayer = active, isPaused = paused)

    @Test
    fun `nothing to keep alive unless a clock runs`() {
        assertNull(backgroundClockOf(game(PlayerState(60_000), PlayerState(60_000), active = null), asked))
        assertNull(backgroundClockOf(game(PlayerState(60_000), PlayerState(60_000), paused = true), asked))
        val flagged = game(PlayerState(0, isOutOfTime = true), PlayerState(60_000)).copy(firstToFlag = 1, isPaused = true)
        assertNull(backgroundClockOf(flagged, asked))
    }

    @Test
    fun `the player on the move and their time`() {
        val running = game(PlayerState(60_000), PlayerState(42_000), active = 2)
        assertEquals(BackgroundClock(playerIndex = 2, countdownMs = 42_000), backgroundClockOf(running, asked))
    }

    @Test
    fun `Hidden shows no time, and a delay counts towards the flag`() {
        val hidden = asked.copy(main = PlayerSettings(mode = TimerMode.HIDDEN))
        assertNull(backgroundClockOf(game(PlayerState(42_000), PlayerState(42_000)), hidden)!!.countdownMs)
        val delay = asked.copy(main = PlayerSettings(mode = TimerMode.US_DELAY, incrementMs = 5_000))
        assertEquals(47_000L, backgroundClockOf(game(PlayerState(42_000, delayRemainingMs = 5_000), PlayerState(60_000)), delay)!!.countdownMs)
    }

    @Test
    fun `a countdown that keeps running needs no redraw, one that jumped does`() {
        val shown = NotifiedCountdown.of(BackgroundClock(1, 60_000), nowElapsedMs = 0)
        // A second later the clock has run a second: the same end.
        assertFalse(shown.outOfStepWith(NotifiedCountdown.of(BackgroundClock(1, 59_000), nowElapsedMs = 1_000)))
        // Shared (Auto) handed the turn on, the table's clock refilled: nobody's turn changed, the end did.
        assertTrue(shown.outOfStepWith(NotifiedCountdown.of(BackgroundClock(null, 30_000), nowElapsedMs = 30_000)))
        assertTrue(NotifiedCountdown.of(BackgroundClock(null, 1_000), 29_000).outOfStepWith(NotifiedCountdown.of(BackgroundClock(null, 30_000), 30_000)))
        // Byoyomi starting within the turn.
        assertTrue(shown.outOfStepWith(NotifiedCountdown.of(BackgroundClock(1, 30_000), nowElapsedMs = 60_000)))
        // The turn passing, or the countdown going.
        assertTrue(shown.outOfStepWith(NotifiedCountdown.of(BackgroundClock(2, 59_000), nowElapsedMs = 1_000)))
        assertTrue(shown.outOfStepWith(NotifiedCountdown.of(BackgroundClock(1, null), nowElapsedMs = 1_000)))
    }

    @Test
    fun `a clock that counts up has no countdown`() {
        val stopwatch = asked.copy(main = PlayerSettings(mode = TimerMode.CHRONO_COUNTUP))
        val clock = backgroundClockOf(game(PlayerState(0), PlayerState(0)).copy(globalTimeMs = 5_000), stopwatch)
        assertNull(clock!!.countdownMs)
        assertNull("the table's clock is nobody's", clock.playerIndex)
        val overrun = asked.copy(flagBehavior = FlagBehavior.NEGATIVE)
        val over = game(PlayerState(3_000, isOutOfTime = true, isNegative = true), PlayerState(60_000))
        assertNull(backgroundClockOf(over, overrun)!!.countdownMs)
    }
}
