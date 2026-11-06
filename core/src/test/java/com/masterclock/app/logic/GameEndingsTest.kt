package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** How a game ends, and what is left of a flag once the arbiter has undone it. */
class GameEndingsTest {

    private fun out(time: Long = 0) = PlayerState(timeRemainingMs = time, isOutOfTime = true)

    @Test
    fun `a player out of time and playing on can be heard pausing`() {
        val negative = PlayerState(5_000, isOutOfTime = true, isNegative = true)
        val reverse = PlayerState(5_000, isOutOfTime = true)
        for (p in listOf(negative, reverse)) {
            val paused = ChessClockState(listOf(p, PlayerState(60_000)), activePlayer = 1, isPaused = true)
            assertEquals(ClockAnnouncement.Paused, clockAnnouncement(paused))
            assertEquals(ClockAnnouncement.OutOfTime(1), clockAnnouncement(paused.copy(isPaused = false)))
        }
    }

    @Test
    fun `a pause that ends the game still says who is out`() {
        val over = ChessClockState(listOf(out(), PlayerState(60_000)), activePlayer = 1, isPaused = true, firstToFlag = 1)
        assertEquals(ClockAnnouncement.OutOfTime(1), clockAnnouncement(over))
    }

    @Test
    fun `a Japanese byoyomi player brought back plays a period that counts`() {
        val s = PlayerSettings(mode = TimerMode.BYOYOMI_JAPANESE, byoyomiPeriods = 3, byoyomiTimeMs = 30_000)
        val state = ChessClockState(listOf(out().copy(isInByoyomi = true, byoyomiPeriodsRemaining = 0), PlayerState(60_000)), activePlayer = 1, isPaused = true)
        val back = arbiterAdjustTime(state, 1, 30_000, ChessClockSettings(main = s)).players[0]
        assertFalse(back.isOutOfTime)
        assertEquals(1, back.byoyomiPeriodsRemaining)
    }

    @Test
    fun `a game with no byoyomi gives none to a player brought back`() {
        val s = PlayerSettings(mode = TimerMode.BYOYOMI_JAPANESE, byoyomiPeriods = 0, byoyomiTimeMs = 30_000)
        val settings = ChessClockSettings(main = s)
        val state = ChessClockState(listOf(out().copy(isInByoyomi = true), PlayerState(60_000)), activePlayer = 1, isPaused = true)
        val back = arbiterAdjustTime(state, 1, 90_000, settings)
        assertFalse(back.players[0].isInByoyomi)
        assertEquals(90_000, computePostMoveState(back, 1, 1_000, settings, s).players[0].timeRemainingMs)
    }

    @Test
    fun `running out of moves ends the game under every flag behaviour`() {
        val s = PlayerSettings(mode = TimerMode.MOVE_COUNTS_DOWN, maxMoves = 1)
        for (fb in FlagBehavior.entries) {
            val settings = ChessClockSettings(main = s, flagBehavior = fb)
            val state = ChessClockState(listOf(PlayerState(0, moveCount = 1), PlayerState(0, moveCount = 1)), activePlayer = 1)
            assertTrue(fb.name, isGameOver(computePostMoveState(state, 1, 1_000, settings, s), fb, 2))
        }
    }

    @Test
    fun `a move left does not end it`() {
        val s = PlayerSettings(mode = TimerMode.MOVE_COUNTS_DOWN, maxMoves = 2)
        val settings = ChessClockSettings(main = s, flagBehavior = FlagBehavior.NEGATIVE)
        val state = ChessClockState(listOf(PlayerState(0, moveCount = 2), PlayerState(0, moveCount = 2)), activePlayer = 1)
        assertFalse(isGameOver(computePostMoveState(state, 1, 1_000, settings, s), FlagBehavior.NEGATIVE, 2))
    }

    @Test
    fun `an earlier period flag outlives an overrun paid back`() {
        val s = PlayerSettings(mode = TimerMode.FIDE_PERIODS, fidePeriods = listOf(FidePeriod(timeMs = 60_000), FidePeriod(timeMs = 30_000)))
        for (fb in listOf(FlagBehavior.FREEZE, FlagBehavior.NEGATIVE, FlagBehavior.REVERSE)) {
            val settings = ChessClockSettings(main = s, flagBehavior = fb, forcedMoveCounter = false)
            // Into the second period on a flag, then out of it for good.
            val second = tickPlayer(PlayerState(1_000), 2_000, s, settings)
            assertTrue(fb.name, second.hasFlagged)
            val flagged = tickPlayer(second, 30_000, s, settings)
            assertTrue(fb.name, flagged.isOutOfTime)
            val back = flagged.arbiterCredited(60_000, fb)
            assertFalse(fb.name, back.isOutOfTime)
            assertTrue(fb.name, back.hasFlagged)
        }
    }

    @Test
    fun `the last period running out is no period flag`() {
        val s = PlayerSettings(mode = TimerMode.FIDE_PERIODS, fidePeriods = listOf(FidePeriod(timeMs = 60_000)))
        val settings = ChessClockSettings(main = s, flagBehavior = FlagBehavior.FREEZE)
        val flagged = tickPlayer(PlayerState(1_000), 2_000, s, settings)
        assertTrue(flagged.isOutOfTime)
        assertFalse(flagged.hasFlagged)
    }

    @Test
    fun `the first flag moves on when that player is brought back`() {
        val s = PlayerSettings(mode = TimerMode.SUDDEN_DEATH)
        val settings = ChessClockSettings(main = s, flagBehavior = FlagBehavior.FLAG, numberOfPlayers = 4)
        val state = ChessClockState(listOf(out(), out(), PlayerState(60_000), PlayerState(60_000)), activePlayer = 3, isPaused = true, firstToFlag = 1)
        assertEquals(2, arbiterAdjustTime(state, 1, 60_000, settings).firstToFlag)
    }

    @Test
    fun `the first flag stands while that player is out`() {
        val s = PlayerSettings(mode = TimerMode.SUDDEN_DEATH)
        val settings = ChessClockSettings(main = s, flagBehavior = FlagBehavior.FLAG, numberOfPlayers = 4)
        val state = ChessClockState(listOf(out(), out(), PlayerState(60_000), PlayerState(60_000)), activePlayer = 3, isPaused = true, firstToFlag = 1)
        assertEquals(1, arbiterAdjustTime(state, 2, 60_000, settings).firstToFlag)
    }

    @Test
    fun `at a simultaneous Gong each player's phase sounds`() {
        val mine = PlayerSettings(mode = TimerMode.GONG, gongSimultaneous = true, gongReflectionMs = 10_000, gongMoveMs = 2_000)
        val settings = ChessClockSettings(differentSettingsPerPlayer = true, p1Custom = mine, p2Custom = mine.copy(gongReflectionMs = 5_000))
        val state = ChessClockState(listOf(PlayerState(10_000), PlayerState(5_000)), activePlayer = 1)
        assertEquals(true, tickClock(state, 5_000, settings).coupled?.playGong)
    }
}
