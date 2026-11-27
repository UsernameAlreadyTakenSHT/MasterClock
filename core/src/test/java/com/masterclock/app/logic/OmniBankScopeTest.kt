package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How long saved time lasts, by the choice the wizard offers: one turn, one round, one game, the whole
 * session. "One round" kept a player's savings to the end of the game where each plays once a round --
 * the same as "one game" -- and "one turn" let them build up, because time drawn from the bank and not
 * spent was put by again with the turn's own savings.
 *
 * Alice's turn is 2:00; her own time is spent first, then what she drew.
 */
class OmniBankScopeTest {

    private val minute = 60_000L

    private fun settings(scope: TimeBankScope, rounds: Int = 4) = OmniSettings(
        numberOfPlayers = 2,
        timeBankMode = TimeBankMode.ACCUMULATIVE,
        timeBankScope = scope,
        games = listOf(OmniGameSettings(rounds = List(rounds) { OmniRoundSettings(turnLogic = RoundTurnLogic.FIXED, turnDurationMs = 2 * minute) })),
    )

    /** Plays Alice's (player 1's) turns, each leaving [left] on her clock; Bob's leave nothing. Her turn lengths, in order. */
    private fun alicesTurns(s: OmniSettings, left: List<Long>): List<Long> {
        var state = createInitialOmniState(s).copy(isRunning = true, hasStarted = true)
        val lengths = mutableListOf<Long>()
        for (l in left) {
            lengths += state.currentTurnTimeMs
            state = computeOmniAdvance(state.copy(currentTurnTimeMs = l), s)      // Alice ends her turn
            state = computeOmniAdvance(state.copy(currentTurnTimeMs = 0), s)      // Bob ends his
        }
        lengths += state.currentTurnTimeMs
        return lengths
    }

    @Test
    fun `one round - savings serve the next round's turn, then go`() {
        // Saves 0:30, then spends only 0:10 of her 2:30: she saved 1:50 of her own, not 2:20.
        val turns = alicesTurns(settings(TimeBankScope.ROUND_TO_ROUND), listOf(30_000, 140_000, 0))
        assertEquals(listOf(120_000L, 150_000L, 230_000L, 120_000L), turns)
    }

    @Test
    fun `one round no longer builds up as one game does`() {
        val round = alicesTurns(settings(TimeBankScope.ROUND_TO_ROUND), listOf(30_000, 150_000))
        val game = alicesTurns(settings(TimeBankScope.GAME_TO_GAME), listOf(30_000, 150_000))
        // Not touching her time in round two: "one round" keeps the 2:00 she saved of her own...
        assertEquals(240_000L, round.last())
        // ...where "one game" keeps all of the 2:30 she had left.
        assertEquals(270_000L, game.last())
    }

    @Test
    fun `one turn - only the turn's own savings, never a build-up`() {
        // 0:30 saved; then none of her time spent, twice: 2:00 of her own saved each time, and what she
        // drew on top does not build -- 4:00 at most, where it grew 2:30, 4:30, 6:30...
        val turns = alicesTurns(settings(TimeBankScope.TURN_TO_TURN), listOf(30_000, 150_000, 240_000))
        assertEquals(listOf(120_000L, 150_000L, 240_000L, 240_000L), turns)
    }

    @Test
    fun `the whole session keeps everything`() {
        val turns = alicesTurns(settings(TimeBankScope.SESSION_WIDE), listOf(30_000, 150_000))
        assertEquals(listOf(120_000L, 150_000L, 270_000L), turns)
    }

    @Test
    fun `a round where each player plays twice still keeps savings within it`() {
        // Not the "plays once" case: savings build up within the round, and the round's end clears them.
        val twice = OmniRoundSettings(turnLogic = RoundTurnLogic.SEQUENCE, customTurns = List(2) { OmniTurnSettings(durationMs = 2 * minute) })
        val s = settings(TimeBankScope.ROUND_TO_ROUND).copy(games = listOf(OmniGameSettings(rounds = List(2) { twice })))
        var state = createInitialOmniState(s).copy(isRunning = true, hasStarted = true)
        state = computeOmniAdvance(state.copy(currentTurnTimeMs = 30_000), s) // Alice, turn 1
        state = computeOmniAdvance(state.copy(currentTurnTimeMs = 0), s)      // Bob, turn 1
        assertEquals(150_000L, state.currentTurnTimeMs)                       // Alice, turn 2: 2:00 + 0:30
        state = computeOmniAdvance(state.copy(currentTurnTimeMs = 150_000), s) // she spends nothing
        state = computeOmniAdvance(state.copy(currentTurnTimeMs = 0), s)      // Bob: the round ends
        assertEquals(1, state.currentRoundIndex)
        assertEquals(120_000L, state.currentTurnTimeMs)                       // round two: nothing carried
    }
}
