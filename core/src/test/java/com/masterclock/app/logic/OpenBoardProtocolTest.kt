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
    fun `draughts men and dames are understood by the FEN reader`() {
        // Not used by this protocol, which never turns a position into a move, but the reader has
        // to know these symbols for any make that reports draughts positions and no moves.
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
    fun `a position never becomes a move on this protocol`() {
        // The board names its own moves, so inferring one from a position as well would report the
        // same move twice -- once through "move", once from diffing the position it left behind.
        val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        assertEquals(BoardReport.Ignored, OpenBoardProtocol.decode(message("state $fen")))
        assertEquals(BoardReport.Ignored, OpenBoardProtocol.decode(message("sync $fen")))
    }

    @Test
    fun `a move is reported once, even when a position follows it`() {
        val tracker = BoardMoveTracker()
        val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPP1PPP/RNBQKBNR"

        assertEquals(listOf("e2e4"), tracker.onReport(OpenBoardProtocol.decode(message("move e2e4"))))
        assertEquals(emptyList<String>(), tracker.onReport(OpenBoardProtocol.decode(message("sync $fen"))))
    }

    @Test
    fun `board size makes no difference to a move`() {
        // International draughts is played on ten by ten. Since only "move" is read, and that is
        // just text, the board's size never comes into it.
        assertEquals(BoardReport.Moves(listOf("c3d4")), OpenBoardProtocol.decode(message("move c3d4")))
        assertEquals(
            BoardReport.Ignored,
            OpenBoardProtocol.decode(message("sync 1m1m1m1m1m/m1m1m1m1m1/10/10/10/10/10/10/1M1M1M1M1M/M1M1M1M1M1")),
        )
    }

    @Test
    fun `a draughts round announces its variant before it begins`() {
        // Order is the whole point: the spec says set_variant may only come before begin, and a
        // board told nothing plays chess -- then disagrees with every move that follows.
        val lines = OpenBoardProtocol.initCommandFor(GameType.DRAUGHTS)
            .toString(Charsets.US_ASCII).trim().lines()

        assertEquals(2, lines.size)
        assertEquals("set_variant draughts_standard", lines[0])
        assertTrue(lines[1].startsWith("begin "))
    }

    @Test
    fun `the draughts position is ten by ten with the middle clear`() {
        val fen = OpenBoardProtocol.initCommandFor(GameType.DRAUGHTS)
            .toString(Charsets.US_ASCII).trim().lines()[1].removePrefix("begin ").removeSuffix(" w")
        val ranks = fen.split("/")

        assertEquals(10, ranks.size)
        // Twenty men a side, and the two middle ranks empty, which is what makes it a start.
        assertEquals(20, fen.count { it == 'm' })
        assertEquals(20, fen.count { it == 'M' })
        assertEquals(listOf("10", "10"), ranks.subList(4, 6))
    }

    @Test
    fun `a game of chess says nothing about variants`() {
        // A board assumes standard when never told, so naming it would be a round trip for nothing.
        val chess = OpenBoardProtocol.initCommandFor(GameType.CHESS).toString(Charsets.US_ASCII)
        assertFalse(chess.contains("set_variant"))
        assertTrue(chess.trim().startsWith("begin "))
    }

    @Test
    fun `shogi falls back to chess rather than inventing a variant`() {
        // It cannot be picked any more, precisely because no board plays it, and the protocol has
        // no name for it either.
        assertArrayEquals(
            OpenBoardProtocol.initCommandFor(GameType.CHESS),
            OpenBoardProtocol.initCommandFor(GameType.SHOGI),
        )
    }

    @Test
    fun `the vendor makes ignore the game entirely`() {
        // They are chess boards and nothing else; their opening does not vary.
        GameType.entries.forEach { game ->
            assertArrayEquals(ChessnutProtocol.initCommand, ChessnutProtocol.initCommandFor(game))
            assertArrayEquals(DgtProtocol.initCommand, DgtProtocol.initCommandFor(game))
        }
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
