package com.masterclock.app.logic

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattConnectionSettings
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

class BluetoothBoardManager(private val context: Context) {
    // The BLE ATT MTU already caps a single characteristic update well under this (~512 bytes max),
    // so this is defense-in-depth against a malicious/buggy peripheral, not a real-world-reachable limit.
    private val MAX_CHARACTERISTIC_BYTES = 1024

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
                _connectionState.value = ConnectionState.Idle
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            // Here we would look for specific services/characteristics for DGT, ChessUp, etc.
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            val data = characteristic.value?.take(MAX_CHARACTERISTIC_BYTES)?.toByteArray()?.toString(Charsets.UTF_8) ?: ""
            _lastMove.value = data
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            val data = value.take(MAX_CHARACTERISTIC_BYTES).toByteArray().toString(Charsets.UTF_8)
            _lastMove.value = data
            _onMoveReceivedCallback?.invoke(data)
        }
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

    fun connect(device: BluetoothDevice, onMoveReceived: (String) -> Unit) {
        if (!hasConnectPermission()) {
            _connectionState.value = ConnectionState.Error("Bluetooth connect permission required")
            return
        }
        stopScan()
        _connectionState.value = ConnectionState.Connecting
        _onMoveReceivedCallback = onMoveReceived

        val connectionSettings = BluetoothGattConnectionSettings.Builder()
            .setTransport(BluetoothDevice.TRANSPORT_LE)
            .setAutoConnectEnabled(false)
            .build()
        activeGatt = device.connectGatt(connectionSettings, ContextCompat.getMainExecutor(context), gattCallback)
    }

    fun disconnect() {
        if (hasConnectPermission()) {
            activeGatt?.disconnect()
            activeGatt?.close()
        }
        activeGatt = null
        _connectionState.value = ConnectionState.Idle
    }
}
