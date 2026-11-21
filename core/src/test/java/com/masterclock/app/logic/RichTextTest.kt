package com.masterclock.app.logic

import com.masterclock.app.data.storedJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RichTextTest {

    @Test
    fun `a highlight has one of five colours, an old one is yellow, and none takes it off`() {
        val rich = RichText("play e4 now").toggled(5, 7, NoteStyle.BOLD).highlighted(0, 7, 3)
        assertTrue(rich.allHave(0, 7, NoteStyle.HIGHLIGHT))
        assertEquals(3, highlighterOf(rich.runs().first().styles))
        // Bold kept beside it, and typing on from it goes on in the same colour.
        assertTrue(rich.allHave(5, 7, NoteStyle.BOLD))
        val typed = rich.edited("play e4! now", cursor = 8)
        assertEquals(3, highlighterOf(typed.runs().first { 7 in it.start until it.end }.styles))
        // Another colour over part of it; then none.
        val pink = rich.highlighted(0, 4, 2)
        assertEquals(2, highlighterOf(pink.runs().first().styles))
        assertFalse(rich.highlighted(0, 7, null).allHave(0, 1, NoteStyle.HIGHLIGHT))
        assertTrue(rich.highlighted(0, 7, null).allHave(5, 7, NoteStyle.BOLD))
        // A highlight from before, with no number, is the yellow; a number no highlighter has, too.
        assertEquals(0, highlighterOf(NoteStyle.HIGHLIGHT))
        assertEquals(0, highlighterOf(NoteStyle.HIGHLIGHT or (7 shl NoteStyle.HIGHLIGHT_SHIFT)))
    }

    @Test
    fun `a highlight is a style like bold, beside the others and kept through an edit`() {
        val rich = RichText("play e4 now").toggled(5, 7, NoteStyle.BOLD).toggled(0, 7, NoteStyle.HIGHLIGHT)
        assertTrue(rich.allHave(0, 7, NoteStyle.HIGHLIGHT))
        assertTrue(rich.allHave(5, 7, NoteStyle.BOLD or NoteStyle.HIGHLIGHT))
        // A character typed at its end takes the style before it.
        val typed = rich.edited("play e4! now", cursor = 8)
        assertTrue(typed.allHave(0, 8, NoteStyle.HIGHLIGHT))
        assertFalse(typed.allHave(8, 12, NoteStyle.HIGHLIGHT))
        assertFalse(rich.toggled(0, 7, NoteStyle.HIGHLIGHT).allHave(0, 1, NoteStyle.HIGHLIGHT))
    }

    private fun RichText.has(at: Int, bit: Int) = allHave(at, at + 1, bit)

    @Test
    fun `a note written with markers reads as it was drawn, without them`() {
        val rich = richTextFromMarkup("# Plan\nPlay **e4** and *hope*, <u>never</u> <color:FFF44336>blunder</color>\n## Endgame\nA * lone star and Game #3")
        assertEquals("Plan\nPlay e4 and hope, never blunder\nEndgame\nA * lone star and Game #3", rich.text)
        assertTrue(rich.has(0, NoteStyle.LARGE))
        assertTrue(rich.has(rich.text.indexOf("e4"), NoteStyle.BOLD))
        assertFalse(rich.has(rich.text.indexOf(" and"), NoteStyle.BOLD))
        assertTrue(rich.has(rich.text.indexOf("hope"), NoteStyle.ITALIC))
        assertTrue(rich.has(rich.text.indexOf("never"), NoteStyle.UNDERLINE))
        assertTrue(rich.has(rich.text.indexOf("Endgame"), NoteStyle.TITLE))
        val red = rich.spans.single { it.color != 0L }
        assertEquals(0xFFF44336, red.color)
        assertEquals("blunder", rich.text.substring(red.start, red.end))
    }

    @Test
    fun `an old checkbox becomes a box`() {
        assertEquals("Tasks\n☐ Analyse\n☑ Rest\nnot [ ] here", richTextFromMarkup("Tasks\n[ ] Analyse\n[x] Rest\nnot [ ] here").text)
    }

    @Test
    fun `a note keeps its format once converted`() {
        val old = NotebookNote(id = "n", content = "**bold**", timestamp = 1)
        val converted = old.withRichText(old.richTextOf())
        assertEquals("bold", converted.content)
        assertTrue(converted.richFormat)
        // Read again it is not converted again: the stars it might hold now are text.
        val withStars = converted.withRichText(RichText("a **b**", converted.richSpans))
        assertEquals("a **b**", withStars.richTextOf().text)
        // And it travels through the store as it is.
        val back = storedJson.decodeFromString(NotebookNote.serializer(), storedJson.encodeToString(NotebookNote.serializer(), converted))
        assertEquals(converted, back)
    }

    @Test
    fun `typing inside bold is bold, typing after a new line takes no heading`() {
        val rich = RichText("Title\nbody", listOf(NoteSpan(0, 5, NoteStyle.LARGE or NoteStyle.BOLD)))
        val typed = rich.edited("Titles\nbody")
        assertTrue(typed.has(5, NoteStyle.LARGE))
        val newLine = rich.edited("Title\nmore\nbody")
        assertFalse(newLine.has(6, NoteStyle.LARGE))
        // A style chosen with nothing selected is what is typed next.
        val chosen = RichText("ab").edited("aXb", typing = NoteStyle.ITALIC to 0L)
        assertTrue(chosen.has(1, NoteStyle.ITALIC))
        assertFalse(chosen.has(0, NoteStyle.ITALIC))
    }

    @Test
    fun `deleting keeps the formatting around it in place`() {
        val rich = RichText("one two three", listOf(NoteSpan(8, 13, NoteStyle.BOLD)))
        // "two " taken out, the cursor where it was.
        val shorter = rich.edited("one three", cursor = 4)
        assertEquals(listOf(NoteSpan(4, 9, NoteStyle.BOLD)), shorter.spans)
    }

    @Test
    fun `a style is set on a selection and taken off when it was all set`() {
        val rich = RichText("hello world")
        val bold = rich.toggled(0, 5, NoteStyle.BOLD)
        assertTrue(bold.allHave(0, 5, NoteStyle.BOLD))
        assertEquals(rich.spans, bold.toggled(0, 5, NoteStyle.BOLD).spans)
        // Part of it bold: the whole selection becomes bold.
        assertTrue(bold.toggled(3, 8, NoteStyle.BOLD).allHave(3, 8, NoteStyle.BOLD))
    }

    @Test
    fun `a heading is a whole line, and one size at a time`() {
        val rich = RichText("first\nsecond\nthird").headingToggled(8, 8, NoteStyle.LARGE)
        assertTrue(rich.allHave(6, 12, NoteStyle.LARGE))
        assertFalse(rich.has(0, NoteStyle.LARGE))
        assertFalse(rich.has(13, NoteStyle.LARGE))
        val smaller = rich.headingToggled(7, 7, NoteStyle.TITLE)
        assertTrue(smaller.allHave(6, 12, NoteStyle.TITLE))
        assertFalse(smaller.has(6, NoteStyle.LARGE))
    }

    @Test
    fun `lines become a list and go back`() {
        val rich = RichText("eggs\nmilk")
        val bullets = rich.listToggled(0, 9, ListKind.BULLET)
        assertEquals("• eggs\n• milk", bullets.rich.text)
        assertEquals("eggs\nmilk", bullets.rich.listToggled(0, bullets.rich.text.length, ListKind.BULLET).rich.text)
        assertEquals("1. eggs\n2. milk", rich.listToggled(0, 9, ListKind.NUMBERED).rich.text)
        assertEquals("☐ eggs\n☐ milk", rich.listToggled(0, 9, ListKind.CHECKBOX).rich.text)
        // A bullet list turned into boxes.
        assertEquals("☐ eggs\n☐ milk", bullets.rich.listToggled(0, bullets.rich.text.length, ListKind.CHECKBOX).rich.text)
    }

    @Test
    fun `enter goes on with the list, and on an empty item ends it`() {
        val numbered = RichText("1. e4\n")
        val next = numbered.listContinued(6)!!
        assertEquals("1. e4\n2. ", next.rich.text)
        assertEquals(9, next.selectionStart)
        val boxes = RichText("☑ done\n").listContinued(7)!!
        assertEquals("☑ done\n☐ ", boxes.rich.text)
        val ended = RichText("• a\n• \n").listContinued(7)!!
        assertEquals("• a\n", ended.rich.text)
        assertEquals(4, ended.selectionStart)
        assertNull(RichText("plain\n").listContinued(6))
    }

    @Test
    fun `a box is ticked and unticked from anywhere on its line`() {
        val rich = RichText("☐ analyse\n☐ rest")
        val ticked = rich.checkboxToggled(4)!!
        assertEquals("☑ analyse\n☐ rest", ticked.text)
        assertTrue(ticked.isTickedLine(0))
        assertEquals(rich.text, ticked.checkboxToggled(0)!!.text)
        assertNull(RichText("no box").checkboxToggled(2))
    }

    @Test
    fun `formatting from outside is held to the text`() {
        val spans = listOf(NoteSpan(-5, 3, NoteStyle.BOLD), NoteSpan(2, 99, NoteStyle.ITALIC), NoteSpan(4, 2, NoteStyle.BOLD), NoteSpan(0, 1, 0))
        assertEquals(listOf(NoteSpan(0, 3, NoteStyle.BOLD), NoteSpan(2, 5, NoteStyle.ITALIC)), cleanSpans(spans, 5))
    }

    @Test
    fun `a heading is its line's wherever the cursor stands on it, its start included`() {
        val rich = RichText("title\nbody").headingToggled(2, 2, NoteStyle.LARGE)
        // The start of the line, where no character stands before the cursor to ask.
        assertTrue(rich.linesAllHave(0, 0, NoteStyle.LARGE))
        assertTrue(rich.linesAllHave(5, 5, NoteStyle.LARGE))
        assertFalse(rich.linesAllHave(6, 6, NoteStyle.LARGE))
    }

    @Test
    fun `a heading taken off leaves none on the break typed after it`() {
        val typed = RichText("title").headingToggled(0, 0, NoteStyle.LARGE).edited("title\n", cursor = 6)
        // The break takes the style of the character before it, the heading with the rest.
        assertTrue(typed.spans.any { it.end == 6 && it.styles and NoteStyle.LARGE != 0 })
        val plain = typed.headingToggled(2, 2, NoteStyle.LARGE)
        assertTrue(plain.spans.none { it.styles and NoteStyle.HEADINGS != 0 })
        // Nor does setting one put it on a break, between two lines or after the last.
        val two = RichText("one\ntwo\n").headingToggled(0, 7, NoteStyle.TITLE)
        assertEquals(listOf(NoteSpan(0, 3, NoteStyle.TITLE), NoteSpan(4, 7, NoteStyle.TITLE)), two.spans)
    }
}
