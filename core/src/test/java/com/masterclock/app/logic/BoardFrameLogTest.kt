package com.masterclock.app.logic

import com.masterclock.app.logic.BoardFrameLog.Direction
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What is kept of a board's talk, how it reads as a file, and that the file reads back. */
class BoardFrameLogTest {

    private var clock = 1_000_000L
    private fun log(capacity: Int = 10) = BoardFrameLog(capacity) { clock }

    @Test
    fun `a frame is kept with its time from the first, its way, its link and its bytes`() {
        val log = log()
        log.record(Direction.FROM_BOARD, "BLE", "Millennium", byteArrayOf(0x7E, 0x12, -1))
        clock += 1_250
        log.record(Direction.TO_BOARD, "BLE", "Millennium", byteArrayOf(0x01))
        assertEquals(2, log.count.value)
        assertEquals(
            listOf(
                BoardFrameLog.Frame(0, Direction.FROM_BOARD, "BLE", "Millennium", "7E 12 FF"),
                BoardFrameLog.Frame(1_250, Direction.TO_BOARD, "BLE", "Millennium", "01"),
            ),
            log.frames(),
        )
    }

    @Test
    fun `nothing is kept of nothing, and only the last frames of a long session`() {
        val log = log(capacity = 3)
        log.record(Direction.FROM_BOARD, "USB", "DGT", ByteArray(0))
        assertEquals(0, log.count.value)
        for (i in 1..5) {
            log.record(Direction.FROM_BOARD, "USB", "DGT", byteArrayOf(i.toByte()))
            clock += 100
        }
        assertEquals(3, log.count.value)
        assertEquals(listOf("03", "04", "05"), log.frames().map { it.hex })
        // Times stay counted from the first frame of the session, kept or not.
        assertEquals(200L, log.frames().first().atMs)
        assertTrue(log.text("test").contains("3 frames, 2 earlier ones not kept"))
        log.clear()
        assertEquals(0, log.count.value)
        assertFalse(log.text("test").contains("not kept"))
    }

    @Test
    fun `the file names what wrote it and reads back frame for frame`() {
        val log = log()
        log.record(Direction.FROM_BOARD, "BT serial", "Chessnut Air", byteArrayOf(0x01, 0x24, 0x00))
        clock += 42
        log.record(Direction.TO_BOARD, "BT serial", "Chessnut Air", byteArrayOf(0x21, 0x01, 0x00))
        val text = log.text("MasterClock 0.8.44 on a test device")
        assertTrue(text.startsWith(BoardFrameLog.HEADER + "\n# MasterClock_0.8.44_on_a_test_device\n"))
        // A name of several words is one word in the file, so a line still splits on spaces.
        assertTrue(text.contains("0.000 < BT_serial Chessnut_Air 01 24 00"))
        assertTrue(text.contains("0.042 > BT_serial Chessnut_Air 21 01 00"))
        assertEquals(log.frames(), BoardFrameLog.parse(text))
        assertArrayEquals(byteArrayOf(0x21, 0x01, 0x00), BoardFrameLog.bytesOf(BoardFrameLog.parse(text)[1]))
    }

    @Test
    fun `a write the link refused is marked as not sent, and the seconds are in plain digits`() {
        val before = java.util.Locale.getDefault()
        try {
            // A phone set to Arabic: its own digits, in a file meant for a maintainer or a script.
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-EG"))
            val log = log()
            clock += 12_345
            log.record(Direction.NOT_SENT, "BLE", "Open BLE board", "begin".toByteArray())
            val text = log.text("test")
            assertTrue(text, text.contains("0.000 ! BLE Open_BLE_board 62 65 67 69 6E"))
            assertTrue(text.contains("! meant for it and not sent"))
            assertEquals(Direction.NOT_SENT, BoardFrameLog.parse(text).single().direction)
            clock += 1_500
            log.record(Direction.FROM_BOARD, "BLE", "Open BLE board", byteArrayOf(0x0A))
            assertTrue(log.text("test").contains("1.500 < BLE Open_BLE_board 0A"))
        } finally {
            java.util.Locale.setDefault(before)
        }
    }

    @Test
    fun `a frame longer than is kept is cut, and says so`() {
        val log = log()
        log.record(Direction.FROM_BOARD, "USB", "Raw", ByteArray(BoardFrameLog.MAX_FRAME_BYTES + 20) { 0x55 })
        val frame = log.frames().single()
        assertTrue(frame.hex.endsWith(" ..."))
        assertEquals(BoardFrameLog.MAX_FRAME_BYTES, BoardFrameLog.bytesOf(BoardFrameLog.parse(log.text("test")).single()).size)
    }

    @Test
    fun `a board that never stops talking is kept by weight, the latest of it`() {
        val log = BoardFrameLog(capacity = 1_000_000) { clock }
        val chunk = ByteArray(1_000) { 0x11 }
        // Each frame is three thousand characters of text less one; seven hundred fit, a thousand do not.
        repeat(1_000) { log.record(Direction.FROM_BOARD, "BLE", "Millennium", chunk); clock += 10 }
        val kept = log.count.value
        assertTrue("$kept", kept in 600..700)
        assertTrue(log.frames().sumOf { it.hex.length } <= BoardFrameLog.MAX_LOG_CHARS)
        assertEquals((1_000 - kept) * 10L, log.frames().first().atMs)
        assertTrue(log.text("test").contains("$kept frames, ${1_000 - kept} earlier ones not kept"))
    }

    @Test
    fun `a file from outside is read as far as it can be`() {
        val text = """
            ${BoardFrameLog.HEADER}
            # anything
            not a frame at all
            0.100 < BLE Millennium 7E 12
            0.200 ? BLE Millennium 7E
            0.300 > BLE Millennium ZZ 12
            0.400 > BLE Millennium 7
            -5.000 < BLE Millennium 01
            2.5 > USB DGT 4d 00

        """.trimIndent()
        assertEquals(
            listOf(
                BoardFrameLog.Frame(100, Direction.FROM_BOARD, "BLE", "Millennium", "7E 12"),
                BoardFrameLog.Frame(0, Direction.FROM_BOARD, "BLE", "Millennium", "01"),
                BoardFrameLog.Frame(2_500, Direction.TO_BOARD, "USB", "DGT", "4D 00"),
            ),
            BoardFrameLog.parse(text),
        )
        assertEquals(emptyList<BoardFrameLog.Frame>(), BoardFrameLog.parse(""))
    }

    @Test
    fun `a signed number is not a byte`() {
        val text = BoardFrameLog.HEADER + "\n0.000 < BLE Raw 0A -5\n0.100 < BLE Raw +A 0B\n0.200 < BLE Raw 0C 0D\n"
        assertEquals(listOf("0C 0D"), BoardFrameLog.parse(text).map { it.hex })
    }
}
