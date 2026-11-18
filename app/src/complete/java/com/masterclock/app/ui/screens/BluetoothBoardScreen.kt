package com.masterclock.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
    val serialManager = viewModel.bluetoothSerialBoardManager
    val simulatedManager = viewModel.simulatedBoardManager
    val simulatedState by simulatedManager.connectionState.collectAsState()
    val simulatedMove by simulatedManager.lastMove.collectAsState()
    val bluetoothState by manager.connectionState.collectAsState()
    val usbState by usbManager.connectionState.collectAsState()
    val serialState by serialManager.connectionState.collectAsState()
    val scannedDevices by manager.scannedDevices.collectAsState()
    val usbDevices by usbManager.attachedDevices.collectAsState()
    val pairedDevices by serialManager.pairedDevices.collectAsState()
    val bluetoothMove by manager.lastMove.collectAsState()
    val usbMove by usbManager.lastMove.collectAsState()
    val serialMove by serialManager.lastMove.collectAsState()
    val usbNeedsCalibration by usbManager.needsCalibration.collectAsState()
    val serialNeedsCalibration by serialManager.needsCalibration.collectAsState()
    val context = LocalContext.current
    val framesLogged by viewModel.boardFrameLog.count.collectAsState()
    val shareLogLabel = stringResource(R.string.board_share_log)

    // Which game this is decides what the board is told to set up, so it is read at the moment of
    // connecting rather than assumed to be chess.
    val settings by viewModel.settings.collectAsState()
    val gameType = settings.gameType

    // Only one board is connected at a time, which nothing used to see to: each transport let go of
    // its own alone, so a DGT on USB could be connected over Bluetooth as well, and one move then
    // switched the clock twice.
    val dropAll = {
        manager.disconnect()
        usbManager.disconnect()
        serialManager.disconnect()
        simulatedManager.disconnect()
    }

    // Whichever transport is not idle is the one this screen is talking about.
    val connectionState = when {
        simulatedState !is ConnectionState.Idle -> simulatedState
        usbState !is ConnectionState.Idle -> usbState
        serialState !is ConnectionState.Idle -> serialState
        else -> bluetoothState
    }
    val lastMove = when {
        simulatedState !is ConnectionState.Idle -> simulatedMove
        usbState !is ConnectionState.Idle -> usbMove
        serialState !is ConnectionState.Idle -> serialMove
        else -> bluetoothMove
    }

    // Neither USB nor a paired serial board needs scanning: one is plugged in, the other was paired
    // in the system settings. Both are simply looked up.
    LaunchedEffect(Unit) {
        usbManager.refreshDevices()
        serialManager.refreshPairedDevices()
    }

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
            // The paired list too: it needs the same Bluetooth permission, so on a first visit it
            // came up empty and stayed so until the scan button was pressed a second time.
            serialManager.refreshPairedDevices()
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
            // What the board has said, for whoever is to make sense of it: a board that connects
            // and gives no moves, or a make the app does not know. Offered once there is something.
            if (framesLogged > 0) {
                IconButton(onClick = { shareBoardFrameLog(context, viewModel.boardFrameLog, shareLogLabel) }) {
                    Icon(Icons.Default.Share, contentDescription = shareLogLabel)
                }
            }
            if (connectionState is ConnectionState.Scanning) {
                IconButton(onClick = { manager.stopScan() }) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            // Errors keep the button, and that is the point: the commonest one is "Bluetooth is
            // disabled", which the user then goes and fixes. Offering the scan only from Idle left
            // them with the message that told them what to do and no way to try again afterwards.
            } else if (connectionState !is ConnectionState.Connected && connectionState !is ConnectionState.Connecting) {
                IconButton(onClick = {
                    // Whichever transport failed last, this is the user asking to start over. A
                    // stale error from one of them would otherwise keep the whole screen showing a
                    // failure while another transport was busy succeeding.
                    manager.clearError()
                    usbManager.clearError()
                    serialManager.clearError()
                    usbManager.refreshDevices()
                    serialManager.refreshPairedDevices()
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
                            is ConnectionState.Error -> stringResource(R.string.board_error, stringResource(s.reason.text))
                        }
                        Text(statusText, fontWeight = FontWeight.Bold)
                        if (connectionState is ConnectionState.Connected) {
                            Text(stringResource(R.string.board_connected), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    // While connecting too: the boards are out of reach until an attempt ends, which
                    // is half a minute for one that does not answer.
                    if (connectionState is ConnectionState.Connected || connectionState is ConnectionState.Connecting) {
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { dropAll() }) {
                            Text(stringResource(R.string.board_disconnect))
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Until a tag-reading board has been shown its pieces it is connected, talking, and
            // producing nothing at all. Without this the screen would look like a board that simply
            // does not work.
            if (connectionState is ConnectionState.Connected && (usbNeedsCalibration || serialNeedsCalibration)) {
                Text(
                    stringResource(R.string.board_needs_calibration),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
            }

            if (lastMove != null) {
                Text(stringResource(R.string.board_last_move, lastMove.orEmpty()), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
            }

            SimulatedBoards(
                enabled = connectionState !is ConnectionState.Connecting,
                onMake = { make -> dropAll(); simulatedManager.connect(make) { viewModel.recordBoardMove(it) } },
                onLog = { frames -> dropAll(); simulatedManager.replay(frames) { viewModel.recordBoardMove(it) } },
            )

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
                        onClick = { dropAll(); usbManager.connect(candidate.id, gameType) { viewModel.recordBoardMove(it) } },
                        enabled = connectionState !is ConnectionState.Connecting,
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

            Text(stringResource(R.string.board_paired_section), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            if (pairedDevices.isEmpty()) {
                Text(stringResource(R.string.board_paired_none), style = MaterialTheme.typography.bodyMedium)
            } else {
                // Every device paired with the phone is listed -- headphones, a car, a watch -- and the
                // screen does not scroll, so a long list pushed the nearby boards below off the screen.
                // The list scrolls within a bounded height instead.
                Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                pairedDevices.forEach { candidate ->
                    Surface(
                        onClick = { dropAll(); serialManager.connect(candidate.id, gameType) { viewModel.recordBoardMove(it) } },
                        enabled = connectionState !is ConnectionState.Connecting,
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bluetooth, null)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(candidate.label, fontWeight = FontWeight.Bold)
                                Text(candidate.detail, style = MaterialTheme.typography.bodySmall)
                            }
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
                            onClick = { dropAll(); manager.connect(device.device, gameType) { viewModel.recordBoardMove(it) } },
                            enabled = connectionState !is ConnectionState.Connecting,
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

/**
 * Hands the board's frame log to another app as a text file. A file and not the text itself: a
 * long session is several hundred kilobytes, more than an intent carries. Under the cache's
 * `shares/`, which the file provider serves, and the ones shared before go as this one is written.
 */
private fun shareBoardFrameLog(context: android.content.Context, log: com.masterclock.app.logic.BoardFrameLog, title: String) {
    runCatching {
        val shares = java.io.File(context.cacheDir, "shares")
        shares.listFiles()?.filter { it.isDirectory && it.name.startsWith("board-log-") }?.forEach { it.deleteRecursively() }
        val dir = java.io.File(shares, "board-log-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val file = java.io.File(dir, "masterclock_board_log.txt")
        val about = "MasterClock ${com.masterclock.app.logic.AppInfo.CHANGELOG.first().version}, Android ${Build.VERSION.RELEASE}, ${Build.MANUFACTURER} ${Build.MODEL}"
        file.writeText(log.text(about))
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(send, title))
    }.onFailure { android.util.Log.w("BluetoothBoardScreen", "Could not share the board's log", it) }
}

/** As much of a log file as is read: a full log is a few hundred kilobytes. */
private const val MAX_LOG_FILE_BYTES = 2 * 1024 * 1024

/**
 * The boards that are not there: one of each make, playing a short game, and a log of a real board
 * played back. A test build's alone -- a player's list of boards holds boards. In English whatever
 * the language, as nothing a player reads.
 */
@Composable
private fun SimulatedBoards(
    enabled: Boolean,
    onMake: (com.masterclock.app.logic.BoardProtocol) -> Unit,
    onLog: (List<com.masterclock.app.logic.BoardFrameLog.Frame>) -> Boolean,
) {
    val context = LocalContext.current
    val debuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    if (!debuggable) return

    val pickLog = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // A file from anywhere: read to a bound, and parse() takes of it only what is a frame.
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = ByteArray(MAX_LOG_FILE_BYTES)
                var size = 0
                while (size < bytes.size) {
                    val read = input.read(bytes, size, bytes.size - size)
                    if (read <= 0) break
                    size += read
                }
                String(bytes, 0, size, Charsets.UTF_8)
            }
        }.getOrNull().orEmpty()
        if (!onLog(com.masterclock.app.logic.BoardFrameLog.parse(text))) {
            android.widget.Toast.makeText(context, "Nothing in this file from a board the app decodes", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    Text("Simulated boards (test builds only)", style = MaterialTheme.typography.titleSmall)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("simulated_boards"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        com.masterclock.app.logic.SimulatedBoard.makes.forEach { make ->
            AssistChip(onClick = { onMake(make) }, enabled = enabled, label = { Text(make.name) })
        }
        AssistChip(onClick = { pickLog.launch(arrayOf("text/*", "application/octet-stream")) }, enabled = enabled, label = { Text("Replay a log") })
    }
    Spacer(Modifier.height(16.dp))
}
