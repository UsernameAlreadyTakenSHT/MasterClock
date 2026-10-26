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
    fun `Overtime warns of the reserve once the move clock is spent`() {
        val p = PlayerState(timeRemainingMs = 0, secondaryTimeMs = 8_000)
        assertEquals(8_000, alertTimeMs(ChessClockState(players = listOf(p)), p, TimerMode.MOVE_TIMER_OVERTIME))
        val moving = p.copy(timeRemainingMs = 12_000)
        assertEquals(12_000, alertTimeMs(ChessClockState(players = listOf(moving)), moving, TimerMode.MOVE_TIMER_OVERTIME))
    }

    @Test
    fun `move timer shortcuts name the move time, and the total or reserve where there is one`() {
        fun label(s: PlayerSettings) = buildShortcutTargets(history = listOf(GameLog(startTime = 7, settings = ChessClockSettings(main = s))), presets = emptyList()).first().label
        assertEquals("30s / move", label(PlayerSettings(mode = TimerMode.MOVE_TIMER_SAVE_CAP, moveTimeMs = 30_000)))
        assertEquals("30s / move, 10 min", label(PlayerSettings(mode = TimerMode.MOVE_TIMER_OVERTIME, moveTimeMs = 30_000, initialTimeMs = 600_000)))
        assertEquals("30s / move, 10 min", label(PlayerSettings(mode = TimerMode.MOVE_TIMER_GLOBAL, moveTimeMs = 30_000, initialTimeMs = 600_000)))
    }

    @Test
    fun `CHRONO turns per-player settings off`() {
        val p1 = PlayerSettings(mode = TimerMode.CHRONO_COUNTDOWN, initialTimeMs = 60_000)
        val s = ChessClockSettings(differentSettingsPerPlayer = true, p1Custom = p1, p2Custom = PlayerSettings(mode = TimerMode.CHRONO_COUNTDOWN)).wholeTableSettings()
        assertFalse(s.differentSettingsPerPlayer)
    }
}
