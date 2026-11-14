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
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import com.masterclock.app.logic.ChessTimerViewModel
import com.masterclock.app.logic.DrawingShape
import com.masterclock.app.logic.FlavorConfig
import com.masterclock.app.logic.NotebookNote
import org.junit.Assert.assertEquals
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
