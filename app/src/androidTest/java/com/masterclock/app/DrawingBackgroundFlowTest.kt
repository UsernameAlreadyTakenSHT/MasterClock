package com.masterclock.app

import android.annotation.SuppressLint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.lifecycle.ViewModelProvider
import com.masterclock.app.logic.BoardNoteVariant
import com.masterclock.app.logic.ChessTimerViewModel
import com.masterclock.app.logic.FlavorConfig
import com.masterclock.app.logic.NotebookNote
import com.masterclock.app.logic.NotebookNoteType
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * A drawing drawn over a photo note's photo, then over a board note's position: each a copy of its
 * own, under the strokes, which the eraser does not touch.
 */
class DrawingBackgroundFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val viewModel get() = ViewModelProvider(rule.activity)[ChessTimerViewModel::class.java]

    private fun say(id: Int) = rule.activity.getString(id)

    @SuppressLint("DiscouragedApi")
    private fun complete(name: String): String =
        rule.activity.getString(rule.activity.resources.getIdentifier(name, "string", rule.activity.packageName))

    private fun await(matcher: SemanticsMatcher) =
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    private fun drawing(): NotebookNote = viewModel.settings.value.notebookNotes.single { it.type == NotebookNoteType.DRAWING }

    private fun waitForTheDrawing(what: String, done: (NotebookNote) -> Boolean) {
        try {
            rule.waitUntil(TIMEOUT_MS) { done(drawing()) }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("$what: the drawing is ${drawing()}", e)
        }
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        Thread.sleep(500)
        val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(rule.activity.filesDir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Before
    fun ready() {
        rule.waitUntil(TIMEOUT_MS) { viewModel.isReady.value }
        assumeTrue(FlavorConfig.hasMoreTab())
        rule.runOnUiThread { viewModel.resetAllSettings() }
    }

    private fun aPhoto(id: String): File = File(File(rule.activity.filesDir, "shares").apply { mkdirs() }, "image_$id.jpg").apply {
        val bitmap = android.graphics.Bitmap.createBitmap(600, 800, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.rgb(250, 250, 240))
        val paint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(40, 40, 120); textSize = 34f }
        // A scoresheet, more or less.
        for (move in 1..16) canvas.drawText("$move.  e4   e5", 40f, 40f + move * 44f, paint)
        outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
    }

    private fun chooseBackground(title: String) {
        rule.onNode(hasContentDescription(complete("tools_background")) and hasClickAction()).performClick()
        await(hasText(title))
        rule.onNode(hasText(title) and hasClickAction()).performClick()
    }

    @Test
    fun aDrawingIsDrawnOverAPhotoThenABoard() {
        val photoNote = NotebookNote(title = "Scoresheet", type = NotebookNoteType.IMAGE).let { it.copy(imagePath = aPhoto(it.id).absolutePath) }
        val position = MutableList(64) { "" }.apply { this[4] = "K"; this[3] = "Q"; this[60] = "k"; this[28] = "P"; this[36] = "p"; this[21] = "N" }
        val boardNote = NotebookNote(title = "Open game", type = NotebookNoteType.BOARD, boardPosition = position, boardVariant = BoardNoteVariant.CHESS)
        val sketch = NotebookNote(title = "Sketch", type = NotebookNoteType.DRAWING)
        rule.runOnUiThread { viewModel.updateSettings(viewModel.settings.value.copy(notebookNotes = listOf(photoNote, boardNote, sketch))) }
        await(hasContentDescription(say(R.string.timer_settings)))
        rule.onNode(hasContentDescription(say(R.string.timer_settings)) and hasClickAction()).performClick()
        await(hasText(say(R.string.settings_tab_more)))
        rule.onAllNodesWithText(say(R.string.settings_tab_more))[0].performClick()
        await(hasText(say(R.string.settings_more_notebook)))
        rule.onNode(hasText(say(R.string.settings_more_notebook)) and hasClickAction()).performScrollTo().performClick()
        await(hasText("Sketch"))
        rule.onNode(hasText("Sketch") and hasClickAction()).performClick()
        await(hasContentDescription(complete("tools_background")))

        // The photo: the drawing's own copy, at its own path.
        chooseBackground("Scoresheet")
        waitForTheDrawing("the photo") { it.imagePath != null && !it.drawingBoard }
        val copy = File(drawing().imagePath!!)
        assertEquals("image_${sketch.id}.jpg", copy.name)
        rule.waitUntil(TIMEOUT_MS) { copy.length() > 0 }
        val root = rule.onRoot().fetchSemanticsNode().size
        rule.onRoot().performTouchInput { swipe(Offset(root.width * 0.2f, root.height * 0.6f), Offset(root.width * 0.8f, root.height * 0.75f), 300) }
        waitForTheDrawing("the stroke") { it.drawingPaths.size == 1 }
        shot("background_photo")

        // The board: the photo's copy goes, the photo note keeps its own.
        chooseBackground("Open game")
        waitForTheDrawing("the board") { it.drawingBoard && it.imagePath == null && it.boardPosition == position }
        rule.waitUntil(TIMEOUT_MS) { !copy.exists() }
        assertEquals(true, File(photoNote.imagePath!!).exists())
        // The eraser over the board takes the stroke, and not the board.
        rule.onNode(hasContentDescription(complete("tools_eraser")) and hasClickAction()).performClick()
        rule.onRoot().performTouchInput { swipe(Offset(root.width * 0.2f, root.height * 0.6f), Offset(root.width * 0.5f, root.height * 0.675f), 300) }
        waitForTheDrawing("the eraser") { it.drawingPaths.size == 2 }
        shot("background_board")

        // And none again.
        rule.onNode(hasContentDescription(complete("tools_background")) and hasClickAction()).performClick()
        await(hasText(complete("tools_background_none")))
        rule.onNode(hasText(complete("tools_background_none")) and hasClickAction()).performClick()
        waitForTheDrawing("no background") { !it.drawingBoard && it.imagePath == null }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
