package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class LargeScreenDigitsTest {

    private fun scale(smallest: Int, width: Float, height: Float) = largeScreenDigitScale(smallest, width, height, 400f, 200f)

    @Test
    fun `a phone keeps the sizes chosen for it, whatever the face`() {
        assertEquals(1f, scale(411, 411f, 450f))
        assertEquals(1f, scale(599, 2000f, 2000f))
    }

    @Test
    fun `a tablet goes by the tighter side`() {
        assertEquals(2f, scale(800, 800f, 640f))
        assertEquals(1.6f, scale(800, 1280f, 320f))
    }

    @Test
    fun `a small face on a large screen is not shrunk`() {
        assertEquals(1f, scale(800, 200f, 100f))
    }

    @Test
    fun `there is a limit`() {
        assertEquals(3f, scale(1200, 4000f, 4000f))
    }
}
