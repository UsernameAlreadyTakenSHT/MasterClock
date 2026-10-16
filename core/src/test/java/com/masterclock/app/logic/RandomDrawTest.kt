package com.masterclock.app.logic

import org.junit.Test
import org.junit.Assert.*
import kotlin.random.Random

/** Covers [drawInRange], which draws the Random and Hidden times. */
class RandomDrawTest {

    @Test
    fun `a rounded draw never falls below the minimum`() {
        // 1:05 to 10:00 in ten-second steps: the lowest it may draw is 1:10, not 1:00.
        repeat(2_000) { seed ->
            val drawn = drawInRange(65_000, 600_000, 10_000, 1_000, Random(seed))
            assertTrue("drew $drawn", drawn in 70_000..600_000)
            assertEquals(0, drawn % 10_000)
        }
    }

    @Test
    fun `a range with no ten-second step in it draws to the second`() {
        // 12 to 15 seconds always drew 10, below the minimum.
        val seen = (0 until 500).map { drawInRange(12_000, 15_000, 10_000, 1_000, Random(it)) }.toSet()

        assertEquals(setOf(12_000L, 13_000L, 14_000L, 15_000L), seen)
    }

    @Test
    fun `a range narrower than any step gives its minimum`() {
        assertEquals(12_300, drawInRange(12_300, 12_700, 10_000, 1_000))
    }

    @Test
    fun `bounds that are already steps are both reachable`() {
        val seen = (0 until 500).map { drawInRange(60_000, 80_000, 10_000, 1_000, Random(it)) }.toSet()

        assertEquals(setOf(60_000L, 70_000L, 80_000L), seen)
    }
}
