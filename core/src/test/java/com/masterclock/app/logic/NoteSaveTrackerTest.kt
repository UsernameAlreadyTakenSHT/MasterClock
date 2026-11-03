package com.masterclock.app.logic

import org.junit.Test
import org.junit.Assert.*

/** Covers [NoteSaveTracker], whose comparison with the opening value lost drawings and kept deleted text. */
class NoteSaveTrackerTest {

    /** Plays the debounced save: a change either waits out the delay ([settle]) or is replaced first. */
    private class Editor(stored: String) {
        val tracker = NoteSaveTracker(stored)
        val writes = mutableListOf<String>()
        private var waiting: String? = null
        fun edit(value: String) { waiting = if (tracker.changed(value)) value else null }
        fun settle() { waiting?.let { writes += it; tracker.written(it) }; waiting = null }
        fun leave() = tracker.flush { writes += it }
    }

    @Test
    fun `opening and leaving a note writes nothing`() {
        val editor = Editor("Hello")
        editor.edit("Hello")
        editor.settle()
        editor.leave()
        assertEquals(emptyList<String>(), editor.writes)
    }

    @Test
    fun `undo back to the opening value after a saved clear is written`() {
        val editor = Editor("strokes")
        editor.edit("")
        editor.settle()
        editor.edit("strokes")
        editor.settle()
        assertEquals(listOf("", "strokes"), editor.writes)
    }

    @Test
    fun `undo back to the opening value is flushed when leaving before the wait ends`() {
        val editor = Editor("strokes")
        editor.edit("")
        editor.settle()
        editor.edit("strokes")
        editor.leave()
        assertEquals(listOf("", "strokes"), editor.writes)
    }

    @Test
    fun `a letter typed and deleted within the wait is never written`() {
        val editor = Editor("Hello")
        editor.edit("Hellox")
        editor.edit("Hello")
        editor.leave()
        assertEquals(emptyList<String>(), editor.writes)
    }

    @Test
    fun `an edit still waiting is flushed once when leaving`() {
        val editor = Editor("Hello")
        editor.edit("Hello world")
        editor.leave()
        editor.leave()
        assertEquals(listOf("Hello world"), editor.writes)
    }

    @Test
    fun `nothing is flushed for an edit already written`() {
        val editor = Editor("Hello")
        editor.edit("Hello world")
        editor.settle()
        editor.leave()
        assertEquals(listOf("Hello world"), editor.writes)
    }
}
