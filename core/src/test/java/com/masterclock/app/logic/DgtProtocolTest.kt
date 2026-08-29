package com.masterclock.app.logic

import org.junit.Test
import org.junit.Assert.*

/**
 * Covers the DGT decoder and, just as importantly, its framing.
 *
 * A DGT is reached over a serial line, where messages have no boundaries of their own. A 67-byte
 * board dump at 9600 baud does not arrive in one read, and two field updates can arrive in one --
 * so the reassembly is as much a part of this make as the piece table.
 */
class DgtProtocolTest {

    private fun boardDump(position: BoardPosition): ByteArray {
        val body = ByteArray(BoardPosition.SQUARE_COUNT) { codeOf(position[it]) }
        return message(0x86.toByte(), body)
    }

    private fun fieldUpdate(square: Int, piece: Char): ByteArray =
        message(0x8E.toByte(), byteArrayOf(square.toByte(), codeOf(piece)))

    private fun message(id: Byte, body: ByteArray): ByteArray {
        val total = 3 + body.size
        return byteArrayOf(id, ((total shr 7) and 0x7F).toByte(), (total and 0x7F).toByte()) + body
    }

    private fun codeOf(piece: Char): Byte = when (piece) {
        BoardPosition.EMPTY -> 0x00
        'P' -> 0x01; 'R' -> 0x02; 'N' -> 0x03; 'B' -> 0x04; 'K' -> 0x05; 'Q' -> 0x06
        'p' -> 0x07; 'r' -> 0x08; 'n' -> 0x09; 'b' -> 0x0A; 'k' -> 0x0B; 'q' -> 0x0C
        else -> error("no DGT code for $piece")
    }

    private fun indexOf(square: String): Int = ('8' - square[1]) * 8 + (square[0] - 'a')

    @Test
    fun `a board dump decodes to the position`() {
        val report = DgtProtocol.decode(boardDump(BoardPosition.STARTING))
        assertEquals(BoardReport.Position(BoardPosition.STARTING), report)
    }

    @Test
    fun `kings and queens are not swapped`() {
        // DGT numbers king 0x05 and queen 0x06, the opposite way round from most tables. Getting it
        // wrong exchanges both sides' kings and queens and still yields a legal-looking board.
        val squares = CharArray(BoardPosition.SQUARE_COUNT) { BoardPosition.EMPTY }
        squares[indexOf("e1")] = 'K'
        squares[indexOf("d8")] = 'q'
        val position = BoardPosition(String(squares))

        val report = DgtProtocol.decode(boardDump(position)) as BoardReport.Position
        assertEquals('K', report.position[indexOf("e1")])
        assertEquals('q', report.position[indexOf("d8")])
    }

    @Test
    fun `squares are in the same order this app uses`() {
        val squares = CharArray(BoardPosition.SQUARE_COUNT) { BoardPosition.EMPTY }
        squares[0] = 'r' // a8, the first byte of the dump
        val report = DgtProtocol.decode(boardDump(BoardPosition(String(squares)))) as BoardReport.Position
        assertEquals('r', report.position[0])
    }

    @Test
    fun `a field update names one square`() {
        val report = DgtProtocol.decode(fieldUpdate(indexOf("e4"), 'P'))
        assertEquals(BoardReport.SquareChanged(indexOf("e4"), 'P'), report)
    }

    @Test
    fun `messages that are not about the pieces are ignored`() {
        // Serial number, version, battery: all real messages, none a position.
        assertEquals(BoardReport.Ignored, DgtProtocol.decode(message(0x91.toByte(), ByteArray(5))))
        assertEquals(BoardReport.Ignored, DgtProtocol.decode(byteArrayOf()))
    }

    @Test
    fun `a spare piece code is not guessed at`() {
        val dump = boardDump(BoardPosition.STARTING)
        dump[3] = 0x0D // DGT's spare code, used by their draughts boards
        assertEquals(BoardReport.Ignored, DgtProtocol.decode(dump))
    }

    @Test
    fun `a dump split across reads is reassembled`() {
        val assembler = StreamAssembler(DgtProtocol.framing!!)
        val dump = boardDump(BoardPosition.STARTING)

        assertEquals(emptyList<ByteArray>(), assembler.offer(dump.copyOfRange(0, 20)))
        assertEquals(emptyList<ByteArray>(), assembler.offer(dump.copyOfRange(20, 50)))

        val done = assembler.offer(dump.copyOfRange(50, dump.size))
        assertEquals(1, done.size)
        assertArrayEquals(dump, done.single())
    }

    @Test
    fun `two messages in one read come out as two`() {
        val assembler = StreamAssembler(DgtProtocol.framing!!)
        val first = fieldUpdate(indexOf("e2"), BoardPosition.EMPTY)
        val second = fieldUpdate(indexOf("e4"), 'P')

        val messages = assembler.offer(first + second)
        assertEquals(2, messages.size)
        assertArrayEquals(first, messages[0])
        assertArrayEquals(second, messages[1])
    }

    @Test
    fun `junk before the first message is skipped`() {
        // Joining a board that was already talking, or a half message left by a previous session.
        val assembler = StreamAssembler(DgtProtocol.framing!!)
        val update = fieldUpdate(indexOf("e4"), 'P')

        val messages = assembler.offer(byteArrayOf(0x11, 0x22, 0x33) + update)
        assertEquals(1, messages.size)
        assertArrayEquals(update, messages.single())
    }

    @Test
    fun `a whole move arrives as two field updates`() {
        val tracker = BoardMoveTracker()
        tracker.onReport(DgtProtocol.decode(boardDump(BoardPosition.STARTING)))

        assertEquals(
            emptyList<String>(),
            tracker.onReport(DgtProtocol.decode(fieldUpdate(indexOf("e2"), BoardPosition.EMPTY))),
        )
        assertEquals(
            listOf("e2e4"),
            tracker.onReport(DgtProtocol.decode(fieldUpdate(indexOf("e4"), 'P'))),
        )
    }

    @Test
    fun `a DGT is recognised by name`() {
        assertSame(DgtProtocol, BoardProtocols.forDeviceName("DGT_BLUETOOTH_1234"))
        assertSame(DgtProtocol, BoardProtocols.forDeviceName("DGT Pegasus"))
        assertSame(RawCaptureProtocol, BoardProtocols.forDeviceName("Chess Board"))
    }
}
