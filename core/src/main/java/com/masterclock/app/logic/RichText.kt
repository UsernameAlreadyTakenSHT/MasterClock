package com.masterclock.app.logic

import kotlinx.serialization.Serializable

/**
 * A text note's formatting, kept beside its text: the text holds nothing but what is read.
 *
 * Formatting used to be written into the text as markers -- `**bold**`, `<u>underline</u>`,
 * `<color:FF2196F3>…</color>`, `# ` for a heading -- drawn invisible. The cursor stepped over
 * characters nobody could see, a deletion next to a bold word broke its marker and showed it, and
 * a checkbox was "[ ]" that nothing drew as a box. Now the text is plain and these runs say how each
 * stretch of it looks. A note from before is converted once, on the way in; see [richTextOf].
 *
 * Lists and checkboxes are characters of the text, as on paper: "• ", "1. ", "☐ " and "☑ ".
 */
@Serializable
data class NoteSpan(val start: Int, val end: Int, val styles: Int, val color: Long = 0)

/** The styles a [NoteSpan] carries, as bits. */
object NoteStyle {
    const val BOLD = 1
    const val ITALIC = 2
    const val UNDERLINE = 4
    const val STRIKE = 8
    /** A line in the larger of the two heading sizes. */
    const val LARGE = 16
    /** A line in the smaller heading size. */
    const val TITLE = 32
    const val HEADINGS = LARGE or TITLE
    /** Text marked as with a highlighter: behind it, not in it, so it goes with any text colour. */
    const val HIGHLIGHT = 64
    /**
     * Which of [HIGHLIGHTER_COUNT] highlighters, as a number in the three bits from
     * [HIGHLIGHT_SHIFT]: in the styles rather than a field of its own, so that a span needs nothing
     * new. 0 is the yellow there always was, which a highlight written before this, with no number,
     * reads as -- and so does an older version reading any highlight.
     */
    const val HIGHLIGHT_SHIFT = 8
    const val HIGHLIGHT_COLOR = 7 shl HIGHLIGHT_SHIFT
    const val HIGHLIGHTER_COUNT = 5
    /** A line centred. Like a heading it is the whole line's, and the line after it starts plain. */
    const val CENTER = 128
    /** What belongs to a line rather than to its characters, and so stops at its end. */
    const val LINE_STYLES = HEADINGS or CENTER
}

/** A text and its formatting, as the editor works on them. */
data class RichText(val text: String, val spans: List<NoteSpan> = emptyList())

const val CHECKBOX_OPEN = '☐'
const val CHECKBOX_DONE = '☑'
const val BULLET = '•'

/** More runs than any note written by hand; a note is cut to it on import. */
const val MAX_NOTE_SPANS = 20_000

/** The note's text and formatting, converting a note written with the old markers. */
fun NotebookNote.richTextOf(): RichText =
    if (richFormat) RichText(content, cleanSpans(richSpans, content.length)) else richTextFromMarkup(content)

/** [note] holding [rich]: its text, its formatting, and the format it is now in. */
fun NotebookNote.withRichText(rich: RichText): NotebookNote =
    copy(content = rich.text, richSpans = rich.spans, richFormat = true)

/** [spans] held inside a text of [length], each with something to style, at most [MAX_NOTE_SPANS]. */
fun cleanSpans(spans: List<NoteSpan>, length: Int): List<NoteSpan> =
    spans.asSequence()
        .map { it.copy(start = it.start.coerceIn(0, length), end = it.end.coerceIn(0, length)) }
        .filter { it.start < it.end && (it.styles != 0 || it.color != 0L) }
        .take(MAX_NOTE_SPANS)
        .toList()

/** One style per character, and one colour; 0 for none. */
private class CharStyles(val styles: IntArray, val colors: LongArray)

private fun charStylesOf(rich: RichText): CharStyles {
    val n = rich.text.length
    val styles = IntArray(n)
    val colors = LongArray(n)
    for (span in cleanSpans(rich.spans, n)) {
        for (i in span.start until span.end) {
            styles[i] = styles[i] or span.styles
            if (span.color != 0L) colors[i] = span.color
        }
    }
    return CharStyles(styles, colors)
}

/** The runs of equal style in [styles], as spans. */
private fun spansOf(styles: IntArray, colors: LongArray): List<NoteSpan> {
    val runs = mutableListOf<NoteSpan>()
    var i = 0
    while (i < styles.size) {
        val s = styles[i]
        val c = colors[i]
        var j = i + 1
        while (j < styles.size && styles[j] == s && colors[j] == c) j++
        if (s != 0 || c != 0L) runs += NoteSpan(i, j, s, c)
        i = j
    }
    return runs
}

/**
 * The note as the old markers drew it: each marker the old renderer hid is taken out, and what it
 * styled keeps the style. The same patterns, so a note reads exactly as it did. A "[ ] " or "[x] "
 * starting a line, which nothing drew as a box, becomes the box.
 */
fun richTextFromMarkup(markup: String): RichText {
    val n = markup.length
    val styles = IntArray(n)
    val colors = LongArray(n)
    val hidden = BooleanArray(n)
    fun style(range: IntRange, bit: Int) { for (i in range) styles[i] = styles[i] or bit }
    fun hide(from: Int, until: Int) { for (i in from until until.coerceAtMost(n)) hidden[i] = true }
    fun markers(regex: Regex, open: Int, close: Int, bit: Int, group: Int = 1) {
        regex.findAll(markup).forEach { match ->
            hide(match.range.first, match.range.first + open)
            if (close > 0) hide(match.range.last + 1 - close, match.range.last + 1)
            match.groups[group]?.range?.let { style(it, bit) }
        }
    }
    markers(Regex("""\*\*(.*?)\*\*"""), 2, 2, NoteStyle.BOLD)
    markers(Regex("""\*(.*?)\*"""), 1, 1, NoteStyle.ITALIC)
    markers(Regex("<u>(.*?)</u>"), 3, 4, NoteStyle.UNDERLINE)
    markers(Regex("^# (.*)$", RegexOption.MULTILINE), 2, 0, NoteStyle.LARGE)
    markers(Regex("^## (.*)$", RegexOption.MULTILINE), 3, 0, NoteStyle.TITLE)
    Regex("<color:([0-9A-F]{8})>(.*?)</color>").findAll(markup).forEach { match ->
        hide(match.range.first, match.range.first + 16)
        hide(match.range.last + 1 - 8, match.range.last + 1)
        val color = match.groupValues[1].toLongOrNull(16) ?: 0L
        match.groups[2]?.range?.let { range -> for (i in range) colors[i] = color }
    }

    val text = StringBuilder(n)
    val outStyles = ArrayList<Int>(n)
    val outColors = ArrayList<Long>(n)
    var i = 0
    while (i < n) {
        if (hidden[i]) { i++; continue }
        val lineStart = text.isEmpty() || text.last() == '\n'
        val box = if (lineStart && i + 2 < n && markup[i] == '[' && markup[i + 2] == ']' && markup.getOrNull(i + 3) == ' ') {
            when (markup[i + 1]) { ' ' -> CHECKBOX_OPEN; 'x', 'X' -> CHECKBOX_DONE; else -> null }
        } else null
        if (box != null) {
            text.append(box); outStyles += 0; outColors += 0L
            i += 3
            continue
        }
        text.append(markup[i]); outStyles += styles[i]; outColors += colors[i]
        i++
    }
    return RichText(text.toString(), spansOf(outStyles.toIntArray(), outColors.toLongArray()))
}

/**
 * [old] with its text become [newText], as an edit of the text field leaves it: the formatting of
 * what was kept is kept, and what was typed takes [typing] -- the style chosen with no text
 * selected -- or else the style of the text it was typed into. A new line does not carry a heading
 * on with it.
 *
 * [cursor] is where the cursor stands after the edit, which is the end of what was typed and the
 * place of what was deleted. The texts alone do not say it: "one two three" made "one three" reads
 * as "wo t" taken out as well as "two ", and the "t" of "three" then lost its bold.
 */
fun RichText.edited(newText: String, typing: Pair<Int, Long>? = null, cursor: Int? = null): RichText {
    val oldText = text
    if (newText == oldText) return this
    var prefix = 0
    val maxPrefix = minOf(oldText.length, newText.length, cursor ?: Int.MAX_VALUE)
    while (prefix < maxPrefix && oldText[prefix] == newText[prefix]) prefix++
    var suffix = 0
    val maxSuffix = minOf(oldText.length, newText.length) - prefix
    while (suffix < maxSuffix && oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]) suffix++
    val oldEnd = oldText.length - suffix
    val insertedLength = newText.length - prefix - suffix

    val old = charStylesOf(this)
    val (style, color) = typing ?: when {
        prefix > 0 && oldText[prefix - 1] != '\n' -> old.styles[prefix - 1] to old.colors[prefix - 1]
        oldEnd < oldText.length && oldText[oldEnd] != '\n' -> old.styles[oldEnd] to old.colors[oldEnd]
        else -> 0 to 0L
    }
    val styles = IntArray(newText.length)
    val colors = LongArray(newText.length)
    old.styles.copyInto(styles, 0, 0, prefix)
    old.colors.copyInto(colors, 0, 0, prefix)
    var afterNewLine = false
    for (k in 0 until insertedLength) {
        val at = prefix + k
        styles[at] = if (afterNewLine) style and NoteStyle.LINE_STYLES.inv() else style
        colors[at] = color
        if (newText[at] == '\n') afterNewLine = true
    }
    old.styles.copyInto(styles, prefix + insertedLength, oldEnd, oldText.length)
    old.colors.copyInto(colors, prefix + insertedLength, oldEnd, oldText.length)
    return RichText(newText, spansOf(styles, colors))
}

/** Whether every character from [start] to [end] carries [bit]. */
fun RichText.allHave(start: Int, end: Int, bit: Int): Boolean {
    if (start >= end) return false
    val styles = charStylesOf(this).styles
    return (start until end.coerceAtMost(styles.size)).all { styles[it] and bit != 0 }
}

/** The style a cursor at [at] types with when nothing is chosen: that of the character before it. */
fun RichText.styleAt(at: Int): Pair<Int, Long> {
    if (at <= 0 || at > text.length || text[at - 1] == '\n') return 0 to 0L
    val c = charStylesOf(this)
    return c.styles[at - 1] to c.colors[at - 1]
}

/** [bit] set from [start] to [end], or taken off if all of it had it already. */
fun RichText.toggled(start: Int, end: Int, bit: Int): RichText {
    if (start >= end) return this
    val c = charStylesOf(this)
    val on = !allHave(start, end, bit)
    for (i in start until end.coerceAtMost(c.styles.size)) c.styles[i] = if (on) c.styles[i] or bit else c.styles[i] and bit.inv()
    return RichText(text, spansOf(c.styles, c.colors))
}

/** Which highlighter [styles] are in; 0 for a number no highlighter has, as from an import. */
fun highlighterOf(styles: Int): Int =
    ((styles and NoteStyle.HIGHLIGHT_COLOR) shr NoteStyle.HIGHLIGHT_SHIFT).takeIf { it < NoteStyle.HIGHLIGHTER_COUNT } ?: 0

/** [styles] highlighted with [highlighter], or no longer highlighted for null. */
fun withHighlighter(styles: Int, highlighter: Int?): Int {
    val plain = styles and (NoteStyle.HIGHLIGHT or NoteStyle.HIGHLIGHT_COLOR).inv()
    return if (highlighter == null) plain
    else plain or NoteStyle.HIGHLIGHT or (highlighter.coerceIn(0, NoteStyle.HIGHLIGHTER_COUNT - 1) shl NoteStyle.HIGHLIGHT_SHIFT)
}

/** From [start] to [end] highlighted with [highlighter], or no longer highlighted for null. */
fun RichText.highlighted(start: Int, end: Int, highlighter: Int?): RichText {
    if (start >= end) return this
    val c = charStylesOf(this)
    for (i in start until end.coerceAtMost(c.styles.size)) c.styles[i] = withHighlighter(c.styles[i], highlighter)
    return RichText(text, spansOf(c.styles, c.colors))
}

/** [color] from [start] to [end]; 0 takes the colour away. */
fun RichText.colored(start: Int, end: Int, color: Long): RichText {
    if (start >= end) return this
    val c = charStylesOf(this)
    for (i in start until end.coerceAtMost(c.colors.size)) c.colors[i] = color
    return RichText(text, spansOf(c.styles, c.colors))
}

/** Where the lines touched by [start]..[end] begin and end. */
private fun RichText.linesAround(start: Int, end: Int): IntRange {
    val from = text.lastIndexOf('\n', (start - 1).coerceAtLeast(-1)).let { if (start == 0) 0 else it + 1 }
    val to = text.indexOf('\n', end).let { if (it < 0) text.length else it }
    return from until to
}

/** The lines touched by the selection made a heading of [bit]'s size, or plain again if they were. */
fun RichText.headingToggled(start: Int, end: Int, bit: Int): RichText {
    val lines = linesAround(start, end)
    if (lines.isEmpty()) return this
    val c = charStylesOf(this)
    // Line by line, as centring is: this never sets the heading on the breaks between the lines,
    // so the range never all had it, and a heading set on two lines at once could not be taken
    // off them. (A break typed at the end of a heading does carry it, from the character before:
    // it is taken off below.)
    val on = !lines.all { text[it] == '\n' || c.styles[it] and bit != 0 }
    for (i in lines) {
        // A break is given no heading and keeps none: one typed at the end of a heading had its
        // size, and went on having it once the heading was taken off the line.
        c.styles[i] = if (text[i] == '\n') c.styles[i] and NoteStyle.HEADINGS.inv()
        else (c.styles[i] and NoteStyle.HEADINGS.inv()) or (if (on) bit else 0)
    }
    // And the break that ends the last of the lines, which the range stops short of.
    val closing = lines.last + 1
    if (closing < text.length) c.styles[closing] = c.styles[closing] and NoteStyle.HEADINGS.inv()
    return RichText(text, spansOf(c.styles, c.colors))
}

/** Whether every character of the lines touched by [start]..[end] has [bit], their line breaks aside. */
fun RichText.linesAllHave(start: Int, end: Int, bit: Int): Boolean {
    val lines = linesAround(start, end)
    if (lines.isEmpty()) return false
    val styles = charStylesOf(this).styles
    return lines.any { text[it] != '\n' } && lines.all { text[it] == '\n' || styles[it] and bit != 0 }
}

/** The lines touched by the selection centred, or put back to the left if they all were. */
fun RichText.centerToggled(start: Int, end: Int): RichText {
    val lines = linesAround(start, end)
    if (lines.isEmpty()) return this
    val c = charStylesOf(this)
    // Line by line: the new lines between them never carry the style, so all of the range never has
    // it; and an empty line, which has nothing to carry it, does not count.
    // By the rule of isCenteredLine, read from the table built above: asked of isCenteredLine, each
    // line built that table again, and a long selection in a long note stopped the app for seconds.
    val on = !lineStarts(start, end).filter { it < text.length && text[it] != '\n' }.all { lineStart ->
        val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
        (lineStart until lineEnd.coerceAtMost(c.styles.size)).any { c.styles[it] and NoteStyle.CENTER != 0 }
    }
    for (i in lines) {
        if (text[i] == '\n') continue
        c.styles[i] = if (on) c.styles[i] or NoteStyle.CENTER else c.styles[i] and NoteStyle.CENTER.inv()
    }
    return RichText(text, spansOf(c.styles, c.colors))
}

/**
 * Whether the line starting at [lineStart] is centred: any of its characters is. Not every one, so
 * that a bullet or a box put in front of a centred line, which comes in plain, leaves it centred.
 */
fun RichText.isCenteredLine(lineStart: Int): Boolean {
    val end = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
    if (lineStart >= end) return false
    val styles = charStylesOf(this).styles
    return (lineStart until end.coerceAtMost(styles.size)).any { styles[it] and NoteStyle.CENTER != 0 }
}

/**
 * The start of every centred line, by the rule of [isCenteredLine], in one pass over the text. The
 * editor asked [isCenteredLine] of each line at every keystroke, and each answer builds the table of
 * every character's style again: lines times length, which on a long note was a freeze a keystroke.
 */
fun RichText.centeredLineStarts(): Set<Int> {
    if (spans.none { it.styles and NoteStyle.CENTER != 0 }) return emptySet()
    val styles = charStylesOf(this).styles
    val starts = mutableSetOf<Int>()
    var lineStart = 0
    for (i in 0 until minOf(text.length, styles.size)) {
        if (text[i] == '\n') lineStart = i + 1 else if (styles[i] and NoteStyle.CENTER != 0) starts += lineStart
    }
    return starts
}

/** The start of each line touched by [start]..[end]. */
private fun RichText.lineStarts(start: Int, end: Int): List<Int> {
    val lines = linesAround(start, end)
    val starts = mutableListOf(lines.first)
    for (i in lines) if (text[i] == '\n' && i + 1 <= lines.last + 1) starts += i + 1
    return starts
}

/** What a line starts with as a list: "• ", "1. ", "☐ " or "☑ ", or null. */
fun listPrefixOf(line: String): String? = when {
    line.startsWith("$BULLET ") -> "$BULLET "
    line.startsWith("$CHECKBOX_OPEN ") -> "$CHECKBOX_OPEN "
    line.startsWith("$CHECKBOX_DONE ") -> "$CHECKBOX_DONE "
    else -> Regex("""^(\d{1,4})\. """).find(line)?.value
}

/** The kinds of list a line can be. */
enum class ListKind { BULLET, NUMBERED, CHECKBOX }

private fun ListKind.matches(prefix: String?) = when (this) {
    ListKind.BULLET -> prefix == "$BULLET "
    ListKind.NUMBERED -> prefix != null && prefix.first().isDigit()
    ListKind.CHECKBOX -> prefix == "$CHECKBOX_OPEN " || prefix == "$CHECKBOX_DONE "
}

/** An edit and where the cursor goes after it. */
data class RichEdit(val rich: RichText, val selectionStart: Int, val selectionEnd: Int = selectionStart)

/** [rich] with [length] characters at [at] replaced by [insert], unstyled, and a position moved with it. */
private fun RichText.replaced(at: Int, length: Int, insert: String): RichText {
    val c = charStylesOf(this)
    val newText = text.substring(0, at) + insert + text.substring(at + length)
    val styles = IntArray(newText.length)
    val colors = LongArray(newText.length)
    c.styles.copyInto(styles, 0, 0, at); c.colors.copyInto(colors, 0, 0, at)
    c.styles.copyInto(styles, at + insert.length, at + length, text.length)
    c.colors.copyInto(colors, at + insert.length, at + length, text.length)
    return RichText(newText, spansOf(styles, colors))
}

/**
 * The lines touched by the selection made a list of [kind], or plain lines again if all of them
 * already were. A numbered list counts from 1, or on from the line above it.
 */
fun RichText.listToggled(selectionStart: Int, selectionEnd: Int, kind: ListKind): RichEdit {
    val starts = lineStarts(selectionStart, selectionEnd)
    fun lineAt(s: Int) = text.substring(s, text.indexOf('\n', s).let { if (it < 0) text.length else it })
    val allAlready = starts.all { kind.matches(listPrefixOf(lineAt(it))) }
    var rich = this
    var shift = 0
    var cursorShift = 0
    var number = if (kind == ListKind.NUMBERED && starts.first() > 0) {
        val above = text.substring(text.lastIndexOf('\n', starts.first() - 2) + 1, starts.first() - 1)
        (listPrefixOf(above)?.takeIf { it.first().isDigit() }?.dropLast(2)?.toIntOrNull() ?: 0) + 1
    } else 1
    for (s in starts) {
        val at = s + shift
        val current = listPrefixOf(lineAt(s))
        val removed = current?.length ?: 0
        val added = if (allAlready) "" else when (kind) {
            ListKind.BULLET -> "$BULLET "
            ListKind.CHECKBOX -> if (current == "$CHECKBOX_DONE ") current else "$CHECKBOX_OPEN "
            ListKind.NUMBERED -> "${number++}. "
        }
        rich = rich.replaced(at, removed, added)
        shift += added.length - removed
        if (s <= selectionStart) cursorShift += added.length - removed
    }
    val start = (selectionStart + cursorShift).coerceIn(0, rich.text.length)
    val end = (selectionEnd + shift).coerceIn(start, rich.text.length)
    return RichEdit(rich, start, end)
}

/**
 * A line just ended with Enter goes on as the list it was in: the next line starts with the next
 * bullet, number or empty box. Enter on a list line with nothing after its mark ends the list,
 * and the mark goes. Null when [cursor] does not follow a new line typed into a list.
 */
fun RichText.listContinued(cursor: Int): RichEdit? {
    if (cursor <= 0 || cursor > text.length || text[cursor - 1] != '\n') return null
    val lineStart = text.lastIndexOf('\n', cursor - 2) + 1
    val line = text.substring(lineStart, cursor - 1)
    val prefix = listPrefixOf(line) ?: return null
    if (line.length == prefix.length) {
        // An empty item: the list ends here, the mark and the new line taken back.
        return RichEdit(replaced(lineStart, prefix.length + 1, ""), lineStart)
    }
    val next = when {
        prefix.first().isDigit() -> "${prefix.dropLast(2).toInt() + 1}. "
        prefix == "$CHECKBOX_DONE " -> "$CHECKBOX_OPEN "
        else -> prefix
    }
    return RichEdit(replaced(cursor, 0, next), cursor + next.length)
}

/** The box of the line [at] is on ticked or unticked; null when that line has none. */
fun RichText.checkboxToggled(at: Int): RichText? {
    val lineStart = if (at <= 0) 0 else text.lastIndexOf('\n', at - 1) + 1
    val mark = text.getOrNull(lineStart)
    if (text.getOrNull(lineStart + 1) != ' ') return null
    val toggled = when (mark) { CHECKBOX_OPEN -> CHECKBOX_DONE; CHECKBOX_DONE -> CHECKBOX_OPEN; else -> return null }
    return replaced(lineStart, 1, toggled.toString())
}

/** Whether the line starting at [lineStart] is a ticked box, which the editor draws struck through. */
fun RichText.isTickedLine(lineStart: Int): Boolean =
    text.getOrNull(lineStart) == CHECKBOX_DONE && text.getOrNull(lineStart + 1) == ' '

/** The runs of [rich], for drawing: one per stretch of one style. */
fun RichText.runs(): List<NoteSpan> = cleanSpans(spans, text.length)
