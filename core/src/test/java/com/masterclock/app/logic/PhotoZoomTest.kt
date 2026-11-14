package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoZoomTest {

    private val w = 1000f
    private val h = 800f

    /** Where a point of the photo, from the place's top left corner when shown whole, is drawn. */
    private fun PhotoZoom.drawn(px: Float, py: Float) =
        (w / 2 + x + (px - w / 2) * scale) to (h / 2 + y + (py - h / 2) * scale)

    @Test
    fun `a pinch keeps the point between the fingers under them`() {
        val zoomed = PhotoZoom().transformed(2f, 700f, 300f, 0f, 0f, w, h)
        assertEquals(2f, zoomed.scale, 0.001f)
        val (dx, dy) = zoomed.drawn(700f, 300f)
        assertEquals(700f, dx, 0.01f)
        assertEquals(300f, dy, 0.01f)
        // And again, from there.
        val more = zoomed.transformed(1.5f, 400f, 500f, 0f, 0f, w, h)
        val pointUnderFingers = ((400f - w / 2 - zoomed.x) / zoomed.scale + w / 2) to ((500f - h / 2 - zoomed.y) / zoomed.scale + h / 2)
        val after = more.drawn(pointUnderFingers.first, pointUnderFingers.second)
        assertEquals(400f, after.first, 0.01f)
        assertEquals(500f, after.second, 0.01f)
    }

    @Test
    fun `never smaller than its place, nor larger than five times`() {
        assertEquals(PhotoZoom(), PhotoZoom().transformed(0.5f, 100f, 100f, 0f, 0f, w, h))
        assertEquals(MAX_PHOTO_ZOOM, PhotoZoom().transformed(20f, 500f, 400f, 0f, 0f, w, h).scale, 0.001f)
        // Shown whole, a drag moves nothing.
        assertEquals(PhotoZoom(), PhotoZoom().transformed(1f, 500f, 400f, 300f, -200f, w, h))
    }

    @Test
    fun `a drag stops where the place would show past the photo's edge`() {
        val zoomed = PhotoZoom(scale = 2f)
        val dragged = zoomed.transformed(1f, 500f, 400f, 10_000f, -10_000f, w, h)
        assertEquals(500f, dragged.x, 0.01f)
        assertEquals(-400f, dragged.y, 0.01f)
        // The photo's corner is then at the place's corner, and no further.
        assertEquals(0f, dragged.drawn(0f, h).first, 0.01f)
        assertEquals(h, dragged.drawn(0f, h).second, 0.01f)
    }

    @Test
    fun `a double tap enlarges where it is, and the next shows it whole`() {
        val tapped = PhotoZoom().doubleTapped(250f, 200f, w, h)
        assertEquals(PHOTO_DOUBLE_TAP_ZOOM, tapped.scale, 0.001f)
        assertTrue(tapped.isZoomed)
        val (dx, dy) = tapped.drawn(250f, 200f)
        assertEquals(250f, dx, 0.01f)
        assertEquals(200f, dy, 0.01f)
        val whole = tapped.doubleTapped(10f, 10f, w, h)
        assertEquals(PhotoZoom(), whole)
        assertFalse(whole.isZoomed)
    }
}
