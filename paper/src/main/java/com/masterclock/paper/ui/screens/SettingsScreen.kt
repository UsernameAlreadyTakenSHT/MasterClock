package com.masterclock.paper.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.masterclock.app.logic.*
import com.masterclock.paper.ui.components.*
import com.masterclock.paper.R
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD

/**
 * The E-Ink settings screen, which is the Modes page and nothing else.
 *
 * It held the phone app's categories and a tab bar for them, none of which could ever show: E-Ink
 * reaches one page. Adding one means adding the callbacks along with the UI that calls them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentSettings: ChessClockSettings,
    onSettingsChanged: (ChessClockSettings) -> Unit,
    onBackClick: () -> Unit,
) {
    Scaffold(
        // MMD's bars bring their own rule against the content, so the dividers that used to be
        // drawn beside them are gone.
        topBar = {
            TopAppBarMMD(
                title = { Text(stringResource(R.string.settings_tab_modes).uppercase(), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
                }
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp)) {
            ModesSettingsPage(currentSettings = currentSettings, onSettingsChanged = onSettingsChanged)
        }
    }
}
