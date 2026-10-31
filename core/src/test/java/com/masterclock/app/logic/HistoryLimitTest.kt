package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers [longerHistoryLimit], which a full restore keeps so as not to trim the history it brings. */
class HistoryLimitTest {
    @Test
    fun `no limit wins either way`() {
        assertEquals(-1, longerHistoryLimit(-1, 100))
        assertEquals(-1, longerHistoryLimit(100, -1))
    }

    @Test
    fun `otherwise the larger`() {
        assertEquals(1000, longerHistoryLimit(100, 1000))
        assertEquals(1000, longerHistoryLimit(1000, 100))
    }
}
