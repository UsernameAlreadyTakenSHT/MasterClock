package com.masterclock.paper.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.masterclock.app.logic.*
import com.masterclock.paper.ui.components.ScrollbarMMD
import com.masterclock.paper.ui.components.outlinedButtonBorderMMD
import com.masterclock.paper.ui.components.verticalScrollMMD
import com.masterclock.paper.ui.theme.clockFamily
import com.masterclock.paper.BuildConfig
import com.masterclock.paper.R
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD

@Composable
fun ModesSettingsPage(
    currentSettings: ChessClockSettings,
    onSettingsChanged: (ChessClockSettings) -> Unit,
    onAboutClick: () -> Unit,
) {
    // One scrolling column for the whole page, deliberately.
    //
    // The system-behaviour block and the version footer used to sit outside the scroll area, so
    // they stayed pinned to the bottom while the modes and the time fields scrolled above them. In
    // a Column the children without a weight are served first: those two take the roughly 550dp
    // they ask for, and weight(1f) gets whatever is left. On a 4.3" Mudita screen that leaves about
    // 95dp for everything else, which is not enough to show the minute and second boxes at all --
    // the setting became unreachable on the very device this module exists for.
    //
    // Pinning traded away the primary content to keep the switches always visible. Scrolling the
    // lot costs a swipe on a short screen and nothing on a tall one.
    //
    // The scrollbar is the other half of that trade: E Ink offers no fling, no overscroll stretch
    // and no scrollbar that fades in under a finger, so without one there is nothing on screen to
    // say the page continues. MMD ships one with every scrollable list for exactly this reason.
    val scrollState = rememberScrollState()

    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                // Above the keyboard: the window does not resize edge-to-edge, and the time fields
                // sit where the keyboard rises on a 533dp screen.
                .imePadding()
                .verticalScrollMMD(scrollState)
        ) {
            // One set of times for both players: this app has no switch for a set each, and no
            // import that could bring one in.
            Spacer(Modifier.height(8.dp))
            ModeSelectionPanel(
                p = currentSettings.main,
                onUpdateP = { p: PlayerSettings -> onSettingsChanged(currentSettings.copy(main = p, p1Custom = p, p2Custom = p)) }
            )

            Spacer(Modifier.height(32.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                // Solid and thin rather than MMD's 3dp default: this only separates two groups
                // on one page. The 20% grey it replaced was a dither pattern on E Ink.
                HorizontalDividerMMD(thickness = 1.dp)

                SettingsSection(stringResource(R.string.settings_system_behavior)) {
                    BehaviorSwitch(
                        label = stringResource(R.string.settings_reverse_colors),
                        checked = currentSettings.eInkDarkMode
                    ) {
                        onSettingsChanged(currentSettings.copy(eInkDarkMode = it))
                    }
                    BehaviorSwitch(
                        label = stringResource(R.string.settings_keep_awake),
                        checked = currentSettings.forceScreenOn
                    ) {
                        onSettingsChanged(currentSettings.copy(forceScreenOn = it))
                    }
                    BehaviorSwitch(
                        label = stringResource(R.string.settings_fullscreen),
                        checked = currentSettings.fullscreenMode
                    ) {
                        onSettingsChanged(currentSettings.copy(fullscreenMode = it))
                    }
                    BehaviorSwitch(
                        label = stringResource(R.string.settings_sound),
                        checked = currentSettings.playSwitchSound
                    ) {
                        onSettingsChanged(currentSettings.copy(playSwitchSound = it, tripleBeepTimeUp = it))
                    }
                    BehaviorSwitch(
                        label = stringResource(R.string.settings_haptic_feedback),
                        checked = currentSettings.hapticFeedback
                    ) {
                        onSettingsChanged(currentSettings.copy(hapticFeedback = it))
                    }
                }
            }

            // Last of the settings, before the way to the version: one card a face, each name drawn
            // in its own so that the choice shows what it gives. Full width, one under the other:
            // "Noto Sans Mono" in a third of 288dp would have taken two lines and a smaller size.
            SettingsSection(stringResource(R.string.settings_font)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppFont.entries.forEach { f ->
                        val name = when (f) {
                            AppFont.SYSTEM -> stringResource(R.string.font_system)
                            AppFont.NOTO_SANS -> "Noto Sans"
                            AppFont.NOTO_SANS_MONO -> "Noto Sans Mono"
                        }
                        val face = when (f) {
                            AppFont.SYSTEM -> com.mudita.mmd.eInkTypography.labelLarge
                            else -> MaterialTheme.typography.labelLarge.copy(fontFamily = f.clockFamily())
                        }
                        ModeCard(name, currentSettings.appFont == f, Modifier.fillMaxWidth(), face) {
                            onSettingsChanged(currentSettings.copy(appFont = f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // A button, drawn as one. It was two lines of small print that took a tap with
            // nothing to say so, and the only way to the changelog, the credits and the licences:
            // on a screen with no ripple and no pressed state, what can be pressed has to show it.
            OutlinedButtonMMD(
                onClick = onAboutClick,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                border = outlinedButtonBorderMMD(),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelLarge)
                    Text(AppInfo.BUILD_DATE, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        ScrollbarMMD(scrollState)
    }
}
