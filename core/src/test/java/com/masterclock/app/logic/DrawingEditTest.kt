package com.masterclock.app.logic

import org.junit.Test
import org.junit.Assert.*

/** Covers [DrawingEdit], the drawing note's Undo, which used to have nothing to give back after Clear. */
class DrawingEditTest {

    private fun stroke(x: Float) = DrawingPath(points = listOf(x to 0f, x to 1f))

    @Test
    fun `undo after clear brings the whole drawing back`() {
        val drawn = DrawingEdit(emptyList()).addStroke(stroke(1f)).addStroke(stroke(2f))

        val restored = drawn.clear().undo()

        assertEquals(drawn.paths, restored.paths)
    }

    @Test
    fun `undo steps back one stroke at a time`() {
        val drawn = DrawingEdit(emptyList()).addStroke(stroke(1f)).addStroke(stroke(2f))

        assertEquals(listOf(stroke(1f)), drawn.undo().paths)
        assertEquals(emptyList<DrawingPath>(), drawn.undo().undo().paths)
        assertFalse(drawn.undo().undo().canUndo)
    }

    @Test
    fun `strokes drawn after a clear are undone before the clear is`() {
        val edit = DrawingEdit(emptyList()).addStroke(stroke(1f)).clear().addStroke(stroke(9f))

        assertEquals(emptyList<DrawingPath>(), edit.undo().paths)
        assertEquals(listOf(stroke(1f)), edit.undo().undo().paths)
    }

    @Test
    fun `clearing an empty drawing records nothing`() {
        val empty = DrawingEdit(emptyList())

        assertSame(empty, empty.clear())
        assertFalse(empty.clear().canUndo)
    }

    @Test
    fun `a reopened note with no history still undoes its saved strokes`() {
        val reopened = DrawingEdit(listOf(stroke(1f), stroke(2f)))

        assertTrue(reopened.canUndo)
        assertEquals(listOf(stroke(1f)), reopened.undo().paths)
    }

    @Test
    fun `a reopened note that is cleared can be restored`() {
        val reopened = DrawingEdit(listOf(stroke(1f), stroke(2f)))

        assertEquals(reopened.paths, reopened.clear().undo().paths)
    }

    @Test
    fun `the history is capped`() {
        var edit = DrawingEdit(emptyList())
        repeat(80) { edit = edit.addStroke(stroke(it.toFloat())) }

        assertEquals(50, edit.history.size)
        // Fifty undos reach the oldest state kept; strokes from before it are still dropped one by one.
        repeat(50) { edit = edit.undo() }
        assertEquals(30, edit.paths.size)
        assertTrue(edit.canUndo)
    }
}
