package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the chain makes of hands and of links that are not clean: a piece put back, knocked over,
 * changed for a queen; a message lost, doubled, cut, or buried in noise. A game played neatly over
 * a perfect wire shows none of it, and a real board is neither.
 *
 * The rule throughout is the tracker's own: say nothing rather than name a move that was not
 * played, and be able to hear the next one.
 */
class BoardRoughHandlingTest {

    private fun sq(name: String): Int = ('8' - name[1]) * 8 + (name[0] - 'a')

    /** A position after edits: "e2." empties e2, "e4P" puts a white pawn on e4. */
    private fun BoardPosition.with(vararg edits: String): BoardPosition {
        val squares = squares.toCharArray()
        edits.forEach { squares[sq(it.substring(0, 2))] = it[2] }
        return BoardPosition(String(squares))
    }

    private fun heard(make: BoardProtocol, chunks: List<ByteArray>): List<String> {
        val listener = BoardListener(make)
        return chunks.flatMap { listener.hear(it) }
    }

    /**
     * As a board that sends its whole position reports each of these in turn. `Any`, because Kotlin
     * allows no vararg of a value class, and a list at every call would bury what the tests say.
     */
    private fun seen(vararg positions: Any): List<String> =
        heard(ChessnutProtocol, positions.map { SimulatedBoard.frameOf(ChessnutProtocol, it as BoardPosition) })

    private val start = BoardPosition.STARTING
    private val e4 = start.with("e2.", "e4P")
    private val e4e5 = e4.with("e7.", "e5p")

    // ---- Hands

    @Test
    fun `a piece lifted and put back is not a move`() {
        assertEquals(emptyList<String>(), seen(start, start.with("e2."), start))
        assertEquals(listOf("e2e4"), seen(start, start.with("g1."), start, start.with("e2."), e4))
    }

    @Test
    fun `a piece off the board while another is played does not name the move, and the next is heard`() {
        // A knight picked up to be straightened as the pawn is played, and put back after.
        assertEquals(
            listOf("e2e4", "e7e5"),
            seen(start, start.with("g1."), start.with("g1.", "e2."), start.with("g1.", "e2.", "e4P"), e4, e4.with("e7."), e4e5),
        )
        // Two pawns in the hand at once: it was read as d2e4, from whichever square came first.
        assertEquals(
            listOf("e2e4", "e7e5"),
            seen(start, start.with("e2.", "d2."), start.with("e2.", "d2.", "e4P"), e4, e4.with("e7."), e4e5),
        )
    }

    @Test
    fun `an en passant is still read, and only an en passant`() {
        val before = BoardPosition("....k..." + "........".repeat(2) + "...pP..." + "........".repeat(3) + "....K...")
        // The capturing pawn lands, then the captured one comes off -- or both leave before it lands.
        assertEquals(listOf("e5d6"), seen(before, before.with("e5."), before.with("e5.", "d6P"), before.with("e5.", "d6P", "d5.")))
        assertEquals(listOf("e5d6"), seen(before, before.with("e5."), before.with("e5.", "d5."), before.with("e5.", "d5.", "d6P")))
        // Straight ahead with the pawn beside it gone is not one.
        assertEquals(emptyList<String>(), seen(before, before.with("e5.", "d5."), before.with("e5.", "d5.", "e6P")))
    }

    private val promoting = BoardPosition(".......k" + "....P..." + "........".repeat(5) + "....K...")

    @Test
    fun `a pawn becomes a queen whichever way the hand does it, and the reply is heard`() {
        val queened = promoting.with("e7.", "e8Q")
        val reply = arrayOf(queened.with("h8."), queened.with("h8.", "h7k"))
        // The queen put straight on the last rank.
        assertEquals(listOf("e7e8q", "h8h7"), seen(promoting, promoting.with("e7."), queened, *reply))
        // The pawn put there first, then changed: the move is the pawn's, and the game goes on. No
        // move after a promotion made this way was read until the pieces were set up again.
        assertEquals(
            listOf("e7e8", "h8h7"),
            seen(promoting, promoting.with("e7."), promoting.with("e7.", "e8P"), promoting.with("e7."), queened, *reply),
        )
        // The queen put down before the pawn is taken off.
        assertEquals(listOf("e7e8q", "h8h7"), seen(promoting, promoting.with("e8Q"), queened, *reply))
    }

    @Test
    fun `a piece slid across a square is read as two moves`() {
        // To the tracker, which compares positions and has no clock: e2 to e4 with the pawn resting
        // on e3 is e2e3 and then e3e4. They are joined where there is one, in recordBoardMove, by
        // BoardDiffer.slideOf -- the turn passed twice.
        assertEquals(
            listOf("e2e3", "e3e4"),
            seen(start, start.with("e2."), start.with("e2.", "e3P"), start.with("e2."), e4),
        )
    }

    @Test
    fun `a move taken back is heard as the move back, and the one played instead after it`() {
        // In the middle of a game; at its first move the board is the starting position again, and
        // that is a new game. The clock goes back to whoever takes their move back, which is theirs.
        val d4 = e4e5.with("d2.", "d4P")
        assertEquals(
            listOf("e7e5", "d2d4", "d4d2", "g1f3"),
            seen(e4, e4.with("e7."), e4e5, e4e5.with("d2."), d4, d4.with("d4."), e4e5, e4e5.with("g1."), e4e5.with("g1.", "f3N")),
        )
    }

    @Test
    fun `a board reconnected in the middle of a game hears the next move`() {
        assertEquals(listOf("e7e5"), seen(e4, e4.with("e7."), e4e5))
    }

    // ---- DGT, one square at a time and nothing to check a message by

    private val dump = SimulatedBoard.frameOf(DgtProtocol, start)
    private fun u(edit: String) = SimulatedBoard.dgtSquare(sq(edit.substring(0, 2)), edit[2])
    private fun dgt(vararg messages: ByteArray) = heard(DgtProtocol, messages.toList())

    @Test
    fun `a DGT message doubled, cut in two or sent through noise is the same message`() {
        assertEquals(listOf("e2e4", "e7e5"), dgt(dump, u("e2."), u("e2."), u("e4P"), u("e4P"), u("e7."), u("e5p")))
        assertEquals(listOf("e2e4"), dgt(dump, byteArrayOf(0x01, 0x7F, 0x33), u("e2."), byteArrayOf(0x00), u("e4P")))
        assertEquals(listOf("e2e4"), dgt(dump, u("e2.").copyOfRange(0, 2), u("e2.").copyOfRange(2, 5), u("e4P")))
    }

    @Test
    fun `a DGT square lost names no move, and the next dump puts the board right`() {
        // The lift lost: a pawn on e2 and on e4. It was read as e7e4 and then g1e5.
        assertEquals(emptyList<String>(), dgt(dump, u("e4P"), u("e7."), u("e5p"), u("g1."), u("f3N")))
        // The put-down lost: a pawn nowhere.
        assertEquals(emptyList<String>(), dgt(dump, u("e2."), u("e7."), u("e5p")))
        // Update mode never restates the board, so only a dump recovers it -- and one does.
        assertEquals(
            listOf("e2e4", "e7e5"),
            dgt(dump, u("e4P"), SimulatedBoard.frameOf(DgtProtocol, e4), u("e7."), u("e5p")),
        )
    }

    @Test
    fun `a DGT message that lost its end does not take the next ones for its body`() {
        // Two bytes of a message, then whole ones: the second message's id sits where a length
        // would, and read as one it swallowed everything behind it.
        assertEquals(
            listOf("e2e4", "e7e5"),
            dgt(dump, u("e2.").copyOfRange(0, 2), u("e4P"), SimulatedBoard.frameOf(DgtProtocol, e4), u("e7."), u("e5p")),
        )
    }

    // ---- Millennium, checksummed and in slices

    private fun m(position: BoardPosition) = SimulatedBoard.frameOf(MillenniumProtocol, position)
    private fun millennium(vararg chunks: ByteArray) = heard(MillenniumProtocol, chunks.toList())

    @Test
    fun `a ChessLink reply corrupt, repeated or after noise changes nothing`() {
        val corrupt = m(start.with("e2.")).also { it[10] = (it[10].toInt() xor 0x01).toByte() }
        assertEquals(listOf("e2e4"), millennium(m(start), corrupt, m(e4)))
        assertEquals(listOf("e2e4"), millennium(m(start), byteArrayOf(0x55, 0x00, 0x7A), m(start.with("e2.")), m(e4)))
        // As it really talks: the same position at every scan.
        assertEquals(listOf("e2e4"), millennium(m(start), *Array(20) { m(e4) }))
    }

    @Test
    fun `a ChessLink that lost one notification is heard again at the next reply`() {
        // Thirty bytes of a reply and then whole ones. The stream was cut 67 bytes at a time from
        // there on, from the middle of one reply to the middle of the next, and the board said
        // nothing more for as long as it stayed connected.
        assertEquals(listOf("e2e4", "e7e5"), millennium(m(start), m(start.with("e2.")).copyOfRange(0, 30), m(e4), m(e4), m(e4e5)))
        // And lost from the middle, in the slices Bluetooth hands over.
        val slices = listOf(m(start), m(e4), m(e4), m(e4e5)).flatMap { SimulatedBoard.chunksOf(MillenniumProtocol, it) }
        assertEquals(listOf("e2e4", "e7e5"), heard(MillenniumProtocol, slices.filterIndexed { index, _ -> index != 5 }))
    }

    // ---- Certabo, as its drivers read a line

    @Test
    fun `a Certabo game is heard with a mark in front of each line`() {
        val lines = SimulatedBoard.framesOf(CertaboProtocol).map { ":".toByteArray() + it }
        assertEquals(SimulatedBoard.DEMO_GAME, heard(CertaboProtocol, lines.flatMap { SimulatedBoard.chunksOf(CertaboProtocol, it) }))
    }
}
