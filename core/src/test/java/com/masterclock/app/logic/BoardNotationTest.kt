package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a board's move is written as, and how a castle reported rook first is renamed in the log. */
class BoardNotationTest {

    private fun index(square: String) = ('8' - square[1]) * 8 + (square[0] - 'a')

    private fun position(vararg pieces: Pair<String, Char>): BoardPosition {
        val squares = CharArray(BoardPosition.SQUARE_COUNT) { BoardPosition.EMPTY }
        for ((square, piece) in pieces) squares[index(square)] = piece
        return BoardPosition(String(squares))
    }

    private val kings = arrayOf("e1" to 'K', "e8" to 'k')

    @Test
    fun `a promotion names its piece`() {
        val before = position(*kings, "e7" to 'P')
        assertEquals("e7e8n", BoardDiffer.moveBetween(before, position(*kings, "e8" to 'N')))
    }

    @Test
    fun `a promotion to a piece the board cannot name is a queen`() {
        // The spare queen of a Certabo set, whose chip was never calibrated. It was "e7e8?".
        val before = position(*kings, "e7" to 'P')
        assertEquals("e7e8q", BoardDiffer.moveBetween(before, position(*kings, "e8" to BoardPosition.UNKNOWN_PIECE)))
        val capture = position(*kings, "e7" to 'P', "d8" to 'r')
        assertEquals("e7d8q", BoardDiffer.moveBetween(capture, position(*kings, "d8" to BoardPosition.UNKNOWN_PIECE)))
    }

    @Test
    fun `a pawn read as something else off the last rank is no promotion`() {
        // A chip misread on an ordinary pawn move wrote "e2e4?".
        val before = position(*kings, "e2" to 'P')
        assertEquals("e2e4", BoardDiffer.moveBetween(before, position(*kings, "e4" to BoardPosition.UNKNOWN_PIECE)))
    }

    @Test
    fun `en passant is still read, and so is a draughts capture`() {
        val before = position(*kings, "e5" to 'P', "d5" to 'p')
        assertEquals("e5d6", BoardDiffer.moveBetween(before, position(*kings, "d6" to 'P')))
        val draughts = position("c3" to 'm', "d4" to 'M')
        assertEquals("c3e5", BoardDiffer.moveBetween(draughts, position("e5" to 'm')))
    }

    @Test
    fun `only a king jumping its rook completes a castle`() {
        assertTrue(BoardDiffer.completesCastle("h1f1", "e1g1"))
        assertTrue(BoardDiffer.completesCastle("a1d1", "e1c1"))
        assertTrue(BoardDiffer.completesCastle("h8f8", "e8g8"))
        assertTrue(BoardDiffer.completesCastle("a8d8", "e8c8"))
        assertFalse(BoardDiffer.completesCastle(null, "e1g1"))
        assertFalse(BoardDiffer.completesCastle("a1d1", "e1g1"))
        assertFalse(BoardDiffer.completesCastle("h1f1", "e1f1"))
        assertFalse(BoardDiffer.completesCastle("e1g1", "h1f1"))
    }

    @Test
    fun `the rook's move in the log is renamed after the king`() {
        val events = listOf(
            GameEvent(eventType = "MOVE", playerIndex = 1, moveNotation = "h1f1"),
            GameEvent(eventType = "MOVE", playerIndex = 2, moveNotation = "e7e6"),
        )
        val renamed = events.withLastNotationRenamed("h1f1", "e1g1")
        assertEquals(listOf("e1g1", "e7e6"), renamed.map { it.moveNotation })

        val held = listOf(GameEvent(eventType = "BOARD_MOVE", playerIndex = 1, moveNotation = "h1f1", detail = "Move from board: h1f1"))
        assertEquals("Move from board: e1g1", held.withLastNotationRenamed("h1f1", "e1g1").single().detail)
        assertEquals(events, events.withLastNotationRenamed("a1d1", "e1c1"))
    }
}
