package com.masterclock.app.logic

import org.junit.Test
import org.junit.Assert.*

/**
 * Covers the Chessnut decoder against frames built to the protocol as the vendor's own SDK
 * implements it.
 *
 * The ordering test is the one that earns its keep. Chessnut counts squares from h8 while
 * [BoardPosition] counts from a8, so getting it wrong yields a left-right mirror -- a board that
 * still parses, still looks like chess, and produces moves that are quietly wrong. Every other kind
 * of mistake here fails loudly; this one does not.
 */
class ChessnutProtocolTest {

    /** Builds a state frame from a position, mirroring the encoding the board uses. */
    private fun frameOf(position: BoardPosition, trailing: Int = 4): ByteArray {
        val codes = IntArray(BoardPosition.SQUARE_COUNT)
        for (nibble in 0 until BoardPosition.SQUARE_COUNT) {
            val rankRow = nibble / 8
            val fileFromH = nibble % 8
            val fenIndex = rankRow * 8 + (7 - fileFromH)
            codes[nibble] = PIECE_ORDER.indexOf(position[fenIndex])
        }
        val body = ByteArray(32)
        for (i in 0 until 32) {
            body[i] = ((codes[i * 2]) or (codes[i * 2 + 1] shl 4)).toByte()
        }
        return byteArrayOf(0x01, 0x24) + body + ByteArray(trailing)
    }

    private val PIECE_ORDER = charArrayOf(
        BoardPosition.EMPTY, 'q', 'k', 'b', 'p', 'n', 'R', 'P', 'r', 'B', 'N', 'Q', 'K',
    )

    @Test
    fun `the starting position survives a round trip`() {
        val report = ChessnutProtocol.decode(frameOf(BoardPosition.STARTING))
        assertEquals(BoardReport.Position(BoardPosition.STARTING), report)
    }

    @Test
    fun `files are not mirrored`() {
        // A single rook on a1. If the file ordering were reversed this would decode onto h1, and
        // nothing else in the suite would notice.
        val squares = CharArray(BoardPosition.SQUARE_COUNT) { BoardPosition.EMPTY }
        squares[56] = 'R' // a1
        val position = BoardPosition(String(squares))

        val report = ChessnutProtocol.decode(frameOf(position)) as BoardReport.Position
        assertEquals('R', report.position[56])
        assertEquals(BoardPosition.EMPTY, report.position[63])
    }

    @Test
    fun `ranks are not flipped`() {
        val squares = CharArray(BoardPosition.SQUARE_COUNT) { BoardPosition.EMPTY }
        squares[0] = 'k' // a8
        val position = BoardPosition(String(squares))

        val report = ChessnutProtocol.decode(frameOf(position)) as BoardReport.Position
        assertEquals('k', report.position[0])
        assertEquals(BoardPosition.EMPTY, report.position[56])
    }

    @Test
    fun `both colours decode with the right case`() {
        val squares = CharArray(BoardPosition.SQUARE_COUNT) { BoardPosition.EMPTY }
        squares[0] = 'q'
        squares[63] = 'Q'
        val position = BoardPosition(String(squares))

        val report = ChessnutProtocol.decode(frameOf(position)) as BoardReport.Position
        assertEquals('q', report.position[0])
        assertEquals('Q', report.position[63])
    }

    @Test
    fun `a frame that is not a board report is ignored`() {
        // The echo of the init command, which arrives on the same characteristic.
        assertEquals(BoardReport.Ignored, ChessnutProtocol.decode(byteArrayOf(0x21, 0x01, 0x00)))
        assertEquals(BoardReport.Ignored, ChessnutProtocol.decode(byteArrayOf()))
        // Right report id, wrong length byte.
        assertEquals(BoardReport.Ignored, ChessnutProtocol.decode(byteArrayOf(0x01, 0x10) + ByteArray(32)))
    }

    @Test
    fun `a truncated frame is ignored rather than half-decoded`() {
        val short = frameOf(BoardPosition.STARTING).copyOf(20)
        assertEquals(BoardReport.Ignored, ChessnutProtocol.decode(short))
    }

    @Test
    fun `an unknown piece code is ignored rather than guessed`() {
        val frame = frameOf(BoardPosition.STARTING)
        frame[2] = 0x0F // no piece has code 15
        assertEquals(BoardReport.Ignored, ChessnutProtocol.decode(frame))
    }

    @Test
    fun `the board is recognised by advertised name and by USB ids`() {
        assertSame(ChessnutProtocol, BoardProtocols.forDeviceName("Chessnut Air"))
        assertSame(ChessnutProtocol, BoardProtocols.forDeviceName("Smart Chess"))
        assertSame(ChessnutProtocol, BoardProtocols.forUsbIds(0x2d80, 0x8001))
        assertSame(ChessnutProtocol, BoardProtocols.forUsbIds(0x2d80, 0x8600))
        assertSame(RawCaptureProtocol, BoardProtocols.forUsbIds(0x2d80, 0x9000))
        assertSame(RawCaptureProtocol, BoardProtocols.forDeviceName("DGT Pegasus"))
    }

    @Test
    fun `two frames a move apart yield that move`() {
        val tracker = BoardMoveTracker()
        val after = BoardPosition(
            BoardPosition.STARTING.squares.toCharArray().also {
                it[52] = BoardPosition.EMPTY // e2
                it[36] = 'P' // e4
            }.concatToString()
        )

        // The first frame only says where the pieces are.
        assertEquals(emptyList<String>(), tracker.onReport(ChessnutProtocol.decode(frameOf(BoardPosition.STARTING))))
        assertEquals(listOf("e2e4"), tracker.onReport(ChessnutProtocol.decode(frameOf(after))))
        // A repeat of the same position is not a second move.
        assertEquals(emptyList<String>(), tracker.onReport(ChessnutProtocol.decode(frameOf(after))))
    }
}
