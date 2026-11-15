package com.masterclock.app.logic

import org.junit.Test
import org.junit.Assert.*

/** Covers [TextEditHistory], the text note's Undo. */
class TextEditHistoryTest {

    private fun at(text: String) = TextSnapshot(text, text.length, text.length)

    @Test
    fun `fast typing is one undo step`() {
        var h = TextEditHistory()
        h = h.beforeEdit(at(""), nowMs = 0, typing = true)
        h = h.beforeEdit(at("a"), nowMs = 200, typing = true)
        h = h.beforeEdit(at("ab"), nowMs = 400, typing = true)

        val (restored, after) = h.undo()!!
        assertEquals("", restored.text)
        assertFalse(after.canUndo)
    }

    /** Typed without a pause, as on a phone's keyboard: each word is still a step of its own. */
    @Test
    fun `a word ended by a space or a new line is a step of its own`() {
        var h = TextEditHistory()
        var text = ""
        var now = 0L
        for (c in "play e4\nwin") {
            h = h.beforeEdit(at(text), nowMs = now, typing = true, endsWord = c == ' ' || c == '\n')
            text += c
            now += 100
        }
        val (first, h1) = h.undo(at(text))!!
        assertEquals("play e4\n", first.text)
        val (second, h2) = h1.undo(first)!!
        assertEquals("play ", second.text)
        val (third, h3) = h2.undo(second)!!
        assertEquals("", third.text)
        assertFalse(h3.canUndo)
    }

    @Test
    fun `a pause in typing starts a new step`() {
        var h = TextEditHistory()
        h = h.beforeEdit(at(""), nowMs = 0, typing = true)
        h = h.beforeEdit(at("hello"), nowMs = 0 + TEXT_UNDO_GROUP_MS + 500, typing = true)

        val (first, rest) = h.undo()!!
        assertEquals("hello", first.text)
        assertEquals("", rest.undo()!!.first.text)
    }

    @Test
    fun `a formatting action is its own step, even mid-typing`() {
        var h = TextEditHistory()
        h = h.beforeEdit(at(""), nowMs = 0, typing = true)
        h = h.beforeEdit(TextSnapshot("move", 0, 4), nowMs = 100, typing = false)

        val (beforeBold, rest) = h.undo()!!
        assertEquals(TextSnapshot("move", 0, 4), beforeBold)
        assertEquals("", rest.undo()!!.first.text)
    }

    @Test
    fun `typing right after a formatting action starts a new step`() {
        var h = TextEditHistory()
        h = h.beforeEdit(at("x"), nowMs = 0, typing = false)
        h = h.beforeEdit(at("**x**"), nowMs = 100, typing = true)

        assertEquals("**x**", h.undo()!!.first.text)
    }

    @Test
    fun `typing after an undo starts a new step`() {
        var h = TextEditHistory()
        h = h.beforeEdit(at(""), nowMs = 0, typing = true)
        h = h.beforeEdit(at("ab"), nowMs = 2_000, typing = true)
        h = h.undo()!!.second
        h = h.beforeEdit(at("a"), nowMs = 2_100, typing = true)

        assertEquals("a", h.undo()!!.first.text)
    }

    @Test
    fun `nothing to undo gives nothing`() {
        assertNull(TextEditHistory().undo())
        assertFalse(TextEditHistory().canUndo)
    }

    @Test
    fun `the history is capped`() {
        var h = TextEditHistory()
        repeat(80) { h = h.beforeEdit(at("v$it"), nowMs = it * 10L, typing = false) }

        assertEquals(50, h.past.size)
        assertEquals("v30", h.past.first().text)
    }
}
