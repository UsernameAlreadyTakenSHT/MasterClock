package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A game played on a simulated board of each make comes out of the app's own chain -- stream put
 * back into messages, messages decoded, moves told from pieces in the air -- as the game played.
 */
class SimulatedBoardTest {

    private fun movesHeard(protocol: BoardProtocol, chunks: List<ByteArray>): List<String> {
        val listener = BoardListener(protocol)
        return chunks.flatMap { listener.hear(it) }
    }

    private fun chunksOf(protocol: BoardProtocol): List<ByteArray> =
        SimulatedBoard.framesOf(protocol).flatMap { SimulatedBoard.chunksOf(protocol, it) }

    @Test
    fun `every make the app decodes can be simulated`() {
        // All but the raw capture, which decodes nothing: there is no game to hear in it.
        assertEquals((BoardProtocols.known - RawCaptureProtocol).map { it.name }.toSet(), SimulatedBoard.makes.map { it.name }.toSet())
    }

    @Test
    fun `the game played on each make is the game heard`() {
        // A castle is the king's move alone, and an en passant the capturing pawn's: the rook that
        // follows and the pawn that comes off are heard and are not moves.
        SimulatedBoard.makes.forEach { make ->
            assertEquals(make.name, SimulatedBoard.DEMO_GAME, movesHeard(make, chunksOf(make)))
        }
    }

    @Test
    fun `a make with a stream is handed slices, and a whole message says nothing new`() {
        listOf(MillenniumProtocol, CertaboProtocol, DgtProtocol).forEach { make ->
            val frames = SimulatedBoard.framesOf(make)
            assertTrue(make.name, frames.any { SimulatedBoard.chunksOf(make, it).size > 1 })
            // Slices or whole messages, the same game.
            assertEquals(make.name, SimulatedBoard.DEMO_GAME, movesHeard(make, frames))
        }
        // Chessnut frames nothing: a message cut in two would be two messages that mean nothing.
        assertTrue(SimulatedBoard.framesOf(ChessnutProtocol).all { SimulatedBoard.chunksOf(ChessnutProtocol, it).size == 1 })
    }

    @Test
    fun `each make speaks as that make does`() {
        // The first thing a board says is where the pieces are, and the app reads the start position in it.
        assertEquals(
            BoardReport.Position(BoardPosition.STARTING, mayBeTurned = true),
            MillenniumProtocol.decode(SimulatedBoard.framesOf(MillenniumProtocol).first()),
        )
        assertEquals(BoardReport.Position(BoardPosition.STARTING), ChessnutProtocol.decode(SimulatedBoard.framesOf(ChessnutProtocol).first()))
        assertEquals(
            BoardReport.Position(BoardPosition.STARTING, mayBeTurned = true),
            DgtProtocol.decode(SimulatedBoard.framesOf(DgtProtocol).first()),
        )
        // DGT then names one square at a time: e2 empties, before e4 fills.
        assertEquals(BoardReport.SquareChanged(52, BoardPosition.EMPTY), DgtProtocol.decode(SimulatedBoard.framesOf(DgtProtocol)[1]))
        assertEquals(BoardReport.SquareChanged(36, 'P'), DgtProtocol.decode(SimulatedBoard.framesOf(DgtProtocol)[2]))
        // Certabo names pieces of wood, not kinds: thirty-two tags, all different.
        val tags = (CertaboProtocol.decode(SimulatedBoard.framesOf(CertaboProtocol).first()) as BoardReport.TaggedSquares).tags
        assertEquals(32, tags.filterNotNull().toSet().size)
        assertEquals(BoardReport.Moves(listOf("e2e4")), OpenBoardProtocol.decode(SimulatedBoard.framesOf(OpenBoardProtocol).first()))
    }

    @Test
    fun `a log of a board plays back as the game it recorded`() {
        var clock = 0L
        val log = BoardFrameLog(capacity = 5_000) { clock }
        chunksOf(DgtProtocol).forEach {
            log.record(BoardFrameLog.Direction.FROM_BOARD, "USB", DgtProtocol.name, it)
            clock += 50
        }
        // What the app wrote to the board is in the file and is not something the board said.
        log.record(BoardFrameLog.Direction.TO_BOARD, "USB", DgtProtocol.name, byteArrayOf(0x40, 0x42, 0x44))
        val frames = BoardFrameLog.parse(log.text("test"))
        val heard = frames.filter { it.direction == BoardFrameLog.Direction.FROM_BOARD }.map { BoardFrameLog.bytesOf(it) }
        assertEquals(SimulatedBoard.DEMO_GAME, movesHeard(DgtProtocol, heard))
    }

    @Test
    fun `a log with nothing from a known make is not played`() {
        val unknown = BoardFrameLog.parse("0.100 < BLE Raw_capture 01 02 03\n0.200 > BLE Millennium_ChessLink 53")
        assertEquals(1, unknown.count { it.direction == BoardFrameLog.Direction.FROM_BOARD })
        assertFalse(SimulatedBoardManager().replay(unknown) {})
        assertFalse(SimulatedBoardManager().replay(emptyList()) {})
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a promotion is refused rather than played wrongly`() {
        SimulatedBoard.framesOf(MillenniumProtocol, listOf("e2e4q"))
    }
}
