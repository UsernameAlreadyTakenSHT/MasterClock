package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteInsertsTest {

    @Test
    fun `a chessboard's squares are named from White's side`() {
        assertEquals("a8", squareName(SquareBoard.CHESS, 0, 0))
        assertEquals("h1", squareName(SquareBoard.CHESS, 7, 7))
        assertEquals("e4", squareName(SquareBoard.CHESS, 4, 4))
        assertNull(squareName(SquareBoard.CHESS, 8, 0))
    }

    @Test
    fun `a draughts board numbers its dark squares row by row`() {
        // International: 1 to 5 along the top row, 46 to 50 along the bottom one.
        assertEquals("1", squareName(SquareBoard.DRAUGHTS_10, 0, 1))
        assertEquals("5", squareName(SquareBoard.DRAUGHTS_10, 0, 9))
        assertEquals("6", squareName(SquareBoard.DRAUGHTS_10, 1, 0))
        assertEquals("50", squareName(SquareBoard.DRAUGHTS_10, 9, 8))
        assertNull(squareName(SquareBoard.DRAUGHTS_10, 0, 0))
        // Eight by eight: 1 to 32.
        assertEquals("1", squareName(SquareBoard.DRAUGHTS_8, 0, 1))
        assertEquals("32", squareName(SquareBoard.DRAUGHTS_8, 7, 6))
        val all = (0 until 10).flatMap { r -> (0 until 10).mapNotNull { c -> squareName(SquareBoard.DRAUGHTS_10, r, c) } }
        assertEquals((1..50).map { it.toString() }, all)
    }
}
