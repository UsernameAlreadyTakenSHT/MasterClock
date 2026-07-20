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
fun DisplaySettingsPage(currentSettings: ChessClockSettings, onSettingsChanged: (ChessClockSettings) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(8.dp))

        SettingsSection("Time Format") {
            val showTenths = currentSettings.showTenthsThresholdMs != 0L
            Column {
                BehaviorSwitch("Always show hours", currentSettings.alwaysShowHours, topRounded = true) { onSettingsChanged(currentSettings.copy(alwaysShowHours = it)) }
                BehaviorSwitch("Always show minutes", currentSettings.alwaysShowMinutes) { onSettingsChanged(currentSettings.copy(alwaysShowMinutes = it)) }

                BehaviorSwitch(
                    label = "Show tenths of seconds",
                    checked = showTenths
                ) { enabled ->
                    onSettingsChanged(currentSettings.copy(
                        showTenthsThresholdMs = if (enabled) 10000L else 0L,
                        showHundredths = if (!enabled) false else currentSettings.showHundredths
                    ))
                }

                if (showTenths) {
                    Surface(
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        val options = listOf(10, 20, 30, 60, -1)
                        val currentVal = if (currentSettings.showTenthsThresholdMs == Long.MAX_VALUE) -1 else (currentSettings.showTenthsThresholdMs / 1000).toInt()

                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            options.forEachIndexed { i, s ->
                                val label = if (s == -1) "All" else "${s}s"
                                SegmentedButton(
                                    selected = currentVal == s,
                                    onClick = {
                                        val threshold = if (s == -1) Long.MAX_VALUE else s * 1000L
                                        onSettingsChanged(currentSettings.copy(showTenthsThresholdMs = threshold))
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }

                BehaviorSwitch(
                    label = "Show hundredths",
                    checked = currentSettings.showHundredths,
                    enabled = showTenths,
                    bottomRounded = !currentSettings.showHundredths || !showTenths
                ) { onSettingsChanged(currentSettings.copy(showHundredths = it)) }

                if (showTenths && currentSettings.showHundredths) {
                    Surface(
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Threshold", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(2f)) {
                                SegmentedButton(
                                    selected = !currentSettings.showHundredthsOnlyUnder10s,
                                    onClick = { onSettingsChanged(currentSettings.copy(showHundredthsOnlyUnder10s = false)) },
                                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                                    label = { Text("Like tenths", style = MaterialTheme.typography.labelSmall) }
                                )
                                SegmentedButton(
                                    selected = currentSettings.showHundredthsOnlyUnder10s,
                                    onClick = { onSettingsChanged(currentSettings.copy(showHundredthsOnlyUnder10s = true)) },
                                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                                    label = { Text("Under 10s", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            }
        }

        SettingsSection("Information") {
            Column {
                BehaviorSwitch("Show period (1/3)", currentSettings.showCurrentPeriod, topRounded = true) { onSettingsChanged(currentSettings.copy(showCurrentPeriod = it)) }
                BehaviorSwitch("Always show moves", currentSettings.alwaysShowMoveCount, bottomRounded = true) { onSettingsChanged(currentSettings.copy(alwaysShowMoveCount = it)) }
            }
        }

        SettingsSection("Layout & Scale") {
            Column {
                BehaviorSwitch("Active side bigger", currentSettings.activePlayerSideBigger, topRounded = true) { onSettingsChanged(currentSettings.copy(activePlayerSideBigger = it)) }
                Row(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Orientation", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    SingleChoiceSegmentedButtonRow {
                        ClockOrientation.entries.forEachIndexed { i, o ->
                            val label = when(o) {
                                ClockOrientation.VERTICAL -> "Vert"
                                ClockOrientation.HORIZONTAL_LEFT -> "L"
                                ClockOrientation.HORIZONTAL_RIGHT -> "R"
                            }
                            SegmentedButton(
                                selected = currentSettings.clockOrientation == o,
                                onClick = { onSettingsChanged(currentSettings.copy(clockOrientation = o)) },
                                shape = SegmentedButtonDefaults.itemShape(i, ClockOrientation.entries.size),
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
                BehaviorSwitch("Flash on low time (<10s)", currentSettings.flashOnLowTime) { onSettingsChanged(currentSettings.copy(flashOnLowTime = it)) }
                BehaviorSwitch("Keep screen awake", currentSettings.forceScreenOn) { onSettingsChanged(currentSettings.copy(forceScreenOn = it)) }
                BehaviorSwitch("Force full brightness", currentSettings.forceFullBrightness) { onSettingsChanged(currentSettings.copy(forceFullBrightness = it)) }
                BehaviorSwitch("Fullscreen", currentSettings.fullscreenMode, bottomRounded = true) { onSettingsChanged(currentSettings.copy(fullscreenMode = it)) }
            }
        }

        SettingsSection("Theme") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                AppThemeMode.entries.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = currentSettings.themeMode == t,
                        onClick = { onSettingsChanged(currentSettings.copy(themeMode = t)) },
                        shape = SegmentedButtonDefaults.itemShape(i, AppThemeMode.entries.size),
                        label = { Text(t.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }

        SettingsSection("Primary Colors") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Active side background", style = MaterialTheme.typography.labelMedium)
                ColorRow(currentSettings.activeColor) { onSettingsChanged(currentSettings.copy(activeColor = it)) }

                Text("Inactive side background", style = MaterialTheme.typography.labelMedium)
                ColorRow(currentSettings.inactiveColor) { onSettingsChanged(currentSettings.copy(inactiveColor = it)) }

                Text("Active clock text", style = MaterialTheme.typography.labelMedium)
                ColorRow(currentSettings.activeTextColor) { onSettingsChanged(currentSettings.copy(activeTextColor = it)) }

                Text("Inactive clock text", style = MaterialTheme.typography.labelMedium)
                ColorRow(currentSettings.inactiveTextColor) { onSettingsChanged(currentSettings.copy(inactiveTextColor = it)) }
            }
        }

        SettingsSection("Secondary Information") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Label text color (Moves, Periods, Delay)", style = MaterialTheme.typography.labelMedium)
                ColorRow(currentSettings.secondaryTextColor) { onSettingsChanged(currentSettings.copy(secondaryTextColor = it)) }

                Text("Alert text color (FLAGGED, MOVE!)", style = MaterialTheme.typography.labelMedium)
                ColorRow(currentSettings.alertTextColor) { onSettingsChanged(currentSettings.copy(alertTextColor = it)) }
            }
        }

        SettingsSection("Special States") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Loss state background", style = MaterialTheme.typography.labelMedium)
                ColorRow(currentSettings.lossColor) { onSettingsChanged(currentSettings.copy(lossColor = it)) }

                Text("Gong: Reflection background", style = MaterialTheme.typography.labelMedium)
                ColorRow(currentSettings.reflectionColor) { onSettingsChanged(currentSettings.copy(reflectionColor = it)) }

                Text("Gong: Action background", style = MaterialTheme.typography.labelMedium)
                ColorRow(currentSettings.actionColor) { onSettingsChanged(currentSettings.copy(actionColor = it)) }
            }
        }
        Spacer(Modifier.height(64.dp))
    }
}
