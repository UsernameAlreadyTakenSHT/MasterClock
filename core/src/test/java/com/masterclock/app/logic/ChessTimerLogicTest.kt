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
 * [simulateMultiPlayerTick] below is a narrower, still-hand-written duplicate for the handful of
 * modes (HOURGLASS/CHRONO_COUNTDOWN/CHRONO_COUNTUP/PHASES/GONG/MOVE_TIMER_SHARED/MOVE_TIMER_GLOBAL_SHARED) that are
 * implemented inline in `ChessTimerViewModel.tick()` rather than in the extracted [tickPlayer]; that
 * remains a known, documented gap rather than something this change
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
        assertTrue(next.players[0].hasFlagged)
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

    // --- Modes still implemented inline in ChessTimerViewModel.tick(), not yet extracted ---

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
    // MOVE_TIMER_SHARED and PHASES have only their post-move side covered -- their countdown lives
    // inline in ChessTimerViewModel.tick() and their phase transitions in private methods, neither
    // reachable from here.

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
    fun `US_DELAY clamps the delay at zero rather than billing the overshoot to the main clock`() {
        val s = PlayerSettings(mode = TimerMode.US_DELAY, incrementMs = 5000)
        val settings = ChessClockSettings(main = s)
        // 500ms of delay left, a 1000ms tick: the 500ms of overshoot is not taken from the main
        // clock, it is dropped. At the 100ms tick this is worth at most a tenth of a second a move.
        val next = tickPlayer(PlayerState(timeRemainingMs = 300_000, delayRemainingMs = 500), 1000, s, settings)
        assertEquals(0, next.delayRemainingMs)
        assertEquals(300_000, next.timeRemainingMs)
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
        assertEquals(300_000, next.timeRemainingMs)
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

    // Modes whose countdown lives elsewhere: what matters here is that a press does NOT reset them.

    @Test
    fun `MOVE_TIMER_GLOBAL_SHARED restores the mover's move clock on a press`() {
        val s = PlayerSettings(mode = TimerMode.MOVE_TIMER_GLOBAL_SHARED, moveTimeMs = 30_000)
        val settings = ChessClockSettings(main = s)
        val state = ChessClockState(players = listOf(PlayerState(timeRemainingMs = 4000)), activePlayer = 1)
        val next = computePostMoveState(state, 1, 0, settings, s)
        assertEquals(30_000, next.players[0].timeRemainingMs)
    }

    @Test
    fun `MOVE_TIMER_SHARED is not reset by a press`() {
        val s = PlayerSettings(mode = TimerMode.MOVE_TIMER_SHARED, moveTimeMs = 30_000)
        val settings = ChessClockSettings(main = s)
        val state = ChessClockState(players = listOf(PlayerState(timeRemainingMs = 12_000)), activePlayer = 1)
        val next = computePostMoveState(state, 1, 0, settings, s)
        // One clock is shared by everyone, so refilling it on each press would make it endless.
        assertEquals(12_000, next.players[0].timeRemainingMs)
        assertEquals(1, next.players[0].moveCount)
    }

    @Test
    fun `PHASES is not reset by a press`() {
        val s = PlayerSettings(mode = TimerMode.PHASES)
        val settings = ChessClockSettings(main = s)
        val state = ChessClockState(players = listOf(PlayerState(timeRemainingMs = 45_000)), activePlayer = 1)
        val next = computePostMoveState(state, 1, 0, settings, s)
        // A phase runs to its own end; only a phase transition may change this clock.
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
