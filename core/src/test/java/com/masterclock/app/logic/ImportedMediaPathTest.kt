package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Covers [sanitizeImportedMediaPath], which decides whether a note media path arriving from an
 * imported settings file, backup archive or QR code may be kept.
 *
 * It matters more than its size suggests: the notebook's delete button hands these paths to
 * `shredFile`, which overwrites a file with random bytes before unlinking it. The predicate used to
 * ask only whether the path sat inside the app sandbox, and the app's own DataStore does, so a
 * crafted note could aim the shredder at every setting the user had.
 */
class ImportedMediaPathTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun filesDir(): File = temp.root

    @Test
    fun `keeps an audio path the notebook itself would have written`() {
        val path = File(filesDir(), "audio_9f8e7d6c-1234-4321-abcd-0123456789ab.mp4").path
        assertEquals(path, sanitizeImportedMediaPath(filesDir(), path))
    }

    @Test
    fun `keeps image and video paths under shares`() {
        val shares = File(filesDir(), "shares").apply { mkdirs() }
        val image = File(shares, "image_abc123.jpg").path
        val video = File(shares, "video_abc123.mp4").path
        assertEquals(image, sanitizeImportedMediaPath(filesDir(), image))
        assertEquals(video, sanitizeImportedMediaPath(filesDir(), video))
    }

    /** The finding five independent security reviews arrived at, reproduced. */
    @Test
    fun `refuses the app's own DataStore file`() {
        val datastore = File(File(filesDir(), "datastore"), "settings.preferences_pb").path
        assertNull(sanitizeImportedMediaPath(filesDir(), datastore))
    }

    @Test
    fun `refuses a file in the sandbox that is not named like note media`() {
        val path = File(filesDir(), "game_database").path
        assertNull(sanitizeImportedMediaPath(filesDir(), path))
    }

    @Test
    fun `refuses note media naming in the wrong directory`() {
        // The right name is not enough: audio lives in filesDir, never under shares.
        val shares = File(filesDir(), "shares").apply { mkdirs() }
        assertNull(sanitizeImportedMediaPath(filesDir(), File(shares, "audio_abc.mp4").path))
        // And image lives under shares, never directly in filesDir.
        assertNull(sanitizeImportedMediaPath(filesDir(), File(filesDir(), "image_abc.jpg").path))
    }

    @Test
    fun `refuses a path that escapes the sandbox`() {
        assertNull(sanitizeImportedMediaPath(filesDir(), "/data/data/com.other.app/files/audio_a.mp4"))
        assertNull(sanitizeImportedMediaPath(filesDir(), "/etc/passwd"))
    }

    @Test
    fun `refuses traversal that lands on a legitimate-looking name`() {
        val escaped = File(filesDir(), "../../audio_abc.mp4").path
        assertNull(sanitizeImportedMediaPath(filesDir(), escaped))
    }

    @Test
    fun `refuses a nested directory below shares`() {
        val nested = File(File(File(filesDir(), "shares"), "deeper"), "image_abc.jpg").path
        assertNull(sanitizeImportedMediaPath(filesDir(), nested))
    }

    @Test
    fun `treats null and blank as nothing to keep`() {
        assertNull(sanitizeImportedMediaPath(filesDir(), null))
        assertNull(sanitizeImportedMediaPath(filesDir(), "   "))
    }
}
