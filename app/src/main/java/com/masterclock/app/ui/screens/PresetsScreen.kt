package com.masterclock.app.ui.screens

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masterclock.app.R
import com.masterclock.app.logic.*
import java.text.SimpleDateFormat
import java.util.*

data class ClockPreset(
    val name: String,
    val description: String,
    val settings: ChessClockSettings
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetsScreen(
    history: List<GameLog>,
    customPresets: List<SavedPreset>,
    onPresetSelected: (ChessClockSettings, List<PlayerStateProxy>?) -> Unit,
    onSavePreset: (String) -> Unit,
    onRenamePreset: (String, String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onStatisticsClick: () -> Unit,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var presetBeingRenamed by remember { mutableStateOf<SavedPreset?>(null) }
    var presetBeingDeleted by remember { mutableStateOf<SavedPreset?>(null) }
    
    val presets = listOf(
        ClockPreset("1 min", "Sudden Death 1:00", ChessClockSettings(main = PlayerSettings(initialTimeMs = 60_000, mode = TimerMode.SUDDEN_DEATH))),
        ClockPreset("5 min", "Sudden Death 5:00", ChessClockSettings(main = PlayerSettings(initialTimeMs = 300_000, mode = TimerMode.SUDDEN_DEATH))),
        ClockPreset("10 min", "Sudden Death 10:00", ChessClockSettings(main = PlayerSettings(initialTimeMs = 600_000, mode = TimerMode.SUDDEN_DEATH))),
        ClockPreset("30 min", "Sudden Death 30:00", ChessClockSettings(main = PlayerSettings(initialTimeMs = 1_800_000, mode = TimerMode.SUDDEN_DEATH))),
        ClockPreset("60 min", "Sudden Death 60:00", ChessClockSettings(main = PlayerSettings(initialTimeMs = 3_600_000, mode = TimerMode.SUDDEN_DEATH))),
        ClockPreset("Fisch. 3 + 2s", "Fischer 3:00 +2s", ChessClockSettings(main = PlayerSettings(initialTimeMs = 180_000, incrementMs = 2_000, mode = TimerMode.FISCHER))),
        ClockPreset("Fisch. 15 + 10s", "Fischer 15:00 +10s", ChessClockSettings(main = PlayerSettings(initialTimeMs = 900_000, incrementMs = 10_000, mode = TimerMode.FISCHER))),
        ClockPreset("90'/40 + 30' + 30s", "90min/40 + 30min (Fischer 30s)", ChessClockSettings(main = PlayerSettings(mode = TimerMode.FIDE_PERIODS, fidePeriods = listOf(FidePeriod(timeMs = 5400_000, incrementMs = 30_000, movesToNext = 40, isFischer = true), FidePeriod(timeMs = 1800_000, incrementMs = 30_000, movesToNext = 0, isFischer = true))))),
        ClockPreset("120'/40 + 60'/20 + 15' + 30s", "120/40 + 60/20 + 15min (Fischer 30s)", ChessClockSettings(main = PlayerSettings(mode = TimerMode.FIDE_PERIODS, fidePeriods = listOf(FidePeriod(timeMs = 7200_000, incrementMs = 30_000, movesToNext = 40, isFischer = true), FidePeriod(timeMs = 3600_000, incrementMs = 30_000, movesToNext = 60, isFischer = true), FidePeriod(timeMs = 900_000, incrementMs = 30_000, movesToNext = 0, isFischer = true))))),
        ClockPreset("US 5 + 2s", "Sudden Death 5:00, delay 2s", ChessClockSettings(main = PlayerSettings(initialTimeMs = 300_000, incrementMs = 2_000, mode = TimerMode.US_DELAY))),
        ClockPreset("US 25 + 5s", "Sudden Death 25:00, delay 5s", ChessClockSettings(main = PlayerSettings(initialTimeMs = 1_500_000, incrementMs = 5_000, mode = TimerMode.US_DELAY))),
        ClockPreset("US 80'/40 + 30' + 30s", "80min/40 + 30min (Delay 30s)", ChessClockSettings(main = PlayerSettings(mode = TimerMode.FIDE_PERIODS, fidePeriods = listOf(FidePeriod(timeMs = 4800_000, incrementMs = 30_000, movesToNext = 40, isFischer = false, hasDelay = true), FidePeriod(timeMs = 1800_000, incrementMs = 30_000, movesToNext = 0, isFischer = false, hasDelay = true))))),
        ClockPreset("Bronst. 90 + 5s", "Bronstein 90:00, delay 5s", ChessClockSettings(main = PlayerSettings(initialTimeMs = 5400_000, incrementMs = 5_000, mode = TimerMode.BRONSTEIN))),
        ClockPreset("Jap. Byo 20'", "Byoyomi Japanese: 20min + 1x30s", ChessClockSettings(main = PlayerSettings(initialTimeMs = 1200_000, byoyomiTimeMs = 30_000, byoyomiPeriods = 1, mode = TimerMode.BYOYOMI_JAPANESE))),
        ClockPreset("Jap. Byo 60'", "Byoyomi Japanese: 60min + 3x20s", ChessClockSettings(main = PlayerSettings(initialTimeMs = 3600_000, byoyomiTimeMs = 20_000, byoyomiPeriods = 3, mode = TimerMode.BYOYOMI_JAPANESE))),
        ClockPreset("Can. Byo 60'", "Byoyomi Canadian: 60min + 5min/20 moves", ChessClockSettings(main = PlayerSettings(initialTimeMs = 3600_000, byoyomiTimeMs = 300_000, byoyomiPeriods = 20, mode = TimerMode.BYOYOMI_CANADIAN))),
        ClockPreset("Armag. 5 / 4", "White 5:00, Black 4:00", ChessClockSettings(differentSettingsPerPlayer = true, p1Custom = PlayerSettings(initialTimeMs = 300_000, mode = TimerMode.SUDDEN_DEATH), p2Custom = PlayerSettings(initialTimeMs = 240_000, mode = TimerMode.SUDDEN_DEATH))),
        ClockPreset("Hourgl. 1 min", "Hourglass 1:00 each", ChessClockSettings(main = PlayerSettings(initialTimeMs = 60_000, mode = TimerMode.HOURGLASS))),
        ClockPreset("30s / Move", "Move Timer Standard 30s/move", ChessClockSettings(main = PlayerSettings(moveTimeMs = 30_000, mode = TimerMode.MOVE_TIMER_STANDARD))),
        ClockPreset(
            "Scrabble 25'", "Sudden Death 25:00, flag reverse", 
            ChessClockSettings(main = PlayerSettings(initialTimeMs = 1_500_000, mode = TimerMode.SUDDEN_DEATH), flagBehavior = FlagBehavior.REVERSE)
        ),
        ClockPreset(
            "Random (1/10)", "Random 1-10 min, Rounded",
            ChessClockSettings(main = PlayerSettings(mode = TimerMode.RANDOM, randomMinTimeMs = 60_000, randomMaxTimeMs = 600_000, randomMinIncMs = 0, randomMaxIncMs = 0, roundedTime = true))
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.preset_title), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) } }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) { Text(stringResource(R.string.preset_tab_all), Modifier.padding(12.dp), maxLines = 1) }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) { Text(stringResource(R.string.preset_tab_mine), Modifier.padding(12.dp), maxLines = 1) }
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) { Text(stringResource(R.string.preset_tab_last_games), Modifier.padding(12.dp), maxLines = 1) }
            }

            if (selectedTab == 0) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(presets, key = { it.name }) { preset ->
                        PresetCard(preset.name) { onPresetSelected(preset.settings, null) }
                    }
                }
            } else if (selectedTab == 1) {
                Column(Modifier.fillMaxSize()) {
                    Button(
                        onClick = { showSaveDialog = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.preset_save_current))
                    }

                    if (customPresets.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(R.string.preset_empty_custom),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    } else {
                        Text(
                            stringResource(R.string.preset_long_press_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(customPresets, key = { it.id }) { preset ->
                                PresetCard(
                                    text = preset.name,
                                    onEdit = { presetBeingRenamed = preset },
                                    onDelete = { presetBeingDeleted = preset },
                                    onClick = { onPresetSelected(preset.settings, null) }
                                )
                            }
                        }
                    }
                }
            } else {
                // Standard and Light have no More tab, so this is their only route to the
                // statistics screen; Complete additionally lists it under More > Game Data.
                if (FlavorConfig.hasStatistics()) {
                    TextButton(
                        onClick = onStatisticsClick,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)
                    ) {
                        Icon(Icons.Default.BarChart, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.preset_view_statistics))
                    }
                }

                val lastTen = remember(history) { history.take(10) }
                if (lastTen.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.preset_empty_history), style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(lastTen, key = { it.startTime }) { log ->
                            val date = remember(log.startTime) {
                                SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(log.startTime))
                            }
                            val modeName = remember(log.settings.main.mode) {
                                log.settings.main.mode.displayName()
                            }
                            
                            val timeInfo = remember(log.initialPlayerStates) {
                                log.initialPlayerStates.firstOrNull()?.let {
                                    val seconds = it.timeRemainingMs / 1000
                                    val m = seconds / 60
                                    val s = seconds % 60
                                    if (m > 0) "${m}m${s}s" else "${s}s"
                                } ?: ""
                            }

                            PresetCard("$modeName\n$timeInfo\n($date)") {
                                onPresetSelected(log.settings, log.initialPlayerStates.ifEmpty { null })
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        PresetNameDialog(
            title = stringResource(R.string.preset_save_title),
            initialName = "",
            confirmLabel = stringResource(R.string.common_save),
            onDismiss = { showSaveDialog = false },
            onConfirm = { onSavePreset(it); showSaveDialog = false }
        )
    }

    presetBeingRenamed?.let { preset ->
        PresetNameDialog(
            title = stringResource(R.string.preset_rename_title),
            initialName = preset.name,
            confirmLabel = stringResource(R.string.common_rename),
            onDismiss = { presetBeingRenamed = null },
            onConfirm = { onRenamePreset(preset.id, it); presetBeingRenamed = null }
        )
    }

    presetBeingDeleted?.let { preset ->
        AlertDialog(
            onDismissRequest = { presetBeingDeleted = null },
            title = { Text(stringResource(R.string.preset_delete_title, preset.name)) },
            text = { Text(stringResource(R.string.preset_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = { onDeletePreset(preset.id); presetBeingDeleted = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = { TextButton(onClick = { presetBeingDeleted = null }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}

/**
 * A preset tile. [onEdit]/[onDelete] are only supplied by the user's own presets and are reached by
 * long-pressing the tile, so the card stays the same size as the built-in ones; leaving them null
 * keeps the built-in and Last Games grids exactly as they were.
 */
@Composable
fun PresetCard(
    text: String,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val hasActions = onEdit != null || onDelete != null
    val manageLabel = stringResource(R.string.preset_manage_action)
    var menuOpen by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(12.dp)

    Box {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = if (hasActions) {
                        {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuOpen = true
                        }
                    } else null,
                    onLongClickLabel = if (hasActions) manageLabel else null
                )
        ) {
            Column(
                modifier = Modifier.padding(6.dp).heightIn(min = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    lineHeight = 14.sp
                )
            }
        }

        if (hasActions) {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                onEdit?.let { edit ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_rename)) },
                        leadingIcon = { Icon(Icons.Default.Edit, null, Modifier.size(18.dp)) },
                        onClick = { menuOpen = false; edit() }
                    )
                }
                onDelete?.let { delete ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = { menuOpen = false; delete() }
                    )
                }
            }
        }
    }
}

/** Shared by "save current" and "rename": a single named text field in a dialog. */
@Composable
private fun PresetNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.common_name)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}
