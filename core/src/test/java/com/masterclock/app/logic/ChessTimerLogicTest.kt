package com.masterclock.app.logic

import org.junit.Test
import org.junit.Assert.*

/**
 * Exercises the real production state-transition functions ([tickPlayer], [computePostMoveState])
 * exposed by ChessTimerViewModel.kt, instead of a hand-written duplicate of their logic.
 *
 * Previously this file reimplemented the entire state machine in private `simulateTick`/
 * `simulateMove` helpers and asserted against that copy -- meaning it never actually ran
 * ChessTimerViewModel's code, and a real bug (FAST_MOVE/TRANSFER computing the opponent's new time
 * into a variable that was then never applied to the state -- fixed in this same change) survived
 * undetected.
 *
 * The coupled modes (HOURGLASS, CHRONO_COUNTDOWN, CHRONO_COUNTUP, GONG, MOVE_TIMER_SHARED,
 * MOVE_TIMER_GLOBAL_SHARED) go through [tickCoupled], the same function tick() calls. They used to
 * be tested through a hand-written copy of tick()'s inline branches, which kept passing whatever
 * the real code did. PHASES is the one mode still inline in the ViewModel and not reached here.
 */
class ChessTimerLogicTest {

    // --- tickPlayer: per-player time countdown for every non-multi-player-coupled mode ---

    @Test
    fun `SUDDEN_DEATH freezes at zero with the default flag behavior`() {
        val s = PlayerSettings(mode = TimerMode.SUDDEN_DEATH)
        val settings = ChessClockSettings(main = s, flagBehavior = FlagBehavior.FREEZE)
        val next = tickPlayer(PlayerState(timeRemainingMs = 500), delta = 1000, s = s, settings = settings)
        assertEquals(0, next.timeRemainingMs)
        assertTrue(next.isOutOfTime)
        assertFalse(next.isNegative)
    }

    @Test
    fun `NEGATIVE flag behavior flips sign and keeps counting once out of time`() {
        val s = PlayerSettings(mode = TimerMode.SUDDEN_DEATH)
        val settings = ChessClockSettings(main = s, flagBehavior = FlagBehavior.NEGATIVE)
        var p = tickPlayer(PlayerState(timeRemainingMs = 500), delta = 1000, s = s, settings = settings)
        assertTrue(p.isOutOfTime)
        assertTrue(p.isNegative)
        assertEquals(500, p.timeRemainingMs)
        p = tickPlayer(p, delta = 200, s = s, settings = settings)
        assertEquals(700, p.timeRemainingMs)
    }

    @Test
    fun `REVERSE flag behavior counts back up without the negative flag`() {
        val s = PlayerSettings(mode = TimerMode.SUDDEN_DEATH)
        val settings = ChessClockSettings(main = s, flagBehavior = FlagBehavior.REVERSE)
        val next = tickPlayer(PlayerState(timeRemainingMs = 1000), delta = 2000, s = s, settings = settings)
        assertTrue(next.isOutOfTime)
        assertFalse(next.isNegative)
        assertEquals(1000, next.timeRemainingMs)
    }

    @Test
    fun `MOVE_TIMER_GLOBAL flags when either the move clock or the game clock hits zero`() {
        val s = PlayerSettings(mode = TimerMode.MOVE_TIMER_GLOBAL, moveTimeMs = 10_000, initialTimeMs = 30_000)
        val settings = ChessClockSettings(main = s)
        val next = tickPlayer(PlayerState(timeRemainingMs = 10_000, secondaryTimeMs = 30_000), delta = 5000, s = s, settings = settings)
        assertEquals(5000, next.timeRemainingMs)
        assertEquals(25_000, next.secondaryTimeMs)
        assertFalse(next.isOutOfTime)
    }

    @Test
    fun `BYOYOMI_JAPANESE enters byoyomi once main time is exhausted`() {
        val s = PlayerSettings(mode = TimerMode.BYOYOMI_JAPANESE, byoyomiTimeMs = 30_000, byoyomiPeriods = 3)
        val settings = ChessClockSettings(main = s)
        val next = tickPlayer(PlayerState(timeRemainingMs = 500), delta = 1000, s = s, settings = settings)
        assertTrue(next.isInByoyomi)
        // The 500ms the main clock could not cover come out of the first period.
        assertEquals(29_500, next.timeRemainingMs)
    }

    @Test
    fun `BYOYOMI_JAPANESE consumes a period when a byoyomi countdown reaches zero`() {
        val s = PlayerSettings(mode = TimerMode.BYOYOMI_JAPANESE, byoyomiTimeMs = 30_000, byoyomiPeriods = 3)
        val settings = ChessClockSettings(main = s)
        val p = PlayerState(timeRemainingMs = 500, isInByoyomi = true, byoyomiPeriodsRemaining = 2)
        val next = tickPlayer(p, delta = 1000, s = s, settings = settings)
        assertEquals(1, next.byoyomiPeriodsRemaining)
        assertEquals(29_500, next.timeRemainingMs)
        assertFalse(next.isOutOfTime)
    }

    @Test
    fun `BYOYOMI_CANADIAN charges the rest of the tick to the period it enters`() {
        val s = PlayerSettings(mode = TimerMode.BYOYOMI_CANADIAN, byoyomiTimeMs = 300_000, byoyomiPeriods = 10)
        val settings = ChessClockSettings(main = s)
        val next = tickPlayer(PlayerState(timeRemainingMs = 40), delta = 100, s = s, settings = settings)
        assertTrue(next.isInByoyomi)
        assertEquals(299_940, next.timeRemainingMs)
    }

    @Test
    fun `BYOYOMI_JAPANESE with zero-length periods still ends in a flag`() {
        val s = PlayerSettings(mode = TimerMode.BYOYOMI_JAPANESE, byoyomiTimeMs = 0, byoyomiPeriods = 3)
        val settings = ChessClockSettings(main = s)
        val next = tickPlayer(PlayerState(timeRemainingMs = 10, byoyomiPeriodsRemaining = 3), delta = 100, s = s, settings = settings)
        assertTrue(next.isOutOfTime)
    }

    @Test
    fun `BYOYOMI_JAPANESE flags once the last period is consumed`() {
        val s = PlayerSettings(mode = TimerMode.BYOYOMI_JAPANESE, byoyomiTimeMs = 30_000, byoyomiPeriods = 1)
        val settings = ChessClockSettings(main = s)
        val p = PlayerState(timeRemainingMs = 500, isInByoyomi = true, byoyomiPeriodsRemaining = 1)
        val next = tickPlayer(p, delta = 1000, s = s, settings = settings)
        assertTrue(next.isOutOfTime)
        assertEquals(0, next.byoyomiPeriodsRemaining)
    }

    @Test
    fun `HIDDEN triggers a reveal window when crossing a percentage threshold`() {
        val s = PlayerSettings(mode = TimerMode.HIDDEN, showHiddenPercentages = true)
        val settings = ChessClockSettings(main = s)
        val p = PlayerState(timeRemainingMs = 10_000, initialTotalTimeMs = 10_000, lastRevealPercentage = 101)
        val next = tickPlayer(p, delta = 5100, s = s, settings = settings)
        assertEquals(50, next.lastRevealPercentage)
        assertEquals(5000L, next.revealTimeUntilMs)
    }

    @Test
    fun `FIDE_PERIODS advances to the next period when time runs out and forced counter is off`() {
        val period1 = FidePeriod(timeMs = 100, incrementMs = 0, movesToNext = 0, isFischer = false)
        val period2 = FidePeriod(timeMs = 5000, incrementMs = 0, movesToNext = 0, isFischer = false)
        val s = PlayerSettings(mode = TimerMode.FIDE_PERIODS, fidePeriods = listOf(period1, period2))
        val settings = ChessClockSettings(main = s, forcedMoveCounter = false)
        val next = tickPlayer(PlayerState(timeRemainingMs = 50, currentPeriodIndex = 0), delta = 100, s = s, settings = settings)
        assertEquals(1, next.currentPeriodIndex)
        assertEquals(5000, next.timeRemainingMs)
        assertTrue(next.hasFlagged)
        assertFalse(next.isOutOfTime)
    }

    @Test
    fun `FAST_MOVE ACCELERATE applies no speed-up before the grace period elapses`() {
        val s = PlayerSettings(mode = TimerMode.FAST_MOVE, fastMoveMode = FastMoveType.ACCELERATE, fastMoveGracePeriodMs = 5000)
        val settings = ChessClockSettings(main = s)
        val p = PlayerState(timeRemainingMs = 100_000, initialTotalTimeMs = 100_000)
        val next = tickPlayer(p, delta = 1000, s = s, settings = settings)
        assertEquals(99_000, next.timeRemainingMs)
    }

    // --- computePostMoveState: what happens to the mover (and possibly the opponent) after a move ---

    @Test
    fun `FISCHER adds the increment after a move`() {
        val s = PlayerSettings(mode = TimerMode.FISCHER, incrementMs = 2000)
        val settings = ChessClockSettings(main = s)
        val state = ChessClockState(players = listOf(PlayerState(timeRemainingMs = 10_000)), activePlayer = 1)
        val next = computePostMoveState(state, playerIndex = 1, timeSpentOnMove = 0, settings = settings, s = s)
        assertEquals(12_000, next.players[0].timeRemainingMs)
        assertEquals(1, next.players[0].moveCount)
    }

    @Test
    fun `BRONSTEIN credits back at most the increment`() {
        val s = PlayerSettings(mode = TimerMode.BRONSTEIN, incrementMs = 2000)
        val settings = ChessClockSettings(main = s)
        val state = ChessClockState(players = listOf(PlayerState(timeRemainingMs = 10_000)), activePlayer = 1)
        val next = computePostMoveState(state, 1, timeSpentOnMove = 5000, settings, s)
        assertEquals(12_000, next.players[0].timeRemainingMs) // capped at incrementMs even though 5s were spent
    }

    @Test
    fun `MOVE_COUNTS_DOWN flags once the counter reaches zero`() {
        val s = PlayerSettings(mode = TimerMode.MOVE_COUNTS_DOWN)
        val settings = ChessClockSettings(main = s)
        val state = ChessClockState(players = listOf(PlayerState(timeRemainingMs = 0, moveCount = 1)), activePlayer = 1)
        val next = computePostMoveState(state, 1, 0, settings, s)
        assertEquals(0, next.players[0].moveCount)
        assertTrue(next.players[0].isOutOfTime)
    }

    @Test
    fun `BYOYOMI_PROGRESSIVE grows the move quota after each cycle`() {
        val s = PlayerSettings(mode = TimerMode.BYOYOMI_PROGRESSIVE, byoyomiTimeMs = 5000, byoyomiProgression = 2)
        val settings = ChessClockSettings(main = s)
        val state = ChessClockState(
            players = listOf(PlayerState(timeRemainingMs = 0, isInByoyomi = true, movesRemainingInPeriod = 1, currentByoyomiMovesGoal = 5)),
            activePlayer = 1
        )
        val next = computePostMoveState(state, 1, 0, settings, s)
        assertEquals(7, next.players[0].currentByoyomiMovesGoal)
        assertEquals(5000, next.players[0].timeRemainingMs)
    }

    @Test
    fun `FIDE_PERIODS forced move counter advances the period after enough moves`() {
        val period1 = FidePeriod(timeMs = 100_000, incrementMs = 0, movesToNext = 2, isFischer = false)
        val period2 = FidePeriod(timeMs = 5000, incrementMs = 30_000, isFischer = true)
        val s = PlayerSettings(mode = TimerMode.FIDE_PERIODS, fidePeriods = listOf(period1, period2))
        val settings = ChessClockSettings(main = s, forcedMoveCounter = true)
        val state = ChessClockState(
            players = listOf(PlayerState(timeRemainingMs = 10_000, moveCount = 1, currentPeriodIndex = 0)),
            activePlayer = 1
        )
        val next = computePostMoveState(state, 1, 0, settings, s)
        assertEquals(1, next.players[0].currentPeriodIndex)
        assertEquals(15_000, next.players[0].timeRemainingMs)
        // Reaching the move count is the normal end of a period, not a flag -- this used to assert
        // the opposite, and the screen showed PERIOD FLAG for the rest of the game.
        assertFalse(next.players[0].hasFlagged)
    }

    @Test
    fun `FIDE_PERIODS non-Fischer period applies a per-move delay instead of an increment`() {
        val period = FidePeriod(timeMs = 100_000, incrementMs = 30_000, movesToNext = 0, isFischer = false, hasDelay = true)
        val s = PlayerSettings(mode = TimerMode.FIDE_PERIODS, fidePeriods = listOf(period))
        val settings = ChessClockSettings(main = s)
        val state = ChessClockState(players = listOf(PlayerState(timeRemainingMs = 50_000, currentPeriodIndex = 0)), activePlayer = 1)
        val next = computePostMoveState(state, 1, 0, settings, s)
        // Delay, unlike Fischer increment, does not add time to the clock.
        assertEquals(50_000, next.players[0].timeRemainingMs)
        assertEquals(30_000, next.players[0].delayRemainingMs)
    }

    @Test
    fun `FIDE_PERIODS delay carries over using the new period's own incrementMs after a forced transition`() {
        // Mirrors the "US 80'/40 + 30' + 30s" preset: both periods are non-Fischer with a 30s delay,
        // so the delay should apply from move 1, not only after entering period 2.
        val period1 = FidePeriod(timeMs = 100_000, incrementMs = 30_000, movesToNext = 1, isFischer = false, hasDelay = true)
        val period2 = FidePeriod(timeMs = 5000, incrementMs = 30_000, isFischer = false, hasDelay = true)
        val s = PlayerSettings(mode = TimerMode.FIDE_PERIODS, fidePeriods = listOf(period1, period2))
        val settings = ChessClockSettings(main = s, forcedMoveCounter = true)
        val state = ChessClockState(
            players = listOf(PlayerState(timeRemainingMs = 10_000, moveCount = 1, currentPeriodIndex = 0)),
            activePlayer = 1
        )
        val next = computePostMoveState(state, 1, 0, settings, s)
        assertEquals(1, next.players[0].currentPeriodIndex)
        assertEquals(30_000, next.players[0].delayRemainingMs)
    }

    @Test
    fun `FAST_MOVE TRANSFER moves the spent time to the opponent (regression test)`() {
        // Previously this computed `updatedOpponent` into a local list that was never merged back
        // into the returned state, so the opponent silently never received the transferred time.
        val s = PlayerSettings(mode = TimerMode.FAST_MOVE, fastMoveMode = FastMoveType.TRANSFER, fastMoveTransferCumulative = true, moveTimeMs = 30_000)
        val settings = ChessClockSettings(main = s, numberOfPlayers = 2)
        val state = ChessClockState(players = listOf(PlayerState(timeRemainingMs = 5000), PlayerState(timeRemainingMs = 8000)), activePlayer = 1)
        val next = computePostMoveState(state, playerIndex = 1, timeSpentOnMove = 3000, settings = settings, s = s)
        assertEquals(30_000, next.players[0].timeRemainingMs) // mover resets to moveTimeMs
        assertEquals(11_000, next.players[1].timeRemainingMs) // opponent gains the 3s spent on the move
    }

    @Test
    fun `FAST_MOVE TRANSFER non-cumulative replaces the opponent's time with exactly the time spent`() {
        val s = PlayerSettings(mode = TimerMode.FAST_MOVE, fastMoveMode = FastMoveType.TRANSFER, fastMoveTransferCumulative = false, moveTimeMs = 30_000)
        val settings = ChessClockSettings(main = s, numberOfPlayers = 2)
        val state = ChessClockState(players = listOf(PlayerState(timeRemainingMs = 5000), PlayerState(timeRemainingMs = 999)), activePlayer = 1)
        val next = computePostMoveState(state, 1, timeSpentOnMove = 4000, settings, s)
        assertEquals(4000, next.players[1].timeRemainingMs)
    }

    @Test
    fun `per-player settings pick each player's own mode independently`() {
        val settings = ChessClockSettings(
            differentSettingsPerPlayer = true,
            p1Custom = PlayerSettings(mode = TimerMode.FISCHER, initialTimeMs = 10_000, incrementMs = 2000),
            p2Custom = PlayerSettings(mode = TimerMode.MOVE_COUNTS_UP)
        )
        var state = ChessClockState(players = listOf(PlayerState(timeRemainingMs = 10_000), PlayerState(timeRemainingMs = 0)), activePlayer = 1)
        state = computePostMoveState(state, 1, 0, settings, settings.p1Custom)
        assertEquals(12_000, state.players[0].timeRemainingMs)
        state = computePostMoveState(state, 2, 0, settings, settings.p2Custom)
        assertEquals(1, state.players[1].moveCount)
    }

    // --- NEGATIVE: an overrun is stored as a positive number with isNegative beside it ---

    private fun negativeMove(s: PlayerSettings, p: PlayerState, spent: Long = 0): PlayerState {
        val settings = ChessClockSettings(main = s, flagBehavior = FlagBehavior.NEGATIVE)
        return computePostMoveState(ChessClockState(players = listOf(p, PlayerState(60_000)), activePlayer = 1), 1, spent, settings, s).players[0]
    }

    private val overrun5s = PlayerState(timeRemainingMs = 5_000, isOutOfTime = true, isNegative = true)

    @Test
    fun `a Fischer increment takes an overrun back towards zero`() {
        val next = negativeMove(PlayerSettings(mode = TimerMode.FISCHER, incrementMs = 2_000), overrun5s)
        assertEquals(3_000, next.timeRemainingMs) // -0:05 + 2s = -0:03, not -0:07
        assertTrue(next.isNegative)
    }

    @Test
    fun `an increment past the overrun gives the player time again`() {
        val next = negativeMove(PlayerSettings(mode = TimerMode.FISCHER, incrementMs = 8_000), overrun5s)
        assertEquals(3_000, next.timeRemainingMs)
        assertFalse(next.isNegative)
        assertFalse(next.isOutOfTime)
    }

    @Test
    fun `Bronstein credits against the overrun too`() {
        val next = negativeMove(PlayerSettings(mode = TimerMode.BRONSTEIN, incrementMs = 3_000), overrun5s, spent = 10_000)
        assertEquals(2_000, next.timeRemainingMs)
        assertTrue(next.isNegative)
    }

    @Test
    fun `Save and Cap banks nothing from an overrun`() {
        val s = PlayerSettings(mode = TimerMode.MOVE_TIMER_SAVE_CAP, moveTimeMs = 30_000, timeCapMs = 120_000)
        val next = negativeMove(s, PlayerState(timeRemainingMs = 10_000, isOutOfTime = true, isNegative = true))
        assertEquals(30_000, next.timeRemainingMs) // a fresh move, not 0:40
        assertFalse(next.isNegative)
        assertFalse(next.isOutOfTime)
    }

    @Test
    fun `a move timer starts every move afresh after an overrun`() {
        val s = PlayerSettings(mode = TimerMode.MOVE_TIMER_STANDARD, moveTimeMs = 30_000)
        val next = negativeMove(s, overrun5s)
        assertEquals(30_000, next.timeRemainingMs)
        assertFalse(next.isNegative)
        assertFalse(next.isOutOfTime)
        // And it counts down again, rather than up from -0:30 as it used to.
        val settings = ChessClockSettings(main = s, flagBehavior = FlagBehavior.NEGATIVE)
        assertEquals(29_000, tickPlayer(next, 1_000, s, settings).timeRemainingMs)
    }

    @Test
    fun `a player eliminated under FLAG is not brought back by a move`() {
        val s = PlayerSettings(mode = TimerMode.MOVE_TIMER_STANDARD, moveTimeMs = 30_000)
        val settings = ChessClockSettings(main = s, flagBehavior = FlagBehavior.FLAG, numberOfPlayers = 3)
        val out = PlayerState(timeRemainingMs = 0, isOutOfTime = true)
        val state = ChessClockState(players = listOf(out, PlayerState(30_000), PlayerState(30_000)), activePlayer = 1)
        assertTrue(computePostMoveState(state, 1, 0, settings, s).players[0].isOutOfTime)
    }

    @Test
    fun `MOVE_TIMER_GLOBAL shows a move overrun as negative under NEGATIVE`() {
        val s = PlayerSettings(mode = TimerMode.MOVE_TIMER_GLOBAL, moveTimeMs = 30_000)
        val settings = ChessClockSettings(main = s, flagBehavior = FlagBehavior.NEGATIVE)
        var p = tickPlayer(PlayerState(timeRemainingMs = 500, secondaryTimeMs = 600_000), 1_000, s, settings)
        assertEquals(500, p.timeRemainingMs)
        assertTrue(p.isNegative) // drawn as -0:00.5, where it used to read 0:00.5 counting up
        p = tickPlayer(p, 1_000, s, settings)
        assertEquals(1_500, p.timeRemainingMs)
        assertEquals(598_000, p.secondaryTimeMs) // the total keeps running
    }

    @Test
    fun `MOVE_TIMER_GLOBAL freezes its move clock at zero under FREEZE`() {
        val s = PlayerSettings(mode = TimerMode.MOVE_TIMER_GLOBAL, moveTimeMs = 30_000)
        val settings = ChessClockSettings(main = s, flagBehavior = FlagBehavior.FREEZE)
        val p = tickPlayer(PlayerState(timeRemainingMs = 500, secondaryTimeMs = 600_000), 1_000, s, settings)
        assertEquals(0, p.timeRemainingMs)
        assertTrue(p.isOutOfTime)
        assertFalse(p.isNegative)
    }

    @Test
    fun `MOVE_TIMER_GLOBAL_SHARED shows a move overrun as negative under NEGATIVE`() {
        val s = PlayerSettings(mode = TimerMode.MOVE_TIMER_GLOBAL_SHARED, moveTimeMs = 30_000)
        val settings = ChessClockSettings(main = s, flagBehavior = FlagBehavior.NEGATIVE)
        val players = listOf(PlayerState(timeRemainingMs = 200, secondaryTimeMs = 600_000), PlayerState(timeRemainingMs = 30_000, secondaryTimeMs = 600_000))
        val next = tickCoupled(ChessClockState(players = players, activePlayer = 1), 1_000, settings, s, 1)!!.state.players[0]
        assertEquals(800, next.timeRemainingMs)
        assertTrue(next.isNegative)
    }

    // --- flagsEndGame: one rule for a press and for Resume ---

    @Test
    fun `a flag ends the game under FREEZE only once someone is out`() {
        val oneOut = listOf(PlayerState(0, isOutOfTime = true), PlayerState(60_000))
        assertTrue(flagsEndGame(oneOut, FlagBehavior.FREEZE, 2))
        assertFalse(flagsEndGame(listOf(PlayerState(1), PlayerState(1)), FlagBehavior.FREEZE, 2))
    }

    @Test
    fun `NEGATIVE plays on after a flag, so Resume must too`() {
        // Resume used to refuse while anyone was out, which froze a NEGATIVE game at its first pause.
        val oneOut = listOf(PlayerState(5_000, isOutOfTime = true, isNegative = true), PlayerState(60_000))
        assertFalse(flagsEndGame(oneOut, FlagBehavior.NEGATIVE, 2))
    }

    @Test
    fun `FLAG with four players plays on until one is left`() {
        val players = { out: Int -> List(4) { PlayerState(if (it < out) 0 else 60_000, isOutOfTime = it < out) } }
        assertFalse(flagsEndGame(players(1), FlagBehavior.FLAG, 4))
        assertFalse(flagsEndGame(players(2), FlagBehavior.FLAG, 4))
        assertTrue(flagsEndGame(players(3), FlagBehavior.FLAG, 4))
        assertTrue(flagsEndGame(players(1).take(2), FlagBehavior.FLAG, 2))
    }

    @Test
    fun `under FLAG an eliminated player's turn is skipped`() {
        val players = listOf(PlayerState(30_000), PlayerState(0, isOutOfTime = true), PlayerState(30_000), PlayerState(30_000))
        assertEquals(3, nextPlayerInTurn(1, players, 4, FlagBehavior.FLAG))
        assertEquals(1, nextPlayerInTurn(4, players, 4, FlagBehavior.FLAG))
    }

    @Test
    fun `under NEGATIVE a player who is out still takes their turn`() {
        val players = listOf(PlayerState(30_000), PlayerState(5_000, isOutOfTime = true, isNegative = true), PlayerState(30_000))
        assertEquals(2, nextPlayerInTurn(1, players, 3, FlagBehavior.NEGATIVE))
    }

    // --- tickCoupled: the modes whose players share or trade time ---

    private fun coupled(state: ChessClockState, delta: Long, settings: ChessClockSettings): CoupledTick =
        tickCoupled(state, delta, settings, settings.main, state.activePlayer ?: 1)!!

    @Test
    fun `HOURGLASS shares the active player's loss with the others`() {
        val settings = ChessClockSettings(numberOfPlayers = 3, main = PlayerSettings(mode = TimerMode.HOURGLASS, initialTimeMs = 30_000))
        val state = ChessClockState(players = listOf(PlayerState(30_000), PlayerState(30_000), PlayerState(30_000)), activePlayer = 1)
        val next = coupled(state, 10_000, settings).state
        assertEquals(20_000, next.players[0].timeRemainingMs)
        assertEquals(35_000, next.players[1].timeRemainingMs) // gains half of P1's loss
    }

    @Test
    fun `HOURGLASS with two players moves time across exactly`() {
        val settings = ChessClockSettings(numberOfPlayers = 2, main = PlayerSettings(mode = TimerMode.HOURGLASS))
        val state = ChessClockState(players = listOf(PlayerState(60_000), PlayerState(60_000)), activePlayer = 2)
        val next = coupled(state, 1_000, settings).state
        assertEquals(61_000, next.players[0].timeRemainingMs)
        assertEquals(59_000, next.players[1].timeRemainingMs)
    }

    @Test
    fun `HOURGLASS with four players hands out every millisecond the active player loses`() {
        val settings = ChessClockSettings(numberOfPlayers = 4, main = PlayerSettings(mode = TimerMode.HOURGLASS))
        val before = ChessClockState(players = List(4) { PlayerState(60_000) }, activePlayer = 1)
        val after = coupled(before, 10, settings).state
        // 10ms over three players used to give 3 each and lose the tenth.
        assertEquals(before.players.sumOf { it.timeRemainingMs }, after.players.sumOf { it.timeRemainingMs })
    }

    @Test
    fun `HOURGLASS gives nothing to a player FLAG has eliminated`() {
        val settings = ChessClockSettings(numberOfPlayers = 3, main = PlayerSettings(mode = TimerMode.HOURGLASS), flagBehavior = FlagBehavior.FLAG)
        val players = listOf(PlayerState(60_000), PlayerState(0, isOutOfTime = true), PlayerState(60_000))
        val after = coupled(ChessClockState(players = players, activePlayer = 1), 1_000, settings).state
        assertEquals(0, after.players[1].timeRemainingMs)
        assertEquals(61_000, after.players[2].timeRemainingMs)
    }

    @Test
    fun `HOURGLASS stops feeding the others once the active player is out`() {
        val settings = ChessClockSettings(numberOfPlayers = 2, main = PlayerSettings(mode = TimerMode.HOURGLASS), flagBehavior = FlagBehavior.FLAG)
        val players = listOf(PlayerState(0, isOutOfTime = true), PlayerState(60_000))
        // The game is over; the survivor's clock used to climb for ever.
        assertEquals(60_000, coupled(ChessClockState(players = players, activePlayer = 1), 1_000, settings).state.players[1].timeRemainingMs)
    }

    @Test
    fun `MOVE_TIMER_GLOBAL_SHARED keeps an inactive player's own flag`() {
        val s = PlayerSettings(mode = TimerMode.MOVE_TIMER_GLOBAL_SHARED, moveTimeMs = 30_000)
        val settings = ChessClockSettings(numberOfPlayers = 3, main = s, flagBehavior = FlagBehavior.FLAG)
        val players = listOf(
            PlayerState(timeRemainingMs = 30_000, secondaryTimeMs = 600_000),
            PlayerState(timeRemainingMs = 30_000, secondaryTimeMs = 600_000, isOutOfTime = true),
            PlayerState(timeRemainingMs = 30_000, secondaryTimeMs = 600_000),
        )
        val next = tickCoupled(ChessClockState(players = players, activePlayer = 1), 1_000, settings, s, 1)!!.state
        assertTrue(next.players[1].isOutOfTime)
        assertFalse(next.players[2].isOutOfTime)
    }

    @Test
    fun `CHRONO_COUNTDOWN runs the one shared clock and freezes the game when it ends`() {
        val settings = ChessClockSettings(main = PlayerSettings(mode = TimerMode.CHRONO_COUNTDOWN), flagBehavior = FlagBehavior.FREEZE)
        val state = ChessClockState(players = listOf(PlayerState(0), PlayerState(0)), activePlayer = 1, globalTimeMs = 500)
        val result = coupled(state, 1_000, settings)
        assertEquals(0, result.state.globalTimeMs)
        assertTrue(result.state.players.all { it.isOutOfTime })
        assertTrue(result.state.isPaused)
        assertTrue(result.cue!!.after.isOutOfTime) // the flag is what the cue carries to the audio
    }

    @Test
    fun `CHRONO_COUNTUP only counts up`() {
        val settings = ChessClockSettings(main = PlayerSettings(mode = TimerMode.CHRONO_COUNTUP))
        val state = ChessClockState(players = listOf(PlayerState(0), PlayerState(0)), activePlayer = 1, globalTimeMs = 5_000)
        val result = coupled(state, 1_000, settings)
        assertEquals(6_000, result.state.globalTimeMs)
        assertNull(result.cue)
    }

    @Test
    fun `MOVE_TIMER_SHARED hands the turn on when the shared move time runs out`() {
        val settings = ChessClockSettings(numberOfPlayers = 3, main = PlayerSettings(mode = TimerMode.MOVE_TIMER_SHARED, moveTimeMs = 10_000))
        val players = List(3) { PlayerState(timeRemainingMs = 400) }
        val result = coupled(ChessClockState(players = players, activePlayer = 3, cycleCount = 1), 1_000, settings)
        assertEquals(1, result.state.activePlayer)
        assertEquals(2, result.state.cycleCount) // back to P1 starts a new cycle
        assertTrue(result.state.players.all { it.timeRemainingMs == 10_000L })
    }

    @Test
    fun `MOVE_TIMER_GLOBAL_SHARED takes a tick from the move clock and from the shared pool`() {
        val settings = ChessClockSettings(main = PlayerSettings(mode = TimerMode.MOVE_TIMER_GLOBAL_SHARED))
        val players = listOf(PlayerState(timeRemainingMs = 30_000, secondaryTimeMs = 600_000), PlayerState(timeRemainingMs = 30_000, secondaryTimeMs = 600_000))
        val next = coupled(ChessClockState(players = players, activePlayer = 1), 1_000, settings).state
        assertEquals(29_000, next.players[0].timeRemainingMs)
        assertTrue(next.players.all { it.secondaryTimeMs == 599_000L })
    }

    @Test
    fun `GONG moves from reflection to move with a gong, then on to the next player`() {
        val s = PlayerSettings(mode = TimerMode.GONG, gongReflectionMs = 5_000, gongMoveMs = 3_000)
        val settings = ChessClockSettings(main = s)
        val reflecting = ChessClockState(players = listOf(PlayerState(timeRemainingMs = 100, isGongReflectionPhase = true), PlayerState(5_000)), activePlayer = 1)
        val moving = coupled(reflecting, 200, settings)
        assertTrue(moving.playGong)
        // The cue that drives the beeps and the "Move" line: reflection before, move after.
        assertTrue(moving.cue!!.before.isGongReflectionPhase)
        assertFalse(moving.cue!!.after.isGongReflectionPhase)
        assertEquals(3_000, moving.state.players[0].timeRemainingMs)
        assertFalse(moving.state.players[0].isGongReflectionPhase)

        val done = coupled(moving.state.copy(players = moving.state.players.toMutableList().apply { this[0] = this[0].copy(timeRemainingMs = 100) }), 200, settings)
        assertEquals(2, done.state.activePlayer)
        assertEquals(1, done.state.players[0].moveCount)
    }

    @Test
    fun `the player who flags in a coupled mode is recorded as first to flag`() {
        val settings = ChessClockSettings(numberOfPlayers = 2, main = PlayerSettings(mode = TimerMode.HOURGLASS), flagBehavior = FlagBehavior.FLAG)
        val before = ChessClockState(players = listOf(PlayerState(60_000), PlayerState(300)), activePlayer = 2)
        val after = coupled(before, 1_000, settings).state.withFirstFlag(before, 2)
        assertEquals(2, after.firstToFlag)
        // And it is not overwritten by a later flag.
        assertEquals(2, after.copy(players = after.players.map { it.copy(isOutOfTime = true) }).withFirstFlag(after, 1).firstToFlag)
    }

    @Test
    fun `modes that are not coupled are left to tickPlayer`() {
        val settings = ChessClockSettings(main = PlayerSettings(mode = TimerMode.FISCHER))
        assertNull(tickCoupled(ChessClockState(players = listOf(PlayerState(1), PlayerState(1)), activePlayer = 1), 100, settings, settings.main, 1))
    }

    // --- applyPresetTimeControl: presets carry a time control, not a whole app configuration ---

    @Test
    fun `applying a preset takes its time control`() {
        val current = ChessClockSettings(main = PlayerSettings(initialTimeMs = 600_000, mode = TimerMode.SUDDEN_DEATH))
        val preset = ChessClockSettings(
            main = PlayerSettings(initialTimeMs = 180_000, incrementMs = 2_000, mode = TimerMode.FISCHER),
            numberOfPlayers = 4,
            differentSettingsPerPlayer = true,
            flagBehavior = FlagBehavior.NEGATIVE,
        )

        val applied = applyPresetTimeControl(current, preset)
        assertEquals(TimerMode.FISCHER, applied.main.mode)
        assertEquals(180_000, applied.main.initialTimeMs)
        assertEquals(2_000, applied.main.incrementMs)
        assertEquals(4, applied.numberOfPlayers)
        assertTrue(applied.differentSettingsPerPlayer)
        assertEquals(FlagBehavior.NEGATIVE, applied.flagBehavior)
    }

    @Test
    fun `applying a preset leaves appearance, audio and behaviour alone`() {
        // The built-in presets are each a fresh ChessClockSettings(), so before this merge existed
        // tapping one silently reset every preference below to its default.
        val current = ChessClockSettings(
            activeColor = 0xFF123456,
            inactiveColor = 0xFF654321,
            soundsVolume = 0.25f,
            playSwitchSound = true,
            hapticFeedback = true,
            themeMode = AppThemeMode.DARK,
            clockOrientation = ClockOrientation.HORIZONTAL_LEFT,
            confirmReset = false,
            logHistoryLimit = 42,
            customBeepUri = "content://kept",
        )
        val preset = ChessClockSettings(main = PlayerSettings(initialTimeMs = 60_000, mode = TimerMode.SUDDEN_DEATH))

        val applied = applyPresetTimeControl(current, preset)
        assertEquals(0xFF123456, applied.activeColor)
        assertEquals(0xFF654321, applied.inactiveColor)
        assertEquals(0.25f, applied.soundsVolume, 0.0001f)
        assertTrue(applied.playSwitchSound)
        assertTrue(applied.hapticFeedback)
        assertEquals(AppThemeMode.DARK, applied.themeMode)
        assertEquals(ClockOrientation.HORIZONTAL_LEFT, applied.clockOrientation)
        assertFalse(applied.confirmReset)
        assertEquals(42, applied.logHistoryLimit)
        assertEquals("content://kept", applied.customBeepUri)
        // and the time control did change
        assertEquals(60_000, applied.main.initialTimeMs)
    }

    @Test
    fun `applying a preset keeps the user's notebook`() {
        val current = ChessClockSettings(notebookNotes = listOf(NotebookNote(title = "My opening prep")))
        val preset = ChessClockSettings(main = PlayerSettings(initialTimeMs = 60_000))

        val applied = applyPresetTimeControl(current, preset)
        assertEquals(1, applied.notebookNotes.size)
        assertEquals("My opening prep", applied.notebookNotes.first().title)
    }

    // --- Modes that had no coverage at all until now ---
    //
    // Everything below drives the real tickPlayer/computePostMoveState, like the sections above.
    // MOVE_TIMER_SHARED's countdown is covered through tickCoupled above. PHASES has only its
    // post-move side covered: its countdown and phase transitions are still ViewModel methods,
    // not reachable from here.

    // US_DELAY: the delay is spent before the main clock, and never out of it.

    @Test
    fun `US_DELAY spends the delay before touching the main clock`() {
        val s = PlayerSettings(mode = TimerMode.US_DELAY, incrementMs = 5000)
        val settings = ChessClockSettings(main = s)
        val p = PlayerState(timeRemainingMs = 300_000, delayRemainingMs = 5000)
        val next = tickPlayer(p, delta = 1000, s = s, settings = settings)
        assertEquals(4000, next.delayRemainingMs)
        assertEquals(300_000, next.timeRemainingMs)
    }

    @Test
    fun `US_DELAY bills the part of a tick past the delay to the main clock`() {
        val s = PlayerSettings(mode = TimerMode.US_DELAY, incrementMs = 5000)
        val settings = ChessClockSettings(main = s)
        // 500ms of delay left, a 1000ms tick: the other 500ms are thinking time past the delay.
        // They used to be dropped -- up to a tick's worth, every move, charged to no one.
        val next = tickPlayer(PlayerState(timeRemainingMs = 300_000, delayRemainingMs = 500), 1000, s, settings)
        assertEquals(0, next.delayRemainingMs)
        assertEquals(299_500, next.timeRemainingMs)
    }

    @Test
    fun `US_DELAY burns the main clock once the delay is gone`() {
        val s = PlayerSettings(mode = TimerMode.US_DELAY, incrementMs = 5000)
        val settings = ChessClockSettings(main = s)
        val next = tickPlayer(PlayerState(timeRemainingMs = 300_000, delayRemainingMs = 0), 1000, s, settings)
        assertEquals(299_000, next.timeRemainingMs)
    }

    // MOVE_TIMER_STANDARD: a fixed allowance per move, restored on every press.

    @Test
    fun `MOVE_TIMER_STANDARD counts the move allowance down`() {
        val s = PlayerSettings(mode = TimerMode.MOVE_TIMER_STANDARD, moveTimeMs = 30_000)
        val settings = ChessClockSettings(main = s)
        val next = tickPlayer(PlayerState(timeRemainingMs = 30_000), 1000, s, settings)
        assertEquals(29_000, next.timeRemainingMs)
    }

    @Test
    fun `MOVE_TIMER_STANDARD flags when the move allowance runs out`() {
        val s = PlayerSettings(mode = TimerMode.MOVE_TIMER_STANDARD, moveTimeMs = 30_000)
        val settings = ChessClockSettings(main = s, flagBehavior = FlagBehavior.FREEZE)
        val next = tickPlayer(PlayerState(timeRemainingMs = 500), 1000, s, settings)
        assertEquals(0, next.timeRemainingMs)
        assertTrue(next.isOutOfTime)
    }

    @Test
    fun `MOVE_TIMER_STANDARD restores the full allowance on a press, keeping nothing`() {
        val s = PlayerSettings(mode = TimerMode.MOVE_TIMER_STANDARD, moveTimeMs = 30_000)
        val settings = ChessClockSettings(main = s)
        val state = ChessClockState(players = listOf(PlayerState(timeRemainingMs = 22_000)), activePlayer = 1)
        val next = computePostMoveState(state, 1, timeSpentOnMove = 8000, settings, s)
        // The 22 seconds left are lost, not banked -- that is what separates this from SAVE_CAP.
        assertEquals(30_000, next.players[0].timeRemainingMs)
    }

    // MOVE_TIMER_OVERTIME: an allowance per move, backed by one global reserve it dips into.

    @Test
    fun `MOVE_TIMER_OVERTIME spends the move clock while it lasts and leaves the reserve alone`() {
        val s = PlayerSettings(mode = TimerMode.MOVE_TIMER_OVERTIME, moveTimeMs = 30_000, initialTimeMs = 600_000)
        val settings = ChessClockSettings(main = s)
        val next = tickPlayer(PlayerState(timeRemainingMs = 5000, secondaryTimeMs = 600_000), 1000, s, settings)
        assertEquals(4000, next.timeRemainingMs)
        assertEquals(600_000, next.secondaryTimeMs)
        assertFalse(next.isOutOfTime)
    }

    @Test
    fun `MOVE_TIMER_OVERTIME takes only the overshoot from the reserve on the crossing tick`() {
        val s = PlayerSettings(mode = TimerMode.MOVE_TIMER_OVERTIME, moveTimeMs = 30_000, initialTimeMs = 600_000)
        val settings = ChessClockSettings(main = s)
        // 500ms of move clock left against a 1000ms tick: 500ms of it is real overtime, and only
        // that 500ms may reach the reserve. Charging the whole delta here would bill the player
        // twice for the same half-second.
        val next = tickPlayer(PlayerState(timeRemainingMs = 500, secondaryTimeMs = 600_000), 1000, s, settings)
        assertEquals(0, next.timeRemainingMs)
        assertEquals(599_500, next.secondaryTimeMs)
        assertFalse(next.isOutOfTime)
    }

    @Test
    fun `MOVE_TIMER_OVERTIME burns the reserve at full rate once the move clock is empty`() {
        val s = PlayerSettings(mode = TimerMode.MOVE_TIMER_OVERTIME, moveTimeMs = 30_000, initialTimeMs = 600_000)
        val settings = ChessClockSettings(main = s)
        val next = tickPlayer(PlayerState(timeRemainingMs = 0, secondaryTimeMs = 599_500), 1000, s, settings)
        assertEquals(0, next.timeRemainingMs)
        assertEquals(598_500, next.secondaryTimeMs)
    }

    @Test
    fun `MOVE_TIMER_OVERTIME flags only when the reserve itself is exhausted`() {
        val s = PlayerSettings(mode = TimerMode.MOVE_TIMER_OVERTIME, moveTimeMs = 30_000, initialTimeMs = 600_000)
        val settings = ChessClockSettings(main = s)
        val next = tickPlayer(PlayerState(timeRemainingMs = 0, secondaryTimeMs = 500), 1000, s, settings)
        assertTrue(next.isOutOfTime)
    }

    @Test
    fun `MOVE_TIMER_OVERTIME restores the move clock on a press and carries the reserve over`() {
        val s = PlayerSettings(mode = TimerMode.MOVE_TIMER_OVERTIME, moveTimeMs = 30_000, initialTimeMs = 600_000)
        val settings = ChessClockSettings(main = s)
        val state = ChessClockState(
            players = listOf(PlayerState(timeRemainingMs = 0, secondaryTimeMs = 545_000)),
            activePlayer = 1,
        )
        val next = computePostMoveState(state, 1, timeSpentOnMove = 85_000, settings, s)
        assertEquals(30_000, next.players[0].timeRemainingMs)
        // The reserve is the whole point of the mode: a press must not refill it.
        assertEquals(545_000, next.players[0].secondaryTimeMs)
    }

    // BYOYOMI_CANADIAN: after the main time, a block of time for a fixed number of moves.

    @Test
    fun `BYOYOMI_CANADIAN enters byoyomi when the main time runs out`() {
        val s = PlayerSettings(mode = TimerMode.BYOYOMI_CANADIAN, byoyomiTimeMs = 300_000, byoyomiPeriods = 25)
        val settings = ChessClockSettings(main = s)
        val next = tickPlayer(PlayerState(timeRemainingMs = 500, isInByoyomi = false), 1000, s, settings)
        assertTrue(next.isInByoyomi)
        // The block starts at 5:00 less the 500ms the main clock could not cover.
        assertEquals(299_500, next.timeRemainingMs)
        assertFalse(next.isOutOfTime)
    }

    @Test
    fun `BYOYOMI_CANADIAN counts the block down once inside it`() {
        val s = PlayerSettings(mode = TimerMode.BYOYOMI_CANADIAN, byoyomiTimeMs = 300_000, byoyomiPeriods = 25)
        val settings = ChessClockSettings(main = s)
        val next = tickPlayer(PlayerState(timeRemainingMs = 300_000, isInByoyomi = true), 1000, s, settings)
        assertEquals(299_000, next.timeRemainingMs)
        assertFalse(next.isOutOfTime)
    }

    @Test
    fun `BYOYOMI_CANADIAN flags when the block runs out`() {
        val s = PlayerSettings(mode = TimerMode.BYOYOMI_CANADIAN, byoyomiTimeMs = 300_000, byoyomiPeriods = 25)
        val settings = ChessClockSettings(main = s)
        val next = tickPlayer(PlayerState(timeRemainingMs = 500, isInByoyomi = true), 1000, s, settings)
        assertTrue(next.isOutOfTime)
    }

    @Test
    fun `BYOYOMI_CANADIAN counts a move off the quota without refilling the block`() {
        val s = PlayerSettings(mode = TimerMode.BYOYOMI_CANADIAN, byoyomiTimeMs = 300_000, byoyomiPeriods = 25)
        val settings = ChessClockSettings(main = s)
        val state = ChessClockState(
            players = listOf(PlayerState(timeRemainingMs = 240_000, isInByoyomi = true, movesRemainingInPeriod = 25)),
            activePlayer = 1,
        )
        val next = computePostMoveState(state, 1, 0, settings, s)
        assertEquals(24, next.players[0].movesRemainingInPeriod)
        assertEquals(240_000, next.players[0].timeRemainingMs)
    }

    @Test
    fun `BYOYOMI_CANADIAN refills the block only on the last move of the quota`() {
        val s = PlayerSettings(mode = TimerMode.BYOYOMI_CANADIAN, byoyomiTimeMs = 300_000, byoyomiPeriods = 25)
        val settings = ChessClockSettings(main = s)
        val state = ChessClockState(
            players = listOf(PlayerState(timeRemainingMs = 4000, isInByoyomi = true, movesRemainingInPeriod = 1)),
            activePlayer = 1,
        )
        val next = computePostMoveState(state, 1, 0, settings, s)
        assertEquals(300_000, next.players[0].timeRemainingMs)
        assertEquals(25, next.players[0].movesRemainingInPeriod)
    }

    // RANDOM: the roll is held in secondaryTimeMs and credited on the press.

    @Test
    fun `RANDOM credits the rolled bonus after a move`() {
        val s = PlayerSettings(mode = TimerMode.RANDOM)
        val settings = ChessClockSettings(main = s)
        val state = ChessClockState(
            players = listOf(PlayerState(timeRemainingMs = 100_000, secondaryTimeMs = 3000)),
            activePlayer = 1,
        )
        val next = computePostMoveState(state, 1, 0, settings, s)
        assertEquals(103_000, next.players[0].timeRemainingMs)
    }

    @Test
    fun `RANDOM keeps the roll for the next move rather than clearing it`() {
        val s = PlayerSettings(mode = TimerMode.RANDOM)
        val settings = ChessClockSettings(main = s)
        val state = ChessClockState(
            players = listOf(PlayerState(timeRemainingMs = 100_000, secondaryTimeMs = 3000)),
            activePlayer = 1,
        )
        val next = computePostMoveState(state, 1, 0, settings, s)
        // A new roll is the ViewModel's job; this function must not silently zero the old one.
        assertEquals(3000, next.players[0].secondaryTimeMs)
    }

    // Modes whose countdown lives elsewhere.
    //
    // Read the last two carefully: startOrSwitch returns before applyPostMoveLogic for both
    // MOVE_TIMER_SHARED and PHASES, and applyPostMoveLogic is computePostMoveState's only caller,
    // so production never reaches this function with either mode. They pass through the `else ->
    // tempP` fallthrough. What they pin is therefore narrower than "a press does not reset the
    // clock": they fail if someone adds one of these modes to the branch that restores moveTimeMs,
    // and they would stay green if someone deleted the early return in startOrSwitch instead.

    @Test
    fun `MOVE_TIMER_GLOBAL_SHARED restores the mover's move clock on a press`() {
        val s = PlayerSettings(mode = TimerMode.MOVE_TIMER_GLOBAL_SHARED, moveTimeMs = 30_000)
        val settings = ChessClockSettings(main = s)
        val state = ChessClockState(players = listOf(PlayerState(timeRemainingMs = 4000)), activePlayer = 1)
        val next = computePostMoveState(state, 1, 0, settings, s)
        assertEquals(30_000, next.players[0].timeRemainingMs)
    }

    @Test
    fun `MOVE_TIMER_SHARED has no post-move branch that would refill its clock`() {
        val s = PlayerSettings(mode = TimerMode.MOVE_TIMER_SHARED, moveTimeMs = 30_000)
        val settings = ChessClockSettings(main = s)
        val state = ChessClockState(players = listOf(PlayerState(timeRemainingMs = 12_000)), activePlayer = 1)
        val next = computePostMoveState(state, 1, 0, settings, s)
        // One clock is shared by everyone, so a branch restoring moveTimeMs here would make it
        // endless the day someone routed this mode through applyPostMoveLogic.
        assertEquals(12_000, next.players[0].timeRemainingMs)
        assertEquals(1, next.players[0].moveCount)
    }

    @Test
    fun `PHASES has no post-move branch that would restart its clock`() {
        val s = PlayerSettings(mode = TimerMode.PHASES)
        val settings = ChessClockSettings(main = s)
        val state = ChessClockState(players = listOf(PlayerState(timeRemainingMs = 45_000)), activePlayer = 1)
        val next = computePostMoveState(state, 1, 0, settings, s)
        // A phase runs to its own end; only startPhaseTransition/performPhaseAdvance may move this
        // clock, and neither of those is reachable from here.
        assertEquals(45_000, next.players[0].timeRemainingMs)
    }

    // MOVE_TIMER_SAVE_CAP: unused move time is carried over, and the clock as a whole has a ceiling.

    @Test
    fun `SAVE_CAP carries over what a move did not use`() {
        val s = PlayerSettings(mode = TimerMode.MOVE_TIMER_SAVE_CAP, moveTimeMs = 30_000, timeCapMs = 120_000)
        val settings = ChessClockSettings(main = s)
        // First move: the pot was 30s, ten of them were spent, twenty are left.
        val state = ChessClockState(players = listOf(PlayerState(timeRemainingMs = 20_000)), activePlayer = 1)
        val next = computePostMoveState(state, 1, timeSpentOnMove = 10_000, settings, s)
        assertEquals(50_000, next.players[0].timeRemainingMs)
        assertEquals(20_000, next.players[0].secondaryTimeMs)
    }

    @Test
    fun `SAVE_CAP does not bank again the time the clock was already carrying`() {
        val s = PlayerSettings(mode = TimerMode.MOVE_TIMER_SAVE_CAP, moveTimeMs = 30_000, timeCapMs = 120_000)
        val settings = ChessClockSettings(main = s)
        // Second move: the pot was 30s + a 20s bank, ten were spent, forty are left. The bank is
        // inside timeRemainingMs already -- adding secondaryTimeMs to it, as this used to, banked
        // 60s and set the clock to 90s instead of 70s, and compounded from there.
        val state = ChessClockState(
            players = listOf(PlayerState(timeRemainingMs = 40_000, secondaryTimeMs = 20_000)),
            activePlayer = 1,
        )
        val next = computePostMoveState(state, 1, timeSpentOnMove = 10_000, settings, s)
        assertEquals(70_000, next.players[0].timeRemainingMs)
        assertEquals(40_000, next.players[0].secondaryTimeMs)
    }

    @Test
    fun `SAVE_CAP caps the whole clock, not the bank alone`() {
        val s = PlayerSettings(mode = TimerMode.MOVE_TIMER_SAVE_CAP, moveTimeMs = 30_000, timeCapMs = 120_000)
        val settings = ChessClockSettings(main = s)
        val state = ChessClockState(
            players = listOf(PlayerState(timeRemainingMs = 100_000, secondaryTimeMs = 70_000)),
            activePlayer = 1,
        )
        val next = computePostMoveState(state, 1, 0, settings, s)
        // 2:00 on the clock, not 2:30. Capping the bank instead would leave room for a fresh move
        // on top of a full bank, so a 30s move with a 2:00 cap would reach 2:30.
        assertEquals(120_000, next.players[0].timeRemainingMs)
        assertEquals(90_000, next.players[0].secondaryTimeMs)
    }

    @Test
    fun `SAVE_CAP converges on the cap over a run of fast moves instead of passing it`() {
        val s = PlayerSettings(mode = TimerMode.MOVE_TIMER_SAVE_CAP, moveTimeMs = 30_000, timeCapMs = 120_000)
        val settings = ChessClockSettings(main = s)
        var clock = 30_000L
        repeat(8) {
            val state = ChessClockState(
                players = listOf(PlayerState(timeRemainingMs = clock - 10_000)),
                activePlayer = 1,
            )
            clock = computePostMoveState(state, 1, 10_000, settings, s).players[0].timeRemainingMs
            assertTrue("clock went past the cap: $clock", clock <= 120_000)
        }
        assertEquals(120_000, clock)
    }

    @Test
    fun `SAVE_CAP banks nothing from a turn that ran out`() {
        val s = PlayerSettings(mode = TimerMode.MOVE_TIMER_SAVE_CAP, moveTimeMs = 30_000, timeCapMs = 120_000)
        val settings = ChessClockSettings(main = s)
        val state = ChessClockState(players = listOf(PlayerState(timeRemainingMs = 0)), activePlayer = 1)
        val next = computePostMoveState(state, 1, timeSpentOnMove = 30_000, settings, s)
        assertEquals(30_000, next.players[0].timeRemainingMs)
        assertEquals(0, next.players[0].secondaryTimeMs)
    }

    @Test
    fun `SAVE_CAP with a cap below the move time saves nothing rather than shortening the move`() {
        // A cap under one move cannot bind without handing the player less time than the mode
        // promises them each turn, so it degrades to plain MOVE_TIMER_STANDARD. That also makes a
        // cap of zero -- which the settings field accepts -- mean "save nothing" instead of
        // "no time at all".
        val s = PlayerSettings(mode = TimerMode.MOVE_TIMER_SAVE_CAP, moveTimeMs = 30_000, timeCapMs = 0)
        val settings = ChessClockSettings(main = s)
        val state = ChessClockState(players = listOf(PlayerState(timeRemainingMs = 25_000)), activePlayer = 1)
        val next = computePostMoveState(state, 1, 5000, settings, s)
        assertEquals(30_000, next.players[0].timeRemainingMs)
        assertEquals(0, next.players[0].secondaryTimeMs)
    }
}
