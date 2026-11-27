package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A turn with no phases is one phase as long as the turn, so time drawn from the bank lengthens both.
 * It lengthened the turn alone: the phase clock ran out at the turn's base length and sounded the
 * end of a turn that still had the banked time to go.
 */
class OmniBankedPhaseTest {

    private fun settings(phases: List<OmniPhaseSettings> = emptyList()) = OmniSettings(
        numberOfPlayers = 2,
        usePhaseClock = true,
        timeBankMode = TimeBankMode.ACCUMULATIVE,
        games = listOf(OmniGameSettings(rounds = List(2) {
            OmniRoundSettings(turnLogic = RoundTurnLogic.FIXED, turnDurationMs = 60_000, fixedTurnPhases = phases)
        })),
    )

    /** Player 2 ending round one, on their last phase, with 20s in player 1's bank for round two. */
    private fun beforePlayerOnesNextTurn(s: OmniSettings) = createInitialOmniState(s).copy(
        isRunning = true, hasStarted = true,
        currentPlayerIndex = 1, turnCounterInRound = 1,
        currentPhaseIndex = (s.games[0].rounds[0].fixedTurnPhases.size - 1).coerceAtLeast(0),
        currentTurnTimeMs = 0,
        playerTimeBanks = mapOf(0 to 20_000L),
    )

    @Test
    fun `a turn with no phases has its banked time on its phase clock too`() {
        val s = settings()
        val next = computeOmniAdvance(beforePlayerOnesNextTurn(s), s)
        assertEquals(0, next.currentPlayerIndex)
        assertEquals(80_000, next.currentTurnTimeMs)
        assertEquals(80_000, next.currentPhaseTimeMs)
    }

    @Test
    fun `a turn with phases keeps its first phase as set up`() {
        val s = settings(listOf(OmniPhaseSettings(name = "Draw", durationMs = 15_000), OmniPhaseSettings(name = "Play", durationMs = 45_000)))
        val next = computeOmniAdvance(beforePlayerOnesNextTurn(s), s)
        assertEquals(80_000, next.currentTurnTimeMs)
        assertEquals(15_000, next.currentPhaseTimeMs)
    }

    @Test
    fun `no early end-of-turn sound for a turn lengthened by the bank`() {
        val s = settings()
        var state = computeOmniAdvance(beforePlayerOnesNextTurn(s), s)
        val sounds = mutableListOf<OmniSound>()
        // A minute: the turn's base length, short of what the bank added.
        repeat(600) { computeOmniTick(state, 100, s).let { state = it.state; sounds += it.sounds } }
        assertEquals(emptyList<OmniSound>(), sounds.filter { it == OmniSound.TURN_END })
        assertEquals(20_000, state.currentTurnTimeMs)
    }
}
