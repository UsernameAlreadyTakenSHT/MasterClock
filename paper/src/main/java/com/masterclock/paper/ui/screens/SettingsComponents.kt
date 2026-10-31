package com.masterclock.paper.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masterclock.app.logic.*
import com.masterclock.paper.ui.components.*
import com.masterclock.paper.BuildConfig
import com.masterclock.paper.R
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.switcher.SwitchMMD
import com.mudita.mmd.components.tabs.PrimaryTabRowMMD
import com.mudita.mmd.components.tabs.TabMMD
import com.mudita.mmd.components.text_field.TextFieldMMD

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp, top = 16.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

@Composable
fun ModeCard(title: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (selected) {
        ButtonMMD(
            onClick = onClick,
            // 48dp, the smallest target Android recommends; 44 was under it.
            modifier = modifier.height(48.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge
            )
        }
    } else {
        OutlinedButtonMMD(
            onClick = onClick,
            modifier = modifier.height(48.dp),
            border = outlinedButtonBorderMMD()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
fun BehaviorSwitch(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    // One switch for TalkBack: the row, with its label, its state and the role. The row was a bare
    // clickable and the switch kept its own node, so TalkBack stopped twice -- on the label with no
    // state, then on an unlabelled "Switch, off".
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )
        SwitchMMD(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

private const val MAX_TIME_MS = 60 * 60_000L

@Composable
fun MSInput(label: String, timeMs: Long, onTimeChange: (Long) -> Unit) {
    val totalSeconds = timeMs / 1000
    val m = (totalSeconds / 60).toInt()
    val s = (totalSeconds % 60).toInt()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground, // AVOID GREY: 100% opacity
            fontWeight = FontWeight.Medium
        )
        // An hour at most, so 60 minutes leaves no room for seconds: each field was clamped on its
        // own, which let the pair reach 60:59.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeField(m.toString(), "m", stringResource(R.string.a11y_field_minutes, label), Modifier.weight(1f)) {
                val newM = it.toIntOrNull()?.coerceIn(0, 60) ?: 0
                onTimeChange(((newM * 60000L) + (s * 1000L)).coerceAtMost(MAX_TIME_MS))
            }
            TimeField(s.toString(), "s", stringResource(R.string.a11y_field_seconds, label), Modifier.weight(1f)) {
                val newS = it.toIntOrNull()?.coerceIn(0, 59) ?: 0
                onTimeChange(((m * 60000L) + (newS * 1000L)).coerceAtMost(MAX_TIME_MS))
            }
        }
    }
}

@Composable
fun TimeField(value: String, suffix: String, description: String, modifier: Modifier = Modifier, onValueChange: (String) -> Unit) {
    // No placeholder: an empty field already reads as zero.
    TextFieldMMD(
        value = if (value == "0") "" else value,
        // Digits only. The number keyboard also offers "-" and ".", which read as no number at all
        // and emptied the field.
        onValueChange = { typed -> typed.filter(Char::isDigit).take(2).let { if (it.length == typed.length) onValueChange(it) } },
        // Which time and which unit: TalkBack read only "edit box, m", the label above not being
        // part of the field.
        modifier = modifier.semantics { contentDescription = description },
        suffix = { Text(suffix) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge
    )
}

@Composable
fun ModeSelectionPanel(p: PlayerSettings, onUpdateP: (PlayerSettings) -> Unit) {
    val mainMode = when (p.mode) {
        TimerMode.SUDDEN_DEATH -> 0
        TimerMode.FISCHER -> 1
        TimerMode.MOVE_TIMER_STANDARD -> 2
        else -> 0
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ModeCard("Timer", mainMode == 0, Modifier.weight(1f)) { onUpdateP(p.copy(mode = TimerMode.SUDDEN_DEATH)) }
        ModeCard("Fischer", mainMode == 1, Modifier.weight(1f)) { onUpdateP(p.copy(mode = TimerMode.FISCHER)) }
        ModeCard("Move", mainMode == 2, Modifier.weight(1f)) { onUpdateP(p.copy(mode = TimerMode.MOVE_TIMER_STANDARD)) }
    }

    Spacer(Modifier.height(8.dp))

    if (mainMode != 2) { // NO Initial Time for Move Timer
        MSInput(stringResource(R.string.settings_initial_time), p.initialTimeMs) { onUpdateP(p.copy(initialTimeMs = it)) }
    }

    if (mainMode == 1) {
        MSInput(stringResource(R.string.settings_increment), p.incrementMs) { onUpdateP(p.copy(incrementMs = it)) }
    }
    if (mainMode == 2) {
        MSInput(stringResource(R.string.settings_move_time), p.moveTimeMs) { onUpdateP(p.copy(moveTimeMs = it)) }
    }
}

/** One credit/licence row. Flat e-ink styling: no dimmed variant, everything on onSurface. */
@Composable
private fun CreditRow(entry: AppInfo.CreditEntry) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    // Hoisted: shown from the link's handler, not composable scope.
    val noBrowserText = stringResource(R.string.toast_no_browser)
    Column {
        Text(entry.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(entry.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        entry.url?.let { url ->
            // A real link, not text that merely looks like one -- LinkAnnotation is also what makes
            // a screen reader announce it as one. Underlined rather than coloured: e-ink has no
            // colour to spare, and the flat onSurface styling here is deliberate.
            // openUri throws when the device has no browser, which is not worth a crash.
            Text(
                text = buildAnnotatedString {
                    withLink(
                        LinkAnnotation.Url(
                            url,
                            TextLinkStyles(
                                style = SpanStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textDecoration = TextDecoration.Underline
                                )
                            )
                        ) {
                            // A Kompakt has no browser, and the tap did nothing at all; it says why.
                            runCatching { uriHandler.openUri(url) }
                                .onFailure { android.widget.Toast.makeText(context, noBrowserText, android.widget.Toast.LENGTH_SHORT).show() }
                        }
                    ) { append(url) }
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

// PrimaryTabRowMMD carries Material's experimental marker, inherited from the tab row it wraps.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogCreditsDialog(onDismiss: () -> Unit) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(stringResource(R.string.credits_tab_changes), stringResource(R.string.credits_tab_credits), stringResource(R.string.credits_tab_licenses))

    Dialog(onDismissRequest = onDismiss) {
        NoDialogDim()
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    MMDDefaults.BorderWidth,
                    MaterialTheme.colorScheme.onSurface,
                    RoundedCornerShape(MMDDefaults.CornerRadius)
                ),
            shape = RoundedCornerShape(MMDDefaults.CornerRadius),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.settings_app_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        AppInfo.BUILD_DATE,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                PrimaryTabRowMMD(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    tabs.forEachIndexed { index, label ->
                        TabMMD(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            // A third tab leaves each one about a third of the dialog, where the
                            // default label size wrapped "Changelog" onto a second line.
                            text = {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    // See the app module: clipping mid-glyph is how "Changelog"
                                    // lost its tail; an ellipsis degrades readably instead.
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }

                // The changelog runs well past the 320dp cap, and inside a dialog there is even
                // less to hint at it than on a full page. Same scrollbar as the settings page.
                // A scroll state per tab visit, so every tab starts at its own beginning: one shared
                // state landed you halfway down Licenses because you had scrolled Changes. It was
                // reset by a LaunchedEffect, which runs after the frame it is in, so the new tab was
                // drawn once at the old offset and then again at the top -- two refreshes on E Ink.
                // A fresh state is at the top from its first frame.
                val entriesScroll = remember(selectedTab) { ScrollState(0) }

                // weight, not a bare height cap. Without it the entries take their 320dp off the
                // top and Close, measured last with nothing left, comes out zero-high: on a real
                // Kompakt screen (480x800 at 240dpi, so 320x533dp) the button was not merely
                // cramped, it was absent from the layout altogether and the dialog could only be
                // dismissed by tapping outside it.
                //
                // fill = false so the entries still stop at 320dp on a screen with room to spare;
                // the weight only ever takes space away, never adds it. Close is unweighted and so
                // is measured first, at its full height -- the scrolling half is the half that can
                // afford to shrink.
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .heightIn(max = 320.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(entriesScroll),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (selectedTab) {
                            0 -> AppInfo.CHANGELOG.forEach { entry ->
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
                                AppInfo.credits().forEach { CreditRow(it) }
                                // Always empty here -- paper ships no rulebook and no screen to
                                // open one -- so the heading has to go with the list.
                                val rulesCredits = AppInfo.rulesCredits()
                                if (rulesCredits.isNotEmpty()) {
                                    Text(
                                        stringResource(R.string.credits_rules_documents),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    rulesCredits.forEach { CreditRow(it) }
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
                                        )
                                    )
                                }
                            }
                        }
                    }

                    ScrollbarMMD(entriesScroll)
                }

                ButtonMMD(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.common_close), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
