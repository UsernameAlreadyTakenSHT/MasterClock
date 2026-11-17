package com.masterclock.app

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.masterclock.app.data.GameDatabase
import com.masterclock.app.data.SavedClockEntity
import com.masterclock.app.logic.AliveMark
import com.masterclock.app.logic.ChessClockState
import com.masterclock.app.logic.ChessClockStateProxy
import com.masterclock.app.logic.PlayerState
import com.masterclock.app.logic.toProxy
import kotlinx.serialization.json.Json
import com.masterclock.app.logic.FlagBehavior
import kotlinx.coroutines.runBlocking
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

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun viewModelOf(scenario: ActivityScenario<MainActivity>): ChessTimerViewModel {
        lateinit var found: ChessTimerViewModel
        scenario.onActivity { found = ViewModelProvider(it)[ChessTimerViewModel::class.java] }
        return found
    }

    /** A game of [settings] with one move played and a clock left running, then closed and brought back. */
    private fun closedAndBack(
        settings: ChessClockSettings.() -> ChessClockSettings,
        sinceTheSaveMs: Long = SINCE_THE_SAVE_MS,
        alsoOf: (ChessTimerViewModel) -> Unit = {},
        check: (atTheMove: List<Long>, back: List<Long>, active: Int, ran: LongRange) -> Unit,
    ) {
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
        Thread.sleep(sinceTheSaveMs)
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
        alsoOf(after)
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

    /**
     * A flag that falls after the save and before the app is closed is in the game's record when it
     * comes back, as it is on its clock. The record was cut at the save while the clock was run on
     * past the flag: an overrun with no flag logged, and none to come.
     */
    @Test
    fun aFlagFallenSinceTheSaveIsStillInTheRecord() {
        assumeTrue(FlavorConfig.keepsGameHistory())
        closedAndBack(
            // Play goes on past the flag, so the clock is still running when the screen closes.
            settings = { copy(flagBehavior = FlagBehavior.NEGATIVE, main = PlayerSettings(initialTimeMs = FLAGS_AFTER_MS)) },
            sinceTheSaveMs = FLAGS_AFTER_MS + 1_500,
            alsoOf = { after ->
                val active = after.uiState.value.activePlayer!!
                assertTrue("player $active is past their time", after.uiState.value.players[active - 1].isOutOfTime)
                // The record as it reached the database, once the resume's own write has landed.
                Thread.sleep(1_500)
                val events = runBlocking { GameDatabase.getDatabase(app).gameLogDao().getRecentLogs(1) }.single().eventsJson
                assertTrue("no flag in the record: $events", events.contains("\"FLAG\""))
            },
        ) { _, _, _, _ -> }
    }

    /** As for a flag: a byoyomi period that went after the save is still in the record. */
    @Test
    fun aPeriodLostSinceTheSaveIsStillInTheRecord() {
        assumeTrue(FlavorConfig.keepsGameHistory())
        closedAndBack(
            // Three seconds of main time, then periods of two: one is gone five seconds in.
            settings = { copy(main = PlayerSettings(mode = TimerMode.BYOYOMI_JAPANESE, initialTimeMs = 3_000, byoyomiPeriods = 5, byoyomiTimeMs = 2_000)) },
            sinceTheSaveMs = 6_500,
            alsoOf = { after ->
                val player = after.uiState.value.players[after.uiState.value.activePlayer!! - 1]
                assertTrue("in byoyomi", player.isInByoyomi)
                assertTrue("a period gone, ${player.byoyomiPeriodsRemaining} left", player.byoyomiPeriodsRemaining < 5)
                Thread.sleep(1_500)
                val events = runBlocking { GameDatabase.getDatabase(app).gameLogDao().getRecentLogs(1) }.single().eventsJson
                assertTrue("no period lost in the record: $events", events.contains("Byoyomi period lost"))
            },
        ) { _, _, _, _ -> }
    }

    /**
     * A game whose flag fell while it ran on -- after its last save, and after the last tick that
     * would have ended it -- comes back finished, to be seen, and is not kept to come back again.
     * The save and its mark are put there by hand: the moment is too narrow to play into.
     */
    @Test
    fun aGameThatEndedWhileItRanOnIsNotKept() {
        val first = ActivityScenario.launch(MainActivity::class.java)
        assumeTrue(FlavorConfig.savesGameInPlay())
        val before = viewModelOf(first)
        rule.waitUntil(TIMEOUT_MS) { before.isReady.value }
        first.onActivity {
            before.resetAllSettings()
            before.reset()
        }
        first.close()
        // What the screen queued as it closed, the reset's clearing among it.
        Thread.sleep(1_000)

        // A running clock with a second left, saved, and marked alive three seconds later: under
        // Freeze, which the defaults are, the flag in between ends the game.
        val dao = GameDatabase.getDatabase(app).gameLogDao()
        val savedAt = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val settings = ChessClockSettings(main = PlayerSettings(initialTimeMs = 60_000))
        val state = ChessClockState(
            players = listOf(PlayerState(timeRemainingMs = 1_000), PlayerState(timeRemainingMs = 60_000)),
            activePlayer = 1, isPaused = false,
        )
        runBlocking {
            dao.saveClock(SavedClockEntity(
                timestamp = savedAt,
                settingsJson = Json.encodeToString(ChessClockSettings.serializer(), settings),
                stateJson = Json.encodeToString(ChessClockStateProxy.serializer(), state.toProxy().copy(savedAtElapsedMs = elapsed)),
            ))
        }
        AliveMark(java.io.File(app.filesDir, "alive_mark")).apply { write(savedAt, elapsed + 3_000); close() }

        val second = ActivityScenario.launch(MainActivity::class.java)
        val after = viewModelOf(second)
        rule.waitUntil(TIMEOUT_MS) { after.isReady.value }
        // Mini has taken it up by itself by now, and let it go: there the save is not waited for.
        if (!FlavorConfig.resumesSavedGameAtLaunch()) {
            rule.waitUntil(TIMEOUT_MS) { after.hasSavedClock.value }
            second.onActivity { after.resumeSavedClock() }
        }
        rule.waitUntil(TIMEOUT_MS) { after.uiState.value.players.firstOrNull()?.isOutOfTime == true }
        assertTrue("it comes back stopped", after.uiState.value.isPaused)
        rule.waitUntil(TIMEOUT_MS) { runBlocking { dao.getSavedClock() } == null }
        second.close()

        // And the next launch has nothing to take up.
        val third = ActivityScenario.launch(MainActivity::class.java)
        val then = viewModelOf(third)
        rule.waitUntil(TIMEOUT_MS) { then.isReady.value }
        Thread.sleep(1_000)
        assertTrue("nothing kept", !then.hasSavedClock.value)
        assertEquals("a clock that has not started", null, then.uiState.value.activePlayer)
        third.close()
    }

    private companion object {
        /** A clock short enough to flag inside the test, and long enough to play the move on. */
        const val FLAGS_AFTER_MS = 4_000L
        const val TIMEOUT_MS = 10_000L
        /** Long enough that the save is plainly not where the game comes back, and short of the ten-second one. */
        const val SINCE_THE_SAVE_MS = 3_500L
        /** The mark's fifth of a second, and the moment between the move and its being seen here. */
        const val TOLERANCE_MS = 400L
    }
}
