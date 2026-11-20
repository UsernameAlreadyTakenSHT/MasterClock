package com.masterclock.paper.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.masterclock.app.logic.AppInfo
import com.masterclock.paper.BuildConfig
import com.masterclock.paper.R
import com.masterclock.paper.ui.components.ScrollbarMMD
import com.masterclock.paper.ui.components.verticalScrollMMD
import com.mudita.mmd.components.tabs.PrimaryTabRowMMD
import com.mudita.mmd.components.tabs.TabMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD

/**
 * The version, what changed in it, the credits and the licences: a screen of its own, reached from
 * the foot of the settings.
 *
 * It was a dialog: three tabs and a text several screens long, in a window with a Close button.
 * Mudita's guide keeps a dialog to one decision, said briefly and dismissed once taken, and this is
 * something read. A screen also has the whole height, where the dialog capped its text at 320dp and
 * had to be laid out with care for its button to be there at all on a Kompakt.
 */
// PrimaryTabRowMMD carries Material's experimental marker, inherited from the tab row it wraps.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBackClick: () -> Unit, onMessage: (String) -> Unit) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(stringResource(R.string.credits_tab_changes), stringResource(R.string.credits_tab_credits), stringResource(R.string.credits_tab_licenses))

    Scaffold(
        topBar = {
            TopAppBarMMD(
                title = {
                    Text(
                        stringResource(R.string.settings_app_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
                }
            )
        },
    ) { innerPadding ->
        Column(
            // A margin below as well: the text ran down to the system's bar, its last line cut
            // against it.
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                AppInfo.BUILD_DATE,
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            PrimaryTabRowMMD(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                tabs.forEachIndexed { index, label ->
                    TabMMD(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        // Three tabs leave each one a third of the width, where the default label
                        // size wrapped "Changelog" onto a second line.
                        text = {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                // See the app module: clipping mid-glyph is how "Changelog" lost its
                                // tail; an ellipsis degrades readably instead.
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }

            // A scroll state per tab visit, so every tab starts at its own beginning: one shared
            // state landed you halfway down Licenses because you had scrolled Changes. It was
            // reset by a LaunchedEffect, which runs after the frame it is in, so the new tab was
            // drawn once at the old offset and then again at the top -- two refreshes on E Ink.
            // A fresh state is at the top from its first frame.
            // Saveable, as rememberScrollState was: a recreation kept the place in the tab.
            val entriesScroll = rememberSaveable(selectedTab, saver = ScrollState.Saver) { ScrollState(0) }

            // The changelog runs to many screens, and E Ink has nothing to hint at it: the same
            // scrollbar as the settings page, there whenever the text overflows.
            Row(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScrollMMD(entriesScroll)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (selectedTab) {
                        0 -> AppInfo.changelogFor().forEach { entry ->
                            Column {
                                Text(
                                    "${entry.version} — ${entry.date}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                entry.notes.forEach { note ->
                                    Text("• $note", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                        1 -> {
                            AppInfo.credits().forEach { CreditRow(it, onMessage) }
                            // Always empty here -- paper ships no rulebook and no screen to open
                            // one -- so the heading has to go with the list.
                            val rulesCredits = AppInfo.rulesCredits()
                            if (rulesCredits.isNotEmpty()) {
                                Text(
                                    stringResource(R.string.credits_rules_documents),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                rulesCredits.forEach { CreditRow(it, onMessage) }
                            }
                        }
                        else -> {
                            Text(
                                stringResource(R.string.credits_libraries),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            AppInfo.ossLicenses().forEach { lib ->
                                CreditRow(
                                    AppInfo.CreditEntry(
                                        title = lib.name,
                                        detail = "${lib.copyright} — ${lib.license}",
                                        url = lib.url,
                                    ),
                                    onMessage,
                                )
                            }
                        }
                    }
                }

                ScrollbarMMD(entriesScroll)
            }
        }
    }
}
