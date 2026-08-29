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
interface BoardProtocol {
    /** Shown in the board picker. Not translated: these are product names. */
    val name: String

    /**
     * The GATT service to look for, or null to accept any service.
     *
     * Null is what [RawCaptureProtocol] uses; a real make always names one, which also lets the
     * scan filter on it instead of listing every BLE device in the room.
     */
    val serviceUuid: UUID?

    /**
     * The characteristic the board pushes updates on, or null to subscribe to every characteristic
     * that supports notification.
     */
    val notifyCharacteristicUuid: UUID?

    /** Characteristic to write [initCommand] to once connected, if the board needs waking up. */
    val writeCharacteristicUuid: UUID? get() = null

    /** Sent once after subscribing. Several makes stay silent until asked to report. */
    val initCommand: ByteArray? get() = null

    /** Whether an advertised name looks like this make. Used when [serviceUuid] is null. */
    fun matchesDeviceName(deviceName: String?): Boolean = false

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
    override val serviceUuid: UUID? = null
    override val notifyCharacteristicUuid: UUID? = null

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
}
