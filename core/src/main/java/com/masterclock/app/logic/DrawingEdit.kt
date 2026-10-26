package com.masterclock.app.logic

/** How many drawing states Undo can step back through. Far more than anyone undoes in a row. */
private const val MAX_DRAWING_HISTORY = 50

/**
 * A drawing note's strokes, and the earlier states Undo steps back through.
 *
 * Undo used to be "drop the last stroke", which has nothing to give back after Clear: clearing
 * emptied the drawing and Undo then had no strokes left to drop, so one stray tap on Clear cost the
 * whole drawing. Every edit now records the state it replaced, Clear included -- as the board
 * note's editor already did -- so Undo after Clear brings the drawing back.
 *
 * The history covers this editing session only. A note reopened later starts without one, and Undo
 * then falls back to dropping the last saved stroke, which is what it always did.
 */
data class DrawingEdit(
    val paths: List<DrawingPath>,
    val history: List<List<DrawingPath>> = emptyList(),
) {
    val canUndo: Boolean get() = history.isNotEmpty() || paths.isNotEmpty()

    fun addStroke(stroke: DrawingPath): DrawingEdit = record(paths + stroke)

    /** Empties the drawing. Clearing one that is already empty records nothing to undo. */
    fun clear(): DrawingEdit = if (paths.isEmpty()) this else record(emptyList())

    fun undo(): DrawingEdit = when {
        history.isNotEmpty() -> DrawingEdit(paths = history.last(), history = history.dropLast(1))
        paths.isNotEmpty() -> copy(paths = paths.dropLast(1))
        else -> this
    }

    private fun record(next: List<DrawingPath>) =
        DrawingEdit(paths = next, history = (history + listOf(paths)).takeLast(MAX_DRAWING_HISTORY))
}
