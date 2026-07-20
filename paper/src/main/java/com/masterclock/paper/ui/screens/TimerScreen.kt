package com.masterclock.paper.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masterclock.paper.R
import com.masterclock.app.logic.*
import com.masterclock.paper.ui.components.*
import java.util.Locale

@Composable
fun TimerScreen(
    viewModel: ChessTimerViewModel,
    onSettingsClick: () -> Unit,
    onPresetsClick: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var showResetDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Player 1 Block (Upside down for opponent, floating)
        Box(modifier = Modifier.weight(1f).graphicsLayer { rotationZ = 180f }) {
            EInkPlayerArea(
                playerIndex = 1,
                state = state,
                onClick = { viewModel.startOrSwitch(1) }
            )
        }

        // Control Row (Official MMD Outlined Buttons - Sentence Case)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButtonMMD(
                onClick = { if (settings.confirmReset && state.activePlayer != null) showResetDialog = true else viewModel.reset() },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.common_reset),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            OutlinedButtonMMD(
                onClick = { if (state.isPaused) viewModel.resume() else viewModel.pause() },
                modifier = Modifier.weight(1f)
            ) {
                val label = if (state.isPaused || state.activePlayer == null) "Resume" else "Pause"
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            OutlinedButtonMMD(
                onClick = onSettingsClick,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        // Player 2 Block (Floating)
        Box(modifier = Modifier.weight(1f)) {
            EInkPlayerArea(
                playerIndex = 2,
                state = state,
                onClick = { viewModel.startOrSwitch(2) }
            )
        }
    }

    if (showResetDialog) {
        MMDAlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = stringResource(R.string.timer_reset_title),
            text = stringResource(R.string.timer_reset_message),
            confirmButtonText = stringResource(R.string.common_reset),
            onConfirm = { 
                viewModel.reset()
                showResetDialog = false 
            },
            dismissButtonText = stringResource(R.string.common_cancel),
            onDismiss = { showResetDialog = false }
        )
    }
}

@Composable
fun EInkPlayerArea(
    playerIndex: Int,
    state: ChessClockState,
    onClick: () -> Unit
) {
    val playerState = state.players.getOrNull(playerIndex - 1) ?: PlayerState(0)
    val isActive = state.activePlayer == playerIndex && !state.isPaused
    val isOutOfTime = playerState.isOutOfTime
    
    // Arrangement.SpaceEvenly pushes elements apart and centers them relative to empty space.
    // Placing Indicator FIRST in both areas:
    // P1 (rotated): Indicator ends up at screen bottom of area (near center).
    // P2 (unrotated): Indicator stays at screen top of area (near center).
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        // Minimal Active Indicator (Dot or Slash)
        Box(
            modifier = Modifier.size(32.dp), 
            contentAlignment = Alignment.Center
        ) {
            if (isOutOfTime) {
                Text(
                    text = "/",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            } else if (isActive) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onBackground)
                )
            }
        }

        TimerDisplay(
            timeMs = playerState.timeRemainingMs,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 90.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onBackground
            ),
            isNegative = playerState.isNegative
        )
        
        // Final empty spacer/box to ensure the timer is truly centered in its zone
        // when considering SpaceEvenly logic with the indicator.
        Box(modifier = Modifier.size(32.dp))
    }
}

@Composable
fun TimerDisplay(timeMs: Long, style: TextStyle, isNegative: Boolean = false) {
    val absTimeMs = kotlin.math.abs(timeMs)
    val totalSeconds = absTimeMs / 1000
    val minutes = (totalSeconds / 60).toInt()
    val seconds = (totalSeconds % 60).toInt()
    
    // Always show minutes with 2 digits for perfect alignment (e.g., 09:59)
    val mText = String.format(Locale.US, "%02d", minutes)
    val sText = String.format(Locale.US, "%02d", seconds)
    
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
        if (isNegative) Text(text = "-", style = style)
        Text(text = mText, style = style)
        Text(text = ":", style = style)
        Text(text = sText, style = style)
    }
}
