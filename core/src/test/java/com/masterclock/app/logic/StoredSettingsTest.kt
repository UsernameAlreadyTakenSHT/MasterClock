package com.masterclock.app.logic

import com.masterclock.app.data.decodeStoredSettings
import com.masterclock.app.data.storedJson
import org.junit.Assert.*
import org.junit.Test

class StoredSettingsTest {

    // Timestamps given: the default is the time of creation, which a read back would take again.
    private val notes = listOf(NotebookNote(id = "a", title = "Opening ideas", timestamp = 1_000), NotebookNote(id = "b", title = "Endgame", timestamp = 2_000))
    private val stored = storedJson.encodeToString(
        ChessClockSettings.serializer(),
        ChessClockSettings(flagBehavior = FlagBehavior.NEGATIVE, numberOfPlayers = 3, notebookNotes = notes),
    )

    @Test
    fun `settings read back as they were stored`() {
        val read = decodeStoredSettings(stored)
        assertEquals(FlagBehavior.NEGATIVE, read.flagBehavior)
        assertEquals(notes, read.notebookNotes)
    }

    @Test
    fun `a value from a newer version falls back to its default, the rest kept`() {
        val read = decodeStoredSettings(stored.replace("\"NEGATIVE\"", "\"SOMETHING_NEWER\""))
        assertEquals(FlagBehavior.FREEZE, read.flagBehavior)
        assertEquals(3, read.numberOfPlayers)
        assertEquals(notes, read.notebookNotes)
    }

    @Test
    fun `settings that cannot be read keep the notebook`() {
        val read = decodeStoredSettings(stored.replace("\"numberOfPlayers\":3", "\"numberOfPlayers\":{}"))
        assertEquals(ChessClockSettings().numberOfPlayers, read.numberOfPlayers)
        assertEquals(notes, read.notebookNotes)
    }

    @Test
    fun `a note that cannot be read costs only itself`() {
        val broken = stored.replace("\"title\":\"Endgame\"", "\"title\":{}").replace("\"numberOfPlayers\":3", "\"numberOfPlayers\":{}")
        assertEquals(listOf("a"), decodeStoredSettings(broken).notebookNotes.map { it.id })
    }

    @Test
    fun `nothing readable at all gives the defaults`() {
        assertEquals(ChessClockSettings(), decodeStoredSettings("not json"))
    }
}
