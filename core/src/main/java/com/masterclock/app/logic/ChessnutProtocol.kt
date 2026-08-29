package com.masterclock.app.logic

import java.util.UUID

/**
 * Chessnut Air, Evo, Pro and the boards sold as "Smart Chess".
 *
 * Worked out from Chessnut's own EasyLinkSDK, which is MIT licensed, and cross-checked against an
 * independent community implementation that arrived at the same piece table from the other
 * direction. Two sources agreeing matters here: a wrong nibble ordering produces a mirrored board
 * that still looks plausible.
 *
 * The board reports its whole state, never a move -- the SDK builds a FEN straight out of a report,
 * which is only possible because the report is a position. [BoardMoveTracker] does the rest.
 */
object ChessnutProtocol : BoardProtocol {
    override val name = "Chessnut"

    /**
     * No service UUID: the community implementations name characteristics but not the service that
     * holds them, and the manager is happy to search every service for a known characteristic.
     */
    override val ble = BleAddressing(
        serviceUuid = null,
        notifyCharacteristicUuid = UUID.fromString("1B7E8262-2877-41C3-B46E-CF057C562023"),
        writeCharacteristicUuid = UUID.fromString("1B7E8272-2877-41C3-B46E-CF057C562023"),
    )

    /** Puts the board into real-time mode. Until it arrives, the board reports nothing at all. */
    override val initCommand = byteArrayOf(0x21, 0x01, 0x00)

    override fun matchesDeviceName(deviceName: String?): Boolean {
        val name = deviceName ?: return false
        return name.startsWith("Chessnut", ignoreCase = true) || name.startsWith("Smart Chess", ignoreCase = true)
    }

    override fun matchesUsbIds(vendorId: Int, productId: Int): Boolean =
        vendorId == USB_VENDOR_ID && (productId and 0xFF00) in USB_PRODUCT_FAMILIES

    override fun decode(payload: ByteArray): BoardReport {
        // A state frame is 0x01 0x24 then 32 bytes of board, then a few bytes this app has no use
        // for. Anything else -- keep-alives, the echo of the init command, button reports -- is not
        // a position and must not be read as one.
        if (payload.size < HEADER_SIZE + BOARD_BYTES) return BoardReport.Ignored
        if (payload[0] != STATE_REPORT_ID || payload[1] != STATE_PAYLOAD_LENGTH) return BoardReport.Ignored

        val squares = CharArray(BoardPosition.SQUARE_COUNT)
        for (nibble in 0 until BoardPosition.SQUARE_COUNT) {
            val byte = payload[HEADER_SIZE + nibble / 2].toInt()
            // Even nibbles are the low half of their byte, odd ones the high half.
            val code = if (nibble % 2 == 0) byte and 0x0F else (byte shr 4) and 0x0F
            val piece = PIECES.getOrNull(code) ?: return BoardReport.Ignored
            squares[fenIndexOfNibble(nibble)] = piece
        }
        return BoardReport.Position(BoardPosition(String(squares)))
    }

    /**
     * Chessnut counts squares from h8 down to a1; [BoardPosition] counts from a8.
     *
     * Within a rank the board runs h to a, so a nibble's file is mirrored while its rank is not.
     * This is the single most error-prone line in the whole make: get it wrong and every position
     * is a left-right mirror of the truth, which reads as a legal board and produces moves that are
     * merely wrong rather than obviously broken.
     */
    private fun fenIndexOfNibble(nibble: Int): Int {
        val rankRow = nibble / 8
        val fileFromH = nibble % 8
        return rankRow * 8 + (7 - fileFromH)
    }

    /** Index is the nibble value; case is colour, as in FEN. Identical in both reference sources. */
    private val PIECES = charArrayOf(
        BoardPosition.EMPTY, 'q', 'k', 'b', 'p', 'n', 'R', 'P', 'r', 'B', 'N', 'Q', 'K',
    )

    private const val HEADER_SIZE = 2
    private const val BOARD_BYTES = 32
    private const val STATE_REPORT_ID: Byte = 0x01
    private const val STATE_PAYLOAD_LENGTH: Byte = 0x24

    private const val USB_VENDOR_ID = 0x2d80
    private val USB_PRODUCT_FAMILIES = setOf(0x8000, 0x8100, 0x8200, 0x8300, 0x8400, 0x8500, 0x8600)
}
