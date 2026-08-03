package com.masterclock.app.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.masterclock.app.R

/**
 * Title bar and back arrow shared by every full-screen tool.
 *
 * It lives in its own file, and in src/main rather than beside the tools themselves, because the
 * tool screens are COMPLETE only while StatisticsScreen -- which Standard and Light also reach --
 * uses the same scaffold. Leaving it in ToolsScreens.kt would have pinned that whole file into
 * every flavor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable (RowScope.() -> Unit) = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
                },
                actions = actions
            )
        },
        content = content
    )
}
