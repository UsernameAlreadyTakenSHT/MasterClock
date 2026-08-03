package com.masterclock.app.logic

import org.junit.Test
import org.junit.Assert.*
import kotlin.random.Random

/**
 * Exercises [computeOmniAdvance], the pure state-transition function extracted from
 * OmniTimerViewModel.advanceOmni() / tickOmni() so the *ForcesCutoff settings can be tested
 * without an Android runtime or emulator.
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

    // --- RANDOM turn order ---

    private fun randomSettings(
        players: Int,
        eachTurn: Boolean = false,
        avoidBackToBack: Boolean = true,
        autoBalance: Boolean = true,
    ) = OmniSettings(
        numberOfPlayers = players,
        playerOrderType = PlayerOrderType.RANDOM,
        randomEachTurn = eachTurn,
        randomAvoidBackToBack = avoidBackToBack,
        randomAutoBalance = autoBalance,
    )

    @Test
    fun `shuffle mode gives every player exactly one turn per round`() {
        val settings = randomSettings(players = 5)
        repeat(50) { seed ->
            val order = generateOmniRoundOrder(settings, turnCount = 5, previousPlayer = null, random = Random(seed))
            assertEquals(5, order.size)
            assertEquals((0 until 5).toSet(), order.toSet())
        }
    }

    @Test
    fun `shuffle mode fills rounds holding more turns than players with whole fresh passes`() {
        val settings = randomSettings(players = 3)
        val order = generateOmniRoundOrder(settings, turnCount = 7, previousPlayer = null, random = Random(1))
        assertEquals(7, order.size)
        // Two complete passes plus one turn of a third.
        assertEquals((0 until 3).toSet(), order.subList(0, 3).toSet())
        assertEquals((0 until 3).toSet(), order.subList(3, 6).toSet())
    }

    @Test
    fun `avoidBackToBack keeps the round from opening on the player who just played`() {
        val settings = randomSettings(players = 4)
        repeat(100) { seed ->
            val order = generateOmniRoundOrder(settings, turnCount = 4, previousPlayer = 2, random = Random(seed))
            assertNotEquals(2, order.first())
            assertEquals((0 until 4).toSet(), order.toSet())
        }
    }

    @Test
    fun `avoidBackToBack also holds between the passes filling a longer round`() {
        val settings = randomSettings(players = 3)
        repeat(100) { seed ->
            val order = generateOmniRoundOrder(settings, turnCount = 9, previousPlayer = null, random = Random(seed))
            order.zipWithNext { a, b -> assertNotEquals(a, b) }
        }
    }

    @Test
    fun `avoidBackToBack off allows a repeat across the boundary`() {
        val settings = randomSettings(players = 3, avoidBackToBack = false)
        val opensOnPreviousPlayer = (0 until 200).any { seed ->
            generateOmniRoundOrder(settings, turnCount = 3, previousPlayer = 1, random = Random(seed)).first() == 1
        }
        assertTrue(opensOnPreviousPlayer)
    }

    @Test
    fun `a single player is unaffected by avoidBackToBack`() {
        val settings = randomSettings(players = 1)
        val order = generateOmniRoundOrder(settings, turnCount = 3, previousPlayer = 0, random = Random(0))
        assertEquals(listOf(0, 0, 0), order)
    }

    @Test
    fun `per-turn draw self-balances instead of letting gaps accumulate`() {
        // A plain uniform draw over 300 turns would routinely spread the counts by 30+; the
        // deficit weighting has to hold them far tighter than that.
        val settings = randomSettings(players = 3, eachTurn = true, avoidBackToBack = false)
        repeat(20) { seed ->
            val order = generateOmniRoundOrder(settings, turnCount = 300, previousPlayer = null, random = Random(seed))
            val counts = (0 until 3).map { p -> order.count { it == p } }
            assertTrue("seed $seed spread counts as $counts", counts.max() - counts.min() <= 6)
        }
    }

    @Test
    fun `per-turn draw still allows the repeats and skips a shuffle cannot produce`() {
        // The point of this mode: a player may take two turns in a row (with back-to-back
        // blocking off) and another may sit a short round out entirely.
        val settings = randomSettings(players = 3, eachTurn = true, avoidBackToBack = false)
        val sawRepeat = (0 until 200).any { seed ->
            generateOmniRoundOrder(settings, turnCount = 6, previousPlayer = null, random = Random(seed))
                .zipWithNext().any { (a, b) -> a == b }
        }
        val sawSkip = (0 until 200).any { seed ->
            generateOmniRoundOrder(settings, turnCount = 3, previousPlayer = null, random = Random(seed)).toSet().size < 3
        }
        assertTrue(sawRepeat)
        assertTrue(sawSkip)
    }

    @Test
    fun `a player who just played is less likely to be drawn again`() {
        // First turn is even, so P0 leading after it must make P0 the rarest pick on turn two.
        val settings = randomSettings(players = 3, eachTurn = true, avoidBackToBack = false)
        var p0Twice = 0
        val trials = 3000
        repeat(trials) { seed ->
            val order = generateOmniRoundOrder(settings, turnCount = 2, previousPlayer = null, random = Random(seed))
            if (order[0] == 0 && order[1] == 0) p0Twice++
        }
        // P0 opens ~1/3 of the time, and is then weighted 1 against 2+2 for the others, so a
        // double is ~1/3 * 1/5 ≈ 6.7% -- well under the 1/9 ≈ 11% an unweighted draw would give.
        val rate = p0Twice.toDouble() / trials
        assertTrue("P0 doubled $rate of the time, expected around 0.067", rate in 0.04..0.09)
    }

    @Test
    fun `per-turn draw still honours avoidBackToBack`() {
        val settings = randomSettings(players = 3, eachTurn = true)
        repeat(20) { seed ->
            val order = generateOmniRoundOrder(settings, turnCount = 60, previousPlayer = null, random = Random(seed))
            order.zipWithNext { a, b -> assertNotEquals(a, b) }
        }
    }

    @Test
    fun `balancing off leaves the draw even and free to drift`() {
        val balanced = randomSettings(players = 3, eachTurn = true, avoidBackToBack = false, autoBalance = true)
        val even = randomSettings(players = 3, eachTurn = true, avoidBackToBack = false, autoBalance = false)

        fun worstSpread(settings: OmniSettings) = (0 until 40).maxOf { seed ->
            val order = generateOmniRoundOrder(settings, turnCount = 300, previousPlayer = null, random = Random(seed))
            val counts = (0 until 3).map { p -> order.count { it == p } }
            counts.max() - counts.min()
        }

        // The whole point of the toggle: turning balancing off must visibly let the counts drift.
        assertTrue(worstSpread(even) > worstSpread(balanced))
    }

    @Test
    fun `an even draw can hand one player most of a short round`() {
        val settings = randomSettings(players = 3, eachTurn = true, avoidBackToBack = false, autoBalance = false)
        val sawLandslide = (0 until 300).any { seed ->
            val order = generateOmniRoundOrder(settings, turnCount = 6, previousPlayer = null, random = Random(seed))
            (0 until 3).any { p -> order.count { it == p } >= 4 }
        }
        assertTrue(sawLandslide)
    }

    @Test
    fun `an even draw still honours avoidBackToBack`() {
        val settings = randomSettings(players = 3, eachTurn = true, autoBalance = false)
        repeat(20) { seed ->
            val order = generateOmniRoundOrder(settings, turnCount = 60, previousPlayer = null, random = Random(seed))
            order.zipWithNext { a, b -> assertNotEquals(a, b) }
        }
    }

    @Test
    fun `advancing a turn follows the order drawn for the round`() {
        val settings = randomSettings(players = 4)
        val order = listOf(2, 0, 3, 1)
        val state = OmniState(turnCounterInRound = 0, currentPlayerIndex = 2, roundPlayerOrder = order)

        val next = computeOmniAdvance(state, settings)
        assertEquals(1, next.turnCounterInRound)
        assertEquals(0, next.currentPlayerIndex)
        // Same round, so the drawn order carries over untouched.
        assertEquals(order, next.roundPlayerOrder)
    }

    @Test
    fun `advancing a round draws a new order and starts on its first player`() {
        val settings = randomSettings(players = 3).copy(
            games = listOf(OmniGameSettings(rounds = listOf(OmniRoundSettings(), OmniRoundSettings())))
        )
        val state = OmniState(turnCounterInRound = 2, currentPlayerIndex = 1, roundPlayerOrder = listOf(1, 0, 2))

        val next = computeOmniAdvance(state, settings, random = Random(11))
        assertEquals("ROUND", next.transitionLabel)
        assertEquals(3, next.roundPlayerOrder.size)
        assertEquals((0 until 3).toSet(), next.roundPlayerOrder.toSet())
        assertEquals(next.roundPlayerOrder.first(), next.currentPlayerIndex)
        assertNotEquals(1, next.currentPlayerIndex) // avoidBackToBack across the round boundary
    }

    @Test
    fun `the deterministic orders keep no drawn order`() {
        val settings = OmniSettings(numberOfPlayers = 3, playerOrderType = PlayerOrderType.LINEAR)
        val state = OmniState(turnCounterInRound = 0, currentPlayerIndex = 0)

        val next = computeOmniAdvance(state, settings)
        assertTrue(next.roundPlayerOrder.isEmpty())
        assertEquals(1, next.currentPlayerIndex)
    }

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
