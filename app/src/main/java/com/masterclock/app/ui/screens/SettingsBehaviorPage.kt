package com.masterclock.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.masterclock.app.logic.*

@Composable
fun BehaviorSettingsPage(currentSettings: ChessClockSettings, onSettingsChanged: (ChessClockSettings) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(8.dp))

        SettingsSection("Flag Behavior") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                FlagBehavior.entries.forEachIndexed { i, b ->
                    SegmentedButton(
                        selected = currentSettings.flagBehavior == b,
                        onClick = { onSettingsChanged(currentSettings.copy(flagBehavior = b)) },
                        shape = SegmentedButtonDefaults.itemShape(i, FlagBehavior.entries.size),
                        label = { Text(b.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }

        SettingsSection("Tournament Rules") {
            Column {
                BehaviorSwitch(
                    label = "Fischer FIDE (Bonus on move 0)",
                    checked = currentSettings.fischerFideFirstMove,
                    topRounded = true
                ) { onSettingsChanged(currentSettings.copy(fischerFideFirstMove = it)) }
                BehaviorSwitch(
                    label = "Forced move counter",
                    checked = currentSettings.forcedMoveCounter,
                    bottomRounded = true
                ) { onSettingsChanged(currentSettings.copy(forcedMoveCounter = it)) }
            }
        }

        SettingsSection("Interaction") {
            Column {
                BehaviorSwitch(
                    label = "Confirm reset",
                    checked = currentSettings.confirmReset,
                    topRounded = true
                ) { onSettingsChanged(currentSettings.copy(confirmReset = it)) }
                BehaviorSwitch(
                    label = "Trigger on Press (else on Release)",
                    checked = currentSettings.triggerOnPress
                ) { onSettingsChanged(currentSettings.copy(triggerOnPress = it)) }
                BehaviorSwitch(
                    label = "Pause when in background",
                    checked = currentSettings.pauseOnBackground,
                    bottomRounded = true
                ) { onSettingsChanged(currentSettings.copy(pauseOnBackground = it)) }
            }
        }

        SettingsSection("Advanced") {
            val morePlayersEnabled = currentSettings.numberOfPlayers > 2
            Column {
                BehaviorSwitch(
                    label = "Different settings per player",
                    checked = currentSettings.differentSettingsPerPlayer && !morePlayersEnabled,
                    enabled = !morePlayersEnabled,
                    topRounded = true
                ) { onSettingsChanged(currentSettings.copy(differentSettingsPerPlayer = it)) }

                BehaviorSwitch(
                    label = "Enable more players (experimental)",
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
                            Text("Players", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
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
                            label = "Single player at top",
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
            SettingsSection("Visuals") {
                SimulatedScreen(currentSettings, onSettingsChanged)
            }
        }
        Spacer(Modifier.height(64.dp))
    }
}
