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
    fun `a resumed clock keeps today's notebook and preferences, and the game's own rules`() {
        val now = ChessClockSettings(
            notebookNotes = listOf(NotebookNote(title = "written after the autosave")),
            activeColor = 0xFF123456, voiceAnnouncementsEnabled = true, logHistoryLimit = 20,
            flagBehavior = FlagBehavior.FREEZE,
        )
        val saved = ChessClockSettings(
            notebookNotes = listOf(NotebookNote(title = "deleted since")),
            main = PlayerSettings(initialTimeMs = 300_000),
            differentSettingsPerPlayer = true,
            p1Custom = PlayerSettings(initialTimeMs = 120_000),
            p2Custom = PlayerSettings(initialTimeMs = 600_000),
            numberOfPlayers = 3, flagBehavior = FlagBehavior.NEGATIVE, fischerFideFirstMove = true,
        )
        val resumed = restoreSavedTimeControl(now, saved)
        assertEquals(now.notebookNotes, resumed.notebookNotes)
        assertEquals(0xFF123456, resumed.activeColor)
        assertTrue(resumed.voiceAnnouncementsEnabled)
        assertEquals(20, resumed.logHistoryLimit)
        assertEquals(saved.main, resumed.main)
        assertEquals(saved.p1Custom, resumed.p1Custom)
        assertEquals(saved.p2Custom, resumed.p2Custom)
        assertTrue(resumed.differentSettingsPerPlayer)
        assertEquals(3, resumed.numberOfPlayers)
        assertEquals(FlagBehavior.NEGATIVE, resumed.flagBehavior)
        assertTrue(resumed.fischerFideFirstMove)
    }

    @Test
    fun `a resumed clock keeps per-player settings it was not using`() {
        // Per-player off: a preset folds main into p1..p4, a resumed game must not.
        val saved = ChessClockSettings(main = PlayerSettings(initialTimeMs = 300_000), p1Custom = PlayerSettings(initialTimeMs = 42_000))
        assertEquals(42_000, restoreSavedTimeControl(ChessClockSettings(), saved).p1Custom.initialTimeMs)
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
