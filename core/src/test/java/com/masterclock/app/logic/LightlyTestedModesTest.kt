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

    // --- Second group: covered, but not much more (10 to 14 tests each on 2026-11-25) ---

    // Canadian byoyomi: a block of time for a quota of moves, the same quota every time.

    private val canadian = PlayerSettings(mode = TimerMode.BYOYOMI_CANADIAN, initialTimeMs = 60_000, byoyomiTimeMs = 300_000, byoyomiPeriods = 20)

    @Test
    fun `Canadian byoyomi begins with what the tick ran past main time already gone`() {
        val after = tick(PlayerState(timeRemainingMs = 500, movesRemainingInPeriod = 20, currentByoyomiMovesGoal = 20), 2_000, canadian)
        assertTrue(after.isInByoyomi)
        assertEquals(298_500, after.timeRemainingMs)
    }

    @Test
    fun `Canadian byoyomi - a move in main time leaves the quota alone`() {
        val main = PlayerState(timeRemainingMs = 30_000, movesRemainingInPeriod = 20, currentByoyomiMovesGoal = 20)
        val after = move(main, spent = 3_000, s = canadian)
        assertEquals(20, after.movesRemainingInPeriod)
        assertEquals(30_000, after.timeRemainingMs)
    }

    @Test
    fun `Canadian byoyomi - the quota met renews the block, with the same quota`() {
        var p = PlayerState(timeRemainingMs = 300_000, isInByoyomi = true, movesRemainingInPeriod = 20, currentByoyomiMovesGoal = 20)
        repeat(19) { p = move(tick(p, 10_000, canadian), 10_000, canadian) }
        assertEquals(1, p.movesRemainingInPeriod)
        assertEquals(110_000, p.timeRemainingMs)
        p = move(tick(p, 10_000, canadian), 10_000, canadian)
        // A fresh block of the full time, and the same twenty moves: Canadian does not grow.
        assertEquals(300_000, p.timeRemainingMs)
        assertEquals(20, p.movesRemainingInPeriod)
    }

    @Test
    fun `Canadian byoyomi - a quota of zero is still one move a block`() {
        val zero = canadian.copy(byoyomiPeriods = 0)
        val lastMove = PlayerState(timeRemainingMs = 5_000, isInByoyomi = true, movesRemainingInPeriod = 1, currentByoyomiMovesGoal = 1)
        assertEquals(1, move(lastMove, 0, zero).movesRemainingInPeriod)
    }

    @Test
    fun `Canadian byoyomi - a block run out is the flag, and a move gives no new one`() {
        val inBlock = PlayerState(timeRemainingMs = 1_000, isInByoyomi = true, movesRemainingInPeriod = 3, currentByoyomiMovesGoal = 20)
        val out = tick(inBlock, 1_500, canadian)
        assertTrue(out.isOutOfTime)
        val moved = move(out, 0, canadian)
        assertTrue(moved.isOutOfTime)
        assertEquals(0, moved.timeRemainingMs)
    }

    // Hourglass: what one player's clock loses, the others gain.

    private val hourglass = PlayerSettings(mode = TimerMode.HOURGLASS, initialTimeMs = 60_000)

    private fun hourglassTick(players: List<PlayerState>, delta: Long, active: Int, flag: FlagBehavior = FlagBehavior.FREEZE) =
        tickCoupled(ChessClockState(players = players, activePlayer = active, isPaused = false), delta, ChessClockSettings(main = hourglass, numberOfPlayers = players.size, flagBehavior = flag), hourglass, active)!!

    @Test
    fun `Hourglass passes the time the player on the move loses to the other`() {
        val after = hourglassTick(listOf(PlayerState(60_000), PlayerState(60_000)), 1_000, active = 1).state
        assertEquals(59_000, after.players[0].timeRemainingMs)
        assertEquals(61_000, after.players[1].timeRemainingMs)
    }

    @Test
    fun `Hourglass keeps the table's total time over a whole game`() {
        var state = ChessClockState(players = listOf(PlayerState(60_000), PlayerState(45_000), PlayerState(30_000)), activePlayer = 1, isPaused = false)
        var active = 1
        repeat(300) {
            state = hourglassTick(state.players, 137, active).state
            if (it % 7 == 6) active = active % 3 + 1
        }
        assertEquals(135_000, state.players.sumOf { it.timeRemainingMs })
    }

    @Test
    fun `Hourglass splits an odd millisecond to whoever has least`() {
        val after = hourglassTick(listOf(PlayerState(60_000), PlayerState(10_000), PlayerState(50_000)), 1_001, active = 1).state
        assertEquals(10_501, after.players[1].timeRemainingMs)
        assertEquals(50_500, after.players[2].timeRemainingMs)
    }

    @Test
    fun `Hourglass running out is the flag, and the clock stops under FREEZE`() {
        val tick = hourglassTick(listOf(PlayerState(500), PlayerState(60_000)), 1_000, active = 1)
        assertTrue(tick.state.players[0].isOutOfTime)
        assertEquals(0, tick.state.players[0].timeRemainingMs)
        // It gains only what was really lost: half a second, not the whole tick.
        assertEquals(60_500, tick.state.players[1].timeRemainingMs)
        assertTrue(tick.state.isPaused)
    }

    @Test
    fun `Hourglass gives nothing to a player FLAG has put out`() {
        val out = PlayerState(0, isOutOfTime = true)
        val after = hourglassTick(listOf(PlayerState(60_000), out, PlayerState(30_000)), 1_000, active = 1, flag = FlagBehavior.FLAG).state
        assertEquals(0, after.players[1].timeRemainingMs)
        assertEquals(31_000, after.players[2].timeRemainingMs)
    }

    // Gong: a reflection phase, then a move phase, and the end of the move phase is the move.

    private val gong = PlayerSettings(mode = TimerMode.GONG, gongReflectionMs = 5_000, gongMoveMs = 3_000)

    private fun gongTick(state: ChessClockState, delta: Long, s: PlayerSettings = gong) =
        tickCoupled(state, delta, ChessClockSettings(main = s), s, state.activePlayer ?: 1)!!

    private val gongStart get() = ChessClockState(
        players = listOf(PlayerState(5_000, isGongReflectionPhase = true), PlayerState(5_000, isGongReflectionPhase = true)),
        activePlayer = 1, isPaused = false,
    )

    @Test
    fun `Gong - reflection ends with the gong and the move phase begins`() {
        val tick = gongTick(gongStart, 5_000)
        val p1 = tick.state.players[0]
        assertFalse(p1.isGongReflectionPhase)
        assertEquals(3_000, p1.timeRemainingMs)
        assertTrue(tick.playGong)
        assertEquals(1, tick.state.activePlayer)
    }

    @Test
    fun `Gong - the end of the move phase is the move, and the turn passes`() {
        val moving = gongTick(gongStart, 5_000).state
        val tick = gongTick(moving, 3_000)
        val p1 = tick.state.players[0]
        assertTrue(p1.isGongReflectionPhase)
        assertEquals(5_000, p1.timeRemainingMs)
        assertEquals(1, p1.moveCount)
        assertEquals(2, tick.state.activePlayer)
        assertEquals(listOf(1 to 8_000L), tick.gongMoves)
        // The player waiting does not run.
        assertEquals(5_000, tick.state.players[1].timeRemainingMs)
    }

    @Test
    fun `Gong - what a tick runs past a phase comes off the next`() {
        assertEquals(2_500, gongTick(gongStart, 5_500).state.players[0].timeRemainingMs)
    }

    @Test
    fun `Gong - a phase of zero is still a second long`() {
        val zero = gong.copy(gongMoveMs = 0)
        assertEquals(1_000, gongTick(gongStart, 5_000, zero).state.players[0].timeRemainingMs)
    }

    @Test
    fun `Gong played all at once runs every player and passes no turn`() {
        val together = gong.copy(gongSimultaneous = true)
        val tick = gongTick(gongStart, 6_000, together)
        tick.state.players.forEach {
            assertFalse(it.isGongReflectionPhase)
            assertEquals(2_000, it.timeRemainingMs)
        }
        assertEquals(1, tick.state.activePlayer)
    }

    // Chronos: one clock for the table, down or up.

    @Test
    fun `Chrono countdown runs the table's clock and stops it at zero`() {
        val down = PlayerSettings(mode = TimerMode.CHRONO_COUNTDOWN, initialTimeMs = 10_000)
        val settings = ChessClockSettings(main = down)
        var state = ChessClockState(players = listOf(PlayerState(0), PlayerState(0)), activePlayer = 1, isPaused = false, globalTimeMs = 10_000)
        state = tickCoupled(state, 4_000, settings, down, 1)!!.state
        assertEquals(6_000, state.globalTimeMs)
        assertFalse(state.isPaused)
        state = tickCoupled(state, 7_000, settings, down, 1)!!.state
        assertEquals(0, state.globalTimeMs)
        assertTrue(state.players.all { it.isOutOfTime })
        assertTrue(state.isPaused)
    }

    @Test
    fun `Chrono count-up only counts up, and never runs out`() {
        val up = PlayerSettings(mode = TimerMode.CHRONO_COUNTUP)
        val settings = ChessClockSettings(main = up)
        var state = ChessClockState(players = listOf(PlayerState(0)), activePlayer = 1, isPaused = false)
        repeat(1_000) { state = tickCoupled(state, 3_600, settings, up, 1)!!.state }
        assertEquals(3_600_000, state.globalTimeMs)
        assertFalse(state.players[0].isOutOfTime)
    }

    // Random: one draw for the table, inside the range, and its increment after every move.

    @Test
    fun `Random draws inside the range, in steps of ten seconds when rounded`() {
        val s = PlayerSettings(mode = TimerMode.RANDOM, randomMinTimeMs = 180_000, randomMaxTimeMs = 600_000, randomMinIncMs = 0, randomMaxIncMs = 5_000, roundedTime = true)
        val r = kotlin.random.Random(7)
        repeat(500) {
            val (base, inc) = drawRandomTimeControl(s, r)
            assertTrue(base in 180_000..600_000)
            assertEquals(0, base % 10_000)
            assertTrue(inc in 0..5_000)
            assertEquals(0, inc % 1_000)
        }
    }

    @Test
    fun `Random gives every player the same draw`() {
        val s = PlayerSettings(mode = TimerMode.RANDOM)
        val state = initialClockState(ChessClockSettings(main = s, numberOfPlayers = 4), 423_000L to 3_000L)
        state.players.forEach {
            assertEquals(423_000, it.timeRemainingMs)
            assertEquals(3_000, it.secondaryTimeMs)
        }
    }

    @Test
    fun `Random adds the drawn increment after every move`() {
        val s = PlayerSettings(mode = TimerMode.RANDOM)
        var p = PlayerState(timeRemainingMs = 423_000, secondaryTimeMs = 3_000)
        p = move(tick(p, 10_000, s), 10_000, s)
        assertEquals(416_000, p.timeRemainingMs)
    }
}
