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

    // --- Time banking: leftover turn time is banked only when the turn actually ends ---

    @Test
    fun `phase advance does not bank the turn's remaining time`() {
        val turn = OmniTurnSettings(durationMs = 60_000, phases = listOf(
            OmniPhaseSettings(durationMs = 20_000), OmniPhaseSettings(durationMs = 10_000)
        ))
        val round = OmniRoundSettings(customTurns = listOf(turn, turn), turnLogic = RoundTurnLogic.SEQUENCE)
        val settings = OmniSettings(
            usePhaseClock = true, timeBankMode = TimeBankMode.ACCUMULATIVE, timeBankScope = TimeBankScope.SESSION_WIDE,
            games = listOf(OmniGameSettings(rounds = listOf(round))), numberOfPlayers = 2
        )
        val state = OmniState(currentPhaseIndex = 0, turnCounterInRound = 0, currentTurnTimeMs = 30_000)

        val next = computeOmniAdvance(state, settings)
        assertEquals(1, next.currentPhaseIndex)
        // The turn is still in progress with its time on the clock -- nothing may be banked yet.
        assertTrue(next.playerTimeBanks.isEmpty())
        assertEquals(30_000, next.currentTurnTimeMs)
    }

    @Test
    fun `turn advance banks the leftover exactly once and draws the next player's bank into their turn`() {
        val turnA = OmniTurnSettings(durationMs = 60_000, phases = listOf(OmniPhaseSettings(durationMs = 20_000)))
        val turnB = OmniTurnSettings(durationMs = 90_000, phases = listOf(OmniPhaseSettings(durationMs = 45_000)))
        val round = OmniRoundSettings(customTurns = listOf(turnA, turnB), turnLogic = RoundTurnLogic.SEQUENCE)
        val settings = OmniSettings(
            usePhaseClock = true, timeBankMode = TimeBankMode.ACCUMULATIVE, timeBankScope = TimeBankScope.SESSION_WIDE,
            games = listOf(OmniGameSettings(rounds = listOf(round))), numberOfPlayers = 2
        )
        val state = OmniState(
            currentPhaseIndex = 0, turnCounterInRound = 0, currentPlayerIndex = 0,
            currentTurnTimeMs = 30_000, playerTimeBanks = mapOf(1 to 5_000L)
        )

        val next = computeOmniAdvance(state, settings)
        assertEquals("TURN", next.transitionLabel)
        assertEquals(30_000L, next.playerTimeBanks[0])
        // Player 1's pre-banked 5s is drawn into their new turn and cleared from the bank.
        assertEquals(90_000 + 5_000, next.currentTurnTimeMs)
        assertEquals(0L, next.playerTimeBanks[1])
    }

    // --- Phase clock reinitialization on every advance level ---

    @Test
    fun `turn advance starts the new turn on its first phase's own duration`() {
        val turnA = OmniTurnSettings(durationMs = 60_000, phases = listOf(OmniPhaseSettings(durationMs = 20_000)))
        val turnB = OmniTurnSettings(durationMs = 90_000, phases = listOf(OmniPhaseSettings(durationMs = 45_000)))
        val round = OmniRoundSettings(customTurns = listOf(turnA, turnB), turnLogic = RoundTurnLogic.SEQUENCE)
        val settings = OmniSettings(usePhaseClock = true, games = listOf(OmniGameSettings(rounds = listOf(round))), numberOfPlayers = 2)
        val state = OmniState(currentPhaseIndex = 0, turnCounterInRound = 0, currentPhaseTimeMs = 0)

        val next = computeOmniAdvance(state, settings)
        assertEquals(45_000, next.currentPhaseTimeMs)
    }

    @Test
    fun `round advance starts the new round on its first turn's first phase duration`() {
        val round1 = OmniRoundSettings(customTurns = listOf(OmniTurnSettings(durationMs = 60_000, phases = listOf(OmniPhaseSettings(durationMs = 20_000)))), turnLogic = RoundTurnLogic.SEQUENCE)
        val round2 = OmniRoundSettings(customTurns = listOf(OmniTurnSettings(durationMs = 120_000, phases = listOf(OmniPhaseSettings(durationMs = 75_000)))), turnLogic = RoundTurnLogic.SEQUENCE)
        val settings = OmniSettings(usePhaseClock = true, games = listOf(OmniGameSettings(rounds = listOf(round1, round2))), numberOfPlayers = 2)
        val state = OmniState(currentPhaseIndex = 0, turnCounterInRound = 0, currentPhaseTimeMs = 0)

        val next = computeOmniAdvance(state, settings)
        assertEquals("ROUND", next.transitionLabel)
        assertEquals(120_000, next.currentTurnTimeMs)
        assertEquals(75_000, next.currentPhaseTimeMs)
    }

    @Test
    fun `game advance starts the new game on its first phase duration and its own game clock`() {
        val game1 = OmniGameSettings(durationMs = 1_000_000, rounds = listOf(
            OmniRoundSettings(customTurns = listOf(OmniTurnSettings(durationMs = 60_000, phases = listOf(OmniPhaseSettings(durationMs = 20_000)))), turnLogic = RoundTurnLogic.SEQUENCE)
        ))
        val game2 = OmniGameSettings(durationMs = 2_000_000, rounds = listOf(
            OmniRoundSettings(customTurns = listOf(OmniTurnSettings(durationMs = 150_000, phases = listOf(OmniPhaseSettings(durationMs = 99_000)))), turnLogic = RoundTurnLogic.SEQUENCE)
        ))
        val settings = OmniSettings(usePhaseClock = true, games = listOf(game1, game2), numberOfPlayers = 2)
        val state = OmniState(currentPhaseIndex = 0, turnCounterInRound = 0, currentPhaseTimeMs = 0)

        val next = computeOmniAdvance(state, settings)
        assertEquals("GAME", next.transitionLabel)
        assertEquals(99_000, next.currentPhaseTimeMs)
        assertEquals(2_000_000, next.currentGameTimeMs)
    }

    // --- FIXED rounds are exactly numberOfPlayers turns, whatever customTurns holds ---

    @Test
    fun `FIXED round ignores leftover SEQUENCE customTurns when counting its turns`() {
        val stale = List(5) { OmniTurnSettings(durationMs = 60_000) }
        val round1 = OmniRoundSettings(turnLogic = RoundTurnLogic.FIXED, customTurns = stale, turnDurationMs = 60_000)
        val round2 = OmniRoundSettings(turnLogic = RoundTurnLogic.FIXED, turnDurationMs = 60_000)
        val settings = OmniSettings(games = listOf(OmniGameSettings(rounds = listOf(round1, round2))), numberOfPlayers = 2)
        // Second (last) turn of a 2-player FIXED round: advancing must end the round, not run
        // a phantom 3rd..5th turn inherited from the stale customTurns list.
        val state = OmniState(turnCounterInRound = 1, currentPlayerIndex = 1)

        val next = computeOmniAdvance(state, settings)
        assertEquals("ROUND", next.transitionLabel)
        assertEquals(1, next.currentRoundIndex)
    }

    // --- Per-phase Auto Advance wizard toggle resolution ---

    @Test
    fun `omniPhaseAutoAdvances reads the configured phase's toggle and defaults to true`() {
        val manualPhase = OmniPhaseSettings(durationMs = 20_000, autoAdvance = false)
        val autoPhase = OmniPhaseSettings(durationMs = 20_000, autoAdvance = true)
        val turn = OmniTurnSettings(phases = listOf(manualPhase, autoPhase))
        val round = OmniRoundSettings(customTurns = listOf(turn), turnLogic = RoundTurnLogic.SEQUENCE)
        val settings = OmniSettings(usePhaseClock = true, games = listOf(OmniGameSettings(rounds = listOf(round))), numberOfPlayers = 1)

        assertFalse(omniPhaseAutoAdvances(settings, OmniState(currentPhaseIndex = 0, turnCounterInRound = 0)))
        assertTrue(omniPhaseAutoAdvances(settings, OmniState(currentPhaseIndex = 1, turnCounterInRound = 0)))

        // FIXED round with no configured phases: matches OmniPhaseSettings' default of true.
        val fixedSettings = OmniSettings(usePhaseClock = true, games = listOf(OmniGameSettings(rounds = listOf(OmniRoundSettings()))), numberOfPlayers = 2)
        assertTrue(omniPhaseAutoAdvances(fixedSettings, OmniState(currentPhaseIndex = 0, turnCounterInRound = 0)))
    }
}
