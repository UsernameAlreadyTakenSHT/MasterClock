package com.masterclock.app.logic

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattConnectionSettings
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

sealed class ConnectionState {
    object Idle : ConnectionState()
    object Scanning : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val deviceName: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

data class ScannedDevice(
    val device: BluetoothDevice,
    val rssi: Int
)

/** Client Characteristic Configuration, the descriptor every notification is switched on through. */
private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

/** Either flavour of server-pushed update; indication is the acknowledged one, and both are fine here. */
private const val NOTIFY_OR_INDICATE =
    BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE

class BluetoothBoardManager(private val context: Context) {
    // The BLE ATT MTU already caps a single characteristic update well under this (~512 bytes max),
    // so this is defense-in-depth against a malicious/buggy peripheral, not a real-world-reachable limit.
    private val MAX_CHARACTERISTIC_BYTES = 1024

    /** Which make the connected board speaks. See [BoardProtocol]. */
    private var protocol: BoardProtocol = RawCaptureProtocol

    /** The game this round is, for makes that have to be told before play starts. */
    private var gameType: GameType = GameType.CHESS

    /** Holds the previous position, since boards report state rather than moves. */
    private val moveTracker = BoardMoveTracker()

    /**
     * Reassembles messages for makes that need it, even here.
     *
     * A notification is usually a whole message, which is why Chessnut needs nothing. But a default
     * BLE payload is 20 bytes, and a ChessLink board reply is 67 -- so on that make a reply arrives
     * in three notifications and decoding them one at a time yields three malformed frames.
     */
    private var assembler: StreamAssembler? = null

    /**
     * Characteristics still waiting to have notifications turned on.
     *
     * They have to be done one at a time: Android allows a single outstanding GATT operation, and a
     * second descriptor write issued before [BluetoothGattCallback.onDescriptorWrite] arrives is
     * dropped silently. That is the classic reason a board connects, reports nothing, and gives no
     * error -- so the queue drains from the callback rather than in a loop.
     */
    private val pendingNotifySubscriptions = ArrayDeque<BluetoothGattCharacteristic>()

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter

    private fun hasScanPermission(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasConnectPermission(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else {
        // BLUETOOTH / BLUETOOTH_ADMIN are normal (install-time) permissions pre-S.
        true
    }
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()
    
    private val _lastMove = MutableStateFlow<String?>(null)
    val lastMove: StateFlow<String?> = _lastMove.asStateFlow()

    private var activeGatt: BluetoothGatt? = null
    private var _onMoveReceivedCallback: ((String) -> Unit)? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (device.name != null) {
                val existing = _scannedDevices.value.find { it.device.address == device.address }
                if (existing == null) {
                    _scannedDevices.value = _scannedDevices.value + ScannedDevice(device, result.rssi)
                } else {
                    // Update RSSI if it changed significantly
                    if (kotlin.math.abs(existing.rssi - result.rssi) > 5) {
                        _scannedDevices.value = _scannedDevices.value.map {
                            if (it.device.address == device.address) it.copy(rssi = result.rssi) else it
                        }
                    }
                }
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (!hasConnectPermission()) {
                    _connectionState.value = ConnectionState.Error("Bluetooth connect permission required")
                    return
                }
                _connectionState.value = ConnectionState.Connected(gatt.device.name ?: "Unknown Board")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // Android hands out a fixed number of GATT clients and only close() gives one back.
                // A board that goes out of range or is switched off arrives here, and without this
                // the client is kept forever: a handful of those and no further connection succeeds
                // at all, in this app or any other, until it is restarted.
                //
                // Only when we did not close it ourselves. disconnect() clears activeGatt after
                // closing, so this comparison is what tells an unsolicited drop from a deliberate
                // one and keeps the object from being closed twice.
                if (activeGatt === gatt) {
                    if (hasConnectPermission()) gatt.close()
                    activeGatt = null
                    pendingNotifySubscriptions.clear()
                }
                _connectionState.value = ConnectionState.Idle
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (!hasConnectPermission()) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = ConnectionState.Error("Could not read the board's services")
                return
            }

            // A service the board exposes is a fact; its advertised name is a guess. So if any make
            // names a service and this board has it, that make wins over whatever the name suggested.
            BoardProtocols.forAdvertisedServices(gatt.services.map { it.uuid })?.let { byService ->
                if (byService !== protocol) {
                    protocol = byService
                    assembler = protocol.framing?.let { StreamAssembler(it) }
                }
            }

            val services = protocol.ble?.serviceUuid
                ?.let { listOfNotNull(gatt.getService(it)) }
                ?: gatt.services
            val characteristics = services
                .flatMap { it.characteristics }
                .filter { protocol.ble?.notifyCharacteristicUuid == null || it.uuid == protocol.ble?.notifyCharacteristicUuid }
                .filter { it.properties and NOTIFY_OR_INDICATE != 0 }

            if (characteristics.isEmpty()) {
                _connectionState.value = ConnectionState.Error("This device reports nothing this app can listen to")
                return
            }

            pendingNotifySubscriptions.clear()
            pendingNotifySubscriptions.addAll(characteristics)
            subscribeToNextCharacteristic(gatt)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            // Failures are not fatal in raw-capture mode: a board may expose characteristics it will
            // not actually let us subscribe to, and the others still work.
            subscribeToNextCharacteristic(gatt)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            handlePayload(characteristic.value)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handlePayload(value)
        }
    }

    private fun handlePayload(payload: ByteArray?) {
        val bytes = payload?.take(MAX_CHARACTERISTIC_BYTES)?.toByteArray() ?: return
        val payloads = assembler?.offer(bytes) ?: listOf(bytes)
        payloads.forEach { message ->
            // Answer before acting on it: a board waiting for an acknowledgement sends nothing more
            // until it arrives, so a slow reply costs the next move, not just this one.
            protocol.replyTo(message)?.let { writeToBoard(it) }
            moveTracker.onReport(protocol.decode(message)).forEach { move ->
                _lastMove.value = move
                _onMoveReceivedCallback?.invoke(move)
            }
        }
    }

    /** Writes to whichever characteristic the make nominated for it. */
    private fun writeToBoard(bytes: ByteArray) {
        if (!hasConnectPermission()) return
        val gatt = activeGatt ?: return
        val uuid = protocol.ble?.writeCharacteristicUuid ?: return
        val characteristic = gatt.services.flatMap { it.characteristics }.firstOrNull { it.uuid == uuid } ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            run {
                characteristic.value = bytes
                gatt.writeCharacteristic(characteristic)
            }
        }
    }

    private fun subscribeToNextCharacteristic(gatt: BluetoothGatt) {
        if (!hasConnectPermission()) return
        val characteristic = pendingNotifySubscriptions.removeFirstOrNull()
        if (characteristic == null) {
            sendInitCommand(gatt)
            return
        }

        gatt.setCharacteristicNotification(characteristic, true)
        // setCharacteristicNotification only routes callbacks locally; the board is not told
        // anything until its CCCD is written.
        val cccd = characteristic.getDescriptor(CCCD_UUID)
        if (cccd == null) {
            subscribeToNextCharacteristic(gatt)
            return
        }

        val enable = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        }

        val written = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(cccd, enable) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run { cccd.value = enable; gatt.writeDescriptor(cccd) }
        }
        // Nothing will call back if the write was refused outright, so keep the queue moving.
        if (!written) subscribeToNextCharacteristic(gatt)
    }

    private fun sendInitCommand(gatt: BluetoothGatt) {
        protocol.initCommandFor(gameType)?.let { writeToBoard(it) }
    }

    fun startScan() {
        if (adapter == null) {
            _connectionState.value = ConnectionState.Error("Bluetooth not supported")
            return
        }
        if (!adapter.isEnabled) {
            _connectionState.value = ConnectionState.Error("Bluetooth is disabled. Please enable it.")
            return
        }
        if (!hasScanPermission()) {
            _connectionState.value = ConnectionState.Error("Bluetooth scan permission required")
            return
        }
        _scannedDevices.value = emptyList()
        _connectionState.value = ConnectionState.Scanning
        adapter.bluetoothLeScanner?.startScan(scanCallback)
    }

    fun stopScan() {
        if (!hasScanPermission()) return
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        if (_connectionState.value is ConnectionState.Scanning) {
            _connectionState.value = ConnectionState.Idle
        }
    }

    fun connect(device: BluetoothDevice, gameType: GameType, onMoveReceived: (String) -> Unit) {
        this.gameType = gameType
        if (!hasConnectPermission()) {
            _connectionState.value = ConnectionState.Error("Bluetooth connect permission required")
            return
        }
        // Tapping a second board without leaving the first used to overwrite activeGatt and lose
        // the old client for good. Nothing here is a no-op when there is nothing to close.
        disconnect()
        stopScan()
        _connectionState.value = ConnectionState.Connecting
        _onMoveReceivedCallback = onMoveReceived
        // Nothing implements a real make yet, so this resolves to raw capture for every board. Once
        // one does, the same call picks it up with no change here.
        protocol = BoardProtocols.forDeviceName(device.name)
        assembler = protocol.framing?.let { StreamAssembler(it) }
        moveTracker.reset()
        pendingNotifySubscriptions.clear()

        val connectionSettings = BluetoothGattConnectionSettings.Builder()
            .setTransport(BluetoothDevice.TRANSPORT_LE)
            .setAutoConnectEnabled(false)
            .build()
        activeGatt = device.connectGatt(connectionSettings, ContextCompat.getMainExecutor(context), gattCallback)
    }

    /**
     * Puts a failed attempt behind us, so the screen stops reporting it.
     *
     * Only an error is cleared: a scan or a live connection must survive this untouched. Without
     * it, a failure -- "Bluetooth is disabled" being the usual one -- stays on screen after the user
     * has gone and fixed exactly what it asked for.
     */
    fun clearError() {
        if (_connectionState.value is ConnectionState.Error) _connectionState.value = ConnectionState.Idle
    }

    fun disconnect() {
        if (hasConnectPermission()) {
            activeGatt?.disconnect()
            activeGatt?.close()
        }
        activeGatt = null
        pendingNotifySubscriptions.clear()
        _connectionState.value = ConnectionState.Idle
    }
}
