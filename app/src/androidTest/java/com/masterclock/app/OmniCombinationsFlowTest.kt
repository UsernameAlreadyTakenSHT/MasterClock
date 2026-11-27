package com.masterclock.app

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import com.masterclock.app.logic.ChessTimerViewModel
import com.masterclock.app.logic.FlavorConfig
import com.masterclock.app.logic.OmniGameSettings
import com.masterclock.app.logic.OmniPhaseSettings
import com.masterclock.app.logic.OmniRoundSettings
import com.masterclock.app.logic.OmniSettings
import com.masterclock.app.logic.OmniTimerViewModel
import com.masterclock.app.logic.PlayerOrderType
import com.masterclock.app.logic.RoundTurnLogic
import com.masterclock.app.logic.TransitionType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The Omni combinations a table is most likely to set up and no test played: the Snake order, a
 * random order drawn each round, phases, a pause between turns -- on its own or waiting for a hand --
 * a session taken up again in the middle of a round, and what TalkBack hears through all of it. Six
 * screen tests covered Omni, all on the default plain order; both reviews of Omni asked for these.
 *
 * What is checked is what the table sees: the "PLAYER n" on screen, the overlay's words, the live
 * region. The ViewModel is read beside it only to say why something failed.
 */
class OmniCombinationsFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val viewModel get() = ViewModelProvider(rule.activity)[ChessTimerViewModel::class.java]
    private val omni get() = ViewModelProvider(rule.activity)[OmniTimerViewModel::class.java]
    private val state get() = omni.omniState.value

    private fun say(id: Int) = rule.activity.getString(id)
    private fun say(id: Int, n: Int) = rule.activity.getString(id, n)

    private fun exists(matcher: SemanticsMatcher, merged: Boolean = true) =
        rule.onAllNodes(matcher, useUnmergedTree = !merged).fetchSemanticsNodes().isNotEmpty()

    private fun await(matcher: SemanticsMatcher, merged: Boolean = true) =
        rule.waitUntil(TIMEOUT_MS) { exists(matcher, merged) }

    private fun control(id: Int) = rule.onNode(hasContentDescription(say(id)) and hasClickAction())

    /** The live region, which is what TalkBack speaks without being asked. */
    private fun announced(text: String) = hasContentDescription(text) and SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)

    /** The player the screen shows on turn, from its "PLAYER n". */
    private fun shownPlayer(players: Int): Int {
        val shown = (1..players).filter { exists(hasText(say(R.string.common_player_n, it), ignoreCase = true)) }
        assertEquals("the screen shows one player on turn", 1, shown.size)
        return shown.single()
    }

    private fun next() {
        control(R.string.omni_next).performClick()
        rule.waitForIdle()
    }

    @Before
    fun startFromTheDefaults() {
        rule.waitUntil(TIMEOUT_MS) { viewModel.isReady.value }
        assumeTrue(FlavorConfig.hasOmni())
        rule.runOnUiThread {
            viewModel.resetAllSettings()
            viewModel.reset()
        }
        await(hasContentDescription(say(R.string.timer_settings)))
        rule.runOnUiThread { omni.resetOmni() }
    }

    @After
    fun backToOmniDefaults() {
        rule.runOnUiThread {
            omni.resetOmni()
            omni.updateOmniSettings(OmniSettings())
        }
    }

    private fun setUp(settings: OmniSettings) = rule.runOnUiThread { omni.updateOmniSettings(settings.copy(launchCountdownMs = 0)) }

    /** From the clock through the wizard to a running session, as OmniFlowTest does. */
    private fun launchASession() {
        control(R.string.timer_settings).performClick()
        await(hasTestTag("more_modes"))
        rule.onNode(hasTestTag("more_modes")).performScrollTo().performClick()
        await(hasText("Omni-Timer", substring = true))
        rule.onNode(hasText("Omni-Timer", substring = true) and hasClickAction()).performScrollTo().performClick()
        val next = hasText(say(R.string.omni_cfg_next_step)) and hasClickAction()
        val launch = hasText(say(R.string.omni_cfg_launch_game)) and hasClickAction()
        await(next)
        repeat(MAX_WIZARD_STEPS) {
            if (exists(next)) rule.onNode(next).performClick()
            rule.waitForIdle()
        }
        await(launch)
        rule.onNode(launch).performClick()
        await(hasText(say(R.string.omni_running)))
    }

    private fun fixedRounds(n: Int) = OmniGameSettings(rounds = List(n) { OmniRoundSettings(turnLogic = RoundTurnLogic.FIXED) })

    // --- Orders ---

    @Test
    fun snakeTurnsBackOnScreenAtEachRound() {
        setUp(OmniSettings(numberOfPlayers = 3, playerOrderType = PlayerOrderType.SNAKE, games = listOf(fixedRounds(3))))
        launchASession()

        val seen = mutableListOf(shownPlayer(3))
        repeat(5) { next(); seen += shownPlayer(3) }

        assertEquals("from ${state}", listOf(1, 2, 3, 3, 2, 1), seen)
    }

    @Test
    fun aRandomOrderDrawnEachRoundGivesEveryoneOneTurnAndNoRepeatAcrossTheRound() {
        setUp(OmniSettings(numberOfPlayers = 4, playerOrderType = PlayerOrderType.RANDOM, randomEachTurn = false, randomAvoidBackToBack = true, games = listOf(fixedRounds(3))))
        launchASession()

        val seen = mutableListOf(shownPlayer(4))
        repeat(11) {
            next()
            val shown = shownPlayer(4)
            assertEquals("the screen and the clock name the same player", state.currentPlayerIndex + 1, shown)
            seen += shown
        }

        // Three rounds of four: each a draw of everyone, once.
        seen.chunked(4).forEach { round -> assertEquals("a round of $seen", setOf(1, 2, 3, 4), round.toSet()) }
        // Nobody plays the last turn of one round and the first of the next.
        assertNotEquals(seen[3], seen[4])
        assertNotEquals(seen[7], seen[8])
    }

    // --- Phases ---

    @Test
    fun aTurnGoesThroughItsPhasesThenPassesToTheNextPlayer() {
        val phases = listOf(OmniPhaseSettings(name = "Draw", durationMs = 30_000), OmniPhaseSettings(name = "Play", durationMs = 30_000))
        setUp(OmniSettings(numberOfPlayers = 2, usePhaseClock = true, games = listOf(OmniGameSettings(rounds = listOf(OmniRoundSettings(turnLogic = RoundTurnLogic.FIXED, fixedTurnPhases = phases))))))
        launchASession()

        await(hasText("Draw:"))
        assertEquals(1, shownPlayer(2))

        next()
        // The next phase, the same player.
        await(hasText("Play:"))
        assertEquals(1, shownPlayer(2))

        next()
        // The next player, from the first phase.
        await(hasText("Draw:"))
        assertEquals(2, shownPlayer(2))
    }

    // --- Pauses between turns ---

    @Test
    fun aPauseBetweenTurnsShowsWhoIsNextHoldsTheirClockAndEndsOnItsOwn() {
        setUp(OmniSettings(numberOfPlayers = 3, interTurnPauseMs = 3_000, transitionType = TransitionType.AUTOMATIC))
        launchASession()

        next()
        await(hasText(say(R.string.omni_turn_finished)))
        assertTrue(exists(hasText(say(R.string.omni_next_player, 2))))
        // Nothing to press through it, and the next player's clock not yet running.
        assertFalse(exists(hasContentDescription(say(R.string.omni_next)) and hasClickAction()))
        val held = state.currentTurnTimeMs
        Thread.sleep(1_000)
        assertEquals(held, state.currentTurnTimeMs)

        // It ends by itself, on player 2.
        rule.waitUntil(TIMEOUT_MS) { !exists(hasText(say(R.string.omni_turn_finished))) }
        assertEquals(2, shownPlayer(3))
    }

    @Test
    fun aPauseThatWaitsForAHandStaysUntilReady() {
        setUp(OmniSettings(numberOfPlayers = 2, interTurnPauseMs = 1_000, transitionType = TransitionType.MANUAL_READY))
        launchASession()

        next()
        await(hasText(say(R.string.omni_i_am_ready)) and hasClickAction())
        // Long past its second, it is still there.
        Thread.sleep(2_500)
        assertTrue(exists(hasText(say(R.string.omni_turn_finished))))

        rule.onNode(hasText(say(R.string.omni_i_am_ready)) and hasClickAction()).performClick()
        rule.waitUntil(TIMEOUT_MS) { !exists(hasText(say(R.string.omni_turn_finished))) }
        assertEquals(2, shownPlayer(2))
    }

    // --- Taken up again ---

    @Test
    fun aSessionClosedInTheMiddleOfARoundComesBackOnTheSameTurn() {
        setUp(OmniSettings(numberOfPlayers = 3, playerOrderType = PlayerOrderType.SNAKE, games = listOf(fixedRounds(2))))
        launchASession()
        // Into the second round, which Snake plays backwards: player 3, then player 2.
        repeat(4) { next() }
        assertEquals(2, shownPlayer(3))
        control(R.string.omni_pause).performClick()
        await(hasText(say(R.string.omni_paused)))
        val left = state

        // What a new process does after Android closed the app: a new ViewModel, reading the store.
        lateinit var fresh: OmniTimerViewModel
        rule.runOnUiThread { fresh = OmniTimerViewModel(rule.activity.application as Application) }
        rule.waitUntil(TIMEOUT_MS) { fresh.omniState.value.hasStarted }
        val back = fresh.omniState.value
        assertFalse(back.isRunning)
        assertEquals(left.currentRoundIndex, back.currentRoundIndex)
        assertEquals(left.turnCounterInRound, back.turnCounterInRound)
        assertEquals(left.currentPlayerIndex, back.currentPlayerIndex)
        assertEquals(left.tablePass, back.tablePass)

        // Taken up, the round goes on in its own order: player 1 last.
        rule.runOnUiThread { fresh.startOmni(); fresh.advanceOmni() }
        assertEquals(0, fresh.omniState.value.currentPlayerIndex)
        rule.runOnUiThread { fresh.pauseOmni() }
    }

    // --- Next before Play ---

    @Test
    fun nextBeforeASessionHasStartedMovesNothing() {
        setUp(OmniSettings(numberOfPlayers = 3))
        rule.runOnUiThread { repeat(3) { omni.advanceOmni() } }
        // Three taps showed player 2, and Play then started from player 1 all the same.
        assertEquals(0, state.currentPlayerIndex)
        assertEquals(0, state.turnCounterInRound)
    }

    // --- TalkBack ---

    @Test
    fun talkBackHearsTheTurnThePauseAndThePlayerAfterIt() {
        setUp(OmniSettings(numberOfPlayers = 3, interTurnPauseMs = 2_000, transitionType = TransitionType.AUTOMATIC))
        launchASession()
        await(announced(say(R.string.common_player_n, 1)), merged = false)

        next()
        await(announced(say(R.string.omni_turn_finished) + ". " + say(R.string.omni_next_player, 2)), merged = false)
        await(announced(say(R.string.common_player_n, 2)), merged = false)

        control(R.string.omni_pause).performClick()
        await(announced(say(R.string.a11y_announce_paused)), merged = false)
    }

    @Test
    fun talkBackHearsThePhaseWithThePlayer() {
        val phases = listOf(OmniPhaseSettings(name = "Draw", durationMs = 30_000), OmniPhaseSettings(name = "Play", durationMs = 30_000))
        setUp(OmniSettings(numberOfPlayers = 2, usePhaseClock = true, games = listOf(OmniGameSettings(rounds = listOf(OmniRoundSettings(turnLogic = RoundTurnLogic.FIXED, fixedTurnPhases = phases))))))
        launchASession()
        await(announced(say(R.string.common_player_n, 1) + ", Draw"), merged = false)
        next()
        await(announced(say(R.string.common_player_n, 1) + ", Play"), merged = false)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val MAX_WIZARD_STEPS = 12
    }
}
