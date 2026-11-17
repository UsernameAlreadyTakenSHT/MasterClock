package com.masterclock.app

import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.masterclock.app.logic.BUILTIN_SHORTCUT_PRESETS
import com.masterclock.app.logic.ChessTimerViewModel
import com.masterclock.app.logic.FlavorConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * The app opened by a launcher shortcut while a game waits to be taken up again, where it is taken
 * up by itself at launch: Mini. The shortcut asks for its own game and gets it. The resume read its
 * save off the main thread and installed it moments later, over the shortcut's game or under it as
 * the timing fell.
 */
class ShortcutAtLaunchFlowTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun viewModelOf(scenario: ActivityScenario<MainActivity>): ChessTimerViewModel {
        lateinit var found: ChessTimerViewModel
        scenario.onActivity { found = ViewModelProvider(it)[ChessTimerViewModel::class.java] }
        return found
    }

    @Test
    fun aShortcutStartsItsOwnGameOverTheOneWaiting() {
        val first = ActivityScenario.launch(MainActivity::class.java)
        // Asked once the activity is up: it is the activity that says which build this is, and asked
        // before it the answer was Complete's, and the test skipped itself on the one build it is for.
        if (!FlavorConfig.resumesSavedGameAtLaunch()) first.close()
        assumeTrue(FlavorConfig.resumesSavedGameAtLaunch())
        val before = viewModelOf(first)
        rule.waitUntil(TIMEOUT_MS) { before.isReady.value }
        first.onActivity {
            before.resetAllSettings()
            before.reset()
        }
        // A game under way, a move played, left as the app is closed: the save Mini takes up.
        first.onActivity { before.startOrSwitch(1) }
        rule.waitUntil(TIMEOUT_MS) { before.uiState.value.activePlayer != null }
        val mover = before.uiState.value.activePlayer!!
        first.onActivity { before.startOrSwitch(mover) }
        rule.waitUntil(TIMEOUT_MS) { before.uiState.value.activePlayer != mover }
        first.close()

        // A time control the waiting game does not have, so that the two cannot be mistaken.
        val waiting = before.settings.value.main
        val shortcut = BUILTIN_SHORTCUT_PRESETS.first { it.settings.main.initialTimeMs != waiting.initialTimeMs || it.settings.main.incrementMs != waiting.incrementMs }
        val intent = Intent(app, MainActivity::class.java).putExtra(PresetShortcuts.EXTRA_SHORTCUT_ID, shortcut.id)
        val second = ActivityScenario.launch<MainActivity>(intent)
        val after = viewModelOf(second)
        rule.waitUntil(TIMEOUT_MS) { after.isReady.value }
        rule.waitUntil(TIMEOUT_MS) { after.settings.value.main.initialTimeMs == shortcut.settings.main.initialTimeMs }
        // And still there once anything a resume had in hand has landed.
        Thread.sleep(SETTLE_MS)
        assertEquals("the shortcut's time", shortcut.settings.main.initialTimeMs, after.settings.value.main.initialTimeMs)
        assertEquals("the shortcut's increment", shortcut.settings.main.incrementMs, after.settings.value.main.incrementMs)
        assertNull("a new game, nobody on the move yet", after.uiState.value.activePlayer)
        assertEquals(shortcut.settings.main.initialTimeMs, after.uiState.value.players[0].timeRemainingMs)
        second.close()
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val SETTLE_MS = 2_000L
    }
}
