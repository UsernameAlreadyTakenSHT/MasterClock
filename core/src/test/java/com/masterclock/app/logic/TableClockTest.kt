package com.masterclock.app.logic

import org.junit.Test
import org.junit.Assert.*

class TableClockTest {

    @Test
    fun `a CHRONO countdown running out is the end of the game, not player 1's flag`() {
        val settings = ChessClockSettings(main = PlayerSettings(mode = TimerMode.CHRONO_COUNTDOWN), flagBehavior = FlagBehavior.FLAG)
        val state = ChessClockState(players = listOf(PlayerState(0), PlayerState(0)), activePlayer = 1, isPaused = false, globalTimeMs = 100)
        val end = tickCoupled(state, 200, settings, settings.main, 1)!!.state
        assertTrue(tableClockRanOut(end))
        assertEquals(ClockAnnouncement.Finished, clockAnnouncement(end))
    }

    @Test
    fun `everyone out with a first flag is a flag, not the table's clock`() {
        val state = ChessClockState(players = listOf(PlayerState(0, isOutOfTime = true), PlayerState(0, isOutOfTime = true)), activePlayer = 1, firstToFlag = 1)
        assertFalse(tableClockRanOut(state))
    }

    @Test
    fun `CHRONO turns per-player settings off`() {
        val p1 = PlayerSettings(mode = TimerMode.CHRONO_COUNTDOWN, initialTimeMs = 60_000)
        val s = ChessClockSettings(differentSettingsPerPlayer = true, p1Custom = p1, p2Custom = PlayerSettings(mode = TimerMode.CHRONO_COUNTDOWN)).wholeTableSettings()
        assertFalse(s.differentSettingsPerPlayer)
    }
}
