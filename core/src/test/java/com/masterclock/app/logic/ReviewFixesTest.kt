package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The scoreboard's cap and the log's, as a launch and a long game meet them. */
class ReviewFixesTest {

    @Test
    fun `the scoreboard keeps its newest games past the cap`() {
        val games = (1..MAX_SCOREBOARD_GAMES + 3).map { ScoreboardGame(result = "game $it", timestamp = it.toLong()) }
        val kept = sanitizeImportedScoreboard(ScoreboardSession(games = games)).games
        assertEquals(MAX_SCOREBOARD_GAMES, kept.size)
        assertEquals("game ${MAX_SCOREBOARD_GAMES + 3}", kept.last().result)
        assertEquals("game 4", kept.first().result)
    }

    @Test
    fun `a face of whole seconds ticks to its next reading`() {
        // Rounded down: 4.500 reads 0:04 until 3.999.
        assertEquals(501L, wholeSecondTickMs(4_500))
        assertEquals(1_000L, wholeSecondTickMs(3_999))
        // On the edge, and just short of it, the next reading is at hand: not zero, not a busy loop.
        assertEquals(20L, wholeSecondTickMs(5_000))
        assertEquals(20L, wholeSecondTickMs(5_003))
        // The last second ends on the flag.
        assertEquals(401L, wholeSecondTickMs(400))
        // An overrun counts up.
        assertEquals(700L, wholeSecondTickMs(-300))
        var remaining = 10_000L
        var ticks = 0
        while (remaining > 0) { remaining -= wholeSecondTickMs(remaining); ticks++ }
        assertTrue("ten seconds took $ticks ticks", ticks in 10..12)
    }

    @Test
    fun `a restore keeps the newest presets that fit`() {
        val local = List(495) { SavedPreset(name = "L$it", settings = ChessClockSettings()) }
        val imported = List(10) { SavedPreset(name = "I$it", settings = ChessClockSettings()) }
        val merged = mergeImportedPresets(local, imported)
        assertEquals(500, merged.size) // MAX_IMPORTED_PRESETS, private to its file
        assertEquals(local, merged.take(495))
        assertEquals(listOf("I5", "I6", "I7", "I8", "I9"), merged.drop(495).map { it.name })
    }

    @Test
    fun `an imported scoreboard adds its newest games that fit`() {
        val local = ScoreboardSession(games = (1..MAX_SCOREBOARD_GAMES - 3).map { ScoreboardGame(result = "L$it", timestamp = it.toLong()) })
        val imported = ScoreboardSession(games = (1..10).map { ScoreboardGame(result = "I$it", timestamp = 100_000L + it) })
        val merged = mergeImportedScoreboard(local, imported).games
        assertEquals(MAX_SCOREBOARD_GAMES, merged.size)
        assertEquals(listOf("I8", "I9", "I10"), merged.takeLast(3).map { it.result })
    }

    @Test
    fun `an Omni order drawn for more players never names one who is gone`() {
        val settings = OmniSettings(numberOfPlayers = 3, playerOrderType = PlayerOrderType.RANDOM)
        // Drawn for six, the count since lowered to three.
        val stale = listOf(5, 1, 4, 0, 3, 2)
        for (turn in stale.indices) {
            val player = omniPlayerForTurn(stale, turn, 0, settings)
            assertTrue("turn $turn went to player $player", player in 0 until 3)
        }
        assertEquals(1, omniPlayerForTurn(stale, 1, 0, settings))
    }

    @Test
    fun `a full log still takes what ends the game`() {
        val nearlyFull = MAX_IMPORTED_EVENTS_PER_LOG - ENDING_EVENTS_HEADROOM
        assertFalse(eventFitsLog(nearlyFull, GameEvent(eventType = "MOVE")))
        assertFalse(eventFitsLog(nearlyFull, GameEvent(eventType = "PHASE", detail = "Phase 3")))
        assertTrue(eventFitsLog(nearlyFull, GameEvent(eventType = "FLAG")))
        assertTrue(eventFitsLog(nearlyFull, GameEvent(eventType = "RESET")))
        assertTrue(eventFitsLog(nearlyFull, GameEvent(eventType = "PHASE", detail = "Finished")))
        assertFalse("the cap itself stands", eventFitsLog(MAX_IMPORTED_EVENTS_PER_LOG, GameEvent(eventType = "FLAG")))
    }
}
