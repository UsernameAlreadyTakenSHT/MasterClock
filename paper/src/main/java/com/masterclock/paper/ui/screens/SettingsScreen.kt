package com.masterclock.paper.ui.screens

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
import androidx.compose.ui.unit.dp
import com.masterclock.app.logic.*
import com.masterclock.paper.ui.navigation.Route
import com.masterclock.paper.ui.components.*
import com.masterclock.paper.R

/** [labelRes] rather than a String: an enum cannot call stringResource, so it carries the id. */
enum class SettingsCategory(@StringRes val labelRes: Int, val icon: ImageVector) {
    MODES(R.string.settings_tab_modes, Icons.Default.Timer),
    BEHAVIOR(R.string.settings_tab_behavior, Icons.Default.SettingsSuggest),
    DISPLAY(R.string.settings_tab_display, Icons.Default.Palette),
    AUDIO(R.string.settings_tab_audio, Icons.AutoMirrored.Filled.VolumeUp),
    MORE(R.string.settings_tab_more, Icons.Default.Menu),
    OMNI(R.string.settings_tab_omni, Icons.Default.Dataset);

    companion object {
        fun getVisibleCategories(): List<SettingsCategory> {
            // Omni has no navbar tab -- see app module's SettingsScreen.kt for the equivalent fix.
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
            Column {
                TopAppBar(
                    title = { Text(stringResource(category.labelRes).uppercase(), style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
            }
        },
        bottomBar = {
            val visibleCategories = SettingsCategory.getVisibleCategories()
            if (visibleCategories.size > 1) {
                Column {
                    HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        visibleCategories.forEach { cat ->
                            NavigationBarItem(
                                selected = category == cat,
                                onClick = { onCategoryChanged(cat) },
                                icon = { Icon(cat.icon, stringResource(cat.labelRes)) },
                                label = { Text(stringResource(cat.labelRes).uppercase(), style = MaterialTheme.typography.labelMedium) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    indicatorColor = androidx.compose.ui.graphics.Color.Transparent
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp)) {
            when (category) {
                SettingsCategory.MODES -> ModesSettingsPage(
                    currentSettings = currentSettings, 
                    onSettingsChanged = onSettingsChanged
                )
                else -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Text(stringResource(R.string.settings_not_optimized), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    if (showResetSettingsDialog) {
        MMDAlertDialog(
            onDismissRequest = { showResetSettingsDialog = false },
            title = stringResource(R.string.settings_reset_settings),
            text = stringResource(R.string.settings_reset_message),
            confirmButtonText = stringResource(R.string.common_reset),
            onConfirm = { 
                onResetSettings()
                showResetSettingsDialog = false 
            },
            dismissButtonText = stringResource(R.string.common_cancel),
            onDismiss = { showResetSettingsDialog = false }
        )
    }

    if (showClearLogsDialog) {
        MMDAlertDialog(
            onDismissRequest = { showClearLogsDialog = false },
            title = stringResource(R.string.settings_clear_logs),
            text = stringResource(R.string.settings_clear_logs_title),
            confirmButtonText = stringResource(R.string.common_clear),
            onConfirm = { 
                onClearLogs()
                showClearLogsDialog = false 
            },
            dismissButtonText = stringResource(R.string.common_cancel),
            onDismiss = { showClearLogsDialog = false }
        )
    }
}
