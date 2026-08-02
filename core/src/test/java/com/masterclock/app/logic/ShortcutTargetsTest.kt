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
        main = PlayerSettings(initialTimeMs = timeMs, incrementMs = incMs, mode = TimerMode.FISHER)
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
            history = listOf(log(startTime = 100), log(startTime = 200)),
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
            history = listOf(log(startTime = 1), log(startTime = 3), log(startTime = 2)),
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
        val fide = ChessClockSettings(main = PlayerSettings(initialTimeMs = 0, mode = TimerMode.FIDE_PERIODS))
        val targets = buildShortcutTargets(
            history = listOf(GameLog(startTime = 7, settings = fide)),
            presets = emptyList(),
        )

        assertEquals("FIDE periods", targets.first().label)
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
    fun `an unnamed preset is dropped rather than published as a blank shortcut`() {
        val targets = buildShortcutTargets(
            history = emptyList(),
            presets = listOf(preset("   ", createdAt = 1)),
        )

        assertEquals(listOf("builtin:fischer_3_2", "builtin:fischer_15_10"), targets.map { it.id })
    }
}
