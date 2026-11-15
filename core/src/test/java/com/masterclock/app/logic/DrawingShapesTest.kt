package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class DrawingShapesTest {

    private fun shape(kind: Int, a: Pair<Float, Float>, b: Pair<Float, Float>) = shapePath(kind, a, b, 0xFF000000, 4f)

    @Test
    fun `a line is touched along it, a box anywhere inside`() {
        val line = shape(DrawingShape.LINE, 0f to 0f, 100f to 100f)
        assertTrue(touches(line, 50f, 52f, reach = 5f))
        assertFalse(touches(line, 50f, 80f, reach = 5f))
        val box = shape(DrawingShape.RECTANGLE, 100f to 100f, 0f to 0f)
        assertTrue(touches(box, 50f, 50f, reach = 5f))
        assertTrue(touches(box, 104f, 50f, reach = 5f))
        assertFalse(touches(box, 120f, 50f, reach = 5f))
        // A stroke of the finger and the eraser are not shapes.
        assertFalse(touches(DrawingPath(points = listOf(0f to 0f, 10f to 10f)), 5f, 5f, 5f))
        assertFalse(touches(line.copy(isEraser = true), 50f, 50f, 5f))
    }

    @Test
    fun `the topmost shape is the one tapped`() {
        val under = shape(DrawingShape.RECTANGLE, 0f to 0f, 100f to 100f)
        val over = shape(DrawingShape.ELLIPSE, 40f to 40f, 60f to 60f)
        val stroke = DrawingPath(points = listOf(0f to 0f, 100f to 100f))
        val paths = listOf(under, over, stroke)
        assertEquals(1, shapeAt(paths, 50f, 50f, 5f))
        assertEquals(0, shapeAt(paths, 10f, 90f, 5f))
        assertNull(shapeAt(paths, 300f, 300f, 5f))
    }

    @Test
    fun `a handle resizes, the body moves, and the kind changes alone`() {
        val box = shape(DrawingShape.RECTANGLE, 10f to 10f, 50f to 50f)
        assertEquals(ShapeGrip.END, gripAt(box, 52f, 49f, handle = 8f, reach = 5f))
        assertEquals(ShapeGrip.START, gripAt(box, 9f, 11f, handle = 8f, reach = 5f))
        assertEquals(ShapeGrip.BODY, gripAt(box, 30f, 30f, handle = 8f, reach = 5f))
        assertNull(gripAt(box, 90f, 90f, handle = 8f, reach = 5f))
        assertEquals(listOf(10f to 10f, 80f to 60f), box.gripped(ShapeGrip.END, 30f, 10f).points)
        assertEquals(listOf(0f to 15f, 50f to 50f), box.gripped(ShapeGrip.START, -10f, 5f).points)
        assertEquals(listOf(15f to 5f, 55f to 45f), box.gripped(ShapeGrip.BODY, 5f, -5f).points)
        assertEquals(box.points, box.copy(shape = DrawingShape.ARROW).points)
    }

    @Test
    fun `an arrow's head points back from its end, and fits a short arrow`() {
        val head = arrowHead(0f to 0f, 100f to 0f, width = 4f)
        assertEquals(2, head.size)
        head.forEach { (x, y) ->
            assertTrue(x < 100f)
            assertEquals(16f, hypot(100f - x, y), 0.01f)
        }
        assertEquals(-head[0].second, head[1].second, 0.01f)
        // On an arrow shorter than two heads, the head is half of it.
        assertEquals(5f, arrowHead(0f to 0f, 10f to 0f, 4f).map { hypot(10f - it.first, it.second) }.first(), 0.01f)
        assertTrue(arrowHead(5f to 5f, 5f to 5f, 4f).isEmpty())
    }

    @Test
    fun `a piece is put in a box, touched in it, and kept square however the box is pulled`() {
        val knight = pieceSticker("N", 100f, 100f, 40f)
        assertTrue(knight.isPiece)
        assertTrue(knight.isSelectable)
        assertFalse(knight.isShape)
        assertEquals(listOf(80f to 80f, 120f to 120f), knight.points)
        assertTrue(touches(knight, 110f, 90f, reach = 0f))
        assertFalse(touches(knight, 200f, 100f, reach = 5f))
        // Pulled wide, the piece stays square, in the middle.
        assertEquals(Triple(100f, 80f, 40f), knight.gripped(ShapeGrip.END, 40f, 0f).pieceSquare())
        // A label that names no piece is no piece.
        assertFalse(knight.copy(label = "X").isPiece)
    }

    @Test
    fun `an imported piece is kept only as a piece drawn, and nothing else keeps a label`() {
        val note = NotebookNote(
            title = "d", type = NotebookNoteType.DRAWING,
            drawingPaths = listOf(
                pieceSticker("q", 10f, 10f, 8f),
                pieceSticker("<script>", 10f, 10f, 8f),
                DrawingPath(points = listOf(0f to 0f, 1f to 1f), label = "junk"),
            ),
        )
        val kept = sanitizeNote(java.io.File("unused"), note, freshId = false).drawingPaths
        assertEquals(listOf(DrawingShape.PIECE, DrawingShape.FREE, DrawingShape.FREE), kept.map { it.shape })
        assertEquals(listOf("q", "", ""), kept.map { it.label })
    }

    @Test
    fun `an imported shape of no known kind is read as a stroke`() {
        val note = NotebookNote(
            title = "d", type = NotebookNoteType.DRAWING,
            drawingPaths = listOf(shape(DrawingShape.ELLIPSE, 0f to 0f, 1f to 1f), shape(99, 0f to 0f, 1f to 1f), shape(-1, 0f to 0f, 1f to 1f)),
        )
        val kept = sanitizeNote(java.io.File("unused"), note, freshId = false).drawingPaths.map { it.shape }
        assertEquals(listOf(DrawingShape.ELLIPSE, DrawingShape.FREE, DrawingShape.FREE), kept)
    }

    @Test
    fun `a shape changed is one step that undo and redo know`() {
        val box = shape(DrawingShape.RECTANGLE, 0f to 0f, 10f to 10f)
        val drawn = DrawingEdit(emptyList()).addStroke(box)
        val moved = drawn.replace(0, box.gripped(ShapeGrip.BODY, 5f, 5f))
        assertEquals(listOf(5f to 5f, 15f to 15f), moved.paths.single().points)
        assertEquals(box, moved.undo().paths.single())
        assertEquals(moved.paths, moved.undo().redo().paths)
        // Nothing changed, nothing recorded; an index not there, nothing done.
        assertEquals(drawn, drawn.replace(0, box))
        assertEquals(drawn, drawn.replace(3, box))
        // Taken out, and given back by Undo.
        val removed = moved.remove(0)
        assertTrue(removed.paths.isEmpty())
        assertEquals(moved.paths, removed.undo().paths)
        assertEquals(moved, moved.remove(5))
    }
}
