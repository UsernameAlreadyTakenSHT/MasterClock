package com.masterclock.app.logic

import kotlinx.serialization.json.Json
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

/**
 * Whole Omni sessions, played by the thousand over settings drawn at random, with the rules a
 * session must keep checked after every single action.
 *
 * [OmniTimerLogicTest] and [OmniTickTest] pin the cases somebody thought of. Omni has five nested
 * clocks, four player orders, three banks in four scopes, five cutoffs and three kinds of pause,
 * and its defects have all sat where two of those meet -- a bank across a round that loops, a
 * cutoff during a pause. Nobody lists those by hand, so this plays them: it drives the engine as
 * the screen does (Next, Ready, Pause, the app closed and reopened, time passing in ticks of every
 * size) and asks, each time, whether the session still makes sense.
 *
 * A failure names its seed. Run that seed alone to replay the session action for action.
 */
class OmniSimulationTest {

    // ---------------------------------------------------------------------------------------
    // Settings, drawn at random
    // ---------------------------------------------------------------------------------------

    private fun <T> Random.of(vararg options: T): T = options[nextInt(options.size)]
    private fun Random.chance(percent: Int) = nextInt(100) < percent

    /** Short, so a session is a few hundred ticks. [edge] lets a duration be zero. */
    private fun Random.duration(edge: Boolean): Long =
        if (edge && chance(15)) 0L else of(1_000L, 2_000L, 3_000L, 5_000L, 10_000L)

    private fun Random.phases(edge: Boolean): List<OmniPhaseSettings> =
        List(of(0, 0, 1, 2, 3)) { OmniPhaseSettings(id = "p$it", durationMs = duration(edge)) }

    private fun Random.round(edge: Boolean, index: Int): OmniRoundSettings {
        val sequence = chance(40)
        // A FIXED round can carry turns left from when it was a SEQUENCE one; they must not count.
        val customTurns = List(if (sequence) of(1, 1, 2, 3) else of(0, 0, 2)) {
            OmniTurnSettings(id = "t$it", durationMs = duration(edge), phases = phases(edge))
        }
        return OmniRoundSettings(
            id = "r$index",
            durationMs = duration(edge),
            turnDurationMs = duration(edge),
            turnLogic = if (sequence) RoundTurnLogic.SEQUENCE else RoundTurnLogic.FIXED,
            roundEndBehavior = if (chance(30)) RoundEndBehavior.LOOP else RoundEndBehavior.ADVANCE,
            // In the wizard a SEQUENCE round always has a turn. A backup can bring one with none.
            customTurns = if (sequence && edge && chance(15)) emptyList() else customTurns,
            fixedTurnPhases = phases(edge),
        )
    }

    private fun Random.settings(edge: Boolean): OmniSettings = OmniSettings(
        useGlobalClock = chance(50),
        useGameClock = chance(60),
        useRoundClock = chance(60),
        useTurnClock = chance(80),
        usePhaseClock = chance(40),
        numberOfPlayers = of(1, 2, 2, 3, 4, 5),
        playerOrderType = of(*PlayerOrderType.entries.toTypedArray()),
        randomEachTurn = chance(50),
        randomAvoidBackToBack = chance(60),
        randomAutoBalance = chance(50),
        globalDurationMs = if (edge && chance(10)) 0L else of(5_000L, 20_000L, 60_000L, 600_000L),
        games = List(of(1, 1, 2, 3)) { g ->
            OmniGameSettings(id = "g$g", durationMs = if (edge && chance(10)) 0L else of(4_000L, 15_000L, 60_000L), rounds = List(of(1, 2, 3)) { round(edge, it) })
        },
        interTurnPauseMs = of(0L, 0L, 500L, 2_000L),
        interRoundPauseMs = of(0L, 0L, 1_000L),
        interGamePauseMs = of(0L, 0L, 1_500L),
        launchCountdownMs = of(0L, 300L, 3_000L),
        transitionType = of(*TransitionType.entries.toTypedArray()),
        pauseDeductsFromGlobal = chance(30),
        pauseDeductsFromGame = chance(30),
        pauseDeductsFromRound = chance(30),
        timeBankMode = of(*TimeBankMode.entries.toTypedArray()),
        timeBankScope = of(*TimeBankScope.entries.toTypedArray()),
        soundTurnEnd = chance(70),
        soundRoundEnd = chance(70),
        soundGameEnd = chance(70),
        phaseForcesCutoff = chance(30),
        turnForcesCutoff = chance(30),
        roundForcesCutoff = chance(30),
        gameForcesCutoff = chance(30),
        globalForcesCutoff = chance(30),
    )

    // ---------------------------------------------------------------------------------------
    // What went wrong, by rule
    // ---------------------------------------------------------------------------------------

    private class Stop : RuntimeException()

    private class Findings {
        val byRule = LinkedHashMap<String, MutableList<String>>()
        var sessions = 0
        var finished = 0
        var actions = 0L

        fun report(): String = buildString {
            append("${byRule.values.sumOf { it.size }} sessions out of $sessions broke a rule:\n")
            byRule.forEach { (rule, cases) ->
                append("\n== $rule (${cases.size} sessions)\n   ${cases.first()}\n")
                // The first in full, the rest by their first line: enough to see one cause or several.
                cases.drop(1).take(60).forEach { append("   ${it.lineSequence().first()}\n") }
            }
        }

        fun assertNone() { if (byRule.isNotEmpty()) fail(report()) }
    }

    // ---------------------------------------------------------------------------------------
    // The session, driven as the screen and the ViewModel drive it
    // ---------------------------------------------------------------------------------------

    private data class Place(val game: Int, val round: Int, val pass: Int, val turn: Int, val phase: Int) : Comparable<Place> {
        override fun compareTo(other: Place): Int = compareValuesBy(this, other, { it.game }, { it.round }, { it.pass }, { it.turn }, { it.phase })
        fun sameTurnAs(other: Place) = game == other.game && round == other.round && pass == other.pass && turn == other.turn
    }

    private val OmniState.place get() = Place(currentGameIndex, currentRoundIndex, roundPasses, turnCounterInRound, currentPhaseIndex)
    private val OmniState.over get() = isInTransition && transitionLabel == "SESSION"
    private val OmniState.bankTotal get() = playerTimeBanks.values.sum()

    private val json = Json

    private inner class Session(var settings: OmniSettings, val seed: Int, val rng: Random, val findings: Findings, val strict: Boolean = true) {
        lateinit var state: OmniState
        private val trail = ArrayDeque<String>()
        /** The player of each turn as it began, by game, round and pass. */
        val turnsPlayed = ArrayList<Pair<Place, Int>>()
        var nexts = 0

        private fun note(action: String) {
            findings.actions++
            trail.addLast(action)
            if (trail.size > 14) trail.removeFirst()
        }

        fun rule(name: String, holds: Boolean, detail: () -> String = { "" }) {
            if (holds) return
            findings.byRule.getOrPut(name) { ArrayList() }.add(
                "seed $seed: ${detail()}\n   last actions: ${trail.joinToString(" > ")}\n   state: $state\n   settings: $settings"
            )
            throw Stop()
        }

        private fun freshTurnMs(s: OmniState) =
            getOmniDuration(settings, "TURN", gameIdx = s.currentGameIndex, roundIdx = s.currentRoundIndex, playerIdx = s.turnCounterInRound)

        /** startOmni(), with the first round's order drawn from this session's own dice. */
        fun start() {
            val order = omniRoundOrderFor(settings, gameIdx = 0, roundIdx = 0, previousPlayer = null, random = rng)
            state = createInitialOmniState(settings).copy(
                roundPlayerOrder = order,
                currentPlayerIndex = omniPlayerForTurn(order, 0, 0, settings),
                isRunning = true, hasStarted = true,
                isLaunching = settings.launchCountdownMs > 0, launchTimeRemainingMs = settings.launchCountdownMs,
            )
            note("start")
            turnsPlayed += state.place to state.currentPlayerIndex
            standing()
        }

        val canNext get() = state.hasStarted && !state.isLaunching && !state.isInTransition
        val canReady get() = state.isInTransition && !state.over && settings.transitionType == TransitionType.MANUAL_READY

        fun next() {
            val before = state
            note("next")
            state = computeOmniAdvance(state, settings, random = rng)
            nexts++
            rule("Next always moves the session on", state.over || state.place > before.place) { "from ${before.place} to ${state.place}" }
            moved(before, wasPaused = false)
            standing()
        }

        fun ready() {
            note("ready")
            state = state.copy(isInTransition = false, transitionTimeRemainingMs = 0)
            standing()
        }

        fun pause() { note("pause"); state = state.copy(isRunning = false) }
        fun resume() { note("resume"); state = state.copy(isRunning = true) }

        /** The app closed and reopened: what is stored, read back, and optionally taken as the session. */
        fun closeAndReopen(adopt: Boolean) {
            note(if (adopt) "reopen" else "save")
            val stored = omniSessionToStore(state)
            rule("a session under way is stored, a finished one is not", (stored != null) == (state.hasStarted && !state.over))
            if (stored == null) return
            val read = json.decodeFromString(OmniState.serializer(), json.encodeToString(OmniState.serializer(), stored))
            rule("a stored session reads back as it was written", read == stored) { "wrote $stored, read $read" }
            val restored = omniSessionToRestore(read, settings)
            rule("a session comes back paused and otherwise as it was left", restored == state.copy(isRunning = false)) { "came back as $restored" }
            if (adopt) state = restored!!
        }

        /** The paused session's settings changed under it, as updateOmniSettings does. */
        fun edit(newSettings: OmniSettings) {
            note("edit")
            settings = newSettings
            if (state.currentPlayerIndex >= newSettings.numberOfPlayers) state = state.copy(currentPlayerIndex = 0)
        }

        /** What [delta] of running time must force, by the engine's own rule: outermost first. */
        private fun owedCutoff(before: OmniState, delta: Long): String? {
            if (before.isLaunching && before.launchTimeRemainingMs > delta) return null
            fun crosses(clock: Long, runs: Boolean) = runs && clock > 0 && clock <= delta
            val loops = omniCurrentRound(settings, before).roundEndBehavior == RoundEndBehavior.LOOP
            if (before.isInTransition) {
                return when {
                    settings.globalForcesCutoff && crosses(before.currentGlobalTimeMs, settings.pauseDeductsFromGlobal && settings.useGlobalClock) -> "SESSION"
                    settings.gameForcesCutoff && crosses(before.currentGameTimeMs, settings.pauseDeductsFromGame && settings.useGameClock) -> "GAME"
                    (settings.roundForcesCutoff || loops) && crosses(before.currentRoundTimeMs, settings.pauseDeductsFromRound && settings.useRoundClock) -> "ROUND"
                    else -> null
                }
            }
            return when {
                settings.globalForcesCutoff && crosses(before.currentGlobalTimeMs, settings.useGlobalClock) -> "SESSION"
                settings.gameForcesCutoff && crosses(before.currentGameTimeMs, settings.useGameClock) -> "GAME"
                (settings.roundForcesCutoff || loops) && crosses(before.currentRoundTimeMs, settings.useRoundClock) -> "ROUND"
                settings.turnForcesCutoff && crosses(before.currentTurnTimeMs, settings.useTurnClock) -> "TURN"
                settings.phaseForcesCutoff && before.currentPhaseIndex < omniCurrentTurnPhases(settings, before).size - 1 &&
                    crosses(before.currentPhaseTimeMs, settings.usePhaseClock) -> "PHASE"
                else -> null
            }
        }

        private fun owedSounds(before: OmniState, delta: Long): List<OmniSound> {
            if (before.isLaunching && before.launchTimeRemainingMs > delta) return emptyList()
            fun crosses(clock: Long, runs: Boolean) = runs && clock > 0 && clock <= delta
            val paused = before.isInTransition
            val owed = ArrayList<OmniSound>()
            if (settings.soundGameEnd && crosses(before.currentGlobalTimeMs, settings.useGlobalClock && (!paused || settings.pauseDeductsFromGlobal))) owed += OmniSound.GAME_END
            if (settings.soundGameEnd && crosses(before.currentGameTimeMs, settings.useGameClock && (!paused || settings.pauseDeductsFromGame))) owed += OmniSound.GAME_END
            if (settings.soundRoundEnd && crosses(before.currentRoundTimeMs, settings.useRoundClock && (!paused || settings.pauseDeductsFromRound))) owed += OmniSound.ROUND_END
            // One beep when a turn and its phase end together.
            if (!paused && settings.soundTurnEnd && (crosses(before.currentTurnTimeMs, settings.useTurnClock) || crosses(before.currentPhaseTimeMs, settings.usePhaseClock))) owed += OmniSound.TURN_END
            return owed
        }

        fun tick(delta: Long) {
            if (!state.isRunning) return
            val before = state
            note("tick $delta")
            val tick = computeOmniTick(state, delta, settings)
            state = tick.state
            if (!strict) { standing(); return }

            rule("each clock that ends is announced once, and only if its sound is on", tick.sounds.sortedBy { it.ordinal } == owedSounds(before, delta).sortedBy { it.ordinal }) {
                "played ${tick.sounds}, owed ${owedSounds(before, delta)}, from $before"
            }
            val owed = owedCutoff(before, delta)
            val here = before.place
            val there = state.place
            when (owed) {
                null -> rule("time alone never moves the session on without a cutoff", there == here && state.over == before.over) { "from $here to $there, from $before" }
                "SESSION" -> rule("the session clock's cutoff ends the session", state.over) { "from $before" }
                "GAME" -> rule("a game clock's cutoff ends its game", state.over || there.game == here.game + 1) { "from $here to $there" }
                "ROUND" -> rule("a round clock's cutoff, or a looping round's clock, ends its round", state.over || there.game > here.game || (there.game == here.game && there.round == here.round + 1)) { "from $here to $there" }
                "TURN" -> rule("a turn clock's cutoff ends its turn", state.over || !there.sameTurnAs(here)) { "from $here to $there" }
                "PHASE" -> rule("a phase clock's cutoff moves to the next phase of the same turn", there.sameTurnAs(here) && there.phase == here.phase + 1) { "from $here to $there" }
            }
            if (owed == null) {
                val launching = before.isLaunching && before.launchTimeRemainingMs > delta
                val paused = before.isInTransition
                fun after(clock: Long, runs: Boolean) = if (runs && !launching) (clock - delta).coerceAtLeast(0) else clock
                rule("each clock in use loses exactly the time that passed, and no other moves",
                    state.currentGlobalTimeMs == after(before.currentGlobalTimeMs, settings.useGlobalClock && (!paused || settings.pauseDeductsFromGlobal)) &&
                        state.currentGameTimeMs == after(before.currentGameTimeMs, settings.useGameClock && (!paused || settings.pauseDeductsFromGame)) &&
                        state.currentRoundTimeMs == after(before.currentRoundTimeMs, settings.useRoundClock && (!paused || settings.pauseDeductsFromRound)) &&
                        state.currentTurnTimeMs == after(before.currentTurnTimeMs, settings.useTurnClock && !paused) &&
                        state.currentPhaseTimeMs == after(before.currentPhaseTimeMs, settings.usePhaseClock && !paused)) { "from $before" }
                rule("the banks do not change while a turn is played", state.playerTimeBanks == before.playerTimeBanks)
                if (paused && !before.over && !launching) {
                    val ends = settings.transitionType == TransitionType.AUTOMATIC && before.transitionTimeRemainingMs <= delta
                    rule("an automatic pause ends when its time is up, and a Ready pause waits", state.isInTransition == !ends) { "from $before" }
                }
            } else moved(before, wasPaused = before.isInTransition)
            standing()
        }

        /** What must hold of a step that changed the turn, whoever caused it. */
        private fun moved(before: OmniState, wasPaused: Boolean) {
            if (!strict) return
            val here = before.place
            val there = state.place
            if (!state.over && there.sameTurnAs(here)) {
                rule("a phase ending leaves the turn clock and the banks alone", state.playerTimeBanks == before.playerTimeBanks && state.currentTurnTimeMs <= before.currentTurnTimeMs)
                return
            }
            // A turn interrupted during the pause before it was never played: only what its player
            // had drawn from the bank can go back.
            val leftover = if (wasPaused) (before.currentTurnTimeMs - freshTurnMs(before)).coerceAtLeast(0) else before.currentTurnTimeMs
            if (state.over) {
                rule("no banked time appears from nowhere", state.bankTotal <= before.bankTotal + leftover) { "banks ${before.playerTimeBanks} then ${state.playerTimeBanks}, leftover $leftover" }
                return
            }
            turnsPlayed += there to state.currentPlayerIndex
            val fresh = freshTurnMs(state)
            rule("a turn starts with at least its own time", state.currentTurnTimeMs >= fresh) { "starts with ${state.currentTurnTimeMs} of $fresh" }
            val drawn = state.currentTurnTimeMs - fresh
            rule("no banked time appears from nowhere", state.bankTotal + drawn <= before.bankTotal + leftover) {
                "banks ${before.playerTimeBanks} then ${state.playerTimeBanks}, drew $drawn, leftover $leftover"
            }
            if (settings.timeBankMode == TimeBankMode.NONE) rule("with no bank a turn starts with exactly its own time", drawn == 0L)
            val (label, pauseMs) = when {
                there.game != here.game -> "GAME" to settings.interGamePauseMs
                there.round != here.round -> "ROUND" to settings.interRoundPauseMs
                else -> "TURN" to settings.interTurnPauseMs
            }
            rule("a pause stands between turns, rounds and games only where one is set, and is the one set",
                state.transitionLabel == label && state.isInTransition == (pauseMs > 0) && state.transitionTimeRemainingMs == pauseMs) {
                "expected $label for $pauseMs, got ${state.transitionLabel} for ${state.transitionTimeRemainingMs}, pausing ${state.isInTransition}"
            }
            val round = omniCurrentRound(settings, state)
            rule("a new turn starts at its first phase, with that phase's time",
                state.currentPhaseIndex == 0 && state.currentPhaseTimeMs == getOmniDuration(settings, "PHASE", gameIdx = there.game, roundIdx = there.round, playerIdx = there.turn, phaseIdx = 0))
            if (there.game != here.game || there.round != here.round) {
                rule("a new round starts with its own round clock", state.currentRoundTimeMs == round.durationMs)
            }
            if (there.game != here.game) rule("a new game starts with its own game clock", state.currentGameTimeMs == settings.games[there.game].durationMs)
        }

        /** What must hold of the session at any moment. */
        private fun standing() {
            val s = state
            rule("no clock goes below zero", listOf(s.currentGlobalTimeMs, s.currentGameTimeMs, s.currentRoundTimeMs, s.currentTurnTimeMs, s.currentPhaseTimeMs, s.transitionTimeRemainingMs, s.launchTimeRemainingMs).all { it >= 0 })
            rule("no bank goes below zero", s.playerTimeBanks.values.all { it >= 0 })
            rule("the player on turn is one of the players", s.currentPlayerIndex in 0 until settings.numberOfPlayers) { "player ${s.currentPlayerIndex} of ${settings.numberOfPlayers}" }
            if (!strict) return
            rule("the game is one of the session's", s.currentGameIndex in settings.games.indices)
            val game = settings.games[s.currentGameIndex]
            rule("the round is one of the game's", s.currentRoundIndex in game.rounds.indices)
            val turns = omniTurnsOf(game.rounds[s.currentRoundIndex], settings.numberOfPlayers)
            rule("the turn is one of the round's", if (turns.isEmpty()) s.turnCounterInRound == 0 else s.turnCounterInRound in turns.indices) { "turn ${s.turnCounterInRound} of ${turns.size}" }
            val phases = turns.getOrNull(s.turnCounterInRound)?.phases.orEmpty()
            rule("the phase is one of the turn's", if (settings.usePhaseClock) s.currentPhaseIndex in 0..(phases.size - 1).coerceAtLeast(0) else s.currentPhaseIndex == 0) { "phase ${s.currentPhaseIndex} of ${phases.size}" }
            rule("the player on turn is the one the order gives this turn", s.currentPlayerIndex == omniPlayerForTurn(s.roundPlayerOrder, s.turnCounterInRound, s.currentRoundIndex, settings)) {
                "player ${s.currentPlayerIndex}, order ${s.roundPlayerOrder}"
            }
            val bankKeysFit = when (settings.timeBankMode) {
                TimeBankMode.NONE -> s.playerTimeBanks.isEmpty()
                TimeBankMode.GLOBAL_RESERVE -> s.playerTimeBanks.keys.all { it == OMNI_GLOBAL_TIME_BANK_KEY }
                TimeBankMode.ACCUMULATIVE -> s.playerTimeBanks.keys.all { it in 0 until settings.numberOfPlayers }
            }
            rule("banks are kept where the bank mode keeps them", bankKeysFit) { "banks ${s.playerTimeBanks}" }
            if (s.over) rule("a finished session has stopped", !s.isRunning)
        }
    }

    /** Plays one session; a broken rule or a crash is recorded, and the next session goes ahead. */
    private fun play(seed: Int, findings: Findings, edge: Boolean = false, strict: Boolean = true, shape: (OmniSettings) -> OmniSettings = { it }, script: Session.() -> Unit) {
        val rng = Random(seed)
        val session = Session(shape(rng.settings(edge)), seed, rng, findings, strict)
        findings.sessions++
        try {
            session.start()
            session.script()
            if (session.state.over) findings.finished++
        } catch (_: Stop) {
        } catch (crash: Throwable) {
            findings.byRule.getOrPut("the engine never crashes") { ArrayList() }.add("seed $seed: $crash\n   settings: ${session.settings}")
        }
    }

    /** A round that loops needs its clock to end; without time passing it would never stop. */
    private fun OmniSettings.withoutEndlessLoops() = copy(
        pauseDeductsFromGlobal = false, pauseDeductsFromGame = false, pauseDeductsFromRound = false,
        games = games.map { game -> game.copy(rounds = game.rounds.map { if (useRoundClock && it.durationMs > 0) it.copy(roundEndBehavior = RoundEndBehavior.ADVANCE) else it }) },
    )

    // ---------------------------------------------------------------------------------------
    // The sessions
    // ---------------------------------------------------------------------------------------

    @Test
    fun `players who only press Next play every turn of every round of every game once`() {
        val findings = Findings()
        repeat(4_000) { seed ->
            play(seed, findings, shape = { it.withoutEndlessLoops() }) {
                var guard = 0
                while (!state.over && guard++ < 100_000) when {
                    canNext -> next()
                    canReady -> ready()
                    // Nothing to press during the launch or an automatic pause: time has to pass.
                    else -> tick(100)
                }
                rule("the session ends", state.over)

                val players = settings.numberOfPlayers
                var owedNexts = 0
                val seats = turnsPlayed.groupBy({ Triple(it.first.game, it.first.round, it.first.pass) }, { it.second })
                settings.games.forEachIndexed { g, game ->
                    game.rounds.forEachIndexed { r, round ->
                        val turns = omniTurnsOf(round, players)
                        owedNexts += turns.sumOf { if (settings.usePhaseClock) it.phases.size.coerceAtLeast(1) else 1 }
                        val played = seats[Triple(g, r, 0)].orEmpty()
                        rule("every turn of a round is played", played.size == turns.size) { "game $g round $r: ${played.size} of ${turns.size} turns, by $played" }
                        // Drawn afresh every turn, RANDOM promises nobody an equal share.
                        if (settings.playerOrderType != PlayerOrderType.RANDOM || !settings.randomEachTurn) {
                            rule("every player plays as many turns of a round as any other", (0 until players).all { p -> played.count { it == p } == turns.size / players }) {
                                "game $g round $r, ${settings.playerOrderType}: turns went to $played"
                            }
                        }
                    }
                }
                rule("the session takes one Next per phase, or per turn without phases, and no more", nexts == owedNexts) { "$nexts presses for $owedNexts" }
                if (settings.playerOrderType == PlayerOrderType.RANDOM && settings.randomAvoidBackToBack && players > 1) {
                    rule("with back-to-back turns ruled out, nobody plays twice in a row", turnsPlayed.zipWithNext().none { (a, b) -> a.second == b.second }) {
                        "turns went to ${turnsPlayed.map { it.second }}"
                    }
                }
            }
        }
        println("Next only: ${findings.finished} of ${findings.sessions} sessions finished, ${findings.actions} actions")
        findings.assertNone()
    }

    /** Everything a table can do, in any order: time, Next, Ready, Pause, the app closed and reopened. */
    private fun Session.playAnyhow() {
        var guard = 0
        while (!state.over && guard++ < 20_000) {
            val dice = rng.nextInt(100)
            when {
                dice < 20 && canNext -> next()
                dice < 30 && canReady -> ready()
                dice < 37 -> if (state.isRunning) pause() else resume()
                dice < 43 -> closeAndReopen(adopt = rng.nextBoolean())
                !state.isRunning && dice < 60 -> resume()
                else -> tick(listOf(10L, 10L, 100L, 100L, 100L, 250L, 1_000L, 3_000L, 12_000L)[rng.nextInt(9)])
            }
        }
        rule("the session ends", state.over) { "still at ${state.place} after 20 000 actions" }
    }

    @Test
    fun `a session played anyhow keeps its clocks, banks, players and places in order`() {
        val findings = Findings()
        repeat(6_000) { seed -> play(100_000 + seed, findings) { playAnyhow() } }
        println("Anyhow: ${findings.finished} of ${findings.sessions} sessions finished, ${findings.actions} actions")
        findings.assertNone()
    }

    @Test
    fun `settings at their limits -- zero durations, a round with no turn -- still make a session`() {
        val findings = Findings()
        repeat(6_000) { seed -> play(200_000 + seed, findings, edge = true) { playAnyhow() } }
        println("Limits: ${findings.finished} of ${findings.sessions} sessions finished, ${findings.actions} actions")
        findings.assertNone()
    }

    /** Whether cutoffs alone carry [this] to its end: one level of clocks all running, all set, all cutting off. */
    private fun OmniSettings.runsItself(): Boolean {
        val rounds = games.flatMap { it.rounds }
        return (globalForcesCutoff && useGlobalClock && globalDurationMs > 0) ||
            (gameForcesCutoff && useGameClock && games.all { it.durationMs > 0 }) ||
            (roundForcesCutoff && useRoundClock && rounds.all { it.durationMs > 0 }) ||
            (turnForcesCutoff && useTurnClock && rounds.all { round -> round.turnDurationMs > 0 && omniTurnsOf(round, numberOfPlayers).all { it.durationMs > 0 } })
    }

    @Test
    fun `a session whose clocks cut off runs to its end with nobody touching it`() {
        val findings = Findings()
        var selfRunning = 0
        repeat(4_000) { seed ->
            play(300_000 + seed, findings) {
                val runsItself = settings.runsItself()
                if (runsItself) selfRunning++
                var guard = 0
                // Ready is the one thing a table has to do here: a Ready pause waits for it by design.
                while (!state.over && guard++ < (if (runsItself) 300_000 else 2_000)) {
                    if (canReady) ready() else tick(listOf(10L, 100L, 100L, 250L, 1_000L, 2_500L)[rng.nextInt(6)])
                }
                if (runsItself) rule("a session that cuts off at every level ends by itself", state.over) { "still at ${state.place}" }
            }
        }
        println("Hands off: ${findings.finished} of ${findings.sessions} sessions finished ($selfRunning set to run themselves), ${findings.actions} actions")
        findings.assertNone()
    }

    @Test
    fun `a paused session whose settings are changed under it still plays to an end`() {
        val findings = Findings()
        repeat(4_000) { seed ->
            // Not strict: places are read against the settings the session started with, which is
            // the point. What must survive is the session itself -- no crash, no negative clock, a
            // player who exists, and an end.
            play(400_000 + seed, findings, strict = false) {
                repeat(rng.nextInt(1, 60)) {
                    if (state.over) return@repeat
                    when {
                        canNext && rng.nextInt(100) < 40 -> next()
                        canReady -> ready()
                        else -> tick(listOf(100L, 250L, 1_000L)[rng.nextInt(3)])
                    }
                }
                if (state.over) return@play
                pause()
                edit(rng.settings(edge = rng.nextInt(100) < 30))
                resume()
                var guard = 0
                while (!state.over && guard++ < 20_000) when {
                    canNext && rng.nextInt(100) < 30 -> next()
                    canReady -> ready()
                    else -> tick(listOf(100L, 250L, 1_000L, 3_000L)[rng.nextInt(4)])
                }
                rule("the session ends", state.over) {
                    val round = omniCurrentRound(settings, state)
                    "still at ${state.place} after 20 000 actions; round ${round.roundEndBehavior}, its clock ${if (settings.useRoundClock) "on" else "off"} at ${state.currentRoundTimeMs} of ${round.durationMs}"
                }
            }
        }
        println("Edited: ${findings.finished} of ${findings.sessions} sessions finished, ${findings.actions} actions")
        findings.assertNone()
    }
}
