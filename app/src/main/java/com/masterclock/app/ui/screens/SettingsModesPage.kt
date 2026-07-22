package com.masterclock.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.masterclock.app.BuildConfig
import com.masterclock.app.logic.*

@Composable
fun ModesSettingsPage(currentSettings: ChessClockSettings, onSettingsChanged: (ChessClockSettings) -> Unit, onOmniClick: () -> Unit) {
    var selectedPlayerTab by remember { mutableIntStateOf(0) }
    var showChangelog by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (currentSettings.differentSettingsPerPlayer) {
            PrimaryTabRow(selectedTabIndex = selectedPlayerTab, modifier = Modifier.padding(vertical = 4.dp)) {
                Tab(selected = selectedPlayerTab == 0, onClick = { selectedPlayerTab = 0 }) { Text("Player 1", Modifier.padding(12.dp)) }
                Tab(selected = selectedPlayerTab == 1, onClick = { selectedPlayerTab = 1 }) { Text("Player 2", Modifier.padding(12.dp)) }
            }
        }

        val targetP = if (!currentSettings.differentSettingsPerPlayer) {
            currentSettings.main
        } else {
            if (selectedPlayerTab == 0) currentSettings.p1Custom else currentSettings.p2Custom.copy(mode = currentSettings.p1Custom.mode)
        }

        fun updateTarget(newP: PlayerSettings) {
            val newSettings = if (!currentSettings.differentSettingsPerPlayer) {
                currentSettings.copy(main = newP, p1Custom = newP, p2Custom = newP)
            } else {
                if (selectedPlayerTab == 0) {
                    currentSettings.copy(
                        p1Custom = newP,
                        p2Custom = currentSettings.p2Custom.copy(mode = newP.mode)
                    )
                } else {
                    currentSettings.copy(p2Custom = newP)
                }
            }
            onSettingsChanged(newSettings)
        }

        Spacer(Modifier.height(8.dp))
        if (!currentSettings.differentSettingsPerPlayer || selectedPlayerTab == 0) {
            ModeSelectionPanel(
                p = targetP,
                isOneForAll = currentSettings.isOneForAll,
                onUpdateP = { p: PlayerSettings -> updateTarget(p) },
                onUpdateOneForAll = { b: Boolean -> onSettingsChanged(currentSettings.copy(isOneForAll = b)) },
                onOmniClick = onOmniClick
            )
        }

        TimeParameterPanel(
            p = targetP,
            loopPhases = currentSettings.loopPhases,
            pauseMs = currentSettings.pauseBetweenPhasesMs,
            allowPhaseSkip = currentSettings.allowPhaseSkip,
            onUpdate = { p: PlayerSettings -> updateTarget(p) },
            onUpdateGlobal = { loop: Boolean, pause: Long, allowSkip: Boolean -> onSettingsChanged(currentSettings.copy(loopPhases = loop, pauseBetweenPhasesMs = pause, allowPhaseSkip = allowSkip)) }
        )

        if (!FlavorConfig.hasFullSettingsTabs()) {
            Spacer(Modifier.height(24.dp))
            SettingsSection("System behavior") {
                BehaviorSwitch(
                    label = "Sound",
                    checked = currentSettings.playSwitchSound
                ) {
                    onSettingsChanged(currentSettings.copy(playSwitchSound = it, tripleBeepTimeUp = it))
                }
                BehaviorSwitch(
                    label = "Haptic feedback",
                    checked = currentSettings.hapticFeedback
                ) {
                    onSettingsChanged(currentSettings.copy(hapticFeedback = it))
                }
                BehaviorSwitch(
                    label = "Keep screen awake",
                    checked = currentSettings.forceScreenOn
                ) {
                    onSettingsChanged(currentSettings.copy(forceScreenOn = it))
                }
            }
        }

        if (!FlavorConfig.hasMoreTab()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showChangelog = true }
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                Text(AppInfo.BUILD_DATE, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }

        Spacer(Modifier.height(64.dp))
    }

    if (showChangelog) {
        ChangelogCreditsDialog(onDismiss = { showChangelog = false })
    }
}
