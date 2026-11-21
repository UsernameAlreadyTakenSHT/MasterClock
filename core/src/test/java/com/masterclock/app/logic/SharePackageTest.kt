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

    @Test
    fun `what is shared with someone else names no custom sound, in its games either`() {
        val mine = ChessClockSettings(
            customGongUri = "content://docs/document/primary%3AMusic%2Fgong.mp3",
            customBeepUri = "content://docs/document/primary%3AMusic%2Fbeep.mp3",
            customFinalBeepUri = "content://docs/document/primary%3AMusic%2Flast.mp3",
            customSwitchUri = "content://docs/document/primary%3AMusic%2Fswitch.mp3",
        )
        val played = ExportSnapshot(
            settings = mine,
            // A game keeps the settings it was played with: the same four, a second time.
            logs = listOf(GameLog(id = "g", settings = mine, events = listOf(GameEvent(eventType = "MOVE", playerIndex = 1, moveNotation = "e2e4")))),
            scoreboard = ScoreboardSession(),
            presets = emptyList(),
        )
        for (withGames in listOf(true, false)) {
            val sent = String(encodeSharePackage(sharePackageOf(played, includeLogs = withGames).forSomeoneElse()))
            assertTrue("with games: $withGames", !sent.contains("Music") && !sent.contains("content://"))
        }
        // An export kept for oneself still has them, in the settings and in the games.
        val kept = String(encodeSharePackage(sharePackageOf(played, includeLogs = true)))
        assertEquals(8, Regex("primary%3AMusic").findAll(kept).count())
    }
}
