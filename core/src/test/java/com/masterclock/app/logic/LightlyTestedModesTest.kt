package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules of the modes that had the fewest tests of their own, counted on 2026-11-25: Bronstein,
 * US Delay, Progressive byoyomi, Hidden and the move counters. Each was guarded by ClockInvariantsTest's
 * random games, which check that a clock never gains time it was not owed, but not by what the mode
 * itself promises: Bronstein's credit is what the move cost and no more, US Delay's delay is spent
 * first and never banked, a progressive block asks for more moves each time. Bronstein and US Delay
 * first, being the two played in tournaments.
 */
class LightlyTestedModesTest {

    private fun settings(s: PlayerSettings, flag: FlagBehavior = FlagBehavior.FREEZE) = ChessClockSettings(main = s, flagBehavior = flag)

    private fun move(p: PlayerState, spent: Long, s: PlayerSettings, flag: FlagBehavior = FlagBehavior.FREEZE): PlayerState =
        computePostMoveState(ChessClockState(players = listOf(p), activePlayer = 1), 1, spent, settings(s, flag), s).players[0]

    private fun tick(p: PlayerState, delta: Long, s: PlayerSettings, flag: FlagBehavior = FlagBehavior.FREEZE) =
        tickPlayer(p, delta, s, settings(s, flag))

    // --- Bronstein: the time a move cost comes back, up to the increment ---

    private val bronstein = PlayerSettings(mode = TimerMode.BRONSTEIN, incrementMs = 3_000)

    @Test
    fun `Bronstein gives back all of a move that cost less than the increment`() {
        val after = move(PlayerState(timeRemainingMs = 58_500), spent = 1_500, s = bronstein)
        // The clock reads what it read before the move: a quick move costs nothing.
        assertEquals(60_000, after.timeRemainingMs)
        assertEquals(1, after.moveCount)
    }

    @Test
    fun `Bronstein gives back exactly the increment for a move that cost exactly that`() {
        assertEquals(60_000, move(PlayerState(timeRemainingMs = 57_000), spent = 3_000, s = bronstein).timeRemainingMs)
    }

    @Test
    fun `Bronstein never gives back more than the increment`() {
        // Ten seconds spent, three given back: the move cost seven.
        assertEquals(53_000, move(PlayerState(timeRemainingMs = 50_000), spent = 10_000, s = bronstein).timeRemainingMs)
    }

    @Test
    fun `Bronstein gives nothing for a move that cost nothing`() {
        // Unlike Fischer, which would add the increment here and let a player bank time by moving fast.
        assertEquals(60_000, move(PlayerState(timeRemainingMs = 60_000), spent = 0, s = bronstein).timeRemainingMs)
    }

    @Test
    fun `Bronstein can never take a clock above where the move began`() {
        // Over many quick moves the clock stays put, where Fischer's would climb.
        var p = PlayerState(timeRemainingMs = 60_000)
        repeat(20) {
            val spent = 1_000L + it * 100
            p = tick(p, spent, bronstein)
            p = move(p, spent, bronstein)
            assertEquals(60_000, p.timeRemainingMs)
        }
    }

    @Test
    fun `Bronstein gives nothing to a player FLAG holds out`() {
        val out = PlayerState(timeRemainingMs = 0, isOutOfTime = true)
        val after = move(out, spent = 2_000, s = bronstein, flag = FlagBehavior.FLAG)
        assertEquals(0, after.timeRemainingMs)
        assertTrue(after.isOutOfTime)
    }

    @Test
    fun `Bronstein's credit pays off an overrun under NEGATIVE before it is time again`() {
        // One second over; the move cost five, three come back: two seconds to the good.
        val over = PlayerState(timeRemainingMs = 1_000, isOutOfTime = true, isNegative = true)
        val after = move(over, spent = 5_000, s = bronstein, flag = FlagBehavior.NEGATIVE)
        assertEquals(2_000, after.timeRemainingMs)
        assertFalse(after.isNegative)
        assertFalse(after.isOutOfTime)
    }

    @Test
    fun `Bronstein's credit smaller than the overrun leaves it smaller and still over`() {
        val over = PlayerState(timeRemainingMs = 5_000, isOutOfTime = true, isNegative = true)
        val after = move(over, spent = 8_000, s = bronstein, flag = FlagBehavior.NEGATIVE)
        assertEquals(2_000, after.timeRemainingMs)
        assertTrue(after.isNegative)
        assertTrue(after.isOutOfTime)
    }

    // --- US Delay: the delay is spent first, and what is left of it is lost ---

    private val usDelay = PlayerSettings(mode = TimerMode.US_DELAY, incrementMs = 5_000)

    @Test
    fun `US Delay adds nothing to the clock after a move`() {
        // Not Bronstein: an unused delay is not banked, whatever the move cost.
        val p = PlayerState(timeRemainingMs = 60_000, delayRemainingMs = 3_000)
        val after = move(p, spent = 2_000, s = usDelay)
        assertEquals(60_000, after.timeRemainingMs)
        assertEquals(1, after.moveCount)
    }

    @Test
    fun `US Delay used up to the millisecond leaves the main clock untouched`() {
        val after = tick(PlayerState(timeRemainingMs = 60_000, delayRemainingMs = 5_000), 5_000, usDelay)
        assertEquals(0, after.delayRemainingMs)
        assertEquals(60_000, after.timeRemainingMs)
        assertFalse(after.isOutOfTime)
    }

    @Test
    fun `US Delay running out on an empty clock is the flag`() {
        val after = tick(PlayerState(timeRemainingMs = 0, delayRemainingMs = 2_000), 2_000, usDelay)
        assertTrue(after.isOutOfTime)
        assertEquals(0, after.timeRemainingMs)
    }

    @Test
    fun `US Delay - one long tick runs through the delay and the clock to the flag`() {
        // 5s of delay, 3s on the clock, 10s gone: the flag, and under FREEZE nothing below zero.
        val after = tick(PlayerState(timeRemainingMs = 3_000, delayRemainingMs = 5_000), 10_000, usDelay)
        assertTrue(after.isOutOfTime)
        assertEquals(0, after.timeRemainingMs)
    }

    @Test
    fun `US Delay - the overrun past delay and clock is counted under NEGATIVE`() {
        val after = tick(PlayerState(timeRemainingMs = 3_000, delayRemainingMs = 5_000), 10_000, usDelay, FlagBehavior.NEGATIVE)
        assertTrue(after.isOutOfTime)
        assertTrue(after.isNegative)
        assertEquals(2_000, after.timeRemainingMs)
    }

    @Test
    fun `US Delay over a whole game charges only the time past each delay`() {
        // Ten moves: 3s, 7s, 3s, 7s... with a 5s delay. Only the 2s past it on the slow ones count.
        var p = PlayerState(timeRemainingMs = 60_000)
        repeat(10) {
            p = p.copy(delayRemainingMs = usDelay.incrementMs)
            val spent = if (it % 2 == 0) 3_000L else 7_000L
            repeat((spent / 100).toInt()) { p = tick(p, 100, usDelay) }
            p = move(p, spent, usDelay)
        }
        assertEquals(60_000 - 5 * 2_000L, p.timeRemainingMs)
    }

    // --- Progressive byoyomi: each block asks for more moves ---

    private val progressive = PlayerSettings(mode = TimerMode.BYOYOMI_PROGRESSIVE, byoyomiTimeMs = 30_000, byoyomiPeriods = 2, byoyomiProgression = 3)

    @Test
    fun `progressive byoyomi begins with what the tick ran past main time already gone`() {
        val after = tick(PlayerState(timeRemainingMs = 1_000), 1_500, progressive)
        assertTrue(after.isInByoyomi)
        assertEquals(29_500, after.timeRemainingMs)
        assertFalse(after.isOutOfTime)
    }

    @Test
    fun `progressive byoyomi - a move inside a block counts down the moves and keeps the clock running`() {
        val inBlock = PlayerState(timeRemainingMs = 12_000, isInByoyomi = true, movesRemainingInPeriod = 3, currentByoyomiMovesGoal = 5)
        val after = move(inBlock, spent = 4_000, s = progressive)
        assertEquals(2, after.movesRemainingInPeriod)
        // The block's time is not given back move by move: it is for the whole block.
        assertEquals(12_000, after.timeRemainingMs)
        assertEquals(5, after.currentByoyomiMovesGoal)
    }

    @Test
    fun `progressive byoyomi - the last move of a block starts the next one, longer by the progression`() {
        val lastMove = PlayerState(timeRemainingMs = 2_000, isInByoyomi = true, movesRemainingInPeriod = 1, currentByoyomiMovesGoal = 5)
        val after = move(lastMove, spent = 1_000, s = progressive)
        assertEquals(30_000, after.timeRemainingMs)
        assertEquals(8, after.movesRemainingInPeriod)
        assertEquals(8, after.currentByoyomiMovesGoal)
    }

    @Test
    fun `progressive byoyomi - three blocks in a row grow by the progression each time`() {
        var p = PlayerState(timeRemainingMs = 30_000, isInByoyomi = true, movesRemainingInPeriod = 2, currentByoyomiMovesGoal = 2)
        val goals = mutableListOf<Int>()
        repeat(3) {
            repeat(p.movesRemainingInPeriod) { p = move(p, spent = 100, s = progressive) }
            goals += p.currentByoyomiMovesGoal
        }
        assertEquals(listOf(5, 8, 11), goals)
    }

    @Test
    fun `progressive byoyomi - a block that runs out is the flag, with no block after it`() {
        val inBlock = PlayerState(timeRemainingMs = 1_000, isInByoyomi = true, movesRemainingInPeriod = 2, currentByoyomiMovesGoal = 5)
        val after = tick(inBlock, 1_000, progressive)
        assertTrue(after.isOutOfTime)
        assertEquals(0, after.timeRemainingMs)
        // A move then gives no fresh block to a player who is out.
        val moved = move(after, spent = 0, s = progressive)
        assertTrue(moved.isOutOfTime)
        assertEquals(0, moved.timeRemainingMs)
    }

    @Test
    fun `progressive byoyomi - the goal stops at the largest count, it does not wrap`() {
        val big = progressive.copy(byoyomiProgression = Int.MAX_VALUE)
        val lastMove = PlayerState(timeRemainingMs = 2_000, isInByoyomi = true, movesRemainingInPeriod = 1, currentByoyomiMovesGoal = 5)
        val after = move(lastMove, spent = 0, s = big)
        assertEquals(MAX_BYOYOMI_COUNT, after.currentByoyomiMovesGoal)
        assertTrue(after.movesRemainingInPeriod > 0)
    }

    // --- Hidden: the time left is shown only at thresholds ---

    private val hidden = PlayerSettings(mode = TimerMode.HIDDEN, showHiddenPercentages = true)

    @Test
    fun `Hidden shows the time for five seconds as it crosses half`() {
        val p = PlayerState(timeRemainingMs = 51_000, initialTotalTimeMs = 100_000)
        val after = tick(p, 2_000, hidden)
        assertEquals(50, after.lastRevealPercentage)
        assertEquals(5_000, after.revealTimeUntilMs)
        // And then it hides again, a tick at a time.
        val later = tick(after, 1_000, hidden)
        assertEquals(4_000, later.revealTimeUntilMs)
        assertEquals(50, later.lastRevealPercentage)
    }

    @Test
    fun `Hidden shows each threshold once, in order, down to one percent`() {
        var p = PlayerState(timeRemainingMs = 100_000, initialTotalTimeMs = 100_000)
        val shown = mutableListOf<Int>()
        while (!p.isOutOfTime) {
            val before = p.lastRevealPercentage
            p = tick(p, 500, hidden)
            if (p.lastRevealPercentage != before) shown += p.lastRevealPercentage
        }
        assertEquals(listOf(50, 25, 10, 5, 2, 1), shown)
    }

    @Test
    fun `Hidden crossing several thresholds at once shows the lowest`() {
        val p = PlayerState(timeRemainingMs = 60_000, initialTotalTimeMs = 100_000)
        assertEquals(5, tick(p, 56_000, hidden).lastRevealPercentage)
    }

    @Test
    fun `Hidden with percentages switched off never shows the time`() {
        var p = PlayerState(timeRemainingMs = 100_000, initialTotalTimeMs = 100_000)
        val off = hidden.copy(showHiddenPercentages = false)
        while (!p.isOutOfTime) {
            p = tick(p, 1_000, off)
            assertEquals(0, p.revealTimeUntilMs)
        }
    }

    @Test
    fun `Hidden - a move ends the reveal and adds the bonus`() {
        val revealed = PlayerState(timeRemainingMs = 40_000, secondaryTimeMs = 2_000, revealTimeUntilMs = 3_000, lastRevealPercentage = 50, initialTotalTimeMs = 100_000)
        val after = move(revealed, spent = 1_000, s = hidden)
        assertEquals(0, after.revealTimeUntilMs)
        assertEquals(42_000, after.timeRemainingMs)
    }

    @Test
    fun `Hidden running out flags and hides`() {
        val p = PlayerState(timeRemainingMs = 500, revealTimeUntilMs = 4_000, initialTotalTimeMs = 100_000)
        val after = tick(p, 1_000, hidden)
        assertTrue(after.isOutOfTime)
        assertEquals(0, after.revealTimeUntilMs)
    }

    // --- Move counters ---

    @Test
    fun `move counters do not run on the clock`() {
        for (mode in listOf(TimerMode.MOVE_COUNTS_UP, TimerMode.MOVE_COUNTS_DOWN)) {
            val p = PlayerState(timeRemainingMs = 0, moveCount = 7)
            assertEquals(p, tick(p, 60_000, PlayerSettings(mode = mode)))
        }
    }

    @Test
    fun `counting up adds a move at every press and never runs out`() {
        val up = PlayerSettings(mode = TimerMode.MOVE_COUNTS_UP)
        var p = PlayerState(timeRemainingMs = 0)
        repeat(250) { p = move(p, spent = 1_000, s = up) }
        assertEquals(250, p.moveCount)
        assertFalse(p.isOutOfTime)
    }

    @Test
    fun `counting down takes a move at every press and is out at zero, not before`() {
        val down = PlayerSettings(mode = TimerMode.MOVE_COUNTS_DOWN)
        var p = PlayerState(timeRemainingMs = 0, moveCount = 3)
        p = move(p, 0, down); assertEquals(2, p.moveCount); assertFalse(p.isOutOfTime)
        p = move(p, 0, down); assertEquals(1, p.moveCount); assertFalse(p.isOutOfTime)
        p = move(p, 0, down); assertEquals(0, p.moveCount); assertTrue(p.isOutOfTime)
        // And never below.
        p = move(p, 0, down); assertEquals(0, p.moveCount)
    }
}
