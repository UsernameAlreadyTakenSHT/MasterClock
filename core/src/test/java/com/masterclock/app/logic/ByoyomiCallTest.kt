package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A byoyomi period read aloud as a shogi timekeeper reads it; see [byoyomiCall]. */
class ByoyomiCallTest {

    /** Every call of a period of [seconds], the clock coming down a tenth at a time as it does at the end. */
    private fun read(seconds: Int): List<String> {
        val period = seconds * 1000L
        val calls = mutableListOf<String>()
        var left = period
        while (left > 0) {
            val next = left - 100
            when (val call = byoyomiCall(period, left, next)) {
                is ByoyomiCall.Seconds -> calls += "${call.elapsed}s"
                is ByoyomiCall.Count -> calls += call.number.toString()
                null -> Unit
            }
            left = next
        }
        return calls
    }

    @Test
    fun `a minute a move is thirty, forty, fifty seconds, then one to nine`() {
        assertEquals(listOf("30s", "40s", "50s", "1", "2", "3", "4", "5", "6", "7", "8", "9"), read(60))
    }

    @Test
    fun `thirty seconds a move is ten, twenty seconds, then one to nine`() {
        assertEquals(listOf("10s", "20s", "1", "2", "3", "4", "5", "6", "7", "8", "9"), read(30))
    }

    @Test
    fun `ten is never called, it is the flag`() {
        for (seconds in listOf(5, 10, 20, 30, 45, 60, 120)) {
            val calls = read(seconds)
            assertFalse("$seconds: $calls", "10" in calls)
            assertEquals("$seconds: $calls", "9".takeIf { seconds >= 10 } ?: (seconds - 1).toString(), calls.last())
        }
    }

    @Test
    fun `other lengths take the tens of their last half minute and the same count`() {
        assertEquals(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9"), read(10))
        assertEquals(listOf("10s", "1", "2", "3", "4", "5", "6", "7", "8", "9"), read(20))
        assertEquals(listOf("90s", "100s", "110s", "1", "2", "3", "4", "5", "6", "7", "8", "9"), read(120))
        // Under ten seconds, counted from the start.
        assertEquals(listOf("1", "2", "3", "4"), read(5))
    }

    @Test
    fun `each call is made once, on the second it belongs to`() {
        // The tick on the second's edge, landing a millisecond past it as wholeSecondTickMs does.
        assertEquals(ByoyomiCall.Seconds(10), byoyomiCall(30_000, 20_999, 19_999))
        assertNull(byoyomiCall(30_000, 19_999, 19_900))
        assertEquals(ByoyomiCall.Count(1), byoyomiCall(30_000, 9_100, 9_000))
        assertNull(byoyomiCall(30_000, 9_000, 8_900))
        // A second skipped by a slow tick speaks the one reached, not the one missed.
        assertEquals(ByoyomiCall.Count(3), byoyomiCall(30_000, 9_500, 6_900))
    }

    @Test
    fun `a byoyomi beginning and a period lost are announced, the last period going is the flag`() {
        val main = PlayerState(5_000)
        val entered = PlayerState(30_000, isInByoyomi = true, byoyomiPeriodsRemaining = 5)
        assertEquals(ByoyomiPassage.Entered, byoyomiPassage(main, entered))
        assertEquals(ByoyomiPassage.PeriodsLeft(4), byoyomiPassage(entered.copy(timeRemainingMs = 100), entered.copy(byoyomiPeriodsRemaining = 4)))
        // A second of a period, and a move played in one: nothing to say.
        assertNull(byoyomiPassage(entered, entered.copy(timeRemainingMs = 29_000)))
        assertNull(byoyomiPassage(main, main.copy(timeRemainingMs = 4_000)))
        // The last one gone is time up, said as that.
        assertNull(byoyomiPassage(entered.copy(byoyomiPeriodsRemaining = 1), entered.copy(timeRemainingMs = 0, byoyomiPeriodsRemaining = 0, isOutOfTime = true)))
    }

    @Test
    fun `a clock going up, a period over and a period of nothing say nothing`() {
        // A move played or a period lost: the clock is back at the top.
        assertNull(byoyomiCall(30_000, 4_000, 30_000))
        assertNull(byoyomiCall(30_000, 500, 0))
        assertNull(byoyomiCall(0, 5_000, 4_000))
        assertTrue(ChessClockSettings(spokenByoyomi = true).speaks())
        assertTrue(ChessClockSettings(voiceAnnouncementsEnabled = true).speaks())
        assertFalse(ChessClockSettings().speaks())
    }
}
