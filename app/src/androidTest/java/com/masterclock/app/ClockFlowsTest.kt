package com.masterclock.app

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The clock screen as a player and a screen reader meet it: real touches on the real activity, and
 * what TalkBack is handed, which is the semantics tree.
 *
 * The logic is pinned in `core`, and nothing there can see that a press reaches it, that a dialog
 * stands between Reset and the game, or that a turn reaches the live region. These cover the paths
 * where that would cost a game. Run with `connectedCompleteDebugAndroidTest`, on a device or
 * emulator; they reset the app's settings.
 */
class ClockFlowsTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val viewModel get() = ViewModelProvider(rule.activity)[ChessTimerViewModel::class.java]

    private fun say(id: Int, vararg args: Any) = rule.activity.getString(id, *args)

    private fun exists(matcher: SemanticsMatcher, merged: Boolean = true) =
        rule.onAllNodes(matcher, useUnmergedTree = !merged).fetchSemanticsNodes().isNotEmpty()

    private fun await(matcher: SemanticsMatcher, merged: Boolean = true) =
        rule.waitUntil(TIMEOUT_MS) { exists(matcher, merged) }

    private fun described(text: String) = hasContentDescription(text, substring = true)

    /** The live region, which is what TalkBack speaks without being asked. */
    private fun announced(text: String) = hasContentDescription(text) and SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)

    private fun control(id: Int) = rule.onNode(hasContentDescription(say(id)) and hasClickAction())

    private fun player(number: Int) = rule.onNode(described(say(R.string.a11y_player_clock, number, "")))

    private fun playerOnTheMove(number: Int) = described(say(R.string.a11y_player_clock, number, "")) and described(YOUR_TURN)

    @Before
    fun startFromTheDefaults() {
        rule.waitUntil(TIMEOUT_MS) { viewModel.isReady.value }
        rule.runOnUiThread {
            viewModel.resetAllSettings()
            viewModel.reset()
        }
        await(hasContentDescription(say(R.string.timer_start)))
    }

    @Test
    fun startGivesTheClockToPlayerOneAndSaysSo() {
        control(R.string.timer_start).performClick()

        await(playerOnTheMove(1))
        await(announced(say(R.string.a11y_announce_to_move, 1)), merged = false)
        assertEquals(1, viewModel.uiState.value.activePlayer)
    }

    @Test
    fun aPressPassesTheTurnAndSaysSo() {
        control(R.string.timer_start).performClick()
        await(playerOnTheMove(1))

        player(1).performClick()

        await(playerOnTheMove(2))
        await(announced(say(R.string.a11y_announce_to_move, 2)), merged = false)
        assertEquals(2, viewModel.uiState.value.activePlayer)
    }

    @Test
    fun aPressOnTheClockThatIsNotRunningDoesNothing() {
        control(R.string.timer_start).performClick()
        await(playerOnTheMove(1))

        player(2).performClick()
        rule.waitForIdle()

        assertEquals(1, viewModel.uiState.value.activePlayer)
    }

    @Test
    fun pauseStopsTheClockAndResumeStartsIt() {
        control(R.string.timer_start).performClick()
        await(playerOnTheMove(1))

        control(R.string.timer_pause).performClick()

        await(hasContentDescription(say(R.string.timer_resume)))
        await(announced(say(R.string.a11y_announce_paused)), merged = false)
        val stopped = viewModel.uiState.value.players[0].timeRemainingMs
        Thread.sleep(300)
        rule.waitForIdle()
        assertEquals(stopped, viewModel.uiState.value.players[0].timeRemainingMs)

        control(R.string.timer_resume).performClick()

        await(hasContentDescription(say(R.string.timer_pause)))
        await(announced(say(R.string.a11y_announce_to_move, 1)), merged = false)
        rule.waitUntil(TIMEOUT_MS) { viewModel.uiState.value.players[0].timeRemainingMs < stopped }
    }

    @Test
    fun resetAsksFirstAndCancelKeepsTheGame() {
        control(R.string.timer_start).performClick()
        await(playerOnTheMove(1))

        control(R.string.common_reset).performClick()

        await(hasText(say(R.string.timer_reset_title)))
        rule.onNode(hasText(say(R.string.common_cancel))).performClick()

        rule.waitUntil(TIMEOUT_MS) { !exists(hasText(say(R.string.timer_reset_title))) }
        assertNotNull(viewModel.uiState.value.activePlayer)
    }

    @Test
    fun resetConfirmedEndsTheGame() {
        control(R.string.timer_start).performClick()
        await(playerOnTheMove(1))

        control(R.string.common_reset).performClick()
        await(hasText(say(R.string.timer_reset_title)))
        rule.onNode(hasText(say(R.string.common_reset)) and hasClickAction()).performClick()

        await(hasContentDescription(say(R.string.timer_start)))
        assertNull(viewModel.uiState.value.activePlayer)
    }

    @Test
    fun resetWithNoGameRunningAsksNothing() {
        control(R.string.common_reset).performClick()
        rule.waitForIdle()

        assertFalse(exists(hasText(say(R.string.timer_reset_title))))
    }

    @Test
    fun aRunningClockIsDescribedByStepsAndAStoppedOneExactly() {
        control(R.string.timer_start).performClick()

        // Whole minutes while it runs: exact seconds kept TalkBack reading a focused clock aloud.
        await(playerOnTheMove(1))
        val running = playerDescription(1)
        assertTrue(running, running.contains("minute"))
        assertFalse(running, running.contains("second"))

        control(R.string.timer_pause).performClick()
        await(hasContentDescription(say(R.string.timer_resume)))

        rule.waitUntil(TIMEOUT_MS) { playerDescription(1).contains("second") }
    }

    @Test
    fun aClockOffersTalkBackTheSwitchByName() {
        val action = player(1).fetchSemanticsNode().config[SemanticsActions.OnClick]
        assertEquals(say(R.string.timer_switch_turn), action.label)
    }

    @Test
    fun theControlsAreOnScreen() {
        control(R.string.timer_start).assertIsDisplayed()
        control(R.string.common_reset).assertIsDisplayed()
    }

    private fun playerDescription(number: Int) =
        player(number).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()

    private val YOUR_TURN get() = say(R.string.a11y_player_clock_active, 1, "").substringAfterLast(",").trim()

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
