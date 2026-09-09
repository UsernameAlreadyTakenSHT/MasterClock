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

    /** Whether this build can reach this page at all; see the app module for the full note. */
    fun isReachableInThisBuild(): Boolean = when (this) {
        MODES -> true
        BEHAVIOR, DISPLAY, AUDIO -> FlavorConfig.hasFullSettingsTabs()
        MORE -> FlavorConfig.hasMoreTab()
        OMNI -> FlavorConfig.hasOmni()
    }

    companion object {
        /** Resolves a category from a navigation route; see the app module for the full note. */
        fun fromRoute(name: String): SettingsCategory =
            entries.firstOrNull { it.name == name && it.isReachableInThisBuild() } ?: MODES

        fun getVisibleCategories(): List<SettingsCategory> {
            // Omni has no navbar tab -- see app module's SettingsScreen.kt for the equivalent fix.
            return entries.filter { it != OMNI && it.isReachableInThisBuild() }
        }
    }
}

/**
 * The E-Ink settings screen, which is the Modes page and nothing else.
 *
 * It used to take eight more callbacks -- clear logs, reset settings, export, backup, import,
 * restore, share, open a tool -- and carry the two confirmation dialogs for the first two. None of
 * it could ever run: getVisibleCategories() yields a single category for E_INK, so the bottom bar
 * is never drawn and `category` can never become anything but MODES; six of those callbacks were
 * not referenced in the body at all, and the two dialog flags were only ever written `false`.
 *
 * Adding a page here means adding the callbacks back along with the UI that calls them, not before.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentSettings: ChessClockSettings,
    category: SettingsCategory,
    onSettingsChanged: (ChessClockSettings) -> Unit,
    onBackClick: () -> Unit,
    onCategoryChanged: (SettingsCategory) -> Unit
) {

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
}
