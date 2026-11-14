package com.masterclock.app

import android.annotation.SuppressLint
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModelProvider
import com.masterclock.app.logic.ChessTimerViewModel
import com.masterclock.app.logic.FlavorConfig
import com.masterclock.app.logic.NoteStyle
import com.masterclock.app.logic.NotebookNote
import com.masterclock.app.logic.NotebookNoteType
import com.masterclock.app.logic.allHave
import com.masterclock.app.logic.richTextOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * A text note written through its editor: the formatting is the note's, beside its text, and none of
 * it is characters in the text. The notebook is Complete's alone, and so are its strings, which are
 * looked up by name for that reason: the other builds compile these tests without them.
 */
class TextNoteFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val viewModel get() = ViewModelProvider(rule.activity)[ChessTimerViewModel::class.java]

    private fun say(id: Int) = rule.activity.getString(id)

    /** One of Complete's own strings. */
    @SuppressLint("DiscouragedApi")
    private fun complete(name: String): String =
        rule.activity.getString(rule.activity.resources.getIdentifier(name, "string", rule.activity.packageName))

    private fun await(matcher: SemanticsMatcher) =
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    /**
     * A toolbar button, which must be in view: the bar scrolled sideways, and the box, the numbered
     * list and the colour were past the edge of a phone's screen, where nobody saw them.
     */
    private fun press(name: String) = rule.onNode(hasContentDescription(complete(name)) and hasClickAction()).assertIsDisplayed().performClick()

    /** The note's text field: the second field on the page, under the title. */
    private fun content() = rule.onAllNodes(hasSetTextAction())[1]

    private fun note(): NotebookNote = viewModel.settings.value.notebookNotes.single()

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

    private fun openTheNotebook() {
        rule.onNode(hasContentDescription(say(R.string.timer_settings)) and hasClickAction()).performClick()
        await(hasText(say(R.string.settings_tab_more)))
        rule.onAllNodesWithText(say(R.string.settings_tab_more))[0].performClick()
        await(hasText(say(R.string.settings_more_notebook)))
        rule.onNode(hasText(say(R.string.settings_more_notebook)) and hasClickAction()).performScrollTo().performClick()
        await(hasContentDescription(complete("tools_new_note")))
    }

    private fun waitForTheNote(what: String, done: (NotebookNote) -> Boolean) {
        try {
            rule.waitUntil(TIMEOUT_MS) { viewModel.settings.value.notebookNotes.singleOrNull()?.let(done) == true }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("$what: the note reads \"${viewModel.settings.value.notebookNotes.singleOrNull()?.content}\"", e)
        }
    }

    @Test
    fun formattingIsKeptBesideTheText() {
        openTheNotebook()
        rule.onNode(hasContentDescription(complete("tools_new_note")) and hasClickAction()).performClick()
        await(hasText(complete("tools_text")))
        rule.onNode(hasText(complete("tools_text")) and hasClickAction()).performClick()
        await(hasSetTextAction())

        content().performTextInput("Play")
        // Bold chosen with nothing selected: what is typed next is bold.
        press("tools_bold")
        content().performTextInput(" e4")
        // The line made a list item, then a box.
        press("tools_bullet_list")
        waitForTheNote("the bullet") { it.content == "• Play e4" }
        press("tools_checkbox")
        waitForTheNote("the box") { it.content == "☐ Play e4" }

        val written = note()
        assertTrue(written.richFormat)
        val rich = written.richTextOf()
        assertTrue("e4 is not bold", rich.allHave(rich.text.indexOf("e4"), rich.text.length, NoteStyle.BOLD))
        assertTrue("Play is bold", !rich.allHave(rich.text.indexOf("Play"), rich.text.indexOf("Play") + 4, NoteStyle.BOLD))

        // A tap on the box -- the cursor put beside it -- ticks it.
        content().performTextInputSelection(TextRange(1))
        waitForTheNote("the tick") { it.content == "☑ Play e4" }
    }

    @Test
    fun aNoteWrittenWithMarkersOpensWithoutThem() {
        val old = NotebookNote(title = "Old", content = "**Bold** and <u>under</u>", type = NotebookNoteType.TEXT)
        rule.runOnUiThread { viewModel.updateSettings(viewModel.settings.value.copy(notebookNotes = listOf(old))) }
        openTheNotebook()
        rule.onNode(hasText("Old") and hasClickAction()).performClick()
        await(hasSetTextAction())
        val shown = content().fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        assertEquals("Bold and under", shown)
        // Stored as it was until it is changed, then in the new form.
        assertEquals(old.content, note().content)
        content().performTextInput("!")
        waitForTheNote("the note in its new form") { it.richFormat }
        assertTrue(note().content.startsWith("Bold and under") || note().content.endsWith("Bold and under"))
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
