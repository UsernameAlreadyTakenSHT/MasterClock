package com.masterclock.app.logic

import org.junit.Test
import org.junit.Assert.*

/**
 * Covers the Certabo decoder and the calibration it cannot work without.
 *
 * Certabo is the only make here that reports which *individual* piece is on a square rather than
 * what kind it is, so the tests are as much about learning the mapping as about parsing the frame.
 */
class CertaboProtocolTest {

    /** Builds a frame: five ASCII decimals per square, from a1 upwards, newline-terminated. */
    private fun frame(tagsFromA1: List<String?>): ByteArray {
        val values = tagsFromA1.flatMap { tag ->
            tag?.split("-") ?: listOf("0", "0", "0", "0", "0")
        }
        return (values.joinToString(" ") + "\n").toByteArray(Charsets.US_ASCII)
    }

    /** The starting position, each piece given a distinct made-up chip id. */
    private fun startingTagsFromA1(): List<String?> {
        val tags = arrayOfNulls<String>(BoardPosition.SQUARE_COUNT)
        var chip = 1
        for (indexFromA1 in 0 until BoardPosition.SQUARE_COUNT) {
            val rankFromBottom = indexFromA1 / 8
            val file = indexFromA1 % 8
            val boardIndex = (7 - rankFromBottom) * 8 + file
            if (BoardPosition.STARTING[boardIndex] != BoardPosition.EMPTY) {
                tags[indexFromA1] = "0-0-0-0-${chip++}"
            }
        }
        return tags.toList()
    }

    private fun indexOf(square: String): Int = ('8' - square[1]) * 8 + (square[0] - 'a')

    @Test
    fun `a frame decodes to tags in this app's square order`() {
        val report = CertaboProtocol.decode(frame(startingTagsFromA1())) as BoardReport.TaggedSquares
        assertEquals(BoardPosition.SQUARE_COUNT, report.tags.size)
        // a1 is the first value in the frame and the bottom-left square here.
        assertNotNull(report.tags[indexOf("a1")])
        assertNotNull(report.tags[indexOf("a8")])
        // The middle of the board is empty in the starting position.
        assertNull(report.tags[indexOf("e4")])
    }

    @Test
    fun `all-zero means an empty square, not a chip numbered zero`() {
        val tags = List<String?>(BoardPosition.SQUARE_COUNT) { null }
        val report = CertaboProtocol.decode(frame(tags)) as BoardReport.TaggedSquares
        assertTrue(report.tags.all { it == null })
    }

    @Test
    fun `a frame of the wrong length is ignored`() {
        assertEquals(BoardReport.Ignored, CertaboProtocol.decode("1 2 3\n".toByteArray()))
        assertEquals(BoardReport.Ignored, CertaboProtocol.decode(byteArrayOf()))
    }

    @Test
    fun `a frame with something that is not a number is ignored`() {
        val text = String(frame(startingTagsFromA1()), Charsets.US_ASCII).replaceFirst("0", "x")
        assertEquals(BoardReport.Ignored, CertaboProtocol.decode(text.toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun `frames are split on newlines`() {
        val assembler = StreamAssembler(CertaboProtocol.framing!!)
        val one = frame(startingTagsFromA1())

        assertEquals(emptyList<ByteArray>(), assembler.offer(one.copyOfRange(0, 40)))
        val done = assembler.offer(one.copyOfRange(40, one.size))
        assertEquals(1, done.size)
        assertArrayEquals(one, done.single())
    }

    @Test
    fun `nothing is understood until the pieces have been set up once`() {
        val calibration = PieceTagCalibration()
        // A position that is not the starting one teaches nothing, so it cannot be read either.
        val tags = arrayOfNulls<String>(BoardPosition.SQUARE_COUNT)
        tags[indexOf("e4")] = "0-0-0-0-1"
        assertNull(calibration.identify(tags.toList()))
        assertFalse(calibration.isCalibrated)
    }

    @Test
    fun `the starting position teaches every piece at once`() {
        val calibration = PieceTagCalibration()
        val report = CertaboProtocol.decode(frame(startingTagsFromA1())) as BoardReport.TaggedSquares

        val position = calibration.identify(report.tags)
        assertTrue(calibration.isCalibrated)
        assertEquals(BoardPosition.STARTING, position)
    }

    @Test
    fun `a move is read once the board has been calibrated`() {
        val tracker = BoardMoveTracker()
        assertTrue(tracker.needsCalibration)

        val start = startingTagsFromA1()
        tracker.onReport(CertaboProtocol.decode(frame(start)))
        assertFalse(tracker.needsCalibration)

        // Move the pawn that was on e2 to e4, carrying its chip with it.
        val moved = start.toMutableList()
        val e2FromA1 = 8 + 4 // rank 2, file e
        val e4FromA1 = 24 + 4 // rank 4, file e
        moved[e4FromA1] = moved[e2FromA1]
        moved[e2FromA1] = null

        assertEquals(listOf("e2e4"), tracker.onReport(CertaboProtocol.decode(frame(moved))))
    }

    @Test
    fun `an unknown chip is refused rather than guessed`() {
        val calibration = PieceTagCalibration()
        calibration.identify((CertaboProtocol.decode(frame(startingTagsFromA1())) as BoardReport.TaggedSquares).tags)

        // A piece from another set appears on an empty square.
        val tags = arrayOfNulls<String>(BoardPosition.SQUARE_COUNT)
        tags[indexOf("e4")] = "9-9-9-9-9"
        assertNull(calibration.identify(tags.toList()))
    }

    @Test
    fun `a Certabo is recognised by name`() {
        assertSame(CertaboProtocol, BoardProtocols.forDeviceName("Certabo Chessboard"))
    }
}
