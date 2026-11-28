package com.masterclock.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masterclock.app.R
import com.masterclock.app.logic.*
import java.util.Locale

@Composable
fun OmniTimerScreen(
    viewModel: OmniTimerViewModel,
    pauseOnBackground: Boolean,
    onBack: () -> Unit
) {
    val state by viewModel.omniState.collectAsState()
    val settings by viewModel.omniSettings.collectAsState()
    // Saved, as the clock's questions are: a theme or a fold changing closed them as if Cancel had been pressed.
    var showResetDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    // The same question for Stop and Reset, each in its own words: Stop asked "Reset Session?".
    var stopping by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    // Leaving the screen pauses the session. It ran on behind the rest of the app -- the clocks
    // counting down and the cues sounding from the timer or the settings -- with nothing on screen to
    // stop it. A recreation (rotation, theme) is not leaving.
    val activity = androidx.activity.compose.LocalActivity.current
    DisposableEffect(Unit) {
        onDispose { if (activity?.isChangingConfigurations != true) viewModel.pauseOmni() }
    }
    // As the clock does. Going to the background disposes nothing, so a session went on counting
    // and sounding with the phone locked, whatever "Pause when in background" said.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val pauseWhenLeft by androidx.compose.runtime.rememberUpdatedState(pauseOnBackground)
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            // Not for a recreation either, as onDispose above has it: a tablet or a foldable ignores
            // the portrait lock, and turning one paused the session.
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE && pauseWhenLeft && activity?.isChangingConfigurations != true) viewModel.pauseOmni()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(if (stopping) R.string.omni_stop_title else R.string.omni_reset_title)) },
            text = { Text(stringResource(R.string.omni_reset_message)) },
            confirmButton = {
                TextButton(onClick = {
                    if (stopping) viewModel.stopOmni() else viewModel.resetOmni()
                    showResetDialog = false
                }) { Text(stringResource(if (stopping) R.string.omni_stop_confirm else R.string.omni_reset_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text(stringResource(R.string.omni_cancel)) }
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
                // Stop ends the session as Reset does, and sat beside Pause with no question asked.
                onStop = { if (state.hasStarted) { stopping = true; showResetDialog = true } else viewModel.stopOmni() },
                // Asked once a session has begun, as Stop is: "All progress will be lost" stood
                // over a session with none.
                onReset = { stopping = false; if (state.hasStarted) showResetDialog = true else viewModel.resetOmni() }
            )
        }
        OmniAnnouncer(state, settings)
    }
}

/**
 * What TalkBack is told without being asked: whose turn it is and the phase, a pause and what comes
 * after it, the countdown before the start, a session paused. The screen said none of it -- no live
 * region anywhere -- so a player who cannot see it heard nothing as the turn passed or a pause began,
 * and had to feel round the screen to find out whose turn it was. The words are the screen's own.
 *
 * One node, there from the start, its text changing: a live region speaks when its text does, and
 * one that only appears with its first announcement may say nothing for it. See the clock's
 * ClockAnnouncer for the same reasoning.
 */
@Composable
private fun OmniAnnouncer(state: OmniState, settings: OmniSettings) {
    val message = when {
        state.isLaunching -> stringResource(R.string.omni_get_ready)
        state.isInTransition -> omniTransitionTitle(state) + ". " + omniTransitionMessage(state, settings)
        !state.hasStarted -> ""
        !state.isRunning -> stringResource(R.string.a11y_announce_paused)
        else -> {
            val player = stringResource(R.string.common_player_n, state.currentPlayerIndex % settings.numberOfPlayers.coerceAtLeast(1) + 1)
            val phase = if (settings.usePhaseClock) {
                omniTurnsOf(omniCurrentRound(settings, state), settings.numberOfPlayers)
                    .getOrNull(state.turnCounterInRound)?.phases?.getOrNull(state.currentPhaseIndex)?.name
            } else null
            if (phase != null) "$player, $phase" else player
        }
    }
    Box(
        Modifier
            .zIndex(1f)
            .size(1.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = message
            }
    )
}

/** What the pause is after, as its overlay heads it. */
@Composable
private fun omniTransitionTitle(state: OmniState): String = when (state.transitionLabel) {
    "ROUND" -> stringResource(R.string.omni_round_finished)
    "GAME" -> stringResource(R.string.omni_game_finished)
    "SESSION" -> stringResource(R.string.omni_session_complete)
    else -> stringResource(R.string.omni_turn_finished)
}

/** What comes after the pause, as its overlay says it. */
@Composable
private fun omniTransitionMessage(state: OmniState, settings: OmniSettings): String = when (state.transitionLabel) {
    "ROUND" -> stringResource(R.string.omni_prepare_round, state.currentRoundIndex + 1)
    "GAME" -> stringResource(R.string.omni_prepare_game, state.currentGameIndex + 1)
    "SESSION" -> stringResource(R.string.omni_thanks)
    else -> stringResource(R.string.omni_next_player, (state.currentPlayerIndex % settings.numberOfPlayers.coerceAtLeast(1)) + 1)
}

@Composable
fun OmniHeader(state: OmniState, settings: OmniSettings, onBack: () -> Unit) {
    Surface(
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
                Text(stringResource(R.string.omni_header, omniCurrentRound(settings, state).name), fontWeight = FontWeight.Bold)
            }

            if (settings.useGlobalClock) {
                OmniProgressBar(label = stringResource(R.string.omni_progress_session), timeMs = state.currentGlobalTimeMs, totalMs = settings.globalDurationMs, color = Color(0xFF2196F3))
            }
            if (settings.useGameClock) {
                // Each game has its own duration (editable per game in the wizard); using the
                // global gameDurationMs default here made the fraction wrong whenever they differ.
                val gameTotal = settings.games.getOrNull(state.currentGameIndex)?.durationMs ?: settings.gameDurationMs
                // Held to the list: a paused session's games can be edited, and it read "Game 2/1".
                OmniProgressBar(label = stringResource(R.string.omni_progress_game, (state.currentGameIndex + 1).coerceAtMost(settings.games.size), settings.games.size), timeMs = state.currentGameTimeMs, totalMs = gameTotal, color = Color(0xFF4CAF50))
            }
            if (settings.useRoundClock) {
                val currentGame = omniCurrentGame(settings, state)
                val roundTotal = currentGame.rounds.getOrNull(state.currentRoundIndex)?.durationMs ?: 0L
                OmniProgressBar(label = stringResource(R.string.omni_progress_round, (state.currentRoundIndex + 1).coerceAtMost(currentGame.rounds.size), currentGame.rounds.size), timeMs = state.currentRoundTimeMs, totalMs = roundTotal, color = Color(0xFFFF9800))
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
            Text(stringResource(R.string.omni_get_ready), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(16.dp))
            // Rounded up, so a ten-second countdown starts at 10 and reaches 1, not 11 then 1.
            val seconds = (timeRemainingMs + 999) / 1000
            Text("$seconds", fontSize = 120.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun ActiveTimerLayout(state: OmniState, settings: OmniSettings, onAdvance: () -> Unit) {
    val numPlayers = settings.numberOfPlayers.coerceAtLeast(1)
    val pIdx = state.currentPlayerIndex % numPlayers
    val pColor = Color(omniPlayerColor(settings, pIdx))

    val turnNumber = omniTurnNumber(settings, state)
    val playerNumber = pIdx + 1

    Column(
        // It scrolls where it does not fit -- a short screen, a large font, the three bars on --
        // rather than cut the Next button off at the bottom; centred as before where it does.
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
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
                    val currentRound = omniCurrentRound(settings, state)
                    val totalTurnsInRound = omniTurnsOf(currentRound, numPlayers).size
                    val turnsPerPlayer = totalTurnsInRound / numPlayers

                    if (turnsPerPlayer > 1 || currentRound.roundEndBehavior == RoundEndBehavior.LOOP) {
                        Text(stringResource(R.string.omni_cfg_turn_n, turnNumber).uppercase(androidx.compose.ui.platform.LocalConfiguration.current.locales[0]), style = MaterialTheme.typography.labelSmall, color = pColor)
                    }
                    Text(stringResource(R.string.common_player_n, playerNumber).uppercase(androidx.compose.ui.platform.LocalConfiguration.current.locales[0]), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = pColor)
                }
            }
        }

        if (settings.useTurnClock) {
            // On one line, smaller where it must be: from an hour up the reading is wider than a
            // 360 dp screen at this size, and broke in two.
            OneLineText(
                // Once the turn's own time is out it runs on the shared reserve, and the clock with it:
                // it stood at 00:00 while the player went on playing. See omniSharedPoolMs.
                formatTime(if (state.currentTurnTimeMs > 0) state.currentTurnTimeMs else omniTurnLeftMs(state, settings)),
                style = LocalTextStyle.current.copy(fontSize = 84.sp),
                minSize = 36.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-2).sp,
            )
        }

        if (settings.timeBankMode != TimeBankMode.NONE) {
            // The current player's own bank is always drawn into their turn clock as the turn
            // starts, so showing just their entry was permanently 0. Show the total still held
            // in reserve (other players' banks / the shared pool) instead.
            // Only banks that belong to someone: a player removed from a paused session keeps none.
            val bankedMs = state.playerTimeBanks.filterKeys { it == OMNI_GLOBAL_TIME_BANK_KEY || it < numPlayers }.values.sum()
            if (bankedMs > 0) {
                Text(
                    stringResource(R.string.omni_in_reserve, formatTime(bankedMs)),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = pColor,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        if (settings.usePhaseClock) {
            val turnsList = omniTurnsOf(omniCurrentRound(settings, state), numPlayers)
            // turnsList (SEQUENCE mode) is indexed by the raw turn count within the round, same as the
            // engine (OmniTimerViewModel.advanceOmni), not by currentPlayerIndex.
            val currentTurn = turnsList.getOrNull(state.turnCounterInRound)
            val phaseName = currentTurn?.phases?.getOrNull(state.currentPhaseIndex)?.name ?: stringResource(R.string.omni_phase_default)

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
            Icon(Icons.Default.SkipNext, stringResource(R.string.omni_next), modifier = Modifier.size(44.dp))
        }
    }
}

@Composable
fun TransitionOverlay(state: OmniState, settings: OmniSettings, onReady: () -> Unit) {
    val title = omniTransitionTitle(state)
    val message = omniTransitionMessage(state, settings)

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
                        Text(stringResource(R.string.omni_i_am_ready), fontWeight = FontWeight.Black)
                    }
                }
            } else {
                Button(onClick = onReady, shape = RoundedCornerShape(16.dp), modifier = Modifier.height(56.dp)) {
                    Text(stringResource(R.string.omni_close_session), fontWeight = FontWeight.Black)
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
                    when {
                        state.isRunning -> stringResource(R.string.omni_pause)
                        state.hasStarted -> stringResource(R.string.timer_resume)
                        else -> stringResource(R.string.timer_start)
                    }
                )
            }
            IconButton(onClick = onStop) {
                Icon(Icons.Default.Stop, stringResource(R.string.omni_stop_session))
            }
            IconButton(onClick = onReset) {
                Icon(Icons.Default.Refresh, stringResource(R.string.omni_reset_session), tint = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = if (state.isRunning) stringResource(R.string.omni_running) else stringResource(R.string.omni_paused),
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
