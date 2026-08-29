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
     * The handshake for a game of chess, which is what a board assumes when told nothing.
     *
     * `begin` is not optional and it is not `get_state`: that one is a feature a board may or may
     * not implement, and a conformant board sent it instead of `begin` simply waits, saying nothing.
     */
    override val initCommand = "$BEGIN $CHESS_FEN w\n".toByteArray(Charsets.US_ASCII)

    /**
     * The opening for a given game.
     *
     * Chess sends `begin` alone: the spec says a board assumes `standard` when no variant was set,
     * so naming it would add a round trip for nothing. Draughts must announce itself first --
     * `set_variant` before `begin`, in that order and only before it -- or the board starts a game
     * of chess and disagrees with every move that follows.
     *
     * International draughts is the variant chosen, being what "draughts" means to most players and
     * the default the board note already takes. The eight-by-eight games -- Russian, Brazilian,
     * English -- differ in their rules as well as their size, so picking between them needs a
     * setting this app does not have yet, and guessing would be worse than the ten-by-ten default.
     *
     * Shogi cannot reach here: it is withheld from the game picker precisely because no board
     * plays it, and the protocol has no variant for it either. It falls back to chess rather than
     * inventing something.
     */
    override fun initCommandFor(gameType: GameType): ByteArray = when (gameType) {
        GameType.DRAUGHTS ->
            "$SET_VARIANT $DRAUGHTS_VARIANT\n$BEGIN $DRAUGHTS_FEN w\n".toByteArray(Charsets.US_ASCII)
        else -> initCommand
    }

    override fun decode(payload: ByteArray): BoardReport {
        val text = String(payload, Charsets.US_ASCII).trim()
        val command = text.substringBefore(' ')
        val argument = text.substringAfter(' ', "").trim()

        return when (command) {
            // The board has decided a move was played. No inference needed, and no assumption that
            // the game is chess: whatever notation it uses is carried through untouched.
            MOVE -> if (argument.isEmpty()) BoardReport.Ignored else BoardReport.Moves(listOf(argument))

            // Positions are not turned into moves here, and that is deliberate.
            //
            // A board that names its own moves must not also have them inferred: the tracker would
            // diff this position against the last settled one and report a move that has already
            // been reported through `move`, counting a single move twice. Inference exists for the
            // vendor makes, which say nothing else.
            //
            // It also settles the board-size question. Whether a draughts board is eight squares
            // wide or ten changes nothing, because no position is read either way -- only `move` is,
            // and that is just text.
            STATE, SYNC, UNSYNC, UNSYNC_SETTABLE -> BoardReport.Ignored

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
    private const val SET_VARIANT = "set_variant"
    private const val OK = "ok"

    private const val CHESS_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"

    /** International draughts: ten by ten, twenty men a side, the middle two ranks clear. */
    private const val DRAUGHTS_VARIANT = "draughts_standard"
    private const val DRAUGHTS_FEN =
        "1m1m1m1m1m/m1m1m1m1m1/1m1m1m1m1m/m1m1m1m1m1/10/10/1M1M1M1M1M/M1M1M1M1M1/1M1M1M1M1M/M1M1M1M1M1"
}
