package com.masterclock.app.logic

/**
 * What stands on the 64 squares, as every electronic board reports it.
 *
 * None of them send moves. Chessnut pushes 32 bytes holding two squares per nibble, Certabo sends
 * the RFID id sitting on each square, DGT reports piece codes -- all of them describe a position,
 * and the move has to be worked out by comparing one report with the last. That comparison is
 * identical whichever make sent it, so it lives here rather than in each [BoardProtocol].
 *
 * [squares] is 64 characters in FEN order: index 0 is a8, index 7 is h8, index 63 is h1. Pieces use
 * FEN letters, uppercase for white, and [EMPTY] for an empty square.
 */
@JvmInline
value class BoardPosition(val squares: String) {
    init {
        require(squares.length == SQUARE_COUNT) { "A board has $SQUARE_COUNT squares, got ${squares.length}" }
    }

    operator fun get(index: Int): Char = squares[index]

    /** The board turned half way round: a8 is where h1 was. */
    fun turned(): BoardPosition = BoardPosition(squares.reversed())

    companion object {
        const val SQUARE_COUNT = 64
        const val EMPTY = '.'

        /**
         * A square that holds something the board cannot identify.
         *
         * Occupied, so it takes part in working out a move; not a piece, so no promotion is ever
         * read out of it. Boards with occupancy-only sensors report whole positions this way.
         */
        const val UNKNOWN_PIECE = '?'

        val STARTING = BoardPosition(
            "rnbqkbnr" +
                "pppppppp" +
                "........" +
                "........" +
                "........" +
                "........" +
                "PPPPPPPP" +
                "RNBQKBNR"
        )

        val EMPTY_BOARD = BoardPosition(EMPTY.toString().repeat(SQUARE_COUNT))

        /**
         * Reads the placement field of a FEN -- the part before the first space.
         *
         * Returns null for anything that is not an eight-by-eight chess position, which is how a
         * draughts board's own state notation is turned away rather than mangled into 64 squares.
         */
        fun fromFenPlacement(fen: String): BoardPosition? {
            val ranks = fen.trim().substringBefore(' ').split('/')
            if (ranks.size != 8) return null

            val squares = StringBuilder(SQUARE_COUNT)
            for (rank in ranks) {
                var filled = 0
                for (c in rank) {
                    when {
                        c.isDigit() -> {
                            val gap = c - '0'
                            if (gap == 0) return null
                            repeat(gap) { squares.append(EMPTY) }
                            filled += gap
                        }
                        // Chess pieces; draughts men and dames (m/M, d/D), which the open protocol
                        // adds and which 8x8 draughts variants use; and the three ways a board says
                        // it can feel a piece but cannot tell which one it is.
                        c.lowercaseChar() in "pnbrqkmdu" || c == UNKNOWN_PIECE -> {
                            squares.append(c)
                            filled++
                        }
                        else -> return null
                    }
                }
                if (filled != 8) return null
            }
            return BoardPosition(squares.toString())
        }

        /** "a8" for 0, "h1" for 63. */
        fun squareName(index: Int): String {
            val file = 'a' + (index % 8)
            val rank = '8' - (index / 8)
            return "$file$rank"
        }
    }
}

/**
 * Works out which move turned one position into another.
 *
 * Returns null whenever the change is not a finished move, which is the common case rather than the
 * exception: a board reports continuously, so it sees the piece lifted before it sees it land, and
 * both hands during a castle. Reporting a move for those halfway states would press the clock
 * mid-move, so anything that does not match a known shape is treated as "not yet".
 *
 * Notation is coordinate style -- "e2e4", "e7e8q" for a promotion -- not SAN. SAN needs to know
 * which of two knights could have reached a square, and therefore the legal moves of the whole
 * position, which is far more than a clock needs. Coordinate notation is unambiguous, and it is
 * what the exported PGN will show.
 */
object BoardDiffer {
    private fun squareOf(name: String): Int = ('8' - name[1]) * 8 + (name[0] - 'a')

    /**
     * The move that completes [move] when it was the first half of a castle, played on [before].
     *
     * King first, as the rules want it: only a castle moves a king two files. Rook first as well,
     * which is how many players do it: a rook from its corner to the square beside the king, the
     * king about to jump it. That reading is safe, since a king cannot then move two files in any
     * legal game unless it is this castle. Only known pieces count: a board that feels a piece and
     * cannot name it reports a two-file rook move in exactly the same way.
     */
    fun secondHalfOf(move: String, before: BoardPosition): String? {
        if (move.length < 4 || move[1] != move[3]) return null
        val rank = move[1]
        val piece = before[squareOf(move.substring(0, 2))]
        val from = move[0]
        val to = move[2]
        return when {
            piece.equals('k', ignoreCase = true) && from == 'e' && to == 'g' -> "h${rank}f$rank"
            piece.equals('k', ignoreCase = true) && from == 'e' && to == 'c' -> "a${rank}d$rank"
            !piece.equals('r', ignoreCase = true) -> null
            from == 'h' && to == 'f' && before[squareOf("e$rank")].equals('k', ignoreCase = true) -> "e${rank}g$rank"
            from == 'a' && to == 'd' && before[squareOf("e$rank")].equals('k', ignoreCase = true) -> "e${rank}c$rank"
            else -> null
        }
    }

    /**
     * The square of the pawn [move] captured en passant, when it read as a quiet move: the capturing
     * pawn put down before the other was taken off. Left on the baseline, that pawn broke the next
     * comparison, and with a capture next no move was recognised for the rest of the game.
     */
    fun capturedEnPassantBy(move: String, before: BoardPosition): Int? {
        if (move.length < 4 || move[0] == move[2]) return null
        val from = squareOf(move.substring(0, 2))
        val to = squareOf(move.substring(2, 4))
        val isPawn = before[from].equals('p', ignoreCase = true)
        return if (isPawn && before[to] == BoardPosition.EMPTY) squareOf("${move[2]}${move[1]}") else null
    }


    fun moveBetween(before: BoardPosition, after: BoardPosition): String? {
        val changed = (0 until BoardPosition.SQUARE_COUNT).filter { before[it] != after[it] }
        if (changed.isEmpty()) return null

        val vacated = changed.filter { before[it] != BoardPosition.EMPTY && after[it] == BoardPosition.EMPTY }
        val appeared = changed.filter { before[it] == BoardPosition.EMPTY && after[it] != BoardPosition.EMPTY }
        val replaced = changed.filter { before[it] != BoardPosition.EMPTY && after[it] != BoardPosition.EMPTY }

        return when {
            // A quiet move: one square emptied, one filled by the same piece -- or by a different
            // one, which is a pawn that promoted.
            vacated.size == 1 && appeared.size == 1 && replaced.isEmpty() ->
                move(vacated.single(), appeared.single(), before, after)

            // A capture: the destination was already occupied, so it changes rather than fills. The
            // piece that lands is normally the one that left -- unless it is a pawn reaching the
            // last rank, which arrives as whatever it promoted to.
            vacated.size == 1 && appeared.isEmpty() && replaced.size == 1 &&
                (after[replaced.single()] == before[vacated.single()] ||
                    before[vacated.single()].equals('p', ignoreCase = true)) ->
                move(vacated.single(), replaced.single(), before, after)

            // Castling: king and rook both move, so two squares empty and two fill. The move is
            // named after the king, as both PGN and the arbiter do.
            vacated.size == 2 && appeared.size == 2 && replaced.isEmpty() -> {
                val kingFrom = vacated.firstOrNull { before[it].equals('k', ignoreCase = true) }
                val kingTo = appeared.firstOrNull { after[it].equals('k', ignoreCase = true) }
                if (kingFrom != null && kingTo != null) move(kingFrom, kingTo, before, after) else null
            }

            // En passant: the captured pawn is not on the square the capturer lands on, so two
            // squares empty and only one fills. Never a king's or a rook's: that shape is also a
            // castle with both pieces in hand, the king put down first, and read as a king's move it
            // left a position with no rook in it as the baseline, which no later move matched.
            vacated.size == 2 && appeared.size == 1 && replaced.isEmpty() -> {
                val landed = appeared.single()
                val from = vacated.firstOrNull { before[it] == after[landed] }
                if (from != null && !before[from].isKingOrRook()) move(from, landed, before, after) else null
            }

            else -> null
        }
    }

    private fun move(from: Int, to: Int, before: BoardPosition, after: BoardPosition): String {
        val moved = before[from]
        val landed = after[to]
        val notation = BoardPosition.squareName(from) + BoardPosition.squareName(to)
        val lastRank = notation[3] == '1' || notation[3] == '8'
        // A pawn that arrives on the last rank as something else has promoted; PGN spells the new
        // piece in lower case regardless of colour. Only there: a chip misread on e4 made "e2e4?".
        // A piece the board cannot name is the spare queen of [PieceTagCalibration], and was
        // written "e7e8?", which a PGN reader takes for an annotation with no piece at all.
        return when {
            !moved.equals('p', ignoreCase = true) || !lastRank || landed.equals('p', ignoreCase = true) -> notation
            landed == BoardPosition.UNKNOWN_PIECE -> notation + 'q'
            else -> notation + landed.lowercaseChar()
        }
    }

    private fun Char.isKingOrRook() = equals('k', ignoreCase = true) || equals('r', ignoreCase = true)

    /**
     * Whether [move] is the king's half of a castle whose rook went first as [previous].
     *
     * The rook's half is a move like any other -- a rook beside an unmoved king may simply be moving
     * -- so it is reported, and the clock passes on it. Only the king jumping it says it was a castle,
     * and a king moves two files in no other move: what was recorded as the rook's move is then
     * renamed after the king, as PGN wants it.
     */
    fun completesCastle(previous: String?, move: String): Boolean = previous != null && CASTLES[move] == previous

    /** Each castle's king move, and the rook move that can come before it. */
    private val CASTLES = mapOf("e1g1" to "h1f1", "e1c1" to "a1d1", "e8g8" to "h8f8", "e8c8" to "a8d8")
}
