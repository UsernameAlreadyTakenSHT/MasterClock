package com.masterclock.app.logic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A board of each make the app knows, without the board: the bytes one would send as a game is
 * played on it, written the way that make writes them.
 *
 * Nothing about a connected board could be tried without owning it -- five makes, three links, and
 * an emulator that has no Bluetooth. What is simulated is the board's side of the conversation, so
 * everything after the radio is exercised as it is with the real thing: putting a stream back into
 * messages, decoding them, telling a move from a piece in the air, and passing the turn.
 *
 * The radio and the USB stack themselves are not: this stands in for the wire, not for what
 * carries it.
 *
 * Each encoder here is the inverse of that make's decoder and nothing more. It proves the app reads
 * what the app believes the make sends; a real board that sends something else is what
 * [BoardFrameLog] is for, and its log plays back here unchanged.
 */
object SimulatedBoard {

    /** The makes that can be simulated, which is every one the app decodes. */
    val makes: List<BoardProtocol> =
        listOf(MillenniumProtocol, ChessnutProtocol, CertaboProtocol, DgtProtocol, OpenBoardProtocol)

    /**
     * Short, and with what a board makes hard: a capture, both castles and an en passant. No
     * promotion, which on a real board needs a second queen fetched from the box.
     */
    val DEMO_GAME = listOf(
        "e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "f8c5", "e1g1", "g8f6",
        "d2d4", "e5d4", "e4e5", "d7d5", "e5d6", "c5d6", "f3d4", "e8g8",
    )

    /** One piece of wood: its kind, and which one it is, as a tag-reading board tells them apart. */
    private data class Piece(val id: Int, val kind: Char)

    private fun square(name: String): Int = ('8' - name[1]) * 8 + (name[0] - 'a')

    /**
     * What stands on the board at each moment a board would report: the pieces set up, then every
     * lift and every put-down of every move, in the order a hand makes them. A captured piece comes
     * off before the capturing one lands; a pawn taken en passant, after; a castle is the king and
     * then the rook.
     */
    private fun momentsOf(moves: List<String>): List<List<Piece?>> {
        val board = MutableList<Piece?>(BoardPosition.SQUARE_COUNT) { index ->
            BoardPosition.STARTING[index].takeIf { it != BoardPosition.EMPTY }?.let { Piece(index + 1, it) }
        }
        val moments = mutableListOf(board.toList())
        fun carry(from: Int, to: Int) {
            val piece = board[from]
            board[from] = null
            moments += board.toList()
            board[to] = piece
            moments += board.toList()
        }
        fun takeOff(at: Int) {
            board[at] = null
            moments += board.toList()
        }
        for (move in moves) {
            require(move.length == 4) { "A simulated board plays no promotion: $move" }
            val from = square(move.substring(0, 2))
            val to = square(move.substring(2, 4))
            val mover = requireNotNull(board[from]) { "Nothing stands on ${move.substring(0, 2)}" }
            val king = mover.kind.equals('k', ignoreCase = true)
            val pawn = mover.kind.equals('p', ignoreCase = true)
            val rank = from / 8 * 8
            when {
                king && to - from == 2 -> { carry(from, to); carry(rank + 7, rank + 5) }
                king && from - to == 2 -> { carry(from, to); carry(rank, rank + 3) }
                pawn && from % 8 != to % 8 && board[to] == null -> { carry(from, to); takeOff(rank + to % 8) }
                board[to] != null -> { takeOff(to); carry(from, to) }
                else -> carry(from, to)
            }
        }
        return moments
    }

    /**
     * The messages a board of this make sends over [moves], one array a message. A make that sends
     * its whole position sends it at every lift and every put-down; DGT sends it once and then the
     * squares that change; the open protocol names each move and says nothing of a piece in the air.
     */
    fun framesOf(protocol: BoardProtocol, moves: List<String> = DEMO_GAME): List<ByteArray> {
        if (protocol === OpenBoardProtocol) {
            momentsOf(moves) // for what it refuses
            return moves.map { "move $it\n".toByteArray(Charsets.US_ASCII) }
        }
        val moments = momentsOf(moves)
        return when (protocol) {
            MillenniumProtocol -> moments.map(::millennium)
            ChessnutProtocol -> moments.map(::chessnut)
            CertaboProtocol -> moments.map(::certabo)
            DgtProtocol -> listOf(dgtDump(moments.first())) + moments.zipWithNext().flatMap { (before, after) ->
                (0 until BoardPosition.SQUARE_COUNT).filter { before[it] != after[it] }.map { dgtUpdate(it, after[it]) }
            }
            else -> throw IllegalArgumentException("No simulated board for ${protocol.name}")
        }
    }

    /**
     * One position as a make that sends its whole board writes it -- DGT's is its dump -- for what a
     * game played cleanly never shows: a piece put back, slid, knocked over, a pawn become a queen.
     * Not for Certabo, whose frame names pieces of wood and not kinds, nor for the open protocol,
     * which names moves.
     */
    fun frameOf(protocol: BoardProtocol, position: BoardPosition): ByteArray {
        val board = List(BoardPosition.SQUARE_COUNT) { index ->
            position[index].takeIf { it != BoardPosition.EMPTY }?.let { Piece(index + 1, it) }
        }
        return when (protocol) {
            MillenniumProtocol -> millennium(board)
            ChessnutProtocol -> chessnut(board)
            DgtProtocol -> dgtDump(board)
            else -> throw IllegalArgumentException("${protocol.name} does not send a position")
        }
    }

    /** DGT's message for one square changing, [piece] being what stands there now. */
    fun dgtSquare(squareIndex: Int, piece: Char): ByteArray =
        dgtUpdate(squareIndex, piece.takeIf { it != BoardPosition.EMPTY }?.let { Piece(0, it) })

    /**
     * A message as the link delivers it. A serial link hands over a stream in slices that fall
     * anywhere, and a make that says how its messages are delimited is given them so: that is the
     * half of the work a whole message at a time would never try.
     */
    fun chunksOf(protocol: BoardProtocol, frame: ByteArray): List<ByteArray> =
        if (protocol.framing == null) listOf(frame)
        else (frame.indices step SLICE_BYTES).map { frame.copyOfRange(it, minOf(it + SLICE_BYTES, frame.size)) }

    private const val SLICE_BYTES = 20

    // 's', the 64 squares from h1 leftwards and upwards, two hex digits of XOR, each byte with odd parity.
    private fun millennium(board: List<Piece?>): ByteArray {
        val body = CharArray(BoardPosition.SQUARE_COUNT)
        for (reply in body.indices) {
            body[reply] = board[(7 - reply / 8) * 8 + (7 - reply % 8)]?.kind ?: '.'
        }
        val text = "s" + String(body)
        val checksum = text.fold(0) { acc, c -> acc xor c.code } and 0xFF
        return (text + "%02X".format(checksum)).map { c ->
            val value = c.code and 0x7F
            if (Integer.bitCount(value) % 2 == 0) (value or 0x80).toByte() else value.toByte()
        }.toByteArray()
    }

    // 01 24, then a nibble a square from h8, low nibble first, and four bytes of uptime.
    private fun chessnut(board: List<Piece?>): ByteArray {
        val codes = charArrayOf(BoardPosition.EMPTY, 'q', 'k', 'b', 'p', 'n', 'R', 'P', 'r', 'B', 'N', 'Q', 'K')
        val bytes = ByteArray(38)
        bytes[0] = 0x01
        bytes[1] = 0x24
        for (nibble in 0 until BoardPosition.SQUARE_COUNT) {
            val kind = board[nibble / 8 * 8 + (7 - nibble % 8)]?.kind ?: BoardPosition.EMPTY
            val code = codes.indexOf(kind)
            val at = 2 + nibble / 2
            bytes[at] = (bytes[at].toInt() or if (nibble % 2 == 0) code else code shl 4).toByte()
        }
        return bytes
    }

    // A line of 320 numbers: the five bytes of the chip under each square, zeros where nothing stands.
    private fun certabo(board: List<Piece?>): ByteArray =
        (board.joinToString(" ") { piece ->
            if (piece == null) "0 0 0 0 0" else "12 ${piece.id} 0 ${piece.kind.code} 77"
        } + "\r\n").toByteArray(Charsets.US_ASCII)

    private fun dgtCode(piece: Piece?): Byte = "PRNBKQprnbkq".indexOf(piece?.kind ?: ' ').plus(1).toByte()

    private fun dgtMessage(id: Int, body: ByteArray): ByteArray {
        val length = body.size + 3
        return byteArrayOf(id.toByte(), (length shr 7 and 0x7F).toByte(), (length and 0x7F).toByte()) + body
    }

    private fun dgtDump(board: List<Piece?>): ByteArray = dgtMessage(0x86, ByteArray(BoardPosition.SQUARE_COUNT) { dgtCode(board[it]) })

    private fun dgtUpdate(squareIndex: Int, piece: Piece?): ByteArray = dgtMessage(0x8E, byteArrayOf(squareIndex.toByte(), dgtCode(piece)))
}

/**
 * The app's side of one connection, from bytes to moves: what each of the three managers does with
 * what its link hands it, without a link. One a connection, as their assembler and tracker are.
 */
class BoardListener(private val protocol: BoardProtocol) {
    private val assembler = protocol.framing?.let { StreamAssembler(it) }
    private val tracker = BoardMoveTracker()

    /** The moves [chunk] completes, most often none. */
    fun hear(chunk: ByteArray): List<String> =
        (assembler?.offer(chunk) ?: listOf(chunk)).flatMap { tracker.onReport(protocol.decode(it)) }
}

/**
 * A simulated board as the screen sees a board: something to connect to, that is then connected and
 * gives moves. It plays [SimulatedBoard]'s game as a make would send it, or a [BoardFrameLog] that a
 * real board wrote, at the pace it was written.
 */
class SimulatedBoardManager {

    /** As on the real links: what this one hands over is written down too. */
    var frameLog: BoardFrameLog? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _lastMove = MutableStateFlow<String?>(null)
    val lastMove: StateFlow<String?> = _lastMove.asStateFlow()

    /** Plays the demonstration game as a board of this make sends it, a message every [frameEveryMs]. */
    fun connect(protocol: BoardProtocol, frameEveryMs: Long = FRAME_EVERY_MS, onMoveReceived: (String) -> Unit) {
        val chunks = SimulatedBoard.framesOf(protocol).flatMap { frame ->
            SimulatedBoard.chunksOf(protocol, frame).mapIndexed { index, chunk -> (if (index == 0) frameEveryMs else SLICE_EVERY_MS) to chunk }
        }
        play(protocol, chunks, onMoveReceived)
    }

    /**
     * Plays back what a board said in a log, gaps included up to [MAX_REPLAY_GAP_MS]: nobody waits
     * through the minutes a player thought. False when the log holds nothing from a make the app
     * decodes, which is what a log of an unknown board is.
     */
    fun replay(frames: List<BoardFrameLog.Frame>, onMoveReceived: (String) -> Unit): Boolean {
        val heard = frames.filter { it.direction == BoardFrameLog.Direction.FROM_BOARD }
        val protocol = heard.firstNotNullOfOrNull { frame ->
            SimulatedBoard.makes.firstOrNull { BoardFrameLog.nameInLog(it.name) == frame.protocol }
        } ?: return false
        var previous = heard.first().atMs
        val chunks = heard.filter { BoardFrameLog.nameInLog(protocol.name) == it.protocol }.map { frame ->
            val wait = (frame.atMs - previous).coerceIn(0, MAX_REPLAY_GAP_MS)
            previous = frame.atMs
            wait to BoardFrameLog.bytesOf(frame)
        }
        play(protocol, chunks, onMoveReceived)
        return true
    }

    private fun play(protocol: BoardProtocol, chunks: List<Pair<Long, ByteArray>>, onMoveReceived: (String) -> Unit) {
        job?.cancel()
        _lastMove.value = null
        _connectionState.value = ConnectionState.Connecting
        job = scope.launch {
            val listener = BoardListener(protocol)
            delay(CONNECT_MS)
            _connectionState.value = ConnectionState.Connected("Simulated ${protocol.name}")
            for ((wait, chunk) in chunks) {
                delay(wait)
                frameLog?.record(BoardFrameLog.Direction.FROM_BOARD, "simulated", protocol.name, chunk)
                val moves = listener.hear(chunk)
                // On the main thread, where the real links hand their moves over.
                if (moves.isNotEmpty()) withContext(Dispatchers.Main) {
                    moves.forEach { _lastMove.value = it; onMoveReceived(it) }
                }
            }
            // And it stays connected, as a board does once nobody moves.
        }
    }

    fun disconnect() {
        job?.cancel()
        job = null
        _connectionState.value = ConnectionState.Idle
    }

    fun release() {
        disconnect()
        scope.cancel()
    }

    companion object {
        /** Slow enough to watch the clock answer each move, a lift and a put-down being two messages. */
        const val FRAME_EVERY_MS = 700L
        const val SLICE_EVERY_MS = 20L
        const val CONNECT_MS = 300L
        const val MAX_REPLAY_GAP_MS = 2_000L
    }
}
