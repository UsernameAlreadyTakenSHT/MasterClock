package com.masterclock.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.masterclock.app.logic.*
import com.masterclock.app.ui.navigation.Route

enum class SettingsCategory(val label: String, val icon: ImageVector) {
    MODES("Modes", Icons.Default.Timer),
    BEHAVIOR("Behavior", Icons.Default.SettingsSuggest),
    DISPLAY("Display", Icons.Default.Palette),
    AUDIO("Audio", Icons.AutoMirrored.Filled.VolumeUp),
    MORE("More", Icons.Default.Menu),
    OMNI("Omni", Icons.Default.Dataset);

    companion object {
        fun getVisibleCategories(): List<SettingsCategory> {
            // Omni has no navbar tab -- it's reached only via the "Omni-Timer" ModeCard in the
            // Modes page (SettingsComponents.kt), which switches the category directly.
            return entries.filter { category ->
                if (category == OMNI) false
                else if (category == MORE) FlavorConfig.hasMoreTab()
                else FlavorConfig.hasAdvancedSettings()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ChessTimerViewModel,
    currentSettings: ChessClockSettings,
    category: SettingsCategory,
    onSettingsChanged: (ChessClockSettings) -> Unit,
    onClearLogs: () -> Unit,
    onResetSettings: () -> Unit,
    onExportSettings: (Boolean) -> Unit,
    onExportMedia: () -> Unit,
    onImportSettings: () -> Unit,
    onImportMedia: () -> Unit,
    onShareSettings: (Boolean, Boolean) -> Unit,
    onBackClick: () -> Unit,
    onToolClick: (Route) -> Unit,
    onCategoryChanged: (SettingsCategory) -> Unit
) {
    var showResetSettingsDialog by remember { mutableStateOf(false) }
    var showClearLogsDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(category.label, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
        bottomBar = {
            val visibleCategories = SettingsCategory.getVisibleCategories()
            if (visibleCategories.size > 1) {
                NavigationBar {
                    visibleCategories.forEach { cat ->
                        NavigationBarItem(
                            selected = category == cat,
                            onClick = { onCategoryChanged(cat) },
                            icon = { Icon(cat.icon, cat.label) },
                            label = { Text(cat.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp)) {
            when (category) {
                SettingsCategory.MODES -> ModesSettingsPage(
                    currentSettings = currentSettings, 
                    onSettingsChanged = onSettingsChanged,
                    onOmniClick = { onCategoryChanged(SettingsCategory.OMNI) }
                )
                SettingsCategory.BEHAVIOR -> BehaviorSettingsPage(currentSettings, onSettingsChanged)
                SettingsCategory.DISPLAY -> DisplaySettingsPage(currentSettings, onSettingsChanged)
                SettingsCategory.AUDIO -> AudioSettingsPage(
                    onPreviewSwitchSound = { viewModel.previewSwitchSound() },
                    onPreviewBeep = { viewModel.previewBeep() },
                    onPreviewGong = { viewModel.previewGong() },
                    onPreviewFinalBeep = { viewModel.previewFinalBeep() },
                    onPreviewVoice = { viewModel.previewVoice() },
                    currentSettings = currentSettings,
                    onSettingsChanged = onSettingsChanged
                )
                SettingsCategory.OMNI -> {
                    val omniViewModel: OmniTimerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                    SettingsOmniPage(
                        viewModel = omniViewModel,
                        onPlay = { 
                            omniViewModel.startOmni()
                            onToolClick(Route.OmniTimer) 
                        }
                    )
                }
                SettingsCategory.MORE -> MoreSettingsPage(
                    currentSettings = currentSettings,
                    onSettingsChanged = onSettingsChanged,
                    onClearLogs = { showClearLogsDialog = true },
                    onResetSettings = { showResetSettingsDialog = true },
                    onExportSettings = onExportSettings,
                    onExportMedia = onExportMedia,
                    onImportSettings = onImportSettings,
                    onImportMedia = onImportMedia,
                    onShareSettings = onShareSettings,
                    onToolClick = onToolClick,
                    onCategoryChanged = onCategoryChanged,
                    scope = scope
                )
            }
        }
    }

    if (showResetSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showResetSettingsDialog = false },
            title = { Text("Reset all settings?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { onResetSettings(); showResetSettingsDialog = false }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetSettingsDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showClearLogsDialog) {
        AlertDialog(
            onDismissRequest = { showClearLogsDialog = false },
            title = { Text("Clear all game logs?") },
            text = { Text("All recorded history will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = { onClearLogs(); showClearLogsDialog = false }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearLogsDialog = false }) { Text("Cancel") }
            }
        )
    }
}
