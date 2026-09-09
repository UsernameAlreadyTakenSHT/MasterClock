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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.masterclock.app.logic.*
import com.masterclock.app.R
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

        SettingsSection(stringResource(R.string.settings_more_help)) {
            ToolCard(
                title = stringResource(R.string.settings_more_manual),
                icon = Icons.AutoMirrored.Filled.HelpCenter,
                topRounded = true,
                bottomRounded = true
            ) {
                onCategoryChanged(SettingsCategory.MORE)
                onToolClick(Route.ModeGuide)
            }
        }

        SettingsSection(stringResource(R.string.settings_more_game_tools)) {
            Column {
                ToolCard(
                    title = stringResource(R.string.settings_more_variants),
                    icon = Icons.Default.Shuffle,
                    topRounded = true
                ) {
                    onCategoryChanged(SettingsCategory.MORE)
                    onToolClick(Route.Chess960)
                }
                ToolCard(
                    title = stringResource(R.string.settings_more_rules),
                    icon = Icons.AutoMirrored.Filled.MenuBook
                ) {
                    onCategoryChanged(SettingsCategory.MORE)
                    onToolClick(Route.Rules)
                }
                ToolCard(
                    title = stringResource(R.string.settings_more_scoreboard),
                    icon = Icons.Default.Leaderboard
                ) {
                    onCategoryChanged(SettingsCategory.MORE)
                    onToolClick(Route.Scoreboard)
                }
                ToolCard(
                    title = stringResource(R.string.settings_more_notebook),
                    icon = Icons.AutoMirrored.Filled.NoteAdd,
                    bottomRounded = true
                ) {
                    onCategoryChanged(SettingsCategory.MORE)
                    onToolClick(Route.Notebook)
                }
            }
        }

        SettingsSection(stringResource(R.string.settings_more_more_tools)) {
            Column {
                ToolCard(
                    title = stringResource(R.string.settings_more_coin_toss),
                    icon = Icons.Default.MonetizationOn,
                    topRounded = true
                ) {
                    onCategoryChanged(SettingsCategory.MORE)
                    onToolClick(Route.CoinToss)
                }
                ToolCard(
                    title = stringResource(R.string.settings_more_dice),
                    icon = Icons.Default.Casino
                ) {
                    onCategoryChanged(SettingsCategory.MORE)
                    onToolClick(Route.DiceRoll)
                }
                ToolCard(
                    title = stringResource(R.string.settings_more_straw),
                    icon = Icons.Default.HorizontalRule
                ) {
                    onCategoryChanged(SettingsCategory.MORE)
                    onToolClick(Route.ShortStraw)
                }
                ToolCard(
                    title = stringResource(R.string.settings_more_card),
                    icon = Icons.Default.Style
                ) {
                    onCategoryChanged(SettingsCategory.MORE)
                    onToolClick(Route.RandomCard)
                }
                ToolCard(
                    title = stringResource(R.string.settings_more_stop),
                    icon = Icons.Default.Timer10
                ) {
                    onCategoryChanged(SettingsCategory.MORE)
                    onToolClick(Route.StopPrecision)
                }
                ToolCard(
                    title = stringResource(R.string.settings_more_blindfold),
                    icon = Icons.Default.VisibilityOff
                ) {
                    onCategoryChanged(SettingsCategory.MORE)
                    onToolClick(Route.BlindfoldTrainer)
                }
                ToolCard(
                    title = stringResource(R.string.settings_more_knight),
                    icon = Icons.Default.Extension
                ) {
                    onCategoryChanged(SettingsCategory.MORE)
                    onToolClick(Route.KnightPath)
                }
                ToolCard(
                    title = stringResource(R.string.settings_more_name_square),
                    icon = Icons.Default.Grid4x4,
                    bottomRounded = true
                ) {
                    onCategoryChanged(SettingsCategory.MORE)
                    onToolClick(Route.NameSquare)
                }
            }
        }

        SettingsSection(stringResource(R.string.settings_more_board)) {
            Column {
                ToolCard(
                    title = stringResource(R.string.settings_more_link_board),
                    icon = Icons.Default.Bluetooth,
                    topRounded = true
                ) {
                    onCategoryChanged(SettingsCategory.MORE)
                    onToolClick(Route.BluetoothBoard)
                }
                BehaviorSwitch(stringResource(R.string.settings_more_auto_switch), currentSettings.autoSwitchOnBoardMove) { onSettingsChanged(currentSettings.copy(autoSwitchOnBoardMove = it)) }
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.settings_more_game), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    // Shogi is withheld for now rather than removed -- but it stays listed for
                    // anyone who had already chosen it, who would otherwise be left on a setting
                    // they can neither see nor leave.
                    val games = GameType.entries.filter { it.isOfferable(currentSettings.gameType) }
                    SingleChoiceSegmentedButtonRow {
                        games.forEachIndexed { i, gt ->
                            SegmentedButton(
                                selected = currentSettings.gameType == gt,
                                onClick = { onSettingsChanged(currentSettings.copy(gameType = gt)) },
                                shape = SegmentedButtonDefaults.itemShape(i, games.size),
                                label = { Text(gt.label(), style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        }

        SettingsSection(stringResource(R.string.settings_more_game_data)) {
            Column {
                ToolCard(
                    title = stringResource(R.string.settings_more_logs),
                    icon = Icons.Default.History,
                    topRounded = true
                ) {
                    onCategoryChanged(SettingsCategory.MORE)
                    onToolClick(Route.GameLogs)
                }

                ToolCard(
                    title = stringResource(R.string.stats_title),
                    icon = Icons.Default.BarChart
                ) {
                    onCategoryChanged(SettingsCategory.MORE)
                    onToolClick(Route.Statistics)
                }

                val limitByCount = currentSettings.logHistoryLimit != -1
                val limitByAge = currentSettings.logDurationLimit != LogDurationLimit.INFINITE

                BehaviorSwitch(
                    label = stringResource(R.string.settings_more_limit_count),
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
                    label = stringResource(R.string.settings_more_limit_age),
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
                                    LogDurationLimit.ONE_DAY -> stringResource(R.string.age_one_day)
                                    LogDurationLimit.ONE_WEEK -> stringResource(R.string.age_one_week)
                                    LogDurationLimit.ONE_MONTH -> stringResource(R.string.age_one_month)
                                    LogDurationLimit.SIX_MONTHS -> stringResource(R.string.age_six_months)
                                    LogDurationLimit.ONE_YEAR -> stringResource(R.string.age_one_year)
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

        SettingsSection(stringResource(R.string.settings_more_data_sharing)) {
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
                        Text(stringResource(R.string.settings_more_export))
                    }
                    Button(
                        onClick = { showImportPopup = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_more_import))
                    }
                }

                // Hoisted out of the coroutine: lint forbids reading resources off LocalContext there.
                val preparingApkText = stringResource(R.string.toast_preparing_apk)
                val shareApkViaText = stringResource(R.string.settings_more_share_apk_via)
                val apkErrorText = stringResource(R.string.toast_apk_error)

                OutlinedButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            try {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(localContext, preparingApkText, Toast.LENGTH_SHORT).show()
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
                                    localContext.startActivity(Intent.createChooser(intent, shareApkViaText))
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("SettingsScreen", "Failed to share APK", e)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(localContext, apkErrorText, Toast.LENGTH_LONG).show()
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
                    Text(stringResource(R.string.settings_more_share_apk), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
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

        SettingsSection(stringResource(R.string.settings_more_danger)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onClearLogs() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(stringResource(R.string.settings_more_clear_logs), style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = { onResetSettings() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(stringResource(R.string.settings_more_reset_settings), style = MaterialTheme.typography.labelMedium)
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
            Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            Text(AppInfo.BUILD_DATE, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
        Spacer(Modifier.height(64.dp))
    }

    if (showChangelog) {
        ChangelogCreditsDialog(onDismiss = { showChangelog = false })
    }
}
