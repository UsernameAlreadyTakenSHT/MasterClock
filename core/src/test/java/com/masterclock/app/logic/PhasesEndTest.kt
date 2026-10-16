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
    fun `a finished game survives being saved and restored`() {
        val end = nextPhaseState(stateAt(1), 1, phases, loop = false)

        assertTrue(end.toProxy().toState().phasesFinished)
    }
}
