package com.masterclock.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onPresetSelected: (ChessClockSettings, List<PlayerStateProxy>?) -> Unit,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    
    val presets = listOf(
        ClockPreset("1 min", "Sudden Death 1:00", ChessClockSettings(main = PlayerSettings(initialTimeMs = 60_000, mode = TimerMode.SUDDEN_DEATH))),
        ClockPreset("5 min", "Sudden Death 5:00", ChessClockSettings(main = PlayerSettings(initialTimeMs = 300_000, mode = TimerMode.SUDDEN_DEATH))),
        ClockPreset("10 min", "Sudden Death 10:00", ChessClockSettings(main = PlayerSettings(initialTimeMs = 600_000, mode = TimerMode.SUDDEN_DEATH))),
        ClockPreset("30 min", "Sudden Death 30:00", ChessClockSettings(main = PlayerSettings(initialTimeMs = 1_800_000, mode = TimerMode.SUDDEN_DEATH))),
        ClockPreset("60 min", "Sudden Death 60:00", ChessClockSettings(main = PlayerSettings(initialTimeMs = 3_600_000, mode = TimerMode.SUDDEN_DEATH))),
        ClockPreset("Fisch. 3 + 2s", "Fischer 3:00 +2s", ChessClockSettings(main = PlayerSettings(initialTimeMs = 180_000, incrementMs = 2_000, mode = TimerMode.FISHER))),
        ClockPreset("Fisch. 15 + 10s", "Fischer 15:00 +10s", ChessClockSettings(main = PlayerSettings(initialTimeMs = 900_000, incrementMs = 10_000, mode = TimerMode.FISHER))),
        ClockPreset("90'/40 + 30' + 30s", "90min/40 + 30min (Fischer 30s)", ChessClockSettings(main = PlayerSettings(mode = TimerMode.FIDE_PERIODS, fidePeriods = listOf(FidePeriod(timeMs = 5400_000, incrementMs = 30_000, movesToNext = 40, isFischer = true), FidePeriod(timeMs = 1800_000, incrementMs = 30_000, movesToNext = 0, isFischer = true))))),
        ClockPreset("120'/40 + 60'/20 + 15' + 30s", "120/40 + 60/20 + 15min (Fischer 30s)", ChessClockSettings(main = PlayerSettings(mode = TimerMode.FIDE_PERIODS, fidePeriods = listOf(FidePeriod(timeMs = 7200_000, incrementMs = 30_000, movesToNext = 40, isFischer = true), FidePeriod(timeMs = 3600_000, incrementMs = 30_000, movesToNext = 60, isFischer = true), FidePeriod(timeMs = 900_000, incrementMs = 30_000, movesToNext = 0, isFischer = true))))),
        ClockPreset("US 5 + 2s", "Sudden Death 5:00, delay 2s", ChessClockSettings(main = PlayerSettings(initialTimeMs = 300_000, incrementMs = 2_000, mode = TimerMode.US_DELAY))),
        ClockPreset("US 25 + 5s", "Sudden Death 25:00, delay 5s", ChessClockSettings(main = PlayerSettings(initialTimeMs = 1_500_000, incrementMs = 5_000, mode = TimerMode.US_DELAY))),
        ClockPreset("US 80'/40 + 30' + 30s", "80min/40 + 30min (Delay 30s)", ChessClockSettings(main = PlayerSettings(mode = TimerMode.FIDE_PERIODS, fidePeriods = listOf(FidePeriod(timeMs = 4800_000, incrementMs = 30_000, movesToNext = 40, isFischer = false), FidePeriod(timeMs = 1800_000, incrementMs = 30_000, movesToNext = 0, isFischer = false))))),
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
                title = { Text("Presets", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) { Text("All Presets", Modifier.padding(12.dp)) }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) { Text("Last Games", Modifier.padding(12.dp)) }
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
            } else {
                val lastTen = remember(history) { history.take(10) }
                if (lastTen.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No games played yet.", style = MaterialTheme.typography.bodyLarge)
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
                                log.settings.main.mode.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
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
}

@Composable
fun PresetCard(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier.padding(6.dp).heightIn(min = 36.dp),
            contentAlignment = Alignment.Center
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
}
