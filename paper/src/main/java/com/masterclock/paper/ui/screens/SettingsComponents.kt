package com.masterclock.paper.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
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
            modifier = modifier.height(44.dp)
        ) {
            Text(
                text = title, 
                style = MaterialTheme.typography.labelLarge
            )
        }
    } else {
        OutlinedButtonMMD(
            onClick = onClick,
            modifier = modifier.height(44.dp)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) }
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
            enabled = enabled
        )
    }
}

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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeField(m.toString(), "m", Modifier.weight(1f)) {
                val newM = it.toIntOrNull()?.coerceIn(0, 60) ?: 0
                onTimeChange((newM * 60000L) + (s * 1000L))
            }
            TimeField(s.toString(), "s", Modifier.weight(1f)) {
                val newS = it.toIntOrNull()?.coerceIn(0, 59) ?: 0
                onTimeChange((m * 60000L) + (newS * 1000L))
            }
        }
    }
}

@Composable
fun TimeField(value: String, suffix: String, modifier: Modifier = Modifier, onValueChange: (String) -> Unit) {
    MMDTextField(
        value = if (value == "0") "" else value,
        onValueChange = { if (it.length <= 2) onValueChange(it) },
        modifier = modifier,
        placeholder = "", // NO PLACEHOLDER (Clean/Minimal)
        suffix = suffix,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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

@Composable
fun ColorRow(selectedColor: Long, onColorSelected: (Long) -> Unit) {
    val colors = remember { listOf(0xFF000000, 0xFFFFFFFF) }
    
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        colors.forEach { colorVal ->
            val isSelected = selectedColor == colorVal
            Surface(
                onClick = { if (!isSelected) onColorSelected(colorVal) }, 
                modifier = Modifier.size(48.dp), 
                shape = CircleShape, 
                color = Color(colorVal), 
                border = BorderStroke(width = if (isSelected) 4.dp else 2.dp, color = MaterialTheme.colorScheme.onBackground),
                interactionSource = remember { MutableInteractionSource() }
            ) {}
        }
    }
}

/** One credit/licence row. Flat e-ink styling: no dimmed variant, everything on onSurface. */
@Composable
private fun CreditRow(entry: AppInfo.CreditEntry) {
    val uriHandler = LocalUriHandler.current
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
                        ) { runCatching { uriHandler.openUri(url) } }
                    ) { append(url) }
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun ChangelogCreditsDialog(onDismiss: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(stringResource(R.string.credits_tab_changes), stringResource(R.string.credits_tab_credits), stringResource(R.string.credits_tab_licenses))

    Dialog(onDismissRequest = onDismiss) {
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

                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    tabs.forEachIndexed { index, label ->
                        Tab(
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

                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
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
                            AppInfo.CREDITS.forEach { CreditRow(it) }
                            Text(
                                stringResource(R.string.credits_rules_documents),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            AppInfo.RULES_CREDITS.forEach { CreditRow(it) }
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

                ButtonMMD(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.common_close), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
