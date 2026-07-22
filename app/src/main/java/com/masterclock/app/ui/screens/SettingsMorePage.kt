package com.masterclock.app.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.masterclock.app.logic.*
import com.masterclock.app.BuildConfig
import com.masterclock.app.ui.navigation.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun MoreSettingsPage(
    currentSettings: ChessClockSettings,
    onSettingsChanged: (ChessClockSettings) -> Unit,
    onClearLogs: () -> Unit,
    onResetSettings: () -> Unit,
    onExportSettings: (Boolean) -> Unit,
    onExportMedia: () -> Unit,
    onImportSettings: () -> Unit,
    onImportMedia: () -> Unit,
    onShareSettings: (Boolean, Boolean) -> Unit,
    onToolClick: (Route) -> Unit,
    onCategoryChanged: (SettingsCategory) -> Unit,
    scope: CoroutineScope
) {
    val localContext = LocalContext.current
    var showExportPopup by remember { mutableStateOf(false) }
    var showImportPopup by remember { mutableStateOf(false) }
    var showChangelog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(8.dp))

        SettingsSection("Help and Support") {
            ToolCard(
                title = "Timing Engines Manual",
                icon = Icons.AutoMirrored.Filled.HelpCenter,
                topRounded = true,
                bottomRounded = true
            ) {
                onCategoryChanged(SettingsCategory.MORE)
                onToolClick(Route.ModeGuide)
            }
        }

        SettingsSection("Game Tools") {
            Column {
                ToolCard(
                    title = "Chess variants generator",
                    icon = Icons.Default.Shuffle,
                    topRounded = true
                ) {
                    onCategoryChanged(SettingsCategory.MORE)
                    onToolClick(Route.Chess960)
                }
                ToolCard(
                    title = "Some rules",
                    icon = Icons.AutoMirrored.Filled.MenuBook
                ) {
                    onCategoryChanged(SettingsCategory.MORE)
                    onToolClick(Route.Rules)
                }
                ToolCard(
                    title = "Scoreboard",
                    icon = Icons.Default.Leaderboard
                ) {
                    onCategoryChanged(SettingsCategory.MORE)
                    onToolClick(Route.Scoreboard)
                }
                ToolCard(
                    title = "Notebook",
                    icon = Icons.AutoMirrored.Filled.NoteAdd,
                    bottomRounded = true
                ) {
                    onCategoryChanged(SettingsCategory.MORE)
                    onToolClick(Route.Notebook)
                }
            }
        }

        SettingsSection("More Tools") {
            Column {
                ToolCard(
                    title = "Coin Toss",
                    icon = Icons.Default.MonetizationOn,
                    topRounded = true
                ) {
                    onCategoryChanged(SettingsCategory.MORE)
                    onToolClick(Route.CoinToss)
                }
                ToolCard(
                    title = "Dice roll",
                    icon = Icons.Default.Casino
                ) {
                    onCategoryChanged(SettingsCategory.MORE)
                    onToolClick(Route.DiceRoll)
                }
                ToolCard(
                    title = "Short straw",
                    icon = Icons.Default.HorizontalRule
                ) {
                    onCategoryChanged(SettingsCategory.MORE)
                    onToolClick(Route.ShortStraw)
                }
                ToolCard(
                    title = "Random card",
                    icon = Icons.Default.Style
                ) {
                    onCategoryChanged(SettingsCategory.MORE)
                    onToolClick(Route.RandomCard)
                }
                ToolCard(
                    title = "Stop at right moment",
                    icon = Icons.Default.Timer10
                ) {
                    onCategoryChanged(SettingsCategory.MORE)
                    onToolClick(Route.StopPrecision)
                }
                ToolCard(
                    title = "Blindfold Trainer",
                    icon = Icons.Default.VisibilityOff
                ) {
                    onCategoryChanged(SettingsCategory.MORE)
                    onToolClick(Route.BlindfoldTrainer)
                }
                ToolCard(
                    title = "Knight's Path",
                    icon = Icons.Default.Extension
                ) {
                    onCategoryChanged(SettingsCategory.MORE)
                    onToolClick(Route.KnightPath)
                }
                ToolCard(
                    title = "Name the square",
                    icon = Icons.Default.Grid4x4,
                    bottomRounded = true
                ) {
                    onCategoryChanged(SettingsCategory.MORE)
                    onToolClick(Route.NameSquare)
                }
            }
        }

        SettingsSection("Board Connection") {
            Column {
                ToolCard(
                    title = "Link board (not implemented yet)",
                    icon = Icons.Default.Bluetooth,
                    topRounded = true
                ) {
                    onCategoryChanged(SettingsCategory.MORE)
                    onToolClick(Route.BluetoothBoard)
                }
                BehaviorSwitch("Auto switch turn on move", currentSettings.autoSwitchOnBoardMove) { onSettingsChanged(currentSettings.copy(autoSwitchOnBoardMove = it)) }
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Game", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    SingleChoiceSegmentedButtonRow {
                        GameType.entries.forEachIndexed { i, gt ->
                            SegmentedButton(
                                selected = currentSettings.gameType == gt,
                                onClick = { onSettingsChanged(currentSettings.copy(gameType = gt)) },
                                shape = SegmentedButtonDefaults.itemShape(i, GameType.entries.size),
                                label = { Text(gt.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        }

        SettingsSection("Game Data") {
            Column {
                ToolCard(
                    title = "Games logs & history",
                    icon = Icons.Default.History,
                    topRounded = true
                ) {
                    onCategoryChanged(SettingsCategory.MORE)
                    onToolClick(Route.GameLogs)
                }

                val limitByCount = currentSettings.logHistoryLimit != -1
                val limitByAge = currentSettings.logDurationLimit != LogDurationLimit.INFINITE

                BehaviorSwitch(
                    label = "Limit history by count",
                    checked = limitByCount
                ) { enabled ->
                    val newLimit = if (enabled) 100 else -1
                    onSettingsChanged(currentSettings.copy(logHistoryLimit = newLimit))
                }

                if (limitByCount) {
                    Surface(
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        val countOptions = listOf(10, 100, 1000, 10000)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            countOptions.forEachIndexed { i, limit ->
                                val text = if (limit >= 1000) "${limit/1000}k" else limit.toString()
                                SegmentedButton(
                                    selected = currentSettings.logHistoryLimit == limit,
                                    onClick = { onSettingsChanged(currentSettings.copy(logHistoryLimit = limit)) },
                                    shape = SegmentedButtonDefaults.itemShape(i, countOptions.size),
                                    label = { Text(text, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }

                BehaviorSwitch(
                    label = "Limit history by age",
                    checked = limitByAge,
                    bottomRounded = !limitByAge
                ) { enabled ->
                    val newLimit = if (enabled) LogDurationLimit.ONE_MONTH else LogDurationLimit.INFINITE
                    onSettingsChanged(currentSettings.copy(logDurationLimit = newLimit))
                }

                if (limitByAge) {
                    Surface(
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        val durationOptions = LogDurationLimit.entries.filter { it != LogDurationLimit.INFINITE }
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            durationOptions.forEachIndexed { i, d ->
                                val text = when(d) {
                                    LogDurationLimit.ONE_DAY -> "1d"
                                    LogDurationLimit.ONE_WEEK -> "1w"
                                    LogDurationLimit.ONE_MONTH -> "1m"
                                    LogDurationLimit.SIX_MONTHS -> "6m"
                                    LogDurationLimit.ONE_YEAR -> "1y"
                                    else -> ""
                                }
                                SegmentedButton(
                                    selected = currentSettings.logDurationLimit == d,
                                    onClick = { onSettingsChanged(currentSettings.copy(logDurationLimit = d)) },
                                    shape = SegmentedButtonDefaults.itemShape(i, durationOptions.size),
                                    label = { Text(text, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            }
        }

        SettingsSection("Data & Sharing") {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { showExportPopup = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.FileUpload, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Export / Share")
                    }
                    Button(
                        onClick = { showImportPopup = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Import / Scan")
                    }
                }

                OutlinedButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            try {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(localContext, "Preparing application file...", Toast.LENGTH_SHORT).show()
                                }

                                val pm = localContext.packageManager
                                val appInfo = pm.getApplicationInfo(localContext.packageName, 0)
                                val apkFile = File(appInfo.publicSourceDir)

                                val shareFolder = File(localContext.cacheDir, "apk_share")
                                if (!shareFolder.exists()) shareFolder.mkdirs()

                                val destinationFile = File(shareFolder, "MasterClock.apk")
                                apkFile.inputStream().use { input ->
                                    destinationFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }

                                val uri = FileProvider.getUriForFile(
                                    localContext,
                                    "${localContext.packageName}.fileprovider",
                                    destinationFile
                                )

                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/vnd.android.package-archive"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }

                                withContext(Dispatchers.Main) {
                                    localContext.startActivity(Intent.createChooser(intent, "Share APK via"))
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("SettingsScreen", "Failed to share APK", e)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(localContext, "Error: Could not prepare file", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Android, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share Application (APK)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }

            if (showExportPopup) {
                ExportDataDialog(
                    onDismiss = { showExportPopup = false },
                    onExportSettings = { includeLogs ->
                        onExportSettings(includeLogs)
                        showExportPopup = false
                    },
                    onExportAll = {
                        onExportMedia()
                        showExportPopup = false
                    },
                    onShare = { includeLogs, useQr ->
                        onShareSettings(includeLogs, useQr)
                        showExportPopup = false
                    }
                )
            }

            if (showImportPopup) {
                ImportDataDialog(
                    onDismiss = { showImportPopup = false },
                    onImportSettings = {
                        onImportSettings()
                        showImportPopup = false
                    },
                    onImportAll = {
                        onImportMedia()
                        showImportPopup = false
                    },
                    onScanQr = {
                        onToolClick(Route.QRReceive)
                        showImportPopup = false
                    }
                )
            }
        }

        SettingsSection("Danger Zone") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onClearLogs() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Clear logs", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = { onResetSettings() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Reset settings", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showChangelog = true }
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            Text(AppInfo.BUILD_DATE, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
        Spacer(Modifier.height(64.dp))
    }

    if (showChangelog) {
        ChangelogCreditsDialog(onDismiss = { showChangelog = false })
    }
}
