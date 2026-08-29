package com.masterclock.app.logic

import org.junit.Test
import org.junit.Assert.*

/**
 * Covers [BoardMoveTracker], which turns a stream of board reports into moves.
 *
 * The case that matters is a move arriving in pieces. A board does not wait for the player to
 * finish: it reports the piece lifted, then reports it put down, and on a DGT in update mode those
 * are two separate one-square messages. Comparing each report with the one before it sees a square
 * empty and then a square fill, and recognises neither -- which is exactly the bug these tests were
 * written to catch.
 */
class BoardMoveTrackerTest {

    private fun position(vararg placements: Pair<String, Char>): BoardPosition {
        val squares = CharArray(BoardPosition.SQUARE_COUNT) { BoardPosition.EMPTY }
        placements.forEach { (square, piece) -> squares[indexOf(square)] = piece }
        return BoardPosition(String(squares))
    }

    private fun indexOf(square: String): Int = ('8' - square[1]) * 8 + (square[0] - 'a')

    @Test
    fun `a move reported in two halves is still one move`() {
        val tracker = BoardMoveTracker()
        assertEquals(emptyList<String>(), tracker.onReport(BoardReport.Position(position("e2" to 'P'))))

        // Piece in hand: nothing to report yet.
        assertEquals(emptyList<String>(), tracker.onReport(BoardReport.Position(position())))
        // Put down: now the two halves add up.
        assertEquals(listOf("e2e4"), tracker.onReport(BoardReport.Position(position("e4" to 'P'))))
    }

    @Test
    fun `the same move is not reported twice`() {
        val tracker = BoardMoveTracker()
        tracker.onReport(BoardReport.Position(position("e2" to 'P')))
        assertEquals(listOf("e2e4"), tracker.onReport(BoardReport.Position(position("e4" to 'P'))))
        assertEquals(emptyList<String>(), tracker.onReport(BoardReport.Position(position("e4" to 'P'))))
    }

    @Test
    fun `single-square updates accumulate into a move`() {
        val tracker = BoardMoveTracker()
        tracker.onReport(BoardReport.Position(position("e2" to 'P')))

        assertEquals(emptyList<String>(), tracker.onReport(BoardReport.SquareChanged(indexOf("e2"), BoardPosition.EMPTY)))
        assertEquals(listOf("e2e4"), tracker.onReport(BoardReport.SquareChanged(indexOf("e4"), 'P')))
    }

    @Test
    fun `a square update before any full position is ignored`() {
        // A board in update mode says nothing about the other 63 squares, so there is no position
        // to apply the change to until a dump has arrived.
        val tracker = BoardMoveTracker()
        assertEquals(emptyList<String>(), tracker.onReport(BoardReport.SquareChanged(indexOf("e4"), 'P')))
    }

    @Test
    fun `a piece put back where it came from is not a move`() {
        val tracker = BoardMoveTracker()
        tracker.onReport(BoardReport.Position(position("e2" to 'P')))
        tracker.onReport(BoardReport.Position(position()))
        assertEquals(emptyList<String>(), tracker.onReport(BoardReport.Position(position("e2" to 'P'))))
    }

    @Test
    fun `a capture reported as lift, lift, place`() {
        // The capturer is lifted, the captured piece removed, then the capturer put down. Only the
        // final report completes a move.
        val tracker = BoardMoveTracker()
        tracker.onReport(BoardReport.Position(position("d4" to 'P', "e5" to 'p')))

        assertEquals(emptyList<String>(), tracker.onReport(BoardReport.Position(position("e5" to 'p'))))
        assertEquals(emptyList<String>(), tracker.onReport(BoardReport.Position(position())))
        assertEquals(listOf("d4e5"), tracker.onReport(BoardReport.Position(position("e5" to 'P'))))
    }

    @Test
    fun `reset forgets the position so a new game starts clean`() {
        val tracker = BoardMoveTracker()
        tracker.onReport(BoardReport.Position(position("e2" to 'P')))
        tracker.reset()
        // Without the reset this would read as a move from e2.
        assertEquals(emptyList<String>(), tracker.onReport(BoardReport.Position(position("e4" to 'P'))))
    }

    @Test
    fun `directly named moves pass straight through`() {
        val tracker = BoardMoveTracker()
        assertEquals(listOf("e2e4"), tracker.onReport(BoardReport.Moves(listOf("e2e4"))))
        assertEquals(emptyList<String>(), tracker.onReport(BoardReport.Ignored))
    }
}
