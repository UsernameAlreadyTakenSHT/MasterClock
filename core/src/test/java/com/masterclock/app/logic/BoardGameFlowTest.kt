package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Test

/** Moves as a board reports them, one square at a time: a piece lifted, a piece put down. */
class BoardGameFlowTest {

    private var tracker = BoardMoveTracker()
    private var board = BoardPosition.STARTING.squares.toCharArray()

    private fun index(square: String) = ('8' - square[1]) * 8 + (square[0] - 'a')

    private fun report(): List<String> = tracker.onReport(BoardReport.Position(BoardPosition(String(board))))

    private fun lift(square: String): List<String> {
        board[index(square)] = BoardPosition.EMPTY
        return report()
    }

    private fun put(square: String, piece: Char): List<String> {
        board[index(square)] = piece
        return report()
    }

    private fun play(from: String, to: String): List<String> {
        val piece = board[index(from)]
        lift(from)
        return put(to, piece)
    }

    /** The whole board in one report, as a connection or a set-up gives it. */
    private fun show(position: BoardPosition): List<String> {
        board = position.squares.toCharArray()
        return report()
    }

    /** A new connection to a board with nothing between either side's kings and rooks. */
    private fun connectReadyToCastle() {
        tracker = BoardMoveTracker()
        board = ("r...k..r" + "pppppppp" + "........" + "........" + "........" + "........" + "PPPPPPPP" + "R...K..R").toCharArray()
        report()
    }

    @Test
    fun `castling king first is one move`() {
        connectReadyToCastle()
        assertEquals(listOf("e1g1"), play("e1", "g1"))
        assertEquals(emptyList<String>(), play("h1", "f1"))
    }

    @Test
    fun `castling queenside and with Black too`() {
        connectReadyToCastle()
        assertEquals(listOf("e1c1"), play("e1", "c1"))
        assertEquals(emptyList<String>(), play("a1", "d1"))
        assertEquals(listOf("e8g8"), play("e8", "g8"))
        assertEquals(emptyList<String>(), play("h8", "f8"))
    }

    @Test
    fun `castling rook first is one move`() {
        connectReadyToCastle()
        assertEquals(listOf("h1f1"), play("h1", "f1"))
        assertEquals(emptyList<String>(), play("e1", "g1"))
    }

    @Test
    fun `the move after a castle is read`() {
        connectReadyToCastle()
        play("e1", "g1")
        play("h1", "f1")
        assertEquals(listOf("e7e5"), play("e7", "e5"))
    }

    @Test
    fun `a rook that moves on its own stays a move`() {
        connectReadyToCastle()
        assertEquals(listOf("h1f1"), play("h1", "f1"))
        assertEquals(listOf("e7e6"), play("e7", "e6"))
        assertEquals(listOf("f1f3"), play("f1", "f3").ifEmpty { listOf("not read") })
    }

    @Test
    fun `setting the pieces up again starts a new game`() {
        report()
        play("e2", "e4")
        play("e7", "e5")

        assertEquals(emptyList<String>(), show(BoardPosition.STARTING))

        assertEquals(listOf("d2d4"), play("d2", "d4"))
    }

    @Test
    fun `a board connected empty reads the game once it is set up`() {
        show(BoardPosition.EMPTY_BOARD)
        show(BoardPosition.STARTING)
        assertEquals(listOf("e2e4"), play("e2", "e4"))
    }

    @Test
    fun `en passant capturer first leaves the game readable`() {
        report()
        play("e2", "e4")
        play("a7", "a6")
        play("e4", "e5")
        play("d7", "d5")

        assertEquals(listOf("e5d6"), play("e5", "d6"))
        assertEquals(emptyList<String>(), lift("d5"))

        // A capture next, which the pawn left on the baseline used to stop for good.
        lift("d6")
        lift("e7")
        assertEquals(listOf("d6e7"), put("e7", 'P'))
    }
}
