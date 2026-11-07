package com.masterclock.app.logic

import com.masterclock.app.data.Converters
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
    fun `reset settings keeps the notebook and how much history there is`() {
        val mine = ChessClockSettings(flagBehavior = FlagBehavior.NEGATIVE, notebookNotes = notes, logHistoryLimit = -1, logDurationLimit = LogDurationLimit.entries.first { it != LogDurationLimit.INFINITE })
        val reset = settingsAfterReset(mine)
        assertEquals(notes, reset.notebookNotes)
        assertEquals(-1, reset.logHistoryLimit)
        assertEquals(mine.logDurationLimit, reset.logDurationLimit)
        assertEquals(ChessClockSettings().flagBehavior, reset.flagBehavior)
    }

    @Test
    fun `a time control changed ends the game and a colour does not`() {
        val base = ChessClockSettings()
        assertEquals(true, settingsEndTheGame(base, base.copy(main = base.main.copy(initialTimeMs = 1))))
        assertEquals(false, settingsEndTheGame(base, base.copy(confirmReset = false)))
    }

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
    fun `a setting that cannot be read costs only itself`() {
        val read = decodeStoredSettings(stored.replace("\"numberOfPlayers\":3", "\"numberOfPlayers\":{}"))
        assertEquals(ChessClockSettings().numberOfPlayers, read.numberOfPlayers)
        assertEquals(FlagBehavior.NEGATIVE, read.flagBehavior)
        assertEquals(notes, read.notebookNotes)
    }

    @Test
    fun `a game whose settings cannot be fully read keeps its moves`() {
        val game = GameLog(id = "g", startTime = 1, settings = ChessClockSettings(flagBehavior = FlagBehavior.NEGATIVE, numberOfPlayers = 3), events = listOf(GameEvent(timestamp = 2, eventType = "MOVE")))
        val converters = Converters()
        val entity = converters.fromGameLog(game)
        val broken = entity.copy(settingsJson = entity.settingsJson.replace("\"numberOfPlayers\":3", "\"numberOfPlayers\":{}").replace("\"NEGATIVE\"", "\"SOMETHING_NEWER\""))
        val read = converters.toGameLog(broken)
        assertEquals(game.events, read.events)
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
