package com.masterclock.app

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.masterclock.app.logic.ChessClockSettings
import com.masterclock.app.logic.SoundManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * A custom sound the device cannot play: the bundled sound takes its place rather than leaving it
 * silent. On the device's own sound pool, which is where the decoding fails and nothing in `core`
 * can reach it. See SoundManager.readySounds.
 */
class SoundFallbackFlowTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext
    private val all = setOf("beep", "gong", "final", "switch")
    private var sounds: SoundManager? = null
    private val notASound = File(app.cacheDir, "not_a_sound.bin")

    @After
    fun release() {
        instrumentation.runOnMainSync { sounds?.release() }
        notASound.delete()
    }

    private fun loaded(settings: ChessClockSettings): Set<String> {
        // On the main thread, as the app builds it: the pool reports its loads to the thread that made it.
        instrumentation.runOnMainSync { sounds = SoundManager(app).also { it.loadSounds(settings) } }
        val until = System.currentTimeMillis() + TIMEOUT_MS
        var ready = emptySet<String>()
        while (System.currentTimeMillis() < until) {
            instrumentation.runOnMainSync { ready = sounds!!.readySounds() }
            if (ready == all) break
            Thread.sleep(100)
        }
        return ready
    }

    @Test
    fun theBundledSoundsAllLoad() {
        assertEquals(all, loaded(ChessClockSettings()))
    }

    @Test
    fun aFileThatIsNotASoundGivesItsPlaceBackToTheBundledOne() {
        // It opens, as any file does, and is not audio: the load is accepted and the decoding fails.
        notASound.writeBytes(ByteArray(4_096) { (it * 31).toByte() })
        val custom = Uri.fromFile(notASound).toString()
        assertEquals(all, loaded(ChessClockSettings(customBeepUri = custom, customFinalBeepUri = custom)))
    }

    @Test
    fun aFileThatIsNotThereGivesItsPlaceBackToo() {
        val gone = Uri.fromFile(File(app.cacheDir, "gone.mp3")).toString()
        assertEquals(all, loaded(ChessClockSettings(customSwitchUri = gone, customGongUri = gone)))
    }

    private companion object {
        const val TIMEOUT_MS = 8_000L
    }
}
