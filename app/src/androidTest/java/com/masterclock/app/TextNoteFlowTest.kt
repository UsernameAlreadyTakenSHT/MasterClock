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
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModelProvider
import com.masterclock.app.logic.ChessTimerViewModel
import com.masterclock.app.logic.FlavorConfig
import com.masterclock.app.logic.NoteStyle
import com.masterclock.app.logic.NotebookNote
import com.masterclock.app.logic.NotebookNoteType
import com.masterclock.app.logic.allHave
import com.masterclock.app.logic.highlighterOf
import com.masterclock.app.logic.runs
import com.masterclock.app.logic.isCenteredLine
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
        // Bold and highlight chosen with nothing selected: what is typed next is both.
        press("tools_bold")
        // The highlighter is chosen among five: pink.
        press("tools_highlight")
        rule.onNode(hasContentDescription(complete("tools_highlight_pink")) and hasClickAction()).performClick()
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
        assertTrue("e4 is not highlighted", rich.allHave(rich.text.indexOf("e4"), rich.text.length, NoteStyle.HIGHLIGHT))
        assertEquals("e4 is not pink", 2, highlighterOf(rich.runs().last().styles))
        assertTrue("Play is bold", !rich.allHave(rich.text.indexOf("Play"), rich.text.indexOf("Play") + 4, NoteStyle.BOLD))

        // The cursor brought beside the box, as an arrow key or Home would, leaves it as it is...
        content().performTextInputSelection(TextRange(0))
        content().performTextInputSelection(TextRange(1))
        rule.waitForIdle()
        Thread.sleep(700)
        rule.waitForIdle()
        assertEquals("☐ Play e4", note().content)
        // ...and a tap on it ticks it: the box is the field's first character, past its padding.
        content().performTouchInput { click(androidx.compose.ui.geometry.Offset(22.dp.toPx(), 28.dp.toPx())) }
        waitForTheNote("the tick") { it.content == "☑ Play e4" }

        // Clear takes it all, and Undo gives it all back, formatting with it.
        rule.onNode(hasContentDescription(say(R.string.common_clear)) and hasClickAction()).performClick()
        waitForTheNote("the clear") { it.content.isEmpty() }
        press("tools_undo")
        waitForTheNote("the undo") { it.content == "☑ Play e4" }
        val back = note().richTextOf()
        assertTrue("the undo lost the bold", back.allHave(back.text.indexOf("e4"), back.text.length, NoteStyle.BOLD))
    }

    @Test
    fun theInsertPanelPutsPiecesAndAnnotationsAtTheCursor() {
        openTheNotebook()
        rule.onNode(hasContentDescription(complete("tools_new_note")) and hasClickAction()).performClick()
        await(hasText(complete("tools_text")))
        rule.onNode(hasText(complete("tools_text")) and hasClickAction()).performClick()
        await(hasSetTextAction())
        // What this phone's font draws, for the record: the panel leaves out what it does not.
        val paint = android.graphics.Paint()
        android.util.Log.i("TextNoteFlowTest", "draughts pieces drawn: " + com.masterclock.app.logic.NoteInserts.DRAUGHTS_PIECES.map { it to paint.hasGlyph(it) })

        // A button each, the one pressed closing its panel and the next opening its own.
        press("tools_insert_pieces")
        rule.onNode(hasContentDescription(complete("tools_piece_white_king")) and hasClickAction()).performClick()
        press("tools_insert_annotations")
        rule.onNode(hasContentDescription(complete("tools_sym_interesting")) and hasClickAction()).performClick()
        rule.onNode(hasContentDescription(complete("tools_sym_white_clearly")) and hasClickAction()).performClick()
        waitForTheNote("the inserts") { it.content == "♔!?±" }
    }

    /** Whether the keyboard is on screen, as the window's insets have it. */
    private fun keyboardUp(): Boolean {
        var up = false
        rule.runOnUiThread {
            up = androidx.core.view.ViewCompat.getRootWindowInsets(rule.activity.window.decorView)
                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
        }
        return up
    }

    /**
     * An insert panel open, and the note tapped to place the cursor: the keyboard comes up and the
     * panel goes. Left open beside it, the two took the screen between them and the note had none.
     */
    @Test
    fun anInsertPanelGivesWayToTheKeyboard() {
        openTheNotebook()
        rule.onNode(hasContentDescription(complete("tools_new_note")) and hasClickAction()).performClick()
        await(hasText(complete("tools_text")))
        rule.onNode(hasText(complete("tools_text")) and hasClickAction()).performClick()
        await(hasSetTextAction())
        press("tools_insert_emojis")
        val panel = androidx.compose.ui.test.hasTestTag("emoji_grid")
        await(panel)
        content().performClick()
        // A device with a hardware keyboard shows none, and has nothing to give way to.
        val shown = runCatching { rule.waitUntil(3_000) { keyboardUp() } }.isSuccess
        org.junit.Assume.assumeTrue("no on-screen keyboard here", shown)
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(panel).fetchSemanticsNodes().isEmpty() }
    }

    /** An emoji of the open group, scrolled to first: the grid's lower rows are off a short screen. */
    private fun pick(emoji: String) {
        rule.onNode(androidx.compose.ui.test.hasTestTag("emoji_grid")).performScrollToNode(hasText(emoji))
        rule.onNode(hasText(emoji) and hasClickAction()).performClick()
    }

    @Test
    fun emojisGoInFromTheirTabWhole() {
        openTheNotebook()
        rule.onNode(hasContentDescription(complete("tools_new_note")) and hasClickAction()).performClick()
        await(hasText(complete("tools_text")))
        rule.onNode(hasText(complete("tools_text")) and hasClickAction()).performClick()
        await(hasSetTextAction())
        content().performTextInput("Won ")
        press("tools_insert_emojis")
        for (emoji in listOf("🏆", "⏱️", "🧠")) pick(emoji)
        waitForTheNote("the emojis") { it.content == "Won 🏆⏱️🧠" }
        // Another group, named for TalkBack, and its emojis in place of the game's.
        rule.onNode(hasContentDescription(complete("tools_emoji_food")) and hasClickAction()).performClick()
        pick("🍇")
        waitForTheNote("the grapes") { it.content == "Won 🏆⏱️🧠🍇" }
        // One from the keyboard, which the panel leaves to it: a skin tone.
        content().performTextInput(" 👍🏽")
        waitForTheNote("the thumb") { it.content == "Won 🏆⏱️🧠🍇 👍🏽" }
        // What it looks like, kept where the tests' runner can fetch it: see run-as.
        rule.waitForIdle()
        val shot = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(rule.activity.filesDir, "note_emojis.png").outputStream().use { shot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    /**
     * Typed letter by letter with no pause, as on a phone's keyboard: Undo takes the last word, then
     * the one before. It took the whole text at once, a pause being all that ended a step.
     */
    @Test
    fun typingWithoutAPauseIsUndoneWordByWord() {
        openTheNotebook()
        rule.onNode(hasContentDescription(complete("tools_new_note")) and hasClickAction()).performClick()
        await(hasText(complete("tools_text")))
        rule.onNode(hasText(complete("tools_text")) and hasClickAction()).performClick()
        await(hasSetTextAction())
        for (c in "first second") content().performTextInput(c.toString())
        waitForTheNote("the words") { it.content == "first second" }
        press("tools_undo")
        waitForTheNote("the first undo") { it.content == "first " }
        press("tools_undo")
        waitForTheNote("the second undo") { it.content == "" }
        press("tools_redo")
        waitForTheNote("the redo") { it.content == "first " }
    }

    /** Typing undone, a pause apart: the second word goes, then the first; Redo brings them back. */
    @Test
    fun typingIsUndoneAndRedone() {
        openTheNotebook()
        rule.onNode(hasContentDescription(complete("tools_new_note")) and hasClickAction()).performClick()
        await(hasText(complete("tools_text")))
        rule.onNode(hasText(complete("tools_text")) and hasClickAction()).performClick()
        await(hasSetTextAction())
        content().performTextInput("first")
        waitForTheNote("the first word") { it.content == "first" }
        Thread.sleep(1_500)
        content().performTextInput(" second")
        waitForTheNote("the second word") { it.content == "first second" }
        press("tools_undo")
        waitForTheNote("the first undo") { it.content == "first" }
        press("tools_undo")
        waitForTheNote("the second undo") { it.content == "" }
        press("tools_redo")
        waitForTheNote("the redo") { it.content == "first" }
    }

    /** Four words highlighted in four colours, by selecting each and choosing. */
    @Test
    fun wordsAreHighlightedInTheirColours() {
        openTheNotebook()
        rule.onNode(hasContentDescription(complete("tools_new_note")) and hasClickAction()).performClick()
        await(hasText(complete("tools_text")))
        rule.onNode(hasText(complete("tools_text")) and hasClickAction()).performClick()
        await(hasSetTextAction())
        content().performTextInput("Plan attack defend endgame")
        waitForTheNote("the text") { it.content == "Plan attack defend endgame" }
        val words = listOf("Plan" to "tools_highlight_yellow", "attack" to "tools_highlight_orange", "defend" to "tools_highlight_blue", "endgame" to "tools_highlight_green")
        for ((word, colour) in words) {
            val at = "Plan attack defend endgame".indexOf(word)
            content().performTextInputSelection(TextRange(at, at + word.length))
            press("tools_highlight")
            if (word == "Plan") { rule.waitForIdle(); shotOf("note_highlighters") }
            rule.onNode(hasContentDescription(complete(colour)) and hasClickAction()).performClick()
        }
        waitForTheNote("the highlights") { n -> n.richTextOf().runs().count { it.styles and NoteStyle.HIGHLIGHT != 0 } == 4 }
        val colours = note().richTextOf().runs().filter { it.styles and NoteStyle.HIGHLIGHT != 0 }.map { highlighterOf(it.styles) }
        assertEquals(listOf(0, 1, 3, 4), colours)
        content().performTextInputSelection(TextRange(0))
        rule.waitForIdle()
        shotOf("note_highlighted")
        // The text's colours: four, the text's own, and a custom one, which once used is offered apart.
        content().performTextInputSelection(TextRange(0, 4))
        press("tools_color")
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(androidx.compose.ui.test.hasTestTag("text_color_custom")).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
        shotOf("note_colors")
        rule.onNode(androidx.compose.ui.test.hasTestTag("text_color_custom")).performClick()
        val rgb = SemanticsMatcher("an RGB slider") {
            it.config.getOrElseNullable(SemanticsProperties.ProgressBarRangeInfo) { null }?.range == 0f..1f
        }
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(rgb).fetchSemanticsNodes().size == 3 }
        listOf(0.1f, 0.6f, 0.5f).forEachIndexed { i, v ->
            rule.onAllNodes(rgb)[i].performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(v) }
        }
        rule.onNode(hasText(say(R.string.common_select)) and hasClickAction()).performClick()
        waitForTheNote("the custom colour") { n -> n.richTextOf().runs().any { it.color != 0L && it.color !in listOf(0xFF000000, 0xFF2196F3, 0xFFF44336, 0xFF4CAF50) } }
        content().performTextInputSelection(TextRange(5, 11))
        press("tools_color")
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(hasText(complete("tools_custom_colours"))).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
        shotOf("note_colors_mine")
    }

    private fun shotOf(name: String) {
        val shot = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(rule.activity.filesDir, "$name.png").outputStream().use { shot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun aLineIsCentredAndUndoneAndRedone() {
        openTheNotebook()
        rule.onNode(hasContentDescription(complete("tools_new_note")) and hasClickAction()).performClick()
        await(hasText(complete("tools_text")))
        rule.onNode(hasText(complete("tools_text")) and hasClickAction()).performClick()
        await(hasSetTextAction())
        content().performTextInput("Round one")
        press("tools_center")
        waitForTheNote("the centring") { it.richTextOf().isCenteredLine(0) }
        rule.onNode(hasContentDescription(complete("tools_undo")) and hasClickAction()).performClick()
        waitForTheNote("the undo") { !it.richTextOf().isCenteredLine(0) && it.content == "Round one" }
        rule.onNode(hasContentDescription(complete("tools_redo")) and hasClickAction()).performClick()
        waitForTheNote("the redo") { it.richTextOf().isCenteredLine(0) && it.content == "Round one" }
        content().performTextInput("\nleft")
        rule.waitForIdle()
        val shot = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(rule.activity.filesDir, "note_center.png").outputStream().use { shot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
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
