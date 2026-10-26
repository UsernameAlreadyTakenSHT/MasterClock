package com.masterclock.app.logic

import java.io.File

/**
 * Where a new take for [target] is captured before it replaces it.
 *
 * The camera and the recorder used to write straight into the note's own file. They open it for
 * writing as they start, which empties it, so a retake that was cancelled, failed, or -- for a
 * recording -- lasted under a second had already destroyed the take it was meant to replace, and
 * the note went on pointing at an empty file. A take now goes here first, and only a finished one
 * is moved over the original by [commitPendingMedia].
 *
 * Same directory as [target], so the move is a rename and never a copy. The name keeps the note's
 * id in it, which is what deleting a note sweeps by, and does not match the media name patterns,
 * so neither a backup nor a restore ever takes it for a note's media.
 */
fun pendingMediaFile(target: File): File = File(target.parentFile, target.name + ".pending")

/**
 * Puts a finished take in [pending] in place of [target], and hands the take it replaces to
 * [shred]. Returns false, leaving [target] as it was and [pending] gone, when there is no take to
 * commit or the move fails.
 *
 * The old take is set aside before the new one moves in and is only shredded once that has
 * worked, so there is no moment at which a failure leaves the note with neither.
 */
fun commitPendingMedia(pending: File, target: File, shred: (File) -> Unit): Boolean {
    if (!pending.isFile || pending.length() == 0L) {
        pending.delete()
        return false
    }
    val aside = File(target.parentFile, target.name + ".replaced")
    // Left behind by a commit that was interrupted between the two renames.
    if (aside.exists()) shred(aside)

    val hadOld = target.exists()
    if (hadOld && !target.renameTo(aside)) {
        pending.delete()
        return false
    }
    if (!pending.renameTo(target)) {
        if (hadOld) aside.renameTo(target)
        pending.delete()
        return false
    }
    if (hadOld) shred(aside)
    return true
}
