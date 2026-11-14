package com.masterclock.app.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.masterclock.app.R
import com.masterclock.app.logic.CHECKBOX_DONE
import com.masterclock.app.logic.CHECKBOX_OPEN
import com.masterclock.app.logic.ListKind
import com.masterclock.app.logic.MAX_NOTE_CONTENT_CHARS
import com.masterclock.app.logic.MAX_NOTE_TITLE_CHARS
import com.masterclock.app.logic.NoteStyle
import com.masterclock.app.logic.NotebookNote
import com.masterclock.app.logic.RichText
import com.masterclock.app.logic.TextEditHistory
import com.masterclock.app.logic.TextSnapshot
import com.masterclock.app.logic.allHave
import com.masterclock.app.logic.checkboxToggled
import com.masterclock.app.logic.colored
import com.masterclock.app.logic.edited
import com.masterclock.app.logic.headingToggled
import com.masterclock.app.logic.isTickedLine
import com.masterclock.app.logic.listContinued
import com.masterclock.app.logic.listToggled
import com.masterclock.app.logic.richTextOf
import com.masterclock.app.logic.runs
import com.masterclock.app.logic.styleAt
import com.masterclock.app.logic.toggled
import com.masterclock.app.logic.withRichText

/**
 * A text note, its formatting kept beside its text; see RichText in core.
 *
 * The text field holds the text and nothing else, so the cursor, the keyboard's suggestions and its
 * corrections work on what is read. The formatting is drawn over it. A style chosen with nothing
 * selected is the style of what is typed next. Enter on a list line starts the next item; a tap on a
 * box ticks it. A note written with the old markers is shown converted, and written in the new form
 * at its first change.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RichTextNoteEditor(note: NotebookNote, onUpdate: NoteChange, onBack: () -> Unit) {
    var title by remember { mutableStateOf(note.title) }
    var rich by remember { mutableStateOf(note.richTextOf()) }
    // The field's own value, composition included: rebuilt from the text at each keystroke, a
    // keyboard's word in progress was lost, and with it its suggestions and corrections.
    var field by remember { mutableStateOf(TextFieldValue(rich.text, TextRange(rich.text.length))) }
    var typing by remember { mutableStateOf<Pair<Int, Long>?>(null) }
    var history by remember { mutableStateOf(TextEditHistory()) }
    var showColorPicker by remember { mutableStateOf(false) }

    PersistDebounced(title to rich) { (newTitle, newRich) -> onUpdate { it.copy(title = newTitle).withRichText(newRich) } }

    fun record(typingEdit: Boolean) {
        history = history.beforeEdit(TextSnapshot(rich.text, field.selection.start, field.selection.end, rich.spans), SystemClock.uptimeMillis(), typingEdit)
    }

    /** Puts [next] in the field, the cursor or selection at [start]..[end]. */
    fun show(next: RichText, start: Int, end: Int = start) {
        rich = next
        field = TextFieldValue(next.text, TextRange(start.coerceIn(0, next.text.length), end.coerceIn(0, next.text.length)))
        typing = null
    }

    val selection = field.selection
    fun isOn(bit: Int): Boolean =
        if (selection.collapsed) ((typing ?: rich.styleAt(selection.start)).first and bit) != 0
        else rich.allHave(selection.min, selection.max, bit)

    fun style(bit: Int) {
        if (selection.collapsed) {
            val current = typing ?: rich.styleAt(selection.start)
            typing = (current.first xor bit) to current.second
        } else {
            record(false)
            show(rich.toggled(selection.min, selection.max, bit), selection.start, selection.end)
        }
    }

    fun heading(bit: Int) {
        record(false)
        show(rich.headingToggled(selection.min, selection.max, bit), selection.start, selection.end)
    }

    fun list(kind: ListKind) {
        record(false)
        val edit = rich.listToggled(selection.min, selection.max, kind)
        show(edit.rich, edit.selectionStart, edit.selectionEnd)
    }

    fun color(color: Long) {
        if (selection.collapsed) {
            typing = (typing ?: rich.styleAt(selection.start)).first to color
        } else {
            record(false)
            show(rich.colored(selection.min, selection.max, color), selection.start, selection.end)
        }
    }

    val styled = remember(rich) { annotated(rich) }
    val transformation = remember(styled) {
        VisualTransformation { text -> TransformedText(if (text.text == styled.text) styled else AnnotatedString(text.text), OffsetMapping.Identity) }
    }

    ToolScaffold(title = stringResource(R.string.tools_edit_note), onBack = onBack, actions = {
        IconButton(onClick = {
            history.undo()?.let { (snapshot, rest) ->
                history = rest
                show(RichText(snapshot.text, snapshot.spans), snapshot.selectionStart, snapshot.selectionEnd)
            }
        }, enabled = history.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.tools_undo)) }
        // As the drawing's Clear: the whole text and its formatting, one step that Undo gives back.
        IconButton(onClick = {
            record(false)
            show(RichText(""), 0)
        }, enabled = rich.text.isNotEmpty()) { Icon(Icons.Default.DeleteSweep, stringResource(R.string.common_clear)) }
    }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(MAX_NOTE_TITLE_CHARS) },
                label = { Text(stringResource(R.string.tools_title)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp)) {
                // On as many lines as it takes. It scrolled sideways, with nothing to say it did: on a
                // phone the lists, the box and the colour were past the edge, and looked to be missing.
                FlowRow(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    StyleButton(Icons.Default.FormatBold, R.string.tools_bold, isOn(NoteStyle.BOLD)) { style(NoteStyle.BOLD) }
                    StyleButton(Icons.Default.FormatItalic, R.string.tools_italic, isOn(NoteStyle.ITALIC)) { style(NoteStyle.ITALIC) }
                    StyleButton(Icons.Default.FormatUnderlined, R.string.tools_underline, isOn(NoteStyle.UNDERLINE)) { style(NoteStyle.UNDERLINE) }
                    StyleButton(Icons.Default.FormatStrikethrough, R.string.tools_strikethrough, isOn(NoteStyle.STRIKE)) { style(NoteStyle.STRIKE) }
                    // Named for what they make: two icons of a T said nothing of headings, nor which was larger.
                    HeadingButton("H1", R.string.tools_large, isOn(NoteStyle.LARGE)) { heading(NoteStyle.LARGE) }
                    HeadingButton("H2", R.string.tools_normal, isOn(NoteStyle.TITLE)) { heading(NoteStyle.TITLE) }
                    IconButton(onClick = { list(ListKind.BULLET) }) { Icon(Icons.AutoMirrored.Filled.FormatListBulleted, stringResource(R.string.tools_bullet_list)) }
                    IconButton(onClick = { list(ListKind.NUMBERED) }) { Icon(Icons.Default.FormatListNumbered, stringResource(R.string.tools_numbered_list)) }
                    IconButton(onClick = { list(ListKind.CHECKBOX) }) { Icon(Icons.Default.CheckBox, stringResource(R.string.tools_checkbox)) }
                    IconButton(onClick = { showColorPicker = true }) { Icon(Icons.Default.Palette, stringResource(R.string.tools_color), tint = MaterialTheme.colorScheme.primary) }
                }
            }
            if (showColorPicker) {
                Dialog(onDismissRequest = { showColorPicker = false }) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(16.dp)) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.tools_select_text_color), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(16.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // The text's own colour again, which the markers had no way to say.
                                Surface(modifier = Modifier.size(40.dp).clickable { color(0L); showColorPicker = false }, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.FormatColorReset, stringResource(R.string.tools_color_default)) }
                                }
                                NOTE_COLORS.forEach { value ->
                                    Surface(modifier = Modifier.size(40.dp).clickable { color(value); showColorPicker = false }, shape = CircleShape, color = Color(value), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {}
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            TextButton(onClick = { showColorPicker = false }) { Text(stringResource(R.string.common_cancel)) }
                        }
                    }
                }
            }
            OutlinedTextField(
                value = field,
                onValueChange = { value ->
                    if (value.text.length > MAX_NOTE_CONTENT_CHARS) return@OutlinedTextField
                    if (value.text != rich.text) {
                        record(true)
                        var next = rich.edited(value.text, typing, cursor = value.selection.end)
                        var shown = value
                        // Enter on a list line: the next item, or the end of the list.
                        if (value.text.length == rich.text.length + 1 && value.selection.collapsed && value.text.getOrNull(value.selection.start - 1) == '\n') {
                            next.listContinued(value.selection.start)?.let { edit ->
                                next = edit.rich
                                shown = TextFieldValue(edit.rich.text, TextRange(edit.selectionStart))
                            }
                        }
                        rich = next
                        field = shown
                        typing = null
                    } else {
                        // A tap on a box ticks it: the cursor lands on one side of the box or the other.
                        val at = value.selection
                        val lineStart = if (at.start == 0) 0 else value.text.lastIndexOf('\n', at.start - 1) + 1
                        val onBox = at.collapsed && at != field.selection && at.start - lineStart <= 1 &&
                            value.text.getOrNull(lineStart).let { it == CHECKBOX_OPEN || it == CHECKBOX_DONE }
                        val ticked = if (onBox) rich.checkboxToggled(at.start) else null
                        if (ticked != null) {
                            record(false)
                            show(ticked, lineStart + 2)
                        } else {
                            if (at != field.selection) typing = null
                            field = value
                        }
                    }
                },
                visualTransformation = transformation,
                modifier = Modifier.fillMaxWidth().weight(1f),
                placeholder = { Text(stringResource(R.string.tools_format_hint)) },
                shape = RoundedCornerShape(12.dp),
            )
        }
    }
}

/** A style the selection has or has not, which TalkBack hears as a switch. */
@Composable
private fun StyleButton(icon: ImageVector, label: Int, on: Boolean, onClick: () -> Unit) {
    IconToggleButton(
        checked = on,
        onCheckedChange = { onClick() },
        colors = IconButtonDefaults.iconToggleButtonColors(checkedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
    ) { Icon(icon, stringResource(label)) }
}

/** A heading size, which a letter and a number say better than an icon. */
@Composable
private fun HeadingButton(mark: String, label: Int, on: Boolean, onClick: () -> Unit) {
    val name = stringResource(label)
    IconToggleButton(
        checked = on,
        onCheckedChange = { onClick() },
        modifier = Modifier.semantics { contentDescription = name },
        colors = IconButtonDefaults.iconToggleButtonColors(checkedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
    ) { Text(mark, fontWeight = FontWeight.Black, modifier = Modifier.clearAndSetSemantics {}) }
}

private val NOTE_COLORS = listOf(0xFF4CAF50, 0xFF2196F3, 0xFFF44336, 0xFFFFEB3B, 0xFFFF9800, 0xFF000000, 0xFF9E9E9E, 0xFFFFFFFF)

/** The note as it is drawn: each run in its style, and a ticked box's line struck through. */
private fun annotated(rich: RichText): AnnotatedString = buildAnnotatedString {
    append(rich.text)
    for (run in rich.runs()) {
        val s = run.styles
        val decorations = listOfNotNull(
            TextDecoration.Underline.takeIf { s and NoteStyle.UNDERLINE != 0 },
            TextDecoration.LineThrough.takeIf { s and NoteStyle.STRIKE != 0 },
        )
        addStyle(
            SpanStyle(
                fontWeight = when {
                    s and NoteStyle.LARGE != 0 -> FontWeight.Black
                    s and (NoteStyle.BOLD or NoteStyle.TITLE) != 0 -> FontWeight.Bold
                    else -> null
                },
                fontStyle = if (s and NoteStyle.ITALIC != 0) FontStyle.Italic else null,
                fontSize = when {
                    s and NoteStyle.LARGE != 0 -> 24.sp
                    s and NoteStyle.TITLE != 0 -> 20.sp
                    else -> androidx.compose.ui.unit.TextUnit.Unspecified
                },
                textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
                color = if (run.color != 0L) Color(run.color) else Color.Unspecified,
            ),
            run.start, run.end,
        )
    }
    var lineStart = 0
    while (lineStart <= rich.text.length) {
        val lineEnd = rich.text.indexOf('\n', lineStart).let { if (it < 0) rich.text.length else it }
        if (rich.isTickedLine(lineStart) && lineStart + 2 < lineEnd) {
            addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = Color.Gray), lineStart + 2, lineEnd)
        }
        lineStart = lineEnd + 1
    }
}
