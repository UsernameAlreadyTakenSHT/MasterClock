package com.masterclock.app.logic

import org.junit.Test
import org.junit.Assert.*

/**
 * Covers which games the picker offers.
 *
 * Shogi is withheld while there is no electronic shogi board to connect to, but withheld is not
 * removed: everything that reads a shogi game -- the KIF export, the rules document, the spoken
 * labels -- still works, and someone who had already chosen it must still be able to see and leave
 * that choice.
 */
class GameTypeOfferingTest {

    @Test
    fun `chess and draughts are always offered`() {
        GameType.entries.forEach { current ->
            assertTrue(GameType.CHESS.isOfferable(current))
            assertTrue(GameType.DRAUGHTS.isOfferable(current))
        }
    }

    @Test
    fun `shogi is withheld from anyone not already using it`() {
        assertFalse(GameType.SHOGI.isOfferable(GameType.CHESS))
        assertFalse(GameType.SHOGI.isOfferable(GameType.DRAUGHTS))
    }

    @Test
    fun `shogi stays offered to whoever had already chosen it`() {
        // Otherwise the setting is invisible and unleavable: the picker would show chess and
        // draughts with neither selected, and no way to say which one is meant.
        assertTrue(GameType.SHOGI.isOfferable(GameType.SHOGI))
    }

    @Test
    fun `shogi is still a game type, not a removed one`() {
        // The export, the rules document and the spoken labels all switch on this value. Deleting
        // it would take a saved shogi game with it; only the offer is withdrawn.
        assertTrue(GameType.SHOGI in GameType.entries)
    }
}
