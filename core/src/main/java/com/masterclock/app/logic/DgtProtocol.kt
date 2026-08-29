package com.masterclock.app.logic

/**
 * DGT's electronic boards -- the tournament standard, and the make this app's users are likeliest
 * to own.
 *
 * Taken from the MIT-licensed dgt-board driver, which carries the constants as data. DGT's own
 * `dgtbrd.h` is no longer published on their site; what circulates is a copy preserved by the
 * community, so this is one of the makes where a second reading would be worth having if hardware
 * ever turns up.
 *
 * Unlike Chessnut, a DGT reports its squares in the same order this app uses, a8 first -- so there
 * is no mirror to get wrong here. What it does instead is send one square at a time once in update
 * mode, which is what [BoardReport.SquareChanged] exists for.
 */
object DgtProtocol : BoardProtocol {
    override val name = "DGT"

    /**
     * Reached over a serial line, not GATT: the USB boards are serial, and the Bluetooth e-Boards
     * present as a virtual serial port over Bluetooth Classic rather than as a BLE peripheral. The
     * Pegasus is BLE, but its service and characteristic are not published anywhere this could be
     * taken from, so it is not claimed here.
     */
    override val ble = null

    /**
     * Reset, ask for the current position, then switch to update mode.
     *
     * The dump matters as much as the mode: update mode reports single squares and says nothing
     * about the other sixty-three, so without a full position first there is nothing to apply them
     * to.
     */
    override val initCommand = byteArrayOf(SEND_RESET, SEND_BOARD, SEND_UPDATE_BOARD)

    /** DGT's own boards run at 9600, which is why that is this app's default. */
    override val usbBaudRate = 9600

    override fun matchesDeviceName(deviceName: String?): Boolean =
        deviceName?.contains("DGT", ignoreCase = true) == true

    /**
     * Every message is an id with its top bit set, then its total length in two seven-bit halves.
     * A byte without that top bit cannot start a message, so it is dropped and the search resumes.
     */
    override val framing = StreamFraming { buffer ->
        when {
            buffer[0].toInt() and MESSAGE_BIT == 0 -> StreamFraming.RESYNC
            buffer.size < HEADER_SIZE -> null
            else -> {
                val length = ((buffer[1].toInt() and 0x7F) shl 7) or (buffer[2].toInt() and 0x7F)
                // A message is at least its own header; anything shorter is noise that happened to
                // have the top bit set.
                if (length < HEADER_SIZE) StreamFraming.RESYNC else length
            }
        }
    }

    override fun decode(payload: ByteArray): BoardReport {
        if (payload.size < HEADER_SIZE) return BoardReport.Ignored
        return when (payload[0]) {
            BOARD_DUMP -> decodeBoardDump(payload)
            FIELD_UPDATE -> decodeFieldUpdate(payload)
            // Serial numbers, version, battery status, clock messages: all real, none about where
            // the pieces are.
            else -> BoardReport.Ignored
        }
    }

    private fun decodeBoardDump(payload: ByteArray): BoardReport {
        if (payload.size < HEADER_SIZE + BoardPosition.SQUARE_COUNT) return BoardReport.Ignored
        val squares = CharArray(BoardPosition.SQUARE_COUNT)
        for (square in 0 until BoardPosition.SQUARE_COUNT) {
            squares[square] = pieceOf(payload[HEADER_SIZE + square]) ?: return BoardReport.Ignored
        }
        return BoardReport.Position(BoardPosition(String(squares)))
    }

    private fun decodeFieldUpdate(payload: ByteArray): BoardReport {
        if (payload.size < HEADER_SIZE + 2) return BoardReport.Ignored
        val square = payload[HEADER_SIZE].toInt() and 0xFF
        if (square >= BoardPosition.SQUARE_COUNT) return BoardReport.Ignored
        val piece = pieceOf(payload[HEADER_SIZE + 1]) ?: return BoardReport.Ignored
        return BoardReport.SquareChanged(square, piece)
    }

    /**
     * Note the order: pawn, rook, knight, bishop, then **king before queen**.
     *
     * That is DGT's numbering, not the usual one, and swapping the last two produces a board where
     * both sides' kings and queens are exchanged -- a position that is entirely legal-looking.
     */
    private fun pieceOf(code: Byte): Char? = when (code.toInt() and 0xFF) {
        0x00 -> BoardPosition.EMPTY
        0x01 -> 'P'
        0x02 -> 'R'
        0x03 -> 'N'
        0x04 -> 'B'
        0x05 -> 'K'
        0x06 -> 'Q'
        0x07 -> 'p'
        0x08 -> 'r'
        0x09 -> 'n'
        0x0A -> 'b'
        0x0B -> 'k'
        0x0C -> 'q'
        // 0x0D-0x0F are DGT's spare piece codes, used by their draughts boards and by nothing this
        // app understands as a chess piece.
        else -> null
    }

    private const val MESSAGE_BIT = 0x80
    private const val HEADER_SIZE = 3

    private const val SEND_RESET: Byte = 0x40
    private const val SEND_BOARD: Byte = 0x42
    private const val SEND_UPDATE_BOARD: Byte = 0x44

    private const val BOARD_DUMP: Byte = 0x86.toByte()
    private const val FIELD_UPDATE: Byte = 0x8E.toByte()
}
