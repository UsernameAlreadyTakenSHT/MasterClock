package com.masterclock.app.ui.screens

import com.masterclock.app.R

/*
 * What TalkBack says for a symbol a note offers, instead of the symbol: "!?" read as two marks, the
 * Informant's signs not at all, and a piece of a board or a drawing as the letter it is stored as.
 */

/** A text note's annotations, by the characters they put in. */
internal val SYMBOL_NAMES: Map<String, Int> = mapOf(
    "!!" to R.string.tools_sym_brilliant,
    "!" to R.string.tools_sym_good,
    "!?" to R.string.tools_sym_interesting,
    "?!" to R.string.tools_sym_dubious,
    "?" to R.string.tools_sym_mistake,
    "??" to R.string.tools_sym_blunder,
    "+−" to R.string.tools_sym_white_winning,
    "±" to R.string.tools_sym_white_clearly,
    "⩲" to R.string.tools_sym_white_slightly,
    "=" to R.string.tools_sym_equal,
    "⩱" to R.string.tools_sym_black_slightly,
    "∓" to R.string.tools_sym_black_clearly,
    "−+" to R.string.tools_sym_black_winning,
    "∞" to R.string.tools_sym_unclear,
    "+" to R.string.tools_sym_check,
    "#" to R.string.tools_sym_mate,
    "0-0" to R.string.tools_sym_castle_short,
    "0-0-0" to R.string.tools_sym_castle_long,
    "x" to R.string.tools_sym_capture,
    "-" to R.string.tools_sym_move_to,
    "□" to R.string.tools_sym_only_move,
    "N" to R.string.tools_sym_novelty,
    "∆" to R.string.tools_sym_idea,
    "→" to R.string.tools_sym_attack,
    "↑" to R.string.tools_sym_initiative,
    "⇆" to R.string.tools_sym_counterplay,
    "⨀" to R.string.tools_sym_zugzwang,
    "⊕" to R.string.tools_sym_time_trouble,
    "1-0" to R.string.tools_sym_white_won,
    "2-0" to R.string.tools_sym_white_won,
    "½-½" to R.string.tools_sym_draw,
    "1-1" to R.string.tools_sym_draw,
    "0-1" to R.string.tools_sym_black_won,
    "0-2" to R.string.tools_sym_black_won,
)

/** The pieces, by their Unicode character in a text note and their letter on a board or a drawing. */
internal val PIECE_NAMES: Map<String, Int> = mapOf(
    "♔" to R.string.tools_piece_white_king,
    "K" to R.string.tools_piece_white_king,
    "♕" to R.string.tools_piece_white_queen,
    "Q" to R.string.tools_piece_white_queen,
    "♖" to R.string.tools_piece_white_rook,
    "R" to R.string.tools_piece_white_rook,
    "♗" to R.string.tools_piece_white_bishop,
    "B" to R.string.tools_piece_white_bishop,
    "♘" to R.string.tools_piece_white_knight,
    "♙" to R.string.tools_piece_white_pawn,
    "P" to R.string.tools_piece_white_pawn,
    "♚" to R.string.tools_piece_black_king,
    "k" to R.string.tools_piece_black_king,
    "♛" to R.string.tools_piece_black_queen,
    "q" to R.string.tools_piece_black_queen,
    "♜" to R.string.tools_piece_black_rook,
    "r" to R.string.tools_piece_black_rook,
    "♝" to R.string.tools_piece_black_bishop,
    "b" to R.string.tools_piece_black_bishop,
    "♞" to R.string.tools_piece_black_knight,
    "n" to R.string.tools_piece_black_knight,
    "♟" to R.string.tools_piece_black_pawn,
    "p" to R.string.tools_piece_black_pawn,
    "⛀" to R.string.tools_piece_white_man,
    "M" to R.string.tools_piece_white_man,
    "⛁" to R.string.tools_piece_white_dame,
    "D" to R.string.tools_piece_white_dame,
    "⛂" to R.string.tools_piece_black_man,
    "m" to R.string.tools_piece_black_man,
    "⛃" to R.string.tools_piece_black_dame,
    "d" to R.string.tools_piece_black_dame,
    "N" to R.string.tools_piece_white_knight,
)
