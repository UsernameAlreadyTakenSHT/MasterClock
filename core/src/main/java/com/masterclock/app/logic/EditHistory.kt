package com.masterclock.app.logic

/**
 * Undo and Redo over whole states of [T], at most [limit] of them each way: the board note's, whose
 * state is its game and its pieces. [DrawingEdit] and [TextEditHistory] have their own, for what
 * they do besides: a drawing reopened undoes stroke by stroke, and typing is grouped.
 *
 * An edit records the state it replaces and forgets what Redo could have given back: after an Undo,
 * doing something new starts another line of changes, as in any editor.
 */
data class EditHistory<T>(
    val past: List<T> = emptyList(),
    val future: List<T> = emptyList(),
    val limit: Int = 50,
) {
    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    /** [before] recorded, as the state an edit is about to replace. */
    fun record(before: T): EditHistory<T> = copy(past = (past + listOf(before)).takeLast(limit), future = emptyList())

    /** The state to put back in place of [current], and the history after it; null when there is none. */
    fun undo(current: T): Pair<T, EditHistory<T>>? =
        past.lastOrNull()?.let { it to copy(past = past.dropLast(1), future = (future + listOf(current)).takeLast(limit)) }

    /** The state Undo last took away, to put back in place of [current]; null when there is none. */
    fun redo(current: T): Pair<T, EditHistory<T>>? =
        future.lastOrNull()?.let { it to copy(past = (past + listOf(current)).takeLast(limit), future = future.dropLast(1)) }
}
