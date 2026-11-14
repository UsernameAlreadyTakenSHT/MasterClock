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
 *
 * Redo gives back what Undo took, stroke dropped that way included, until something new is drawn.
 */
data class DrawingEdit(
    val paths: List<DrawingPath>,
    val history: List<List<DrawingPath>> = emptyList(),
    val future: List<List<DrawingPath>> = emptyList(),
) {
    val canUndo: Boolean get() = history.isNotEmpty() || paths.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    fun addStroke(stroke: DrawingPath): DrawingEdit = record(paths + stroke)

    /** The stroke at [index] taken out, as one step. */
    fun remove(index: Int): DrawingEdit =
        if (index !in paths.indices) this else record(paths.filterIndexed { i, _ -> i != index })

    /** The stroke at [index] changed to [stroke] -- a shape moved, resized or made another -- as one step. */
    fun replace(index: Int, stroke: DrawingPath): DrawingEdit =
        if (index !in paths.indices || paths[index] == stroke) this
        else record(paths.toMutableList().also { it[index] = stroke })

    /** Empties the drawing. Clearing one that is already empty records nothing to undo. */
    fun clear(): DrawingEdit = if (paths.isEmpty()) this else record(emptyList())

    fun undo(): DrawingEdit = when {
        history.isNotEmpty() -> DrawingEdit(paths = history.last(), history = history.dropLast(1), future = keep(future + listOf(paths)))
        paths.isNotEmpty() -> copy(paths = paths.dropLast(1), future = keep(future + listOf(paths)))
        else -> this
    }

    fun redo(): DrawingEdit =
        if (future.isEmpty()) this
        else DrawingEdit(paths = future.last(), history = keep(history + listOf(paths)), future = future.dropLast(1))

    private fun record(next: List<DrawingPath>) =
        DrawingEdit(paths = next, history = keep(history + listOf(paths)))

    private fun keep(states: List<List<DrawingPath>>) = states.takeLast(MAX_DRAWING_HISTORY)
}

/**
 * How much a drawing made on a canvas of [drawnWidth] by [drawnHeight] is scaled to show whole on
 * one of [canvasWidth] by [canvasHeight]: by the tighter side, so nothing is cut and nothing is
 * stretched. 1 where either size is unknown, which is a drawing from before its canvas was kept.
 */
fun drawingScale(drawnWidth: Float, drawnHeight: Float, canvasWidth: Float, canvasHeight: Float): Float =
    if (drawnWidth <= 0f || drawnHeight <= 0f || canvasWidth <= 0f || canvasHeight <= 0f) 1f
    else minOf(canvasWidth / drawnWidth, canvasHeight / drawnHeight)
