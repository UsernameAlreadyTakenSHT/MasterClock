package com.masterclock.app.logic

import org.junit.Assert.*
import org.junit.Test

/** The arbiter's corrections, with the numbers of the cases that went wrong; see [ClockInvariantsTest]. */
class ArbiterCorrectionsTest {

    private val minute = 60_000L

    private fun settings(mode: TimerMode, fb: FlagBehavior, players: Int = 2) = ChessClockSettings(
        main = PlayerSettings(mode = mode, moveTimeMs = 30_000, initialTimeMs = 10 * minute),
        flagBehavior = fb,
        numberOfPlayers = players,
    )

    private fun table(vararg players: PlayerState) = ChessClockState(players = players.toList(), activePlayer = 1)

    @Test
    fun `a penalty past zero is an overrun of what was left over`() {
        val s = settings(TimerMode.SUDDEN_DEATH, FlagBehavior.NEGATIVE)
        val after = arbiterAdjustTime(table(PlayerState(20_000), PlayerState(minute)), 1, -minute, s).players[0]
        assertEquals(40_000, after.timeRemainingMs)
        assertTrue(after.isOutOfTime && after.isNegative)
    }

    @Test
    fun `a penalty past zero under REVERSE counts the overrun`() {
        val s = settings(TimerMode.SUDDEN_DEATH, FlagBehavior.REVERSE)
        val after = arbiterAdjustTime(table(PlayerState(20_000), PlayerState(minute)), 1, -minute, s).players[0]
        assertEquals(40_000, after.timeRemainingMs)
        assertTrue(after.isOutOfTime)
    }

    @Test
    fun `a penalty past zero under FREEZE is a flag, and the first`() {
        val s = settings(TimerMode.SUDDEN_DEATH, FlagBehavior.FREEZE)
        val after = arbiterAdjustTime(table(PlayerState(20_000), PlayerState(minute)), 2, -2 * minute, s)
        assertEquals(0, after.players[1].timeRemainingMs)
        assertTrue(after.players[1].isOutOfTime)
        assertEquals(2, after.firstToFlag)
    }

    @Test
    fun `undoing a FIDE period keeps the overrun`() {
        val periods = listOf(
            FidePeriod(timeMs = 90 * minute, incrementMs = 30_000, movesToNext = 40),
            FidePeriod(timeMs = 30 * minute, incrementMs = 30_000, movesToNext = 0),
        )
        val p = PlayerSettings(mode = TimerMode.FIDE_PERIODS, fidePeriods = periods)
        val s = ChessClockSettings(main = p, flagBehavior = FlagBehavior.NEGATIVE)
        val at40 = PlayerState(timeRemainingMs = 15 * minute + 30_000, moveCount = 40, currentPeriodIndex = 1)
        val after = arbiterAdjustMoveCount(table(at40, PlayerState(minute)), 1, -1, s)
        val undone = after.players[0]
        assertEquals(14 * minute + 30_000, undone.timeRemainingMs)
        assertTrue(undone.isOutOfTime && undone.isNegative)
        assertEquals(0, undone.currentPeriodIndex)
        assertEquals(1, after.firstToFlag)
    }

    @Test
    fun `a Global reserve taken to exactly zero is an overrun from zero`() {
        for (fb in listOf(FlagBehavior.NEGATIVE, FlagBehavior.REVERSE)) {
            val s = settings(TimerMode.MOVE_TIMER_GLOBAL, fb)
            val p = PlayerState(timeRemainingMs = 25_000, secondaryTimeMs = minute)
            val after = arbiterAdjustTime(table(p, PlayerState(30_000, secondaryTimeMs = minute)), 1, -minute, s).players[0]
            assertEquals(0, after.timeRemainingMs)
            assertTrue(after.isOutOfTime)
            assertEquals(fb == FlagBehavior.NEGATIVE, after.isNegative)
        }
    }

    @Test
    fun `a Global Shared reserve taken to zero puts every player out at once`() {
        val s = settings(TimerMode.MOVE_TIMER_GLOBAL_SHARED, FlagBehavior.FREEZE)
        val p = PlayerState(timeRemainingMs = 25_000, secondaryTimeMs = minute)
        val after = arbiterAdjustTime(table(p, p), 1, -minute, s)
        assertTrue(after.players.all { it.isOutOfTime && it.secondaryTimeMs == 0L })
        assertEquals(1, after.firstToFlag)
    }

    @Test
    fun `reserve given back to a Global player in overrun brings them back`() {
        val s = settings(TimerMode.MOVE_TIMER_GLOBAL, FlagBehavior.NEGATIVE)
        val over = PlayerState(timeRemainingMs = 5_000, secondaryTimeMs = 0, isOutOfTime = true, isNegative = true)
        val after = arbiterAdjustTime(table(over, PlayerState(30_000, secondaryTimeMs = minute)), 1, minute, s)
        assertEquals(PlayerState(timeRemainingMs = 30_000, secondaryTimeMs = minute), after.players[0])
        assertNull(after.firstToFlag)
    }

    @Test
    fun `reserve given back in Overtime brings the player back on the reserve`() {
        val s = settings(TimerMode.MOVE_TIMER_OVERTIME, FlagBehavior.REVERSE)
        val over = PlayerState(timeRemainingMs = 5_000, secondaryTimeMs = 0, isOutOfTime = true)
        val after = arbiterAdjustTime(table(over, PlayerState(30_000, secondaryTimeMs = minute)), 1, minute, s).players[0]
        assertEquals(PlayerState(timeRemainingMs = 0, secondaryTimeMs = minute), after)
    }

    @Test
    fun `time given undoes a move clock flag, with a whole move`() {
        for (mode in listOf(TimerMode.MOVE_TIMER_GLOBAL, TimerMode.MOVE_TIMER_GLOBAL_SHARED)) {
            val s = settings(mode, FlagBehavior.FLAG)
            val flagged = PlayerState(timeRemainingMs = 0, secondaryTimeMs = minute, isOutOfTime = true)
            val other = PlayerState(timeRemainingMs = 30_000, secondaryTimeMs = minute)
            val after = arbiterAdjustTime(table(flagged, other), 1, 1_000, s)
            assertEquals(PlayerState(timeRemainingMs = 30_000, secondaryTimeMs = minute), after.players[0])
            assertEquals(other, after.players[1])
        }
    }

    @Test
    fun `more than a move given to a flagged move clock is all there`() {
        val s = settings(TimerMode.MOVE_TIMER_GLOBAL, FlagBehavior.FREEZE)
        val flagged = PlayerState(timeRemainingMs = 0, secondaryTimeMs = minute, isOutOfTime = true)
        val after = arbiterAdjustTime(table(flagged, PlayerState(30_000, secondaryTimeMs = minute)), 1, minute, s)
        assertEquals(PlayerState(timeRemainingMs = minute, secondaryTimeMs = minute), after.players[0])
    }

    @Test
    fun `a move overrun paid off comes back with a whole move, one not covered stays out`() {
        val s = settings(TimerMode.MOVE_TIMER_GLOBAL, FlagBehavior.NEGATIVE)
        val over = PlayerState(timeRemainingMs = 5_000, secondaryTimeMs = minute, isOutOfTime = true, isNegative = true)
        val other = PlayerState(30_000, secondaryTimeMs = minute)
        assertEquals(30_000, arbiterAdjustTime(table(over, other), 1, 6_000, s).players[0].timeRemainingMs)
        val still = arbiterAdjustTime(table(over, other), 1, 1_000, s).players[0]
        assertEquals(4_000, still.timeRemainingMs)
        assertTrue(still.isOutOfTime)
    }

    @Test
    fun `a shared reserve given to one player does not undo another's move clock flag`() {
        val s = settings(TimerMode.MOVE_TIMER_GLOBAL_SHARED, FlagBehavior.FREEZE)
        val flagged = PlayerState(timeRemainingMs = 0, secondaryTimeMs = minute, isOutOfTime = true)
        val other = PlayerState(timeRemainingMs = 30_000, secondaryTimeMs = minute)
        val after = arbiterAdjustTime(table(flagged, other), 2, 10_000, s)
        assertTrue(after.players[0].isOutOfTime)
        assertEquals(minute + 10_000, after.players[1].secondaryTimeMs)
    }

    @Test
    fun `a penalty past zero in Shared Auto flags nobody`() {
        val s = settings(TimerMode.MOVE_TIMER_SHARED, FlagBehavior.FREEZE)
        val after = arbiterAdjustTime(table(PlayerState(20_000), PlayerState(20_000)), 1, -minute, s)
        assertTrue(after.players.none { it.isOutOfTime })
        assertTrue(after.players.all { it.timeRemainingMs == 0L })
    }
}
