package com.masterclock.paper

import android.view.WindowManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import com.masterclock.app.logic.AppFont
import com.masterclock.app.logic.AppInfo
import com.masterclock.app.logic.ChessClockSettings
import com.masterclock.app.logic.ChessTimerViewModel
import com.masterclock.app.logic.PlayerSettings
import com.masterclock.app.logic.TimerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The E-Ink screens beyond the clock's first press, which PaperClockFlowsTest covers: what the middle
 * button reads and does as a game goes on and ends, the time fields and their limits, the switches,
 * the font, the question a change that ends a game asks first, the About screen, and Back on the
 * clock. Each was fixed in November and checked by screenshots alone.
 * Run with `:paper:connectedDebugAndroidTest`; they reset the app's settings on that device.
 */
class PaperScreensFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val viewModel get() = ViewModelProvider(rule.activity)[ChessTimerViewModel::class.java]
    private val settings get() = viewModel.settings.value
    private val state get() = viewModel.uiState.value

    private fun say(id: Int, vararg args: Any) = rule.activity.getString(id, *args)

    private fun exists(matcher: SemanticsMatcher) = rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private fun await(matcher: SemanticsMatcher) = rule.waitUntil(TIMEOUT_MS) { exists(matcher) }

    private fun button(id: Int) = hasText(say(id)) and hasClickAction()

    private fun press(id: Int) = rule.onNode(button(id)).performClick()

    private fun player(number: Int) = rule.onNode(hasContentDescription(say(R.string.a11y_player_clock, number, ""), substring = true))

    private val settingsHeading get() = hasText(say(R.string.timer_settings).uppercase())

    /** The settings as the page writes them: one set of times for both players. */
    private fun setMain(p: PlayerSettings) = rule.runOnUiThread {
        viewModel.updateSettings(settings.copy(main = p, p1Custom = p, p2Custom = p))
        viewModel.reset()
    }

    private fun openSettings() {
        press(R.string.timer_settings)
        await(settingsHeading)
    }

    private fun field(id: Int, of: Int) = rule.onNode(hasContentDescription(say(id, say(of))))

    private fun switch(label: Int) = rule.onNode(hasText(say(label)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))

    @Before
    fun startFromTheDefaults() {
        rule.waitUntil(TIMEOUT_MS) { viewModel.isReady.value }
        rule.runOnUiThread {
            viewModel.resetAllSettings()
            viewModel.reset()
        }
        await(button(R.string.timer_start))
    }

    // --- The clock ---

    @Test
    fun theMiddleButtonReadsStartThenPauseThenResume() {
        press(R.string.timer_start)
        await(button(R.string.timer_pause))

        press(R.string.timer_pause)
        await(button(R.string.timer_resume))
        assertTrue(state.isPaused)

        press(R.string.timer_resume)
        await(button(R.string.timer_pause))
        assertFalse(state.isPaused)
        assertEquals(1, state.activePlayer)
    }

    @Test
    fun aPressOnTheSideNotOnTheMoveDoesNothingRunningOrPaused() {
        press(R.string.timer_start)
        rule.waitUntil(TIMEOUT_MS) { state.activePlayer == 1 }

        player(2).performClick()
        rule.waitForIdle()
        assertEquals(1, state.activePlayer)

        press(R.string.timer_pause)
        await(button(R.string.timer_resume))
        player(2).performClick()
        rule.waitForIdle()
        assertEquals(1, state.activePlayer)
        assertTrue(state.isPaused)
    }

    @Test
    fun resetOnAClockThatHasNotRunAsksNothing() {
        press(R.string.common_reset)
        rule.waitForIdle()

        assertFalse(exists(hasText(say(R.string.timer_reset_title))))
        assertNull(state.activePlayer)
    }

    @Test
    fun aFallenFlagIsSaidAndStartAsksBeforeTheNextGame() {
        setMain(PlayerSettings(mode = TimerMode.SUDDEN_DEATH, initialTimeMs = 1_000))
        press(R.string.timer_start)

        // TalkBack's reading of the side that ran out; the word on screen is inside the collapsed area.
        rule.waitUntil(TIMEOUT_MS) { state.players[0].isOutOfTime }
        await(hasContentDescription(say(R.string.a11y_player_clock, 1, say(R.string.a11y_out_of_time))))
        // Nothing to resume: the middle button starts the next game, and asks first.
        await(button(R.string.timer_start))
        assertFalse(exists(button(R.string.timer_resume)))

        press(R.string.timer_start)
        await(hasText(say(R.string.timer_reset_title)))
        rule.onAllNodes(button(R.string.common_reset)).onLast().performClick()

        rule.waitUntil(TIMEOUT_MS) { state.activePlayer == 1 && !state.isPaused && !state.players[0].isOutOfTime }
    }

    @Test
    fun cancelOnAFallenFlagKeepsWhoLost() {
        setMain(PlayerSettings(mode = TimerMode.SUDDEN_DEATH, initialTimeMs = 1_000))
        press(R.string.timer_start)
        rule.waitUntil(TIMEOUT_MS) { state.players[0].isOutOfTime }
        await(button(R.string.timer_start))

        press(R.string.timer_start)
        await(hasText(say(R.string.timer_reset_title)))
        press(R.string.common_cancel)

        rule.waitUntil(TIMEOUT_MS) { !exists(hasText(say(R.string.timer_reset_title))) }
        assertTrue(state.players[0].isOutOfTime)
    }

    // --- The settings ---

    @Test
    fun theTimeFieldsSetTheClock() {
        openSettings()

        field(R.string.a11y_field_minutes, R.string.settings_initial_time).performScrollTo().performTextReplacement("5")
        // Each field writes its own unit beside the other's as last drawn: the minutes in first.
        rule.waitUntil(TIMEOUT_MS) { settings.main.initialTimeMs == 300_000L }
        field(R.string.a11y_field_seconds, R.string.settings_initial_time).performScrollTo().performTextReplacement("30")

        rule.waitUntil(TIMEOUT_MS) { settings.main.initialTimeMs == 330_000L }
        assertEquals(330_000L, settings.p1Custom.initialTimeMs)
        assertEquals(330_000L, settings.p2Custom.initialTimeMs)

        rule.onNode(hasContentDescription(say(R.string.common_back)) and hasClickAction()).performClick()
        await(button(R.string.timer_start))
        assertEquals(330_000L, state.players[0].timeRemainingMs)
        assertEquals(330_000L, state.players[1].timeRemainingMs)
    }

    @Test
    fun anHourIsTheMostTheFieldsAllow() {
        openSettings()

        field(R.string.a11y_field_minutes, R.string.settings_initial_time).performScrollTo().performTextReplacement("75")
        rule.waitUntil(TIMEOUT_MS) { settings.main.initialTimeMs == 60 * 60_000L }

        // Sixty minutes leaves no room for seconds: each field was clamped alone and the pair reached 60:59.
        field(R.string.a11y_field_seconds, R.string.settings_initial_time).performScrollTo().performTextReplacement("30")
        rule.waitForIdle()
        assertEquals(60 * 60_000L, settings.main.initialTimeMs)
    }

    @Test
    fun theFieldsTakeDigitsOnly() {
        openSettings()
        val before = settings.main.initialTimeMs

        field(R.string.a11y_field_minutes, R.string.settings_initial_time).performScrollTo().performTextReplacement("-")
        field(R.string.a11y_field_minutes, R.string.settings_initial_time).performTextReplacement("1.5")
        rule.waitForIdle()

        // The number keyboard offers both; they read as no number and emptied the field.
        assertEquals(before, settings.main.initialTimeMs)
    }

    @Test
    fun fischerAsksForTheIncrementAndMoveForTheTimeOfAMove() {
        openSettings()
        assertFalse(exists(hasContentDescription(say(R.string.a11y_field_minutes, say(R.string.settings_increment)))))

        rule.onNode(hasText("Fischer") and hasClickAction()).performScrollTo().performClick()
        await(hasContentDescription(say(R.string.a11y_field_seconds, say(R.string.settings_increment))))
        field(R.string.a11y_field_seconds, R.string.settings_increment).performScrollTo().performTextReplacement("5")
        rule.waitUntil(TIMEOUT_MS) { settings.main.mode == TimerMode.FISCHER && settings.main.incrementMs == 5_000L }

        rule.onNode(hasText("Move") and hasClickAction()).performScrollTo().performClick()
        await(hasContentDescription(say(R.string.a11y_field_seconds, say(R.string.settings_move_time))))
        // A move timer has no time for the game, only for each move.
        assertFalse(exists(hasContentDescription(say(R.string.a11y_field_minutes, say(R.string.settings_initial_time)))))
        assertEquals(TimerMode.MOVE_TIMER_STANDARD, settings.main.mode)
    }

    @Test
    fun eachSwitchIsOneNodeAndChangesItsSetting() {
        openSettings()

        fun flip(label: Int, read: (ChessClockSettings) -> Boolean) {
            val before = read(settings)
            switch(label).performScrollTo()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, if (before) ToggleableState.On else ToggleableState.Off))
                .performClick()
            rule.waitUntil(TIMEOUT_MS) { read(settings) != before }
            // One node with its label and its state: TalkBack stopped twice, on the label and on an
            // unlabelled "Switch, off".
            switch(label).assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, if (before) ToggleableState.Off else ToggleableState.On))
        }

        flip(R.string.settings_reverse_colors) { it.eInkDarkMode }
        flip(R.string.settings_keep_awake) { it.forceScreenOn }
        flip(R.string.settings_fullscreen) { it.fullscreenMode }
        flip(R.string.settings_haptic_feedback) { it.hapticFeedback }
        // One switch for both sounds of this build: the move and the flag.
        flip(R.string.settings_sound) { it.playSwitchSound }
        assertTrue(settings.tripleBeepTimeUp)
    }

    @Test
    fun keepAwakeReachesTheWindow() {
        fun kept() = rule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        rule.waitUntil(TIMEOUT_MS) { kept() }

        openSettings()
        switch(R.string.settings_keep_awake).performScrollTo().performClick()
        rule.waitUntil(TIMEOUT_MS) { !kept() }

        switch(R.string.settings_keep_awake).performClick()
        rule.waitUntil(TIMEOUT_MS) { kept() }
    }

    @Test
    fun aFontCardIsChosenAndSaysSo() {
        openSettings()
        val mono = hasText("Noto Sans Mono") and hasClickAction()

        rule.onNode(mono).performScrollTo().performClick()

        rule.waitUntil(TIMEOUT_MS) { settings.appFont == AppFont.NOTO_SANS_MONO }
        rule.onNode(mono).assert(isSelected())
        rule.onNode(hasText(say(R.string.font_system)) and hasClickAction()).assert(!isSelected())
    }

    @Test
    fun aChangeThatEndsTheGameAsksAndCancelKeepsIt() {
        press(R.string.timer_start)
        await(button(R.string.timer_pause))
        openSettings()

        rule.onNode(hasText("Fischer") and hasClickAction()).performScrollTo().performClick()
        await(hasText(say(R.string.timer_reset_title)))
        press(R.string.common_cancel)

        rule.waitUntil(TIMEOUT_MS) { !exists(hasText(say(R.string.timer_reset_title))) }
        assertEquals(TimerMode.SUDDEN_DEATH, settings.main.mode)
        assertNotNull(state.activePlayer)
    }

    @Test
    fun aChangeThatEndsTheGameIsMadeOnceConfirmed() {
        press(R.string.timer_start)
        await(button(R.string.timer_pause))
        openSettings()

        rule.onNode(hasText("Fischer") and hasClickAction()).performScrollTo().performClick()
        await(hasText(say(R.string.timer_reset_title)))
        rule.onAllNodes(button(R.string.common_reset)).onLast().performClick()

        rule.waitUntil(TIMEOUT_MS) { settings.main.mode == TimerMode.FISCHER }
        rule.waitUntil(TIMEOUT_MS) { state.activePlayer == null }
    }

    // --- About ---

    @Test
    fun theVersionOpensAboutWithItsThreeTabs() {
        openSettings()
        rule.onNode(hasText(say(R.string.settings_version, BuildConfig.VERSION_NAME)) and hasClickAction()).performScrollTo().performClick()

        await(hasText(say(R.string.app_name)))
        // What changed, first: the newest entry's notes.
        await(hasText("• " + AppInfo.changelogFor().first().notes.first()))

        rule.onNode(hasText(say(R.string.credits_tab_credits)) and hasClickAction()).performClick()
        await(hasText(AppInfo.credits().first().title))

        rule.onNode(hasText(say(R.string.credits_tab_licenses)) and hasClickAction()).performClick()
        await(hasText(say(R.string.credits_libraries)))

        rule.onNode(hasContentDescription(say(R.string.common_back)) and hasClickAction()).performClick()
        await(settingsHeading)
    }

    // --- Back on the clock ---

    @Test
    fun backOnTheClockAsksForASecondPressThenLeavesWithTheGameKept() {
        press(R.string.timer_start)
        await(button(R.string.timer_pause))

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        await(hasText(say(R.string.back_press_again_to_exit)))
        assertEquals(Lifecycle.State.RESUMED, rule.activity.lifecycle.currentState)

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitUntil(TIMEOUT_MS) { !rule.activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        // Sent to the background, as Android sends a root activity, not finished: the game is kept.
        assertFalse(rule.activity.isFinishing)
        assertNotNull(state.activePlayer)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
