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
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Omni from the settings to a running session and back: its own screens, and none of the clock's. */
class OmniFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val viewModel get() = ViewModelProvider(rule.activity)[ChessTimerViewModel::class.java]

    private fun say(id: Int) = rule.activity.getString(id)

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
    }

    @Test
    fun aSessionLaunchesPausesAndResumes() {
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
        control(R.string.omni_pause).performClick()
        await(hasText(say(R.string.omni_paused)))

        control(R.string.timer_resume).performClick()
        await(hasText(say(R.string.omni_running)))
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val MAX_WIZARD_STEPS = 12
    }
}
