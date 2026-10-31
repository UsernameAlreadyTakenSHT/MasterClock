package com.masterclock.app.logic

import java.util.Locale
import kotlin.random.Random

/**
 * The starting positions the variant generator offers. They live here, beside the clock's logic,
 * rather than with their screen, so that what they promise can be tested: the bishops on opposite
 * colours, the king between the rooks, one setup for both sides or one each.
 */
enum class ChessVariant(
    val label: String,
    val isSymmetric: Boolean,
    val kingBetweenRooks: Boolean,
    val oppositeBishops: Boolean,
) {
    CHESS960("Chess960", true, true, true),
    SHUFFLE("Shuffle", true, false, false),
    TRANSCENDENTAL("Transcendental", false, false, true),
    CHESS2880("Chess2880", true, false, true),
    DOUBLE_CHESS960("Double 960", false, true, true),
}

/**
 * One side's back rank, files a to h, as piece letters. Square 0 is a1, which is dark, so the even
 * squares are the dark ones. [random] is there for the tests; the screen draws from the default.
 */
fun generateChessPosition(v: ChessVariant, random: Random = Random): List<String> {
    if (!v.oppositeBishops) return mutableListOf("R", "N", "B", "Q", "K", "B", "N", "R").apply { shuffle(random) }
    val b1 = listOf(0, 2, 4, 6).random(random)
    val b2 = listOf(1, 3, 5, 7).random(random)
    val pos = MutableList(8) { "" }
    pos[b1] = "B"
    pos[b2] = "B"
    val remaining = (0..7).filter { pos[it] == "" }
    if (v.kingBetweenRooks) {
        // Three of the six squares left, in order: rook, king, rook. The other three take the
        // knights and the queen.
        val rkr = remaining.shuffled(random).take(3).sorted()
        val others = mutableListOf("N", "N", "Q").apply { shuffle(random) }
        var next = 0
        for (i in remaining) {
            pos[i] = when (i) {
                rkr[0], rkr[2] -> "R"
                rkr[1] -> "K"
                else -> others[next++]
            }
        }
    } else {
        val others = mutableListOf("N", "N", "Q", "R", "R", "K").apply { shuffle(random) }
        remaining.forEachIndexed { index, square -> pos[square] = others[index] }
    }
    return pos
}

/**
 * The starting FEN. [castling] is for a variant whose king starts between its rooks; the others --
 * Shuffle, Transcendental, 2880 -- have no castling, and "KQkq" claimed it, so an engine given the
 * position would play it. With two rooks a side, KQkq reads as Chess960's castling (X-FEN).
 */
fun generateFen(white: List<String>, black: List<String>, castling: Boolean = true): String {
    val b = black.joinToString("").lowercase(Locale.US)
    val w = white.joinToString("")
    return "$b/pppppppp/8/8/8/8/PPPPPPPP/$w w ${if (castling) "KQkq" else "-"} - 0 1"
}
