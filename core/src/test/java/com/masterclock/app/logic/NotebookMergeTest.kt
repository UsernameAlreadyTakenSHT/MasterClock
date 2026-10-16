package com.masterclock.app.logic

import org.junit.Assert.*
import org.junit.Test

/**
 * Pins [mergeImportedNotes]: an import may add to the notebook but never take anything out of it.
 *
 * Every import used to replace the notebook with the file's, and no export but the ZIP carries all
 * of it, so importing one's own settings file deleted every drawing and recording on the device.
 */
class NotebookMergeTest {

    private val text = NotebookNote(id = "t", title = "Opening", content = "1. e4", timestamp = 1L)
    private val drawing = NotebookNote(
        id = "d",
        title = "Sketch",
        type = NotebookNoteType.DRAWING,
        drawingPaths = listOf(DrawingPath(points = listOf(1f to 2f))),
        timestamp = 2L,
    )
    private val voice = NotebookNote(id = "v", title = "Memo", type = NotebookNoteType.VOICE, audioPath = "/a/audio_v.mp4", timestamp = 3L)

    @Test
    fun `an import carrying no notes leaves the notebook as it was`() {
        val local = listOf(text, drawing, voice)
        assertEquals(local, mergeImportedNotes(local, emptyList()))
    }

    @Test
    fun `an import carrying only the text notes keeps the others`() {
        val local = listOf(text, drawing, voice)
        // As sanitizeImportedSettings hands it over: same note, fresh id.
        val imported = listOf(text.copy(id = "fresh"))
        assertEquals(local, mergeImportedNotes(local, imported))
    }

    @Test
    fun `restoring a backup onto the device it came from doubles nothing`() {
        val local = listOf(text, drawing, voice)
        val imported = local.map { it.copy(id = it.id + "-new") }
        assertEquals(local, mergeImportedNotes(local, imported))
    }

    @Test
    fun `notes the device does not have are added after its own`() {
        val other = NotebookNote(id = "o", title = "From a friend", content = "Najdorf", timestamp = 9L)
        assertEquals(listOf(text, other), mergeImportedNotes(listOf(text), listOf(other)))
    }

    @Test
    fun `a note edited since the export is kept in both versions`() {
        val edited = text.copy(content = "1. e4 c5")
        assertEquals(listOf(edited, text.copy(id = "x")), mergeImportedNotes(listOf(edited), listOf(text.copy(id = "x"))))
    }

    @Test
    fun `an import stops at the notebook's ceiling and never cuts the device's own notes`() {
        val local = List(MAX_NOTEBOOK_NOTES - 2) { NotebookNote(id = "l$it", title = "Local $it", timestamp = it.toLong()) }
        val imported = List(5) { NotebookNote(id = "i$it", title = "Imported $it", timestamp = 1_000L + it) }
        val merged = mergeImportedNotes(local, imported)
        assertEquals(MAX_NOTEBOOK_NOTES, merged.size)
        assertEquals(local, merged.take(local.size))
    }

    @Test
    fun `a full notebook takes nothing from an import`() {
        val local = List(MAX_NOTEBOOK_NOTES) { NotebookNote(id = "l$it", title = "Local $it", timestamp = it.toLong()) }
        assertEquals(local, mergeImportedNotes(local, listOf(NotebookNote(title = "One more"))))
    }

    @Test
    fun `a note repeated inside the import is added once`() {
        val other = NotebookNote(id = "o1", title = "Twice", timestamp = 5L)
        val merged = mergeImportedNotes(emptyList(), listOf(other, other.copy(id = "o2")))
        assertEquals(listOf(other), merged)
    }
}
