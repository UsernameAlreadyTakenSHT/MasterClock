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
