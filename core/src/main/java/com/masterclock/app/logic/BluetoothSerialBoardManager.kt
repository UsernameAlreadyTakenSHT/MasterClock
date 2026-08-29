package com.masterclock.app.logic

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import java.io.IOException
import java.util.UUID

/**
 * A board reached over Bluetooth Classic, as a virtual serial port.
 *
 * The third transport, and it exists for one make: DGT's Bluetooth e-Boards are not BLE
 * peripherals. They present a Serial Port Profile, which Windows shows as a COM port and Android
 * reaches through an RFCOMM socket -- so [BluetoothBoardManager] and its GATT services cannot see
 * them at all. DGT stopped selling them, but a board that is no longer made is still a board
 * someone owns.
 *
 * What arrives here is the same byte protocol as over USB, framing included, which is why this is
 * only a transport: [BoardProtocol] needs nothing new for it.
 *
 * There is no scanning. An SPP board is paired once in the system's Bluetooth settings and shows up
 * here afterwards; discovering an unpaired one would not help, since pairing cannot be completed
 * from inside an app anyway.
 */
class BluetoothSerialBoardManager(private val context: Context) {

    private companion object {
        /** The well-known Serial Port Profile service. Every SPP device answers on it. */
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val READ_BUFFER_BYTES = 256
    }

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<BoardCandidate>>(emptyList())
    val pairedDevices: StateFlow<List<BoardCandidate>> = _pairedDevices.asStateFlow()

    private val _lastMove = MutableStateFlow<String?>(null)
    val lastMove: StateFlow<String?> = _lastMove.asStateFlow()

    private var protocol: BoardProtocol = RawCaptureProtocol
    private val moveTracker = BoardMoveTracker()
    private var assembler: StreamAssembler? = null
    private var socket: BluetoothSocket? = null
    private var readJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun hasConnectPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true // BLUETOOTH is an install-time permission below Android 12.
        }

    fun refreshPairedDevices() {
        if (!hasConnectPermission()) {
            _pairedDevices.value = emptyList()
            return
        }
        _pairedDevices.value = adapter?.bondedDevices.orEmpty().map { device ->
            BoardCandidate(
                id = device.address,
                label = device.name ?: device.address,
                detail = device.address,
                kind = BoardTransportKind.BLUETOOTH_SERIAL,
            )
        }
    }

    fun connect(candidateId: String, onMoveReceived: (String) -> Unit) {
        if (!hasConnectPermission()) {
            _connectionState.value = ConnectionState.Error("Bluetooth connect permission required")
            return
        }
        val device = adapter?.bondedDevices.orEmpty().firstOrNull { it.address == candidateId }
        if (device == null) {
            _connectionState.value = ConnectionState.Error("That board is no longer paired")
            return
        }

        protocol = BoardProtocols.forDeviceName(device.name)
        assembler = protocol.framing?.let { StreamAssembler(it) }
        moveTracker.reset()
        _connectionState.value = ConnectionState.Connecting

        readJob?.cancel()
        readJob = scope.launch { openAndRead(device, onMoveReceived) }
    }

    private suspend fun openAndRead(device: BluetoothDevice, onMoveReceived: (String) -> Unit) {
        val opened = try {
            // Discovery is expensive and slows every connection attempt down while it runs; Android
            // asks for it to be stopped before opening a socket.
            @Suppress("MissingPermission")
            adapter?.cancelDiscovery()
            @Suppress("MissingPermission")
            device.createRfcommSocketToServiceRecord(SPP_UUID).also { it.connect() }
        } catch (e: IOException) {
            withContext(Dispatchers.Main) {
                _connectionState.value = ConnectionState.Error("Could not open the board: ${e.message ?: "no answer"}")
            }
            return
        } catch (e: SecurityException) {
            withContext(Dispatchers.Main) {
                _connectionState.value = ConnectionState.Error("Bluetooth connect permission required")
            }
            return
        }

        socket = opened
        withContext(Dispatchers.Main) {
            _connectionState.value = ConnectionState.Connected(device.name ?: device.address)
        }

        // Several makes report nothing until asked; DGT wants a dump before update mode.
        protocol.initCommand?.let { command ->
            runCatching { opened.outputStream.write(command); opened.outputStream.flush() }
        }

        val input = opened.inputStream
        val buffer = ByteArray(READ_BUFFER_BYTES)
        while (scope.isActive && readJob?.isActive == true) {
            val read = try {
                input.read(buffer)
            } catch (e: IOException) {
                // The board went out of range or was switched off; that is a disconnect, not a bug.
                break
            }
            if (read <= 0) break

            val chunk = buffer.copyOf(read)
            val payloads = assembler?.offer(chunk) ?: listOf(chunk)
            val moves = payloads.flatMap { moveTracker.onReport(protocol.decode(it)) }
            if (moves.isEmpty()) continue
            withContext(Dispatchers.Main) {
                moves.forEach { move ->
                    _lastMove.value = move
                    onMoveReceived(move)
                }
            }
        }

        withContext(Dispatchers.Main) { disconnect() }
    }

    fun disconnect() {
        readJob?.cancel()
        readJob = null
        runCatching { socket?.close() }
        socket = null
        assembler?.reset()
        _connectionState.value = ConnectionState.Idle
    }

    fun release() {
        disconnect()
        scope.cancel()
    }
}
