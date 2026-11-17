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

    @Test
    fun `every phone build saves the game in play, and Mini takes it up at launch`() {
        for (flavor in listOf(AppFlavor.COMPLETE, AppFlavor.STANDARD, AppFlavor.LITE)) {
            FlavorConfig.currentFlavor = flavor
            assertTrue(flavor.name, FlavorConfig.savesGameInPlay())
            // The arbiter's panel offers it there; nothing is resumed unasked.
            assertFalse(flavor.name, FlavorConfig.resumesSavedGameAtLaunch())
        }
        // No panel to ask: the game was lost, and now comes back by itself.
        FlavorConfig.currentFlavor = AppFlavor.MINI
        assertTrue(FlavorConfig.savesGameInPlay())
        assertTrue(FlavorConfig.resumesSavedGameAtLaunch())
        FlavorConfig.currentFlavor = AppFlavor.E_INK
        assertFalse(FlavorConfig.savesGameInPlay())
        assertFalse(FlavorConfig.resumesSavedGameAtLaunch())
    }

    @Test
    fun `the save interval is one of the choices, two seconds unless set`() {
        assertEquals(2_000L, ChessClockSettings().autoSaveEveryMs())
        for (choice in AUTO_SAVE_CHOICES_MS) assertEquals(choice, ChessClockSettings(autoSaveIntervalMs = choice).autoSaveEveryMs())
        // Anything else, from an import or an older file, is the default: never a save every millisecond.
        for (odd in listOf(0L, 1L, 999L, 3_000L, -5L, Long.MAX_VALUE)) assertEquals(2_000L, ChessClockSettings(autoSaveIntervalMs = odd).autoSaveEveryMs())
    }

    @Test
    fun `the clock ticks on the second only where the face shows whole seconds alone`() {
        // Tenths under ten seconds, as the settings come.
        val s = ChessClockSettings()
        val plain = PlayerState(120_000)
        for (mode in listOf(TimerMode.SUDDEN_DEATH, TimerMode.FISCHER, TimerMode.BRONSTEIN, TimerMode.US_DELAY, TimerMode.MOVE_TIMER_STANDARD,
            TimerMode.BYOYOMI_JAPANESE, TimerMode.BYOYOMI_CANADIAN, TimerMode.BYOYOMI_PROGRESSIVE, TimerMode.FIDE_PERIODS)) {
            assertTrue(mode.name, ticksOnTheSecond(mode, plain, s, 120_000))
        }
        // Two clocks moving, a rate of its own, or the table's clock: ten times a second, as before.
        for (mode in listOf(TimerMode.HOURGLASS, TimerMode.GONG, TimerMode.MOVE_TIMER_GLOBAL, TimerMode.MOVE_TIMER_OVERTIME, TimerMode.MOVE_TIMER_SAVE_CAP,
            TimerMode.MOVE_TIMER_SHARED, TimerMode.MOVE_TIMER_GLOBAL_SHARED, TimerMode.FAST_MOVE, TimerMode.CHRONO_COUNTDOWN, TimerMode.CHRONO_COUNTUP,
            TimerMode.PHASES, TimerMode.HIDDEN, TimerMode.RANDOM, TimerMode.MOVE_COUNTS_UP, TimerMode.MOVE_COUNTS_DOWN)) {
            assertFalse(mode.name, ticksOnTheSecond(mode, plain, s, 120_000))
        }
        // The last ten seconds show tenths; so does a face set to show them always.
        assertFalse(ticksOnTheSecond(TimerMode.SUDDEN_DEATH, PlayerState(9_500), s, 9_500))
        assertTrue(ticksOnTheSecond(TimerMode.SUDDEN_DEATH, PlayerState(10_000), s, 10_000))
        assertFalse(ticksOnTheSecond(TimerMode.SUDDEN_DEATH, plain, s.copy(showTenthsThresholdMs = Long.MAX_VALUE), 120_000))
        // A delay is counted to a tenth, and an overrun counts the other way.
        assertFalse(ticksOnTheSecond(TimerMode.US_DELAY, PlayerState(120_000, delayRemainingMs = 3_000), s, 120_000))
        assertFalse(ticksOnTheSecond(TimerMode.SUDDEN_DEATH, PlayerState(45_000, isOutOfTime = true), s.copy(flagBehavior = FlagBehavior.REVERSE), 45_000))
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
        // Withheld from the notification, not from the flag: the phone is still kept awake for it.
        assertEquals(42_000L, backgroundClockOf(game(PlayerState(42_000), PlayerState(42_000)), hidden)!!.soundsInMs)
        val delay = asked.copy(main = PlayerSettings(mode = TimerMode.US_DELAY, incrementMs = 5_000))
        assertEquals(47_000L, backgroundClockOf(game(PlayerState(42_000, delayRemainingMs = 5_000), PlayerState(60_000)), delay)!!.countdownMs)
    }

    @Test
    fun `the phone is kept awake to the flag and a moment more, and for nothing without one`() {
        assertEquals(42_000L + WAKE_MARGIN_MS, wakeForMs(BackgroundClock(1, 42_000)))
        assertEquals(42_000L + WAKE_MARGIN_MS, wakeForMs(BackgroundClock(1, null, soundsInMs = 42_000)))
        // A stopwatch, a move count, an overrun: nothing to be late for.
        assertNull(wakeForMs(BackgroundClock(1, null)))
        assertNull(wakeForMs(null))
        // Not all night for a game left behind.
        assertEquals(MAX_WAKE_MS, wakeForMs(BackgroundClock(1, 20 * 3_600_000L)))
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
