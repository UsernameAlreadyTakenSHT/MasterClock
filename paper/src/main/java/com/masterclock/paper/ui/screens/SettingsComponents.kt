package com.masterclock.paper.ui.screens

import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masterclock.app.logic.*
import com.masterclock.paper.ui.components.*
import com.masterclock.paper.R
import androidx.compose.ui.text.TextStyle
import com.mudita.mmd.components.buttons.ButtonDefaultsMMD
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.switcher.SwitchMMD
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

/**
 * [style] is the row's, from [sharedLabelStyle]: MMD pads a button's label by 16dp a side, which
 * left "Fischer" a third of the screen less 32dp, and cut it to "Fisch" at a large font. The label
 * gets the width, on one line, at the size the three cards share.
 */
@Composable
fun ModeCard(title: String, selected: Boolean, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.labelLarge, onClick: () -> Unit) {
    val cardPadding = PaddingValues(horizontal = 4.dp, vertical = ButtonDefaultsMMD.buttonVerticalPadding)
    // Which is chosen showed in the fill alone, and TalkBack read the three cards alike, as the phone's
    // did until they said it.
    val chosen = Modifier.semantics { this.selected = selected }
    if (selected) {
        ButtonMMD(
            onClick = onClick,
            // 48dp, the smallest target Android recommends; 44 was under it.
            modifier = modifier.heightIn(min = 48.dp).then(chosen),
            contentPadding = cardPadding,
        ) {
            Text(text = title, style = style, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButtonMMD(
            onClick = onClick,
            modifier = modifier.heightIn(min = 48.dp).then(chosen),
            border = outlinedButtonBorderMMD(),
            contentPadding = cardPadding,
        ) {
            Text(text = title, style = style, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun BehaviorSwitch(
    label: String,
    checked: Boolean,
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
            // Kept off the switch: a label as long as the row ran right up to it at a large font.
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            // Full ink, with no dimmed form for a switch out of use: there is none here, and half
            // ink is a grey.
            color = MaterialTheme.colorScheme.onBackground
        )
        SwitchMMD(
            checked = checked,
            onCheckedChange = onCheckedChange,
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

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cardStyle = sharedLabelStyle(listOf("Timer", "Fischer", "Move"), MaterialTheme.typography.labelLarge, (maxWidth - 16.dp) / 3 - 22.dp, lineWidth = (maxWidth - 16.dp) / 3 - 8.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeCard("Timer", mainMode == 0, Modifier.weight(1f), cardStyle) { onUpdateP(p.copy(mode = TimerMode.SUDDEN_DEATH)) }
            ModeCard("Fischer", mainMode == 1, Modifier.weight(1f), cardStyle) { onUpdateP(p.copy(mode = TimerMode.FISCHER)) }
            ModeCard("Move", mainMode == 2, Modifier.weight(1f), cardStyle) { onUpdateP(p.copy(mode = TimerMode.MOVE_TIMER_STANDARD)) }
        }
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
internal fun CreditRow(entry: AppInfo.CreditEntry, onMessage: (String) -> Unit) {
    val uriHandler = LocalUriHandler.current
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
                                .onFailure { onMessage(noBrowserText) }
                        }
                    ) { append(url) }
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
