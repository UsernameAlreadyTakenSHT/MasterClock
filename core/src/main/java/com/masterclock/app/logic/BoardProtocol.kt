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
     * Turn one notification payload into the moves it describes.
     *
     * A payload is not always one move: some boards report the whole 64-square occupancy on every
     * change, which yields one move or none, and some batch several. Returning a list rather than a
     * nullable keeps both honest.
     *
     * Whatever notation comes out is what an exported PGN will show, so implementations should emit
     * what their board actually means rather than guessing at SAN.
     */
    fun decode(payload: ByteArray): List<String>
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

    override fun decode(payload: ByteArray): List<String> =
        if (payload.isEmpty()) emptyList() else listOf(payload.toHexString())
}

/** Lowercase hex, space-separated, as a BLE trace would show it. */
fun ByteArray.toHexString(): String = joinToString(" ") { "%02x".format(it) }

object BoardProtocols {
    /**
     * Every make the app can talk to, most specific first; [RawCaptureProtocol] must stay last
     * because it matches nothing and is only ever chosen explicitly.
     */
    val known: List<BoardProtocol> = listOf(RawCaptureProtocol)

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
