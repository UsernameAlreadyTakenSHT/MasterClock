package com.masterclock.app.logic

/**
 * What a text note's Insert panel offers: characters, all of them, since a text field holds
 * nothing else. The pieces the board note draws are pictures, which cannot go into one; these are
 * the same pieces as Unicode has them, and the screen leaves out any the phone's font cannot draw.
 */
object NoteInserts {
    val CHESS_PIECES = listOf("♔", "♕", "♖", "♗", "♘", "♙", "♚", "♛", "♜", "♝", "♞", "♟")

    /** A man and a king, white then black. Fonts often lack them; see the screen. */
    val DRAUGHTS_PIECES = listOf("⛀", "⛁", "⛂", "⛃")

    /** The marks a move is given in the books, best to worst. */
    val MOVE_QUALITY = listOf("!!", "!", "!?", "?!", "?", "??")

    /** Who stands better, from White's side to Black's. */
    val EVALUATION = listOf("+−", "±", "⩲", "=", "⩱", "∓", "−+", "∞")

    /**
     * Check and mate, castling, a capture and a move as draughts writes them, the Informant's signs
     * -- only move, novelty, with the idea, attack, initiative, counterplay, zugzwang, time trouble --
     * and the results of both games.
     */
    val OTHER = listOf(
        "+", "#", "0-0", "0-0-0", "x", "-",
        "□", "N", "∆", "→", "↑", "⇆", "⨀", "⊕",
        "1-0", "½-½", "0-1", "2-0", "1-1", "0-2",
    )
}

/** The boards whose squares the Insert panel names. */
enum class SquareBoard(val side: Int) { CHESS(8), DRAUGHTS_10(10), DRAUGHTS_8(8) }

/**
 * The name of the square at [row] from the top and [column] from the left of [board], as seen from
 * White's or the first player's side: "e4" on a chessboard; on a draughts board the number of a
 * dark square, 1 at the top left and counting along each row, which is how draughts writes its
 * moves; null for a light draughts square, which nothing is ever played on.
 */
fun squareName(board: SquareBoard, row: Int, column: Int): String? {
    if (row !in 0 until board.side || column !in 0 until board.side) return null
    return when (board) {
        SquareBoard.CHESS -> "${'a' + column}${board.side - row}"
        SquareBoard.DRAUGHTS_10, SquareBoard.DRAUGHTS_8 ->
            if ((row + column) % 2 == 1) (row * board.side / 2 + column / 2 + 1).toString() else null
    }
}

/** Whether the square at [row], [column] is a dark one, as a board draws it. */
fun isDarkSquare(row: Int, column: Int): Boolean = (row + column) % 2 == 1
