package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared reserve, as a reserve to fall back on. Every turn's unspent time goes into it; nobody is
 * handed it as their turn begins; a player runs on it once their own turn clock is out, and the turn
 * ends only when both are gone. It used to be given whole to whoever played next.
 */
class OmniSharedReserveTest {

    private val s = OmniSettings(
        numberOfPlayers = 3,
        timeBankMode = TimeBankMode.GLOBAL_RESERVE,
        timeBankScope = TimeBankScope.GAME_TO_GAME,
        turnForcesCutoff = true,
        games = listOf(OmniGameSettings(rounds = List(3) { OmniRoundSettings(turnLogic = RoundTurnLogic.FIXED, turnDurationMs = 60_000) })),
    )

    private fun running() = createInitialOmniState(s).copy(isRunning = true, hasStarted = true)

    private fun tick(state: OmniState, times: Int, ms: Long = 100): Pair<OmniState, List<OmniSound>> {
        var st = state
        val sounds = mutableListOf<OmniSound>()
        repeat(times) { computeOmniTick(st, ms, s).let { st = it.state; sounds += it.sounds } }
        return st to sounds
    }

    @Test
    fun `nobody is handed the reserve as their turn begins`() {
        // Player 1 saves 0:40.
        val next = computeOmniAdvance(running().copy(currentTurnTimeMs = 40_000), s)
        assertEquals(1, next.currentPlayerIndex)
        assertEquals(60_000, next.currentTurnTimeMs)
        assertEquals(40_000L, omniSharedPoolMs(next, s))
    }

    @Test
    fun `once a turn's own time is out, the reserve runs down, and the turn goes on`() {
        val withReserve = running().copy(currentTurnTimeMs = 1_000, playerTimeBanks = mapOf(OMNI_GLOBAL_TIME_BANK_KEY to 30_000L))
        val (after, sounds) = tick(withReserve, 50) // five seconds
        assertEquals(0, after.currentTurnTimeMs)
        assertEquals(26_000L, omniSharedPoolMs(after, s))
        // Same player, no end-of-turn sound: the turn is not over.
        assertEquals(0, after.currentPlayerIndex)
        assertFalse(sounds.contains(OmniSound.TURN_END))
    }

    @Test
    fun `the turn ends, sounds and is cut off only when the reserve is gone too`() {
        val withReserve = running().copy(currentTurnTimeMs = 1_000, playerTimeBanks = mapOf(OMNI_GLOBAL_TIME_BANK_KEY to 2_000L))
        val (after, sounds) = tick(withReserve, 30) // three seconds: one of its own, two of the reserve
        assertEquals(listOf(OmniSound.TURN_END), sounds.filter { it == OmniSound.TURN_END })
        assertEquals(0L, omniSharedPoolMs(after, s))
        // The turn clock's cutoff passed the turn on, with the reserve empty.
        assertEquals(1, after.currentPlayerIndex)
    }

    @Test
    fun `what a turn draws from the reserve and does not use stays in it for the others`() {
        var state = running().copy(currentTurnTimeMs = 500, playerTimeBanks = mapOf(OMNI_GLOBAL_TIME_BANK_KEY to 30_000L))
        state = tick(state, 25).first // half a second of its own, two of the reserve
        assertEquals(28_000L, omniSharedPoolMs(state, s))
        // Next, before the reserve is gone: nothing more is taken, and nothing of it is lost.
        state = computeOmniAdvance(state, s)
        assertEquals(28_000L, omniSharedPoolMs(state, s))
    }

    @Test
    fun `a slow player no longer takes what the table saved before their turn begins`() {
        // Players 1 and 2 save 0:30 each; player 3 is the slow one.
        var state = computeOmniAdvance(running().copy(currentTurnTimeMs = 30_000), s)
        state = computeOmniAdvance(state.copy(currentTurnTimeMs = 30_000), s)
        assertEquals(2, state.currentPlayerIndex)
        assertEquals(60_000, state.currentTurnTimeMs)
        assertEquals(60_000L, omniSharedPoolMs(state, s))
        // He uses his own minute and twenty seconds of the reserve: the rest is still there for the table.
        state = tick(state, 800).first
        assertTrue(state.currentPlayerIndex == 2)
        assertEquals(40_000L, omniSharedPoolMs(state, s))
    }

    @Test
    fun `the notification and the wake-up wait for the turn's real end`() {
        val st = running().copy(currentTurnTimeMs = 5_000, playerTimeBanks = mapOf(OMNI_GLOBAL_TIME_BANK_KEY to 20_000L))
        assertEquals(25_000L, omniBackgroundClockOf(st, s)!!.countdownMs)
        assertEquals(25_000L, omniNextDueMs(st, s.copy(useRoundClock = false, useGameClock = false)))
    }

    @Test
    fun `a bank of one's own is still drawn as one's turn begins`() {
        val own = s.copy(timeBankMode = TimeBankMode.ACCUMULATIVE)
        var state = computeOmniAdvance(running().copy(currentTurnTimeMs = 40_000), own)
        repeat(2) { state = computeOmniAdvance(state.copy(currentTurnTimeMs = 0), own) }
        assertEquals(0, state.currentPlayerIndex)
        assertEquals(100_000, state.currentTurnTimeMs)
    }
}
