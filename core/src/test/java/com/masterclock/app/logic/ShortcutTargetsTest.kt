package com.masterclock.app.logic

import org.junit.Test
import org.junit.Assert.*

/**
 * Covers [buildShortcutTargets], the pure slot-filling behind the launcher's long-press shortcuts.
 *
 * The interesting cases are all about *ordering and padding*: the list has to stay useful on a
 * fresh install (no presets, no history) without ever pushing the player's own presets off the end
 * once they exist.
 */
class ShortcutTargetsTest {

    private fun settings(timeMs: Long, incMs: Long = 0) = ChessClockSettings(
        main = PlayerSettings(initialTimeMs = timeMs, incrementMs = incMs, mode = TimerMode.FISCHER)
    )

    private fun log(startTime: Long, timeMs: Long = 300_000) =
        GameLog(startTime = startTime, settings = settings(timeMs))

    private fun preset(name: String, createdAt: Long) =
        SavedPreset(name = name, createdAt = createdAt, settings = settings(600_000))

    @Test
    fun `a fresh install still gets a full list from the blitz built-ins`() {
        val targets = buildShortcutTargets(history = emptyList(), presets = emptyList())

        assertEquals(BUILTIN_SHORTCUT_PRESETS.size, targets.size)
        assertEquals(listOf("builtin:fischer_3_2", "builtin:fischer_15_10"), targets.map { it.id })
    }

    @Test
    fun `recent games come first, then presets, then the built-in padding`() {
        val targets = buildShortcutTargets(
            history = listOf(log(startTime = 100), log(startTime = 200, timeMs = 180_000)),
            presets = listOf(preset("Club night", createdAt = 50)),
        )

        assertEquals(
            listOf("game:200", "game:100", "preset:${targets[2].id.removePrefix("preset:")}", "builtin:fischer_3_2"),
            targets.map { it.id },
        )
        assertEquals("Club night", targets[2].label)
    }

    @Test
    fun `at most two recent games are offered, newest first`() {
        val targets = buildShortcutTargets(
            history = listOf(log(startTime = 1, timeMs = 60_000), log(startTime = 3, timeMs = 180_000), log(startTime = 2)),
            presets = emptyList(),
        )

        assertEquals(listOf("game:3", "game:2"), targets.take(2).map { it.id })
        assertTrue(targets.none { it.id == "game:1" })
    }

    @Test
    fun `presets are offered newest first and push the built-ins out`() {
        val targets = buildShortcutTargets(
            history = emptyList(),
            presets = listOf(
                preset("Oldest", createdAt = 1),
                preset("Newest", createdAt = 9),
                preset("Middle", createdAt = 5),
            ),
        )

        assertEquals(listOf("Newest", "Middle", "Oldest", "3 + 2"), targets.map { it.label })
    }

    @Test
    fun `the list never exceeds the cap`() {
        val targets = buildShortcutTargets(
            history = listOf(log(startTime = 1), log(startTime = 2)),
            presets = (1..10).map { preset("P$it", createdAt = it.toLong()) },
        )

        assertEquals(4, targets.size)
    }

    @Test
    fun `game labels describe the time control rather than the date`() {
        val targets = buildShortcutTargets(
            history = listOf(GameLog(startTime = 7, settings = settings(180_000, incMs = 2_000))),
            presets = emptyList(),
        )

        assertEquals("3 min + 2s", targets.first().label)
    }

    @Test
    fun `a game with no base time falls back to its mode name`() {
        val hourglass = ChessClockSettings(main = PlayerSettings(initialTimeMs = 0, mode = TimerMode.HOURGLASS))
        val targets = buildShortcutTargets(
            history = listOf(GameLog(startTime = 7, settings = hourglass)),
            presets = emptyList(),
        )

        assertEquals(TimerMode.HOURGLASS.displayName(), targets.first().label)
    }

    @Test
    fun `a FIDE game is labelled with its periods, each counting its own moves`() {
        val periods = listOf(FidePeriod(timeMs = 7_200_000, movesToNext = 40), FidePeriod(timeMs = 3_600_000, movesToNext = 60), FidePeriod(timeMs = 900_000, movesToNext = 0))
        val fide = ChessClockSettings(main = PlayerSettings(mode = TimerMode.FIDE_PERIODS, fidePeriods = periods))
        val targets = buildShortcutTargets(history = listOf(GameLog(startTime = 7, settings = fide)), presets = emptyList())
        assertEquals("120'/40 + 60'/20 + 15' + 30s", targets.first().label)
    }

    @Test
    fun `a mode with no increment is labelled without one`() {
        val sd = ChessClockSettings(main = PlayerSettings(initialTimeMs = 600_000, mode = TimerMode.SUDDEN_DEATH))
        val targets = buildShortcutTargets(history = listOf(GameLog(startTime = 7, settings = sd)), presets = emptyList())
        assertEquals("10 min", targets.first().label)
    }

    @Test
    fun `a drawn mode advertises its range, not the base time it never shows`() {
        val random = ChessClockSettings(
            main = PlayerSettings(
                mode = TimerMode.RANDOM,
                initialTimeMs = 600_000,
                randomMinTimeMs = 60_000,
                randomMaxTimeMs = 600_000,
            )
        )
        val targets = buildShortcutTargets(
            history = listOf(GameLog(startTime = 7, settings = random)),
            presets = emptyList(),
        )

        assertEquals("Random 1-10 min", targets.first().label)
    }

    @Test
    fun `a move timer is labelled per move rather than by a base time it ignores`() {
        val moveTimer = ChessClockSettings(
            main = PlayerSettings(mode = TimerMode.MOVE_TIMER_STANDARD, initialTimeMs = 0, moveTimeMs = 30_000)
        )
        val targets = buildShortcutTargets(
            history = listOf(GameLog(startTime = 7, settings = moveTimer)),
            presets = emptyList(),
        )

        assertEquals("30s / move", targets.first().label)
    }

    @Test
    fun `modes that never read the base time are labelled by what they do read`() {
        fun label(p: PlayerSettings) = timeControlLabel(ChessClockSettings(main = p))
        assertEquals("Gong 30s / 5s", label(PlayerSettings(mode = TimerMode.GONG, gongReflectionMs = 30_000, gongMoveMs = 5_000)))
        assertEquals("Phases (3), 60 min", label(PlayerSettings(mode = TimerMode.PHASES, phases = List(3) { GamePhase(timeMs = 1_200_000) })))
        assertEquals("40 moves", label(PlayerSettings(mode = TimerMode.MOVE_COUNTS_DOWN, maxMoves = 40)))
        assertEquals(TimerMode.CHRONO_COUNTUP.displayName(), label(PlayerSettings(mode = TimerMode.CHRONO_COUNTUP)))
        assertEquals("45s / move", label(PlayerSettings(mode = TimerMode.FAST_MOVE, fastMoveMode = FastMoveType.TRANSFER, moveTimeMs = 45_000)))
        // Accelerate and Shrink start from the base time, and say so.
        assertEquals("10 min", label(PlayerSettings(mode = TimerMode.FAST_MOVE)))
        // A move timer with no move time does not read its unused initial time.
        assertEquals(TimerMode.MOVE_TIMER_SHARED.displayName(), label(PlayerSettings(mode = TimerMode.MOVE_TIMER_SHARED, moveTimeMs = 0)))
    }

    @Test
    fun `an unnamed preset is dropped rather than published as a blank shortcut`() {
        val targets = buildShortcutTargets(
            history = emptyList(),
            presets = listOf(preset("   ", createdAt = 1)),
        )

        assertEquals(listOf("builtin:fischer_3_2", "builtin:fischer_15_10"), targets.map { it.id })
    }

    @Test
    fun `one slot per time control, the newest kept`() {
        val fiveMin = ChessClockSettings(main = PlayerSettings(mode = TimerMode.SUDDEN_DEATH, initialTimeMs = 300_000))
        val history = listOf(GameLog(startTime = 2, settings = fiveMin), GameLog(startTime = 1, settings = fiveMin))
        val targets = buildShortcutTargets(history, emptyList())
        assertEquals(1, targets.count { timeControlLabel(it.settings) == "5 min" })
        assertEquals("game:2", targets.first().id)
    }

    @Test
    fun `the same numbers in another mode are another time control`() {
        fun game(t: Long, mode: TimerMode) = GameLog(startTime = t, settings = ChessClockSettings(main = PlayerSettings(mode = mode, initialTimeMs = 300_000, incrementMs = 2_000)))
        val targets = buildShortcutTargets(listOf(game(2, TimerMode.FISCHER), game(1, TimerMode.BRONSTEIN)), emptyList())
        assertEquals(listOf("game:2", "game:1"), targets.take(2).map { it.id })
    }

    @Test
    fun `a FIDE bonus from a later period, and a delay, are in the label`() {
        fun label(vararg p: FidePeriod) = timeControlLabel(ChessClockSettings(main = PlayerSettings(mode = TimerMode.FIDE_PERIODS, fidePeriods = p.toList())))
        assertEquals("120'/40 + 60'/20 + 15' + 30s", label(
            FidePeriod(timeMs = 7_200_000, incrementMs = 0, movesToNext = 40, isFischer = true),
            FidePeriod(timeMs = 3_600_000, incrementMs = 0, movesToNext = 60, isFischer = true),
            FidePeriod(timeMs = 900_000, incrementMs = 30_000, movesToNext = 0, isFischer = true),
        ))
        assertEquals("80'/40 + 30' d30s", label(
            FidePeriod(timeMs = 4_800_000, incrementMs = 30_000, movesToNext = 40, isFischer = false, hasDelay = true),
            FidePeriod(timeMs = 1_800_000, incrementMs = 30_000, movesToNext = 0, isFischer = false, hasDelay = true),
        ))
    }
}
