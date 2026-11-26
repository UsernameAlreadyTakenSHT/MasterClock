package com.masterclock.app

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import com.masterclock.app.logic.AppFont
import com.masterclock.app.logic.ChessTimerViewModel
import com.masterclock.app.logic.ClockOrientation
import com.masterclock.app.logic.FlavorConfig
import com.masterclock.app.logic.PlayerSettings
import com.masterclock.app.logic.TimerMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Collections
import kotlin.math.abs

/**
 * A Display or Audio setting reaching the clock as the players meet it: its colours, the size of
 * each side, which way its digits face, their font, and its sounds. core pins what each setting
 * means; nothing there can see the screen, nor hear it, and a setting that stopped at the settings
 * page would pass every test it has.
 *
 * What is on screen is read from the pixels, captured as drawn; what is heard, from
 * listenToTheSounds, which names every sound the clock asks for. They reset the app's settings.
 */
class ClockSettingsFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val viewModel get() = ViewModelProvider(rule.activity)[ChessTimerViewModel::class.java]
    private val settings get() = viewModel.settings.value
    private val state get() = viewModel.uiState.value

    private val heard: MutableList<String> = Collections.synchronizedList(mutableListOf())

    private fun say(id: Int, vararg args: Any) = rule.activity.getString(id, *args)

    private fun exists(matcher: SemanticsMatcher) = rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private fun await(matcher: SemanticsMatcher) = rule.waitUntil(TIMEOUT_MS) { exists(matcher) }

    private fun control(id: Int) = rule.onNode(hasContentDescription(say(id)) and hasClickAction())

    private fun player(number: Int) = rule.onNode(hasContentDescription(say(R.string.a11y_player_clock, number, ""), substring = true))

    private fun switch(label: Int) = rule.onNode(hasText(say(label)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))

    private fun change(edit: (com.masterclock.app.logic.ChessClockSettings) -> com.masterclock.app.logic.ChessClockSettings) =
        rule.runOnUiThread { viewModel.updateSettings(edit(settings)); viewModel.reset() }

    /** Into the settings, on [tab]; back to the clock is [backToTheClock]. */
    private fun openSettingsOn(tab: Int) {
        control(R.string.timer_settings).performClick()
        await(hasText(say(tab)))
        rule.onAllNodes(hasText(say(tab)) and hasClickAction())[0].performClick()
    }

    private fun backToTheClock() {
        control(R.string.common_back).performClick()
        await(hasContentDescription(say(R.string.timer_start)))
    }

    /** The game ended, so that the clock comes back from the settings reading Start. */
    private fun clearTheClock() {
        rule.runOnUiThread { viewModel.reset() }
        await(hasContentDescription(say(R.string.timer_start)))
    }

    private fun start() {
        control(R.string.timer_start).performClick()
        rule.waitUntil(TIMEOUT_MS) { state.activePlayer == 1 }
    }

    private fun height(number: Int) = player(number).fetchSemanticsNode().boundsInRoot.height

    private fun picture(number: Int): Bitmap = player(number).captureToImage().asAndroidBitmap()

    /** The colour of a side away from its digits: a tenth in from its left edge, half way down. */
    private fun backgroundOf(number: Int): Int = picture(number).let { it.getPixel(it.width / 10, it.height / 2) }

    private fun near(a: Int, b: Int) =
        abs(Color.red(a) - Color.red(b)) + abs(Color.green(a) - Color.green(b)) + abs(Color.blue(a) - Color.blue(b)) < 30

    /**
     * Width and height of what is drawn on player 2's background: its digits and labels. Read below
     * the top quarter, where the control bar lies over the side and is in the capture: a wide pale
     * bar, counted as ink, made every reading wider than tall, turned or not.
     */
    private fun ink(number: Int): Pair<Int, Int> {
        val p = picture(number)
        val bg = p.getPixel(p.width / 10, p.height / 2)
        var left = p.width; var right = -1; var top = p.height; var bottom = -1
        for (y in p.height / 4 until p.height step 2) for (x in 0 until p.width step 2) {
            val c = p.getPixel(x, y)
            if (Color.alpha(c) > 200 && !near(c, bg) &&
                abs(Color.red(c) - Color.red(bg)) + abs(Color.green(c) - Color.green(bg)) + abs(Color.blue(c) - Color.blue(bg)) > 120) {
                if (x < left) left = x; if (x > right) right = x; if (y < top) top = y; if (y > bottom) bottom = y
            }
        }
        return (right - left) to (bottom - top)
    }

    @Before
    fun startFromTheDefaults() {
        rule.waitUntil(TIMEOUT_MS) { viewModel.isReady.value }
        // Display and Audio are tabs of the full settings only.
        assumeTrue(FlavorConfig.hasFullSettingsTabs())
        rule.runOnUiThread {
            viewModel.resetAllSettings()
            viewModel.reset()
            viewModel.listenToTheSounds { heard += it }
        }
        await(hasContentDescription(say(R.string.timer_start)))
    }

    @After
    fun leaveNothingBehind() {
        if (!FlavorConfig.hasFullSettingsTabs()) return
        rule.runOnUiThread {
            viewModel.listenToTheSounds(null)
            viewModel.reset()
            viewModel.resetAllSettings()
        }
    }

    // --- Display ---

    @Test
    fun theColoursChosenAreTheColoursOfEachSide() {
        change { it.copy(activeColor = MAGENTA, inactiveColor = CYAN) }
        start()

        // The colour fades in over 300ms.
        rule.waitUntil(TIMEOUT_MS) { near(backgroundOf(1), MAGENTA.toInt()) }
        rule.waitUntil(TIMEOUT_MS) { near(backgroundOf(2), CYAN.toInt()) }

        player(1).performClick()
        rule.waitUntil(TIMEOUT_MS) { near(backgroundOf(2), MAGENTA.toInt()) && near(backgroundOf(1), CYAN.toInt()) }
    }

    @Test
    fun activeSideBiggerGrowsWhoeverIsOnTheMove() {
        start()
        Thread.sleep(1_000)
        rule.waitForIdle()
        // Off by default: the two halves alike, whoever is on the move.
        assertEquals(height(1), height(2), 2f)
        clearTheClock()

        openSettingsOn(R.string.settings_tab_display)
        switch(R.string.settings_active_side_bigger).performScrollTo().performClick()
        rule.waitUntil(TIMEOUT_MS) { settings.activePlayerSideBigger }
        backToTheClock()

        start()
        // Half as big again, after a spring that takes its time.
        rule.waitUntil(TIMEOUT_MS) { height(1) > height(2) * 1.3f }
        player(1).performClick()
        rule.waitUntil(TIMEOUT_MS) { height(2) > height(1) * 1.3f }
    }

    @Test
    fun anOrientationTurnsTheDigitsOnTheSide() {
        // Upright, the reading is wider than it is tall.
        val (w, h) = ink(2)
        assertTrue("upright ink ${w}x$h", w > h)

        openSettingsOn(R.string.settings_tab_display)
        rule.onNode(hasText(say(R.string.settings_horiz_left)) and hasClickAction()).performScrollTo().performClick()
        rule.waitUntil(TIMEOUT_MS) { settings.clockOrientation == ClockOrientation.HORIZONTAL_LEFT }
        backToTheClock()

        // Turned a quarter, for players sitting at the ends of the table: taller than it is wide.
        rule.waitUntil(TIMEOUT_MS) { ink(2).let { (tw, th) -> th > tw } }
    }

    @Test
    fun theFontChosenIsTheFaceOfTheDigits() {
        val system = picture(2)

        change { it.copy(appFont = AppFont.NOTO_SANS_MONO) }

        rule.waitUntil(TIMEOUT_MS) { settings.appFont == AppFont.NOTO_SANS_MONO && !picture(2).sameAs(system) }
    }

    // --- Audio ---

    @Test
    fun theSwitchSoundIsHeardOnlyOnceSwitchedOn() {
        start()
        player(1).performClick()
        rule.waitUntil(TIMEOUT_MS) { state.activePlayer == 2 }
        rule.waitForIdle()
        // Off by default.
        assertFalse(heard.contains("switch"))
        clearTheClock()

        openSettingsOn(R.string.settings_tab_audio)
        switch(R.string.settings_play_switch_sound).performScrollTo().performClick()
        rule.waitUntil(TIMEOUT_MS) { settings.playSwitchSound }
        backToTheClock()

        heard.clear()
        start()
        player(1).performClick()
        rule.waitUntil(TIMEOUT_MS) { heard.contains("switch") }
    }

    @Test
    fun theTripleBeepIsHeardWhenTimeRunsOutOnceSwitchedOn() {
        openSettingsOn(R.string.settings_tab_audio)
        switch(R.string.settings_triple_beep).performScrollTo().performClick()
        rule.waitUntil(TIMEOUT_MS) { settings.tripleBeepTimeUp }
        backToTheClock()

        val second = PlayerSettings(mode = TimerMode.SUDDEN_DEATH, initialTimeMs = 1_000)
        change { it.copy(main = second, p1Custom = second, p2Custom = second) }
        heard.clear()
        start()

        rule.waitUntil(TIMEOUT_MS) { state.players[0].isOutOfTime }
        rule.waitUntil(TIMEOUT_MS) { heard.contains("triple") }
    }

    @Test
    fun noTripleBeepWhenItIsSwitchedOff() {
        val second = PlayerSettings(mode = TimerMode.SUDDEN_DEATH, initialTimeMs = 1_000)
        change { it.copy(main = second, p1Custom = second, p2Custom = second, tripleBeepTimeUp = false) }
        heard.clear()
        start()

        rule.waitUntil(TIMEOUT_MS) { state.players[0].isOutOfTime }
        Thread.sleep(1_000)
        assertFalse(heard.contains("triple"))
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val MAGENTA = 0xFFFF00FFL
        const val CYAN = 0xFF00FFFFL
    }
}
