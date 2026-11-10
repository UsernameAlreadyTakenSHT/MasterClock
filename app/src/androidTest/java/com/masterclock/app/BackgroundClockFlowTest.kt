package com.masterclock.app

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.masterclock.app.logic.ChessTimerViewModel
import com.masterclock.app.logic.FlavorConfig
import com.masterclock.app.logic.PlayerSettings
import com.masterclock.app.logic.TimerMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * A game left running in the background, kept alive by its notification: what core's
 * runsInBackground decides, carried out by the activity and the service on a real device.
 */
class BackgroundClockFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val viewModel get() = ViewModelProvider(rule.activity)[ChessTimerViewModel::class.java]
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun notificationShown(): Boolean =
        context.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == BackgroundClockService.NOTIFICATION_ID }

    /** Polled by hand: with the activity off the screen, Compose has no frames to wait on. */
    private fun waitFor(what: String, condition: () -> Boolean) {
        val until = System.currentTimeMillis() + TIMEOUT_MS
        while (!condition()) {
            check(System.currentTimeMillis() < until) { "timed out waiting for $what" }
            Thread.sleep(100)
        }
    }

    @Before
    fun startFromTheDefaults() {
        assumeTrue(FlavorConfig.hasFullSettingsTabs())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        rule.waitUntil(TIMEOUT_MS) { viewModel.isReady.value }
        rule.runOnUiThread {
            viewModel.resetAllSettings()
            viewModel.reset()
        }
        rule.waitForIdle()
    }

    /**
     * The settings back to their defaults and the service stopped, passed or failed: a test that stops
     * with the activity away leaves both behind, and the next suite would start with the pause off. The
     * notification permission stays granted -- revoking it ends the process the tests run in.
     */
    @After
    fun leaveNothingBehind() {
        if (!FlavorConfig.hasFullSettingsTabs()) return
        InstrumentationRegistry.getInstrumentation().runOnMainSync { BackgroundClockService.stop(context) }
        // A test that destroyed the activity has nothing to bring back; the next one resets on its way in.
        if (rule.activityRule.scenario.state == Lifecycle.State.DESTROYED) return
        comeBack()
        rule.runOnUiThread { viewModel.resetAllSettings() }
    }

    private fun keepRunning() {
        rule.runOnUiThread { viewModel.updateSettings(viewModel.settings.value.copy(pauseOnBackground = false, keepRunningInBackground = true)) }
        rule.waitUntil(TIMEOUT_MS) { viewModel.settings.value.keepRunningInBackground && !viewModel.settings.value.pauseOnBackground }
    }

    private fun startTheClock() {
        rule.runOnUiThread { viewModel.startOrSwitch(1) }
        rule.waitUntil(TIMEOUT_MS) { viewModel.uiState.value.activePlayer != null && !viewModel.uiState.value.isPaused }
    }

    private fun leave() = rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
    private fun comeBack() = rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

    @Test
    fun aGameLeftRunningKeepsItsNotificationAndItsClock() {
        keepRunning()
        startTheClock()
        val player = viewModel.uiState.value.activePlayer!!

        leave()
        waitFor("the notification") { notificationShown() }
        val before = viewModel.uiState.value.players[player - 1].timeRemainingMs
        Thread.sleep(1_500)
        assertFalse("the game paused in the background", viewModel.uiState.value.isPaused)
        assertTrue("the clock stood still in the background", viewModel.uiState.value.players[player - 1].timeRemainingMs < before)

        comeBack()
        waitFor("the notification to go") { !notificationShown() }
    }

    @Test
    fun theNotificationsPauseStopsTheGameAndGoes() {
        keepRunning()
        startTheClock()
        leave()
        waitFor("the notification") { notificationShown() }

        // The button itself: its PendingIntent, the action it carries, the service receiving it.
        val shown = context.getSystemService(NotificationManager::class.java).activeNotifications
            .single { it.id == BackgroundClockService.NOTIFICATION_ID }.notification
        shown.actions.single().actionIntent.send()
        waitFor("the pause") { viewModel.uiState.value.isPaused }
        waitFor("the notification to go") { !notificationShown() }
        comeBack()
    }

    private fun shownEnd(): Long? = context.getSystemService(NotificationManager::class.java).activeNotifications
        .singleOrNull { it.id == BackgroundClockService.NOTIFICATION_ID }?.notification?.`when`

    @Test
    fun aTableClockThatRefillsRedrawsItsCountdown() {
        // Shared (Auto), three seconds a move: the table's clock refills as each turn passes, nobody's
        // turn in particular. The notification went on counting the first three seconds into minus figures.
        keepRunning()
        rule.runOnUiThread { viewModel.updateSettings(viewModel.settings.value.copy(main = PlayerSettings(mode = TimerMode.MOVE_TIMER_SHARED, moveTimeMs = 3_000))) }
        rule.waitUntil(TIMEOUT_MS) { viewModel.settings.value.main.mode == TimerMode.MOVE_TIMER_SHARED }
        rule.runOnUiThread { viewModel.resume() }
        rule.waitUntil(TIMEOUT_MS) { viewModel.uiState.value.activePlayer != null && !viewModel.uiState.value.isPaused }

        leave()
        waitFor("the notification") { shownEnd() != null }
        val first = shownEnd()!!
        waitFor("the countdown redrawn for the next turn") { (shownEnd() ?: first) - first >= 2_000 }
    }

    @Test
    fun theNotificationGoesWithTheActivityThatKeptTheGame() {
        // Destroyed for good while away -- "Don't keep activities", or the system -- the ViewModel and
        // its game go, and the notification went on counting past zero.
        keepRunning()
        startTheClock()
        leave()
        waitFor("the notification") { notificationShown() }
        rule.activityRule.scenario.moveToState(Lifecycle.State.DESTROYED)
        waitFor("the notification to go") { !notificationShown() }
    }

    @Test
    fun byDefaultTheGamePausesAndNothingIsShown() {
        startTheClock()
        leave()
        waitFor("the pause") { viewModel.uiState.value.isPaused }
        Thread.sleep(1_000)
        assertFalse(notificationShown())
        comeBack()
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
