package com.masterclock.app.logic

import java.util.UUID

/**
 * The open BLE chess-board protocol, as used by ArduinoBleChess, the OpenChessBoard and the
 * Bluetooth-capable Lichess clients.
 *
 * Not a make. Its author looked at DGT, ChessLink and Certabo, found they agree on nothing, and
 * wrote a common one instead; boards announce it on a fixed service UUID. Supporting it therefore
 * supports every board that adopts it rather than one manufacturer's range, which is why it is
 * worth having even though no shop sells "an open chess board".
 *
 * It also sits at a different level entirely from the four vendor protocols. Those report sensors:
 * nibbles of occupancy, RFID chips, one square at a time. This one reports **chess** -- moves as
 * text, positions as FEN. So none of the machinery the others needed applies here: no piece table,
 * no square ordering to mirror, no calibration, and no diffing, because the board has already
 * worked out what was played.
 *
 * That is also what makes it the only one of the five that covers draughts. Moves are passed
 * through as the board wrote them, so a draughts board sending "3228" produces exactly that in the
 * game log and the PDN export -- nothing here reads a move, it only carries it.
 */
object OpenBoardProtocol : BoardProtocol {
    override val name = "Open BLE board"

    /**
     * A fixed service, unlike every vendor make: this protocol is identified by what a board
     * announces rather than guessed from its name, which is the point of standardising it.
     *
     * The characteristics are named from the board's point of view, so its TX is what this app
     * subscribes to and its RX is what this app writes.
     */
    override val ble = BleAddressing(
        serviceUuid = UUID.fromString("f5351050-b2c9-11ec-a0c0-b3bc53b08d33"),
        notifyCharacteristicUuid = UUID.fromString("f535147e-b2c9-11ec-a0c2-8bbd706ec4e6"),
        writeCharacteristicUuid = UUID.fromString("f53513ca-b2c9-11ec-a0c1-639b8957db99"),
    )

    /**
     * The handshake. The central opens every round with `begin` and its position; the board answers
     * `sync` if the pieces match and `unsync` with what it actually sees if they do not.
     *
     * This is not optional, and it is not `get_state`: that one is a feature a board may or may not
     * implement, and a conformant board sent it instead of `begin` simply waits, saying nothing.
     *
     * The position sent is the standard chess opening. Draughts needs `set_variant` before this,
     * which means knowing which game is being played -- see the note on variants below.
     */
    override val initCommand = "$BEGIN $STARTING_FEN w\n".toByteArray(Charsets.US_ASCII)

    override fun decode(payload: ByteArray): BoardReport {
        val text = String(payload, Charsets.US_ASCII).trim()
        val command = text.substringBefore(' ')
        val argument = text.substringAfter(' ', "").trim()

        return when (command) {
            // The board has decided a move was played. No inference needed, and no assumption that
            // the game is chess: whatever notation it uses is carried through untouched.
            MOVE -> if (argument.isEmpty()) BoardReport.Ignored else BoardReport.Moves(listOf(argument))

            // A position, sent on request and whenever the board thinks the two have drifted apart.
            // A draughts board's state is not a chess FEN and is turned away rather than mangled.
            STATE, SYNC, UNSYNC, UNSYNC_SETTABLE ->
                BoardPosition.fromFenPlacement(argument)?.let { BoardReport.Position(it) } ?: BoardReport.Ignored

            // Acknowledgements, resign and draw offers, options: all meaningful to a chess app, none
            // of them a clock's business.
            else -> BoardReport.Ignored
        }
    }

    /**
     * Every move must be acknowledged, or the board stops sending.
     *
     * This is the one protocol here that expects an answer, and the reason [BoardProtocol.replyTo]
     * exists. The answer is always yes: a clock has no rules engine to reject a move with, and
     * refusing one it simply did not understand would leave the board waiting for a correction that
     * is never coming.
     */
    override fun replyTo(payload: ByteArray): ByteArray? {
        val command = String(payload, Charsets.US_ASCII).trim().substringBefore(' ')
        return if (command == MOVE) "$OK\n".toByteArray(Charsets.US_ASCII) else null
    }

    private const val MOVE = "move"
    private const val STATE = "state"
    private const val SYNC = "sync"
    private const val UNSYNC = "unsync"
    private const val UNSYNC_SETTABLE = "unsync_settable"
    private const val BEGIN = "begin"
    private const val OK = "ok"
    private const val STARTING_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
}
