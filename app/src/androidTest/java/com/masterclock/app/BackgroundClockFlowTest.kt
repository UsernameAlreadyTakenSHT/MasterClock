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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
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

        InstrumentationRegistry.getInstrumentation().runOnMainSync { BackgroundClockLink.pause?.invoke() }
        waitFor("the pause") { viewModel.uiState.value.isPaused }
        waitFor("the notification to go") { !notificationShown() }
        comeBack()
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
