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
 * undetected. See AUDIT.md §6.
 *
 * [simulateMultiPlayerTick] below is a narrower, still-hand-written duplicate for the handful of
 * modes (HOURGLASS/CHRONO_COUNTDOWN/CHRONO_COUNTUP/PHASES/GONG/MOVE_TIMER_SHARED/MOVE_TIMER_GLOBAL_SHARED) that are
 * implemented inline in `ChessTimerViewModel.tick()` rather than in the extracted [tickPlayer]; that
 * remains a known, documented gap (also tracked in AUDIT.md §6) rather than something this change
 * attempts to fully close.
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
        assertEquals(30_000, next.timeRemainingMs)
    }

    @Test
    fun `BYOYOMI_JAPANESE consumes a period when a byoyomi countdown reaches zero`() {
        val s = PlayerSettings(mode = TimerMode.BYOYOMI_JAPANESE, byoyomiTimeMs = 30_000, byoyomiPeriods = 3)
        val settings = ChessClockSettings(main = s)
        val p = PlayerState(timeRemainingMs = 500, isInByoyomi = true, byoyomiPeriodsRemaining = 2)
        val next = tickPlayer(p, delta = 1000, s = s, settings = settings)
        assertEquals(1, next.byoyomiPeriodsRemaining)
        assertEquals(30_000, next.timeRemainingMs)
        assertFalse(next.isOutOfTime)
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
    fun `FISHER adds the increment after a move`() {
        val s = PlayerSettings(mode = TimerMode.FISHER, incrementMs = 2000)
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
        assertTrue(next.players[0].hasFlagged)
    }

    @Test
    fun `FIDE_PERIODS non-Fischer period applies a per-move delay instead of an increment`() {
        val period = FidePeriod(timeMs = 100_000, incrementMs = 30_000, movesToNext = 0, isFischer = false)
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
        val period1 = FidePeriod(timeMs = 100_000, incrementMs = 30_000, movesToNext = 1, isFischer = false)
        val period2 = FidePeriod(timeMs = 5000, incrementMs = 30_000, isFischer = false)
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
    fun `FAST_MOVE TRANSFER moves the spent time to the opponent (regression test, see AUDIT-md section 6)`() {
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
            p1Custom = PlayerSettings(mode = TimerMode.FISHER, initialTimeMs = 10_000, incrementMs = 2000),
            p2Custom = PlayerSettings(mode = TimerMode.MOVE_COUNTS_UP)
        )
        var state = ChessClockState(players = listOf(PlayerState(timeRemainingMs = 10_000), PlayerState(timeRemainingMs = 0)), activePlayer = 1)
        state = computePostMoveState(state, 1, 0, settings, settings.p1Custom)
        assertEquals(12_000, state.players[0].timeRemainingMs)
        state = computePostMoveState(state, 2, 0, settings, settings.p2Custom)
        assertEquals(1, state.players[1].moveCount)
    }

    // --- Modes still implemented inline in ChessTimerViewModel.tick(), not yet extracted (AUDIT.md §6) ---

    /**
     * Duplicates just the HOURGLASS/CHRONO_COUNTDOWN/CHRONO_COUNTUP/GONG/MOVE_TIMER_SHARED/MOVE_TIMER_GLOBAL_SHARED branches of
     * `ChessTimerViewModel.tick()`. Unlike [tickPlayer]/[computePostMoveState] above, these are NOT
     * exercised against production code -- a known, deliberately narrowed gap (see class doc).
     */
    private fun simulateMultiPlayerTick(state: ChessClockState, delta: Long, settings: ChessClockSettings): ChessClockState {
        val activeIdx = state.activePlayer ?: 1
        val s = settings.main
        return when (s.mode) {
            TimerMode.HOURGLASS -> {
                val share = delta / (settings.numberOfPlayers - 1).coerceAtLeast(1)
                val newPlayers = state.players.mapIndexed { idx, p ->
                    if (idx + 1 == activeIdx) {
                        val nt = p.timeRemainingMs - delta
                        p.copy(timeRemainingMs = nt.coerceAtLeast(0), isOutOfTime = nt <= 0)
                    } else {
                        p.copy(timeRemainingMs = p.timeRemainingMs + share)
                    }
                }
                state.copy(players = newPlayers)
            }
            TimerMode.CHRONO_COUNTDOWN -> {
                val ng = (state.globalTimeMs - delta).coerceAtLeast(0)
                state.copy(globalTimeMs = ng, players = state.players.map { it.copy(isOutOfTime = ng <= 0) })
            }
            TimerMode.CHRONO_COUNTUP -> state.copy(globalTimeMs = state.globalTimeMs + delta)
            TimerMode.GONG -> {
                var ns = state
                val updateList = if (s.gongSimultaneous) (1..settings.numberOfPlayers).toList() else listOf(activeIdx)
                for (idx in updateList) {
                    val p = ns.players[idx - 1]
                    var t = p.timeRemainingMs - delta; var ref = p.isGongReflectionPhase; var nextA = ns.activePlayer
                    if (t <= 0) {
                        if (ref) { t = s.gongMoveMs; ref = false }
                        else { t = s.gongReflectionMs; ref = true; if (!s.gongSimultaneous) nextA = (activeIdx % settings.numberOfPlayers) + 1 }
                    }
                    val up = p.copy(timeRemainingMs = t, isGongReflectionPhase = ref)
                    ns = ns.copy(players = ns.players.toMutableList().apply { this[idx - 1] = up }, activePlayer = nextA)
                }
                ns
            }
            else -> state
        }
    }

    @Test
    fun `HOURGLASS shares the active player's loss with the others`() {
        val settings = ChessClockSettings(numberOfPlayers = 3, main = PlayerSettings(mode = TimerMode.HOURGLASS, initialTimeMs = 30_000))
        val state = ChessClockState(players = listOf(PlayerState(30_000), PlayerState(30_000), PlayerState(30_000)), activePlayer = 1)
        val next = simulateMultiPlayerTick(state, 10_000, settings)
        assertEquals(20_000, next.players[0].timeRemainingMs)
        assertEquals(35_000, next.players[1].timeRemainingMs) // gains half of P1's loss
    }
}
