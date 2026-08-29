package com.masterclock.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.masterclock.app.R
import com.masterclock.app.logic.ChessTimerViewModel
import com.masterclock.app.logic.ConnectionState

@Composable
fun BluetoothBoardScreen(
    viewModel: ChessTimerViewModel,
    onBack: () -> Unit
) {
    val manager = viewModel.bluetoothManager
    val usbManager = viewModel.usbBoardManager
    val bluetoothState by manager.connectionState.collectAsState()
    val usbState by usbManager.connectionState.collectAsState()
    val scannedDevices by manager.scannedDevices.collectAsState()
    val usbDevices by usbManager.attachedDevices.collectAsState()
    val bluetoothMove by manager.lastMove.collectAsState()
    val usbMove by usbManager.lastMove.collectAsState()
    val context = LocalContext.current

    // Only one board is connected at a time, so whichever transport is not idle is the one this
    // screen is talking about.
    val connectionState = if (usbState !is ConnectionState.Idle) usbState else bluetoothState
    val lastMove = if (usbState !is ConnectionState.Idle) usbMove else bluetoothMove

    // USB needs no scanning, only a look at what is plugged in -- but a board can be plugged in
    // while the screen is open, so this reruns on every recomposition of the device list.
    LaunchedEffect(Unit) { usbManager.refreshDevices() }

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        // On Android 11 and below, location is unfortunately required for scanning
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            manager.startScan()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            manager.stopScan()
        }
    }

    ToolScaffold(
        title = stringResource(R.string.board_title),
        onBack = onBack,
        actions = {
            if (connectionState is ConnectionState.Scanning) {
                IconButton(onClick = { manager.stopScan() }) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else if (connectionState is ConnectionState.Idle) {
                IconButton(onClick = {
                    usbManager.refreshDevices()
                    val missing = permissions.filter {
                        ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isEmpty()) {
                        manager.startScan()
                    } else {
                        permissionLauncher.launch(missing.toTypedArray())
                    }
                }) {
                    Icon(Icons.Default.Refresh, stringResource(R.string.board_scan))
                }
            }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
            // Status Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when (connectionState) {
                        is ConnectionState.Connected -> MaterialTheme.colorScheme.primaryContainer
                        is ConnectionState.Error -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (connectionState is ConnectionState.Connected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        val statusText = when (val s = connectionState) {
                            ConnectionState.Idle -> stringResource(R.string.board_disconnected)
                            ConnectionState.Scanning -> stringResource(R.string.board_scanning)
                            ConnectionState.Connecting -> stringResource(R.string.board_connecting)
                            is ConnectionState.Connected -> stringResource(R.string.board_connected_to, s.deviceName)
                            is ConnectionState.Error -> stringResource(R.string.board_error, s.message)
                        }
                        Text(statusText, fontWeight = FontWeight.Bold)
                        if (connectionState is ConnectionState.Connected) {
                            Text(stringResource(R.string.board_connected), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (connectionState is ConnectionState.Connected) {
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            if (usbState !is ConnectionState.Idle) usbManager.disconnect() else manager.disconnect()
                        }) {
                            Text(stringResource(R.string.board_disconnect))
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            if (lastMove != null) {
                Text(stringResource(R.string.board_last_move, lastMove.orEmpty()), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
            }

            Text(stringResource(R.string.board_usb_section), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            if (usbDevices.isEmpty()) {
                Text(stringResource(R.string.board_usb_none), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    stringResource(R.string.board_usb_charging_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                usbDevices.forEach { candidate ->
                    Surface(
                        onClick = { usbManager.connect(candidate.id) { viewModel.recordBoardMove(it) } },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Usb, null)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(candidate.label, fontWeight = FontWeight.Bold)
                                Text(candidate.detail, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(stringResource(R.string.board_nearby), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            if (scannedDevices.isEmpty() && connectionState is ConnectionState.Scanning) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.board_looking))
                    }
                }
            } else if (scannedDevices.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.board_none_found), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(scannedDevices) { device ->
                        Surface(
                            onClick = { manager.connect(device.device) { viewModel.recordBoardMove(it) } },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.BluetoothSearching, null)
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(device.device.name ?: stringResource(R.string.board_unknown_device), fontWeight = FontWeight.Bold)
                                    Text(device.device.address, style = MaterialTheme.typography.bodySmall)
                                }
                                Text("${device.rssi} dBm", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
