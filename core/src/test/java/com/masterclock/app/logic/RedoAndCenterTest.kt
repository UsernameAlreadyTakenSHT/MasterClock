package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedoAndCenterTest {

    private fun snap(text: String) = TextSnapshot(text, text.length, text.length)

    @Test
    fun `a text note's redo gives back what undo took, until something new is done`() {
        var h = TextEditHistory().beforeEdit(snap(""), 0, typing = false)
        h = h.beforeEdit(snap("a"), 10_000, typing = false)
        // Now "ab". Undo twice, then Redo twice.
        val (one, h1) = h.undo(snap("ab"))!!
        assertEquals("a", one.text)
        val (zero, h2) = h1.undo(one)!!
        assertEquals("", zero.text)
        assertTrue(h2.canRedo)
        val (again, h3) = h2.redo(zero)!!
        assertEquals("a", again.text)
        val (last, h4) = h3.redo(again)!!
        assertEquals("ab", last.text)
        assertFalse(h4.canRedo)
        assertTrue(h4.canUndo)
        // Undo, then an edit: the undone state is gone.
        val (_, h5) = h4.undo(last)!!
        assertTrue(h5.canRedo)
        assertFalse(h5.beforeEdit(snap("a"), 20_000, typing = true).canRedo)
        assertNull(TextEditHistory().redo(snap("x")))
    }

    private fun stroke(x: Float) = DrawingPath(points = listOf(x to x), color = 0xFF000000, strokeWidth = 4f)

    @Test
    fun `a drawing's redo gives back a stroke, a clear, and a stroke of a reopened drawing`() {
        val drawn = DrawingEdit(emptyList()).addStroke(stroke(1f)).addStroke(stroke(2f))
        val undone = drawn.undo()
        assertEquals(listOf(stroke(1f)), undone.paths)
        assertEquals(drawn.paths, undone.redo().paths)
        assertFalse(undone.redo().canRedo)
        // Clear, undone and redone.
        val cleared = drawn.clear()
        assertEquals(drawn.paths, cleared.undo().paths)
        assertEquals(emptyList<DrawingPath>(), cleared.undo().redo().paths)
        // A drawing reopened has no history; Undo drops its last stroke, and Redo puts it back.
        val reopened = DrawingEdit(listOf(stroke(1f), stroke(2f)))
        assertEquals(reopened.paths, reopened.undo().redo().paths)
        // Drawing after an Undo forgets what Redo had.
        assertFalse(undone.addStroke(stroke(3f)).canRedo)
        assertEquals(undone, undone.redo().undo().copy(future = undone.future))
    }

    @Test
    fun `a board's history undoes and redoes whole states`() {
        var h = EditHistory<String>().record("empty")
        h = h.record("e4")
        val (back, h1) = h.undo("e4 e5")!!
        assertEquals("e4", back)
        val (forward, h2) = h1.redo(back)!!
        assertEquals("e4 e5", forward)
        assertFalse(h2.canRedo)
        assertFalse(h1.record("d4").canRedo)
        assertEquals(3, (1..5).fold(EditHistory<Int>(limit = 3)) { acc, i -> acc.record(i) }.past.size)
    }

    @Test
    fun `centring is the line's, on and off, and the next line starts at the left`() {
        val rich = RichText("Title\nbody\n\nmore")
        val centred = rich.centerToggled(2, 2)
        assertTrue(centred.isCenteredLine(0))
        assertFalse(centred.isCenteredLine(6))
        assertFalse(centred.centerToggled(0, 0).isCenteredLine(0))
        // Several lines, an empty one among them, on and then off again.
        val all = rich.centerToggled(0, rich.text.length)
        assertTrue(listOf(0, 6, 12).all { all.isCenteredLine(it) })
        val none = all.centerToggled(0, rich.text.length)
        assertTrue(listOf(0, 6, 12).none { none.isCenteredLine(it) })
        // Enter at the end of a centred line: the new line is not centred, the old one still is.
        val title = RichText("Title").centerToggled(0, 0)
        val typed = title.edited("Title\nSub", cursor = 9)
        assertTrue(typed.isCenteredLine(0))
        assertFalse(typed.isCenteredLine(6))
        // A bullet put in front of a centred line leaves it centred.
        assertTrue(centred.listToggled(0, 0, ListKind.BULLET).rich.isCenteredLine(0))
    }

    @Test
    fun `the centred lines found in one pass are the ones asked for line by line`() {
        val plain = RichText("one\ntwo\n\nfour")
        assertEquals(emptySet<Int>(), plain.centeredLineStarts())
        val rich = plain.centerToggled(4, 4).centerToggled(9, 9)
        val starts = listOf(0, 4, 8, 9)
        assertEquals(starts.filter { rich.isCenteredLine(it) }.toSet(), rich.centeredLineStarts())
        assertEquals(setOf(4, 9), rich.centeredLineStarts())
    }
}
