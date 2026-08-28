package com.masterclock.app.logic

import org.junit.Test
import org.junit.Assert.*
import java.io.ByteArrayInputStream

/**
 * Covers the bounds put on an imported [GameLog] and on the raw bytes of a settings file.
 *
 * Nothing here defends the sandbox -- [GameEvent] carries no path or URI, and the file-path and
 * note-id sanitisation lives with the settings. These limits exist because the values flow into the
 * statistics screen and the log detail table, which renders one Row per move without a lazy list.
 */
class ImportSanitizationTest {

    private fun log(events: List<GameEvent>, states: List<PlayerStateProxy> = emptyList()) = GameLog(
        settings = ChessClockSettings(),
        events = events,
        initialPlayerStates = states,
    )

    private fun move(player: Int? = 1, spent: Long? = null, remaining: Long? = null, notation: String? = null) =
        GameEvent(
            eventType = "MOVE",
            playerIndex = player,
            timeSpentMs = spent,
            timeRemainingMs = remaining,
            moveNotation = notation,
        )

    // --- sanitizeImportedLog ---

    @Test
    fun `caps the number of events`() {
        val huge = log(List(MAX_IMPORTED_EVENTS_PER_LOG * 5) { move() })
        assertEquals(MAX_IMPORTED_EVENTS_PER_LOG, sanitizeImportedLog(huge).events.size)
    }

    @Test
    fun `keeps a normal game untouched`() {
        val normal = log(List(120) { move(player = it % 2 + 1, spent = 4_000, notation = "e2e4") })
        assertEquals(normal, sanitizeImportedLog(normal))
    }

    @Test
    fun `drops a player index outside the supported range`() {
        val events = listOf(move(player = 0), move(player = 5), move(player = -1), move(player = 3))
        val out = sanitizeImportedLog(log(events)).events
        assertNull(out[0].playerIndex)
        assertNull(out[1].playerIndex)
        assertNull(out[2].playerIndex)
        assertEquals(3, out[3].playerIndex)
    }

    @Test
    fun `clamps negative and absurd durations`() {
        val events = listOf(move(spent = -5_000, remaining = -1), move(spent = Long.MAX_VALUE))
        val out = sanitizeImportedLog(log(events)).events
        assertEquals(0L, out[0].timeSpentMs)
        assertEquals(0L, out[0].timeRemainingMs)
        assertTrue("expected a bounded duration", out[1].timeSpentMs!! < Long.MAX_VALUE)
    }

    @Test
    fun `truncates an overlong move notation`() {
        val out = sanitizeImportedLog(log(listOf(move(notation = "a".repeat(5_000))))).events
        assertEquals(32, out[0].moveNotation!!.length)
    }

    @Test
    fun `caps initial player states at four`() {
        val states = List(50) { PlayerStateProxy(timeRemainingMs = 600_000) }
        assertEquals(4, sanitizeImportedLog(log(emptyList(), states)).initialPlayerStates.size)
    }

    @Test
    fun `a null player index no longer desynchronises the move table`() {
        // moveDurations() skips a MOVE with no player, so the detail screen must not build its rows
        // from a separately filtered list. Sanitising to null and reading durations alone keeps the
        // two in step by construction.
        val events = listOf(move(player = 1), move(player = 99), move(player = 2))
        val durations = moveDurations(sanitizeImportedLog(log(events)))
        assertEquals(2, durations.size)
        assertEquals(listOf(1, 2), durations.map { it.playerIndex })
    }

    @Test
    fun `move notation travels with the duration`() {
        val durations = moveDurations(log(listOf(move(player = 1, notation = "Nf3"))))
        assertEquals("Nf3", durations.single().moveNotation)
    }

    // --- readImportText ---

    @Test
    fun `reads a normal file back verbatim`() {
        val text = """{"settings":{"player1Name":"Zoë"}}"""
        assertEquals(text, readImportText(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))))
    }

    @Test
    fun `decodes multi-byte characters split across chunk boundaries`() {
        // The reader works in 8 KB chunks; padding pushes an accented character onto the seam so a
        // per-chunk decode would corrupt it.
        val text = "é".repeat(9_000)
        assertEquals(text, readImportText(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))))
    }

    @Test(expected = ImportTooLargeException::class)
    fun `refuses a file past the limit`() {
        val big = ByteArray((MAX_IMPORT_TEXT_BYTES + 1).toInt()) { 'x'.code.toByte() }
        readImportText(ByteArrayInputStream(big))
    }

    @Test
    fun `counts bytes actually read rather than a declared size`() {
        val text = "y".repeat(100)
        assertEquals(text, readImportText(ByteArrayInputStream(text.toByteArray()), maxBytes = 100))
    }

    // --- copyImportArchive ---

    @Test
    fun `copies an archive through unchanged`() {
        val bytes = ByteArray(20_000) { (it % 251).toByte() }
        val out = java.io.ByteArrayOutputStream()
        copyImportArchive(ByteArrayInputStream(bytes), out)
        assertArrayEquals(bytes, out.toByteArray())
    }

    @Test(expected = ImportTooLargeException::class)
    fun `refuses an archive past the limit`() {
        val big = ByteArray(101)
        copyImportArchive(ByteArrayInputStream(big), java.io.ByteArrayOutputStream(), maxBytes = 100)
    }

    @Test
    fun `stops writing once the limit is passed`() {
        // The point of the bound is that the bytes never reach the disk, so the sink must not have
        // grown past the limit by the time it throws.
        val out = java.io.ByteArrayOutputStream()
        try {
            copyImportArchive(ByteArrayInputStream(ByteArray(50_000)), out, maxBytes = 8_192)
        } catch (_: ImportTooLargeException) {
        }
        assertTrue("wrote ${out.size()} bytes past an 8192 byte limit", out.size() <= 8_192)
    }

    // --- sanitizeImportedScoreboard ---
    //
    // The scoreboard list is keyed on game id and Compose throws on a duplicate key, so an import
    // carrying two games with the same id used to crash the screen on open. Unlike GameLog, whose
    // id is a Room primary key, nothing else makes these unique.

    private fun session(vararg ids: String) = ScoreboardSession(
        id = "session-from-the-file",
        games = ids.map { ScoreboardGame(id = it, result = "1-0") },
    )

    @Test
    fun `gives every imported scoreboard game a distinct id`() {
        val out = sanitizeImportedScoreboard(session("same", "same", "same"))
        assertEquals(3, out.games.size)
        assertEquals(3, out.games.map { it.id }.toSet().size)
    }

    @Test
    fun `does not keep the ids the file supplied`() {
        val out = sanitizeImportedScoreboard(session("a", "b"))
        assertTrue(out.games.none { it.id == "a" || it.id == "b" })
        assertNotEquals("session-from-the-file", out.id)
    }

    @Test
    fun `leaves everything else about the session alone`() {
        val incoming = ScoreboardSession(
            player1Name = "Ada",
            player2Name = "Bob",
            games = listOf(ScoreboardGame(id = "x", result = "0-1", timestamp = 42L)),
        )
        val out = sanitizeImportedScoreboard(incoming)
        assertEquals("Ada", out.player1Name)
        assertEquals("Bob", out.player2Name)
        assertEquals("0-1", out.games.single().result)
        // Timestamps are shown to the user as the game's time; only the key had to change.
        assertEquals(42L, out.games.single().timestamp)
    }

    @Test
    fun `handles an empty scoreboard`() {
        assertEquals(emptyList<ScoreboardGame>(), sanitizeImportedScoreboard(ScoreboardSession()).games)
    }
}
