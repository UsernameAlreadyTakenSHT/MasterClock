package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Covers [generateChessPosition] and [generateFen]: what each variant promises of its setup. */
class ChessVariantsTest {

    private fun draws(v: ChessVariant, n: Int = 4000) = Random(960).let { r -> List(n) { generateChessPosition(v, r) } }

    private fun assertBishopsOnOppositeColours(pos: List<String>) {
        val squares = pos.indices.filter { pos[it] == "B" }
        assertEquals(2, squares.size)
        assertNotEquals(squares[0] % 2, squares[1] % 2)
    }

    private fun assertKingBetweenRooks(pos: List<String>) {
        val rooks = pos.indices.filter { pos[it] == "R" }
        val king = pos.indexOf("K")
        assertTrue(pos.joinToString(""), rooks[0] < king && king < rooks[1])
    }

    @Test
    fun `every draw is a full back rank`() {
        ChessVariant.entries.forEach { v ->
            draws(v, 200).forEach { assertEquals(listOf("B", "B", "K", "N", "N", "Q", "R", "R"), it.sorted()) }
        }
    }

    @Test
    fun `Chess960 keeps the bishops apart and the king between the rooks`() {
        draws(ChessVariant.CHESS960).forEach { assertBishopsOnOppositeColours(it); assertKingBetweenRooks(it) }
        draws(ChessVariant.DOUBLE_CHESS960).forEach { assertBishopsOnOppositeColours(it); assertKingBetweenRooks(it) }
    }

    @Test
    fun `Transcendental and Chess2880 keep the bishops apart and nothing else`() {
        listOf(ChessVariant.TRANSCENDENTAL, ChessVariant.CHESS2880).forEach { v ->
            val all = draws(v)
            all.forEach { assertBishopsOnOppositeColours(it) }
            assertTrue(all.any { pos -> pos.indexOf("K") < pos.indexOf("R") })
        }
    }

    @Test
    fun `all 960 positions come up, the standard one among them`() {
        val seen = draws(ChessVariant.CHESS960, 40_000).mapTo(HashSet()) { it.joinToString("") }
        assertEquals(960, seen.size)
        assertTrue("RNBQKBNR" in seen)
        assertTrue("BBQNNRKR" in seen)
        assertTrue("RKRNNQBB" in seen)
    }

    @Test
    fun `the FEN has black's rank first, and castling only where the king starts between the rooks`() {
        val white = "RNBQKBNR".map { it.toString() }
        assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", generateFen(white, white))
        assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w - - 0 1", generateFen(white, white, castling = false))
    }
}
