package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What an export holds, and that it reads back as it went. */
class SharePackageTest {

    private val notes = listOf(
        NotebookNote(id = "t", title = "text", type = NotebookNoteType.TEXT, content = "kept"),
        NotebookNote(id = "a", title = "voice", type = NotebookNoteType.VOICE),
    )
    private val snapshot = ExportSnapshot(
        settings = ChessClockSettings(main = PlayerSettings(mode = TimerMode.FISCHER, initialTimeMs = 180_000, incrementMs = 2_000), notebookNotes = notes),
        logs = listOf(GameLog(id = "g", settings = ChessClockSettings(), events = listOf(GameEvent(eventType = "MOVE", playerIndex = 1, moveNotation = "e2e4")))),
        scoreboard = ScoreboardSession(),
        presets = emptyList(),
    )

    @Test
    fun `with the games, only text notes go, and the scoreboard`() {
        val pkg = sharePackageOf(snapshot, includeLogs = true)
        assertEquals(listOf("t"), pkg.settings.notebookNotes.map { it.id })
        assertEquals(listOf("g"), pkg.logs!!.map { it.id })
        assertTrue(pkg.scoreboard != null)
    }

    @Test
    fun `without them, nothing of the notebook or the scoreboard goes`() {
        val pkg = sharePackageOf(snapshot, includeLogs = false)
        assertTrue(pkg.settings.notebookNotes.isEmpty())
        assertNull(pkg.logs)
        assertNull(pkg.scoreboard)
    }

    @Test
    fun `what is written reads back as it went`() {
        val pkg = sharePackageOf(snapshot, includeLogs = true)
        val back = decodeSharePackage(String(encodeSharePackage(pkg)))
        assertEquals(TimerMode.FISCHER, back.settings.main.mode)
        assertEquals(pkg.logs!!.single().events.single().moveNotation, back.logs!!.single().events.single().moveNotation)
    }
}
