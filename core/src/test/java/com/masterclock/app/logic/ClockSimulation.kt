package com.masterclock.app.logic

import kotlin.random.Random

/**
 * A game played on the clock's pure functions, in the order the ViewModel calls them.
 *
 * tick() is the app's own [tickClock], followed by what [ChessTimerViewModel]'s stopIfGameOver and
 * passTurnFromEliminated do; press() follows its startOrSwitch, and resume() its resume. Only that
 * orchestration is written again here, and every piece of clock arithmetic is the app's:
 * [computePostMoveState], [phaseTap], [arbiterAdjustTime], [arbiterAdjustMoveCount] and
 * [initialClockState]. Sounds, the log and the autosave are left out; nothing in them changes a clock.
 *
 * Time here is a number: [moveElapsedMs] is what the ViewModel measures with elapsedRealtime
 * between a move's start and its press, less the time spent paused.
 */
internal class ClockSim(val settings: ChessClockSettings) {
    var state: ChessClockState = initialClockState(settings, RANDOM_ROLL)
        private set
    var moveElapsedMs = 0L
        private set

    private val fb get() = settings.flagBehavior
    private val players get() = settings.numberOfPlayers.coerceAtLeast(1)
    /** A PHASES game finished or flagged by its phase, which [isGameOver] does not know under NEGATIVE and REVERSE. */
    private val phasesOver get() = settings.playerSettings(1).mode == TimerMode.PHASES && state.players[0].isOutOfTime
    val running get() = state.activePlayer != null && !state.isPaused

    fun tick(delta: Long) {
        if (!running) return
        moveElapsedMs += delta
        val result = tickClock(state, delta, settings)
        state = result.state
        result.coupled?.let { if (it.handedOverBy != null || it.gongMoves.isNotEmpty()) moveElapsedMs = 0 }
        // stopIfGameOver
        if (state.activePlayer != null && (phasesOver || isGameOver(state, fb, players))) state = state.copy(isPaused = true)
        // passTurnFromEliminated
        val now = state.activePlayer ?: return
        if (state.isPaused || !turnPassesFromEliminated(state, settings, settings.playerSettings(now).mode)) return
        val next = nextPlayerInTurn(now, state.players, settings.numberOfPlayers, fb)
        if (next == now) return
        moveElapsedMs = 0
        startClock(next)
    }

    fun press(playerIndex: Int) {
        val s = settings.playerSettings(playerIndex)
        if (s.mode == TimerMode.GONG && state.activePlayer != null) { if (state.isPaused) resume(); return }
        if (s.mode == TimerMode.PHASES) {
            if (state.players[0].isOutOfTime) return
            if (state.isPaused) { resume(); return }
            phaseTap(state, state.players[0], settings)?.let { state = it }
            return
        }
        if (isGameOver(state, fb, players)) return
        if (s.mode == TimerMode.MOVE_TIMER_SHARED) return
        if (s.mode.name.startsWith("CHRONO") && settings.isOneForAll) return
        val active = state.activePlayer
        if (active != null && active != playerIndex) return
        if (state.isPaused && active != null) { resume(); return }
        val next = nextPlayerInTurn(playerIndex, state.players, players, fb)
        if (active == null) {
            moveElapsedMs = 0
            startClock(next)
            return
        }
        val before = state.players[playerIndex - 1]
        state = computePostMoveState(state, playerIndex, moveElapsedMs, settings, s)
        val after = state.players[playerIndex - 1]
        if (!before.isOutOfTime && after.isOutOfTime) {
            if (state.firstToFlag == null) state = state.copy(firstToFlag = playerIndex)
            if (isGameOver(state, fb, players)) { state = state.copy(isPaused = true); return }
        }
        moveElapsedMs = 0
        startClock(next)
    }

    fun pause() {
        if (state.activePlayer != null) state = state.copy(isPaused = true)
    }

    fun resume() {
        if (phasesOver) return
        if (state.activePlayer != null && isGameOver(state, fb, players)) return
        val p1 = settings.playerSettings(1)
        if (p1.mode == TimerMode.MOVE_TIMER_SHARED && p1.moveTimeMs <= 0) return
        val fresh = state.activePlayer == null
        if (fresh) moveElapsedMs = 0
        val active = state.activePlayer ?: 1
        val mode = settings.playerSettings(active).mode
        if (flagsEndGame(state.players, fb, players)) return
        val wholeTable = mode == TimerMode.MOVE_TIMER_SHARED || (mode.name.startsWith("CHRONO") && settings.isOneForAll)
        startClock(active, armDelay = fresh && !wholeTable && mode != TimerMode.PHASES)
    }

    /** The arbiter's ±time; the panel opens only on a paused clock. */
    fun arbiterTime(playerIndex: Int, amountMs: Long) {
        pause()
        state = arbiterAdjustTime(state, playerIndex, amountMs, settings)
    }

    /** The arbiter's ±1 move, offered for FIDE periods ended by moves. */
    fun arbiterMoves(playerIndex: Int, delta: Int) {
        if (settings.playerSettings(playerIndex).mode != TimerMode.FIDE_PERIODS || !settings.forcedMoveCounter) return
        pause()
        state = arbiterAdjustMoveCount(state, playerIndex, delta, settings)
    }

    private fun startClock(playerIndex: Int, armDelay: Boolean = true) {
        val s = settings.playerSettings(playerIndex)
        state = state.copy(
            activePlayer = playerIndex,
            isPaused = false,
            players = state.players.mapIndexed { idx, p ->
                if (armDelay && idx + 1 == playerIndex && s.mode == TimerMode.US_DELAY) p.copy(delayRemainingMs = s.incrementMs) else p
            },
        )
    }

    companion object {
        /** RANDOM and HIDDEN's draw, fixed so a failure replays. */
        val RANDOM_ROLL = 30_000L to 2_000L
    }
}

internal sealed class SimAction {
    data class Tick(val ms: Long) : SimAction()
    data class Press(val player: Int) : SimAction()
    object Pause : SimAction() { override fun toString() = "Pause" }
    object Resume : SimAction() { override fun toString() = "Resume" }
    data class ArbiterTime(val player: Int, val ms: Long) : SimAction()
    data class ArbiterMoves(val player: Int, val delta: Int) : SimAction()

    fun applyTo(sim: ClockSim) = when (this) {
        is Tick -> sim.tick(ms)
        is Press -> sim.press(player)
        Pause -> sim.pause()
        Resume -> sim.resume()
        is ArbiterTime -> sim.arbiterTime(player, ms)
        is ArbiterMoves -> sim.arbiterMoves(player, delta)
    }
}

/** A time control small enough that flags, periods and reserves all come within a few dozen actions. */
internal fun randomSimSettings(r: Random): ChessClockSettings {
    fun sec(from: Int, to: Int) = r.nextInt(from, to + 1) * 1000L
    val periods = List(r.nextInt(1, 4)) {
        FidePeriod(timeMs = sec(5, 30), incrementMs = sec(0, 5), movesToNext = r.nextInt(0, 6), isFischer = r.nextBoolean(), hasDelay = r.nextBoolean())
    }
    val s = PlayerSettings(
        mode = TimerMode.entries.random(r),
        // From zero, and with settings that contradict each other: the editor takes them all.
        initialTimeMs = sec(0, 60),
        moveTimeMs = sec(0, 15),
        incrementMs = sec(0, 5),
        timeCapMs = sec(5, 40),
        byoyomiPeriods = r.nextInt(0, 4),
        byoyomiTimeMs = sec(2, 10),
        byoyomiProgression = r.nextInt(0, 3),
        maxMoves = r.nextInt(0, 8),
        fidePeriods = periods,
        phases = List(r.nextInt(1, 5)) { GamePhase(timeMs = sec(0, 15), autoAdvance = r.nextBoolean(), flagOnEnd = r.nextInt(4) == 0) },
        gongSimultaneous = r.nextBoolean(),
        gongReflectionMs = sec(0, 6),
        gongMoveMs = sec(0, 3),
        fastMoveMode = FastMoveType.entries.random(r),
        fastMoveGracePeriodMs = sec(0, 20),
        fastMoveFastPeriodMs = sec(0, 15),
        fastMoveAccelRate = listOf(0.15f, 0.5f, 1f).random(r),
        fastMoveFullAccelRate = listOf(1f, 2f).random(r),
        fastMoveShrinkDecrementMs = sec(0, 5),
        fastMoveShrinkFloorMs = sec(1, 90),
        fastMoveTransferCumulative = r.nextBoolean(),
    ).let {
        // Not from zero with a reserve. One that starts empty has no moment of running out, so its
        // overrun does not begin at zero: known, and to be settled apart from these rules.
        if (it.mode in RESERVE_MODES) it.copy(initialTimeMs = sec(5, 60), moveTimeMs = sec(2, 15)) else it
    }
    return ChessClockSettings(
        main = s,
        flagBehavior = FlagBehavior.entries.random(r),
        numberOfPlayers = if (r.nextInt(3) == 0) r.nextInt(3, 5) else 2,
        forcedMoveCounter = r.nextBoolean(),
        isOneForAll = r.nextBoolean(),
        fischerFideFirstMove = r.nextBoolean(),
        loopPhases = r.nextBoolean(),
        allowPhaseSkip = r.nextBoolean(),
        pauseBetweenPhasesMs = if (r.nextBoolean()) sec(1, 5) else 0,
        pauseUntilTap = r.nextInt(4) == 0,
    )
}

private val RESERVE_MODES = setOf(TimerMode.MOVE_TIMER_GLOBAL, TimerMode.MOVE_TIMER_GLOBAL_SHARED, TimerMode.MOVE_TIMER_OVERTIME)
private val TICKS = longArrayOf(10, 100, 100, 100, 100, 1_000, 3_000, 10_000)
private val ARBITER_AMOUNTS = longArrayOf(1_000, -1_000, 10_000, -10_000, 60_000, -60_000)

/** The next action: mostly time passing and the player on the move pressing, sometimes the arbiter. */
internal fun randomSimAction(r: Random, sim: ClockSim): SimAction {
    val n = sim.settings.numberOfPlayers.coerceAtLeast(1)
    val roll = r.nextInt(100)
    return when {
        roll < 50 -> SimAction.Tick(TICKS[r.nextInt(TICKS.size)])
        roll < 72 -> SimAction.Press(sim.state.activePlayer ?: r.nextInt(1, n + 1))
        roll < 76 -> SimAction.Press(r.nextInt(1, n + 1))
        roll < 79 -> SimAction.Pause
        roll < 82 -> SimAction.Resume
        roll < 94 -> SimAction.ArbiterTime(r.nextInt(1, n + 1), ARBITER_AMOUNTS[r.nextInt(ARBITER_AMOUNTS.size)])
        else -> SimAction.ArbiterMoves(r.nextInt(1, n + 1), if (r.nextBoolean()) 1 else -1)
    }
}
