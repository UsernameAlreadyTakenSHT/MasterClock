package com.masterclock.app.logic

import org.junit.Assert.*
import org.junit.Test

/** Covers [computeOmniTick], the part of the Omni engine that lived behind the ViewModel. */
class OmniTickTest {

    private val minute = 60_000L

    private fun settings(
        turnMs: Long = minute,
        turnCutoff: Boolean = false,
        phases: List<OmniPhaseSettings> = emptyList(),
        configure: OmniSettings.() -> OmniSettings = { this },
    ) = OmniSettings(
        numberOfPlayers = 2,
        games = listOf(OmniGameSettings(rounds = listOf(OmniRoundSettings(turnDurationMs = turnMs, fixedTurnPhases = phases)))),
        turnForcesCutoff = turnCutoff,
    ).configure()

    private fun running(s: OmniSettings) = createInitialOmniState(s).copy(isRunning = true, hasStarted = true)

    @Test
    fun `the launch countdown holds every clock`() {
        val s = settings()
        val launching = running(s).copy(isLaunching = true, launchTimeRemainingMs = 1_000)
        val tick = computeOmniTick(launching, 400, s)
        assertEquals(600, tick.state.launchTimeRemainingMs)
        assertEquals(launching.currentTurnTimeMs, tick.state.currentTurnTimeMs)
    }

    @Test
    fun `the tick that ends the launch counts toward the clocks`() {
        val s = settings()
        val launching = running(s).copy(isLaunching = true, launchTimeRemainingMs = 100)
        val tick = computeOmniTick(launching, 100, s)
        assertFalse(tick.state.isLaunching)
        assertEquals(minute - 100, tick.state.currentTurnTimeMs)
    }

    @Test
    fun `a turn running out sounds once and stays at zero without a cutoff`() {
        val s = settings(turnMs = 1_000)
        val tick = computeOmniTick(running(s), 1_000, s)
        assertEquals(0, tick.state.currentTurnTimeMs)
        assertEquals(listOf(OmniSound.TURN_END), tick.sounds)
        assertEquals(0, tick.state.turnCounterInRound)
        assertTrue(computeOmniTick(tick.state, 100, s).sounds.isEmpty())
    }

    @Test
    fun `a turn cutoff moves on to the next turn`() {
        val s = settings(turnMs = 1_000, turnCutoff = true)
        assertEquals(1, computeOmniTick(running(s), 1_000, s).state.turnCounterInRound)
    }

    @Test
    fun `a clock switched off does not run`() {
        val s = settings { copy(useGameClock = false, gameForcesCutoff = true) }
        val state = running(s)
        assertEquals(state.currentGameTimeMs, computeOmniTick(state, 1_000, s).state.currentGameTimeMs)
    }

    @Test
    fun `an automatic transition ends on its own, a manual one waits`() {
        val auto = settings { copy(transitionType = TransitionType.AUTOMATIC) }
        val waiting = running(auto).copy(isInTransition = true, transitionTimeRemainingMs = 500)
        assertFalse(computeOmniTick(waiting, 500, auto).state.isInTransition)
        val manual = settings { copy(transitionType = TransitionType.MANUAL_READY) }
        assertTrue(computeOmniTick(waiting, 500, manual).state.isInTransition)
    }

    @Test
    fun `the turn clock stands still during a transition`() {
        val s = settings()
        val waiting = running(s).copy(isInTransition = true, transitionTimeRemainingMs = 5_000)
        assertEquals(waiting.currentTurnTimeMs, computeOmniTick(waiting, 1_000, s).state.currentTurnTimeMs)
    }

    @Test
    fun `a turn with no pause has no transition, whatever Ready says`() {
        val s = settings { copy(transitionType = TransitionType.MANUAL_READY) }
        val next = computeOmniAdvance(running(s), s)
        assertEquals(1, next.turnCounterInRound)
        assertFalse(next.isInTransition)
    }

    @Test
    fun `a pause waits for Ready once it has run down`() {
        val s = settings { copy(transitionType = TransitionType.MANUAL_READY, interTurnPauseMs = 5_000) }
        val paused = computeOmniAdvance(running(s), s)
        assertTrue(paused.isInTransition)
        assertTrue(computeOmniTick(paused, 5_000, s).state.isInTransition)
    }

    @Test
    fun `the end of a round or game has no transition without its pause`() {
        val s = settings().copy(games = List(2) { OmniGameSettings(rounds = List(2) { OmniRoundSettings() }) })
        var state = running(s)
        repeat(2) { state = computeOmniAdvance(state, s) }
        assertEquals(1, state.currentRoundIndex)
        assertFalse(state.isInTransition)
        repeat(2) { state = computeOmniAdvance(state, s) }
        assertEquals(1, state.currentGameIndex)
        assertFalse(state.isInTransition)
    }

    @Test
    fun `the session's end still shows, with no pause at all`() {
        val s = settings()
        val over = computeOmniAdvance(computeOmniAdvance(running(s), s), s)
        assertTrue(over.isInTransition)
        assertEquals("SESSION", over.transitionLabel)
    }

    @Test
    fun `a player's own bank from a fixed round pays out on their next turn`() {
        val s = settings(turnMs = 10 * minute).copy(
            timeBankMode = TimeBankMode.ACCUMULATIVE,
            timeBankScope = TimeBankScope.ROUND_TO_ROUND,
            games = listOf(OmniGameSettings(rounds = List(2) { OmniRoundSettings(turnDurationMs = 10 * minute) })),
        )
        var state = running(s).copy(currentTurnTimeMs = 7 * minute) // Player 1 ends with 7:00 left.
        state = computeOmniAdvance(state, s)
        state = computeOmniAdvance(state.copy(currentTurnTimeMs = 5 * minute), s) // Player 2, 5:00 left.
        assertEquals(1, state.currentRoundIndex)
        assertEquals(0, state.currentPlayerIndex)
        assertEquals(17 * minute, state.currentTurnTimeMs)
    }

    @Test
    fun `the shared reserve still ends with the round in the round scope`() {
        val s = settings(turnMs = 10 * minute).copy(
            timeBankMode = TimeBankMode.GLOBAL_RESERVE,
            timeBankScope = TimeBankScope.ROUND_TO_ROUND,
            games = listOf(OmniGameSettings(rounds = List(2) { OmniRoundSettings(turnDurationMs = 10 * minute) })),
        )
        var state = running(s).copy(currentTurnTimeMs = 7 * minute)
        state = computeOmniAdvance(state, s)
        state = computeOmniAdvance(state.copy(currentTurnTimeMs = 5 * minute), s)
        assertEquals(10 * minute, state.currentTurnTimeMs)
    }

    @Test
    fun `the turn number goes on across the passes of a loop round`() {
        val s = settings().copy(games = listOf(OmniGameSettings(rounds = listOf(OmniRoundSettings(roundEndBehavior = RoundEndBehavior.LOOP)))))
        var state = running(s)
        assertEquals(1, omniTurnNumber(s, state))
        repeat(2) { state = computeOmniAdvance(state, s) }
        assertEquals(2, omniTurnNumber(s, state))
        repeat(2) { state = computeOmniAdvance(state, s) }
        assertEquals(3, omniTurnNumber(s, state))
    }

    @Test
    fun `a turn and its phase ending together beep once`() {
        val s = settings(turnMs = 1_000) { copy(usePhaseClock = true) }
        assertEquals(listOf(OmniSound.TURN_END), computeOmniTick(running(s), 1_000, s).sounds)
    }

    private val threePhases = List(3) { OmniPhaseSettings(durationMs = 1_000) }

    @Test
    fun `the phase cutoff moves on through the phases`() {
        val s = settings(phases = threePhases) { copy(usePhaseClock = true, phaseForcesCutoff = true) }
        assertEquals(1, computeOmniTick(running(s), 1_000, s).state.currentPhaseIndex)
    }

    @Test
    fun `the phase cutoff does not end the turn on the last phase`() {
        val s = settings(phases = threePhases) { copy(usePhaseClock = true, phaseForcesCutoff = true) }
        val last = running(s).copy(currentPhaseIndex = 2, currentPhaseTimeMs = 1_000)
        val after = computeOmniTick(last, 1_000, s).state
        assertEquals(0, after.turnCounterInRound)
        assertEquals(2, after.currentPhaseIndex)
    }

    @Test
    fun `a stale index reads as the last entry`() {
        val s = settings().copy(games = listOf(OmniGameSettings(name = "One"), OmniGameSettings(name = "Two")))
        assertEquals("Two", omniCurrentGame(s, OmniState(currentGameIndex = 7)).name)
    }

    @Test
    fun `the phase cutoff leaves a turn with no phases alone`() {
        val s = settings(turnMs = 1_000) { copy(usePhaseClock = true, phaseForcesCutoff = true) }
        assertEquals(0, computeOmniTick(running(s), 1_000, s).state.turnCounterInRound)
    }
}
