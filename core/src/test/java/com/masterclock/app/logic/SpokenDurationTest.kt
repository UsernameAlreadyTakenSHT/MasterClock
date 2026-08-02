package com.masterclock.app.logic

import org.junit.Test
import org.junit.Assert.*

/**
 * Covers [spokenDuration], which splits a clock face into the parts a screen reader should say.
 *
 * It reports numbers rather than words: the wording lives in the UI modules, where resources and
 * plural rules are available. These tests therefore stay pure Kotlin, with no Android on the
 * classpath.
 */
class SpokenDurationTest {

    @Test
    fun `minutes and seconds are split out`() {
        val d = spokenDuration(303_000)

        assertEquals(0L, d.hours)
        assertEquals(5L, d.minutes)
        assertEquals(3L, d.seconds)
        assertFalse(d.isExpired)
    }

    @Test
    fun `hours appear only once the clock is that long`() {
        assertEquals(0L, spokenDuration(600_000).hours)
        assertEquals(1L, spokenDuration(3_903_000).hours)
        assertEquals(2L, spokenDuration(7_261_000).hours)
    }

    @Test
    fun `an exact hour leaves the smaller components at zero, for the UI to drop`() {
        val d = spokenDuration(3_600_000)

        assertEquals(1L, d.hours)
        assertEquals(0L, d.minutes)
        assertEquals(0L, d.seconds)
    }

    @Test
    fun `under a minute only seconds are set`() {
        val d = spokenDuration(45_000)

        assertEquals(0L, d.minutes)
        assertEquals(45L, d.seconds)
    }

    @Test
    fun `a fresh clock is not expired even below one second`() {
        val d = spokenDuration(500)

        assertFalse(d.isExpired)
        assertEquals(0L, d.seconds)
    }

    @Test
    fun `an expired clock is flagged rather than reported as zero`() {
        assertTrue(spokenDuration(0).isExpired)
        assertTrue(spokenDuration(-5_000).isExpired)
    }

    @Test
    fun `sub-second remainders are truncated, never rounded up past the displayed time`() {
        // The clock face still shows 03, so the reader must not announce 4 seconds.
        assertEquals(3L, spokenDuration(3_999).seconds)
    }
}
