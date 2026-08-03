package com.masterclock.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.masterclock.app.R
import com.masterclock.app.logic.*

/**
 * `ActivityResultContracts.GetContent()` (ACTION_GET_CONTENT) doesn't reliably support persistable
 * URI permissions -- only ACTION_OPEN_DOCUMENT does. Custom sound pickers were using GetContent()
 * with no `takePersistableUriPermission` call at all, so the read grant died with the app process:
 * the stored URI became unreadable on next launch, silently falling back to the default sound. This
 * helper takes the persistable grant right after picking, so callers only need
 * to switch their launcher to `ActivityResultContracts.OpenDocument()`.
 */
private fun persistReadPermission(context: android.content.Context, uri: Uri) {
    try {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (e: Exception) {
        Log.w("AudioSettingsPage", "Could not persist read permission for $uri, sound may not survive app restart", e)
    }
}

@Composable
fun AudioSettingsPage(
    onPreviewSwitchSound: () -> Unit,
    onPreviewBeep: () -> Unit,
    onPreviewGong: () -> Unit,
    onPreviewFinalBeep: () -> Unit,
    onPreviewVoice: () -> Unit,
    currentSettings: ChessClockSettings,
    onSettingsChanged: (ChessClockSettings) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(8.dp))

        SettingsSection(stringResource(R.string.settings_sound)) {
            Column {
                BehaviorSwitch(stringResource(R.string.settings_play_switch_sound), currentSettings.playSwitchSound, topRounded = true) { onSettingsChanged(currentSettings.copy(playSwitchSound = it)) }
                BehaviorSwitch(stringResource(R.string.settings_voice_announcements), currentSettings.voiceAnnouncementsEnabled, bottomRounded = true) { onSettingsChanged(currentSettings.copy(voiceAnnouncementsEnabled = it)) }
            }
        }

        SettingsSection(stringResource(R.string.settings_timer_alerts)) {
            Column {
                val beepEnabled = currentSettings.beepThreshold != BeepCountdownThreshold.OFF
                BehaviorSwitch(
                    label = stringResource(R.string.settings_countdown_beep),
                    checked = beepEnabled,
                    topRounded = true
                ) { enabled ->
                    onSettingsChanged(currentSettings.copy(beepThreshold = if (enabled) BeepCountdownThreshold.THREE_SEC else BeepCountdownThreshold.OFF))
                }
                if (beepEnabled) {
                    Surface(
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        val options = listOf(BeepCountdownThreshold.THREE_SEC, BeepCountdownThreshold.TEN_SEC)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            options.forEachIndexed { i, t ->
                                val label = if (t == BeepCountdownThreshold.THREE_SEC) "3s" else "10s"
                                SegmentedButton(
                                    selected = currentSettings.beepThreshold == t,
                                    onClick = { onSettingsChanged(currentSettings.copy(beepThreshold = t)) },
                                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
                BehaviorSwitch(stringResource(R.string.settings_triple_beep), currentSettings.tripleBeepTimeUp, bottomRounded = true) { onSettingsChanged(currentSettings.copy(tripleBeepTimeUp = it)) }
            }
        }

        SettingsSection(stringResource(R.string.settings_haptics)) {
            Column {
                BehaviorSwitch(stringResource(R.string.settings_haptic_switch), currentSettings.hapticFeedback, topRounded = true) { onSettingsChanged(currentSettings.copy(hapticFeedback = it)) }

                val hapticEnabled = currentSettings.hapticCountdownThreshold != BeepCountdownThreshold.OFF
                BehaviorSwitch(
                    label = stringResource(R.string.settings_haptic_countdown),
                    checked = hapticEnabled,
                    bottomRounded = !hapticEnabled
                ) { enabled ->
                    onSettingsChanged(currentSettings.copy(hapticCountdownThreshold = if (enabled) BeepCountdownThreshold.THREE_SEC else BeepCountdownThreshold.OFF))
                }
                if (hapticEnabled) {
                    Surface(
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        val options = listOf(BeepCountdownThreshold.THREE_SEC, BeepCountdownThreshold.TEN_SEC)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            options.forEachIndexed { i, t ->
                                val label = if (t == BeepCountdownThreshold.THREE_SEC) "3s" else "10s"
                                SegmentedButton(
                                    selected = currentSettings.hapticCountdownThreshold == t,
                                    onClick = { onSettingsChanged(currentSettings.copy(hapticCountdownThreshold = t)) },
                                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            }
        }

        SettingsSection(stringResource(R.string.settings_output_volume)) {
            Column {
                BehaviorSwitch(
                    label = stringResource(R.string.settings_use_media_output),
                    checked = currentSettings.audioOutputMedia,
                    topRounded = true
                ) { onSettingsChanged(currentSettings.copy(audioOutputMedia = it)) }

                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column {
                        Text(stringResource(R.string.settings_sounds_volume), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Slider(
                            value = currentSettings.soundsVolume,
                            onValueChange = { onSettingsChanged(currentSettings.copy(soundsVolume = it)) },
                            valueRange = 0f..1f
                        )
                    }
                    Column {
                        Text(stringResource(R.string.settings_voice_volume), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Slider(
                            value = currentSettings.voiceVolume,
                            onValueChange = { onSettingsChanged(currentSettings.copy(voiceVolume = it)) },
                            valueRange = 0f..1f
                        )
                    }
                }
            }
        }

        SettingsSection(stringResource(R.string.settings_sound_previews)) {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onPreviewSwitchSound, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text(stringResource(R.string.settings_switch), style = MaterialTheme.typography.labelSmall) }
                    Button(onClick = onPreviewBeep, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text(stringResource(R.string.settings_beep), style = MaterialTheme.typography.labelSmall) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onPreviewGong, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text(stringResource(R.string.settings_gong), style = MaterialTheme.typography.labelSmall) }
                    Button(onClick = onPreviewFinalBeep, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text(stringResource(R.string.settings_final), style = MaterialTheme.typography.labelSmall) }
                }
                Button(onClick = onPreviewVoice, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) { Text(stringResource(R.string.settings_voice_announcement), style = MaterialTheme.typography.labelSmall) }
            }
        }

        SettingsSection(stringResource(R.string.settings_custom_sounds)) {
            val context = LocalContext.current
            val switchLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { persistReadPermission(context, it); onSettingsChanged(currentSettings.copy(customSwitchUri = it.toString())) } }
            val beepLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { persistReadPermission(context, it); onSettingsChanged(currentSettings.copy(customBeepUri = it.toString())) } }
            val finalBeepLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { persistReadPermission(context, it); onSettingsChanged(currentSettings.copy(customFinalBeepUri = it.toString())) } }
            val gongLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { persistReadPermission(context, it); onSettingsChanged(currentSettings.copy(customGongUri = it.toString())) } }

            Column {
                CustomSoundItem(
                    label = stringResource(R.string.settings_beep_countdown_sound),
                    uri = currentSettings.customBeepUri,
                    topRounded = true,
                    onClear = { onSettingsChanged(currentSettings.copy(customBeepUri = null)) }
                ) { beepLauncher.launch(arrayOf("audio/*")) }
                CustomSoundItem(
                    label = stringResource(R.string.settings_gong_mode_sound),
                    uri = currentSettings.customGongUri,
                    onClear = { onSettingsChanged(currentSettings.copy(customGongUri = null)) }
                ) { gongLauncher.launch(arrayOf("audio/*")) }
                CustomSoundItem(
                    label = stringResource(R.string.settings_final_beep_sound),
                    uri = currentSettings.customFinalBeepUri,
                    onClear = { onSettingsChanged(currentSettings.copy(customFinalBeepUri = null)) }
                ) { finalBeepLauncher.launch(arrayOf("audio/*")) }
                CustomSoundItem(
                    label = stringResource(R.string.settings_switch_sound),
                    uri = currentSettings.customSwitchUri,
                    bottomRounded = true,
                    onClear = { onSettingsChanged(currentSettings.copy(customSwitchUri = null)) }
                ) { switchLauncher.launch(arrayOf("audio/*")) }
            }
        }
        Spacer(Modifier.height(64.dp))
    }
}
