package com.masterclock.app.logic

import org.junit.Test
import org.junit.Assert.*
import java.util.UUID

/**
 * Covers the open BLE board protocol.
 *
 * Almost nothing here resembles the four vendor makes, and that is the point: this one reports
 * chess rather than sensors, so the tests are about carrying a move faithfully instead of decoding
 * one out of occupancy bits.
 *
 * The draughts cases matter as much as the chess ones. No manufacturer sells an electronic draughts
 * board any more, and this protocol is the only route left to one -- which works precisely because
 * nothing here reads a move, it only passes it along.
 */
class OpenBoardProtocolTest {

    private fun message(text: String) = text.toByteArray(Charsets.US_ASCII)

    @Test
    fun `a move is carried through exactly as the board wrote it`() {
        assertEquals(BoardReport.Moves(listOf("e2e4")), OpenBoardProtocol.decode(message("move e2e4")))
        assertEquals(BoardReport.Moves(listOf("e7e8q")), OpenBoardProtocol.decode(message("move e7e8q")))
    }

    @Test
    fun `a draughts move is carried just as faithfully`() {
        // The protocol keeps UCI notation for draughts rather than PDN's square numbering, and
        // promotion to a dame is spelled with a trailing d -- the spec's own example.
        assertEquals(BoardReport.Moves(listOf("b2a1d")), OpenBoardProtocol.decode(message("move b2a1d")))
        assertEquals(BoardReport.Moves(listOf("c3d4")), OpenBoardProtocol.decode(message("move c3d4")))
    }

    @Test
    fun `a draughts position decodes, men and dames included`() {
        // Brazilian, Russian and English draughts are played on 8x8, so their positions parse here;
        // only International, at 10x10, does not.
        val fen = "1m1m1m1m/m1m1m1m1/1m1m1m1m/8/8/M1M1M1M1/1M1M1M1M/M1M1M1M1"
        val report = BoardPosition.fromFenPlacement(fen)
        assertNotNull(report)
        // Draughts is played on one colour only, so every other square is empty.
        assertEquals('m', report!![1])
        assertEquals(BoardPosition.EMPTY, report[0])
        assertEquals('M', report[62])
        assertEquals(BoardPosition.EMPTY, report[63])
    }

    @Test
    fun `a square the board cannot identify is occupied but not a piece`() {
        // Occupancy-only sensors report this. It has to count as occupied, or every move made with
        // one would look like a piece appearing from nowhere.
        val position = BoardPosition.fromFenPlacement("????????/uuuuuuuu/8/8/8/8/UUUUUUUU/????????")
        assertNotNull(position)
        assertEquals(BoardPosition.UNKNOWN_PIECE, position!![0])
        assertEquals('u', position[8])
        assertEquals('U', position[48])
    }

    @Test
    fun `trailing whitespace and newlines do not become part of the move`() {
        assertEquals(BoardReport.Moves(listOf("e2e4")), OpenBoardProtocol.decode(message("move e2e4\n")))
        assertEquals(BoardReport.Moves(listOf("e2e4")), OpenBoardProtocol.decode(message("move e2e4  ")))
    }

    @Test
    fun `a move with nothing after it is not a move`() {
        assertEquals(BoardReport.Ignored, OpenBoardProtocol.decode(message("move")))
        assertEquals(BoardReport.Ignored, OpenBoardProtocol.decode(message("move ")))
    }

    @Test
    fun `a chess position arrives as FEN`() {
        val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        assertEquals(BoardReport.Position(BoardPosition.STARTING), OpenBoardProtocol.decode(message("state $fen")))
        assertEquals(BoardReport.Position(BoardPosition.STARTING), OpenBoardProtocol.decode(message("sync $fen")))
    }

    @Test
    fun `a position that is not eight ranks is turned away rather than mangled`() {
        // International draughts is played on 10x10. Refusing costs nothing: moves arrive through
        // "move" whatever the board size, and only the position would have been misread.
        assertEquals(BoardReport.Ignored, OpenBoardProtocol.decode(message("state 1m1m1m1m1m/m1m1m1m1m1/10/10/10/10/10/10/1M1M1M1M1M/M1M1M1M1M1")))
    }

    @Test
    fun `the handshake is begin, not get_state`() {
        // begin is what the protocol requires of a central; get_state is an optional feature, and a
        // conformant board sent it instead simply waits and says nothing.
        val init = OpenBoardProtocol.initCommand!!.toString(Charsets.US_ASCII).trim()
        assertTrue(init.startsWith("begin "))
        assertTrue(init.endsWith(" w"))
        assertEquals(BoardPosition.STARTING, BoardPosition.fromFenPlacement(init.removePrefix("begin ")))
    }

    @Test
    fun `chatter a clock has no business with is ignored`() {
        listOf("ok", "nok", "moved", "resign", "draw_offer", "undo_offer", "msg hello", "err oops")
            .forEach { assertEquals(BoardReport.Ignored, OpenBoardProtocol.decode(message(it))) }
    }

    @Test
    fun `every move is acknowledged, and nothing else is`() {
        // A board that gets no answer stops sending, so this is what keeps the game flowing.
        assertEquals("ok\n", OpenBoardProtocol.replyTo(message("move e2e4"))?.toString(Charsets.US_ASCII))
        assertNull(OpenBoardProtocol.replyTo(message("state rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR")))
        assertNull(OpenBoardProtocol.replyTo(message("ok")))
    }

    @Test
    fun `the board is found by the service it announces, not by its name`() {
        val service = OpenBoardProtocol.ble!!.serviceUuid!!
        assertSame(OpenBoardProtocol, BoardProtocols.forAdvertisedServices(listOf(service)))
        assertNull(BoardProtocols.forAdvertisedServices(listOf(UUID.randomUUID())))
        // Its name says nothing: "OCB" is one board of many that may speak this.
        assertSame(RawCaptureProtocol, BoardProtocols.forDeviceName("OCB"))
    }

    @Test
    fun `a move reaches the clock without any position ever being known`() {
        // The whole difference from the vendor makes in one assertion: no dump, no calibration, no
        // diffing -- the board already knows what was played.
        val tracker = BoardMoveTracker()
        assertEquals(listOf("e2e4"), tracker.onReport(OpenBoardProtocol.decode(message("move e2e4"))))
    }

    @Test
    fun `FEN parsing rejects what is not an eight-by-eight board`() {
        assertNull(BoardPosition.fromFenPlacement("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP"))
        assertNull(BoardPosition.fromFenPlacement("rnbqkbnr/ppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"))
        assertNull(BoardPosition.fromFenPlacement("W:W31-50:B1-20"))
        assertEquals(
            BoardPosition.STARTING,
            BoardPosition.fromFenPlacement("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"),
        )
    }
}
