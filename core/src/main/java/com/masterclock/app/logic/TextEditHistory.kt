package com.masterclock.app.logic

/** How many states a text note's Undo can step back through, as for drawings and boards. */
private const val MAX_TEXT_HISTORY = 50

/** A pause in typing longer than this starts a new Undo step. */
const val TEXT_UNDO_GROUP_MS = 1_000L

/** A text note's content and where its cursor or selection was, as Undo puts it back. */
data class TextSnapshot(val text: String, val selectionStart: Int, val selectionEnd: Int)

/**
 * What a text note's Undo steps back through.
 *
 * The text note had no Undo at all: a formatting button wraps the selection in markers, and getting
 * rid of a wrong one meant finding and deleting them by hand in text where they are drawn
 * invisible. Each formatting action is one step. Typing is grouped, so Undo does not take a word
 * back one letter at a time: keystrokes less than [TEXT_UNDO_GROUP_MS] apart are one step, and a
 * pause, a formatting action or an Undo ends it.
 *
 * Moving the cursor is not an edit; callers only report changes to the text.
 */
data class TextEditHistory(
    val past: List<TextSnapshot> = emptyList(),
    private val lastTypedAtMs: Long? = null,
) {
    val canUndo: Boolean get() = past.isNotEmpty()

    /**
     * Records [before], the state an edit is about to replace. [typing] is a keystroke or a paste
     * from the keyboard; anything else -- a formatting button -- always stands alone.
     */
    fun beforeEdit(before: TextSnapshot, nowMs: Long, typing: Boolean): TextEditHistory {
        val last = lastTypedAtMs
        val continuesTyping = typing && last != null && nowMs - last < TEXT_UNDO_GROUP_MS
        val nextPast = if (continuesTyping) past else (past + before).takeLast(MAX_TEXT_HISTORY)
        return TextEditHistory(nextPast, lastTypedAtMs = if (typing) nowMs else null)
    }

    /** The state to put back, and the history without it; null when there is nothing to undo. */
    fun undo(): Pair<TextSnapshot, TextEditHistory>? =
        past.lastOrNull()?.let { it to TextEditHistory(past.dropLast(1)) }
}
