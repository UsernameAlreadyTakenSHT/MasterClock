package com.masterclock.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.masterclock.app.logic.*

@Composable
fun ModesSettingsPage(currentSettings: ChessClockSettings, onSettingsChanged: (ChessClockSettings) -> Unit, onOmniClick: () -> Unit) {
    var selectedPlayerTab by remember { mutableIntStateOf(0) }
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
            onUpdate = { p: PlayerSettings -> updateTarget(p) },
            onUpdateGlobal = { loop: Boolean, pause: Long -> onSettingsChanged(currentSettings.copy(loopPhases = loop, pauseBetweenPhasesMs = pause)) }
        )

        if (FlavorConfig.isEInk()) {
            Spacer(Modifier.height(24.dp))
            SettingsSection("E-Ink Options") {
                BehaviorSwitch(
                    label = "Reverse Colors (Dark Mode)",
                    checked = currentSettings.eInkDarkMode,
                    topRounded = true,
                    bottomRounded = true
                ) {
                    onSettingsChanged(currentSettings.copy(eInkDarkMode = it))
                }
            }
        }

        Spacer(Modifier.height(64.dp))
    }
}
