package com.masterclock.app.logic

import org.junit.Test
import org.junit.Assert.*

class SharedModesTest {

    @Test
    fun `a shared mode turns per-player settings off with player 1's becoming everyone's`() {
        val p1 = PlayerSettings(mode = TimerMode.MOVE_TIMER_GLOBAL_SHARED, moveTimeMs = 20_000, initialTimeMs = 600_000)
        val p2 = PlayerSettings(mode = TimerMode.MOVE_TIMER_GLOBAL_SHARED, moveTimeMs = 40_000, initialTimeMs = 60_000)
        val s = ChessClockSettings(differentSettingsPerPlayer = true, p1Custom = p1, p2Custom = p2).wholeTableSettings()
        assertFalse(s.differentSettingsPerPlayer)
        assertEquals(p1, s.main)
        assertEquals(p1, s.p2Custom)
    }

    @Test
    fun `Global Shared counts down whichever clock runs out first`() {
        val s = PlayerSettings(mode = TimerMode.MOVE_TIMER_GLOBAL_SHARED, moveTimeMs = 30_000)
        val settings = ChessClockSettings(main = s)
        val players = List(2) { PlayerState(timeRemainingMs = 30_000, secondaryTimeMs = 4_500) }
        val cue = tickCoupled(ChessClockState(players = players, activePlayer = 1), 1_000, settings, s, 1)!!.cue!!
        // The reserve is the one about to flag everyone, so it is what the beeps follow.
        assertEquals(4_500, cue.before.timeRemainingMs)
        assertEquals(3_500, cue.after.timeRemainingMs)
    }

    @Test
    fun `a Global Shared shortcut names the reserve too`() {
        val settings = ChessClockSettings(main = PlayerSettings(mode = TimerMode.MOVE_TIMER_GLOBAL_SHARED, moveTimeMs = 30_000, initialTimeMs = 600_000))
        val targets = buildShortcutTargets(history = listOf(GameLog(startTime = 7, settings = settings)), presets = emptyList())
        assertEquals("30s / move, 10 min", targets.first().label)
    }

    @Test
    fun `player 2 follows player 1's Fast Move variant, not only the mode`() {
        val p1 = PlayerSettings(mode = TimerMode.FAST_MOVE, fastMoveMode = FastMoveType.TRANSFER, fastMoveTransferCumulative = false)
        val p2 = PlayerSettings(mode = TimerMode.SUDDEN_DEATH, fastMoveMode = FastMoveType.ACCELERATE, initialTimeMs = 123_000).followingModeOf(p1)
        assertEquals(TimerMode.FAST_MOVE, p2.mode)
        assertEquals(FastMoveType.TRANSFER, p2.fastMoveMode)
        assertFalse(p2.fastMoveTransferCumulative)
        // Player 2's own times stay theirs.
        assertEquals(123_000, p2.initialTimeMs)
    }

    @Test
    fun `a seat order that leaves a player out is put back`() {
        // Players 3 and 4 swapped with four, then down to three: player 3 had no seat.
        val three = ChessClockSettings(numberOfPlayers = 3, playerMapping = listOf(1, 2, 4, 3)).withSeatsForPlayers()
        assertEquals(listOf(1, 2, 3, 4), three.playerMapping)
        val kept = ChessClockSettings(numberOfPlayers = 3, playerMapping = listOf(2, 1, 3, 4))
        assertSame(kept, kept.withSeatsForPlayers())
    }

    @Test
    fun `any other mode keeps its per-player settings`() {
        val s = ChessClockSettings(differentSettingsPerPlayer = true, p1Custom = PlayerSettings(initialTimeMs = 300_000), p2Custom = PlayerSettings(initialTimeMs = 240_000))
        assertSame(s, s.wholeTableSettings())
    }
}
