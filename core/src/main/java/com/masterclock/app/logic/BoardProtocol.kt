package com.masterclock.app.logic

import java.util.UUID

/**
 * What one make of electronic board speaks.
 *
 * There is no common protocol between manufacturers: Millennium publishes theirs, Chessnut and
 * Certabo have been reverse-engineered by their users, DGT keeps theirs under NDA. They agree on
 * nothing beyond running over BLE GATT. So everything vendor-specific is confined here, and
 * [BluetoothBoardManager] handles what is genuinely common -- scanning, connecting, subscribing to
 * notifications, and forwarding whatever comes back.
 *
 * Adding a make is therefore one implementation of this interface plus one entry in
 * [BoardProtocols.known]. Nothing else has to change.
 */
/**
 * Where to listen on a board reached over Bluetooth.
 *
 * Kept apart from [BoardProtocol] itself because none of it means anything over USB, where there
 * are no services, characteristics or descriptors -- only endpoints carrying a byte stream. What
 * both transports share is [BoardProtocol.decode], which is also the expensive part to work out per
 * make, so it must not be entangled with either one.
 */
data class BleAddressing(
    /** The GATT service to look for, or null to accept any. A real make names one, which also lets the scan filter on it. */
    val serviceUuid: UUID?,
    /** The characteristic updates arrive on, or null to subscribe to everything that notifies. */
    val notifyCharacteristicUuid: UUID?,
    /** Characteristic to write [BoardProtocol.initCommand] to, if the board needs waking up. */
    val writeCharacteristicUuid: UUID? = null,
)

interface BoardProtocol {
    /** Shown in the board picker. Not translated: these are product names. */
    val name: String

    /** How to address this make over Bluetooth, or null if it has no Bluetooth variant. */
    val ble: BleAddressing?

    /** Sent once the link is up. Several makes stay silent until asked to report. */
    val initCommand: ByteArray? get() = null

    /**
     * What to send once the link is up, for a make whose opening depends on the game being played.
     *
     * Only the open protocol needs this: it is told which game a round is, and refuses to start one
     * it was not told about. The four vendor makes are chess boards and nothing else, so they
     * ignore [gameType] and send their fixed [initCommand].
     */
    fun initCommandFor(gameType: GameType): ByteArray? = initCommand

    /**
     * The same, as the writes it takes: one array a write. One for every make but the open
     * protocol, where a command is a write and two of them in one are a single command the board
     * does not know.
     */
    fun initCommandsFor(gameType: GameType): List<ByteArray> = listOfNotNull(initCommandFor(gameType))

    /**
     * Whether the board takes each write for a whole command, as the open protocol's do. A link then
     * never cuts a command to fit: cut in three, `begin` and its position were one command with half
     * a position and two the board does not know.
     */
    val writesAreCommands: Boolean get() = false

    /** Whether an advertised Bluetooth name looks like this make. */
    fun matchesDeviceName(deviceName: String?): Boolean = false

    /** Whether a USB device's vendor and product ids belong to this make. */
    fun matchesUsbIds(vendorId: Int, productId: Int): Boolean = false

    /**
     * Whether this make has to be shown its pieces before it can be understood.
     *
     * True only where the board reports which individual piece is on a square rather than what kind
     * it is; see [PieceTagCalibration]. The app has to say so, because until the pieces are set up
     * such a board is connected, talking, and producing nothing.
     */
    val needsPieceCalibration: Boolean get() = false

    /**
     * Line speed to ask for over USB serial.
     *
     * Only a make knows its own: DGT's boards run at 9600, others at 115200. The default is the
     * slower one because a device configured faster usually still delivers readable frames, while
     * the reverse produces silence -- and silence is the hardest failure to diagnose here.
     */
    val usbBaudRate: Int get() = 9600

    /**
     * How to find message boundaries, for makes reached over a byte stream.
     *
     * Null means the transport already delivers whole messages, which is true of BLE notifications
     * and HID reports -- Chessnut needs nothing here. A serial line does not: a 67-byte board dump
     * at 9600 baud arrives in several reads, and two short messages can arrive in one.
     */
    val framing: StreamFraming? get() = null

    /**
     * Turn one payload into what it says about the board.
     *
     * Nearly every make reports a position rather than a move -- see [BoardReport.Position], which
     * [BoardMoveTracker] turns into moves. [BoardReport.Moves] exists for the rare payload that
     * really does name a move, and for the raw-capture fallback.
     */
    fun decode(payload: ByteArray): BoardReport

    /**
     * What to write back after receiving [payload], for makes whose protocol expects an answer.
     *
     * Null for the four vendor makes, which talk without waiting. The open BLE protocol does wait:
     * it acknowledges every move, and a board that gets no answer stops sending.
     */
    fun replyTo(payload: ByteArray): ByteArray? = null
}

/** What one payload from a board turned out to be. */
sealed interface BoardReport {
    /**
     * The whole board, as most makes send on every change. [mayBeTurned] is for a board that can
     * stand either way round and does not say which; see [BoardMoveTracker].
     */
    data class Position(val position: BoardPosition, val mayBeTurned: Boolean = false) : BoardReport

    /**
     * One square changed, without restating the rest.
     *
     * DGT's boards work this way once put in update mode: a full dump establishes the position and
     * every change after it is a two-byte message. [BoardMoveTracker] applies these to the position
     * it is already holding.
     */
    data class SquareChanged(val squareIndex: Int, val piece: Char) : BoardReport

    /**
     * What is on each square, identified by tag rather than by type.
     *
     * Certabo's boards read an RFID chip glued under each piece, so what they report is which
     * *individual* piece stands where -- not that it is a knight. Turning that into a position
     * needs a mapping learned once from a known layout; see [PieceTagCalibration]. Null entries are
     * empty squares, and the list is in this app's square order.
     */
    data class TaggedSquares(val tags: List<String?>) : BoardReport

    /** Moves named directly, which a few makes produce. */
    data class Moves(val moves: List<String>) : BoardReport

    /**
     * What an unknown board sent, as hex: something to read on the screen, never a move. It was
     * reported as one, and since this is the fallback for any device the app does not recognise,
     * every keep-alive and battery level from a pair of headphones switched the clock, or went into
     * the game's record as the move "01 24 58".
     */
    data class Raw(val hex: String) : BoardReport

    /** A payload that says nothing about the board: a keep-alive, a battery level, a malformed frame. */
    data object Ignored : BoardReport
}

/**
 * Keeps the previous position so a stream of board reports becomes a stream of moves.
 *
 * One per connection, and deliberately not inside [BoardProtocol]: the protocols are stateless
 * decoders, and every make would otherwise carry its own copy of this.
 */
class BoardMoveTracker(
    /** Milliseconds from any fixed point; a test's own clock, to tell a slide from two moves. */
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    /** When [previousMove] was reported, by [now]. */
    private var previousMoveAt = 0L

    /** The last position a move was recognised from. Only advances when one is. */
    private var settled: BoardPosition? = null

    /** What the board says right now, including halfway through a move. */
    private var current: BoardPosition? = null

    /** Only used by makes that identify pieces by tag; see [BoardReport.TaggedSquares]. */
    private val calibration = PieceTagCalibration()

    /**
     * The rest of a move that reaches the board in two moves' worth of reports, once its first half
     * has been read as a move. A castle is the king and then the rook, each a complete quiet move to
     * the differ. The second was read as the opponent's: they were charged a move, the clock went back
     * to the castling player, and every press after that landed on the wrong side.
     */
    private var secondHalf: String? = null

    /** The square an en passant capture has yet to empty, for the same reason. */
    private var captureToClear: Int? = null

    /** The last move reported, which a king completing a castle names. */
    private var previousMove: String? = null

    /** Whether this board stands turned half way round; see [BoardReport.Position.mayBeTurned]. */
    private var turned = false

    /**
     * The board as it last reported itself, before any turning, and whether it may be turned. A make
     * that names one square at a time names it in the board's own numbering, so its changes are
     * applied here and the result read as a whole position would be: applied to the position already
     * turned, a turned board's squares landed on the wrong side, and the pieces set up again were
     * never seen as a start, a whole position being the only thing that asked.
     */
    private var seen: BoardPosition? = null
    private var seenMayBeTurned = false

    /** The position before [previousMove], for a piece that goes on from where that move ended. */
    private var beforePrevious: BoardPosition? = null

    /** Whether a tag-reading board still needs the pieces set up before it can be understood. */
    val needsCalibration: Boolean get() = !calibration.isCalibrated

    fun onReport(report: BoardReport): List<String> = when (report) {
        is BoardReport.Moves -> report.moves
        is BoardReport.Raw, is BoardReport.Ignored -> emptyList()
        is BoardReport.Position -> {
            seen = report.position
            seenMayBeTurned = report.mayBeTurned
            applyPosition(upright(report))
        }
        is BoardReport.TaggedSquares ->
            calibration.identify(report.tags)?.let { applyPosition(it) } ?: emptyList()
        is BoardReport.SquareChanged -> {
            // Without a full dump first there is nothing to apply this to: a single square says
            // where one piece is, not where the other thirty-one are.
            val base = seen
            if (base == null) {
                emptyList()
            } else {
                val squares = base.squares.toCharArray()
                squares[report.squareIndex] = report.piece
                val next = BoardPosition(String(squares))
                seen = next
                applyPosition(upright(BoardReport.Position(next, seenMayBeTurned)))
            }
        }
    }

    /**
     * The position as the players see it. A board that may be turned shows which way round it stands
     * when the pieces are set up, the one position that cannot be read both ways. Until then it is
     * taken as it comes. What was settled before was read the other way round, or was only a board
     * being set up, so it goes: a baseline nothing can be compared with holds every move back.
     */
    private fun upright(report: BoardReport.Position): BoardPosition {
        val seen = report.position
        if (!report.mayBeTurned) return seen
        val turnedNow = when (BoardPosition.STARTING) {
            seen.turned() -> true
            seen -> false
            else -> turned
        }
        if (turnedNow != turned) {
            turned = turnedNow
            settled = null
            current = null
        }
        return if (turned) seen.turned() else seen
    }

    /**
     * Compares against the last *settled* position, not the last report.
     *
     * This is the whole point of holding two positions. A move reaches the board in pieces: the
     * piece is lifted, and only later put down -- and on a DGT in update mode those are two
     * separate messages, one square each. Comparing each report with the one before it would see a
     * square empty, then a square fill, and recognise neither as a move. Comparing with the last
     * settled position instead lets the halves add up, and the baseline only moves on once a whole
     * move has been recognised.
     *
     * A consequence worth knowing: if a player does something the differ cannot name, the baseline
     * stays where it is and the next comparison spans both changes. That is why [BoardDiffer]
     * refuses anything it does not recognise rather than guessing -- a wrong guess would re-baseline
     * on a position that never existed.
     */
    private fun applyPosition(position: BoardPosition): List<String> {
        current = position
        val from = settled
        // The first report only establishes where the pieces are; there is no move in it, and it
        // usually describes a position set up before the app was watching.
        if (from == null) {
            settled = position
            return emptyList()
        }
        // The pieces set up again. Nothing else starts a new game on a board: a tracker kept the last
        // game's final position, every report differed from it by more than one move, and no move was
        // recognised again until the board was reconnected, with nothing on screen to say so. The
        // same for a board connected empty, or half set up.
        // From further than one move away, that is. One move from the start, the pieces back on
        // their squares are that move taken back, or a knight gone home, and both are moves: said
        // nothing of, a first move taken back left the clock with the other player and every move
        // after it was charged to the wrong one, and 1.Nf3 Nf6 2.Ng1 Ng8 lost its last move.
        if (position == BoardPosition.STARTING && from != position && BoardDiffer.moveBetween(from, position) == null) {
            settled = position
            secondHalf = null
            captureToClear = null
            previousMove = null
            beforePrevious = null
            return emptyList()
        }
        val ghost = captureToClear
        if (ghost != null && position[ghost] == BoardPosition.EMPTY && onlyDiffersAt(from, position, ghost)) {
            settled = position
            captureToClear = null
            return emptyList()
        }
        // A pawn put down on its last rank and then changed for the piece it became: the move is
        // already told, as the pawn's, and this is the same square holding something else. It was
        // nothing the differ could name, the baseline stayed on a pawn that was no longer there,
        // and no move after a promotion made that way was read until the pieces were set up again.
        if (isPromotionCompleted(from, position)) {
            settled = position
            return emptyList()
        }
        val move = BoardDiffer.moveBetween(from, position) ?: return emptyList()
        settled = position
        if (move == secondHalf) {
            secondHalf = null
            // The king after its rook is passed on, for the rook's move to be renamed after it; see
            // BoardDiffer.completesCastle. The rook after its king is the castle already reported.
            return if (BoardDiffer.completesCastle(previousMove, move)) listOf(move) else emptyList()
        }
        // The rook of a castle resting on a square on its way to its own: still the castle's, and
        // the rest of its way is what is waited for now. It was a move of its own, and passed the turn.
        val awaited = secondHalf
        if (awaited != null && BoardDiffer.isOnTheWay(move, awaited)) {
            secondHalf = move.substring(2, 4) + awaited.substring(2, 4)
            return emptyList()
        }
        // A piece going on from where its last move ended is the same move, slid across a square;
        // see BoardDiffer.slideOf. Both stretches are still reported -- the clock joins them, having
        // the time to tell a slide by -- but what the move is, is asked of the whole: a king slid from
        // e1 through f1 to g1 castles, and neither e1f1 nor f1g1 said so, so the rook that followed
        // was a move, the other player's.
        // Within the time the clock itself joins them in, and no later: see SLIDE_WINDOW_MS. Joined
        // here without that limit, a king that rested a second on f1 was two moves to the clock and
        // a castle here -- the rook was swallowed, and the clock left with the player who castled.
        val slidFrom = beforePrevious
        val at = now()
        val whole = BoardDiffer.slideOf(previousMove, move).takeIf { at - previousMoveAt <= SLIDE_WINDOW_MS }
        if (whole != null && slidFrom != null) {
            previousMove = whole
            secondHalf = BoardDiffer.secondHalfOf(whole, slidFrom)
        } else {
            beforePrevious = from
            previousMove = move
            secondHalf = BoardDiffer.secondHalfOf(move, from)
        }
        previousMoveAt = at
        captureToClear = BoardDiffer.capturedEnPassantBy(move, from)
        return listOf(move)
    }

    private fun isPromotionCompleted(before: BoardPosition, after: BoardPosition): Boolean {
        val square = (0 until BoardPosition.SQUARE_COUNT).singleOrNull { before[it] != after[it] } ?: return false
        val pawn = before[square]
        val piece = after[square]
        val lastRank = if (pawn == 'P') square < 8 else if (pawn == 'p') square >= BoardPosition.SQUARE_COUNT - 8 else false
        if (!lastRank || piece == BoardPosition.EMPTY || piece.equals('p', ignoreCase = true) || piece.equals('k', ignoreCase = true)) return false
        return piece == BoardPosition.UNKNOWN_PIECE || piece.isUpperCase() == pawn.isUpperCase()
    }

    private fun onlyDiffersAt(a: BoardPosition, b: BoardPosition, square: Int): Boolean =
        (0 until BoardPosition.SQUARE_COUNT).all { it == square || a[it] == b[it] }

    fun reset() {
        settled = null
        current = null
        turned = false
        seen = null
        seenMayBeTurned = false
        secondHalf = null
        captureToClear = null
        previousMove = null
        beforePrevious = null
        // A different set of pieces may be on the next board, so the learned tags go too.
        calibration.reset()
    }
}

/**
 * The fallback used until a make is implemented: subscribe to everything that notifies, and show
 * each payload as hex.
 *
 * This is deliberately not a pretend board. It is the tool for adding the first real one -- connect
 * an unknown board, move a piece, and read off which characteristic spoke and what it said. Without
 * it, supporting a new make means owning both the board and a separate BLE sniffer.
 */
object RawCaptureProtocol : BoardProtocol {
    override val name = "Raw capture (unknown board)"

    /** Both null: accept any service, and subscribe to every characteristic that notifies. */
    override val ble = BleAddressing(serviceUuid = null, notifyCharacteristicUuid = null)

    override fun decode(payload: ByteArray): BoardReport =
        if (payload.isEmpty()) BoardReport.Ignored else BoardReport.Raw(payload.toHexString())
}

/** Lowercase hex, space-separated, as a BLE trace would show it. */
fun ByteArray.toHexString(): String = joinToString(" ") { "%02x".format(it) }

object BoardProtocols {
    /**
     * Every make the app can talk to, most specific first; [RawCaptureProtocol] must stay last
     * because it matches nothing of its own: it is what the lookups below fall back to.
     */
    val known: List<BoardProtocol> = listOf(
        OpenBoardProtocol, ChessnutProtocol, DgtProtocol, MillenniumProtocol, CertaboProtocol,
        RawCaptureProtocol,
    )

    /**
     * The protocol whose service the connected board actually exposes, if any.
     *
     * Checked before names, because a service a board announces is a fact and a name is a guess.
     * Only the open protocol names one; the vendor makes are found by name because their services
     * are unknown or unpublished.
     */
    fun forAdvertisedServices(services: Collection<java.util.UUID>): BoardProtocol? =
        known.firstOrNull { it.ble?.serviceUuid != null && it.ble?.serviceUuid in services }

    /**
     * The protocol to use for a board advertising [deviceName], or [RawCaptureProtocol] when no
     * implementation claims it.
     */
    fun forDeviceName(deviceName: String?): BoardProtocol =
        known.firstOrNull { it !== RawCaptureProtocol && it.matchesDeviceName(deviceName) }
            ?: RawCaptureProtocol

    /** The protocol for a USB board with these ids, or [RawCaptureProtocol] when none claims it. */
    fun forUsbIds(vendorId: Int, productId: Int): BoardProtocol =
        known.firstOrNull { it !== RawCaptureProtocol && it.matchesUsbIds(vendorId, productId) }
            ?: RawCaptureProtocol

    /**
     * The protocol for a plugged-in board, by its ids first and its names second.
     *
     * Ids are the better evidence and are tried first. But a board built around an off-the-shelf
     * serial chip advertises that chip's vendor and product, not its own -- Certabo is exactly this
     * -- so a make that cannot claim any id would otherwise be unreachable over a cable while
     * working perfectly over Bluetooth, where the advertised name is what gets matched.
     *
     * [names] is what the device calls itself: its product name, its manufacturer, in that order of
     * usefulness. Nulls are expected; not every device fills them in.
     */
    fun forUsbDevice(vendorId: Int, productId: Int, vararg names: String?): BoardProtocol {
        forUsbIds(vendorId, productId).let { if (it !== RawCaptureProtocol) return it }
        return names.filterNotNull().firstNotNullOfOrNull { name ->
            known.firstOrNull { it !== RawCaptureProtocol && it.matchesDeviceName(name) }
        } ?: RawCaptureProtocol
    }
}

/**
 * Which way a board is attached. The pairing and decoding above are the same for all of them.
 *
 * [BLUETOOTH_SERIAL] is not a variant of [BLUETOOTH]: a Bluetooth Classic board offers a serial
 * port over RFCOMM and has no GATT services at all, so nothing about reaching it is shared with a
 * BLE peripheral. DGT's e-Boards are the reason it exists.
 */
enum class BoardTransportKind { BLUETOOTH, BLUETOOTH_SERIAL, USB }

/**
 * A board the user could connect to, named the same way whichever transport found it, so the
 * picker does not need one list per transport.
 */
data class BoardCandidate(
    /** Stable within a transport: a MAC address over Bluetooth, a device name over USB. */
    val id: String,
    val label: String,
    val detail: String,
    val kind: BoardTransportKind,
)
