package com.masterclock.paper.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.masterclock.app.logic.*
import com.masterclock.paper.ui.navigation.Route
import com.masterclock.paper.ui.components.*

enum class SettingsCategory(val label: String, val icon: ImageVector) {
    MODES("Modes", Icons.Default.Timer),
    BEHAVIOR("Behavior", Icons.Default.SettingsSuggest),
    DISPLAY("Display", Icons.Default.Palette),
    AUDIO("Audio", Icons.AutoMirrored.Filled.VolumeUp),
    MORE("More", Icons.Default.Menu),
    OMNI("Omni", Icons.Default.Dataset);

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
                    title = { Text(category.label.uppercase(), style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
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
                                icon = { Icon(cat.icon, cat.label) },
                                label = { Text(cat.label.uppercase(), style = MaterialTheme.typography.labelMedium) },
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
                        Text("This setting page is not yet optimized for E-Ink.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    if (showResetSettingsDialog) {
        MMDAlertDialog(
            onDismissRequest = { showResetSettingsDialog = false },
            title = "Reset Settings",
            text = "Are you sure you want to reset all settings to default? This action cannot be undone.",
            confirmButtonText = "Reset",
            onConfirm = { 
                onResetSettings()
                showResetSettingsDialog = false 
            },
            dismissButtonText = "Cancel",
            onDismiss = { showResetSettingsDialog = false }
        )
    }

    if (showClearLogsDialog) {
        MMDAlertDialog(
            onDismissRequest = { showClearLogsDialog = false },
            title = "Clear Logs",
            text = "Permanently delete all game history?",
            confirmButtonText = "Clear",
            onConfirm = { 
                onClearLogs()
                showClearLogsDialog = false 
            },
            dismissButtonText = "Cancel",
            onDismiss = { showClearLogsDialog = false }
        )
    }
}
