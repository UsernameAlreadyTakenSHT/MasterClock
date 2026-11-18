package com.masterclock.app

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.masterclock.app.data.SettingsRepository
import com.masterclock.app.logic.ChessTimerViewModel
import com.masterclock.app.logic.FlavorConfig
import com.masterclock.app.logic.OmniSettings
import com.masterclock.app.logic.OmniTimerViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * An Omni session that outlives what used to end it: Android closing the app, kept in the store and
 * taken back; and the app off the screen, kept alive by the notification as a chess game is.
 */
class OmniSessionFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val clock get() = ViewModelProvider(rule.activity)[ChessTimerViewModel::class.java]

    /** The activity's, which the Omni screens use; see OmniFlowTest. */
    private val omni get() = ViewModelProvider(rule.activity)[OmniTimerViewModel::class.java]
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun say(id: Int) = rule.activity.getString(id)

    private fun await(matcher: SemanticsMatcher) =
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    private fun control(id: Int) = rule.onNode(hasContentDescription(say(id)) and hasClickAction())

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
        rule.waitUntil(TIMEOUT_MS) { clock.isReady.value }
        assumeTrue(FlavorConfig.hasOmni())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        rule.runOnUiThread {
            clock.resetAllSettings()
            clock.reset()
            omni.resetOmni()
            omni.updateOmniSettings(OmniSettings(launchCountdownMs = 0))
        }
        await(hasContentDescription(say(R.string.timer_settings)))
    }

    /** Nothing left for the next test: the service stopped, the session gone, the defaults back. */
    @After
    fun leaveNothingBehind() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { BackgroundClockService.stop(context) }
        if (rule.activityRule.scenario.state == Lifecycle.State.DESTROYED) return
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.runOnUiThread {
            omni.resetOmni()
            omni.updateOmniSettings(OmniSettings())
            clock.resetAllSettings()
        }
    }

    /** From the clock through the wizard to a running session; see OmniFlowTest. */
    private fun launchASession() {
        control(R.string.timer_settings).performClick()
        // The modes less played are folded under one line; see ModeSelectionPanel.
        await(hasTestTag("more_modes"))
        rule.onNode(hasTestTag("more_modes")).performScrollTo().performClick()
        await(hasText("Omni-Timer", substring = true))
        rule.onNode(hasText("Omni-Timer", substring = true) and hasClickAction()).performScrollTo().performClick()
        val next = hasText(say(R.string.omni_cfg_next_step)) and hasClickAction()
        val launch = hasText(say(R.string.omni_cfg_launch_game)) and hasClickAction()
        await(next)
        repeat(MAX_WIZARD_STEPS) {
            if (rule.onAllNodes(next).fetchSemanticsNodes().isNotEmpty()) rule.onNode(next).performClick()
            rule.waitForIdle()
        }
        await(launch)
        rule.onNode(launch).performClick()
        await(hasText(say(R.string.omni_running)))
    }

    @Test
    fun aSessionComesBackAfterTheAppIsClosed() {
        launchASession()
        // A turn played -- the defaults hold two, and the second would end the session -- and the
        // session paused: where the store must hold it.
        control(R.string.omni_next).performClick()
        rule.waitForIdle()
        assertEquals(1, omni.omniState.value.turnCounterInRound)
        control(R.string.omni_pause).performClick()
        await(hasText(say(R.string.omni_paused)))
        val left = omni.omniState.value

        val stored = runBlocking { SettingsRepository(context).omniSessionFlow.first() }
        assertNotNull("the session was not stored", stored)
        assertEquals(left.turnCounterInRound, stored!!.turnCounterInRound)

        // What a new process does after Android closed the app: a new ViewModel, reading the store.
        lateinit var fresh: OmniTimerViewModel
        rule.runOnUiThread { fresh = OmniTimerViewModel(rule.activity.application as Application) }
        rule.waitUntil(TIMEOUT_MS) { fresh.omniState.value.hasStarted }
        val back = fresh.omniState.value
        assertFalse("it came back running", back.isRunning)
        assertEquals(left.currentPlayerIndex, back.currentPlayerIndex)
        assertEquals(left.turnCounterInRound, back.turnCounterInRound)
        assertEquals(left.currentRoundIndex, back.currentRoundIndex)
        assertTrue("Launch offers no session to resume", fresh.hasSessionToResume())
    }

    @Test
    fun aSessionLeftRunningKeepsItsNotificationAndItsClock() {
        assumeTrue(FlavorConfig.hasFullSettingsTabs())
        rule.runOnUiThread { clock.updateSettings(clock.settings.value.copy(pauseOnBackground = false, keepRunningInBackground = true)) }
        rule.waitUntil(TIMEOUT_MS) { clock.settings.value.keepRunningInBackground && !clock.settings.value.pauseOnBackground }
        launchASession()

        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        waitFor("the notification") { notificationShown() }
        val before = omni.omniState.value.currentTurnTimeMs
        Thread.sleep(1_500)
        assertTrue("the session paused in the background", omni.omniState.value.isRunning)
        assertTrue("its clock stood still in the background", omni.omniState.value.currentTurnTimeMs < before)

        // The notification's Pause pauses the session, and the notification goes with it.
        val shown = context.getSystemService(NotificationManager::class.java).activeNotifications
            .single { it.id == BackgroundClockService.NOTIFICATION_ID }.notification
        shown.actions.single().actionIntent.send()
        waitFor("the pause") { !omni.omniState.value.isRunning }
        waitFor("the notification to go") { !notificationShown() }
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
    }

    @Test
    fun byDefaultALeftSessionPausesAndNothingIsShown() {
        launchASession()
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        waitFor("the pause") { !omni.omniState.value.isRunning }
        Thread.sleep(1_000)
        assertFalse(notificationShown())
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val MAX_WIZARD_STEPS = 12
    }
}
