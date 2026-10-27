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
import java.util.*
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * A built-in preset. Its [name] is made of mode names, which stay as they are in every language;
 * the [description] says it in full, and is what TalkBack reads -- it was written and never shown,
 * so a screen reader heard "Fisch." and "Armag.". [setsFlagBehavior] marks a preset that is about
 * its flag behaviour; the others keep the player's, see applyPresetTimeControl.
 */
data class ClockPreset(
    val name: String,
    @param:StringRes val description: Int,
    val settings: ChessClockSettings,
    val setsFlagBehavior: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetsScreen(
    history: List<GameLog>,
    customPresets: List<SavedPreset>,
    /** The settings, the starting states for a game from Last Games, and whether it is a built-in, with whether that one sets the flag behaviour. */
    onPresetSelected: (ChessClockSettings, List<PlayerStateProxy>?, ClockPreset?) -> Unit,
    onSavePreset: (String) -> Unit,
    onRenamePreset: (String, String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onStatisticsClick: () -> Unit,
    onBack: () -> Unit
) {
    // Saveable, the presets by id: a dark-mode or font-size change recreated the screen on the
    // first tab with any dialog closed and the name being typed gone.
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    var renamingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    val presetBeingRenamed = customPresets.firstOrNull { it.id == renamingId }
    val presetBeingDeleted = customPresets.firstOrNull { it.id == deletingId }
    
    val presets = listOf(
        ClockPreset("1 min", R.string.preset_desc_1_min, ChessClockSettings(main = PlayerSettings(initialTimeMs = 60_000, mode = TimerMode.SUDDEN_DEATH))),
        ClockPreset("5 min", R.string.preset_desc_5_min, ChessClockSettings(main = PlayerSettings(initialTimeMs = 300_000, mode = TimerMode.SUDDEN_DEATH))),
        ClockPreset("10 min", R.string.preset_desc_10_min, ChessClockSettings(main = PlayerSettings(initialTimeMs = 600_000, mode = TimerMode.SUDDEN_DEATH))),
        ClockPreset("30 min", R.string.preset_desc_30_min, ChessClockSettings(main = PlayerSettings(initialTimeMs = 1_800_000, mode = TimerMode.SUDDEN_DEATH))),
        ClockPreset("60 min", R.string.preset_desc_60_min, ChessClockSettings(main = PlayerSettings(initialTimeMs = 3_600_000, mode = TimerMode.SUDDEN_DEATH))),
        ClockPreset("Fisch. 3 + 2s", R.string.preset_desc_fisch_3_2s, ChessClockSettings(main = PlayerSettings(initialTimeMs = 180_000, incrementMs = 2_000, mode = TimerMode.FISCHER))),
        ClockPreset("Fisch. 15 + 10s", R.string.preset_desc_fisch_15_10s, ChessClockSettings(main = PlayerSettings(initialTimeMs = 900_000, incrementMs = 10_000, mode = TimerMode.FISCHER))),
        ClockPreset("90'/40 + 30' + 30s", R.string.preset_desc_90_40_30_30s, ChessClockSettings(main = PlayerSettings(mode = TimerMode.FIDE_PERIODS, fidePeriods = listOf(FidePeriod(timeMs = 5400_000, incrementMs = 30_000, movesToNext = 40, isFischer = true), FidePeriod(timeMs = 1800_000, incrementMs = 30_000, movesToNext = 0, isFischer = true))))),
        ClockPreset("120'/40 + 60'/20 + 15' + 30s", R.string.preset_desc_120_40_60_20_15_30s, ChessClockSettings(main = PlayerSettings(mode = TimerMode.FIDE_PERIODS, fidePeriods = listOf(FidePeriod(timeMs = 7200_000, incrementMs = 30_000, movesToNext = 40, isFischer = true), FidePeriod(timeMs = 3600_000, incrementMs = 30_000, movesToNext = 60, isFischer = true), FidePeriod(timeMs = 900_000, incrementMs = 30_000, movesToNext = 0, isFischer = true))))),
        ClockPreset("US 5 + 2s", R.string.preset_desc_us_5_2s, ChessClockSettings(main = PlayerSettings(initialTimeMs = 300_000, incrementMs = 2_000, mode = TimerMode.US_DELAY))),
        ClockPreset("US 25 + 5s", R.string.preset_desc_us_25_5s, ChessClockSettings(main = PlayerSettings(initialTimeMs = 1_500_000, incrementMs = 5_000, mode = TimerMode.US_DELAY))),
        ClockPreset("US 80'/40 + 30' + 30s", R.string.preset_desc_us_80_40_30_30s, ChessClockSettings(main = PlayerSettings(mode = TimerMode.FIDE_PERIODS, fidePeriods = listOf(FidePeriod(timeMs = 4800_000, incrementMs = 30_000, movesToNext = 40, isFischer = false, hasDelay = true), FidePeriod(timeMs = 1800_000, incrementMs = 30_000, movesToNext = 0, isFischer = false, hasDelay = true))))),
        ClockPreset("Bronst. 90 + 5s", R.string.preset_desc_bronst_90_5s, ChessClockSettings(main = PlayerSettings(initialTimeMs = 5400_000, incrementMs = 5_000, mode = TimerMode.BRONSTEIN))),
        ClockPreset("Jap. Byo 20'", R.string.preset_desc_jap_byo_20, ChessClockSettings(main = PlayerSettings(initialTimeMs = 1200_000, byoyomiTimeMs = 30_000, byoyomiPeriods = 1, mode = TimerMode.BYOYOMI_JAPANESE))),
        ClockPreset("Jap. Byo 60'", R.string.preset_desc_jap_byo_60, ChessClockSettings(main = PlayerSettings(initialTimeMs = 3600_000, byoyomiTimeMs = 20_000, byoyomiPeriods = 3, mode = TimerMode.BYOYOMI_JAPANESE))),
        ClockPreset("Can. Byo 60'", R.string.preset_desc_can_byo_60, ChessClockSettings(main = PlayerSettings(initialTimeMs = 3600_000, byoyomiTimeMs = 300_000, byoyomiPeriods = 20, mode = TimerMode.BYOYOMI_CANADIAN))),
        // main is White's side too: Last Games names a game by it, and turning per-player settings
        // off after this preset left a 10-minute sudden death.
        ClockPreset("Armag. 5 / 4", R.string.preset_desc_armag_5_4, ChessClockSettings(differentSettingsPerPlayer = true, main = PlayerSettings(initialTimeMs = 300_000, mode = TimerMode.SUDDEN_DEATH), p1Custom = PlayerSettings(initialTimeMs = 300_000, mode = TimerMode.SUDDEN_DEATH), p2Custom = PlayerSettings(initialTimeMs = 240_000, mode = TimerMode.SUDDEN_DEATH))),
        ClockPreset("Hourgl. 1 min", R.string.preset_desc_hourgl_1_min, ChessClockSettings(main = PlayerSettings(initialTimeMs = 60_000, mode = TimerMode.HOURGLASS))),
        ClockPreset("30s / Move", R.string.preset_desc_30s_move, ChessClockSettings(main = PlayerSettings(moveTimeMs = 30_000, mode = TimerMode.MOVE_TIMER_STANDARD))),
        ClockPreset("Scrabble 25'", R.string.preset_desc_scrabble_25, 
            ChessClockSettings(main = PlayerSettings(initialTimeMs = 1_500_000, mode = TimerMode.SUDDEN_DEATH), flagBehavior = FlagBehavior.REVERSE),
            setsFlagBehavior = true,
        ),
        ClockPreset("Random (1/10)", R.string.preset_desc_random_1_10,
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
                        PresetCard(preset.name, description = stringResource(preset.description)) { onPresetSelected(preset.settings, null, preset) }
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
                                    onEdit = { renamingId = preset.id },
                                    onDelete = { deletingId = preset.id },
                                    onClick = { onPresetSelected(preset.settings, null, null) }
                                )
                            }
                        }
                    }
                }
            } else {
                // Standard and Lite have no More tab, so this is their only route to the
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
                        // Keyed on id rather than startTime, which an imported log controls
                        // verbatim; see the game history list for the crash that caused.
                        items(lastTen, key = { it.id }) { log ->
                            // Year first, as everywhere in the app: "dd/MM" read as month/day where
                            // that is the order. See isoDateTime.
                            val date = remember(log.startTime) { isoDateTime(log.startTime) }
                            // Player 1's mode, the game's, as the statistics read it: with per-player
                            // settings main can hold anything.
                            val modeName = remember(log.settings) {
                                (if (log.settings.differentSettingsPerPlayer) log.settings.p1Custom else log.settings.main).mode.displayName()
                            }
                            // The time control, not player 1's starting clock: that read "90m0s"
                            // for 1:30, counted a first-move bonus into the time -- 5+3 as "5m3s" --
                            // and never showed an increment, so 5+0 and 5+3 looked the same.
                            val timeInfo = remember(log.settings) { timeControlLabel(log.settings) }

                            PresetCard("$modeName\n$timeInfo\n($date)") {
                                onPresetSelected(log.settings, log.initialPlayerStates.ifEmpty { null }, null)
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
            onDismiss = { renamingId = null },
            onConfirm = { onRenamePreset(preset.id, it); renamingId = null }
        )
    }

    presetBeingDeleted?.let { preset ->
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text(stringResource(R.string.preset_delete_title, preset.name)) },
            text = { Text(stringResource(R.string.preset_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = { onDeletePreset(preset.id); deletingId = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = { TextButton(onClick = { deletingId = null }) { Text(stringResource(R.string.common_cancel)) } }
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
    description: String? = null,
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
                .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
        ) {
            Column(
                modifier = Modifier.padding(6.dp).heightIn(min = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = text,
                    // The description stands for it, when there is one.
                    modifier = if (description != null) Modifier.clearAndSetSemantics {} else Modifier,
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
    var name by rememberSaveable { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_PRESET_NAME_CHARS) },
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
