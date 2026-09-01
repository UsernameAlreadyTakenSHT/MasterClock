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
     * Returns null while no mapping is known, or when a tag in the frame has never been seen --
     * a piece from another set, or a chip that was not on the board during calibration. Refusing is
     * the right answer there: guessing a piece would feed [BoardDiffer] a position that never
     * existed.
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
            squares[square] = if (tag == null) BoardPosition.EMPTY else pieceByTag[tag] ?: return null
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
