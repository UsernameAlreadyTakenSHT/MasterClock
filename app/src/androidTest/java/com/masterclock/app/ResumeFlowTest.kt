package com.masterclock.app

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.masterclock.app.data.GameDatabase
import com.masterclock.app.logic.ChessTimerViewModel
import com.masterclock.app.logic.FlavorConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * A game left and taken up again: the activity closed for good, so its ViewModel is cleared and the
 * next one reads only what reached the database. That is also the proof that the writes queued as the
 * screen closes finish, which is what a game depends on and nothing in `core` can reach.
 */
class ResumeFlowTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun say(id: Int) = app.getString(id)

    private fun viewModelOf(scenario: ActivityScenario<MainActivity>): ChessTimerViewModel {
        lateinit var found: ChessTimerViewModel
        scenario.onActivity { found = ViewModelProvider(it)[ChessTimerViewModel::class.java] }
        return found
    }

    private fun tap(id: Int) = rule.onNode(hasContentDescription(say(id)) and hasClickAction()).performClick()

    @Test
    fun aGameLeftComesBackWhereItStood() {
        val first = ActivityScenario.launch(MainActivity::class.java)
        // Only the arbiter's panel can take a saved game back up.
        assumeTrue(FlavorConfig.hasArbitre())
        val before = viewModelOf(first)
        rule.waitUntil(TIMEOUT_MS) { before.isReady.value }
        first.onActivity {
            before.resetAllSettings()
            before.reset()
        }
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(hasContentDescription(say(R.string.timer_start))).fetchSemanticsNodes().isNotEmpty() }

        tap(R.string.timer_start)
        rule.waitUntil(TIMEOUT_MS) { before.uiState.value.activePlayer == 1 }
        Thread.sleep(1_200)
        first.onActivity { before.startOrSwitch(1) }
        rule.waitUntil(TIMEOUT_MS) { before.uiState.value.activePlayer == 2 }
        tap(R.string.timer_pause)
        rule.waitUntil(TIMEOUT_MS) { before.uiState.value.isPaused }
        val left = before.uiState.value.players
        first.close()

        val second = ActivityScenario.launch(MainActivity::class.java)
        val after = viewModelOf(second)
        rule.waitUntil(TIMEOUT_MS) { after.isReady.value }
        rule.waitUntil(TIMEOUT_MS) { after.hasSavedClock.value }

        tap(R.string.timer_arbitre)
        val resume = hasText(say(R.string.timer_resume)) and hasClickAction()
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(resume).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(resume).performClick()

        rule.waitUntil(TIMEOUT_MS) { after.uiState.value.activePlayer == 2 }
        val back = after.uiState.value.players
        assertEquals(1, back[0].moveCount)
        assertEquals(left[0].timeRemainingMs, back[0].timeRemainingMs)
        assertTrue("${back[1].timeRemainingMs} against ${left[1].timeRemainingMs}", back[1].timeRemainingMs <= left[1].timeRemainingMs)
        second.close()
    }

    @Test
    fun aNewGameLeavesTheInterruptedOneOnOfferAtFirst() {
        val first = ActivityScenario.launch(MainActivity::class.java)
        assumeTrue(FlavorConfig.hasArbitre())
        val before = viewModelOf(first)
        rule.waitUntil(TIMEOUT_MS) { before.isReady.value }
        first.onActivity {
            before.resetAllSettings()
            before.reset()
        }
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(hasContentDescription(say(R.string.timer_start))).fetchSemanticsNodes().isNotEmpty() }
        tap(R.string.timer_start)
        rule.waitUntil(TIMEOUT_MS) { before.uiState.value.activePlayer == 1 }
        tap(R.string.timer_pause)
        rule.waitUntil(TIMEOUT_MS) { before.hasSavedClock.value }
        first.close()

        val second = ActivityScenario.launch(MainActivity::class.java)
        val after = viewModelOf(second)
        rule.waitUntil(TIMEOUT_MS) { after.isReady.value && after.hasSavedClock.value }
        val saved = runBlocking { GameDatabase.getDatabase(app).gameLogDao().getSavedClock() }
        second.onActivity { after.startOrSwitch(1) }
        rule.waitUntil(TIMEOUT_MS) { after.uiState.value.activePlayer != null }
        Thread.sleep(1_500)

        val still = runBlocking { GameDatabase.getDatabase(app).gameLogDao().getSavedClock() }
        assertEquals(saved?.timestamp, still?.timestamp)
        second.close()
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
