package com.masterclock.app.logic

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Rules that hold in every mode, flag behaviour and number of players, checked after every action
 * of thousands of random games played by [ClockSim].
 *
 * The tests beside this one pin the cases someone thought of. The clock's defects kept turning up
 * in the ones nobody had: the arbiter taking a Global reserve to exactly zero under REVERSE, a FIDE
 * period undone below zero, reserve given back to a player in overrun. Each rule here is something
 * the clock must never do, whatever led up to it; a failure prints the shortest game that breaks it.
 */
class ClockInvariantsTest {

    @Test
    fun `no random game breaks a rule of the clock`() {
        val failures = findViolations(games = GAMES, actions = ACTIONS_PER_GAME)
        assertTrue(failures.joinToString("\n\n") { it.report() }, failures.isEmpty())
    }

    internal data class Violation(val rule: String, val detail: String, val settings: ChessClockSettings, val actions: List<SimAction>) {
        fun report(): String {
            val s = settings.main
            return "$rule: $detail\n  ${s.mode} ${settings.flagBehavior} players=${settings.numberOfPlayers} " +
                "forcedMoveCounter=${settings.forcedMoveCounter} oneForAll=${settings.isOneForAll}\n  $s\n  actions: $actions"
        }
    }

    internal companion object {
        const val GAMES = 6_000
        const val ACTIONS_PER_GAME = 160

        /** The first failure of each rule, each cut down to the fewest actions that still break it. */
        fun findViolations(games: Int, actions: Int, seed: Long = 20261103L): List<Violation> {
            val seen = linkedMapOf<String, Violation>()
            val r = Random(seed)
            repeat(games) {
                val settings = randomSimSettings(r)
                val sim = ClockSim(settings)
                val played = mutableListOf<SimAction>()
                for (i in 0 until actions) {
                    val action = randomSimAction(r, sim)
                    played += action
                    val broken = step(sim, action) ?: continue
                    if (broken.first !in seen) seen[broken.first] = shrink(settings, played.toList(), broken.first)
                    break
                }
            }
            return seen.values.toList()
        }

        /** Plays one action and checks it; the rule broken and how, or null. */
        fun step(sim: ClockSim, action: SimAction): Pair<String, String>? {
            val before = sim.state
            val elapsed = sim.moveElapsedMs
            val wasRunning = sim.running
            action.applyTo(sim)
            return check(sim.settings, before, sim.state, action, wasRunning, elapsed)
        }

        fun replay(settings: ChessClockSettings, actions: List<SimAction>): Pair<String, String>? {
            val sim = ClockSim(settings)
            for (a in actions) step(sim, a)?.let { return it }
            return null
        }

        /** Drops every action the failure does not need. */
        fun shrink(settings: ChessClockSettings, actions: List<SimAction>, rule: String): Violation {
            var current = actions
            var changed = true
            while (changed) {
                changed = false
                var i = 0
                while (i < current.size) {
                    val candidate = current.filterIndexed { idx, _ -> idx != i }
                    if (replay(settings, candidate)?.first == rule) { current = candidate; changed = true } else i++
                }
            }
            val (_, detail) = replay(settings, current) ?: (rule to "not reproduced")
            return Violation(rule, detail, settings, current)
        }

        // --- the rules ---

        private fun signed(p: PlayerState, fb: FlagBehavior) =
            if (p.isOutOfTime && (p.isNegative || fb == FlagBehavior.REVERSE)) -p.timeRemainingMs else p.timeRemainingMs

        /** Modes with one clock per player, counting down, credited straight by the arbiter. */
        private val CREDITED = setOf(
            TimerMode.SUDDEN_DEATH, TimerMode.FISCHER, TimerMode.BRONSTEIN, TimerMode.US_DELAY,
            TimerMode.MOVE_TIMER_STANDARD, TimerMode.MOVE_TIMER_SAVE_CAP, TimerMode.HOURGLASS,
            TimerMode.BYOYOMI_JAPANESE, TimerMode.BYOYOMI_CANADIAN, TimerMode.BYOYOMI_PROGRESSIVE,
            TimerMode.FIDE_PERIODS, TimerMode.RANDOM, TimerMode.HIDDEN, TimerMode.FAST_MOVE,
        )
        private val RESERVE = setOf(TimerMode.MOVE_TIMER_GLOBAL, TimerMode.MOVE_TIMER_GLOBAL_SHARED, TimerMode.MOVE_TIMER_OVERTIME)
        /** One clock that only ever runs down while its player is on the move. */
        private val PLAIN = setOf(
            TimerMode.SUDDEN_DEATH, TimerMode.FISCHER, TimerMode.BRONSTEIN, TimerMode.US_DELAY,
            TimerMode.MOVE_TIMER_STANDARD, TimerMode.MOVE_TIMER_SAVE_CAP, TimerMode.RANDOM, TimerMode.HIDDEN, TimerMode.FAST_MOVE,
        )

        fun check(settings: ChessClockSettings, before: ChessClockState, after: ChessClockState, action: SimAction, wasRunning: Boolean, elapsed: Long): Pair<String, String>? {
            val fb = settings.flagBehavior
            val mode = settings.main.mode
            val n = settings.numberOfPlayers.coerceAtLeast(1)

            for ((i, p) in after.players.withIndex()) {
                val who = "P${i + 1} $p"
                // Nothing is stored below zero: an overrun is stored positive beside its flag.
                if (p.timeRemainingMs < 0 || p.secondaryTimeMs < 0 || p.delayRemainingMs < 0) return "stored time is never negative" to who
                if (mode == TimerMode.FIDE_PERIODS && p.currentPeriodIndex !in settings.main.fidePeriods.indices) return "a FIDE period index stays in its list" to who
                if (p.byoyomiPeriodsRemaining < 0 || p.movesRemainingInPeriod < 0) return "a count left is never negative" to who

                // A clock that has run out is marked so: a player at 0:00 who is not out plays on
                // forever, and under FREEZE the game never stops.
                val runOut = when (mode) {
                    in CREDITED -> p.timeRemainingMs == 0L && p.delayRemainingMs == 0L
                    TimerMode.MOVE_TIMER_GLOBAL, TimerMode.MOVE_TIMER_GLOBAL_SHARED -> p.timeRemainingMs == 0L || p.secondaryTimeMs == 0L
                    TimerMode.MOVE_TIMER_OVERTIME -> p.timeRemainingMs == 0L && p.secondaryTimeMs == 0L
                    else -> false
                }
                if (runOut && !p.isOutOfTime) return "a clock that has run out is out" to who

                // Under NEGATIVE a player who is out shows the overrun: out with time still on the
                // clock reads FLAGGED while counting down, and the next press refills it.
                if (fb == FlagBehavior.NEGATIVE && (mode in CREDITED || mode in RESERVE) && p.isOutOfTime && !p.isNegative && p.timeRemainingMs > 0) {
                    return "under NEGATIVE, out means an overrun shown" to who
                }
            }

            if (fb == FlagBehavior.FREEZE || fb == FlagBehavior.FLAG) {
                if (mode != TimerMode.CHRONO_COUNTDOWN && after.players.any { it.isOutOfTime } && after.firstToFlag == null) {
                    return "a flag that stands has a first flagger" to "$after"
                }
            }
            after.firstToFlag?.let { if (it !in 1..n) return "firstToFlag is a player" to "$after" }
            if (after.activePlayer != null && !after.isPaused && isGameOver(after, fb, n)) return "a game that is over is not running" to "$after"

            when (action) {
                is SimAction.Tick -> if (wasRunning) checkTick(settings, before, after, action.ms)?.let { return it }
                is SimAction.Press -> checkPress(settings, before, after, action.player, elapsed)?.let { return it }
                is SimAction.ArbiterTime -> checkArbiterTime(settings, before.copy(isPaused = true), after, action)?.let { return it }
                is SimAction.ArbiterMoves -> checkArbiterMoves(settings, before.copy(isPaused = true), after, action)?.let { return it }
                else -> {}
            }
            return null
        }

        private fun checkTick(settings: ChessClockSettings, before: ChessClockState, after: ChessClockState, delta: Long): Pair<String, String>? {
            val fb = settings.flagBehavior
            val mode = settings.main.mode
            val active = before.activePlayer ?: return null
            for (i in before.players.indices) {
                val b = before.players[i]
                val a = after.players[i]
                // An overrun begins at zero: one tick cannot put a player further over than the
                // tick lasted. Under REVERSE a flag with move time still on the clock read as
                // that much overrun at once.
                if (mode in CREDITED || mode in RESERVE) {
                    val sb = signed(b, fb)
                    val sa = signed(a, fb)
                    if (sb >= 0 && sa < 0 && -sa > delta) return "an overrun begins at zero" to "P${i + 1} ${delta}ms: $b -> $a"
                }
                if (mode in PLAIN && i + 1 != active && b != a && settings.flagBehavior != FlagBehavior.FLAG) {
                    return "only the player on the move is charged" to "P${i + 1}: $b -> $a"
                }
            }
            if (mode in PLAIN) {
                val b = before.players[active - 1]
                val a = after.players[active - 1]
                if (signed(a, fb) > signed(b, fb)) return "the clock on the move only runs down" to "P$active ${delta}ms: $b -> $a"
            }
            // Hourglass: what one player loses the others gain, while nobody is out.
            if (mode == TimerMode.HOURGLASS && before.players.none { it.isOutOfTime } && after.players.none { it.isOutOfTime }) {
                val sb = before.players.sumOf { it.timeRemainingMs }
                val sa = after.players.sumOf { it.timeRemainingMs }
                if (sb != sa) return "Hourglass keeps its total" to "$sb -> $sa"
            }
            return null
        }

        private fun checkPress(settings: ChessClockSettings, before: ChessClockState, after: ChessClockState, player: Int, elapsed: Long): Pair<String, String>? {
            val fb = settings.flagBehavior
            val s = settings.main
            if (before.activePlayer != player || before.isPaused) return null
            val b = before.players[player - 1]
            val a = after.players[player - 1]
            if (a.moveCount == b.moveCount) return null
            val expected = when (s.mode) {
                TimerMode.FISCHER -> s.incrementMs
                TimerMode.BRONSTEIN -> elapsed.coerceAtMost(s.incrementMs)
                else -> return null
            }
            // An increment is the increment: once, and on the signed time (an overrun is paid off).
            if (!b.isOutOfTime || fb == FlagBehavior.NEGATIVE || fb == FlagBehavior.REVERSE) {
                if (signed(a, fb) != signed(b, fb) + expected) return "a press adds the increment once" to "P$player: $b -> $a"
            }
            return null
        }

        private fun checkArbiterTime(settings: ChessClockSettings, before: ChessClockState, after: ChessClockState, action: SimAction.ArbiterTime): Pair<String, String>? {
            val fb = settings.flagBehavior
            val mode = settings.main.mode
            val i = action.player - 1
            val amount = action.ms
            val b = before.players.getOrNull(i) ?: return null
            val a = after.players[i]
            val overflowing = fb == FlagBehavior.NEGATIVE || fb == FlagBehavior.REVERSE
            val what = "P${action.player} ${if (amount > 0) "+" else ""}${amount}ms: $b -> $a"

            if (mode in CREDITED) {
                // The arbiter's time is exactly what is shown: a penalty past zero is an overrun
                // under NEGATIVE and REVERSE, a flag under FREEZE and FLAG.
                if (overflowing && signed(a, fb) != signed(b, fb) + amount) return "the arbiter's time is exact" to what
                if (!overflowing && !b.isOutOfTime) {
                    val t = b.timeRemainingMs + amount
                    if (a.timeRemainingMs != t.coerceAtLeast(0) || a.isOutOfTime != (t <= 0)) return "the arbiter's time is exact" to what
                }
            }

            // Time given back puts a player back in play, once it covers what they are over by.
            if (amount > 0 && b.isOutOfTime && (mode in CREDITED || mode in RESERVE)) {
                val over = (-signed(b, fb)).coerceAtLeast(0)
                if ((!overflowing || amount > over) && a.isOutOfTime) return "time given back brings the player back" to what
            }

            // Given and taken back, or taken and given back, is where it started -- except where
            // something is cut off at zero: a clock under FREEZE and FLAG, a reserve in any mode.
            if (mode in CREDITED || mode in RESERVE) {
                val back = arbiterAdjustTime(arbiterAdjustTime(before, action.player, amount, settings), action.player, -amount, settings)
                val canUndo = if (mode in RESERVE) !b.isOutOfTime && b.secondaryTimeMs + amount > 0
                    else overflowing || (amount > 0 && !b.isOutOfTime)
                if (canUndo && !sameClock(before, back, fb)) return "the arbiter can undo what it did" to "P${action.player} ±${amount}ms: $b -> ${back.players[i]}"
            }
            return null
        }

        private fun checkArbiterMoves(settings: ChessClockSettings, before: ChessClockState, after: ChessClockState, action: SimAction.ArbiterMoves): Pair<String, String>? {
            if (settings.main.mode != TimerMode.FIDE_PERIODS || !settings.forcedMoveCounter) return null
            val fb = settings.flagBehavior
            val i = action.player - 1
            val b = before.players[i]
            val overflowing = fb == FlagBehavior.NEGATIVE || fb == FlagBehavior.REVERSE
            // A move the arbiter adds and takes back leaves the clock as it was; under NEGATIVE and
            // REVERSE, one taken and added back does too, since nothing is cut off at zero.
            if (action.delta > 0 || overflowing) {
                if (b.moveCount + action.delta < 0 || b.moveCount + action.delta > MAX_FIDE_MOVES) return null
                // A period reached on its time with its count already passed is due, and the next
                // count, by press or by the arbiter, starts it: there is nothing to go back to.
                val total = fideMoveTotals(settings.main.fidePeriods).getOrNull(b.currentPeriodIndex) ?: 0
                if (total > 0 && b.moveCount >= total) return null
                val back = arbiterAdjustMoveCount(arbiterAdjustMoveCount(before, action.player, action.delta, settings), action.player, -action.delta, settings)
                if (!sameClock(before, back, fb)) return "the arbiter can undo a move count" to "P${action.player} ±1: $b -> ${back.players[i]}"
            }
            return null
        }

        /** Same time, flags and stage for everyone; the delay re-armed by a period is left aside, as decided. */
        private fun sameClock(x: ChessClockState, y: ChessClockState, fb: FlagBehavior) = x.players.indices.all { i ->
            val p = x.players[i]
            val q = y.players[i]
            signed(p, fb) == signed(q, fb) && p.isOutOfTime == q.isOutOfTime && p.secondaryTimeMs == q.secondaryTimeMs &&
                p.currentPeriodIndex == q.currentPeriodIndex && p.moveCount == q.moveCount
        }
    }
}
