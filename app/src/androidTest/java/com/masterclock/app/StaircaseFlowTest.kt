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
import com.masterclock.app.logic.FastMoveType
import com.masterclock.app.logic.FlavorConfig
import com.masterclock.app.logic.TimerMode
import com.masterclock.app.logic.isStaircase
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Staircase, a mode of its own on the settings screen though it is stored as Fast Move's SHRINK:
 * chosen, it starts Down; Up turns its fields into a step up and a ceiling; and Fast Move, chosen
 * from it, is Fast Move again rather than the Staircase it was stored as.
 */
class StaircaseFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val viewModel get() = ViewModelProvider(rule.activity)[ChessTimerViewModel::class.java]

    private fun say(id: Int) = rule.activity.getString(id)

    private fun await(matcher: SemanticsMatcher) =
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    private fun main() = viewModel.settings.value.main

    private fun shot(name: String) {
        rule.waitForIdle()
        val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(rule.activity.filesDir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Before
    fun ready() {
        rule.waitUntil(TIMEOUT_MS) { viewModel.isReady.value }
        // The modes past the first few are not in Mini.
        assumeTrue(FlavorConfig.isModeAllowed(TimerMode.FAST_MOVE) && FlavorConfig.currentFlavor != com.masterclock.app.logic.AppFlavor.MINI)
        rule.runOnUiThread { viewModel.resetAllSettings() }
        await(hasContentDescription(say(R.string.timer_settings)))
    }

    @Test
    fun staircaseIsAModeOfItsOwnWithDownAndUp() {
        rule.onNode(hasContentDescription(say(R.string.timer_settings)) and hasClickAction()).performClick()
        await(hasText("Staircase"))
        rule.onNode(hasText("Staircase") and hasClickAction()).performScrollTo().performClick()
        rule.waitUntil(TIMEOUT_MS) { main().isStaircase && !main().fastMoveShrinkGrows }
        await(hasText("Up") and hasClickAction())
        rule.onNode(hasText("Up") and hasClickAction()).performScrollTo().performClick()
        rule.waitUntil(TIMEOUT_MS) { main().isStaircase && main().fastMoveShrinkGrows }
        await(hasText(say(R.string.settings_max_time_ceiling)))
        rule.onNode(hasText(say(R.string.settings_increase_per_move))).performScrollTo()
        shot("staircase_up")
        // Fast Move, from Staircase, starts on one of its own variants.
        rule.onNode(hasText("Fast Move") and hasClickAction()).performScrollTo().performClick()
        rule.waitUntil(TIMEOUT_MS) { main().mode == TimerMode.FAST_MOVE && main().fastMoveMode == FastMoveType.ACCELERATE }
        rule.onNode(hasText("Transfer") and hasClickAction()).performScrollTo()
        shot("staircase_fast_move")
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
