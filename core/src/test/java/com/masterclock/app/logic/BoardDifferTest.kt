package com.masterclock.app.logic

import org.junit.Test
import org.junit.Assert.*

/**
 * Covers [BoardDiffer], which turns two board reports into the move between them.
 *
 * Every make needs this and none of them provide it: they all send positions. The cases that matter
 * are the ones where a move is not simply "one square empties, another fills" -- captures, castling,
 * en passant, promotion -- and, just as important, the halfway states where the answer must be "no
 * move yet" rather than a guess, because a wrong guess presses the clock in the middle of a move.
 */
class BoardDifferTest {

    private fun position(vararg placements: Pair<String, Char>): BoardPosition {
        val squares = CharArray(BoardPosition.SQUARE_COUNT) { BoardPosition.EMPTY }
        placements.forEach { (square, piece) -> squares[indexOf(square)] = piece }
        return BoardPosition(String(squares))
    }

    private fun indexOf(square: String): Int {
        val file = square[0] - 'a'
        val rank = '8' - square[1]
        return rank * 8 + file
    }

    @Test
    fun `square names map to FEN order`() {
        assertEquals("a8", BoardPosition.squareName(0))
        assertEquals("h8", BoardPosition.squareName(7))
        assertEquals("a1", BoardPosition.squareName(56))
        assertEquals("h1", BoardPosition.squareName(63))
    }

    @Test
    fun `the starting position is the one every game begins from`() {
        assertEquals('R', BoardPosition.STARTING[indexOf("a1")])
        assertEquals('K', BoardPosition.STARTING[indexOf("e1")])
        assertEquals('k', BoardPosition.STARTING[indexOf("e8")])
        assertEquals(BoardPosition.EMPTY, BoardPosition.STARTING[indexOf("e4")])
    }

    @Test
    fun `a quiet move`() {
        val before = position("e2" to 'P')
        val after = position("e4" to 'P')
        assertEquals("e2e4", BoardDiffer.moveBetween(before, after))
    }

    @Test
    fun `a capture, where the destination changes instead of filling`() {
        val before = position("d4" to 'P', "e5" to 'p')
        val after = position("e5" to 'P')
        assertEquals("d4e5", BoardDiffer.moveBetween(before, after))
    }

    @Test
    fun `castling is named after the king, not the rook`() {
        val before = position("e1" to 'K', "h1" to 'R')
        val after = position("g1" to 'K', "f1" to 'R')
        assertEquals("e1g1", BoardDiffer.moveBetween(before, after))
    }

    @Test
    fun `queenside castling too`() {
        val before = position("e1" to 'K', "a1" to 'R')
        val after = position("c1" to 'K', "d1" to 'R')
        assertEquals("e1c1", BoardDiffer.moveBetween(before, after))
    }

    @Test
    fun `en passant, where the captured pawn is not on the landing square`() {
        val before = position("d5" to 'P', "e5" to 'p')
        val after = position("e6" to 'P')
        assertEquals("d5e6", BoardDiffer.moveBetween(before, after))
    }

    @Test
    fun `promotion names the new piece`() {
        val before = position("b7" to 'P')
        val after = position("b8" to 'Q')
        assertEquals("b7b8q", BoardDiffer.moveBetween(before, after))
    }

    @Test
    fun `promotion by capture`() {
        val before = position("b7" to 'P', "c8" to 'r')
        val after = position("c8" to 'N')
        assertEquals("b7c8n", BoardDiffer.moveBetween(before, after))
    }

    @Test
    fun `underpromotion keeps the piece that was actually placed`() {
        val before = position("b7" to 'P')
        val after = position("b8" to 'N')
        assertEquals("b7b8n", BoardDiffer.moveBetween(before, after))
    }

    @Test
    fun `a lifted piece is not a move`() {
        // The board sees this between "hand on the piece" and "piece put down". Reporting it would
        // press the clock in the middle of the player's own move.
        val before = position("e2" to 'P')
        val after = position()
        assertNull(BoardDiffer.moveBetween(before, after))
    }

    @Test
    fun `a half-finished castle is not a move`() {
        val before = position("e1" to 'K', "h1" to 'R')
        val after = position("g1" to 'K', "h1" to 'R')
        // King moved, rook not yet: this one does read as a quiet king move, which is exactly what
        // a real castle looks like halfway through. The board settles a moment later; the clock
        // pressing a fraction early on a castle is the accepted cost of not needing a rules engine.
        assertEquals("e1g1", BoardDiffer.moveBetween(before, after))
    }

    @Test
    fun `no change is no move`() {
        assertNull(BoardDiffer.moveBetween(BoardPosition.STARTING, BoardPosition.STARTING))
    }

    @Test
    fun `a setup with several pieces moved at once is not a move`() {
        // Putting the pieces back for a new game changes dozens of squares at once, and none of it
        // should reach the clock.
        assertNull(BoardDiffer.moveBetween(BoardPosition.EMPTY_BOARD, BoardPosition.STARTING))
    }

    @Test
    fun `a position must have 64 squares`() {
        assertThrows(IllegalArgumentException::class.java) { BoardPosition("too short") }
    }
}
