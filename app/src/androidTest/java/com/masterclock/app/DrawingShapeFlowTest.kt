package com.masterclock.app

import android.annotation.SuppressLint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import com.masterclock.app.logic.ChessTimerViewModel
import com.masterclock.app.logic.DrawingShape
import com.masterclock.app.logic.FlavorConfig
import com.masterclock.app.logic.NotebookNote
import com.masterclock.app.logic.isEmoji
import com.masterclock.app.logic.isPiece
import com.masterclock.app.logic.pieceSquare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * A shape drawn on a drawing note, then changed: made another shape, moved, undone, taken out. The
 * notebook is Complete's alone, and so are its strings, looked up by name.
 */
class DrawingShapeFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val viewModel get() = ViewModelProvider(rule.activity)[ChessTimerViewModel::class.java]

    private fun say(id: Int) = rule.activity.getString(id)

    @SuppressLint("DiscouragedApi")
    private fun complete(name: String): String =
        rule.activity.getString(rule.activity.resources.getIdentifier(name, "string", rule.activity.packageName))

    private fun await(matcher: SemanticsMatcher) =
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    private fun press(name: String) = rule.onNode(hasContentDescription(complete(name)) and hasClickAction()).performClick()

    private fun note(): NotebookNote = viewModel.settings.value.notebookNotes.single()

    private fun waitForTheNote(what: String, done: (NotebookNote) -> Boolean) {
        try {
            rule.waitUntil(TIMEOUT_MS) { viewModel.settings.value.notebookNotes.singleOrNull()?.let(done) == true }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("$what: the drawing holds ${viewModel.settings.value.notebookNotes.singleOrNull()?.drawingPaths}", e)
        }
    }

    @Before
    fun startFromAnEmptyNotebook() {
        rule.waitUntil(TIMEOUT_MS) { viewModel.isReady.value }
        assumeTrue(FlavorConfig.hasMoreTab())
        rule.runOnUiThread {
            viewModel.resetAllSettings()
            viewModel.updateSettings(viewModel.settings.value.copy(notebookNotes = emptyList()))
        }
        await(hasContentDescription(say(R.string.timer_settings)))
    }

    @Test
    fun aShapeIsDrawnChangedMovedAndTakenOut() {
        rule.onNode(hasContentDescription(say(R.string.timer_settings)) and hasClickAction()).performClick()
        await(hasText(say(R.string.settings_tab_more)))
        rule.onAllNodesWithText(say(R.string.settings_tab_more))[0].performClick()
        await(hasText(say(R.string.settings_more_notebook)))
        rule.onNode(hasText(say(R.string.settings_more_notebook)) and hasClickAction()).performScrollTo().performClick()
        await(hasContentDescription(complete("tools_new_note")))
        rule.onNode(hasContentDescription(complete("tools_new_note")) and hasClickAction()).performClick()
        await(hasText(complete("tools_draw")))
        rule.onNode(hasText(complete("tools_draw")) and hasClickAction()).performClick()
        await(hasContentDescription(complete("tools_shapes")))

        // The page is the lower part of the screen; the drags stay well inside it.
        val root = rule.onRoot().fetchSemanticsNode().size
        val y = root.height * 0.7f
        val from = Offset(root.width * 0.3f, y)
        val to = Offset(root.width * 0.6f, y + root.height * 0.1f)

        // The shapes are behind one button, which opens their choice.
        press("tools_shapes")
        shot("drawing_shapes_menu")
        press("tools_rectangle")
        rule.onRoot().performTouchInput { swipe(from, to, 300) }
        waitForTheNote("the rectangle") { it.drawingPaths.singleOrNull()?.shape == DrawingShape.RECTANGLE }

        // Selected as drawn: the row under the toolbar makes it an ellipse.
        press("tools_ellipse")
        waitForTheNote("the ellipse") { it.drawingPaths.singleOrNull()?.shape == DrawingShape.ELLIPSE }
        val before = note().drawingPaths.single().points
        shot("drawing_selected")

        // A drag from its middle moves it whole.
        val middle = Offset((from.x + to.x) / 2, (from.y + to.y) / 2)
        rule.onRoot().performTouchInput { swipe(middle, middle + Offset(0f, -root.height * 0.1f), 300) }
        waitForTheNote("the move") { it.drawingPaths.single().points != before }
        val after = note().drawingPaths.single().points
        assertEquals(before[0].first, after[0].first, 0.5f)
        assertEquals(before[1].first - before[0].first, after[1].first - after[0].first, 0.5f)
        assertEquals(before[1].second - before[0].second, after[1].second - after[0].second, 0.5f)

        // Undo puts it back where it was; Redo moves it again.
        press("tools_undo")
        waitForTheNote("the undo") { it.drawingPaths.single().points == before }
        press("tools_redo")
        waitForTheNote("the redo") { it.drawingPaths.single().points == after }

        shot("drawing_shape")

        // Redo let go of it; tapped where it now is, it is selected again, and taken out.
        rule.onRoot().performTouchInput { click(middle + Offset(0f, -root.height * 0.1f)) }
        rule.onNode(hasContentDescription(say(R.string.common_delete)) and hasClickAction()).performClick()
        waitForTheNote("the delete") { it.drawingPaths.isEmpty() }
    }

    /** The width slider's ten steps: the widest gives a stroke of the widest width. */
    @Test
    fun theWidthSliderSetsTheStrokesWidth() {
        rule.onNode(hasContentDescription(say(R.string.timer_settings)) and hasClickAction()).performClick()
        await(hasText(say(R.string.settings_tab_more)))
        rule.onAllNodesWithText(say(R.string.settings_tab_more))[0].performClick()
        await(hasText(say(R.string.settings_more_notebook)))
        rule.onNode(hasText(say(R.string.settings_more_notebook)) and hasClickAction()).performScrollTo().performClick()
        await(hasContentDescription(complete("tools_new_note")))
        rule.onNode(hasContentDescription(complete("tools_new_note")) and hasClickAction()).performClick()
        await(hasText(complete("tools_draw")))
        rule.onNode(hasText(complete("tools_draw")) and hasClickAction()).performClick()
        await(hasContentDescription(complete("tools_width")))

        val slider = rule.onNode(hasContentDescription(complete("tools_width")))
        slider.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(9f) }
        val root = rule.onRoot().fetchSemanticsNode().size
        rule.onRoot().performTouchInput { swipe(Offset(root.width * 0.2f, root.height * 0.7f), Offset(root.width * 0.8f, root.height * 0.8f), 300) }
        waitForTheNote("the wide stroke") { it.drawingPaths.size == 1 }
        val wide = note().drawingPaths.single().strokeWidth
        shot("drawing_width_wide")
        slider.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0f) }
        rule.onRoot().performTouchInput { swipe(Offset(root.width * 0.2f, root.height * 0.85f), Offset(root.width * 0.8f, root.height * 0.9f), 300) }
        waitForTheNote("the fine stroke") { it.drawingPaths.size == 2 }
        val fine = note().drawingPaths[1].strokeWidth
        // The widest step is 32 times the finest, on whatever canvas.
        assertEquals(32f, wide / fine, 0.01f)
        shot("drawing_width")

        // The colours, behind the palette button: red chosen, the next stroke is red.
        press("tools_color")
        rule.waitForIdle()
        shot("drawing_colors")
        rule.onNode(androidx.compose.ui.test.hasTestTag("drawing_color_${0xFFF44336}")).performClick()
        rule.onRoot().performTouchInput { swipe(Offset(root.width * 0.2f, root.height * 0.6f), Offset(root.width * 0.8f, root.height * 0.62f), 300) }
        waitForTheNote("the red stroke") { it.drawingPaths.size == 3 }
        assertEquals(0xFFF44336, note().drawingPaths[2].color)
        shot("drawing_red")
    }

    /** An emoji put on the drawing from its button, beside a piece, then moved. */
    @Test
    fun anEmojiIsPutOnAndMoved() {
        rule.onNode(hasContentDescription(say(R.string.timer_settings)) and hasClickAction()).performClick()
        await(hasText(say(R.string.settings_tab_more)))
        rule.onAllNodesWithText(say(R.string.settings_tab_more))[0].performClick()
        await(hasText(say(R.string.settings_more_notebook)))
        rule.onNode(hasText(say(R.string.settings_more_notebook)) and hasClickAction()).performScrollTo().performClick()
        await(hasContentDescription(complete("tools_new_note")))
        rule.onNode(hasContentDescription(complete("tools_new_note")) and hasClickAction()).performClick()
        await(hasText(complete("tools_draw")))
        rule.onNode(hasText(complete("tools_draw")) and hasClickAction()).performClick()
        await(hasContentDescription(complete("tools_insert_emojis")))

        // A knight first, so that the two are seen together.
        press("tools_insert_pieces")
        rule.onNode(hasContentDescription("N") and hasClickAction()).performClick()
        waitForTheNote("the knight") { it.drawingPaths.size == 1 }
        val knightAt = note().drawingPaths.single().points
        val knight = rule.onAllNodes(hasContentDescription("N"))[0].fetchSemanticsNode().boundsInRoot.center
        rule.onRoot().performTouchInput { swipe(knight, knight + Offset(-200f, -250f), 300) }
        waitForTheNote("the knight's move") { it.drawingPaths.single().points != knightAt }

        press("tools_insert_emojis")
        await(hasText("🏆") and hasClickAction())
        shot("drawing_emojis_menu")
        rule.onNode(hasText("🏆") and hasClickAction()).performClick()
        waitForTheNote("the trophy") { it.drawingPaths.size == 2 && it.drawingPaths[1].isEmoji && it.drawingPaths[1].label == "🏆" }
        val placed = note().drawingPaths[1].points
        val trophy = rule.onAllNodes(hasText("🏆"))[0].fetchSemanticsNode().boundsInRoot.center
        rule.onRoot().performTouchInput { swipe(trophy, trophy + Offset(150f, 250f), 300) }
        waitForTheNote("the trophy's move") { it.drawingPaths[1].points != placed }
        shot("drawing_emoji")
    }

    /** A piece put on the drawing from the Pieces button, moved and enlarged, then taken out. */
    @Test
    fun aPieceIsPutOnMovedEnlargedAndTakenOut() {
        rule.onNode(hasContentDescription(say(R.string.timer_settings)) and hasClickAction()).performClick()
        await(hasText(say(R.string.settings_tab_more)))
        rule.onAllNodesWithText(say(R.string.settings_tab_more))[0].performClick()
        await(hasText(say(R.string.settings_more_notebook)))
        rule.onNode(hasText(say(R.string.settings_more_notebook)) and hasClickAction()).performScrollTo().performClick()
        await(hasContentDescription(complete("tools_new_note")))
        rule.onNode(hasContentDescription(complete("tools_new_note")) and hasClickAction()).performClick()
        await(hasText(complete("tools_draw")))
        rule.onNode(hasText(complete("tools_draw")) and hasClickAction()).performClick()
        await(hasContentDescription(complete("tools_insert_pieces")))

        press("tools_insert_pieces")
        await(hasContentDescription("q") and hasClickAction())
        shot("drawing_pieces_menu")
        rule.onNode(hasContentDescription("q") and hasClickAction()).performClick()
        waitForTheNote("the queen") { it.drawingPaths.singleOrNull()?.let { p -> p.isPiece && p.label == "q" } == true }
        val placed = note().drawingPaths.single().points

        // On the drawing it is a picture, not a button: the one without a click action.
        val onDrawing = rule.onAllNodes(hasContentDescription("q"))[0].fetchSemanticsNode().boundsInRoot
        val centre = onDrawing.center
        rule.onRoot().performTouchInput { swipe(centre, centre + Offset(-120f, 200f), 300) }
        waitForTheNote("the move") { it.drawingPaths.single().points != placed }
        val moved = note().drawingPaths.single().points
        assertEquals(placed[1].first - placed[0].first, moved[1].first - moved[0].first, 0.5f)

        // Its lower right handle pulls it larger, and it stays square.
        val corner = rule.onAllNodes(hasContentDescription("q"))[0].fetchSemanticsNode().boundsInRoot.bottomRight
        rule.onRoot().performTouchInput { swipe(corner, corner + Offset(150f, 150f), 300) }
        waitForTheNote("the size") { it.drawingPaths.single().points[1] != moved[1] }
        val (_, _, side) = note().drawingPaths.single().pieceSquare()
        assertTrue(side > moved[1].first - moved[0].first)
        shot("drawing_piece")

        rule.onNode(hasContentDescription(say(R.string.common_delete)) and hasClickAction()).performClick()
        waitForTheNote("the delete") { it.drawingPaths.isEmpty() }
    }

    /** What the screen looks like, kept where the tests' runner can fetch it: see run-as. */
    private fun shot(name: String) {
        rule.waitForIdle()
        val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(rule.activity.filesDir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
