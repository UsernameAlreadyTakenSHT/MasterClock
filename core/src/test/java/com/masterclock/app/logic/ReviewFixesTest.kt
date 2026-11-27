package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What three reviews of the whole app found before 0.8.44, each held here so that it stays found. */
class ReviewFixesTest {

    private fun table(vararg players: PlayerState, active: Int = 1) =
        ChessClockState(players = players.toList(), activePlayer = active, isPaused = false)

    @Test
    fun `under FLAG a Global clock whose total is gone stays at zero`() {
        val s = PlayerSettings(mode = TimerMode.MOVE_TIMER_GLOBAL, moveTimeMs = 30_000)
        val settings = ChessClockSettings(main = s, flagBehavior = FlagBehavior.FLAG)
        // Twenty seconds of the move left, one of the total: the total goes first.
        val out = tickPlayer(PlayerState(timeRemainingMs = 20_000, secondaryTimeMs = 1_000), 2_000, s, settings)
        assertTrue(out.isOutOfTime)
        assertEquals(0L, out.timeRemainingMs)
        assertEquals(0L, tickPlayer(out, 5_000, s, settings).timeRemainingMs)
    }

    @Test
    fun `the arbiter's move stops at the Flag Cap`() {
        val minute = 60_000L
        val periods = listOf(
            FidePeriod(timeMs = 90 * minute, incrementMs = 30_000, movesToNext = 40),
            FidePeriod(timeMs = 30 * minute, incrementMs = 30_000, movesToNext = 0),
        )
        val p = PlayerSettings(mode = TimerMode.FIDE_PERIODS, fidePeriods = periods)
        val s = ChessClockSettings(main = p, flagBehavior = FlagBehavior.NEGATIVE, flagCapMs = 10 * minute)
        val at40 = PlayerState(timeRemainingMs = 15 * minute + 30_000, moveCount = 40, currentPeriodIndex = 1)
        // The second period undone leaves the clock 14:30 over; the cap is ten minutes.
        val undone = arbiterAdjustMoveCount(table(at40, PlayerState(minute)), 1, -1, s).players[0]
        assertTrue(undone.isOutOfTime && undone.isNegative)
        assertEquals(10 * minute, undone.timeRemainingMs)
    }

    @Test
    fun `a turn with no clock banks nothing`() {
        val banked = OmniSettings(timeBankMode = TimeBankMode.GLOBAL_RESERVE, useTurnClock = true)
        val state = OmniState()
        assertEquals(600_000L, applyOmniTimeBanking(state, banked, 0, 600_000).values.sum())
        // The turn's time was never spent: banked, it grew by a whole turn at every Next.
        assertTrue(applyOmniTimeBanking(state, banked.copy(useTurnClock = false), 0, 600_000).isEmpty())
    }

    @Test
    fun `an Omni session keeps the phone awake through its pauses, and shows a countdown only for a turn`() {
        val settings = OmniSettings(useTurnClock = true)
        val turn = OmniState(isRunning = true, hasStarted = true, currentTurnTimeMs = 30_000)
        assertEquals(30_000L, omniBackgroundClockOf(turn, settings)!!.soundsInMs)
        val between = turn.copy(isInTransition = true, transitionTimeRemainingMs = 8_000)
        assertNull(omniBackgroundClockOf(between, settings)!!.countdownMs)
        assertEquals(8_000L, omniBackgroundClockOf(between, settings)!!.soundsInMs)
        val launching = turn.copy(isLaunching = true, launchTimeRemainingMs = 4_000)
        assertNull(omniBackgroundClockOf(launching, settings)!!.countdownMs)
        assertEquals(4_000L, omniBackgroundClockOf(launching, settings)!!.soundsInMs)
        assertEquals(8_000L + WAKE_MARGIN_MS, wakeForMs(omniBackgroundClockOf(between, settings)))
    }

    @Test
    fun `an Omni session is kept awake for whichever of its clocks ends first`() {
        val running = OmniState(
            isRunning = true, hasStarted = true,
            currentGlobalTimeMs = 600_000, currentGameTimeMs = 300_000, currentRoundTimeMs = 20_000,
            currentTurnTimeMs = 30_000, currentPhaseTimeMs = 5_000,
        )
        // No turn clock and a round's limit: nothing kept the phone awake, and nothing counts down
        // in the notification, which shows a turn's time or none.
        val noTurn = OmniSettings(useTurnClock = false, useRoundClock = true, useGameClock = true)
        assertEquals(20_000L, omniNextDueMs(running, noTurn))
        assertNull(omniBackgroundClockOf(running, noTurn)!!.countdownMs)
        assertEquals(20_000L + WAKE_MARGIN_MS, wakeForMs(omniBackgroundClockOf(running, noTurn)))
        // With the turn clock on, a round that ends before the turn does.
        val both = OmniSettings(useTurnClock = true, useRoundClock = true)
        assertEquals(20_000L, omniBackgroundClockOf(running, both)!!.soundsInMs)
        assertEquals(30_000L, omniBackgroundClockOf(running, both)!!.countdownMs)
        // Each clock counts only when it is in use, and one at zero has nothing left to say.
        assertEquals(5_000L, omniNextDueMs(running, both.copy(usePhaseClock = true)))
        assertEquals(300_000L, omniNextDueMs(running, OmniSettings(useTurnClock = false, useRoundClock = false, useGameClock = true)))
        assertEquals(600_000L, omniNextDueMs(running, OmniSettings(useTurnClock = false, useRoundClock = false, useGameClock = false, useGlobalClock = true)))
        assertNull(omniNextDueMs(running, OmniSettings(useTurnClock = false, useRoundClock = false, useGameClock = false)))
        assertEquals(30_000L, omniNextDueMs(running.copy(currentRoundTimeMs = 0), both))
        // In a pause, the pause -- and a clock only if it is told to run through pauses.
        // Between turns: inside the round, which a pause can cost. See omniPauseClocks.
        val paused = running.copy(isInTransition = true, transitionTimeRemainingMs = 25_000, transitionLabel = "TURN")
        assertEquals(25_000L, omniNextDueMs(paused, both))
        assertEquals(20_000L, omniNextDueMs(paused, both.copy(pauseDeductsFromRound = true)))
        // A pause that waits for a hand, with a round still running through it.
        assertEquals(20_000L, omniNextDueMs(paused.copy(transitionTimeRemainingMs = 0), both.copy(pauseDeductsFromRound = true)))
        assertNull(omniNextDueMs(paused.copy(transitionTimeRemainingMs = 0), both))
        // The launch countdown holds everything else.
        assertEquals(4_000L, omniNextDueMs(running.copy(isLaunching = true, launchTimeRemainingMs = 4_000), both))
    }

    @Test
    fun `an imported game starts as a game starts, whatever its file says`() {
        val lost = PlayerStateProxy(timeRemainingMs = 0, isOutOfTime = true, isNegative = true, hasFlagged = true, isInByoyomi = true)
        val log = sanitizeImportedLog(GameLog(settings = ChessClockSettings(), initialPlayerStates = listOf(lost, lost)))
        assertEquals(2, log.initialPlayerStates.size)
        for (state in log.initialPlayerStates) {
            assertFalse(state.isOutOfTime)
            assertFalse(state.isNegative)
            assertFalse(state.hasFlagged)
            assertFalse(state.isInByoyomi)
        }
    }

    @Test
    fun `a random range typed the wrong way round is the same range`() {
        assertEquals(60_000L to 600_000L, randomRangeOf(600_000L, 60_000L))
        val inverted = PlayerSettings(randomMinTimeMs = 600_000, randomMaxTimeMs = 60_000, randomMinIncMs = 5_000, randomMaxIncMs = 1_000)
        repeat(200) {
            val (base, inc) = drawRandomTimeControl(inverted, kotlin.random.Random(it))
            assertTrue("base $base", base in 60_000L..600_000L)
            assertTrue("inc $inc", inc in 1_000L..5_000L)
        }
    }
}
