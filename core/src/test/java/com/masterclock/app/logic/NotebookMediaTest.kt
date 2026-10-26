package com.masterclock.app.logic

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Covers [commitPendingMedia]: a retake replaces the old take only once there is a new one. */
class NotebookMediaTest {

    @get:Rule val dir = TemporaryFolder()

    private val shredded = mutableListOf<String>()
    private val shred: (File) -> Unit = { shredded += it.readText(); it.delete() }

    private fun target() = File(dir.root, "image_abc.jpg")

    @Test
    fun `a finished retake replaces the old take, which is shredded`() {
        val target = target().apply { writeText("old") }
        val pending = pendingMediaFile(target).apply { writeText("new") }

        assertTrue(commitPendingMedia(pending, target, shred))
        assertEquals("new", target.readText())
        assertFalse(pending.exists())
        assertEquals(listOf("old"), shredded)
        assertEquals(listOf("image_abc.jpg"), dir.root.list()!!.toList())
    }

    @Test
    fun `a cancelled retake leaves the old take as it was`() {
        val target = target().apply { writeText("old") }
        val pending = pendingMediaFile(target)

        assertFalse(commitPendingMedia(pending, target, shred))
        assertEquals("old", target.readText())
        assertTrue(shredded.isEmpty())
    }

    @Test
    fun `an empty take does not replace anything and is cleaned up`() {
        val target = target().apply { writeText("old") }
        val pending = pendingMediaFile(target).apply { writeText("") }

        assertFalse(commitPendingMedia(pending, target, shred))
        assertEquals("old", target.readText())
        assertFalse(pending.exists())
    }

    @Test
    fun `a first take is committed with nothing to shred`() {
        val target = target()
        val pending = pendingMediaFile(target).apply { writeText("first") }

        assertTrue(commitPendingMedia(pending, target, shred))
        assertEquals("first", target.readText())
        assertTrue(shredded.isEmpty())
    }

    @Test
    fun `a take left aside by an interrupted commit is shredded on the next one`() {
        val target = target().apply { writeText("current") }
        File(dir.root, "image_abc.jpg.replaced").writeText("stale")
        val pending = pendingMediaFile(target).apply { writeText("new") }

        assertTrue(commitPendingMedia(pending, target, shred))
        assertEquals("new", target.readText())
        assertEquals(listOf("stale", "current"), shredded)
    }

    @Test
    fun `the pending file keeps the note id and does not look like note media`() {
        val pending = pendingMediaFile(File(dir.root, "video_0f3c-11.mp4"))

        assertTrue(pending.name.contains("0f3c-11"))
        assertFalse(Regex("""video_[A-Za-z0-9-]{1,64}\.mp4""").matches(pending.name))
    }
}
