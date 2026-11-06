package com.masterclock.app.logic

import kotlin.random.Random

/**
 * A game played on the clock's pure functions, in the order the ViewModel calls them.
 *
 * tick() follows [ChessTimerViewModel]'s tick, stopIfGameOver and passTurnFromEliminated;
 * press() its startOrSwitch, and resume() its resume. Only the orchestration is written again here
 * -- which function runs, in which order, on which player -- and every piece of clock arithmetic
 * is the app's own: [tickCoupled], [tickPlayer], [computePostMoveState], [arbiterAdjustTime],
 * [arbiterAdjustMoveCount] and [initialClockState]. Sounds, the log and the autosave are left out;
 * nothing in them changes a clock.
 *
 * PHASES is left out: its transitions go through ViewModel methods that read the settings flows.
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
    val running get() = state.activePlayer != null && !state.isPaused

    fun tick(delta: Long) {
        if (!running) return
        moveElapsedMs += delta
        val before = state
        val active = before.activePlayer ?: 1
        val s = settings.playerSettings(active)
        val coupled = tickCoupled(before, delta, settings, s, active)
        state = if (coupled != null) {
            if (coupled.handedOverBy != null || coupled.gongMoves.isNotEmpty()) moveElapsedMs = 0
            if (s.mode == TimerMode.CHRONO_COUNTDOWN) coupled.state else coupled.state.withFirstFlag(before, active)
        } else {
            val newP = tickPlayer(before.players[active - 1], delta, s, settings)
            before.copy(
                players = before.players.toMutableList().apply { this[active - 1] = newP },
                isPaused = fb == FlagBehavior.FREEZE && newP.isOutOfTime,
                firstToFlag = if (newP.isOutOfTime && before.firstToFlag == null) active else before.firstToFlag,
            )
        }
        // stopIfGameOver
        if (state.activePlayer != null && isGameOver(state, fb, players)) state = state.copy(isPaused = true)
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
        if (state.activePlayer != null && isGameOver(state, fb, players)) return
        val p1 = settings.playerSettings(1)
        if (p1.mode == TimerMode.MOVE_TIMER_SHARED && p1.moveTimeMs <= 0) return
        val fresh = state.activePlayer == null
        if (fresh) moveElapsedMs = 0
        val active = state.activePlayer ?: 1
        val mode = settings.playerSettings(active).mode
        if (flagsEndGame(state.players, fb, players)) return
        val wholeTable = mode == TimerMode.MOVE_TIMER_SHARED || (mode.name.startsWith("CHRONO") && settings.isOneForAll)
        startClock(active, armDelay = fresh && !wholeTable)
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

/** Every mode the simulation can play: PHASES' transitions live in the ViewModel. */
internal val SIMULATED_MODES = TimerMode.entries.filter { it != TimerMode.PHASES }

/** A time control small enough that flags, periods and reserves all come within a few dozen actions. */
internal fun randomSimSettings(r: Random): ChessClockSettings {
    fun sec(from: Int, to: Int) = r.nextInt(from, to + 1) * 1000L
    val periods = List(r.nextInt(1, 4)) {
        FidePeriod(timeMs = sec(5, 30), incrementMs = sec(0, 5), movesToNext = r.nextInt(0, 6), isFischer = r.nextBoolean(), hasDelay = r.nextBoolean())
    }
    val s = PlayerSettings(
        mode = SIMULATED_MODES.random(r),
        initialTimeMs = sec(5, 60),
        moveTimeMs = sec(2, 15),
        incrementMs = sec(0, 5),
        timeCapMs = sec(5, 40),
        byoyomiPeriods = r.nextInt(0, 4),
        byoyomiTimeMs = sec(2, 10),
        byoyomiProgression = r.nextInt(0, 3),
        maxMoves = r.nextInt(1, 8),
        fidePeriods = periods,
        gongSimultaneous = r.nextBoolean(),
        gongReflectionMs = sec(2, 6),
        gongMoveMs = sec(1, 3),
        fastMoveMode = FastMoveType.entries.random(r),
        fastMoveGracePeriodMs = sec(0, 5),
        fastMoveFastPeriodMs = sec(3, 15),
        fastMoveAccelRate = listOf(0.15f, 0.5f, 1f).random(r),
        fastMoveFullAccelRate = listOf(1f, 2f).random(r),
        fastMoveShrinkDecrementMs = sec(0, 5),
        fastMoveShrinkFloorMs = sec(1, 5),
        fastMoveTransferCumulative = r.nextBoolean(),
    )
    return ChessClockSettings(
        main = s,
        flagBehavior = FlagBehavior.entries.random(r),
        numberOfPlayers = if (r.nextInt(3) == 0) r.nextInt(3, 5) else 2,
        forcedMoveCounter = r.nextBoolean(),
        isOneForAll = r.nextBoolean(),
        fischerFideFirstMove = r.nextBoolean(),
    )
}

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
