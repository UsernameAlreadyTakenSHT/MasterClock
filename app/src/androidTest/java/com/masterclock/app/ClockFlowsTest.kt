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
import com.masterclock.app.logic.FlavorConfig
import com.masterclock.app.logic.TimerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
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
        // The words in the device's language: "minute" is not in "Minuten", and this failed in German.
        fun unit(id: Int) = rule.activity.resources.getQuantityString(id, 2, 2).replace("2", "").trim()
        val minutes = unit(R.plurals.a11y_minutes)
        val seconds = unit(R.plurals.a11y_seconds)
        val running = playerDescription(1)
        assertTrue(running, running.contains(minutes))
        assertFalse(running, running.contains(seconds))

        control(R.string.timer_pause).performClick()
        await(hasContentDescription(say(R.string.timer_resume)))

        rule.waitUntil(TIMEOUT_MS) { playerDescription(1).contains(seconds) }
    }

    @Test
    fun berserkHalvesOneClockAndPassesNoTurn() {
        // Lite and Mini have no Behaviour tab, where Berserk is allowed, and no import to bring it in.
        assumeTrue(FlavorConfig.hasFullSettingsTabs())
        rule.runOnUiThread { viewModel.updateSettings(viewModel.settings.value.copy(allowBerserk = true)) }
        val button = hasText(say(R.string.timer_berserk)) and hasClickAction()
        await(button)

        // A press on the face passes the turn on its way down, which a button inside it would too.
        rule.onAllNodes(button)[0].performClick()

        rule.waitUntil(TIMEOUT_MS) { viewModel.uiState.value.players.any { it.hasBerserked } }
        val players = viewModel.uiState.value.players
        assertNull(viewModel.uiState.value.activePlayer)
        assertEquals(listOf(300_000L, 600_000L), players.map { it.timeRemainingMs }.sorted())
    }

    @Test
    fun aBerserkBeforeTheFirstPressGoesInTheLog() {
        // Lite and Mini have no Behaviour tab, where Berserk is allowed, and no import to bring it in.
        assumeTrue(FlavorConfig.hasFullSettingsTabs())
        // The game looked for is the one played here: a previous run's Berserk game, newest in the
        // history, would otherwise answer for it.
        rule.waitUntil(TIMEOUT_MS) { viewModel.isHistoryLoaded.value }
        rule.runOnUiThread { viewModel.clearAllLogs() }
        rule.waitUntil(TIMEOUT_MS) { viewModel.gameHistory.value.isEmpty() }
        rule.runOnUiThread { viewModel.updateSettings(viewModel.settings.value.copy(allowBerserk = true)) }
        val button = hasText(say(R.string.timer_berserk)) and hasClickAction()
        await(button)
        rule.onAllNodes(button)[0].performClick()
        rule.waitUntil(TIMEOUT_MS) { viewModel.uiState.value.players.any { it.hasBerserked } }

        control(R.string.timer_start).performClick()
        rule.waitUntil(TIMEOUT_MS) { viewModel.uiState.value.activePlayer != null }
        // One move, so that the game is one Last Games keeps: an empty one is dropped, and this passed
        // only on a device whose history some earlier game had already filled.
        rule.runOnUiThread { viewModel.startOrSwitch(viewModel.uiState.value.activePlayer!!) }
        rule.runOnUiThread { viewModel.reset() }

        rule.waitUntil(TIMEOUT_MS) { viewModel.gameHistory.value.firstOrNull()?.events.orEmpty().any { it.eventType == "BERSERK" } }
    }

    @Test
    fun aRunningGameGoesOnThroughARecreation() {
        // Pause when in background is on by default, and a recreation is not the background: a
        // tablet or a foldable ignores the portrait lock, and turning one paused the game.
        assertTrue(viewModel.settings.value.pauseOnBackground)
        control(R.string.timer_start).performClick()
        rule.waitUntil(TIMEOUT_MS) { viewModel.uiState.value.activePlayer != null && !viewModel.uiState.value.isPaused }
        rule.activityRule.scenario.recreate()
        rule.waitUntil(TIMEOUT_MS) { viewModel.isReady.value }
        rule.waitForIdle()
        assertFalse(viewModel.uiState.value.isPaused)
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

    @Test
    fun nothingBeneathTheArbitersPanelTakesATouch() {
        assumeTrue(FlavorConfig.hasArbitre())
        // PHASES: the bar sits low on the screen, clear of the panel, under the dimmed area alone.
        // With two clocks the bar is in the middle, and the panel itself would be what stopped it.
        rule.runOnUiThread {
            val settings = viewModel.settings.value
            viewModel.updateSettings(settings.copy(main = settings.main.copy(mode = TimerMode.PHASES)))
        }
        await(hasContentDescription(say(R.string.timer_start)))
        control(R.string.timer_start).performClick()
        await(hasContentDescription(say(R.string.timer_pause)))
        control(R.string.timer_pause).performClick()
        await(hasContentDescription(say(R.string.timer_resume)))
        control(R.string.timer_arbitre).performClick()
        await(hasText(say(R.string.timer_arbitre_control)))

        control(R.string.timer_resume).performClick()
        rule.waitForIdle()

        assertTrue(viewModel.uiState.value.isPaused)
        assertTrue(viewModel.uiState.value.isArbitreMode)
    }

    private val YOUR_TURN get() = say(R.string.a11y_player_clock_active, 1, "").substringAfterLast(",").trim()

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
