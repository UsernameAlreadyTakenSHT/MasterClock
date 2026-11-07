package com.masterclock.paper

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import com.masterclock.app.logic.ChessTimerViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The E-Ink clock, which shares its logic with the phone app and none of its screens. The same paths
 * as the phone's ClockFlowsTest: a press, a pause, Reset and its question, what TalkBack is handed.
 * Run with `:paper:connectedDebugAndroidTest`; they reset the app's settings on that device.
 */
class PaperClockFlowsTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val viewModel get() = ViewModelProvider(rule.activity)[ChessTimerViewModel::class.java]

    private fun say(id: Int, vararg args: Any) = rule.activity.getString(id, *args)

    private fun exists(matcher: SemanticsMatcher, merged: Boolean = true) =
        rule.onAllNodes(matcher, useUnmergedTree = !merged).fetchSemanticsNodes().isNotEmpty()

    private fun await(matcher: SemanticsMatcher, merged: Boolean = true) = rule.waitUntil(TIMEOUT_MS) { exists(matcher, merged) }

    private fun button(id: Int) = hasText(say(id)) and hasClickAction()

    private fun player(number: Int) = rule.onNode(hasContentDescription(say(R.string.a11y_player_clock, number, ""), substring = true))

    private fun announced(text: String) = hasContentDescription(text) and SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)

    @Before
    fun startFromTheDefaults() {
        rule.waitUntil(TIMEOUT_MS) { viewModel.isReady.value }
        rule.runOnUiThread {
            viewModel.resetAllSettings()
            viewModel.reset()
        }
        await(button(R.string.timer_start))
    }

    @Test
    fun startGivesTheClockToPlayerOneAndSaysSo() {
        rule.onNode(button(R.string.timer_start)).performClick()

        rule.waitUntil(TIMEOUT_MS) { viewModel.uiState.value.activePlayer == 1 }
        await(announced(say(R.string.a11y_announce_to_move, 1)), merged = false)
    }

    @Test
    fun aPressPassesTheTurn() {
        rule.onNode(button(R.string.timer_start)).performClick()
        rule.waitUntil(TIMEOUT_MS) { viewModel.uiState.value.activePlayer == 1 }

        player(1).performClick()

        rule.waitUntil(TIMEOUT_MS) { viewModel.uiState.value.activePlayer == 2 }
    }

    @Test
    fun pauseStopsTheClock() {
        rule.onNode(button(R.string.timer_start)).performClick()
        await(button(R.string.timer_pause))

        rule.onNode(button(R.string.timer_pause)).performClick()

        await(announced(say(R.string.a11y_announce_paused)), merged = false)
        val stopped = viewModel.uiState.value.players[0].timeRemainingMs
        Thread.sleep(300)
        assertEquals(stopped, viewModel.uiState.value.players[0].timeRemainingMs)
    }

    @Test
    fun resetAsksFirstAndCancelKeepsTheGame() {
        rule.onNode(button(R.string.timer_start)).performClick()
        await(button(R.string.timer_pause))

        rule.onNode(button(R.string.common_reset)).performClick()
        await(hasText(say(R.string.timer_reset_title)))
        rule.onNode(button(R.string.common_cancel)).performClick()

        rule.waitUntil(TIMEOUT_MS) { !exists(hasText(say(R.string.timer_reset_title))) }
        assertNotNull(viewModel.uiState.value.activePlayer)
    }

    @Test
    fun resetConfirmedEndsTheGame() {
        rule.onNode(button(R.string.timer_start)).performClick()
        await(button(R.string.timer_pause))

        rule.onNode(button(R.string.common_reset)).performClick()
        await(hasText(say(R.string.timer_reset_title)))
        rule.onAllNodes(button(R.string.common_reset))[1].performClick()

        await(button(R.string.timer_start))
        assertNull(viewModel.uiState.value.activePlayer)
    }

    @Test
    fun settingsPausesAGameInPlay() {
        rule.onNode(button(R.string.timer_start)).performClick()
        await(button(R.string.timer_pause))

        rule.onNode(button(R.string.timer_settings)).performClick()

        rule.waitUntil(TIMEOUT_MS) { viewModel.uiState.value.isPaused }
    }

    @Test
    fun theSettingsPageOpensAndComesBack() {
        rule.onNode(button(R.string.timer_settings)).performClick()
        await(hasText(say(R.string.settings_tab_modes).uppercase()))

        rule.onNode(hasContentDescription(say(R.string.common_back)) and hasClickAction()).performClick()

        await(button(R.string.timer_start))
        assertFalse(exists(hasText(say(R.string.settings_tab_modes).uppercase())))
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
