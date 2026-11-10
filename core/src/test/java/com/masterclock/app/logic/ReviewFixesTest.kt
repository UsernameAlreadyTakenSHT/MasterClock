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
