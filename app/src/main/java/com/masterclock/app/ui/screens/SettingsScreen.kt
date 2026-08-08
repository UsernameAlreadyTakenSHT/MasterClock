package com.masterclock.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.masterclock.app.R
import com.masterclock.app.logic.*
import com.masterclock.app.ui.navigation.Route

/** [labelRes] rather than a String: an enum cannot call stringResource, so it carries the id. */
enum class SettingsCategory(@StringRes val labelRes: Int, val icon: ImageVector) {
    MODES(R.string.settings_tab_modes, Icons.Default.Timer),
    BEHAVIOR(R.string.settings_tab_behavior, Icons.Default.SettingsSuggest),
    DISPLAY(R.string.settings_tab_display, Icons.Default.Palette),
    AUDIO(R.string.settings_tab_audio, Icons.AutoMirrored.Filled.VolumeUp),
    MORE(R.string.settings_tab_more, Icons.Default.Menu),
    OMNI(R.string.settings_tab_omni, Icons.Default.Dataset);

    companion object {
        /**
         * Resolves a category stored in a navigation route, falling back to [MODES].
         *
         * valueOf would throw on a name this build does not have. The names only ever come from
         * this enum today, but a navigation back stack is restored across process death: a stack
         * saved by an older version, from before a category was renamed or dropped, would crash
         * the app on the way back in rather than land on the Modes tab.
         */
        fun fromRoute(name: String): SettingsCategory =
            entries.firstOrNull { it.name == name } ?: MODES

        fun getVisibleCategories(): List<SettingsCategory> {
            // Omni has no navbar tab -- it's reached only via the "Omni-Timer" ModeCard in the
            // Modes page (SettingsComponents.kt), which switches the category directly.
            return entries.filter { category ->
                when (category) {
                    OMNI -> false
                    MORE -> FlavorConfig.hasMoreTab()
                    MODES -> true
                    BEHAVIOR, DISPLAY, AUDIO -> FlavorConfig.hasFullSettingsTabs()
                }
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
                title = { Text(stringResource(category.labelRes), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
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
                            icon = { Icon(cat.icon, stringResource(cat.labelRes)) },
                            label = { Text(stringResource(cat.labelRes)) }
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
            title = { Text(stringResource(R.string.settings_reset_title)) },
            text = { Text(stringResource(R.string.settings_reset_message)) },
            confirmButton = {
                TextButton(onClick = { onResetSettings(); showResetSettingsDialog = false }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetSettingsDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showClearLogsDialog) {
        AlertDialog(
            onDismissRequest = { showClearLogsDialog = false },
            title = { Text(stringResource(R.string.settings_clear_logs_title)) },
            text = { Text(stringResource(R.string.settings_clear_logs_message)) },
            confirmButton = {
                TextButton(onClick = { onClearLogs(); showClearLogsDialog = false }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(stringResource(R.string.common_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearLogsDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}
