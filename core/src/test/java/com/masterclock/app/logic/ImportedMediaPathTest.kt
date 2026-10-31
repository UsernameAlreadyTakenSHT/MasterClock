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

    private fun own(name: String) = File(filesDir().canonicalFile, name).path
    private fun shared(name: String) = File(File(filesDir().canonicalFile, "shares"), name).path

    @Test
    fun `keeps an audio path the notebook itself would have written`() {
        val path = File(filesDir(), "audio_9f8e7d6c-1234-4321-abcd-0123456789ab.mp4").path
        assertEquals(own("audio_9f8e7d6c-1234-4321-abcd-0123456789ab.mp4"), sanitizeImportedMediaPath(filesDir(), path))
    }

    @Test
    fun `keeps image and video paths under shares`() {
        val shares = File(filesDir(), "shares").apply { mkdirs() }
        val image = File(shares, "image_abc123.jpg").path
        val video = File(shares, "video_abc123.mp4").path
        assertEquals(shared("image_abc123.jpg"), sanitizeImportedMediaPath(filesDir(), image))
        assertEquals(shared("video_abc123.mp4"), sanitizeImportedMediaPath(filesDir(), video))
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
    fun `puts note media back in the directory its kind lives in`() {
        // Audio lives in filesDir, image and video under shares, wherever the path said.
        assertEquals(own("audio_abc.mp4"), sanitizeImportedMediaPath(filesDir(), File(File(filesDir(), "shares"), "audio_abc.mp4").path))
        assertEquals(shared("image_abc.jpg"), sanitizeImportedMediaPath(filesDir(), File(filesDir(), "image_abc.jpg").path))
    }

    /** A backup made under another user, profile or build: the same files, another sandbox path. */
    @Test
    fun `brings a path from another sandbox into this one`() {
        assertEquals(own("audio_a.mp4"), sanitizeImportedMediaPath(filesDir(), "/data/user/10/com.masterclock.app/files/audio_a.mp4"))
        assertEquals(shared("video_v.mp4"), sanitizeImportedMediaPath(filesDir(), "/mnt/expand/1234/user/0/com.masterclock.app/files/shares/video_v.mp4"))
    }

    @Test
    fun `refuses anything not named like note media, wherever it is`() {
        assertNull(sanitizeImportedMediaPath(filesDir(), "/etc/passwd"))
        assertNull(sanitizeImportedMediaPath(filesDir(), File(filesDir(), "..").path))
        assertNull(sanitizeImportedMediaPath(filesDir(), "audio_abc.mp4/"))
    }

    @Test
    fun `traversal and nesting cannot choose where the file is`() {
        assertEquals(own("audio_abc.mp4"), sanitizeImportedMediaPath(filesDir(), File(filesDir(), "../../audio_abc.mp4").path))
        assertEquals(shared("image_abc.jpg"), sanitizeImportedMediaPath(filesDir(), File(File(File(filesDir(), "shares"), "deeper"), "image_abc.jpg").path))
        assertEquals(own("audio_abc.mp4"), sanitizeImportedMediaPath(filesDir(), "C:\\other\\audio_abc.mp4"))
    }

    @Test
    fun `treats null and blank as nothing to keep`() {
        assertNull(sanitizeImportedMediaPath(filesDir(), null))
        assertNull(sanitizeImportedMediaPath(filesDir(), "   "))
    }
}
