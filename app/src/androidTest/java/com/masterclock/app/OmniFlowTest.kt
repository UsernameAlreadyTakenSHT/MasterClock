package com.masterclock.app

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import com.masterclock.app.logic.ChessTimerViewModel
import com.masterclock.app.logic.FlavorConfig
import com.masterclock.app.logic.OmniGameSettings
import com.masterclock.app.logic.OmniRoundSettings
import com.masterclock.app.logic.OmniSettings
import com.masterclock.app.logic.OmniTimerViewModel
import com.masterclock.app.logic.OmniTurnSettings
import com.masterclock.app.logic.RoundTurnLogic
import org.junit.After
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Omni from the settings to a running session and back: its own screens, and none of the clock's. */
class OmniFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val viewModel get() = ViewModelProvider(rule.activity)[ChessTimerViewModel::class.java]

    /**
     * The activity's, which is the one the Omni screens use: the navigation keeps no store of its own
     * per screen, so viewModel() in a route reaches the activity's.
     */
    private val omni get() = ViewModelProvider(rule.activity)[OmniTimerViewModel::class.java]

    private fun say(id: Int) = rule.activity.getString(id)

    private fun say(id: Int, n: Int) = rule.activity.getString(id, n)

    private fun await(matcher: SemanticsMatcher) =
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    private fun control(id: Int) = rule.onNode(hasContentDescription(say(id)) and hasClickAction())

    @Before
    fun startFromTheDefaults() {
        rule.waitUntil(TIMEOUT_MS) { viewModel.isReady.value }
        assumeTrue(FlavorConfig.hasOmni())
        rule.runOnUiThread {
            viewModel.resetAllSettings()
            viewModel.reset()
        }
        await(hasContentDescription(say(R.string.timer_settings)))
        rule.runOnUiThread { omni.resetOmni() }
    }

    /** Omni's settings are stored, and a test that set its own must not leave them to the next. */
    @After
    fun backToOmniDefaults() {
        rule.runOnUiThread {
            omni.resetOmni()
            omni.updateOmniSettings(OmniSettings())
        }
    }

    /** From the clock through the wizard's defaults to a running session. */
    private fun launchASession() {
        control(R.string.timer_settings).performClick()
        await(hasText("Omni-Timer", substring = true))
        rule.onNode(hasText("Omni-Timer", substring = true) and hasClickAction()).performScrollTo().performClick()

        // A wizard: Launch is on its last step, and the defaults pass every one on the way.
        val next = hasText(say(R.string.omni_cfg_next_step)) and hasClickAction()
        val launch = hasText(say(R.string.omni_cfg_launch_game)) and hasClickAction()
        await(next)
        repeat(MAX_WIZARD_STEPS) {
            if (rule.onAllNodes(next).fetchSemanticsNodes().isNotEmpty()) rule.onNode(next).performClick()
            rule.waitForIdle()
        }
        await(launch)
        rule.onNode(launch).performClick()
        await(hasText(say(R.string.omni_running)))
    }

    /**
     * The clocks on screen -- "45:00", and the launch countdown's "9" -- and nothing else, read where the
     * player reads them. Only the figures: with every text, "PAUSED" going was a change, and the check after Resume
     * passed with the clock stopped.
     */
    private fun sessionTime(): List<String> =
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes().flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }
            .filter { CLOCK_TEXT.matches(it) }

    @Test
    fun aSessionLaunchesPausesAndResumes() {
        launchASession()
        // The words on screen, and the clock behind them: a session whose ticker had stopped read
        // "running" all the same.
        val started = sessionTime()
        rule.waitUntil(CLOCK_TIMEOUT_MS) { sessionTime() != started }

        control(R.string.omni_pause).performClick()
        await(hasText(say(R.string.omni_paused)))
        val held = sessionTime()
        Thread.sleep(1_200)
        assertEquals("a paused session's clock moved", held, sessionTime())

        control(R.string.timer_resume).performClick()
        await(hasText(say(R.string.omni_running)))
        val resumed = sessionTime()
        rule.waitUntil(CLOCK_TIMEOUT_MS) { sessionTime() != resumed }
    }

    @Test
    fun everyPlayerPlaysEachTurnOfARound() {
        // Three players, and a round of two turns: a long one and a short one. Every player plays the
        // long turn, then every player the short one -- six turns. It was a round of two, and player 3
        // never played in it.
        val long = OmniTurnSettings(durationMs = 600_000)
        val short = OmniTurnSettings(durationMs = 120_000)
        val round = OmniRoundSettings(turnLogic = RoundTurnLogic.SEQUENCE, customTurns = listOf(long, short))
        rule.runOnUiThread {
            omni.updateOmniSettings(
                OmniSettings(
                    numberOfPlayers = 3,
                    games = listOf(OmniGameSettings(rounds = listOf(round, OmniRoundSettings()))),
                    launchCountdownMs = 0,
                )
            )
        }
        launchASession()

        val played = mutableListOf<Pair<Int, Long>>()
        fun note() = omni.omniState.value.let { played += (it.currentPlayerIndex + 1) to it.currentTurnTimeMs }
        note()
        repeat(5) {
            control(R.string.omni_next).performClick()
            rule.waitForIdle()
            note()
        }
        assertEquals("who played", listOf(1, 2, 3, 1, 2, 3), played.map { it.first })
        // Each turn starts at its own length, a few seconds of it gone at most by the time it is read.
        played.forEachIndexed { i, (_, timeMs) ->
            val length = if (i < 3) long.durationMs else short.durationMs
            assertTrue("turn ${i + 1} started at $timeMs", timeMs in length - 5_000..length)
        }
        // The second pass is each player's second turn, and the face says so.
        await(hasText(say(R.string.omni_cfg_turn_n, 2), ignoreCase = true))
        assertTrue(rule.onAllNodes(hasText(say(R.string.common_player_n, 3), ignoreCase = true)).fetchSemanticsNodes().isNotEmpty())
        // The round is still the first: it ends after the sixth turn, not the second.
        assertEquals(0, omni.omniState.value.currentRoundIndex)
        control(R.string.omni_next).performClick()
        rule.waitForIdle()
        assertEquals(1, omni.omniState.value.currentRoundIndex)
    }

    @Test
    fun aRunningSessionGoesOnThroughARecreation() {
        // What turning a tablet or a foldable does, which ignore the portrait lock; and a theme change.
        launchASession()
        rule.activityRule.scenario.recreate()
        await(hasText(say(R.string.omni_running)))
        rule.waitForIdle()
        assertTrue(rule.onAllNodes(hasText(say(R.string.omni_paused))).fetchSemanticsNodes().isEmpty())
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        /** A launch's countdown holds the clocks for up to ten seconds before they start. */
        const val CLOCK_TIMEOUT_MS = 20_000L
        val CLOCK_TEXT = Regex("""\d+(:\d{2})*""")
        const val MAX_WIZARD_STEPS = 12
    }
}
