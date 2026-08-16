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
fun DisplaySettingsPage(currentSettings: ChessClockSettings, onSettingsChanged: (ChessClockSettings) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(8.dp))

        SettingsSection(stringResource(R.string.settings_time_format)) {
            val showTenths = currentSettings.showTenthsThresholdMs != 0L
            Column {
                BehaviorSwitch(stringResource(R.string.settings_always_hours), currentSettings.alwaysShowHours, topRounded = true) { onSettingsChanged(currentSettings.copy(alwaysShowHours = it)) }
                BehaviorSwitch(stringResource(R.string.settings_always_minutes), currentSettings.alwaysShowMinutes) { onSettingsChanged(currentSettings.copy(alwaysShowMinutes = it)) }

                // Sits with the two switches above because they answer neighbouring questions:
                // those decide which units appear, this decides how each one is written. Carries
                // the same surface as the tenths row further down, so the stack stays unbroken --
                // a bare row here left a gap of bare background between the switches.
                Surface(
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column {
                        Text(
                            stringResource(R.string.settings_leading_zeros),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            TimePadding.entries.forEachIndexed { i, p ->
                                SegmentedButton(
                                    selected = currentSettings.timePadding == p,
                                    onClick = { onSettingsChanged(currentSettings.copy(timePadding = p)) },
                                    shape = SegmentedButtonDefaults.itemShape(i, TimePadding.entries.size),
                                    label = { Text(p.label(), style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }

                BehaviorSwitch(
                    label = stringResource(R.string.settings_show_tenths),
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
                                val label = if (s == -1) stringResource(R.string.settings_all) else "${s}s"
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
                    label = stringResource(R.string.settings_show_hundredths),
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
                            Text(stringResource(R.string.settings_threshold), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(2f)) {
                                SegmentedButton(
                                    selected = !currentSettings.showHundredthsOnlyUnder10s,
                                    onClick = { onSettingsChanged(currentSettings.copy(showHundredthsOnlyUnder10s = false)) },
                                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                                    label = { Text(stringResource(R.string.settings_like_tenths), style = MaterialTheme.typography.labelSmall) }
                                )
                                SegmentedButton(
                                    selected = currentSettings.showHundredthsOnlyUnder10s,
                                    onClick = { onSettingsChanged(currentSettings.copy(showHundredthsOnlyUnder10s = true)) },
                                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                                    label = { Text(stringResource(R.string.settings_under_10s), style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            }
        }

        SettingsSection(stringResource(R.string.settings_information)) {
            Column {
                BehaviorSwitch(stringResource(R.string.settings_show_period), currentSettings.showCurrentPeriod, topRounded = true) { onSettingsChanged(currentSettings.copy(showCurrentPeriod = it)) }
                BehaviorSwitch(stringResource(R.string.settings_always_show_moves), currentSettings.alwaysShowMoveCount, bottomRounded = true) { onSettingsChanged(currentSettings.copy(alwaysShowMoveCount = it)) }
            }
        }

        SettingsSection(stringResource(R.string.settings_layout_scale)) {
            Column {
                BehaviorSwitch(stringResource(R.string.settings_active_side_bigger), currentSettings.activePlayerSideBigger, topRounded = true) { onSettingsChanged(currentSettings.copy(activePlayerSideBigger = it)) }
                Row(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.settings_orientation), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    SingleChoiceSegmentedButtonRow {
                        ClockOrientation.entries.forEachIndexed { i, o ->
                            val label = when(o) {
                                ClockOrientation.VERTICAL -> stringResource(R.string.settings_vert)
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
                BehaviorSwitch(stringResource(R.string.settings_flash_low_time), currentSettings.flashOnLowTime) { onSettingsChanged(currentSettings.copy(flashOnLowTime = it)) }
                BehaviorSwitch(stringResource(R.string.settings_keep_awake), currentSettings.forceScreenOn) { onSettingsChanged(currentSettings.copy(forceScreenOn = it)) }
                BehaviorSwitch(stringResource(R.string.settings_force_brightness), currentSettings.forceFullBrightness) { onSettingsChanged(currentSettings.copy(forceFullBrightness = it)) }
                BehaviorSwitch(stringResource(R.string.settings_fullscreen), currentSettings.fullscreenMode, bottomRounded = true) { onSettingsChanged(currentSettings.copy(fullscreenMode = it)) }
            }
        }

        SettingsSection(stringResource(R.string.settings_theme)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                AppThemeMode.entries.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = currentSettings.themeMode == t,
                        onClick = { onSettingsChanged(currentSettings.copy(themeMode = t)) },
                        shape = SegmentedButtonDefaults.itemShape(i, AppThemeMode.entries.size),
                        label = { Text(t.label(), style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }

        SettingsSection(stringResource(R.string.settings_primary_colors)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_active_bg), style = MaterialTheme.typography.labelMedium)
                ColorRow(currentSettings.activeColor) { onSettingsChanged(currentSettings.copy(activeColor = it)) }

                Text(stringResource(R.string.settings_inactive_bg), style = MaterialTheme.typography.labelMedium)
                ColorRow(currentSettings.inactiveColor) { onSettingsChanged(currentSettings.copy(inactiveColor = it)) }

                Text(stringResource(R.string.settings_active_text), style = MaterialTheme.typography.labelMedium)
                ColorRow(currentSettings.activeTextColor) { onSettingsChanged(currentSettings.copy(activeTextColor = it)) }

                Text(stringResource(R.string.settings_inactive_text), style = MaterialTheme.typography.labelMedium)
                ColorRow(currentSettings.inactiveTextColor) { onSettingsChanged(currentSettings.copy(inactiveTextColor = it)) }
            }
        }

        SettingsSection(stringResource(R.string.settings_secondary_info)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_label_text_color), style = MaterialTheme.typography.labelMedium)
                ColorRow(currentSettings.secondaryTextColor) { onSettingsChanged(currentSettings.copy(secondaryTextColor = it)) }

                Text(stringResource(R.string.settings_alert_text_color), style = MaterialTheme.typography.labelMedium)
                ColorRow(currentSettings.alertTextColor) { onSettingsChanged(currentSettings.copy(alertTextColor = it)) }
            }
        }

        SettingsSection(stringResource(R.string.settings_special_states)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_loss_bg), style = MaterialTheme.typography.labelMedium)
                ColorRow(currentSettings.lossColor) { onSettingsChanged(currentSettings.copy(lossColor = it)) }

                Text(stringResource(R.string.settings_gong_reflection_bg), style = MaterialTheme.typography.labelMedium)
                ColorRow(currentSettings.reflectionColor) { onSettingsChanged(currentSettings.copy(reflectionColor = it)) }
            }
        }
        Spacer(Modifier.height(64.dp))
    }
}
