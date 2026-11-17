package com.masterclock.app

import android.os.SystemClock
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import com.masterclock.app.logic.ChessClockSettings
import com.masterclock.app.logic.ChessTimerViewModel
import com.masterclock.app.logic.FlavorConfig
import com.masterclock.app.logic.PlayerSettings
import com.masterclock.app.logic.TimerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * A game closed with a clock running, and where it comes back: at the moment it was closed, by its
 * alive mark, not at its last save. See AliveMark.
 *
 * Closed with the pause-on-leaving off, so that nothing saves the game as the screen goes: the
 * ViewModel is cleared with the clock running, which is as near as a test gets to the app being
 * killed. The last save is then the last move's, [SINCE_THE_SAVE_MS] old, and a game that came back
 * to it would have spent nothing -- several times [TOLERANCE_MS] short.
 *
 * What the clock spent is held against the time that really went by, measured here, not against
 * the clock as last read: on whole seconds the state moves once a second, and a reading up to a
 * second old, with the time the screen takes to close on top, made the three- and four-player games
 * fail a tolerance the two-player one passed by luck.
 */
class AliveMarkFlowTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private fun viewModelOf(scenario: ActivityScenario<MainActivity>): ChessTimerViewModel {
        lateinit var found: ChessTimerViewModel
        scenario.onActivity { found = ViewModelProvider(it)[ChessTimerViewModel::class.java] }
        return found
    }

    /** A game of [settings] with one move played and a clock left running, then closed and brought back. */
    private fun closedAndBack(settings: ChessClockSettings.() -> ChessClockSettings, check: (atTheMove: List<Long>, back: List<Long>, active: Int, ran: LongRange) -> Unit) {
        assumeTrue(FlavorConfig.savesGameInPlay())
        val first = ActivityScenario.launch(MainActivity::class.java)
        val before = viewModelOf(first)
        rule.waitUntil(TIMEOUT_MS) { before.isReady.value }
        first.onActivity {
            before.resetAllSettings()
            before.updateSettings(before.settings.value.settings().copy(pauseOnBackground = false, keepRunningInBackground = false))
            before.reset()
        }
        // The first press starts the game, the second is a move: the save the mark is written for.
        first.onActivity { before.startOrSwitch(1) }
        rule.waitUntil(TIMEOUT_MS) { before.uiState.value.activePlayer != null }
        val mover = before.uiState.value.activePlayer!!
        first.onActivity { before.startOrSwitch(mover) }
        rule.waitUntil(TIMEOUT_MS) { before.uiState.value.activePlayer != mover }
        // The clocks as the move left them, which is exact: nothing has ticked since.
        val movedAt = SystemClock.elapsedRealtime()
        val active = before.uiState.value.activePlayer!!
        val atTheMove = before.uiState.value.players.map { it.timeRemainingMs }
        Thread.sleep(SINCE_THE_SAVE_MS)
        val closing = SystemClock.elapsedRealtime()
        first.close()
        val closed = SystemClock.elapsedRealtime()
        // The clock stopped somewhere between the close being asked and it being done, give or take
        // the mark's own fifth of a second and the moment it took to see the move.
        val ran = (closing - movedAt - TOLERANCE_MS)..(closed - movedAt + TOLERANCE_MS)

        val second = ActivityScenario.launch(MainActivity::class.java)
        val after = viewModelOf(second)
        rule.waitUntil(TIMEOUT_MS) { after.isReady.value && after.hasSavedClock.value }
        // Mini takes its game up by itself; the others from the arbiter's panel, which this is.
        if (!FlavorConfig.resumesSavedGameAtLaunch()) second.onActivity { after.resumeSavedClock() }
        rule.waitUntil(TIMEOUT_MS) { after.uiState.value.activePlayer == active }
        assertTrue("it comes back paused", after.uiState.value.isPaused)
        check(atTheMove, after.uiState.value.players.map { it.timeRemainingMs }, active, ran)
        second.close()
    }

    /** What a clock spent since the move is the time the game ran, not nothing as its save has it. */
    private fun assertRan(what: String, spent: Long, ran: LongRange) {
        assertTrue("$what spent $spent ms, and the game ran $ran since the move it was saved at", spent in ran)
    }

    private fun aClockForEach(players: Int) = closedAndBack({ copy(numberOfPlayers = players) }) { atTheMove, back, active, ran ->
        assertEquals(players, back.size)
        for (i in atTheMove.indices) {
            if (i == active - 1) assertRan("player $active, on the move,", atTheMove[i] - back[i], ran)
            else assertEquals("player ${i + 1}, waiting", atTheMove[i], back[i])
        }
    }

    @Test
    fun twoPlayersComeBackWhereTheGameWasClosed() = aClockForEach(2)

    @Test
    fun threePlayersComeBackWhereTheGameWasClosed() = aClockForEach(3)

    @Test
    fun fourPlayersComeBackWhereTheGameWasClosed() = aClockForEach(4)

    @Test
    fun anHourglassComesBackWithBothItsClocksMoved() = closedAndBack({ copy(main = PlayerSettings(mode = TimerMode.HOURGLASS, initialTimeMs = 60_000)) }) { atTheMove, back, active, ran ->
        val other = if (active == 1) 2 else 1
        // The one on the move ran down, the other up by as much: what leaves one glass fills the other.
        assertRan("player $active, running down,", atTheMove[active - 1] - back[active - 1], ran)
        assertRan("player $other, filling,", back[other - 1] - atTheMove[other - 1], ran)
        assertEquals("the sand between them", atTheMove.sum(), back.sum())
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        /** Long enough that the save is plainly not where the game comes back, and short of the ten-second one. */
        const val SINCE_THE_SAVE_MS = 3_500L
        /** The mark's fifth of a second, and the moment between the move and its being seen here. */
        const val TOLERANCE_MS = 400L
    }
}
