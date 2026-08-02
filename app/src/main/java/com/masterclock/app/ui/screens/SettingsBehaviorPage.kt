package com.masterclock.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.masterclock.app.R
import com.masterclock.app.logic.*

@Composable
fun BehaviorSettingsPage(currentSettings: ChessClockSettings, onSettingsChanged: (ChessClockSettings) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(8.dp))

        SettingsSection(stringResource(R.string.settings_flag_behavior)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                FlagBehavior.entries.forEachIndexed { i, b ->
                    SegmentedButton(
                        selected = currentSettings.flagBehavior == b,
                        onClick = { onSettingsChanged(currentSettings.copy(flagBehavior = b)) },
                        shape = SegmentedButtonDefaults.itemShape(i, FlagBehavior.entries.size),
                        label = { Text(b.label(), style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }

        SettingsSection(stringResource(R.string.settings_tournament_rules)) {
            Column {
                BehaviorSwitch(
                    label = stringResource(R.string.settings_fischer_fide),
                    checked = currentSettings.fischerFideFirstMove,
                    topRounded = true
                ) { onSettingsChanged(currentSettings.copy(fischerFideFirstMove = it)) }
                BehaviorSwitch(
                    label = stringResource(R.string.settings_forced_move_counter),
                    checked = currentSettings.forcedMoveCounter,
                    bottomRounded = true
                ) { onSettingsChanged(currentSettings.copy(forcedMoveCounter = it)) }
            }
        }

        SettingsSection(stringResource(R.string.settings_interaction)) {
            Column {
                BehaviorSwitch(
                    label = stringResource(R.string.settings_confirm_reset),
                    checked = currentSettings.confirmReset,
                    topRounded = true
                ) { onSettingsChanged(currentSettings.copy(confirmReset = it)) }
                BehaviorSwitch(
                    label = stringResource(R.string.settings_trigger_on_press),
                    checked = currentSettings.triggerOnPress
                ) { onSettingsChanged(currentSettings.copy(triggerOnPress = it)) }
                BehaviorSwitch(
                    label = stringResource(R.string.settings_pause_background),
                    checked = currentSettings.pauseOnBackground,
                    bottomRounded = true
                ) { onSettingsChanged(currentSettings.copy(pauseOnBackground = it)) }
            }
        }

        SettingsSection(stringResource(R.string.settings_advanced)) {
            val morePlayersEnabled = currentSettings.numberOfPlayers > 2
            Column {
                BehaviorSwitch(
                    label = stringResource(R.string.settings_different_per_player),
                    checked = currentSettings.differentSettingsPerPlayer && !morePlayersEnabled,
                    enabled = !morePlayersEnabled,
                    topRounded = true
                ) { onSettingsChanged(currentSettings.copy(differentSettingsPerPlayer = it)) }

                BehaviorSwitch(
                    label = stringResource(R.string.settings_more_players),
                    checked = morePlayersEnabled,
                    bottomRounded = !morePlayersEnabled
                ) { enabled ->
                    onSettingsChanged(currentSettings.copy(
                        numberOfPlayers = if (enabled) 3 else 2,
                        differentSettingsPerPlayer = if (enabled) false else currentSettings.differentSettingsPerPlayer
                    ))
                }

                if (morePlayersEnabled) {
                    Surface(
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.settings_players), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            SingleChoiceSegmentedButtonRow {
                                listOf(3, 4).forEachIndexed { i, n ->
                                    SegmentedButton(
                                        selected = currentSettings.numberOfPlayers == n,
                                        onClick = { onSettingsChanged(currentSettings.copy(numberOfPlayers = n)) },
                                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                                        label = { Text(n.toString(), style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }
                    }

                    if (currentSettings.numberOfPlayers == 3) {
                        Spacer(Modifier.height(8.dp))
                        BehaviorSwitch(
                            label = stringResource(R.string.settings_single_player_top),
                            checked = currentSettings.multiPlayerLayout == MultiPlayerLayout.BALANCED,
                            topRounded = true,
                            bottomRounded = true
                        ) {
                            onSettingsChanged(currentSettings.copy(multiPlayerLayout = if (it) MultiPlayerLayout.BALANCED else MultiPlayerLayout.INVERTED))
                        }
                    }
                }
            }
        }

        if (currentSettings.numberOfPlayers > 2) {
            SettingsSection(stringResource(R.string.settings_visuals)) {
                SimulatedScreen(currentSettings, onSettingsChanged)
            }
        }
        Spacer(Modifier.height(64.dp))
    }
}
