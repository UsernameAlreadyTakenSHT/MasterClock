package com.masterclock.app.logic

import org.junit.Test
import org.junit.Assert.*

/** Covers [nextPhaseState], where a PHASES game that runs out of phases finishes rather than flags. */
class PhasesEndTest {

    private val phases = listOf(GamePhase("Opening", 5_000), GamePhase("Final", 5_000))

    private fun stateAt(phaseIndex: Int) = ChessClockState(
        players = listOf(PlayerState(timeRemainingMs = 0, currentPhaseIndex = phaseIndex), PlayerState(timeRemainingMs = 0)),
        activePlayer = 1,
    )

    @Test
    fun `running out of phases finishes the game without a flag`() {
        val end = nextPhaseState(stateAt(1), 1, phases, loop = false)

        assertTrue(end.phasesFinished)
        // Still out of time, so everything that stops a finished game stops this one.
        assertTrue(end.players[0].isOutOfTime)
        assertEquals(ClockAnnouncement.Finished, clockAnnouncement(end))
    }

    @Test
    fun `moving to the next phase is not a finish`() {
        val next = nextPhaseState(stateAt(0), 1, phases, loop = false)

        assertFalse(next.phasesFinished)
        assertEquals(1, next.players[0].currentPhaseIndex)
        assertEquals(5_000, next.globalTimeMs)
    }

    @Test
    fun `a looping game goes back to the first phase and clears a finish`() {
        val looped = nextPhaseState(stateAt(1).copy(phasesFinished = true), 1, phases, loop = true)

        assertFalse(looped.phasesFinished)
        assertEquals(0, looped.players[0].currentPhaseIndex)
        assertFalse(looped.players[0].isOutOfTime)
    }

    @Test
    fun `no phases at all is an ended game, not a finished one`() {
        val end = nextPhaseState(stateAt(0), 1, emptyList(), loop = true)

        assertTrue(end.players[0].isOutOfTime)
        assertFalse(end.phasesFinished)
        assertEquals(ClockAnnouncement.OutOfTime(1), clockAnnouncement(end))
    }

    @Test
    fun `phases that all last nothing finish rather than loop for ever`() {
        val empty = listOf(GamePhase("A", 0), GamePhase("B", 0))
        val end = nextPhaseState(stateAt(1), 1, empty, loop = true)
        assertTrue(end.phasesFinished)
    }

    @Test
    fun `moving on ends the wait for a tap`() {
        val waiting = stateAt(0).let { it.copy(players = it.players.toMutableList().apply { this[0] = this[0].copy(isPhaseAwaitingTap = true) }) }
        assertFalse(nextPhaseState(waiting, 1, phases, loop = false).players[0].isPhaseAwaitingTap)
        // Saved with the clock, so a resumed game still waits instead of reading as flagged.
        assertTrue(waiting.toProxy().toState().players[0].isPhaseAwaitingTap)
    }

    @Test
    fun `what a tick ran past a phase's end comes off the next`() {
        val running = stateAt(0).copy(isPaused = false)
        val next = nextPhaseState(running, 1, phases, loop = false).carryPhaseOvershoot(60)
        assertEquals(4_940, next.globalTimeMs)
        val pause = running.let { it.copy(players = it.players.toMutableList().apply { this[0] = this[0].copy(isInInterPhasePause = true, pauseTimeRemainingMs = 3_000) }) }
        assertEquals(2_940, pause.carryPhaseOvershoot(60).players[0].pauseTimeRemainingMs)
        // A finished game has nothing to take it from.
        val end = nextPhaseState(stateAt(1), 1, phases, loop = false)
        assertSame(end, end.carryPhaseOvershoot(60))
    }

    @Test
    fun `the table's clock starts on the first phase`() {
        assertEquals(5_000, initialGlobalTimeMs(PlayerSettings(mode = TimerMode.PHASES, phases = phases)))
    }

    @Test
    fun `PHASES turns per-player settings off`() {
        val p1 = PlayerSettings(mode = TimerMode.PHASES, phases = phases)
        val s = ChessClockSettings(differentSettingsPerPlayer = true, p1Custom = p1, p2Custom = PlayerSettings(mode = TimerMode.PHASES)).wholeTableSettings()
        assertFalse(s.differentSettingsPerPlayer)
        assertEquals(p1, s.main)
    }

    @Test
    fun `a finished game survives being saved and restored`() {
        val end = nextPhaseState(stateAt(1), 1, phases, loop = false)

        assertTrue(end.toProxy().toState().phasesFinished)
    }
}
