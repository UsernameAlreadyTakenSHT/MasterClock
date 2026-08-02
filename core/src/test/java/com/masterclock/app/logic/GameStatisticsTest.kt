package com.masterclock.app.logic

import org.junit.Test
import org.junit.Assert.*

/**
 * Covers [moveDurations] and [computeStatistics].
 *
 * The interesting cases are all about logs recorded *before* GameEvent.timeSpentMs existed, where
 * durations have to be reconstructed from timestamps without letting pauses or the opening press
 * corrupt them.
 */
class GameStatisticsTest {

    private fun log(
        events: List<GameEvent>,
        startingMs: Long = 600_000,
        players: Int = 2,
        mode: TimerMode = TimerMode.SUDDEN_DEATH,
    ) = GameLog(
        startTime = 1_000,
        settings = ChessClockSettings(main = PlayerSettings(initialTimeMs = startingMs, mode = mode)),
        events = events,
        initialPlayerStates = List(players) { PlayerStateProxy(timeRemainingMs = startingMs) },
    )

    private fun move(at: Long, player: Int, remaining: Long = 500_000, spent: Long? = null) =
        GameEvent(timestamp = at, eventType = "MOVE", playerIndex = player, timeRemainingMs = remaining, timeSpentMs = spent)

    // --- moveDurations ---

    @Test
    fun `recorded time spent is used verbatim when present`() {
        val durations = moveDurations(
            log(
                listOf(
                    GameEvent(timestamp = 0, eventType = "INITIAL_PRESS", playerIndex = 1),
                    move(at = 9_999, player = 1, spent = 4_000),
                    move(at = 20_000, player = 2, spent = 7_000),
                )
            )
        )

        assertEquals(listOf(4_000L, 7_000L), durations.map { it.durationMs })
    }

    @Test
    fun `older logs fall back to the gap between consecutive events`() {
        val durations = moveDurations(
            log(
                listOf(
                    GameEvent(timestamp = 0, eventType = "INITIAL_PRESS", playerIndex = 1),
                    move(at = 5_000, player = 1),
                    move(at = 12_000, player = 2),
                )
            )
        )

        assertEquals(listOf(5_000L, 7_000L), durations.map { it.durationMs })
    }

    @Test
    fun `a pause between two moves is not counted as thinking time`() {
        val durations = moveDurations(
            log(
                listOf(
                    GameEvent(timestamp = 0, eventType = "INITIAL_PRESS", playerIndex = 1),
                    GameEvent(timestamp = 2_000, eventType = "PAUSE"),
                    GameEvent(timestamp = 62_000, eventType = "RESUME"),
                    move(at = 65_000, player = 1),
                )
            )
        )

        assertEquals(listOf(5_000L), durations.map { it.durationMs })
    }

    @Test
    fun `the opening press is not itself a move`() {
        val durations = moveDurations(
            log(
                listOf(
                    GameEvent(timestamp = 0, eventType = "START"),
                    GameEvent(timestamp = 100, eventType = "INITIAL_PRESS", playerIndex = 1),
                    move(at = 3_100, player = 1),
                )
            )
        )

        assertEquals(1, durations.size)
        assertEquals(1, durations.first().moveNumber)
        assertEquals(3_000L, durations.first().durationMs)
    }

    @Test
    fun `move numbers are counted per player`() {
        val durations = moveDurations(
            log(
                listOf(
                    GameEvent(timestamp = 0, eventType = "INITIAL_PRESS", playerIndex = 1),
                    move(at = 1_000, player = 1, spent = 1_000),
                    move(at = 2_000, player = 2, spent = 1_000),
                    move(at = 3_000, player = 1, spent = 1_000),
                    move(at = 4_000, player = 2, spent = 1_000),
                )
            )
        )

        assertEquals(listOf(1 to 1, 2 to 1, 1 to 2, 2 to 2), durations.map { it.playerIndex to it.moveNumber })
    }

    @Test
    fun `events arriving out of order are sorted before measuring`() {
        val durations = moveDurations(
            log(
                listOf(
                    move(at = 12_000, player = 2),
                    GameEvent(timestamp = 0, eventType = "INITIAL_PRESS", playerIndex = 1),
                    move(at = 5_000, player = 1),
                )
            )
        )

        assertEquals(listOf(5_000L, 7_000L), durations.map { it.durationMs })
    }

    // --- computeStatistics ---

    @Test
    fun `no history yields an empty result rather than a crash`() {
        val stats = computeStatistics(emptyList())

        assertTrue(stats.isEmpty)
        assertEquals(0, stats.gamesPlayed)
        assertEquals(0L, stats.averageMoveMs)
        assertEquals(0f, stats.timePressureShare, 0.001f)
    }

    @Test
    fun `a game with no moves still counts as a game played`() {
        val stats = computeStatistics(listOf(log(listOf(GameEvent(timestamp = 0, eventType = "START")))))

        assertEquals(1, stats.gamesPlayed)
        assertEquals(0, stats.totalMoves)
        assertTrue(stats.isEmpty)
    }

    @Test
    fun `average median and slowest are computed across every game`() {
        val stats = computeStatistics(
            listOf(
                log(
                    listOf(
                        GameEvent(timestamp = 0, eventType = "INITIAL_PRESS", playerIndex = 1),
                        move(at = 1, player = 1, spent = 1_000),
                        move(at = 2, player = 2, spent = 3_000),
                    )
                ),
                log(
                    listOf(
                        GameEvent(timestamp = 0, eventType = "INITIAL_PRESS", playerIndex = 1),
                        move(at = 1, player = 1, spent = 20_000),
                    )
                ),
            )
        )

        assertEquals(2, stats.gamesPlayed)
        assertEquals(3, stats.totalMoves)
        assertEquals(24_000L, stats.totalThinkTimeMs)
        assertEquals(8_000L, stats.averageMoveMs)
        assertEquals(3_000L, stats.medianMoveMs)
        assertEquals(20_000L, stats.slowestMoveMs)
    }

    @Test
    fun `time pressure counts moves made under a tenth of the starting clock`() {
        val stats = computeStatistics(
            listOf(
                log(
                    startingMs = 600_000,
                    events = listOf(
                        GameEvent(timestamp = 0, eventType = "INITIAL_PRESS", playerIndex = 1),
                        move(at = 1, player = 1, remaining = 500_000, spent = 1_000),
                        move(at = 2, player = 2, remaining = 30_000, spent = 1_000),
                    ),
                )
            )
        )

        assertEquals(0.5f, stats.timePressureShare, 0.001f)
    }

    @Test
    fun `games with no recorded starting clock are left out of the pressure ratio`() {
        val stats = computeStatistics(
            listOf(
                GameLog(
                    startTime = 1,
                    settings = ChessClockSettings(),
                    events = listOf(
                        GameEvent(timestamp = 0, eventType = "INITIAL_PRESS", playerIndex = 1),
                        move(at = 1, player = 1, remaining = 10, spent = 1_000),
                    ),
                    initialPlayerStates = emptyList(),
                )
            )
        )

        assertEquals(1, stats.totalMoves)
        assertEquals(0f, stats.timePressureShare, 0.001f)
    }

    // --- three and four players ---

    @Test
    fun `four players each get their own move numbering and durations`() {
        val durations = moveDurations(
            log(
                players = 4,
                events = listOf(
                    GameEvent(timestamp = 0, eventType = "INITIAL_PRESS", playerIndex = 1),
                    move(at = 1_000, player = 1, spent = 1_000),
                    move(at = 3_000, player = 2, spent = 2_000),
                    move(at = 6_000, player = 3, spent = 3_000),
                    move(at = 10_000, player = 4, spent = 4_000),
                    move(at = 11_000, player = 1, spent = 1_000),
                ),
            )
        )

        assertEquals(listOf(1, 2, 3, 4, 1), durations.map { it.playerIndex })
        assertEquals(listOf(1, 1, 1, 1, 2), durations.map { it.moveNumber })
        assertEquals(listOf(1_000L, 2_000L, 3_000L, 4_000L, 1_000L), durations.map { it.durationMs })
    }

    @Test
    fun `three players reconstruct durations without recorded time spent`() {
        val durations = moveDurations(
            log(
                players = 3,
                events = listOf(
                    GameEvent(timestamp = 0, eventType = "INITIAL_PRESS", playerIndex = 1),
                    move(at = 2_000, player = 1),
                    move(at = 5_000, player = 2),
                    move(at = 9_000, player = 3),
                ),
            )
        )

        assertEquals(listOf(2_000L, 3_000L, 4_000L), durations.map { it.durationMs })
    }

    @Test
    fun `time pressure is judged against each player's own starting clock`() {
        val asymmetric = GameLog(
            startTime = 1,
            settings = ChessClockSettings(main = PlayerSettings(initialTimeMs = 600_000)),
            events = listOf(
                GameEvent(timestamp = 0, eventType = "INITIAL_PRESS", playerIndex = 1),
                // 50s left of 600s -> under a tenth, pressured.
                move(at = 1, player = 1, remaining = 50_000, spent = 1_000),
                // 50s left of 120s -> comfortably above a tenth, not pressured.
                move(at = 2, player = 2, remaining = 50_000, spent = 1_000),
            ),
            initialPlayerStates = listOf(
                PlayerStateProxy(timeRemainingMs = 600_000),
                PlayerStateProxy(timeRemainingMs = 120_000),
            ),
        )

        assertEquals(0.5f, computeStatistics(listOf(asymmetric)).timePressureShare, 0.001f)
    }

    @Test
    fun `modes are tallied most played first`() {
        val fischer = log(listOf(GameEvent(timestamp = 0, eventType = "START")), mode = TimerMode.FISHER)
        val sudden = log(listOf(GameEvent(timestamp = 0, eventType = "START")), mode = TimerMode.SUDDEN_DEATH)
        val stats = computeStatistics(listOf(fischer, sudden, fischer))

        assertEquals(TimerMode.FISHER, stats.perMode.first().mode)
        assertEquals(2, stats.perMode.first().games)
        assertEquals(2, stats.perMode.size)
    }
}
