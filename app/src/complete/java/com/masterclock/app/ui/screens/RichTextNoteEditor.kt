package com.masterclock.app.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.FormatAlignCenter
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
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
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
import com.masterclock.app.logic.centerToggled
import com.masterclock.app.logic.highlighted
import com.masterclock.app.logic.highlighterOf
import com.masterclock.app.logic.withHighlighter
import com.masterclock.app.logic.runs
import com.masterclock.app.logic.edited
import com.masterclock.app.logic.isCenteredLine
import com.masterclock.app.logic.emojiRanges
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
    var showHighlighters by remember { mutableStateOf(false) }
    var showCustomColor by remember { mutableStateOf(false) }
    // The insert panel open, if any: one at a time, the same button closing it.
    var inserting by rememberSaveable { mutableStateOf<InsertKind?>(null) }
    fun toggleInsert(kind: InsertKind) { inserting = if (inserting == kind) null else kind }

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

    // Each action reads the cursor as it stands when it runs, never as it stood when the screen was
    // last drawn: two symbols put in one after the other went in the wrong order, the second at the
    // place the first had taken.
    fun isOn(bit: Int): Boolean {
        val selection = field.selection
        return if (selection.collapsed) ((typing ?: rich.styleAt(selection.start)).first and bit) != 0
        else rich.allHave(selection.min, selection.max, bit)
    }

    fun style(bit: Int) {
        val selection = field.selection
        if (selection.collapsed) {
            val current = typing ?: rich.styleAt(selection.start)
            typing = (current.first xor bit) to current.second
        } else {
            record(false)
            show(rich.toggled(selection.min, selection.max, bit), selection.start, selection.end)
        }
    }

    fun heading(bit: Int) {
        val selection = field.selection
        record(false)
        show(rich.headingToggled(selection.min, selection.max, bit), selection.start, selection.end)
    }

    fun center() {
        val selection = field.selection
        record(false)
        show(rich.centerToggled(selection.min, selection.max), selection.start, selection.end)
    }

    /** Whether the line the cursor is on is centred. */
    fun lineCentered(): Boolean {
        val at = field.selection.min.coerceIn(0, rich.text.length)
        return rich.isCenteredLine(rich.text.lastIndexOf('\n', at - 1) + 1)
    }

    fun snapshot() = TextSnapshot(rich.text, field.selection.start, field.selection.end, rich.spans)

    fun list(kind: ListKind) {
        val selection = field.selection
        record(false)
        val edit = rich.listToggled(selection.min, selection.max, kind)
        show(edit.rich, edit.selectionStart, edit.selectionEnd)
    }

    fun color(color: Long) {
        val selection = field.selection
        if (selection.collapsed) {
            typing = (typing ?: rich.styleAt(selection.start)).first to color
        } else {
            record(false)
            show(rich.colored(selection.min, selection.max, color), selection.start, selection.end)
        }
    }

    /** The selection highlighted with [highlighter], or no longer for null; with none, what is typed next. */
    fun highlight(highlighter: Int?) {
        val selection = field.selection
        if (selection.collapsed) {
            val current = typing ?: rich.styleAt(selection.start)
            typing = withHighlighter(current.first, highlighter) to current.second
        } else {
            record(false)
            show(rich.highlighted(selection.min, selection.max, highlighter), selection.start, selection.end)
        }
    }

    /** The highlighter where the cursor is, or under the whole selection; null where there is none. */
    fun highlighterHere(): Int? {
        val selection = field.selection
        val styles = if (selection.collapsed) (typing ?: rich.styleAt(selection.start)).first
        else if (rich.allHave(selection.min, selection.max, NoteStyle.HIGHLIGHT)) rich.runs().first { selection.min in it.start until it.end }.styles
        else return null
        return if (styles and NoteStyle.HIGHLIGHT != 0) highlighterOf(styles) else null
    }

    /** [text] where the cursor is, over the selection if there is one, in the style typed there. */
    fun insert(text: String) {
        val selection = field.selection
        record(false)
        val newText = rich.text.replaceRange(selection.min, selection.max, text)
        if (newText.length > MAX_NOTE_CONTENT_CHARS) return
        val cursor = selection.min + text.length
        val next = rich.edited(newText, typing, cursor = cursor)
        rich = next
        // The field's value kept, its cursor moved past what went in: rebuilt, the keyboard lost
        // the word it was composing.
        field = field.copy(text = newText, selection = TextRange(cursor))
    }

    val styled = remember(rich) { annotated(rich) }
    // Drawn in its styles when the field holds the note's text; the drawing may differ from it in
    // its new lines (see annotated), never in its length, so the offsets map one to one.
    val shownFor = rich.text
    val transformation = remember(styled, shownFor) {
        VisualTransformation { text -> TransformedText(if (text.text == shownFor) styled else AnnotatedString(text.text), OffsetMapping.Identity) }
    }

    ToolScaffold(title = stringResource(R.string.tools_edit_note), onBack = onBack, actions = {
        IconButton(onClick = {
            history.undo(snapshot())?.let { (snapshot, rest) ->
                history = rest
                show(RichText(snapshot.text, snapshot.spans), snapshot.selectionStart, snapshot.selectionEnd)
            }
        }, enabled = history.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.tools_undo)) }
        IconButton(onClick = {
            history.redo(snapshot())?.let { (snapshot, rest) ->
                history = rest
                show(RichText(snapshot.text, snapshot.spans), snapshot.selectionStart, snapshot.selectionEnd)
            }
        }, enabled = history.canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.tools_redo)) }
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
            // As wide as the title and the note above and below it: it stopped where its buttons did.
            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                // On as many lines as it takes. It scrolled sideways, with nothing to say it did: on a
                // phone the lists, the box and the colour were past the edge, and looked to be missing.
                // Each line centred, the space left over shared between its two ends.
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    StyleButton(Icons.Default.FormatBold, R.string.tools_bold, isOn(NoteStyle.BOLD)) { style(NoteStyle.BOLD) }
                    StyleButton(Icons.Default.FormatItalic, R.string.tools_italic, isOn(NoteStyle.ITALIC)) { style(NoteStyle.ITALIC) }
                    StyleButton(Icons.Default.FormatUnderlined, R.string.tools_underline, isOn(NoteStyle.UNDERLINE)) { style(NoteStyle.UNDERLINE) }
                    StyleButton(Icons.Default.FormatStrikethrough, R.string.tools_strikethrough, isOn(NoteStyle.STRIKE)) { style(NoteStyle.STRIKE) }
                    // Five highlighters behind it; marked, and in the colour, where the text is highlighted.
                    val here = highlighterHere()
                    IconToggleButton(
                        checked = here != null,
                        onCheckedChange = { showHighlighters = true },
                        colors = IconButtonDefaults.iconToggleButtonColors(checkedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    ) { Icon(Icons.Default.BorderColor, stringResource(R.string.tools_highlight), tint = here?.let { HIGHLIGHTERS[it].copy(alpha = 1f) } ?: LocalContentColor.current) }
                    // Named for what it makes: an icon of a T said nothing of a heading. One heading only,
                    // the user's choice; a small heading (TITLE) from an older note is still drawn.
                    MarkButton("H1", R.string.tools_large, isOn(NoteStyle.LARGE)) { heading(NoteStyle.LARGE) }
                    IconButton(onClick = { list(ListKind.CHECKBOX) }) { Icon(Icons.Default.CheckBox, stringResource(R.string.tools_checkbox)) }
                    StyleButton(Icons.Default.FormatAlignCenter, R.string.tools_center, lineCentered()) { center() }
                    IconButton(onClick = { list(ListKind.BULLET) }) { Icon(Icons.AutoMirrored.Filled.FormatListBulleted, stringResource(R.string.tools_bullet_list)) }
                    IconButton(onClick = { list(ListKind.NUMBERED) }) { Icon(Icons.Default.FormatListNumbered, stringResource(R.string.tools_numbered_list)) }
                    IconButton(onClick = { showColorPicker = true }) { Icon(Icons.Default.Palette, stringResource(R.string.tools_color), tint = MaterialTheme.colorScheme.primary) }
                    MarkButton("♞", R.string.tools_insert_pieces, inserting == InsertKind.PIECES, size = 26.sp) { toggleInsert(InsertKind.PIECES) }
                    MarkButton("!?", R.string.tools_insert_annotations, inserting == InsertKind.ANNOTATIONS) { toggleInsert(InsertKind.ANNOTATIONS) }
                    MarkButton("🙂", R.string.tools_insert_emojis, inserting == InsertKind.EMOJIS, font = OpenMoji, size = 24.sp) { toggleInsert(InsertKind.EMOJIS) }
                }
            }
            inserting?.let { InsertPanel(it, onInsert = ::insert) }
            if (showHighlighters) {
                Dialog(onDismissRequest = { showHighlighters = false }) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(16.dp)) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.tools_highlight), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val noneName = stringResource(R.string.tools_highlight_none)
                                Surface(modifier = Modifier.size(36.dp).clickable { highlight(null); showHighlighters = false }.semantics { contentDescription = noneName }, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.FormatColorReset, null) }
                                }
                                HIGHLIGHTERS.forEachIndexed { i, colour ->
                                    val name = stringResource(HIGHLIGHTER_NAMES[i])
                                    Surface(modifier = Modifier.size(36.dp).clickable { highlight(i); showHighlighters = false }.semantics { contentDescription = name }, shape = CircleShape, color = colour, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {}
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            TextButton(onClick = { showHighlighters = false }) { Text(stringResource(R.string.common_cancel)) }
                        }
                    }
                }
            }
            if (showCustomColor) ColorPickerDialog(initialColor = Color.Black, onDismiss = { showCustomColor = false }) { chosen ->
                showCustomColor = false
                color(chosen.toArgb().toLong() and 0xFFFFFFFFL)
            }
            if (showColorPicker) {
                Dialog(onDismissRequest = { showColorPicker = false }) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(16.dp)) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.tools_select_text_color), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(16.dp))
                            // Six on a line on any phone, as the highlighters are; custom ones used go first.
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                // The text's own colour again, which the markers had no way to say.
                                Surface(modifier = Modifier.size(36.dp).clickable { color(0L); showColorPicker = false }, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.FormatColorReset, stringResource(R.string.tools_color_default)) }
                                }
                                (customColoursIn(rich.runs().map { it.color }, NOTE_COLORS) + NOTE_COLORS).forEach { value ->
                                    Surface(modifier = Modifier.size(36.dp).clickable { color(value); showColorPicker = false }.testTag("text_color_$value"), shape = CircleShape, color = Color(value), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {}
                                }
                                CustomColourButton(Modifier.testTag("text_color_custom"), size = 36.dp) { showColorPicker = false; showCustomColor = true }
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

/**
 * A button shown by what it makes or puts in -- "H1", a knight, "!?" -- which says it better than an
 * icon would; TalkBack reads its name instead.
 */
@Composable
private fun MarkButton(mark: String, label: Int, on: Boolean, font: FontFamily? = null, size: TextUnit = 18.sp, onClick: () -> Unit) {
    val name = stringResource(label)
    IconToggleButton(
        checked = on,
        onCheckedChange = { onClick() },
        modifier = Modifier.semantics { contentDescription = name },
        colors = IconButtonDefaults.iconToggleButtonColors(checkedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
    ) { Text(mark, fontWeight = FontWeight.Black, fontSize = size, fontFamily = font, maxLines = 1, modifier = Modifier.clearAndSetSemantics {}) }
}

/**
 * OpenMoji, by the HfG Schwäbisch Gmünd, CC BY-SA 4.0: the font a note's emojis are drawn in, built
 * with its catalog by tools/openmoji_font.py, which says what is done to it and why: cut down to
 * Unicode's emojis, and taught the emoji variations, without which Android drew some of them in the
 * phone's font, and split a rainbow flag in two.
 */
internal val OpenMoji = FontFamily(Font(R.font.openmoji))

/**
 * The highlighters, by number: see NoteStyle.HIGHLIGHT_SHIFT. See-through, so that the text reads
 * through them in a light theme and a dark one. Yellow first, the one there always was.
 */
private val HIGHLIGHTERS = listOf(Color(0x80FFEB3B), Color(0x80FFB74D), Color(0x80F48FB1), Color(0x8081D4FA), Color(0x80A5D6A7))
private val HIGHLIGHTER_NAMES = listOf(R.string.tools_highlight_yellow, R.string.tools_highlight_orange, R.string.tools_highlight_pink, R.string.tools_highlight_blue, R.string.tools_highlight_green)

/**
 * A text note's colours: black, blue, red and green, which read on the page, and any other through
 * the custom one. Yellow, orange, grey and white were hard to read on it; a note that has them keeps
 * them, and they are offered again among the custom ones it has used.
 */
private val NOTE_COLORS = listOf(0xFF000000, 0xFF2196F3, 0xFFF44336, 0xFF4CAF50)

/**
 * The note as it is drawn: each run in its style, a ticked box's line struck through, and a centred
 * line centred.
 *
 * Centring is a paragraph's, and Compose breaks the line between two paragraphs itself: the note's
 * own new line then made a second break, an empty line under every centred one. So in a note with a
 * centred line, each line is its own paragraph and is drawn with its new line turned into a
 * zero-width space, which takes the same place, keeps every offset where it was, and breaks nothing.
 * The last character, if it is a new line, stays one: it is what puts the cursor on the empty line
 * after it. Only the drawing changes; the text, the cursor and what is copied are the note's own.
 */
private fun annotated(rich: RichText): AnnotatedString = buildAnnotatedString {
    val text = rich.text
    val lineStarts = generateSequence(0) { at -> text.indexOf('\n', at).takeIf { it >= 0 }?.plus(1) }.toList()
    val centring = lineStarts.any { rich.isCenteredLine(it) }
    append(if (!centring) text else String(CharArray(text.length) { i -> if (text[i] == '\n' && i < text.length - 1) '​' else text[i] }))
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
                // See-through, so that the text reads through it in a light theme and a dark one.
                background = if (s and NoteStyle.HIGHLIGHT != 0) HIGHLIGHTERS[highlighterOf(s)] else Color.Unspecified,
            ),
            run.start, run.end,
        )
    }
    // Emojis in OpenMoji, the same on every phone, newer ones included.
    for (range in emojiRanges(rich.text)) addStyle(SpanStyle(fontFamily = OpenMoji), range.first, range.last + 1)
    for (lineStart in lineStarts) {
        val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
        if (rich.isTickedLine(lineStart) && lineStart + 2 < lineEnd) {
            addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = Color.Gray), lineStart + 2, lineEnd)
        }
        // The paragraph takes the line's new line with it, so that the paragraphs cover the text.
        val paragraphEnd = minOf(lineEnd + 1, text.length)
        if (centring && paragraphEnd > lineStart) {
            addStyle(ParagraphStyle(textAlign = if (rich.isCenteredLine(lineStart)) TextAlign.Center else TextAlign.Start), lineStart, paragraphEnd)
        }
    }
}
