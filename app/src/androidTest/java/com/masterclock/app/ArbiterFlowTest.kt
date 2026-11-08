package com.masterclock.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.masterclock.app.logic.ChessTimerViewModel
import com.masterclock.app.logic.FlavorConfig
import com.masterclock.app.logic.GameEvent
import com.masterclock.app.logic.PlayerSettings
import com.masterclock.app.logic.TimerMode
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * What the arbiter's penalty leaves behind in the real ViewModel: the arithmetic is pinned in core
 * (ArbiterPastZeroTest), and only here can the log it writes, or did not, be read.
 */
class ArbiterFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val viewModel get() = ViewModelProvider(rule.activity)[ChessTimerViewModel::class.java]

    private fun events(): List<GameEvent> = viewModel.gameHistory.value.flatMap { it.events }

    @Before
    fun startFromTheDefaults() {
        assumeTrue(FlavorConfig.hasArbitre() && FlavorConfig.keepsGameHistory())
        rule.waitUntil(TIMEOUT_MS) { viewModel.isReady.value }
        rule.waitUntil(TIMEOUT_MS) { viewModel.isHistoryLoaded.value }
        rule.runOnUiThread {
            viewModel.resetAllSettings()
            viewModel.reset()
            viewModel.clearAllLogs()
        }
        rule.waitUntil(TIMEOUT_MS) { viewModel.gameHistory.value.isEmpty() }
    }

    /** A game under way, one move in so that the history keeps it, paused with the arbiter's panel open. */
    private fun underArbiter(main: PlayerSettings) {
        rule.runOnUiThread { viewModel.updateSettings(viewModel.settings.value.copy(main = main)) }
        rule.waitUntil(TIMEOUT_MS) { viewModel.settings.value.main.mode == main.mode }
        rule.runOnUiThread { viewModel.startOrSwitch(1) }
        rule.waitUntil(TIMEOUT_MS) { viewModel.uiState.value.activePlayer != null }
        rule.runOnUiThread { viewModel.startOrSwitch(viewModel.uiState.value.activePlayer!!) }
        rule.runOnUiThread { viewModel.pause() }
        rule.waitUntil(TIMEOUT_MS) { viewModel.uiState.value.isPaused }
        rule.runOnUiThread { viewModel.toggleArbitreMode() }
        rule.waitUntil(TIMEOUT_MS) { viewModel.uiState.value.isArbitreMode }
    }

    private fun endTheGameAndWaitFor(found: () -> Boolean) {
        rule.runOnUiThread {
            viewModel.toggleArbitreMode()
            viewModel.reset()
        }
        rule.waitUntil(TIMEOUT_MS) { found() }
    }

    @Test
    fun aPenaltyThatCostsByoyomiPeriodsIsInTheLog() {
        underArbiter(PlayerSettings(mode = TimerMode.BYOYOMI_JAPANESE, initialTimeMs = 60_000, byoyomiPeriods = 5, byoyomiTimeMs = 30_000))
        // The main time and three periods' worth: the player is left in byoyomi with two.
        rule.runOnUiThread { viewModel.adjustTime(1, -150_000) }
        endTheGameAndWaitFor { events().any { it.eventType == "PERIOD" && it.playerIndex == 1 } }
    }

    @Test
    fun aPenaltyThatFlagsIsInTheLog() {
        underArbiter(PlayerSettings(mode = TimerMode.SUDDEN_DEATH, initialTimeMs = 60_000))
        rule.runOnUiThread { viewModel.adjustTime(2, -120_000) }
        endTheGameAndWaitFor { events().any { it.eventType == "FLAG" && it.playerIndex == 2 } }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
