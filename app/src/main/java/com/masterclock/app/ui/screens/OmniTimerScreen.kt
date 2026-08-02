package com.masterclock.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masterclock.app.logic.*
import java.util.Locale

@Composable
fun OmniTimerScreen(
    viewModel: OmniTimerViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.omniState.collectAsState()
    val settings by viewModel.omniSettings.collectAsState()
    var showResetDialog by remember { mutableStateOf(false) }
    
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Session?") },
            text = { Text("All progress across rounds and games will be lost.") },
            confirmButton = {
                TextButton(onClick = { 
                    viewModel.resetOmni()
                    showResetDialog = false 
                }) { Text("RESET", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("CANCEL") }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            
            OmniHeader(state, settings, onBack)
            
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isLaunching -> {
                        LaunchCountdownOverlay(state.launchTimeRemainingMs)
                    }
                    state.isInTransition -> {
                        TransitionOverlay(state, settings, onReady = {
                            if (state.transitionLabel == "SESSION") {
                                // "CLOSE SESSION": the session is over — reset to a fresh initial
                                // state so Play starts a new session, instead of just clearing the
                                // overlay and letting Play "resume" a dead one with all clocks at 0.
                                viewModel.resetOmni()
                            } else {
                                viewModel.confirmOmniReady()
                            }
                        })
                    }
                    else -> {
                        ActiveTimerLayout(
                            state = state, 
                            settings = settings, 
                            onAdvance = { viewModel.advanceOmni() }
                        )
                    }
                }
            }
            
            OmniControls(
                state = state,
                onPauseResume = { if (state.isRunning) viewModel.pauseOmni() else viewModel.startOmni() },
                onStop = { viewModel.stopOmni() },
                onReset = { showResetDialog = true }
            )
        }
    }
}

@Composable
fun OmniHeader(state: OmniState, settings: OmniSettings, onBack: () -> Unit) {
    Surface(
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                val currentGame = settings.games.getOrNull(state.currentGameIndex) ?: settings.games.firstOrNull() ?: OmniGameSettings()
                val currentRound = currentGame.rounds.getOrNull(state.currentRoundIndex) ?: currentGame.rounds.firstOrNull() ?: OmniRoundSettings()
                Text("Omni: ${currentRound.name}", fontWeight = FontWeight.Bold)
            }
            
            if (settings.useGlobalClock) {
                OmniProgressBar(label = "Session", timeMs = state.currentGlobalTimeMs, totalMs = settings.globalDurationMs, color = Color(0xFF2196F3))
            }
            if (settings.useGameClock) {
                // Each game has its own duration (editable per game in the wizard); using the
                // global gameDurationMs default here made the fraction wrong whenever they differ.
                val gameTotal = settings.games.getOrNull(state.currentGameIndex)?.durationMs ?: settings.gameDurationMs
                OmniProgressBar(label = "Game ${state.currentGameIndex + 1}/${settings.games.size}", timeMs = state.currentGameTimeMs, totalMs = gameTotal, color = Color(0xFF4CAF50))
            }
            if (settings.useRoundClock) {
                val currentGame = settings.games.getOrNull(state.currentGameIndex) ?: settings.games.firstOrNull() ?: OmniGameSettings()
                val roundTotal = currentGame.rounds.getOrNull(state.currentRoundIndex)?.durationMs ?: 0L
                OmniProgressBar(label = "Round ${state.currentRoundIndex + 1}/${currentGame.rounds.size}", timeMs = state.currentRoundTimeMs, totalMs = roundTotal, color = Color(0xFFFF9800))
            }
        }
    }
}

@Composable
fun OmniProgressBar(label: String, timeMs: Long, totalMs: Long, color: Color) {
    val progress = if (totalMs > 0) timeMs.toFloat() / totalMs else 0f
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(formatTime(timeMs), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            color = color,
            trackColor = color.copy(alpha = 0.2f)
        )
    }
}

@Composable
fun LaunchCountdownOverlay(timeRemainingMs: Long) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("GET READY", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(16.dp))
            val seconds = (timeRemainingMs / 1000) + 1
            Text("$seconds", fontSize = 120.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun ActiveTimerLayout(state: OmniState, settings: OmniSettings, onAdvance: () -> Unit) {
    val numPlayers = settings.numberOfPlayers.coerceAtLeast(1)
    val pIdx = state.currentPlayerIndex % numPlayers
    val pColor = Color(omniPlayerColor(settings, pIdx))

    // turnCounterInRound is the raw, ever-incrementing turn count within the round; currentPlayerIndex
    // is always already in [0, numPlayers) so dividing it here was always 0 ("TURN 1" forever) -- see
    // AUDIT.md §7.1.
    val turnNumber = (state.turnCounterInRound / numPlayers) + 1
    val playerNumber = pIdx + 1

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // --- Player Indicator ---
        Surface(
            color = pColor.copy(alpha = 0.1f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, null, tint = pColor, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    val currentGame = settings.games.getOrNull(state.currentGameIndex) ?: settings.games.firstOrNull() ?: OmniGameSettings()
                    val currentRound = currentGame.rounds.getOrNull(state.currentRoundIndex) ?: currentGame.rounds.firstOrNull() ?: OmniRoundSettings()
                    val totalTurnsInRound = if (currentRound.turnLogic == RoundTurnLogic.SEQUENCE) currentRound.customTurns.size else numPlayers
                    val turnsPerPlayer = totalTurnsInRound / numPlayers
                    
                    if (turnsPerPlayer > 1 || currentRound.roundEndBehavior == RoundEndBehavior.LOOP) {
                        Text("TURN $turnNumber", style = MaterialTheme.typography.labelSmall, color = pColor)
                    }
                    Text("PLAYER $playerNumber", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = pColor)
                }
            }
        }
        
        if (settings.useTurnClock) {
            Text(formatTime(state.currentTurnTimeMs), fontSize = 84.sp, fontWeight = FontWeight.Black, letterSpacing = (-2).sp)
        }

        if (settings.timeBankMode != TimeBankMode.NONE) {
            // The current player's own bank is always drawn into their turn clock as the turn
            // starts, so showing just their entry was permanently 0. Show the total still held
            // in reserve (other players' banks / the shared pool) instead.
            val bankedMs = state.playerTimeBanks.values.sum()
            if (bankedMs > 0) {
                Text(
                    "+${formatTime(bankedMs)} in reserve",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = pColor,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        if (settings.usePhaseClock) {
            val currentGame = settings.games.getOrNull(state.currentGameIndex) ?: settings.games.firstOrNull() ?: OmniGameSettings()
            val currentRound = currentGame.rounds.getOrNull(state.currentRoundIndex) ?: currentGame.rounds.firstOrNull() ?: OmniRoundSettings()
            val turnsList = if (currentRound.turnLogic == RoundTurnLogic.SEQUENCE) currentRound.customTurns else List(numPlayers) { OmniTurnSettings(durationMs = currentRound.turnDurationMs) }
            // turnsList (SEQUENCE mode) is indexed by the raw turn count within the round, same as the
            // engine (OmniTimerViewModel.advanceOmni), not by currentPlayerIndex -- see AUDIT.md §7.1.
            val currentTurn = turnsList.getOrNull(state.turnCounterInRound)
            val phaseName = currentTurn?.phases?.getOrNull(state.currentPhaseIndex)?.name ?: "Phase"
            
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("$phaseName:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(formatTime(state.currentPhaseTimeMs), fontWeight = FontWeight.Black)
                }
            }
        }

        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onAdvance,
            modifier = Modifier.size(110.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = pColor),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
        ) {
            Icon(Icons.Default.SkipNext, "Next", modifier = Modifier.size(44.dp))
        }
    }
}

@Composable
fun TransitionOverlay(state: OmniState, settings: OmniSettings, onReady: () -> Unit) {
    val title = when(state.transitionLabel) {
        "ROUND" -> "ROUND FINISHED"
        "GAME" -> "GAME FINISHED"
        "SESSION" -> "SESSION COMPLETE"
        else -> "TURN FINISHED"
    }
    
    val numPlayers = settings.numberOfPlayers.coerceAtLeast(1)
    val nextPlayerNumber = (state.currentPlayerIndex % numPlayers) + 1

    val message = when(state.transitionLabel) {
        "ROUND" -> "Prepare for Round ${state.currentRoundIndex + 1}"
        "GAME" -> "Prepare for Game ${state.currentGameIndex + 1}"
        "SESSION" -> "Thank you for playing!"
        else -> "Next: Player $nextPlayerNumber"
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.98f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Text(message, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            
            if (state.transitionLabel != "SESSION") {
                Text(formatTime(state.transitionTimeRemainingMs), fontSize = 72.sp, fontWeight = FontWeight.Black)
                
                if (settings.transitionType == TransitionType.MANUAL_READY) {
                    Button(
                        onClick = onReady, 
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.height(56.dp).fillMaxWidth(0.6f)
                    ) {
                        Text("I AM READY", fontWeight = FontWeight.Black)
                    }
                }
            } else {
                Button(onClick = onReady, shape = RoundedCornerShape(16.dp), modifier = Modifier.height(56.dp)) {
                    Text("CLOSE SESSION", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun OmniControls(state: OmniState, onPauseResume: () -> Unit, onStop: () -> Unit, onReset: () -> Unit) {
    BottomAppBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        actions = {
            IconButton(onClick = onPauseResume) {
                Icon(
                    if (state.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (state.isRunning) "Pause" else "Resume"
                )
            }
            IconButton(onClick = onStop) {
                Icon(Icons.Default.Stop, "Stop the session")
            }
            IconButton(onClick = onReset) {
                Icon(Icons.Default.Refresh, "Reset the session", tint = MaterialTheme.colorScheme.error)
            }
            
            Spacer(Modifier.weight(1f))
            
            Text(
                text = if (state.isRunning) "RUNNING" else "PAUSED",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = if (state.isRunning) Color(0xFF4CAF50) else Color.Gray,
                modifier = Modifier.padding(end = 16.dp)
            )
        }
    )
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
