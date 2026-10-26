package com.masterclock.app.logic

import org.junit.Test
import org.junit.Assert.*

class SharedModesTest {

    @Test
    fun `a shared mode turns per-player settings off with player 1's becoming everyone's`() {
        val p1 = PlayerSettings(mode = TimerMode.MOVE_TIMER_GLOBAL_SHARED, moveTimeMs = 20_000, initialTimeMs = 600_000)
        val p2 = PlayerSettings(mode = TimerMode.MOVE_TIMER_GLOBAL_SHARED, moveTimeMs = 40_000, initialTimeMs = 60_000)
        val s = ChessClockSettings(differentSettingsPerPlayer = true, p1Custom = p1, p2Custom = p2).wholeTableForSharedModes()
        assertFalse(s.differentSettingsPerPlayer)
        assertEquals(p1, s.main)
        assertEquals(p1, s.p2Custom)
    }

    @Test
    fun `any other mode keeps its per-player settings`() {
        val s = ChessClockSettings(differentSettingsPerPlayer = true, p1Custom = PlayerSettings(initialTimeMs = 300_000), p2Custom = PlayerSettings(initialTimeMs = 240_000))
        assertSame(s, s.wholeTableForSharedModes())
    }
}
