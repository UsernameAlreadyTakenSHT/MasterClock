package com.masterclock.app.logic

import org.junit.Test
import org.junit.Assert.*

/**
 * Covers [spokenDuration], the screen-reader rendering of a clock face.
 *
 * The cases that matter are the ones a naive "Xh Ym Zs" formatter gets wrong: empty components a
 * reader should not hear at all, and singulars.
 */
class SpokenDurationTest {

    @Test
    fun `minutes and seconds are both spoken`() {
        assertEquals("5 minutes 3 seconds", spokenDuration(303_000))
    }

    @Test
    fun `hours appear only once the clock is that long`() {
        assertEquals("1 hour 5 minutes 3 seconds", spokenDuration(3_903_000))
        assertEquals("2 hours 1 minute 1 second", spokenDuration(7_261_000))
    }

    @Test
    fun `empty components are dropped rather than spoken as zero`() {
        assertEquals("10 minutes", spokenDuration(600_000))
        assertEquals("1 hour", spokenDuration(3_600_000))
    }

    @Test
    fun `under a minute only seconds are spoken`() {
        assertEquals("45 seconds", spokenDuration(45_000))
        assertEquals("1 second", spokenDuration(1_000))
    }

    @Test
    fun `a fresh clock does not read as an empty string`() {
        assertEquals("0 seconds", spokenDuration(500))
    }

    @Test
    fun `an expired clock says so instead of counting nothing`() {
        assertEquals("no time left", spokenDuration(0))
        assertEquals("no time left", spokenDuration(-5_000))
    }

    @Test
    fun `sub-second remainders are truncated, never rounded up past the displayed time`() {
        // The clock face still shows 03, so the reader must not announce 4 seconds.
        assertEquals("3 seconds", spokenDuration(3_999))
    }
}
