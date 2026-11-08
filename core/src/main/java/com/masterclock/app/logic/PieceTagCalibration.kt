package com.masterclock.app.logic

/**
 * Learns which physical piece each tag belongs to.
 *
 * Boards that read a chip under every piece -- Certabo's do -- know which *individual* piece stands
 * on a square, but not that it is a knight. The mapping from tag to piece cannot be shipped: the
 * chips are stuck under the pieces by whoever owns the set, and the numbers differ from set to set.
 *
 * So it is learned from the one layout everybody agrees on. When a frame shows exactly the
 * thirty-two occupied and thirty-two empty squares of the starting position, each tag is given the
 * piece standing on its square. Certabo's own software does the same thing, and asks the player to
 * set the board up before a first game for exactly this reason.
 */
class PieceTagCalibration {

    private companion object {
        /**
         * A chess set has thirty-two pieces, and a player may own a spare or two. Anything past
         * this is not a set being recognised.
         *
         * The map is only ever added to, and a board that keeps sending starting-position frames
         * with tags it has never used before adds thirty-two entries per frame for as long as it
         * stays connected. That board is broken or hostile either way; the ceiling means it costs
         * nothing but its own calibration.
         */
        const val MAX_LEARNED_TAGS = 64
    }

    private val pieceByTag = mutableMapOf<String, Char>()

    val isCalibrated: Boolean get() = pieceByTag.isNotEmpty()

    /**
     * Turns tags into a position, calibrating first if this frame is the starting layout.
     *
     * Returns null while no mapping is known. A tag never seen -- the spare queen of a promotion, or a
     * piece from another set -- is a piece all the same, [BoardPosition.UNKNOWN_PIECE]: refusing the
     * frame stopped every move while it stood there. [BoardDiffer] names no piece from it, and a
     * promotion to it is a queen.
     */
    fun identify(tags: List<String?>): BoardPosition? {
        if (tags.size != BoardPosition.SQUARE_COUNT) return null

        if (looksLikeStartingPosition(tags)) {
            tags.forEachIndexed { square, tag ->
                // A tag already known is re-learned freely -- that is the same set being set up
                // again. Only a tag that would grow the map past the ceiling is refused.
                if (tag != null && (pieceByTag.containsKey(tag) || pieceByTag.size < MAX_LEARNED_TAGS)) {
                    pieceByTag[tag] = BoardPosition.STARTING[square]
                }
            }
        }
        if (!isCalibrated) return null

        val squares = CharArray(BoardPosition.SQUARE_COUNT)
        tags.forEachIndexed { square, tag ->
            // A chip it has not learned is a piece on the board all the same: the spare queen of a
            // promotion, whose chip was never on the board. Refusing the whole frame stopped every
            // move for as long as she stood there.
            squares[square] = if (tag == null) BoardPosition.EMPTY else pieceByTag[tag] ?: BoardPosition.UNKNOWN_PIECE
        }
        return BoardPosition(String(squares))
    }

    /**
     * Whether the occupied squares are exactly the starting position's.
     *
     * Only the pattern is checked, because the pieces are precisely what is not yet known. That is
     * enough: no other position in a game has all sixteen pieces of both sides on their own two
     * ranks and the middle four empty.
     */
    private fun looksLikeStartingPosition(tags: List<String?>): Boolean =
        (0 until BoardPosition.SQUARE_COUNT).all { square ->
            (tags[square] != null) == (BoardPosition.STARTING[square] != BoardPosition.EMPTY)
        }

    fun reset() {
        pieceByTag.clear()
    }
}
