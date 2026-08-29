package com.masterclock.app.logic

/**
 * Millennium's ChessLink boards -- the Exclusive, the King Performance, the eONE.
 *
 * Taken from python-mchess, whose own code is MIT and whose author records that Millennium provided
 * the information for it. Of the four makes here this is the only one documented with the
 * manufacturer's cooperation rather than around it.
 *
 * It is also the only one that does not send raw bytes. Every character is 7-bit ASCII with an odd
 * parity bit in the top position, and every message ends with its own checksum -- so a frame can be
 * checked rather than merely hoped over, which no other make here allows.
 */
object MillenniumProtocol : BoardProtocol {
    override val name = "Millennium ChessLink"

    /**
     * ChessLink is reached as a serial line over USB, and over BLE on the boards that have it. The
     * BLE service is not published anywhere this could honestly take it from, so only the cable is
     * claimed.
     */
    override val ble = null

    /** Ask for the current position. The board reports changes unprompted afterwards. */
    override val initCommand = command("S")

    override fun matchesDeviceName(deviceName: String?): Boolean {
        val name = deviceName ?: return false
        return name.contains("MILLENNIUM", ignoreCase = true) || name.contains("ChessLink", ignoreCase = true)
    }

    /**
     * Every reply is a known length for its leading letter, which makes framing exact rather than
     * a guess. A byte that is not a reply this app knows is dropped and the search resumes.
     */
    override val framing = StreamFraming { buffer ->
        REPLY_LENGTHS[stripParity(buffer[0])] ?: StreamFraming.RESYNC
    }

    override fun decode(payload: ByteArray): BoardReport {
        val text = payload.map { stripParity(it) }.joinToString("")
        if (text.length != BOARD_REPLY_LENGTH || text[0] != BOARD_REPLY) return BoardReport.Ignored
        if (!hasValidChecksum(text)) return BoardReport.Ignored

        val body = text.substring(1, 1 + BoardPosition.SQUARE_COUNT)
        val squares = CharArray(BoardPosition.SQUARE_COUNT)
        for (index in body.indices) {
            val piece = body[index]
            if (piece != EMPTY_SQUARE && piece !in PIECES) return BoardReport.Ignored
            squares[boardIndexOf(index)] = if (piece == EMPTY_SQUARE) BoardPosition.EMPTY else piece
        }
        return BoardReport.Position(BoardPosition(String(squares)))
    }

    /**
     * ChessLink counts from h1 and works along each rank towards a, rank by rank upwards;
     * [BoardPosition] counts from a8 downwards. So both axes are reversed, not one.
     *
     * A known limitation, and the reason this is worth spelling out: a ChessLink can be set up with
     * its cable on either side, and the board sends the same bytes either way. python-mchess spots
     * the difference by noticing that the starting position has come out mirrored and flips from
     * then on. Doing the same needs state across frames and a board to test it against, so a
     * cable-left board will currently read mirrored.
     */
    private fun boardIndexOf(replyIndex: Int): Int {
        val rankFromBottom = replyIndex / 8
        val fileFromH = replyIndex % 8
        return (7 - rankFromBottom) * 8 + (7 - fileFromH)
    }

    /** The checksum is the XOR of everything before it, written as two hex characters. */
    private fun hasValidChecksum(text: String): Boolean {
        val expected = text.substring(0, text.length - 2).fold(0) { acc, c -> acc xor c.code } and 0xFF
        return text.takeLast(2).equals("%02X".format(expected), ignoreCase = true)
    }

    /** Builds a command the way the board expects one: the letters, its checksum, all with parity. */
    private fun command(body: String): ByteArray {
        val checksum = body.fold(0) { acc, c -> acc xor c.code } and 0xFF
        return (body + "%02X".format(checksum)).map { addOddParity(it) }.toByteArray()
    }

    /** The top bit carries parity, never data, so it must come off before the byte means anything. */
    private fun stripParity(byte: Byte): Char = (byte.toInt() and 0x7F).toChar()

    /** Sets the top bit so the number of ones in the byte is odd. */
    private fun addOddParity(c: Char): Byte {
        val value = c.code and 0x7F
        return if (Integer.bitCount(value) % 2 == 0) (value or 0x80).toByte() else value.toByte()
    }

    private const val BOARD_REPLY = 's'
    private const val EMPTY_SQUARE = '.'
    private const val PIECES = "PNBRQKpnbrqk"

    /** One letter, 64 squares, two checksum characters. */
    private const val BOARD_REPLY_LENGTH = 67

    /** Reply lengths by leading letter, as the board defines them. */
    private val REPLY_LENGTHS = mapOf(
        'v' to 7, 's' to BOARD_REPLY_LENGTH, 'l' to 3, 'x' to 3, 'w' to 7, 'r' to 7,
    )
}
