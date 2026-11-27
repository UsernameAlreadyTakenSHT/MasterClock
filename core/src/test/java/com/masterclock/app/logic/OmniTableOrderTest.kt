package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The order the players take their turns in, under SNAKE and ROTATE, as the wizard promises it for
 * three players: "123, 321, 123" and "123, 231, 312", a pass round the table at a time.
 *
 * Both read the round's index, so they turned only where a new round began: a LOOP round, a round of
 * several turns a player and each new game went round in the same order every time. Nothing checked
 * the pattern itself -- OmniSimulationTest compares the player on turn with the function that picks
 * it, which cannot tell a wrong pattern from a right one.
 */
class OmniTableOrderTest {

    private fun settings(order: PlayerOrderType, vararg games: OmniGameSettings) = OmniSettings(
        numberOfPlayers = 3,
        playerOrderType = order,
        games = games.toList(),
    )

    private fun fixedRound() = OmniRoundSettings(turnLogic = RoundTurnLogic.FIXED, roundEndBehavior = RoundEndBehavior.ADVANCE)

    /** The players, numbered from 1, for the first [turns] turns of a session started on [s]. */
    private fun play(s: OmniSettings, turns: Int): List<Int> {
        var state = createInitialOmniState(s).copy(isRunning = true, hasStarted = true)
        val seen = mutableListOf(state.currentPlayerIndex + 1)
        repeat(turns - 1) {
            state = computeOmniAdvance(state, s)
            seen += state.currentPlayerIndex + 1
        }
        return seen
    }

    @Test
    fun `snake turns back at each round`() {
        val s = settings(PlayerOrderType.SNAKE, OmniGameSettings(rounds = List(3) { fixedRound() }))
        assertEquals(listOf(1, 2, 3, 3, 2, 1, 1, 2, 3), play(s, 9))
    }

    @Test
    fun `snake turns back at each pass of a looping round`() {
        val loop = OmniRoundSettings(turnLogic = RoundTurnLogic.FIXED, roundEndBehavior = RoundEndBehavior.LOOP, durationMs = 3_600_000)
        val s = settings(PlayerOrderType.SNAKE, OmniGameSettings(rounds = listOf(loop)))
        // It went 123, 123, 123 for as long as the round clock ran.
        assertEquals(listOf(1, 2, 3, 3, 2, 1, 1, 2, 3), play(s, 9))
    }

    @Test
    fun `snake turns back between the two turns each player has in a round`() {
        val twoEach = OmniRoundSettings(turnLogic = RoundTurnLogic.SEQUENCE, customTurns = listOf(OmniTurnSettings(), OmniTurnSettings()))
        val s = settings(PlayerOrderType.SNAKE, OmniGameSettings(rounds = listOf(twoEach)))
        assertEquals(listOf(1, 2, 3, 3, 2, 1), play(s, 6))
    }

    @Test
    fun `snake goes on turning from one game to the next`() {
        val s = settings(PlayerOrderType.SNAKE, OmniGameSettings(rounds = listOf(fixedRound())), OmniGameSettings(rounds = listOf(fixedRound())))
        // Each game began on player 1 again, which played exactly like the plain order.
        assertEquals(listOf(1, 2, 3, 3, 2, 1), play(s, 6))
    }

    @Test
    fun `rotate starts each pass one player on`() {
        val s = settings(PlayerOrderType.ROTATE, OmniGameSettings(rounds = List(3) { fixedRound() }))
        assertEquals(listOf(1, 2, 3, 2, 3, 1, 3, 1, 2), play(s, 9))
    }

    @Test
    fun `rotate moves on at each pass of a looping round`() {
        val loop = OmniRoundSettings(turnLogic = RoundTurnLogic.FIXED, roundEndBehavior = RoundEndBehavior.LOOP, durationMs = 3_600_000)
        val s = settings(PlayerOrderType.ROTATE, OmniGameSettings(rounds = listOf(loop)))
        assertEquals(listOf(1, 2, 3, 2, 3, 1, 3, 1, 2), play(s, 9))
    }

    @Test
    fun `the plain order is the same at every pass`() {
        val loop = OmniRoundSettings(turnLogic = RoundTurnLogic.FIXED, roundEndBehavior = RoundEndBehavior.LOOP, durationMs = 3_600_000)
        val s = settings(PlayerOrderType.LINEAR, OmniGameSettings(rounds = listOf(fixedRound(), loop)))
        assertEquals(listOf(1, 2, 3, 1, 2, 3, 1, 2, 3), play(s, 9))
    }

    @Test
    fun `a round cut short in the middle of a pass ends that pass`() {
        val s = settings(PlayerOrderType.SNAKE, OmniGameSettings(rounds = List(2) { fixedRound() }))
        var state = createInitialOmniState(s).copy(isRunning = true, hasStarted = true)
        state = computeOmniAdvance(state, s)
        assertEquals(1, state.currentPlayerIndex)
        // The round's clock runs out on player 2: the next round is a new pass, and turns back.
        state = computeOmniAdvance(state, s, forceLevel = "ROUND")
        assertEquals(1, state.currentRoundIndex)
        assertEquals(2, state.currentPlayerIndex)
    }

    @Test
    fun `a session stored before the pass was counted reads as the first pass`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val old = json.decodeFromString(OmniState.serializer(), """{"hasStarted":true,"currentPlayerIndex":1}""")
        assertEquals(0, old.tablePass)
    }
}
