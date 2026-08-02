package com.masterclock.app.logic

import org.junit.Test
import org.junit.Assert.*

/**
 * Covers [clockAnnouncement] and [isUrgentAnnouncement], which drive the timer screen's live region.
 *
 * The property that matters most is the negative one: the message must NOT change as time runs
 * down. A live region re-announces whenever its text changes, so a message that tracked the
 * countdown would make a screen reader recite the seconds and bury everything else.
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
    fun `the message does not change as the clock ticks down`() {
        val before = clockAnnouncement(state(listOf(player(timeMs = 600_000), player()), activePlayer = 1))
        val after = clockAnnouncement(state(listOf(player(timeMs = 12_345), player()), activePlayer = 1))

        assertEquals(before, after)
        assertEquals("Player 1 to move", before)
    }

    @Test
    fun `nothing is announced before the first press`() {
        assertNull(clockAnnouncement(state(listOf(player(), player()), activePlayer = null)))
    }

    @Test
    fun `passing the turn changes the message`() {
        val p1 = clockAnnouncement(state(listOf(player(), player()), activePlayer = 1))
        val p2 = clockAnnouncement(state(listOf(player(), player()), activePlayer = 2))

        assertEquals("Player 1 to move", p1)
        assertEquals("Player 2 to move", p2)
    }

    @Test
    fun `a flag is announced and takes priority over whose turn it is`() {
        val flagged = state(
            players = listOf(player(), player(timeMs = 0, outOfTime = true)),
            activePlayer = 1,
            firstToFlag = 2,
        )

        assertEquals("Player 2 is out of time", clockAnnouncement(flagged))
        assertTrue(isUrgentAnnouncement(flagged))
    }

    @Test
    fun `a flag is still caught when no first-to-flag was recorded`() {
        val flagged = state(
            players = listOf(player(), player(timeMs = 0, outOfTime = true)),
            activePlayer = 1,
        )

        assertEquals("Player 2 is out of time", clockAnnouncement(flagged))
        assertTrue(isUrgentAnnouncement(flagged))
    }

    @Test
    fun `an ordinary turn is not urgent, so it waits its turn to be spoken`() {
        assertFalse(isUrgentAnnouncement(state(listOf(player(), player()), activePlayer = 1)))
    }

    @Test
    fun `pausing is announced`() {
        val paused = state(listOf(player(), player()), activePlayer = 1, isPaused = true)

        assertEquals("Paused", clockAnnouncement(paused))
        assertFalse(isUrgentAnnouncement(paused))
    }

    @Test
    fun `byoyomi is announced with the periods left, pluralised`() {
        val three = state(
            players = listOf(player(inByoyomi = true, periodsLeft = 3), player()),
            activePlayer = 1,
        )
        val one = state(
            players = listOf(player(inByoyomi = true, periodsLeft = 1), player()),
            activePlayer = 1,
        )

        assertEquals("Player 1 to move, byoyomi, 3 periods left", clockAnnouncement(three))
        assertEquals("Player 1 to move, byoyomi, 1 period left", clockAnnouncement(one))
    }

    @Test
    fun `byoyomi with no periods left does not trail an empty count`() {
        val state = state(
            players = listOf(player(inByoyomi = true, periodsLeft = 0), player()),
            activePlayer = 1,
        )

        assertEquals("Player 1 to move, byoyomi", clockAnnouncement(state))
    }

    @Test
    fun `consuming a byoyomi period changes the message so it is re-announced`() {
        val three = clockAnnouncement(
            state(listOf(player(inByoyomi = true, periodsLeft = 3), player()), activePlayer = 1)
        )
        val two = clockAnnouncement(
            state(listOf(player(inByoyomi = true, periodsLeft = 2), player()), activePlayer = 1)
        )

        assertNotEquals(three, two)
    }
}
