package com.masterclock.app.logic

import java.util.UUID

/**
 * What one make of electronic board speaks.
 *
 * There is no common protocol between manufacturers: Millennium publishes theirs, Chessnut and
 * Certabo have been reverse-engineered by their users, DGT keeps theirs under NDA. They agree on
 * nothing beyond running over BLE GATT. So everything vendor-specific is confined here, and
 * [BluetoothBoardManager] handles what is genuinely common -- scanning, connecting, subscribing to
 * notifications, and forwarding whatever comes back.
 *
 * Adding a make is therefore one implementation of this interface plus one entry in
 * [BoardProtocols.known]. Nothing else has to change.
 */
/**
 * Where to listen on a board reached over Bluetooth.
 *
 * Kept apart from [BoardProtocol] itself because none of it means anything over USB, where there
 * are no services, characteristics or descriptors -- only endpoints carrying a byte stream. What
 * both transports share is [BoardProtocol.decode], which is also the expensive part to work out per
 * make, so it must not be entangled with either one.
 */
data class BleAddressing(
    /** The GATT service to look for, or null to accept any. A real make names one, which also lets the scan filter on it. */
    val serviceUuid: UUID?,
    /** The characteristic updates arrive on, or null to subscribe to everything that notifies. */
    val notifyCharacteristicUuid: UUID?,
    /** Characteristic to write [BoardProtocol.initCommand] to, if the board needs waking up. */
    val writeCharacteristicUuid: UUID? = null,
)

interface BoardProtocol {
    /** Shown in the board picker. Not translated: these are product names. */
    val name: String

    /** How to address this make over Bluetooth, or null if it has no Bluetooth variant. */
    val ble: BleAddressing?

    /** Sent once the link is up. Several makes stay silent until asked to report. */
    val initCommand: ByteArray? get() = null

    /** Whether an advertised Bluetooth name looks like this make. */
    fun matchesDeviceName(deviceName: String?): Boolean = false

    /** Whether a USB device's vendor and product ids belong to this make. */
    fun matchesUsbIds(vendorId: Int, productId: Int): Boolean = false

    /**
     * Line speed to ask for over USB serial.
     *
     * Only a make knows its own: DGT's boards run at 9600, others at 115200. The default is the
     * slower one because a device configured faster usually still delivers readable frames, while
     * the reverse produces silence -- and silence is the hardest failure to diagnose here.
     */
    val usbBaudRate: Int get() = 9600

    /**
     * Turn one payload into what it says about the board.
     *
     * Nearly every make reports a position rather than a move -- see [BoardReport.Position], which
     * [BoardMoveTracker] turns into moves. [BoardReport.Moves] exists for the rare payload that
     * really does name a move, and for the raw-capture fallback.
     */
    fun decode(payload: ByteArray): BoardReport
}

/** What one payload from a board turned out to be. */
sealed interface BoardReport {
    /** The whole board, as most makes send on every change. */
    data class Position(val position: BoardPosition) : BoardReport

    /** Moves named directly, which only the raw-capture fallback and a few makes produce. */
    data class Moves(val moves: List<String>) : BoardReport

    /** A payload that says nothing about the board: a keep-alive, a battery level, a malformed frame. */
    data object Ignored : BoardReport
}

/**
 * Keeps the previous position so a stream of board reports becomes a stream of moves.
 *
 * One per connection, and deliberately not inside [BoardProtocol]: the protocols are stateless
 * decoders, and every make would otherwise carry its own copy of this.
 */
class BoardMoveTracker {
    private var lastPosition: BoardPosition? = null

    fun onReport(report: BoardReport): List<String> = when (report) {
        is BoardReport.Moves -> report.moves
        is BoardReport.Ignored -> emptyList()
        is BoardReport.Position -> {
            val previous = lastPosition
            lastPosition = report.position
            // The first report only establishes where the pieces are; there is no move to infer
            // from it, and the board is usually reporting a position set up before the app was even
            // connected.
            if (previous == null) emptyList()
            else listOfNotNull(BoardDiffer.moveBetween(previous, report.position))
        }
    }

    fun reset() {
        lastPosition = null
    }
}

/**
 * The fallback used until a make is implemented: subscribe to everything that notifies, and report
 * each payload as hex.
 *
 * This is deliberately not a pretend board. It is the tool for adding the first real one -- connect
 * an unknown board, move a piece, and read off which characteristic spoke and what it said. Without
 * it, supporting a new make means owning both the board and a separate BLE sniffer.
 */
object RawCaptureProtocol : BoardProtocol {
    override val name = "Raw capture (unknown board)"

    /** Both null: accept any service, and subscribe to every characteristic that notifies. */
    override val ble = BleAddressing(serviceUuid = null, notifyCharacteristicUuid = null)

    override fun decode(payload: ByteArray): BoardReport =
        if (payload.isEmpty()) BoardReport.Ignored else BoardReport.Moves(listOf(payload.toHexString()))
}

/** Lowercase hex, space-separated, as a BLE trace would show it. */
fun ByteArray.toHexString(): String = joinToString(" ") { "%02x".format(it) }

object BoardProtocols {
    /**
     * Every make the app can talk to, most specific first; [RawCaptureProtocol] must stay last
     * because it matches nothing and is only ever chosen explicitly.
     */
    val known: List<BoardProtocol> = listOf(ChessnutProtocol, RawCaptureProtocol)

    /**
     * The protocol to use for a board advertising [deviceName], or [RawCaptureProtocol] when no
     * implementation claims it.
     */
    fun forDeviceName(deviceName: String?): BoardProtocol =
        known.firstOrNull { it !== RawCaptureProtocol && it.matchesDeviceName(deviceName) }
            ?: RawCaptureProtocol

    /** The protocol for a USB board with these ids, or [RawCaptureProtocol] when none claims it. */
    fun forUsbIds(vendorId: Int, productId: Int): BoardProtocol =
        known.firstOrNull { it !== RawCaptureProtocol && it.matchesUsbIds(vendorId, productId) }
            ?: RawCaptureProtocol
}

/** Which way a board is attached. The pairing and decoding above are the same for both. */
enum class BoardTransportKind { BLUETOOTH, USB }

/**
 * A board the user could connect to, named the same way whichever transport found it, so the
 * picker does not need one list per transport.
 */
data class BoardCandidate(
    /** Stable within a transport: a MAC address over Bluetooth, a device name over USB. */
    val id: String,
    val label: String,
    val detail: String,
    val kind: BoardTransportKind,
)
