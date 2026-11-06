package com.masterclock.app.data

import com.masterclock.app.logic.ChessClockSettings
import com.masterclock.app.logic.GameEvent
import com.masterclock.app.logic.GameLog
import org.junit.Assert.*
import org.junit.Test

/** Covers [fitEventsToRow]: a game imported at its character caps made a row Room could not read. */
class ImportedRowSizeTest {

    private val converters = Converters()

    private fun event(detail: String) = GameEvent(eventType = "T".repeat(64), detail = detail, moveNotation = "m".repeat(32))

    private fun game(events: List<GameEvent>) = GameLog(id = "x", settings = ChessClockSettings(), events = events)

    @Test
    fun `control characters at the caps stay under what Room reads`() {
        // Six bytes each once in JSON, so 2,000 of these come to more than 4 MB.
        val entity = converters.fromImportedGameLog(game(List(2_000) { event("\u0001".repeat(256)) }))
        assertTrue(entity.eventsJson.toByteArray().size <= MAX_STORED_EVENTS_BYTES)
    }

    @Test
    fun `three-byte text at the caps stays under what Room reads`() {
        val entity = converters.fromImportedGameLog(game(List(2_000) { event("棋".repeat(256)) }))
        assertTrue(entity.eventsJson.toByteArray().size <= MAX_STORED_EVENTS_BYTES)
    }

    @Test
    fun `what is kept is the start of the game`() {
        val events = List(2_000) { event("\u0001".repeat(250) + it) }
        val kept = fitEventsToRow(events) { list -> list.joinToString { "x".repeat(2_000) } }
        assertTrue(kept.isNotEmpty())
        assertEquals(events.take(kept.size), kept)
    }

    @Test
    fun `a whole game of ordinary moves goes in untouched`() {
        val events = List(2_000) { GameEvent(eventType = "MOVE", playerIndex = 1, moveNotation = "Nf3", moveCount = it) }
        val entity = converters.fromImportedGameLog(game(events))
        assertEquals(converters.fromGameLog(game(events)).eventsJson, entity.eventsJson)
    }

    @Test
    fun `an event too large on its own is dropped rather than looped over`() {
        val kept = fitEventsToRow(listOf(event("x")), maxBytes = 10) { "y".repeat(100) }
        assertTrue(kept.isEmpty())
    }
}
