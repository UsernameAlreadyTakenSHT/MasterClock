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
    fun `the phase cutoff leaves a turn with no phases alone`() {
        val s = settings(turnMs = 1_000) { copy(usePhaseClock = true, phaseForcesCutoff = true) }
        assertEquals(0, computeOmniTick(running(s), 1_000, s).state.turnCounterInRound)
    }
}
