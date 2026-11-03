package com.masterclock.app.logic

/**
 * What a note editor has written, and what it still owes, for a save that waits for typing to stop.
 *
 * A change is compared with the last value actually written, not with the one the editor opened
 * with. Comparing with the opening value lost edits two ways. Clear on a drawing was saved, and
 * Undo then brought the strokes back to exactly what the note had opened with -- so nothing was
 * written, and the note stayed empty in storage while the screen showed the drawing. And typing a
 * letter then deleting it within the wait left the cancelled change still marked as owed, so
 * leaving the note saved the letter that had been deleted.
 *
 * Merely opening a note still writes nothing: the first value is the one already stored.
 */
class NoteSaveTracker<T>(stored: T) {
    private var saved: T = stored
    private var owed = false
    private var owedValue: T = stored

    /** Records [value] as the editor's latest; true when it differs from what is stored and must be written. */
    fun changed(value: T): Boolean {
        owed = value != saved
        owedValue = value
        return owed
    }

    /** Records that [value] has been written. */
    fun written(value: T) {
        saved = value
        if (owed && owedValue == value) owed = false
    }

    /** Hands the value still owed, if any, to [write] and records it as written: the flush on leaving. */
    fun flush(write: (T) -> Unit) {
        if (!owed) return
        val value = owedValue
        written(value)
        write(value)
    }
}
