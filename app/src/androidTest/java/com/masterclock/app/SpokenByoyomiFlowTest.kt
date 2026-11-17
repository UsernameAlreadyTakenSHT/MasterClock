package com.masterclock.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.masterclock.app.logic.ChessTimerViewModel
import com.masterclock.app.logic.FlavorConfig
import com.masterclock.app.logic.PlayerSettings
import com.masterclock.app.logic.TimerMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Collections

/**
 * A byoyomi read aloud, heard as a game plays it: what the running clock asks its voice to say and
 * in what order, which `core`'s tests of the rule cannot tell. See VoiceManager.heard.
 *
 * Heard as asked, before the speech engine, so it holds on a device with no voice at all.
 */
class SpokenByoyomiFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val viewModel get() = ViewModelProvider(rule.activity)[ChessTimerViewModel::class.java]

    /** Each thing said: its name and what it was filled with, and a star where it waits for the one before. */
    private val said: MutableList<String> = Collections.synchronizedList(mutableListOf())

    @Before
    fun listen() {
        assumeTrue(FlavorConfig.hasFullSettingsTabs())
        rule.waitUntil(TIMEOUT_MS) { viewModel.isReady.value }
        rule.runOnUiThread {
            viewModel.resetAllSettings()
            viewModel.reset()
            viewModel.listenToTheVoice { what, with, afterTheLast ->
                said += what + (if (with.isEmpty()) "" else with.joinToString(",", "[", "]")) + (if (afterTheLast) "*" else "")
            }
        }
    }

    @After
    fun leaveNothingBehind() {
        if (!FlavorConfig.hasFullSettingsTabs()) return
        rule.runOnUiThread {
            viewModel.listenToTheVoice(null)
            viewModel.reset()
            viewModel.resetAllSettings()
        }
    }

    /** A second of main time, then periods of [periodMs]: a game that is in byoyomi almost at once. */
    private fun play(periodMs: Long, announcements: Boolean) {
        rule.runOnUiThread {
            viewModel.updateSettings(
                viewModel.settings.value.copy(
                    main = PlayerSettings(mode = TimerMode.BYOYOMI_JAPANESE, initialTimeMs = 1_000, byoyomiPeriods = 3, byoyomiTimeMs = periodMs),
                    spokenByoyomi = true,
                    voiceAnnouncementsEnabled = announcements,
                )
            )
            viewModel.reset()
            said.clear()
            viewModel.startOrSwitch(1)
        }
    }

    @Test
    fun aPeriodOfTwentySecondsIsReadAsTheTimekeeperReadsIt() {
        play(periodMs = 20_000, announcements = false)
        // Main time gone, a whole period read, and the period lost said as the next begins.
        rule.waitUntil(40_000) { said.any { it.startsWith("voice_byoyomi_periods_left") } }
        assertEquals(
            listOf(
                "voice_byoyomi",
                "voice_byoyomi_seconds[10]",
                // "One" waits for "ten seconds" to finish; the rest cut in on their second.
                "1*", "2", "3", "4", "5", "6", "7", "8", "9",
                "voice_byoyomi_periods_left[2]",
            ),
            said.toList().takeWhile { !it.startsWith("voice_byoyomi_periods_left") } + said.first { it.startsWith("voice_byoyomi_periods_left") },
        )
    }

    @Test
    fun withTheAnnouncementsOnNothingIsSaidTwice() {
        play(periodMs = 12_000, announcements = true)
        rule.waitUntil(30_000) { said.any { it.startsWith("voice_byoyomi_periods_left") } }
        val heard = said.toList().takeWhile { !it.startsWith("voice_byoyomi_periods_left") }
        // Twelve seconds: no tens to call, the count alone, from the third second.
        assertEquals(listOf("voice_byoyomi", "1*", "2", "3", "4", "5", "6", "7", "8", "9"), heard)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
