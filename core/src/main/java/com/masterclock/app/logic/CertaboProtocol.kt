package com.masterclock.app.logic

/**
 * Certabo's boards.
 *
 * The simplest wire format of the four and the hardest to make sense of. A frame is plain ASCII:
 * 320 decimal numbers separated by spaces and ended by a newline, at 38400 baud -- five bytes for
 * each of the 64 squares, which are the id of the RFID chip glued under whatever piece is standing
 * there. Five zeroes mean an empty square.
 *
 * So Certabo reports *which piece*, never *what kind of piece*. The chips are stuck under the
 * pieces by whoever owns the set and their numbers differ from set to set, so no table can be
 * shipped: the mapping is learned from the starting position by [PieceTagCalibration], which is
 * what Certabo's own software asks the player to do before a first game.
 *
 * Read from Certabo's published software, which is GPL-3.0 and so may be read for the facts of the
 * protocol but not copied into an MIT project. Nothing here is transcribed from it.
 */
object CertaboProtocol : BoardProtocol {
    override val name = "Certabo"

    /** A serial board, over its cable. Newer models add BLE, whose service is not published. */
    override val ble = null

    /** The board talks on its own as soon as it is powered; nothing has to ask it to. */
    override val initCommand = null

    override val usbBaudRate = 38400

    /** The chips are stuck under the pieces by whoever owns the set, so the mapping must be learned. */
    override val needsPieceCalibration = true

    override fun matchesDeviceName(deviceName: String?): Boolean =
        deviceName?.contains("Certabo", ignoreCase = true) == true

    /** Frames are newline-terminated, so a message ends where the next newline is. */
    override val framing = StreamFraming { buffer ->
        val end = buffer.indexOfFirst { it == NEWLINE }
        if (end < 0) null else end + 1
    }

    override fun decode(payload: ByteArray): BoardReport {
        val values = String(payload, Charsets.US_ASCII).trim().split(" ").filter { it.isNotEmpty() }
        if (values.size != BoardPosition.SQUARE_COUNT * BYTES_PER_SQUARE) return BoardReport.Ignored

        val tags = ArrayList<String?>(BoardPosition.SQUARE_COUNT)
        for (square in 0 until BoardPosition.SQUARE_COUNT) {
            val chunk = values.subList(square * BYTES_PER_SQUARE, (square + 1) * BYTES_PER_SQUARE)
            if (chunk.any { it.toIntOrNull() == null }) return BoardReport.Ignored
            // All zeroes is how the board says "nothing here", not a chip whose id happens to be 0.
            tags += if (chunk.all { it.toInt() == 0 }) null else chunk.joinToString("-")
        }
        // In this app's order as they come: a frame starts at a8 and reads along each rank, rank by
        // rank down to h1, as certabodriver names its squares and cer2nut lays them out. It was read
        // from a1, and calibration cannot show that up: a starting position read upside down is
        // self-consistent, and only the moves came out wrong, e2e4 as e7e5. Still from those two
        // drivers rather than from a board, or from Certabo's own software.
        return BoardReport.TaggedSquares(tags)
    }


    private const val BYTES_PER_SQUARE = 5
    private const val NEWLINE: Byte = 0x0A
}
