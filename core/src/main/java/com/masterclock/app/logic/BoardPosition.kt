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
            // squares empty and only one fills.
            vacated.size == 2 && appeared.size == 1 && replaced.isEmpty() -> {
                val landed = appeared.single()
                val from = vacated.firstOrNull { before[it] == after[landed] }
                if (from != null) move(from, landed, before, after) else null
            }

            else -> null
        }
    }

    private fun move(from: Int, to: Int, before: BoardPosition, after: BoardPosition): String {
        val moved = before[from]
        val landed = after[to]
        val notation = BoardPosition.squareName(from) + BoardPosition.squareName(to)
        // A pawn that arrives as something else has promoted; PGN spells the new piece in lower
        // case regardless of colour.
        return if (moved.equals('p', ignoreCase = true) && !landed.equals('p', ignoreCase = true)) {
            notation + landed.lowercaseChar()
        } else {
            notation
        }
    }
}
