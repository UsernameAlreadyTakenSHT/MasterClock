package com.masterclock.paper.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import com.mudita.mmd.components.buttons.ButtonDefaultsMMD
import androidx.compose.ui.zIndex
import com.masterclock.paper.R
import com.masterclock.app.logic.*
import com.masterclock.paper.ui.components.*
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import java.util.Locale

@Composable
fun TimerScreen(
    viewModel: ChessTimerViewModel,
    onSettingsClick: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    // Saveable: a font-size or language change recreated the screen and closed the confirmation.
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    // Whether the question was asked by Start on a finished game, which goes on to start the next.
    var startAfterReset by rememberSaveable { mutableStateOf(false) }
    // The "Haptic feedback" switch of the settings was read nowhere in this build: it did nothing.
    // A press now vibrates when it is on, as on the phone app.
    val haptic = LocalHapticFeedback.current
    fun press(playerIndex: Int) {
        // The buzz answers a press that did something; see startOrSwitch.
        if (viewModel.startOrSwitch(playerIndex) && settings.hapticFeedback) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    // The announcer overlays the clock rather than sitting in the Column below. It emits nothing
    // until there is something to announce, and the Column spaces its children by 16dp, so as a
    // child it appeared out of nowhere on the first press and pushed 17dp into the layout: both
    // player areas are weight(1f), so they absorbed it and the control row between them jumped.
    Box(modifier = Modifier.fillMaxSize()) {
        ClockAnnouncer(state, settings.flagBehavior)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                // Clear of the system bars, which edge-to-edge draws over the content: Player 2's area
                // ran under the navigation bar, where a hard press mid-game could land on Home.
                // Nothing when the bars are hidden in fullscreen.
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Player 1 Block (Upside down for opponent, floating)
            Box(modifier = Modifier.weight(1f).graphicsLayer { rotationZ = 180f }) {
                EInkPlayerArea(
                    playerIndex = 1,
                    state = state,
                    padding = settings.effectiveTimePadding(),
                    gameOver = isGameOver(state, settings.flagBehavior, settings.numberOfPlayers),
                    flagged = isFlagged(state, 1, settings.flagBehavior),
                    triggerOnPress = settings.triggerOnPress,
                    onClick = { press(1) }
                )
            }
    
            // Control Row (Official MMD Outlined Buttons - Sentence Case)
            // MMD pads a button's label by 16dp a side, which left about 56dp for it in a third of the
            // Kompakt's width: "Hervatten", "Reprendre" broke over two lines and the row grew. The
            // labels get the width, on one line.
            val controlPadding = PaddingValues(horizontal = 4.dp, vertical = ButtonDefaultsMMD.buttonVerticalPadding)
            // 48dp, the smallest target Android recommends, as the mode cards have. They stood at
            // some 36, the height of their label and MMD's padding: above the 32 MMD asks of a
            // button, and small for the three most pressed in the app, on a screen that shows no
            // press.
            val controlSize = Modifier.heightIn(min = 48.dp)
            // One size for the three, the largest at which the longest fits: "Démarrer" filled its
            // button from edge to edge at a large font. Every label the middle button can show is
            // measured, so that the row does not change size when the game starts or pauses.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val controlLabels = listOf(R.string.common_reset, R.string.timer_start, R.string.timer_resume, R.string.timer_pause, R.string.timer_settings).map { stringResource(it) }
                val controlStyle = sharedLabelStyle(controlLabels, MaterialTheme.typography.labelLarge, (maxWidth - 24.dp) / 3 - 22.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButtonMMD(
                        onClick = {
                            startAfterReset = false
                            if (settings.confirmReset && state.activePlayer != null) showResetDialog = true else viewModel.reset()
                        },
                        modifier = Modifier.weight(1f).then(controlSize),
                        border = outlinedButtonBorderMMD(),
                        contentPadding = controlPadding,
                    ) {
                        Text(
                            text = stringResource(R.string.common_reset),
                            style = controlStyle,
                            // One line at the size the row shares; a second where the label is
                            // still too wide at that size's floor. See sharedLabelStyle.
                            maxLines = 2,
                            textAlign = TextAlign.Center,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
    
                    // A finished game has nothing to resume: the button read "Resume", and pressed did
                    // nothing, on a screen that shows no press. It starts the next game, as "Start" does
                    // on a clock that has not run -- Reset, beside it, is what only clears the clock.
                    //
                    // After the question Reset asks. The button turns from Pause to this the moment a
                    // flag falls, in a scramble where both players are still hitting the glass: taken
                    // at once, a press that landed on it wiped who had lost and by how much, the only
                    // record there is, and set a new game running.
                    val over = isGameOver(state, settings.flagBehavior, settings.numberOfPlayers)
                    OutlinedButtonMMD(
                        onClick = {
                            if (over) {
                                if (settings.confirmReset) { startAfterReset = true; showResetDialog = true }
                                else { viewModel.reset(); viewModel.resume() }
                            } else if (state.isPaused) viewModel.resume() else viewModel.pause()
                        },
                        modifier = Modifier.weight(1f).then(controlSize),
                        border = outlinedButtonBorderMMD(),
                        contentPadding = controlPadding,
                    ) {
                        // Before the first press there is nothing to resume: the button starts P1's clock.
                        val label = when {
                            state.activePlayer == null || over -> stringResource(R.string.timer_start)
                            state.isPaused -> stringResource(R.string.timer_resume)
                            else -> stringResource(R.string.timer_pause)
                        }
                        Text(
                            text = label,
                            style = controlStyle,
                            // One line at the size the row shares; a second where the label is
                            // still too wide at that size's floor. See sharedLabelStyle.
                            maxLines = 2,
                            textAlign = TextAlign.Center,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
    
                    OutlinedButtonMMD(
                        // Paused first, as on the phone: a game went on running behind a page with no way
                        // to press it, and a flag could fall there unseen.
                        onClick = { if (!state.isPaused) viewModel.pause(); onSettingsClick() },
                        modifier = Modifier.weight(1f).then(controlSize),
                        border = outlinedButtonBorderMMD(),
                        contentPadding = controlPadding,
                    ) {
                        Text(
                            text = stringResource(R.string.timer_settings),
                            style = controlStyle,
                            // One line at the size the row shares; a second where the label is
                            // still too wide at that size's floor. See sharedLabelStyle.
                            maxLines = 2,
                            textAlign = TextAlign.Center,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
    
            // Player 2 Block (Floating)
            Box(modifier = Modifier.weight(1f)) {
                EInkPlayerArea(
                    playerIndex = 2,
                    state = state,
                    padding = settings.effectiveTimePadding(),
                    gameOver = isGameOver(state, settings.flagBehavior, settings.numberOfPlayers),
                    flagged = isFlagged(state, 2, settings.flagBehavior),
                    triggerOnPress = settings.triggerOnPress,
                    onClick = { press(2) }
                )
            }
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
                if (startAfterReset) viewModel.resume()
                showResetDialog = false
            },
            dismissButtonText = stringResource(R.string.common_cancel),
            onDismiss = { showResetDialog = false }
        )
    }
}

/**
 * A zero-content live region that speaks state changes. It sits outside the player areas because
 * those collapse their subtree with clearAndSetSemantics; see the app module for the full note.
 */
@Composable
private fun ClockAnnouncer(state: ChessClockState, flagBehavior: FlagBehavior) {
    // The node is there from the start, empty, as on the phone. A live region speaks when its text
    // changes, and one that only appeared with its first announcement -- Start, and again after
    // every Reset -- may have said nothing for it.
    val announcement = clockAnnouncement(state, flagBehavior)
    val message = announcement?.let { clockAnnouncementText(it) } ?: ""
    val urgent = isUrgentAnnouncement(state, flagBehavior)

    Box(
        Modifier
            // The clock fills the screen and is drawn after this, which would leave the announcer
            // obscured -- and an obscured node is dropped from the accessibility tree.
            .zIndex(1f)
            .size(1.dp)
            .semantics {
                liveRegion = if (urgent) LiveRegionMode.Assertive else LiveRegionMode.Polite
                contentDescription = message
            }
    )
}

@Composable
fun EInkPlayerArea(
    playerIndex: Int,
    state: ChessClockState,
    padding: TimePadding,
    gameOver: Boolean,
    flagged: Boolean,
    triggerOnPress: Boolean,
    onClick: () -> Unit
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val playerState = state.players.getOrNull(playerIndex - 1) ?: PlayerState(0)
    val isActive = state.activePlayer == playerIndex && !state.isPaused
    // Whose move it is, their clock running or not. A pause took the mark away -- the Pause button,
    // the settings opened, the app left -- and the two sides then looked the same, while only this
    // one's press takes the game up again: the other's does nothing, on a screen that shows no press.
    val onTheMove = state.activePlayer == playerIndex && !gameOver
    val isOutOfTime = playerState.isOutOfTime

    // Hoisted: the semantics lambda is not composable scope.
    val switchTurnLabel = stringResource(R.string.timer_switch_turn)
    val spokenTime = if (isOutOfTime) {
        stringResource(R.string.a11y_out_of_time)
    } else spokenDurationText(if (isActive) steppedForSpeech(playerState.timeRemainingMs) else playerState.timeRemainingMs)
    // Nobody's turn once the game is over, though the last player to move is still the active one.
    val a11yLabel = if (onTheMove) {
        stringResource(R.string.a11y_player_clock_active, playerIndex, spokenTime)
    } else stringResource(R.string.a11y_player_clock, playerIndex, spokenTime)

    // The reading is centred by its weight between two boxes of one height, the indicator's and
    // an empty one. The indicator comes first in both areas:
    // P1 (rotated): Indicator ends up at screen bottom of area (near center).
    // P2 (unrotated): Indicator stays at screen top of area (near center).
    Column(
        modifier = Modifier
            .fillMaxSize()
            // On touch, as the phone app does by default (triggerOnPress), or on release. It was
            // always on release: every switch came as late as the finger left the glass, and a quick
            // press that slid on the panel did not count at all.
            .pointerInput(triggerOnPress) {
                detectTapGestures(
                    onPress = { if (triggerOnPress) currentOnClick() },
                    onTap = { if (!triggerOnPress) currentOnClick() },
                )
            }
            // Unlike the main app this area was already reachable, but it announced as an unlabelled
            // clickable wrapping loose digits; collapsing it gives one target that names the player.
            .clearAndSetSemantics {
                contentDescription = a11yLabel
                role = Role.Button
                onClick(label = switchTurnLabel) { onClick(); true }
            }
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Minimal Active Indicator (Dot or Slash)
        Box(
            // As wide as the word needs: at the 32dp square the dot has, it would be cut.
            modifier = Modifier.heightIn(min = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            // The mark of a flag, so not for a clock that ran out under FLAG after another had: see isFlagged.
            if (flagged) {
                // A word, where a slash stood: the slash said nothing to a player who had not
                // been told what it meant. "Time", and not the phone's "Flagged", for those who
                // have never heard of a flag; here a clock at zero always ends the game.
                Text(
                    text = stringResource(R.string.timer_time_up),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    softWrap = false,
                )
            } else if (isActive) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onBackground)
                )
            } else if (onTheMove) {
                // Paused: the dot's outline, black on white like the rest.
                Box(Modifier.size(12.dp).border(2.dp, MaterialTheme.colorScheme.onBackground, CircleShape))
            }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            TimerDisplay(
                timeMs = playerState.timeRemainingMs,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onBackground
                ),
                width = maxWidth,
                height = maxHeight,
                padding = padding,
                isNegative = playerState.isNegative
            )
        }

        // Final empty spacer/box to ensure the timer is truly centered in its zone.
        Box(modifier = Modifier.size(32.dp))
    }
}

/**
 * The reading, as large as [width] and [height] allow and at most 90dp, on one line.
 *
 * It was 90sp in four separate texts. Sp follows the system font size, and at 90 a five-character
 * reading is about 270dp against the Kompakt's 288 -- so from a font size just above the default
 * the seconds broke onto a line of their own, or were cut, and a reading past 99 minutes did not fit
 * at all. The size is worked out in dp from the room there is, which the font setting does not
 * change; the digits on a clock are not body text.
 */
@Composable
fun TimerDisplay(timeMs: Long, style: TextStyle, width: Dp, height: Dp, padding: TimePadding = TimePadding.FULL, isNegative: Boolean = false) {
    val absTimeMs = kotlin.math.abs(timeMs)
    // Rounded up while there is time left, see wholeSecondsShown; an overrun counts up from zero.
    val totalSeconds = if (isNegative) absTimeMs / 1000 else wholeSecondsShown(timeMs)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    // FULL by default, which is what this clock always did: two digits each, for an alignment that
    // never shifts. E-ink redraws slowly enough that a reading changing width is worth avoiding,
    // so the default stays FULL even though the setting can now widen or narrow it.
    val mText = padTimeUnit(minutes, isLeading = true, padding = padding)
    val sText = padTimeUnit(seconds, isLeading = false, padding = padding)
    
    val text = (if (isNegative) "-" else "") + mText + ":" + sText
    // A monospace digit is about 0.6 of the font size wide, and a line about 1.2 of it tall.
    val size = minOf(MAX_DIGIT_SIZE, width / (text.length * 0.62f), height / 1.2f)
    val fontSize = with(LocalDensity.current) { size.toSp() }
    Text(text = text, style = style.copy(fontSize = fontSize, lineHeight = fontSize * 1.15f), maxLines = 1, softWrap = false)
}

private val MAX_DIGIT_SIZE = 90.dp
