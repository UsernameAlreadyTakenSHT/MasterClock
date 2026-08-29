package com.masterclock.app.logic

import org.junit.Test
import org.junit.Assert.*

/**
 * Covers the Millennium ChessLink decoder.
 *
 * Two things set this make apart and both are tested here: every byte carries an odd parity bit
 * that must come off before it means anything, and every message ends with a checksum -- the only
 * make of the four where a corrupt frame can be detected rather than silently decoded.
 *
 * The square ordering is reversed on both axes, not one, which is the mirror trap in its worst
 * form: getting a single axis wrong is often visible, getting both wrong produces a board rotated
 * 180 degrees that still looks like a game.
 */
class MillenniumProtocolTest {

    private fun boardReply(position: BoardPosition): ByteArray {
        val body = CharArray(BoardPosition.SQUARE_COUNT)
        for (replyIndex in 0 until BoardPosition.SQUARE_COUNT) {
            val rankFromBottom = replyIndex / 8
            val fileFromH = replyIndex % 8
            val boardIndex = (7 - rankFromBottom) * 8 + (7 - fileFromH)
            val piece = position[boardIndex]
            body[replyIndex] = if (piece == BoardPosition.EMPTY) '.' else piece
        }
        return framed("s" + String(body))
    }

    /** Appends the checksum and applies odd parity, as the board does. */
    private fun framed(text: String): ByteArray {
        val checksum = text.fold(0) { acc, c -> acc xor c.code } and 0xFF
        return (text + "%02X".format(checksum)).map { c ->
            val value = c.code and 0x7F
            if (Integer.bitCount(value) % 2 == 0) (value or 0x80).toByte() else value.toByte()
        }.toByteArray()
    }

    private fun indexOf(square: String): Int = ('8' - square[1]) * 8 + (square[0] - 'a')

    @Test
    fun `the starting position survives a round trip`() {
        val report = MillenniumProtocol.decode(boardReply(BoardPosition.STARTING))
        assertEquals(BoardReport.Position(BoardPosition.STARTING), report)
    }

    @Test
    fun `both axes are reversed, not one`() {
        // A lone rook on a8, the corner furthest from where the reply starts. If only the file were
        // reversed it would land on h8; if only the rank, on a1; if neither, on h1.
        val squares = CharArray(BoardPosition.SQUARE_COUNT) { BoardPosition.EMPTY }
        squares[indexOf("a8")] = 'r'
        val position = BoardPosition(String(squares))

        val report = MillenniumProtocol.decode(boardReply(position)) as BoardReport.Position
        assertEquals('r', report.position[indexOf("a8")])
        assertEquals(BoardPosition.EMPTY, report.position[indexOf("h8")])
        assertEquals(BoardPosition.EMPTY, report.position[indexOf("a1")])
        assertEquals(BoardPosition.EMPTY, report.position[indexOf("h1")])
    }

    @Test
    fun `a corrupt frame is rejected by its own checksum`() {
        val reply = boardReply(BoardPosition.STARTING)
        // Flip a bit in a square, leaving the parity plausible but the checksum wrong.
        reply[10] = MillenniumProtocolTestHelper.withOddParity('Q')
        assertEquals(BoardReport.Ignored, MillenniumProtocol.decode(reply))
    }

    @Test
    fun `parity is stripped before the bytes are read`() {
        val reply = boardReply(BoardPosition.STARTING)
        // Every byte should have its top bit used for parity, so at least one must carry it.
        assertTrue(reply.any { it.toInt() and 0x80 != 0 })
        // And decoding still yields the position, which is only possible if it is masked off.
        assertTrue(MillenniumProtocol.decode(reply) is BoardReport.Position)
    }

    @Test
    fun `other replies are not positions`() {
        assertEquals(BoardReport.Ignored, MillenniumProtocol.decode(framed("v100")))
        assertEquals(BoardReport.Ignored, MillenniumProtocol.decode(byteArrayOf()))
    }

    @Test
    fun `framing uses the length each reply letter declares`() {
        val framing = MillenniumProtocol.framing!!
        assertEquals(67, framing.messageLength(boardReply(BoardPosition.STARTING)))
        assertEquals(StreamFraming.RESYNC, framing.messageLength(byteArrayOf(0x00)))
    }

    @Test
    fun `a reply split across reads is reassembled`() {
        val assembler = StreamAssembler(MillenniumProtocol.framing!!)
        val reply = boardReply(BoardPosition.STARTING)

        assertEquals(emptyList<ByteArray>(), assembler.offer(reply.copyOfRange(0, 30)))
        val done = assembler.offer(reply.copyOfRange(30, reply.size))
        assertEquals(1, done.size)
        assertArrayEquals(reply, done.single())
    }

    @Test
    fun `the init command carries its own checksum and parity`() {
        val command = MillenniumProtocol.initCommand!!
        val text = command.map { (it.toInt() and 0x7F).toChar() }.joinToString("")
        assertEquals("S53", text)
        assertTrue(command.all { Integer.bitCount(it.toInt() and 0xFF) % 2 == 1 })
    }

    @Test
    fun `a ChessLink is recognised by name`() {
        assertSame(MillenniumProtocol, BoardProtocols.forDeviceName("MILLENNIUM CHESS"))
        assertSame(MillenniumProtocol, BoardProtocols.forDeviceName("ChessLink"))
    }
}

private object MillenniumProtocolTestHelper {
    fun withOddParity(c: Char): Byte {
        val value = c.code and 0x7F
        return if (Integer.bitCount(value) % 2 == 0) (value or 0x80).toByte() else value.toByte()
    }
}
