package com.masterclock.app.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masterclock.app.R
import com.masterclock.app.logic.*
import com.masterclock.app.ui.theme.MasterClockTheme
import java.util.Locale

@Composable
fun TimerScreen(
    viewModel: ChessTimerViewModel,
    onSettingsClick: () -> Unit,
    onPresetsClick: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var showResetDialog by remember { mutableStateOf(value = false) }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val showPresets = remember { FlavorConfig.hasPresets() }
    val showArbitre = remember { FlavorConfig.hasArbitre() }

    val iconRotation by animateFloatAsState(
        targetValue = when (settings.clockOrientation) {
            ClockOrientation.HORIZONTAL_LEFT -> 90f
            ClockOrientation.HORIZONTAL_RIGHT -> -90f
            else -> 0f
        },
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "iconRotation"
    )

    val activity = context as? ComponentActivity
    
    val activePlayerState = remember(state.activePlayer, state.players) {
        state.activePlayer?.let { state.players.getOrNull(it - 1) }
    }
    
    val secondsLeft = remember(activePlayerState?.timeRemainingMs) {
        activePlayerState?.let { it.timeRemainingMs / 1000 } ?: -1L
    }
    
    val isGlobalMode = remember(settings.main.mode) {
        settings.main.mode == TimerMode.PHASES
    }
    
    val p1Weight by animateFloatAsState(
        targetValue = if (settings.activePlayerSideBigger && (state.activePlayer == 1) && !isGlobalMode) 1.5f else 1f, 
        label = "p1Weight",
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )
    val p2Weight by animateFloatAsState(
        targetValue = if (settings.activePlayerSideBigger && (state.activePlayer == 2) && !isGlobalMode) 1.5f else 1f, 
        label = "p2Weight",
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )

    LaunchedEffect(secondsLeft, state.activePlayer, state.isPaused) {
        if (!state.isPaused && secondsLeft in 1L..10L) {
            val hapticThreshold = when(settings.hapticCountdownThreshold) {
                BeepCountdownThreshold.THREE_SEC -> 3L
                BeepCountdownThreshold.TEN_SEC -> 10L
                else -> 0L
            }
            if (secondsLeft <= hapticThreshold) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    LaunchedEffect(settings.forceScreenOn) {
        if (settings.forceScreenOn) activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isGlobalMode) {
            Box(Modifier.fillMaxSize()) {
                PlayerButton(Modifier.fillMaxSize(), state, 1, settings, contentRotation = iconRotation) { 
                    if (settings.hapticFeedback) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.startOrSwitch(1) 
                }
            }
        } else {
            val playersCount = settings.numberOfPlayers.coerceIn(2, 4)
            val mapping = if (settings.playerMapping.size < 4) listOf(1, 2, 3, 4) else settings.playerMapping
            
            when {
                playersCount == 2 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        val p1Rotation = when (settings.clockOrientation) {
                            ClockOrientation.HORIZONTAL_LEFT -> 90f
                            ClockOrientation.HORIZONTAL_RIGHT -> -90f
                            else -> 180f
                        }
                        PlayerButton(Modifier.weight(p1Weight), state, 1, settings, contentRotation = p1Rotation) { 
                            if (settings.hapticFeedback) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.startOrSwitch(1) 
                        }

                        val p2Rotation = when (settings.clockOrientation) {
                            ClockOrientation.HORIZONTAL_LEFT -> 90f
                            ClockOrientation.HORIZONTAL_RIGHT -> -90f
                            else -> 0f
                        }
                        PlayerButton(Modifier.weight(p2Weight), state, 2, settings, contentRotation = p2Rotation) { 
                            if (settings.hapticFeedback) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.startOrSwitch(2) 
                        }
                    }
                }
                playersCount == 3 -> {
                    val p1 = mapping.getOrElse(0) { 1 }
                    val p2 = mapping.getOrElse(1) { 2 }
                    val p3 = mapping.getOrElse(2) { 3 }
                    val isBalanced = settings.multiPlayerLayout == MultiPlayerLayout.BALANCED
                    Column(Modifier.fillMaxSize()) {
                        if (isBalanced) {
                            val p1Rot = when (settings.clockOrientation) {
                                ClockOrientation.HORIZONTAL_LEFT -> 90f
                                ClockOrientation.HORIZONTAL_RIGHT -> -90f
                                else -> 180f
                            }
                            PlayerButton(Modifier.weight(1f), state, p1, settings, contentRotation = p1Rot) { viewModel.startOrSwitch(p1) }
                            Row(Modifier.weight(1f)) {
                                PlayerButton(Modifier.weight(1f), state, p2, settings, contentRotation = iconRotation) { viewModel.startOrSwitch(p2) }
                                PlayerButton(Modifier.weight(1f), state, p3, settings, contentRotation = iconRotation) { viewModel.startOrSwitch(p3) }
                            }
                        } else {
                            Row(Modifier.weight(1f)) {
                                val topRot = when (settings.clockOrientation) {
                                    ClockOrientation.HORIZONTAL_LEFT -> 90f
                                    ClockOrientation.HORIZONTAL_RIGHT -> -90f
                                    else -> 180f
                                }
                                PlayerButton(Modifier.weight(1f), state, p1, settings, contentRotation = topRot) { viewModel.startOrSwitch(p1) }
                                PlayerButton(Modifier.weight(1f), state, p2, settings, contentRotation = topRot) { viewModel.startOrSwitch(p2) }
                            }
                            PlayerButton(Modifier.weight(1f), state, p3, settings, contentRotation = iconRotation) { viewModel.startOrSwitch(p3) }
                        }
                    }
                }
                playersCount == 4 -> {
                    val p1 = mapping.getOrElse(0) { 1 }
                    val p2 = mapping.getOrElse(1) { 2 }
                    val p3 = mapping.getOrElse(2) { 3 }
                    val p4 = mapping.getOrElse(3) { 4 }
                    val topRot = when (settings.clockOrientation) {
                        ClockOrientation.HORIZONTAL_LEFT -> 90f
                        ClockOrientation.HORIZONTAL_RIGHT -> -90f
                        else -> 180f
                    }
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.weight(1f)) {
                            PlayerButton(Modifier.weight(1f), state, p1, settings, contentRotation = topRot) { viewModel.startOrSwitch(p1) }
                            PlayerButton(Modifier.weight(1f), state, p2, settings, contentRotation = topRot) { viewModel.startOrSwitch(p2) }
                        }
                        Row(Modifier.weight(1f)) {
                            PlayerButton(Modifier.weight(1f), state, p3, settings, contentRotation = iconRotation) { viewModel.startOrSwitch(p3) }
                            PlayerButton(Modifier.weight(1f), state, p4, settings, contentRotation = iconRotation) { viewModel.startOrSwitch(p4) }
                        }
                    }
                }
            }
        }

        val bias = if (settings.numberOfPlayers == 2 && settings.activePlayerSideBigger && !isGlobalMode) {
            val totalWeight = p1Weight + p2Weight
            (p1Weight / totalWeight - 0.5f) * 2f
        } else if (isGlobalMode) 0.85f else 0f

        Box(Modifier.fillMaxSize(), contentAlignment = BiasAlignment(0f, bias)) {
            ControlBar(
                isPaused = state.isPaused,
                isStarted = state.activePlayer != null || (isGlobalMode && !state.isPaused) || (settings.main.mode.name.startsWith("CHRONO") && settings.isOneForAll && !state.isPaused),
                isArbitreMode = state.isArbitreMode,
                iconRotation = iconRotation,
                showPresets = showPresets,
                showArbitre = showArbitre,
                onPauseResume = { if (state.isPaused) viewModel.resume() else viewModel.pause() },
                onReset = { if (settings.confirmReset && state.activePlayer != null) showResetDialog = true else viewModel.reset() },
                onPresets = onPresetsClick,
                onSettings = onSettingsClick,
                onToggleArbitre = { viewModel.toggleArbitreMode() }
            )
        }

        AnimatedVisibility(visible = state.isArbitreMode, enter = fadeIn(tween()), exit = fadeOut(tween())) { 
            ArbitreOverlay(
                numberOfPlayers = settings.numberOfPlayers,
                hasSavedClock = viewModel.hasSavedClock.collectAsState().value,
                contentRotation = iconRotation,
                onAdjust = { idx, amt -> viewModel.adjustTime(idx, amt) }, 
                onSave = { viewModel.saveClockForLater() },
                onResume = { viewModel.resumeSavedClock() },
                onClose = { viewModel.toggleArbitreMode() }
            ) 
        }
    }
    
    if (showResetDialog) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.padding(32.dp).graphicsLayer { rotationZ = iconRotation },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.timer_reset_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.timer_reset_message), style = MaterialTheme.typography.bodyMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { showResetDialog = false }) { Text(stringResource(R.string.common_cancel)) }
                        TextButton(onClick = { viewModel.reset(); showResetDialog = false }) { 
                            Text(stringResource(R.string.common_reset), color = MaterialTheme.colorScheme.error) 
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerButton(modifier: Modifier = Modifier, state: ChessClockState, playerIndex: Int, settings: ChessClockSettings, contentRotation: Float = 0f, onClick: () -> Unit) {
    val playerState = state.players.getOrNull(playerIndex - 1) ?: PlayerState(0)
    val isActive = state.activePlayer == playerIndex && !state.isPaused
    val pSettings = if (settings.differentSettingsPerPlayer) {
        when(playerIndex) {
            1 -> settings.p1Custom; 2 -> settings.p2Custom; 3 -> settings.p3Custom; 4 -> settings.p4Custom; else -> settings.main
        }
    } else settings.main
    
    val haptic = LocalHapticFeedback.current
    val isChrono = pSettings.mode.name.startsWith("CHRONO")
    val isMoveCounts = pSettings.mode.name.startsWith("MOVE_COUNTS")
    val isGong = pSettings.mode == TimerMode.GONG
    
    val isLowTime = isActive && playerState.timeRemainingMs in 1..10000 && settings.flashOnLowTime
    val infiniteTransition = rememberInfiniteTransition(label = "lowTimeFlash")
    val flashAlpha by infiniteTransition.animateFloat(initialValue = 1f, targetValue = 0.5f, animationSpec = infiniteRepeatable(animation = tween(500, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "flashAlpha")

    val activeColor = Color(settings.activeColor)
    val inactiveColor = Color(settings.inactiveColor)
    val lossColor = Color(settings.lossColor) 
    val reflectionColor = Color(settings.reflectionColor)
    val actionColor = Color(settings.actionColor)

    val backgroundColor by animateColorAsState(
        targetValue = when { 
            playerState.isOutOfTime -> if (settings.flagBehavior == FlagBehavior.FLAG) (if (state.firstToFlag == playerIndex) lossColor else if (isActive) activeColor else inactiveColor) else lossColor
            playerState.hasFlagged -> if (isActive) activeColor else inactiveColor
            // Turn-based Gong: only the player whose turn it is shows reflect/move color, everyone
            // else is grey (inactive), same convention as every other mode. Simultaneous Gong has no
            // "inactive" player -- everyone ticks together -- so all boxes reflect their own phase.
            // See AUDIT.md §7.2.
            isGong -> if (state.isPaused || !(pSettings.gongSimultaneous || isActive)) {
                inactiveColor
            } else {
                if (playerState.isGongReflectionPhase) reflectionColor else actionColor
            }
            isActive || (isChrono && settings.isOneForAll && !state.isPaused) -> activeColor 
            else -> inactiveColor 
        }, animationSpec = tween(300), label = "backgroundColor"
    )
    
    val contentColor by animateColorAsState(
        targetValue = if (isActive || (isChrono && settings.isOneForAll && !state.isPaused)) {
            Color(settings.activeTextColor)
        } else {
            Color(settings.inactiveTextColor)
        },
        animationSpec = tween(300),
        label = "contentColor"
    )

    val labelColor = Color(settings.secondaryTextColor)
    val alertTextColor = Color(settings.alertTextColor)

    val enabled = !state.isArbitreMode && (settings.flagBehavior != FlagBehavior.FREEZE || !playerState.isOutOfTime) && pSettings.mode != TimerMode.MOVE_TIMER_SHARED && !(isChrono && settings.isOneForAll)
    
    Surface(
        modifier = modifier.fillMaxSize().pointerInput(settings.triggerOnPress, enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onPress = { if (settings.triggerOnPress) { if (settings.hapticFeedback) haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() } },
                                  onTap = { if (!settings.triggerOnPress) { if (settings.hapticFeedback) haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() } })
            }, 
        color = if (isLowTime) backgroundColor.copy(alpha = flashAlpha) else backgroundColor, contentColor = contentColor
    ) {
        val isGlobal = pSettings.mode == TimerMode.PHASES
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = if (isGlobal) Alignment.TopCenter else Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally, 
                modifier = Modifier.graphicsLayer { rotationZ = contentRotation }.padding(top = if (isGlobal) 100.dp else 0.dp)
            ) {
                Box(modifier = Modifier.height(48.dp), contentAlignment = Alignment.Center) {
                    when {
                        pSettings.mode == TimerMode.MOVE_TIMER_SHARED -> { Text(stringResource(R.string.timer_cycle, state.cycleCount), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = labelColor) }
                        (isChrono && !settings.isOneForAll) || isMoveCounts -> { Text(stringResource(R.string.timer_moves, playerState.moveCount), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = labelColor) }
                        isActive && playerState.delayRemainingMs > 0 -> { Text(stringResource(R.string.timer_delay, String.format(Locale.US, "%.1f", playerState.delayRemainingMs / 1000f)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = labelColor) }
                        pSettings.mode == TimerMode.GONG -> {
                            val label = if (playerState.isGongReflectionPhase) stringResource(R.string.timer_reflect) else stringResource(R.string.timer_move_alert)
                            Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (!playerState.isGongReflectionPhase) alertTextColor else labelColor)
                        }
                        playerState.isInByoyomi -> {
                            val text = if (pSettings.mode == TimerMode.BYOYOMI_JAPANESE) { if (settings.showCurrentPeriod) "PERIOD: ${pSettings.byoyomiPeriods - playerState.byoyomiPeriodsRemaining + 1}/${pSettings.byoyomiPeriods}" else "PERIODS: ${playerState.byoyomiPeriodsRemaining}" } else stringResource(R.string.timer_moves, playerState.movesRemainingInPeriod)
                            Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (isActive) labelColor else labelColor.copy(alpha = 0.7f))
                        }
                        pSettings.mode == TimerMode.FIDE_PERIODS -> {
                            val currentP = pSettings.fidePeriods.getOrNull(playerState.currentPeriodIndex)
                            val info = if (settings.forcedMoveCounter && currentP != null) stringResource(R.string.timer_moves, currentP.movesToNext - playerState.moveCount) else "PERIOD: ${playerState.currentPeriodIndex + 1}/${pSettings.fidePeriods.size}"
                            Text(info, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = labelColor)
                        }
                        pSettings.mode == TimerMode.MOVE_TIMER_OVERTIME || pSettings.mode == TimerMode.MOVE_TIMER_GLOBAL || pSettings.mode == TimerMode.MOVE_TIMER_SAVE_CAP || pSettings.mode == TimerMode.MOVE_TIMER_GLOBAL_SHARED -> {
                            val label = when(pSettings.mode) { TimerMode.MOVE_TIMER_OVERTIME -> "OT"; TimerMode.MOVE_TIMER_GLOBAL -> "TOTAL"; TimerMode.MOVE_TIMER_GLOBAL_SHARED -> "GLOBAL"; else -> "BANK" }
                            Text("$label: ${formatSecondaryTime(playerState.secondaryTimeMs)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = labelColor)
                        }
                        pSettings.mode == TimerMode.PHASES -> {
                            val phase = pSettings.phases.getOrNull(playerState.currentPhaseIndex)
                            val name = phase?.name?.takeIf { it.isNotBlank() } ?: "Phase ${playerState.currentPhaseIndex + 1}"
                            val label = if (playerState.isInInterPhasePause) stringResource(R.string.timer_pause_label) else name
                            Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = labelColor)
                        }
                    }
                }
                if (isMoveCounts) { Text(text = playerState.moveCount.toString(), style = MaterialTheme.typography.displayLarge.copy(fontSize = if (settings.numberOfPlayers > 2) 80.sp else 140.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)) }
                else if (pSettings.mode == TimerMode.HIDDEN && !playerState.isOutOfTime) {
                    val text = if (playerState.revealTimeUntilMs > 0) "${playerState.lastRevealPercentage}%" else "??:??"
                    Text(text = text, style = MaterialTheme.typography.displayLarge.copy(fontSize = if (settings.numberOfPlayers > 2) 50.sp else 84.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace))
                }
                else {
                    val timeToDisplay = if (isChrono || pSettings.mode == TimerMode.PHASES) (if (playerState.isInInterPhasePause) playerState.pauseTimeRemainingMs else state.globalTimeMs) else playerState.timeRemainingMs
                    TimerDisplay(timeMs = timeToDisplay, style = MaterialTheme.typography.displayLarge.copy(fontSize = if (settings.numberOfPlayers > 2) 48.sp else if (pSettings.mode == TimerMode.MOVE_TIMER_SHARED || isChrono || pSettings.mode == TimerMode.PHASES) 100.sp else 64.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace), settings = settings, isNegative = playerState.isNegative)
                }
                if (playerState.isOutOfTime || playerState.hasFlagged) {
                    val flagText = if (pSettings.mode == TimerMode.FIDE_PERIODS && playerState.timeRemainingMs > 0) stringResource(R.string.timer_period_flag) else stringResource(R.string.timer_flagged)
                    Text(flagText, style = if (settings.numberOfPlayers > 2) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineLarge, color = alertTextColor, fontWeight = FontWeight.ExtraBold)
                } else {
                    Box(modifier = Modifier.height(48.dp), contentAlignment = Alignment.Center) {
                        if (playerState.isInByoyomi) Text(stringResource(R.string.timer_byoyomi), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = labelColor.copy(alpha = 0.8f))
                        else if (settings.alwaysShowMoveCount && !isMoveCounts && !(isChrono && settings.isOneForAll)) { Text("P$playerIndex " + stringResource(R.string.timer_moves, playerState.moveCount), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = labelColor.copy(alpha = 0.8f)) }
                        else if (settings.numberOfPlayers > 2) { Text(stringResource(R.string.common_player_n, playerIndex), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = labelColor.copy(alpha = 0.8f)) }
                    }
                }
            }
        }
    }
}

@Composable
fun ControlBar(
    isPaused: Boolean, 
    isStarted: Boolean, 
    isArbitreMode: Boolean, 
    iconRotation: Float, 
    showPresets: Boolean,
    showArbitre: Boolean,
    onPauseResume: () -> Unit, 
    onReset: () -> Unit, 
    onPresets: () -> Unit, 
    onSettings: () -> Unit, 
    onToggleArbitre: () -> Unit
) {
    Surface(
        modifier = Modifier.padding(16.dp).clip(CircleShape), 
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f), 
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 12.dp, 
        shadowElevation = 8.dp
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (showPresets) {
                ControlBarItem(onClick = onPresets, rotation = iconRotation) { Icon(Icons.AutoMirrored.Filled.FormatListBulleted, "Presets") }
            }
            ControlBarItem(onClick = onReset, rotation = iconRotation) { Icon(Icons.Default.Refresh, stringResource(R.string.common_reset)) }
            LargeFloatingActionButton(
                onClick = onPauseResume, 
                shape = CircleShape, 
                containerColor = if (isArbitreMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary, 
                contentColor = if (isArbitreMode) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary, 
                modifier = Modifier.size(64.dp).graphicsLayer { rotationZ = iconRotation }
            ) { 
                Icon(imageVector = if (isPaused || !isStarted) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = if (isPaused) "Resume" else "Pause", modifier = Modifier.size(36.dp)) 
            }
            if (showArbitre) {
                ControlBarItem(onClick = onToggleArbitre, rotation = iconRotation, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = if (isArbitreMode) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer, contentColor = if (isArbitreMode) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer)) { Icon(imageVector = if (isArbitreMode) Icons.Default.Gavel else Icons.Default.Edit, contentDescription = "Arbitre", tint = if (isArbitreMode) MaterialTheme.colorScheme.tertiary else LocalContentColor.current) }
            }
            ControlBarItem(onClick = onSettings, rotation = iconRotation) { Icon(Icons.Default.Settings, "Settings") }
        }
    }
}

@Composable
fun ControlBarItem(onClick: () -> Unit, rotation: Float, colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(), content: @Composable () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(48.dp).graphicsLayer { rotationZ = rotation }, colors = colors, content = content)
}

@Composable
fun ArbitreOverlay(numberOfPlayers: Int, hasSavedClock: Boolean, contentRotation: Float, onAdjust: (Int, Long) -> Unit, onSave: () -> Unit, onResume: () -> Unit, onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(24.dp), 
            color = MaterialTheme.colorScheme.surface, 
            modifier = Modifier.padding(32.dp).graphicsLayer { rotationZ = contentRotation },
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.timer_arbitre_control), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ArbitrePlayerControl(stringResource(R.string.common_player_n, 1), modifier = Modifier.weight(1f)) { onAdjust(1, it) }
                        ArbitrePlayerControl(stringResource(R.string.common_player_n, 2), modifier = Modifier.weight(1f)) { onAdjust(2, it) }
                    }
                    
                    if (numberOfPlayers >= 3) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            ArbitrePlayerControl(stringResource(R.string.common_player_n, 3), modifier = Modifier.weight(1f)) { onAdjust(3, it) }
                            if (numberOfPlayers == 4) {
                                ArbitrePlayerControl(stringResource(R.string.common_player_n, 4), modifier = Modifier.weight(1f)) { onAdjust(4, it) }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onSave, 
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(12.dp)
                    ) { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Save, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.timer_save_clock)) 
                        }
                    }
                    
                    if (hasSavedClock) {
                        Button(
                            onClick = onResume, 
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            shape = RoundedCornerShape(12.dp)
                        ) { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.History, null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.timer_resume)) 
                            }
                        }
                    }
                }

                Button(onClick = onClose, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { 
                    Text(stringResource(R.string.common_back_to_game)) 
                }
            }
        }
    }
}

@Composable
fun ArbitrePlayerControl(label: String, modifier: Modifier = Modifier, onAdjust: (Long) -> Unit) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { 
            FilledTonalIconButton(onClick = { onAdjust(-60_000) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Remove, "-1m") }
            FilledTonalIconButton(onClick = { onAdjust(60_000) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Add, "+1m") }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { 
            FilledTonalIconButton(onClick = { onAdjust(-1_000) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Remove, "-1s") }
            FilledTonalIconButton(onClick = { onAdjust(1_000) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Add, "+1s") }
        }
    }
}

@Composable
fun TimerDisplay(timeMs: Long, style: TextStyle, settings: ChessClockSettings, isNegative: Boolean = false) {
    val absTimeMs = kotlin.math.abs(timeMs)
    val totalSeconds = absTimeMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val tenths = (absTimeMs % 1000) / 100
    val showHours = settings.alwaysShowHours || hours > 0
    val showMinutes = settings.alwaysShowMinutes || minutes > 0 || showHours
    val showTenths = (settings.showTenthsThresholdMs == Long.MAX_VALUE || (absTimeMs < settings.showTenthsThresholdMs && settings.showTenthsThresholdMs > 0))
    val showHundredths = settings.showHundredths && (
        if (settings.showHundredthsOnlyUnder10s) absTimeMs < 10000L 
        else showTenths
    )
    
    val tightStyle = style.copy(letterSpacing = (-2).sp)
    val fractionalStyle = tightStyle.copy(
        fontSize = style.fontSize * 0.5f,
        baselineShift = androidx.compose.ui.text.style.BaselineShift(0.2f),
        letterSpacing = (-1).sp
    )

    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center, modifier = Modifier.wrapContentWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isNegative) Text(text = "-", style = tightStyle)
            if (showHours) { Text(text = hours.toString(), style = tightStyle, textAlign = TextAlign.Center); Text(text = ":", style = tightStyle) }
            if (showMinutes) { val text = if (showHours) String.format(Locale.US, "%02d", minutes) else minutes.toString(); Text(text = text, style = tightStyle, textAlign = TextAlign.Center); Text(text = ":", style = tightStyle) }
            val sText = if (showMinutes) String.format(Locale.US, "%02d", seconds) else seconds.toString(); Text(text = sText, style = tightStyle, textAlign = TextAlign.Center)
        }
        
        if (showHundredths) {
            Text(text = ".", style = fractionalStyle)
            Text(text = String.format(Locale.US, "%02d", (absTimeMs % 1000) / 10), style = fractionalStyle, textAlign = TextAlign.Center)
        } else if (showTenths) {
            Text(text = ".", style = fractionalStyle)
            Text(text = tenths.toString(), style = fractionalStyle, textAlign = TextAlign.Center)
        }
    }
}

fun formatSecondaryTime(timeMs: Long): String { val totalSeconds = timeMs / 1000; return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60) }

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun TimerScreenPreview() { MasterClockTheme { Box(modifier = Modifier.fillMaxSize()) { Text("Preview requires actual state") } } }
