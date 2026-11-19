package com.masterclock.app

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import com.masterclock.app.data.SettingsRepository
import com.masterclock.app.logic.ChessTimerViewModel
import com.masterclock.app.logic.FlavorConfig
import com.masterclock.app.logic.TimerMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * A setting chosen on the screen reaching the clock and the disk. core pins what a setting does;
 * this is the wire between the switch and it, which a game is lost to if it breaks.
 */
class SettingsFlowsTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val viewModel get() = ViewModelProvider(rule.activity)[ChessTimerViewModel::class.java]

    private fun say(id: Int) = rule.activity.getString(id)

    private fun exists(matcher: SemanticsMatcher) = rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private fun await(matcher: SemanticsMatcher) = rule.waitUntil(TIMEOUT_MS) { exists(matcher) }

    private fun tap(id: Int) = rule.onNode(hasContentDescription(say(id)) and hasClickAction()).performClick()

    /** What a new process would read, which is the point of saving it. */
    private fun stored() = runBlocking { SettingsRepository(rule.activity.applicationContext).settingsFlow.first() }

    @Before
    fun startFromTheDefaults() {
        rule.waitUntil(TIMEOUT_MS) { viewModel.isReady.value }
        // Modes and Behavior are both tabs of a page the smaller builds cut down.
        assumeTrue(FlavorConfig.hasFullSettingsTabs())
        rule.runOnUiThread {
            viewModel.resetAllSettings()
            viewModel.reset()
        }
        await(hasContentDescription(say(R.string.timer_start)))
    }

    @Test
    fun aModeChosenInTheSettingsReachesTheClockAndTheDisk() {
        tap(R.string.timer_settings)
        await(hasText("Bonus"))
        rule.onNode(hasText("Bonus") and hasClickAction()).performClick()

        rule.waitUntil(TIMEOUT_MS) { viewModel.settings.value.main.mode == TimerMode.FISCHER }
        tap(R.string.common_back)
        await(hasContentDescription(say(R.string.timer_start)))

        rule.waitUntil(TIMEOUT_MS) { stored().main.mode == TimerMode.FISCHER }
    }

    @Test
    fun confirmResetSwitchedOffDropsTheQuestion() {
        tap(R.string.timer_settings)
        await(hasText(say(R.string.settings_tab_behavior)))
        rule.onNode(hasText(say(R.string.settings_tab_behavior))).performClick()
        await(hasText(say(R.string.settings_confirm_reset)))
        rule.onNode(hasText(say(R.string.settings_confirm_reset))).performScrollTo().performClick()

        rule.waitUntil(TIMEOUT_MS) { !viewModel.settings.value.confirmReset }
        rule.waitUntil(TIMEOUT_MS) { !stored().confirmReset }
        tap(R.string.common_back)
        await(hasContentDescription(say(R.string.timer_start)))

        tap(R.string.timer_start)
        rule.waitUntil(TIMEOUT_MS) { viewModel.uiState.value.activePlayer != null }
        tap(R.string.common_reset)
        rule.waitForIdle()

        assertFalse(exists(hasText(say(R.string.timer_reset_title))))
        assertEquals(null, viewModel.uiState.value.activePlayer)
    }

    @Test
    fun aModeChosenWithAGameInPlayAsksBeforeEndingIt() {
        tap(R.string.timer_start)
        rule.waitUntil(TIMEOUT_MS) { viewModel.uiState.value.activePlayer != null }
        rule.runOnUiThread { viewModel.pause() }
        rule.waitUntil(TIMEOUT_MS) { viewModel.uiState.value.isPaused }

        tap(R.string.timer_settings)
        await(hasText("Bonus"))
        rule.onNode(hasText("Bonus") and hasClickAction()).performClick()

        // The question, and nothing changed behind it: applied as the card was tapped, the game in
        // play was gone before anyone had answered.
        await(hasText(say(R.string.preset_end_game_title)))
        assertEquals(TimerMode.SUDDEN_DEATH, viewModel.settings.value.main.mode)
        rule.onNode(hasText(say(R.string.common_cancel)) and hasClickAction()).performClick()
        rule.waitUntil(TIMEOUT_MS) { !exists(hasText(say(R.string.preset_end_game_title))) }
        assertEquals(TimerMode.SUDDEN_DEATH, viewModel.settings.value.main.mode)
        assertEquals(true, viewModel.uiState.value.activePlayer != null)

        rule.onNode(hasText("Bonus") and hasClickAction()).performClick()
        await(hasText(say(R.string.preset_end_game_title)))
        rule.onNode(hasText(say(R.string.preset_end_game_confirm)) and hasClickAction()).performClick()
        rule.waitUntil(TIMEOUT_MS) { viewModel.settings.value.main.mode == TimerMode.FISCHER }
    }

    @Test
    fun resetSettingsAsksAndCancelLeavesThemAlone() {
        assumeTrue(FlavorConfig.hasMoreTab())
        rule.runOnUiThread { viewModel.updateSettings(viewModel.settings.value.copy(main = viewModel.settings.value.main.copy(mode = TimerMode.FISCHER))) }
        rule.waitUntil(TIMEOUT_MS) { viewModel.settings.value.main.mode == TimerMode.FISCHER }
        tap(R.string.timer_settings)
        await(hasText(say(R.string.settings_tab_more)))
        rule.onAllNodes(hasText(say(R.string.settings_tab_more)))[0].performClick()
        await(hasText(say(R.string.settings_more_reset_settings)))
        val reset = rule.onNode(hasText(say(R.string.settings_more_reset_settings)) and hasClickAction())

        reset.performScrollTo().performClick()
        await(hasText(say(R.string.settings_reset_title)))
        assertEquals(TimerMode.FISCHER, viewModel.settings.value.main.mode)
        rule.onNode(hasText(say(R.string.common_cancel)) and hasClickAction()).performClick()
        rule.waitUntil(TIMEOUT_MS) { !exists(hasText(say(R.string.settings_reset_title))) }
        assertEquals(TimerMode.FISCHER, viewModel.settings.value.main.mode)

        reset.performScrollTo().performClick()
        await(hasText(say(R.string.settings_reset_title)))
        rule.onNode(hasText(say(R.string.settings_reset_confirm)) and hasClickAction()).performClick()
        rule.waitUntil(TIMEOUT_MS) { viewModel.settings.value.main.mode == TimerMode.SUDDEN_DEATH }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
