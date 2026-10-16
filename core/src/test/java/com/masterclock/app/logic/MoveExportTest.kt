package com.masterclock.app.logic

import org.junit.Assert.*
import org.junit.Test

/**
 * Pins [generatePgn]: what it writes has to be read by other chess software, which the old export
 * -- two tags, no result, "???" for every move without notation -- was not.
 */
class MoveExportTest {

    private fun game(vararg notations: String?) = GameLog(
        settings = ChessClockSettings(),
        startTime = 0L,
        events = listOf(GameEvent(eventType = "START")) +
            notations.mapIndexed { i, n -> GameEvent(eventType = "MOVE", playerIndex = i % 2 + 1, moveNotation = n) } +
            GameEvent(eventType = "RESET"),
    )

    private fun movetext(pgn: String) = pgn.substringAfter("\n\n").trim()

    @Test
    fun `writes the seven tag roster in order`() {
        val names = generatePgn(game("e4")).lines().takeWhile { it.startsWith("[") }.map { it.substringAfter("[").substringBefore(" ") }
        assertEquals(listOf("Event", "Site", "Date", "Round", "White", "Black", "Result"), names)
    }

    @Test
    fun `numbers the moves and ends with the result`() {
        assertEquals("1. e4 e5 2. Nf3 *", movetext(generatePgn(game("e4", "e5", "Nf3"))))
    }

    @Test
    fun `a game played without a board says so instead of writing question marks`() {
        val text = movetext(generatePgn(game(null, null, null)))
        assertFalse(text.contains("?"))
        assertEquals("{No move notation was recorded} *", text)
    }

    @Test
    fun `stops at the first move it cannot write`() {
        assertEquals("1. e4 e5 {2 further moves were not recorded} *", movetext(generatePgn(game("e4", "e5", null, "Nc6"))))
    }

    @Test
    fun `a notation that would break the movetext counts as unrecorded`() {
        assertEquals("1. e4 {1 further move was not recorded} *", movetext(generatePgn(game("e4", "e5 {x}"))))
    }

    @Test
    fun `a game with no moves is just a result`() {
        assertEquals("*", movetext(generatePgn(game())))
    }

    @Test
    fun `keeps movetext lines under eighty characters`() {
        val long = generatePgn(game(*Array(120) { if (it % 2 == 0) "Nf3" else "Nf6" }))
        assertTrue(long.lines().all { it.length < 80 })
        assertTrue(movetext(long).endsWith("*"))
    }
}
