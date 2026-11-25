package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The scoreboard's names start empty, so the screen shows each field's translated label, and the
 * English defaults an older store or backup still holds are read as no name at all.
 */
class ScoreboardNamesTest {

    @Test
    fun `a new scoreboard has no names, for the labels to show in the app's language`() {
        val session = ScoreboardSession()
        assertEquals("", session.player1Name)
        assertEquals("", session.player2Name)
    }

    @Test
    fun `the old English defaults are read as empty`() {
        val stored = ScoreboardSession(player1Name = "Player 1", player2Name = "Player 2")
        val read = sanitizeImportedScoreboard(stored)
        assertEquals("", read.player1Name)
        assertEquals("", read.player2Name)
    }

    @Test
    fun `a typed name is kept, even one that looks like a default on the other side`() {
        val read = sanitizeImportedScoreboard(ScoreboardSession(player1Name = "Ada", player2Name = "Player 1"))
        assertEquals("Ada", read.player1Name)
        assertEquals("Player 1", read.player2Name)
    }
}
