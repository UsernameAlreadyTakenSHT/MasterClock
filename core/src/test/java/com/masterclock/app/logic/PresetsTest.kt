package com.masterclock.app.logic

import org.junit.Test
import org.junit.Assert.*

class PresetsTest {

    private val mine = ChessClockSettings(flagBehavior = FlagBehavior.NEGATIVE, fischerFideFirstMove = true)

    @Test
    fun `a built-in keeps the player's flag behaviour and first-move bonus`() {
        val applied = applyPresetTimeControl(mine, ChessClockSettings(main = PlayerSettings(initialTimeMs = 300_000)), keepFlagBehavior = true, keepFirstMoveBonus = true)
        assertEquals(FlagBehavior.NEGATIVE, applied.flagBehavior)
        assertTrue(applied.fischerFideFirstMove)
        assertEquals(300_000, applied.main.initialTimeMs)
    }

    @Test
    fun `a preset the player saved brings back its own`() {
        val applied = applyPresetTimeControl(mine, ChessClockSettings(flagBehavior = FlagBehavior.FLAG))
        assertEquals(FlagBehavior.FLAG, applied.flagBehavior)
        assertFalse(applied.fischerFideFirstMove)
    }

    @Test
    fun `a PHASES preset brings back its loop and pause`() {
        val preset = ChessClockSettings(loopPhases = true, allowPhaseSkip = true, pauseBetweenPhasesMs = 30_000, pauseUntilTap = true)
        val applied = applyPresetTimeControl(ChessClockSettings(), preset)
        assertTrue(applied.loopPhases && applied.allowPhaseSkip && applied.pauseUntilTap)
        assertEquals(30_000, applied.pauseBetweenPhasesMs)
    }

    @Test
    fun `restoring presets stays within the cap, the device's own first`() {
        val local = List(499) { SavedPreset(name = "L$it", settings = ChessClockSettings()) }
        val imported = List(10) { SavedPreset(name = "I$it", settings = ChessClockSettings()) }
        val merged = mergeImportedPresets(local, imported)
        assertEquals(500, merged.size)
        assertEquals(local, merged.take(499))
    }
}
