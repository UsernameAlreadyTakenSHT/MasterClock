package com.masterclock.app.logic

import org.junit.Test
import org.junit.Assert.*

/**
 * Exercises [computeOmniAdvance], the pure state-transition function extracted from
 * OmniTimerViewModel.advanceOmni() / tickOmni() so the *ForcesCutoff settings (see AUDIT.md §7.1)
 * can be tested without an Android runtime or emulator.
 */
class OmniTimerLogicTest {

    @Test
    fun `no forceLevel advances by one phase when phases remain in the current turn`() {
        val phase1 = OmniPhaseSettings(name = "Think", durationMs = 30_000)
        val phase2 = OmniPhaseSettings(name = "Move", durationMs = 10_000)
        val turn = OmniTurnSettings(durationMs = 60_000, phases = listOf(phase1, phase2))
        val round = OmniRoundSettings(customTurns = listOf(turn, turn), turnLogic = RoundTurnLogic.SEQUENCE)
        val game = OmniGameSettings(rounds = listOf(round))
        val settings = OmniSettings(usePhaseClock = true, games = listOf(game), numberOfPlayers = 2)
        val state = OmniState(currentPhaseIndex = 0, turnCounterInRound = 0)

        val next = computeOmniAdvance(state, settings)
        assertEquals(1, next.currentPhaseIndex)
        assertEquals(0, next.turnCounterInRound)
        assertFalse(next.isInTransition)
    }

    @Test
    fun `TURN forceLevel skips remaining phases and advances to the next turn`() {
        val phase1 = OmniPhaseSettings(name = "Think", durationMs = 30_000)
        val phase2 = OmniPhaseSettings(name = "Move", durationMs = 10_000)
        val turn = OmniTurnSettings(durationMs = 60_000, phases = listOf(phase1, phase2))
        val round = OmniRoundSettings(customTurns = listOf(turn, turn), turnLogic = RoundTurnLogic.SEQUENCE)
        val game = OmniGameSettings(rounds = listOf(round))
        val settings = OmniSettings(usePhaseClock = true, games = listOf(game), numberOfPlayers = 2)
        val state = OmniState(currentPhaseIndex = 0, turnCounterInRound = 0)

        val next = computeOmniAdvance(state, settings, forceLevel = "TURN")
        assertTrue(next.isInTransition)
        assertEquals("TURN", next.transitionLabel)
        assertEquals(1, next.turnCounterInRound)
        assertEquals(0, next.currentPhaseIndex)
    }

    @Test
    fun `ROUND forceLevel skips remaining turns and advances to the next round`() {
        val round1 = OmniRoundSettings(name = "Round 1", turnDurationMs = 60_000)
        val round2 = OmniRoundSettings(name = "Round 2", turnDurationMs = 90_000)
        val game = OmniGameSettings(rounds = listOf(round1, round2))
        val settings = OmniSettings(games = listOf(game), numberOfPlayers = 2)
        val state = OmniState(currentRoundIndex = 0, turnCounterInRound = 0, currentPlayerIndex = 0)

        val next = computeOmniAdvance(state, settings, forceLevel = "ROUND")
        assertTrue(next.isInTransition)
        assertEquals("ROUND", next.transitionLabel)
        assertEquals(1, next.currentRoundIndex)
        assertEquals(0, next.turnCounterInRound)
    }

    @Test
    fun `ROUND forceLevel escapes a LOOP round instead of looping back into it`() {
        // A round cutoff (its own timer expiring) must still move past the round even when it is
        // configured to LOOP -- LOOP means "repeat when the turns run out naturally", not "ignore
        // this round's own timer forever" (which would otherwise re-trigger the same cutoff every
        // tick and never advance).
        val round1 = OmniRoundSettings(roundEndBehavior = RoundEndBehavior.LOOP)
        val round2 = OmniRoundSettings()
        val game = OmniGameSettings(rounds = listOf(round1, round2))
        val settings = OmniSettings(games = listOf(game), numberOfPlayers = 2)
        val state = OmniState(currentRoundIndex = 0, turnCounterInRound = 0)

        val next = computeOmniAdvance(state, settings, forceLevel = "ROUND")
        assertEquals(1, next.currentRoundIndex)
    }

    @Test
    fun `GAME forceLevel skips remaining rounds and advances to the next game`() {
        val round = OmniRoundSettings()
        val game1 = OmniGameSettings(name = "Game 1", rounds = listOf(round, round))
        val game2 = OmniGameSettings(name = "Game 2", rounds = listOf(round))
        val settings = OmniSettings(games = listOf(game1, game2), numberOfPlayers = 2)
        val state = OmniState(currentGameIndex = 0, currentRoundIndex = 0, turnCounterInRound = 0)

        val next = computeOmniAdvance(state, settings, forceLevel = "GAME")
        assertTrue(next.isInTransition)
        assertEquals("GAME", next.transitionLabel)
        assertEquals(1, next.currentGameIndex)
        assertEquals(0, next.currentRoundIndex)
    }

    @Test
    fun `SESSION forceLevel ends the session immediately regardless of remaining games`() {
        val game1 = OmniGameSettings(rounds = listOf(OmniRoundSettings(), OmniRoundSettings()))
        val game2 = OmniGameSettings(rounds = listOf(OmniRoundSettings()))
        val settings = OmniSettings(games = listOf(game1, game2), numberOfPlayers = 2)
        val state = OmniState(currentGameIndex = 0, currentRoundIndex = 0, turnCounterInRound = 0, isRunning = true)

        val next = computeOmniAdvance(state, settings, forceLevel = "SESSION")
        assertFalse(next.isRunning)
        assertTrue(next.isInTransition)
        assertEquals("SESSION", next.transitionLabel)
    }

    @Test
    fun `GAME forceLevel on the last game ends the session instead of erroring`() {
        val game = OmniGameSettings(rounds = listOf(OmniRoundSettings()))
        val settings = OmniSettings(games = listOf(game), numberOfPlayers = 2)
        val state = OmniState(currentGameIndex = 0, currentRoundIndex = 0, turnCounterInRound = 0, isRunning = true)

        val next = computeOmniAdvance(state, settings, forceLevel = "GAME")
        assertFalse(next.isRunning)
        assertEquals("SESSION", next.transitionLabel)
    }
}
