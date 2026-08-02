package com.masterclock.app.logic

import org.junit.Test
import org.junit.Assert.*

/**
 * Covers [clockAnnouncement] and [isUrgentAnnouncement], which drive the timer screen's live region.
 *
 * The property that matters most is the negative one: the announcement must NOT change as time
 * runs down. A live region re-announces whenever its text changes, so one that tracked the
 * countdown would make a screen reader recite the seconds and bury everything else.
 *
 * It reports an event rather than a sentence; the wording lives in the UI modules, so these tests
 * stay pure Kotlin.
 */
class ClockAnnouncementTest {

    private fun state(
        players: List<PlayerState>,
        activePlayer: Int? = null,
        isPaused: Boolean = false,
        firstToFlag: Int? = null,
    ) = ChessClockState(
        players = players,
        activePlayer = activePlayer,
        isPaused = isPaused,
        firstToFlag = firstToFlag,
    )

    private fun player(
        timeMs: Long = 600_000,
        outOfTime: Boolean = false,
        inByoyomi: Boolean = false,
        periodsLeft: Int = 0,
    ) = PlayerState(
        timeRemainingMs = timeMs,
        isOutOfTime = outOfTime,
        isInByoyomi = inByoyomi,
        byoyomiPeriodsRemaining = periodsLeft,
    )

    @Test
    fun `the announcement does not change as the clock ticks down`() {
        val before = clockAnnouncement(state(listOf(player(timeMs = 600_000), player()), activePlayer = 1))
        val after = clockAnnouncement(state(listOf(player(timeMs = 12_345), player()), activePlayer = 1))

        assertEquals(before, after)
        assertEquals(ClockAnnouncement.ToMove(1, null), before)
    }

    @Test
    fun `nothing is announced before the first press`() {
        assertNull(clockAnnouncement(state(listOf(player(), player()), activePlayer = null)))
    }

    @Test
    fun `passing the turn changes the announcement`() {
        assertEquals(
            ClockAnnouncement.ToMove(1, null),
            clockAnnouncement(state(listOf(player(), player()), activePlayer = 1)),
        )
        assertEquals(
            ClockAnnouncement.ToMove(2, null),
            clockAnnouncement(state(listOf(player(), player()), activePlayer = 2)),
        )
    }

    @Test
    fun `a flag is announced and takes priority over whose turn it is`() {
        val flagged = state(
            players = listOf(player(), player(timeMs = 0, outOfTime = true)),
            activePlayer = 1,
            firstToFlag = 2,
        )

        assertEquals(ClockAnnouncement.OutOfTime(2), clockAnnouncement(flagged))
        assertTrue(isUrgentAnnouncement(flagged))
    }

    @Test
    fun `a flag is still caught when no first-to-flag was recorded`() {
        val flagged = state(
            players = listOf(player(), player(timeMs = 0, outOfTime = true)),
            activePlayer = 1,
        )

        assertEquals(ClockAnnouncement.OutOfTime(2), clockAnnouncement(flagged))
        assertTrue(isUrgentAnnouncement(flagged))
    }

    @Test
    fun `an ordinary turn is not urgent, so it waits its turn to be spoken`() {
        assertFalse(isUrgentAnnouncement(state(listOf(player(), player()), activePlayer = 1)))
    }

    @Test
    fun `pausing is announced`() {
        val paused = state(listOf(player(), player()), activePlayer = 1, isPaused = true)

        assertEquals(ClockAnnouncement.Paused, clockAnnouncement(paused))
        assertFalse(isUrgentAnnouncement(paused))
    }

    @Test
    fun `byoyomi carries the periods left`() {
        val three = state(
            players = listOf(player(inByoyomi = true, periodsLeft = 3), player()),
            activePlayer = 1,
        )

        assertEquals(ClockAnnouncement.ToMove(1, 3), clockAnnouncement(three))
    }

    @Test
    fun `byoyomi with no periods left is distinct from not being in byoyomi at all`() {
        val inByoyomi = state(
            players = listOf(player(inByoyomi = true, periodsLeft = 0), player()),
            activePlayer = 1,
        )
        val notInByoyomi = state(listOf(player(), player()), activePlayer = 1)

        assertEquals(ClockAnnouncement.ToMove(1, 0), clockAnnouncement(inByoyomi))
        assertEquals(ClockAnnouncement.ToMove(1, null), clockAnnouncement(notInByoyomi))
    }

    @Test
    fun `consuming a byoyomi period changes the announcement so it is re-spoken`() {
        val three = clockAnnouncement(
            state(listOf(player(inByoyomi = true, periodsLeft = 3), player()), activePlayer = 1)
        )
        val two = clockAnnouncement(
            state(listOf(player(inByoyomi = true, periodsLeft = 2), player()), activePlayer = 1)
        )

        assertNotEquals(three, two)
    }
}
