package com.masterclock.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ModeGuideScreen(onBack: () -> Unit) {
    ToolScaffold(
        title = "Timing Engines Manual",
        onBack = onBack
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            
            Text(
                "Exhaustive documentation of MasterClock timing engines and sub-modes, following the interface sequence.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 1. SUDDEN DEATH
            EngineSection("Sudden Death") {
                EngineItem("Standard", "Pure countdown. The first player to reach 0:00 loses or flags.")
            }

            // 2. BONUS (FISHER / BRONSTEIN / US DELAY)
            EngineSection("Bonus Engines") {
                EngineItem("Fisher", "Adds a fixed amount of time (increment) AFTER every move.")
                EngineItem("Bronstein", "Adds back time spent on the move, but never more than the delay setting. Non-cumulative.")
                EngineItem("US Delay", "A countdown delay. The clock waits for the delay duration before starting to tick.")
            }

            // 3. MOVE TIMER
            EngineSection("Move Timer") {
                EngineItem("Standard", "Resets to a fixed duration for every single move.")
                EngineItem("Save & Cap", "Unused move time is stored in a bank, limited by a maximum capacity.")
                EngineItem("Overtime", "Adds unused move time to a secondary global reserve.")
                EngineItem("Global", "Move time and global time tick down simultaneously. Both must stay above zero.")
                EngineItem("Shared (Auto)", "A single timer for all players. Automatically switches at 0:00.")
                EngineItem("Global Shared", "All players share a global reserve while having a personal move timer.")
            }

            // 4. BYOYOMI
            EngineSection("Byoyomi") {
                EngineItem("Japanese", "After main time, players get a series of short periods. Reset period on move.")
                EngineItem("Canadian", "Players must complete a specific number of moves within a single longer period.")
                EngineItem("Progressive", "The number of required moves increases every time a period is completed.")
            }

            // 5. CHRONOS
            EngineSection("Chronos") {
                EngineItem("Countdown", "Master timer counting down for the whole game.")
                EngineItem("Countup", "Stopwatch mode tracking total time elapsed.")
                EngineItem("One for All", "System-wide timer that doesn't switch when players press buttons.")
            }

            // 6. MOVE COUNTS
            EngineSection("Move Counts") {
                EngineItem("Count Up", "Displays the total number of moves made since the start.")
                EngineItem("Count Down", "Starts at a limit and counts down to zero moves remaining.")
            }

            // 7. SPECIALTY ENGINES
            EngineSection("Specialty Engines") {
                EngineItem("Hourglass", "One player's time loss is added to the opponent. Total time is constant.")
                EngineItem("Gong Mode", "Forced moves at fixed intervals (Simultaneous or Turn-based).")
                EngineItem("FIDE Periods", "Multi-stage tournament timing (e.g., 40 moves in 90m + 30m bonus).")
                EngineItem("Phases", "Sequence of different timers (e.g., Prep 5m -> Game 20m -> Sudden Death).")
            }

            // 8. EXPERIMENTAL & OMNI
            EngineSection("Advanced & Experimental") {
                EngineItem("Random", "Base time and increments are randomized within a set range.")
                EngineItem("Hidden", "Masks the clock digits at specific thresholds to increase tension.")
                EngineItem("Fast Move", "Dynamic speed adjustments based on move frequency or cumulative penalty.")
                EngineItem("Omni-Timer", "6-layer professional station for complex game structures (Games > Rounds > Turns > Phases).")
            }

            Spacer(Modifier.height(64.dp))
        }
    }
}

@Composable
private fun EngineSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // MasterClock Label Style: Simple, bold, noir et blanc
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        Column(
            modifier = Modifier.padding(start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun EngineItem(name: String, description: String) {
    Column {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )
    }
}
