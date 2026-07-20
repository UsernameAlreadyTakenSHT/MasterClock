package com.masterclock.paper.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.masterclock.app.logic.*
import com.masterclock.paper.BuildConfig

@Composable
fun ModesSettingsPage(
    currentSettings: ChessClockSettings, 
    onSettingsChanged: (ChessClockSettings) -> Unit, 
    onOmniClick: () -> Unit
) {
    var selectedPlayerTab by remember { mutableIntStateOf(0) }
    var showChangelog by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Scrollable Top Content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            if (currentSettings.differentSettingsPerPlayer) {
                PrimaryTabRow(
                    selectedTabIndex = selectedPlayerTab, 
                    modifier = Modifier.padding(vertical = 4.dp),
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                    Tab(selected = selectedPlayerTab == 0, onClick = { selectedPlayerTab = 0 }) { 
                        Text("Player 1", Modifier.padding(12.dp), fontWeight = FontWeight.Bold) 
                    }
                    Tab(selected = selectedPlayerTab == 1, onClick = { selectedPlayerTab = 1 }) { 
                        Text("Player 2", Modifier.padding(12.dp), fontWeight = FontWeight.Bold) 
                    }
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
            ModeSelectionPanel(
                p = targetP,
                onUpdateP = { p: PlayerSettings -> updateTarget(p) }
            )
            
            Spacer(Modifier.height(32.dp))
        }

        // Fixed Bottom Content (E-Ink Options)
        if (FlavorConfig.isEInk()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
                
                SettingsSection("System behavior") {
                    BehaviorSwitch(
                        label = "Reverse colors",
                        checked = currentSettings.eInkDarkMode
                    ) {
                        onSettingsChanged(currentSettings.copy(eInkDarkMode = it))
                    }
                    BehaviorSwitch(
                        label = "Keep screen awake",
                        checked = currentSettings.forceScreenOn
                    ) {
                        onSettingsChanged(currentSettings.copy(forceScreenOn = it))
                    }
                    BehaviorSwitch(
                        label = "Sound",
                        checked = currentSettings.playSwitchSound
                    ) {
                        onSettingsChanged(currentSettings.copy(playSwitchSound = it))
                    }
                    BehaviorSwitch(
                        label = "Haptic feedback",
                        checked = currentSettings.hapticFeedback
                    ) {
                        onSettingsChanged(currentSettings.copy(hapticFeedback = it))
                    }
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
    }

    if (showChangelog) {
        ChangelogCreditsDialog(onDismiss = { showChangelog = false })
    }
}
