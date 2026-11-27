package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a pause costs. "Pause deducts from round" and "from game" ran the round and game clocks through
 * every pause, but by the pause between rounds the next round is already loaded: the round still to
 * come paid for it, and could end before anyone played in it. A pause now costs only a clock that is
 * the same on both sides of it.
 */
class OmniPauseClocksTest {

    private val everything = OmniSettings(
        numberOfPlayers = 2,
        useGlobalClock = true, useGameClock = true, useRoundClock = true,
        pauseDeductsFromGlobal = true, pauseDeductsFromGame = true, pauseDeductsFromRound = true,
    )

    @Test
    fun `a pause between turns runs the round, the game and the session`() {
        val p = omniPauseClocks(everything, "TURN")
        assertTrue(p.round); assertTrue(p.game); assertTrue(p.global)
    }

    @Test
    fun `a pause between rounds runs the game and the session, not the round to come`() {
        val p = omniPauseClocks(everything, "ROUND")
        assertFalse(p.round); assertTrue(p.game); assertTrue(p.global)
    }

    @Test
    fun `a pause between games runs only the session`() {
        val p = omniPauseClocks(everything, "GAME")
        assertFalse(p.round); assertFalse(p.game); assertTrue(p.global)
    }

    @Test
    fun `a switch left off, or a clock not in use, runs through no pause`() {
        val off = omniPauseClocks(everything.copy(pauseDeductsFromRound = false, useGameClock = false), "TURN")
        assertFalse(off.round); assertFalse(off.game)
    }

    @Test
    fun `a pause longer than the next round no longer ends it before it is played`() {
        // A minute between rounds; round two lasts thirty seconds and loops, which ends it with its clock.
        val short = OmniRoundSettings(durationMs = 30_000, roundEndBehavior = RoundEndBehavior.LOOP)
        val s = everything.copy(
            interRoundPauseMs = 60_000,
            games = listOf(OmniGameSettings(durationMs = 3_600_000, rounds = listOf(OmniRoundSettings(), short))),
        )
        var state = createInitialOmniState(s).copy(isRunning = true, hasStarted = true)
        state = computeOmniAdvance(state, s)
        state = computeOmniAdvance(state, s)
        assertEquals("ROUND", state.transitionLabel)
        assertEquals(1, state.currentRoundIndex)

        repeat(600) { state = computeOmniTick(state, 100, s).state }

        // The pause is over, and round two is there with all its thirty seconds.
        assertFalse(state.isInTransition)
        assertEquals(1, state.currentRoundIndex)
        assertEquals(30_000, state.currentRoundTimeMs)
        // The game paid for the minute; it is the same game on both sides.
        assertEquals(3_600_000 - 60_000L, state.currentGameTimeMs)
    }

    @Test
    fun `a pause between turns still costs the round it is in`() {
        val s = everything.copy(interTurnPauseMs = 10_000)
        var state = createInitialOmniState(s).copy(isRunning = true, hasStarted = true)
        state = computeOmniAdvance(state, s)
        assertEquals("TURN", state.transitionLabel)
        val round = state.currentRoundTimeMs
        repeat(50) { state = computeOmniTick(state, 100, s).state }
        assertEquals(round - 5_000, state.currentRoundTimeMs)
    }

    @Test
    fun `the wake-up during a pause between rounds does not wait on the round to come`() {
        val paused = OmniState(isRunning = true, hasStarted = true, isInTransition = true, transitionLabel = "ROUND",
            transitionTimeRemainingMs = 25_000, currentRoundTimeMs = 20_000, currentGameTimeMs = 900_000, currentGlobalTimeMs = 9_000_000)
        assertEquals(25_000L, omniNextDueMs(paused, everything))
    }
}
