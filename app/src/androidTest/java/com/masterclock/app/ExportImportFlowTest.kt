package com.masterclock.app

import android.net.Uri
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.masterclock.app.logic.ChessTimerViewModel
import com.masterclock.app.logic.SharePackage
import com.masterclock.app.logic.TimerMode
import com.masterclock.app.logic.decodeSharePackage
import com.masterclock.app.logic.readImportText
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * A settings file out and back in, through the same calls MainActivity makes, against the real
 * storage. Everything but the system's file picker, which is another app and out of Compose's reach:
 * the file here is opened through the content resolver as the picked one is.
 */
class ExportImportFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val viewModel get() = ViewModelProvider(rule.activity)[ChessTimerViewModel::class.java]
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun startFromTheDefaults() {
        rule.waitUntil(TIMEOUT_MS) { viewModel.isReady.value }
        rule.runOnUiThread {
            viewModel.resetAllSettings()
            viewModel.reset()
            // Reset settings keeps the scoreboard, as it keeps the notebook, and the game this test
            // leaves there made the next run from Android Studio, which does not uninstall, import two.
            viewModel.resetScoreboard()
        }
        rule.waitUntil(TIMEOUT_MS) { viewModel.scoreboard.value.games.isEmpty() }
    }

    @Test
    fun aSettingsFileComesBackAsItWent() {
        val chosen = viewModel.settings.value.let { it.copy(main = it.main.copy(mode = TimerMode.FISCHER, initialTimeMs = 180_000, incrementMs = 2_000)) }
        assumeTrue(com.masterclock.app.logic.FlavorConfig.isModeAllowed(TimerMode.FISCHER))
        rule.runOnUiThread {
            viewModel.updateSettings(chosen)
            viewModel.addScoreboardGame("1 - 0")
        }
        rule.waitUntil(TIMEOUT_MS) { viewModel.settings.value.main.initialTimeMs == 180_000L && viewModel.scoreboard.value.games.isNotEmpty() }

        val snapshot = runBlocking { viewModel.exportSnapshot() }
        val file = File(rule.activity.cacheDir, "round_trip.json")
        file.writeText(json.encodeToString(SharePackage(settings = snapshot.settings, logs = snapshot.logs, scoreboard = snapshot.scoreboard)))

        rule.runOnUiThread {
            viewModel.resetAllSettings()
            viewModel.resetScoreboard()
        }
        rule.waitUntil(TIMEOUT_MS) { viewModel.settings.value.main.initialTimeMs != 180_000L }
        assertNotEquals(TimerMode.FISCHER, viewModel.settings.value.main.mode)

        val text = rule.activity.contentResolver.openInputStream(Uri.fromFile(file))!!.use { readImportText(it) }
        val pkg = decodeSharePackage(text)
        rule.runOnUiThread {
            viewModel.updateSettings(pkg.settings, logsToImport = pkg.logs, scoreboardToImport = pkg.scoreboard, isImport = true)
        }

        rule.waitUntil(TIMEOUT_MS) { viewModel.settings.value.main.initialTimeMs == 180_000L }
        assertEquals(TimerMode.FISCHER, viewModel.settings.value.main.mode)
        assertEquals(2_000, viewModel.settings.value.main.incrementMs)
        rule.waitUntil(TIMEOUT_MS) { viewModel.scoreboard.value.games.map { it.result } == listOf("1 - 0") }
        file.delete()
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
