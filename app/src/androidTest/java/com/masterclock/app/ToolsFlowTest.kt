package com.masterclock.app

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import com.masterclock.app.data.SettingsRepository
import com.masterclock.app.logic.ChessTimerViewModel
import com.masterclock.app.logic.FlavorConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The small tools of More, which had no test at all: the scoreboard first, which keeps an evening's
 * results and loses them if a wire breaks, then the coin, the die, the straws and the stop-precision
 * trial, each of which says its result to TalkBack as well as drawing it -- and the two must agree.
 * Complete only; the other builds have no More tab. They reset the scoreboard on that device.
 */
class ToolsFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val viewModel get() = ViewModelProvider(rule.activity)[ChessTimerViewModel::class.java]
    private val scoreboard get() = viewModel.scoreboard.value

    private fun say(id: Int, vararg args: Any) = rule.activity.getString(id, *args)

    /** A resource of Complete's own source set, by name: the reduced builds compile these tests too. */
    private fun id(name: String, type: String = "string") =
        rule.activity.resources.getIdentifier(name, type, rule.activity.packageName)

    private fun complete(name: String, vararg args: Any) = rule.activity.getString(id(name), *args)

    private fun exists(matcher: SemanticsMatcher, merged: Boolean = true) =
        rule.onAllNodes(matcher, useUnmergedTree = !merged).fetchSemanticsNodes().isNotEmpty()

    private fun await(matcher: SemanticsMatcher, merged: Boolean = true) = rule.waitUntil(TIMEOUT_MS) { exists(matcher, merged) }

    /** What a new process would read. */
    private fun stored() = runBlocking { SettingsRepository(rule.activity.applicationContext).scoreboardFlow.first() }

    /** A node whose tap is named [label], as TalkBack reads it: "double-tap to <label>". */
    private fun clickLabelled(label: String) = SemanticsMatcher("click labelled \"$label\"") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

    /** What a node announces as its state: the result, once it has landed. */
    private fun SemanticsNode.said(): String = config.getOrNull(SemanticsProperties.StateDescription).orEmpty()

    private fun openTool(title: Int) {
        rule.onNode(hasContentDescription(say(R.string.timer_settings)) and hasClickAction()).performClick()
        await(hasText(say(R.string.settings_tab_more)))
        rule.onAllNodes(hasText(say(R.string.settings_tab_more)) and hasClickAction())[0].performClick()
        await(hasText(say(title)))
        rule.onNode(hasText(say(title)) and hasClickAction()).performScrollTo().performClick()
    }

    @Before
    fun startFromAnEmptyScoreboard() {
        rule.waitUntil(TIMEOUT_MS) { viewModel.isReady.value }
        assumeTrue(FlavorConfig.hasMoreTab())
        rule.runOnUiThread {
            viewModel.resetAllSettings()
            viewModel.reset()
            viewModel.resetScoreboard()
        }
        await(hasContentDescription(say(R.string.timer_start)))
    }

    // --- Scoreboard ---

    private fun result(text: String) = hasText(text) and !hasClickAction()

    private fun top(matcher: SemanticsMatcher) = rule.onNode(matcher).fetchSemanticsNode().boundsInRoot.top

    @Test
    fun aResultButtonAddsAGameNewestFirstAndOnDisk() {
        openTool(R.string.settings_more_scoreboard)
        await(hasText(complete("tools_no_games")))

        rule.onNode(hasText("1 - 0") and hasClickAction()).performClick()
        rule.onNode(hasText("½ - ½") and hasClickAction()).performClick()

        await(result("1 - 0"))
        await(result("½ - ½"))
        assertFalse(exists(hasText(complete("tools_no_games"))))
        assertEquals(listOf("1 - 0", "½ - ½"), scoreboard.games.map { it.result })
        // The last played on top, where the eye goes after a game.
        assertTrue(top(result("½ - ½")) < top(result("1 - 0")))
        rule.waitUntil(TIMEOUT_MS) { stored().games.map { it.result } == listOf("1 - 0", "½ - ½") }
    }

    @Test
    fun aTypedResultIsAddedAndABlankOneIsNot() {
        openTool(R.string.settings_more_scoreboard)
        await(hasText(complete("tools_no_games")))
        val add = rule.onNode(hasContentDescription(complete("tools_add_result")) and hasClickAction())
        // The third text field: the two names, then the result.
        val field = rule.onAllNodes(hasSetTextAction())[2]

        field.performTextReplacement("   ")
        add.performClick()
        rule.waitForIdle()
        assertTrue(scoreboard.games.isEmpty())

        field.performTextReplacement("Draw by repetition")
        add.performClick()

        await(result("Draw by repetition"))
        assertEquals(listOf("Draw by repetition"), scoreboard.games.map { it.result })
        // Emptied for the next one.
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(hasSetTextAction())[2].fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.isNullOrEmpty() }
    }

    @Test
    fun theNamesTypedAreKeptOnDisk() {
        openTool(R.string.settings_more_scoreboard)
        await(hasText(complete("tools_no_games")))

        rule.onAllNodes(hasSetTextAction())[0].performTextReplacement("Ada")
        rule.onAllNodes(hasSetTextAction())[1].performTextReplacement("Grace")

        // Written a moment after the last key, as a note is.
        rule.waitUntil(TIMEOUT_MS) { scoreboard.player1Name == "Ada" && scoreboard.player2Name == "Grace" }
        rule.waitUntil(TIMEOUT_MS) { stored().let { it.player1Name == "Ada" && it.player2Name == "Grace" } }
    }

    @Test
    fun resetAsksFirstAndCancelKeepsTheResults() {
        rule.runOnUiThread { viewModel.addScoreboardGame("1 - 0") }
        openTool(R.string.settings_more_scoreboard)
        await(result("1 - 0"))
        val reset = rule.onNode(hasContentDescription(complete("tools_reset_scoreboard")) and hasClickAction())

        reset.performClick()
        await(hasText(say(R.string.common_cancel)))
        rule.onNode(hasText(say(R.string.common_cancel)) and hasClickAction()).performClick()
        rule.waitUntil(TIMEOUT_MS) { !exists(hasText(say(R.string.common_cancel)) and hasClickAction()) }
        assertEquals(1, scoreboard.games.size)

        reset.performClick()
        await(hasText(say(R.string.common_reset)) and hasClickAction())
        rule.onNode(hasText(say(R.string.common_reset)) and hasClickAction()).performClick()

        await(hasText(complete("tools_no_games")))
        rule.waitUntil(TIMEOUT_MS) { stored().games.isEmpty() }
    }

    // --- Coin and die ---

    @Test
    fun theCoinSaysTheFaceItLandsOn() {
        openTool(R.string.settings_more_coin_toss)
        val toss = clickLabelled(complete("tools_toss_coin"))
        await(toss)
        val faces = setOf(complete("tools_heads"), complete("tools_tails"))

        repeat(3) {
            rule.onNode(toss).performClick()
            // Nothing said while it spins, then the face, once.
            rule.waitUntil(TIMEOUT_MS) { rule.onNode(toss).fetchSemanticsNode().said() in faces }
            val said = rule.onNode(toss).fetchSemanticsNode().said()
            // The face drawn is the face said: the coin turns by half-turns, and an odd one is tails.
            await(hasText(said), merged = false)
            val other = (faces - said).single()
            assertFalse("said $said, drew $other", exists(hasText(other), merged = false))
        }
    }

    @Test
    fun theDieSaysANumberFromOneToSixOnceItHasStopped() {
        openTool(R.string.settings_more_dice)
        val roll = clickLabelled(complete("tools_roll_dice"))
        await(roll)
        // Not yet rolled: nothing to say.
        assertEquals("", rule.onNode(roll).fetchSemanticsNode().said())

        repeat(3) {
            rule.onNode(roll).performClick()
            // The two seconds it turns for pass at once on the test's clock, so the "?" it shows
            // meanwhile is not looked for; what is said once it stops is.
            rule.waitUntil(TIMEOUT_MS) { rule.onNode(roll).fetchSemanticsNode().said().toIntOrNull() in 1..6 }
            assertFalse(exists(hasText("?"), merged = false))
        }
    }

    // --- Straws ---

    @Test
    fun oneStrawIsShortAndTheDrawEndsWhenItIsPicked() {
        openTool(R.string.settings_more_straw)
        rule.onNode(hasText(complete("tools_prepare")) and hasClickAction()).performClick()
        await(clickLabelled(complete("tools_pick_straw", 1)))

        val short = complete("tools_straw_short")
        val long = complete("tools_straw_long")
        var picked = 0
        for (i in 1..5) {
            val straw = rule.onNode(clickLabelled(complete("tools_pick_straw", i)))
            straw.performClick()
            picked++
            rule.waitUntil(TIMEOUT_MS) { straw.fetchSemanticsNode().said() in setOf(short, long) }
            if (straw.fetchSemanticsNode().said() == short) break
        }

        // One short among those drawn, and it is the last: none can be picked after it.
        val said = (1..5).map { rule.onNode(clickLabelled(complete("tools_pick_straw", it))).fetchSemanticsNode().said() }
        assertEquals(1, said.count { it == short })
        assertEquals(picked, said.count { it.isNotEmpty() })
        (1..5).filter { said[it - 1].isEmpty() }.forEach {
            assertTrue(rule.onNode(clickLabelled(complete("tools_pick_straw", it))).fetchSemanticsNode().config.contains(SemanticsProperties.Disabled))
        }

        // Then the screen draws again, through its own action: a tap at its middle lands on a straw.
        rule.onNode(clickLabelled(complete("tools_draw_again"))).performSemanticsAction(SemanticsActions.OnClick)
        await(hasText(complete("tools_prepare")))
    }

    @Test
    fun theNumberOfStrawsChosenIsTheNumberDrawn() {
        openTool(R.string.settings_more_straw)
        await(hasText(complete("tools_prepare")))

        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        await(hasText(rule.activity.resources.getQuantityString(id("tools_straws", "plurals"), 2, 2)))
        rule.onNode(hasText(complete("tools_prepare")) and hasClickAction()).performClick()

        await(clickLabelled(complete("tools_pick_straw", 2)))
        assertFalse(exists(clickLabelled(complete("tools_pick_straw", 3))))
    }

    // --- Stop precision ---

    @Test
    fun stopPrecisionTimesTheTrialAndSaysHowFarOff() {
        openTool(R.string.settings_more_stop)
        rule.onNode(hasText(complete("tools_start")) and hasClickAction()).performClick()
        await(hasText(complete("tools_stop")) and hasClickAction())

        Thread.sleep(1_500)
        rule.onNode(hasText(complete("tools_stop")) and hasClickAction()).performClick()
        await(hasText(complete("tools_start")) and hasClickAction())

        // The reading, to the millisecond, and how far it is from five seconds.
        val reading = Regex("""(\d+)[.,](\d{3})s""")
        val shown = rule.onAllNodes(SemanticsMatcher("a reading") { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { reading.matches(it.text) }
        }).fetchSemanticsNodes().single().config[SemanticsProperties.Text].first { reading.matches(it.text) }.text
        val ms = reading.find(shown)!!.groupValues.let { it[1].toLong() * 1000 + it[2].toLong() }
        assertTrue("read $shown", ms in 1_300..4_000)
        await(hasText(complete("tools_offset", "-", 5_000 - ms)))
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
