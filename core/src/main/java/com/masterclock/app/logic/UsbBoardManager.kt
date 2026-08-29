package com.masterclock.app.logic

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A board attached by cable, in USB host mode.
 *
 * The counterpart to [BluetoothBoardManager], and deliberately its sibling rather than its
 * subclass: the two share no mechanism at all. There are no services, characteristics or
 * descriptors here, only endpoints carrying a byte stream. What they do share is
 * [BoardProtocol.decode] and the [ChessTimerViewModel.recordBoardMove] they both feed, which is
 * where the per-make work lives and the reason it is worth keeping apart from either transport.
 *
 * This speaks USB CDC-ACM, the standard serial-over-USB class that needs no vendor driver. Boards
 * built on an FTDI, CP210x or CH340 chip instead expose a vendor-specific interface and will be
 * listed but refuse to open; supporting them means a driver per chip, which belongs with the work
 * on a specific make rather than here.
 *
 * None of this is verified against hardware -- there is no board to test with yet.
 */
class UsbBoardManager(private val context: Context) {

    private companion object {
        /** Guard against a device claiming an implausible frame size; a serial frame is far smaller. */
        const val MAX_PAYLOAD_BYTES = 1024
        const val READ_BUFFER_BYTES = 256

        /**
         * Blocking read timeout. Short enough that disconnecting is not left waiting on it, long
         * enough not to spin: between moves a board sends nothing for minutes at a time.
         */
        const val READ_TIMEOUT_MS = 500

        const val ACTION_USB_PERMISSION = "com.masterclock.app.USB_PERMISSION"
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _attachedDevices = MutableStateFlow<List<BoardCandidate>>(emptyList())
    val attachedDevices: StateFlow<List<BoardCandidate>> = _attachedDevices.asStateFlow()

    private val _lastMove = MutableStateFlow<String?>(null)
    val lastMove: StateFlow<String?> = _lastMove.asStateFlow()

    private var protocol: BoardProtocol = RawCaptureProtocol

    /** The game this round is, for makes that have to be told before play starts. */
    private var gameType: GameType = GameType.CHESS

    /** Holds the previous position, since boards report state rather than moves. */
    private val moveTracker = BoardMoveTracker()

    /**
     * Reassembles messages for makes that need it. Null for the ones whose reads are already whole
     * messages, which is every HID board.
     */
    private var assembler: StreamAssembler? = null
    private var connection: UsbDeviceConnection? = null
    private var claimedInterface: UsbInterface? = null
    private var readJob: Job? = null
    private var onMoveReceived: ((String) -> Unit)? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Waits for the user's answer to the system permission dialog, which is the only way to reach a
     * USB device. Unlike Bluetooth's, this permission is granted per device and not remembered
     * across replugs unless the user ticks the box, so it is asked at connect time rather than up
     * front.
     */
    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(receivedContext: Context?, intent: Intent?) {
            if (intent?.action != ACTION_USB_PERMISSION) return
            val device = intent.getUsbDevice() ?: return
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                openDevice(device)
            } else {
                _connectionState.value = ConnectionState.Error("USB permission refused")
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            context,
            permissionReceiver,
            IntentFilter(ACTION_USB_PERMISSION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    /** USB needs no scanning: what is plugged in is already known. */
    fun refreshDevices() {
        _attachedDevices.value = usbManager.deviceList.values.map { it.toCandidate() }
    }

    fun connect(candidateId: String, gameType: GameType, onMoveReceived: (String) -> Unit) {
        this.gameType = gameType
        val device = usbManager.deviceList.values.firstOrNull { it.deviceName == candidateId }
        if (device == null) {
            _connectionState.value = ConnectionState.Error("That board is no longer plugged in")
            return
        }

        this.onMoveReceived = onMoveReceived
        protocol = BoardProtocols.forUsbIds(device.vendorId, device.productId)
        assembler = protocol.framing?.let { StreamAssembler(it) }
        moveTracker.reset()
        _connectionState.value = ConnectionState.Connecting

        if (usbManager.hasPermission(device)) {
            openDevice(device)
        } else {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            val intent = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_USB_PERMISSION).setPackage(context.packageName), flags
            )
            usbManager.requestPermission(device, intent)
        }
    }

    private fun openDevice(device: UsbDevice) {
        val serialInterface = device.findCdcDataInterface()
        if (serialInterface == null) {
            _connectionState.value =
                ConnectionState.Error("This board does not use standard USB serial, so it needs a driver this app does not have")
            return
        }

        val opened = usbManager.openDevice(device)
        if (opened == null) {
            _connectionState.value = ConnectionState.Error("Could not open the board")
            return
        }
        if (!opened.claimInterface(serialInterface, true)) {
            opened.close()
            _connectionState.value = ConnectionState.Error("Another app is already using this board")
            return
        }

        val endpointIn = serialInterface.findEndpoint(UsbConstants.USB_DIR_IN)
        if (endpointIn == null) {
            opened.releaseInterface(serialInterface)
            opened.close()
            _connectionState.value = ConnectionState.Error("This board sends nothing this app can read")
            return
        }

        connection = opened
        claimedInterface = serialInterface
        // Only a serial line has a baud rate to set; asking a HID interface for one achieves
        // nothing and can upset devices that answer control requests strictly.
        if (serialInterface.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA) {
            opened.configureCdcLine(serialInterface.id, protocol.usbBaudRate)
        }
        _connectionState.value = ConnectionState.Connected(device.productName ?: device.deviceName)
        // Several makes report nothing until asked; Chessnut is one of them.
        protocol.initCommandFor(gameType)?.let { command ->
            serialInterface.findEndpoint(UsbConstants.USB_DIR_OUT)?.let { endpointOut ->
                opened.bulkTransfer(endpointOut, command, command.size, CONTROL_TRANSFER_TIMEOUT_MS)
            }
        }
        startReading(opened, endpointIn)
    }

    private fun startReading(connection: UsbDeviceConnection, endpoint: UsbEndpoint) {
        readJob?.cancel()
        readJob = scope.launch {
            val buffer = ByteArray(READ_BUFFER_BYTES)
            while (isActive) {
                // bulkTransfer blocks up to the timeout and returns -1 when it expires, which is the
                // normal state between moves rather than an error.
                val read = connection.bulkTransfer(endpoint, buffer, buffer.size, READ_TIMEOUT_MS)
                if (read <= 0) continue

                val chunk = buffer.copyOf(minOf(read, MAX_PAYLOAD_BYTES))
                // A serial read is a slice of a byte stream, not a message. Where the make says how
                // its messages are delimited, put them back together first.
                val payloads = assembler?.offer(chunk) ?: listOf(chunk)
                val moves = payloads.flatMap { moveTracker.onReport(protocol.decode(it)) }
                if (moves.isEmpty()) continue
                withContext(Dispatchers.Main) {
                    moves.forEach { move ->
                        _lastMove.value = move
                        onMoveReceived?.invoke(move)
                    }
                }
            }
        }
    }

    fun disconnect() {
        readJob?.cancel()
        readJob = null
        claimedInterface?.let { connection?.releaseInterface(it) }
        connection?.close()
        connection = null
        claimedInterface = null
        _connectionState.value = ConnectionState.Idle
    }

    /** Call when the owner is destroyed; the receiver outlives a plain [disconnect] otherwise. */
    fun release() {
        disconnect()
        scope.cancel()
        runCatching { context.unregisterReceiver(permissionReceiver) }
    }

    private fun UsbDevice.toCandidate(): BoardCandidate = BoardCandidate(
        id = deviceName,
        label = productName ?: manufacturerName ?: deviceName,
        // Vendor and product ids are what a future make is recognised by, so they are worth showing:
        // they are exactly what someone would report when asking for their board to be supported.
        detail = "USB %04x:%04x".format(vendorId, productId),
        kind = BoardTransportKind.USB,
    )
}

/**
 * The CDC data interface, the one carrying the byte stream.
 *
 * A CDC device exposes two: a control interface (class 0x02) that carries no data, and this one.
 * Some cheaper boards declare the whole device as vendor-specific instead, which is why the
 * fallback below accepts a bulk-carrying vendor interface rather than giving up.
 */
private fun UsbDevice.findCdcDataInterface(): UsbInterface? {
    val interfaces = (0 until interfaceCount).map { getInterface(it) }
    return interfaces.firstOrNull { it.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA }
    // Chessnut's boards are HID over USB rather than serial, and HID carries its reports on
    // interrupt endpoints. Same bytes, different plumbing.
        ?: interfaces.firstOrNull {
            it.interfaceClass == UsbConstants.USB_CLASS_HID && it.findEndpoint(UsbConstants.USB_DIR_IN) != null
        }
        ?: interfaces.firstOrNull {
            it.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC && it.findEndpoint(UsbConstants.USB_DIR_IN) != null
        }
}

/** Bulk for serial, interrupt for HID; [UsbDeviceConnection.bulkTransfer] drives both. */
private fun UsbInterface.findEndpoint(direction: Int): UsbEndpoint? =
    (0 until endpointCount).map { getEndpoint(it) }
        .firstOrNull {
            it.direction == direction &&
                (it.type == UsbConstants.USB_ENDPOINT_XFER_BULK || it.type == UsbConstants.USB_ENDPOINT_XFER_INT)
        }

/**
 * Sets the line speed and raises DTR/RTS.
 *
 * Both are best-effort: a vendor-specific interface will reject them, and a device that ignores
 * them usually still works. Skipping the DTR/RTS one, though, is a common reason a CDC board opens
 * cleanly and then never says anything.
 */
private fun UsbDeviceConnection.configureCdcLine(interfaceId: Int, baudRate: Int) {
    val lineCoding = byteArrayOf(
        (baudRate and 0xff).toByte(),
        ((baudRate shr 8) and 0xff).toByte(),
        ((baudRate shr 16) and 0xff).toByte(),
        ((baudRate shr 24) and 0xff).toByte(),
        0, // 1 stop bit
        0, // no parity
        8, // 8 data bits
    )
    controlTransfer(
        CDC_REQUEST_TYPE_CLASS_INTERFACE_OUT, CDC_SET_LINE_CODING, 0, interfaceId,
        lineCoding, lineCoding.size, CONTROL_TRANSFER_TIMEOUT_MS,
    )
    controlTransfer(
        CDC_REQUEST_TYPE_CLASS_INTERFACE_OUT, CDC_SET_CONTROL_LINE_STATE, CDC_DTR_AND_RTS, interfaceId,
        null, 0, CONTROL_TRANSFER_TIMEOUT_MS,
    )
}

// CDC-ACM class requests, USB CDC spec section 6.2.
private const val CDC_REQUEST_TYPE_CLASS_INTERFACE_OUT = 0x21
private const val CDC_SET_LINE_CODING = 0x20
private const val CDC_SET_CONTROL_LINE_STATE = 0x22

/** DTR and RTS both asserted. Many CDC devices stay silent until they see this. */
private const val CDC_DTR_AND_RTS = 0x03
private const val CONTROL_TRANSFER_TIMEOUT_MS = 2000

@Suppress("DEPRECATION")
private fun Intent.getUsbDevice(): UsbDevice? =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
    } else {
        getParcelableExtra(UsbManager.EXTRA_DEVICE)
    }
