package com.masterclock.app

import android.annotation.SuppressLint
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import com.masterclock.app.logic.ChessTimerViewModel
import com.masterclock.app.logic.FlavorConfig
import com.masterclock.app.logic.NotebookNote
import com.masterclock.app.logic.NotebookNoteType
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * The photo, voice and video notes, around what they capture: the camera and the microphone are
 * another app and the hardware, out of a test's reach. What is left is theirs: the title kept, the
 * button that captures, and Delete, which asks, lets go of the media and shreds its file.
 */
class MediaNoteFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val viewModel get() = ViewModelProvider(rule.activity)[ChessTimerViewModel::class.java]

    private fun say(id: Int) = rule.activity.getString(id)

    @SuppressLint("DiscouragedApi")
    private fun complete(name: String): String =
        rule.activity.getString(rule.activity.resources.getIdentifier(name, "string", rule.activity.packageName))

    private fun await(matcher: SemanticsMatcher) =
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForTheNote(what: String, done: (NotebookNote) -> Boolean) {
        try {
            rule.waitUntil(TIMEOUT_MS) { viewModel.settings.value.notebookNotes.singleOrNull()?.let(done) == true }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("$what: the note is ${viewModel.settings.value.notebookNotes.singleOrNull()}", e)
        }
    }

    @Before
    fun ready() {
        rule.waitUntil(TIMEOUT_MS) { viewModel.isReady.value }
        assumeTrue(FlavorConfig.hasMoreTab())
        rule.runOnUiThread { viewModel.resetAllSettings() }
    }

    private fun open(note: NotebookNote) {
        rule.runOnUiThread { viewModel.updateSettings(viewModel.settings.value.copy(notebookNotes = listOf(note))) }
        await(hasContentDescription(say(R.string.timer_settings)))
        rule.onNode(hasContentDescription(say(R.string.timer_settings)) and hasClickAction()).performClick()
        await(hasText(say(R.string.settings_tab_more)))
        rule.onAllNodesWithText(say(R.string.settings_tab_more))[0].performClick()
        await(hasText(say(R.string.settings_more_notebook)))
        rule.onNode(hasText(say(R.string.settings_more_notebook)) and hasClickAction()).performScrollTo().performClick()
        await(hasText(note.title))
        rule.onNode(hasText(note.title) and hasClickAction()).performClick()
        await(hasSetTextAction())
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(rule.activity.filesDir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** A small photo where the camera would have put one. */
    private fun aPhoto(id: String): File = File(File(rule.activity.filesDir, "shares").apply { mkdirs() }, "image_$id.jpg").apply {
        val bitmap = android.graphics.Bitmap.createBitmap(64, 48, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(30, 120, 200))
        outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
    }

    /** Deletes the note's media through its button and the question it asks. */
    private fun deleteTheMedia(label: String) {
        rule.onNode(hasText(complete(label)) and hasClickAction()).performClick()
        await(hasText(say(R.string.common_delete)))
        rule.onNode(hasText(say(R.string.common_delete)) and hasClickAction()).performClick()
    }

    @Test
    fun aPhotoNoteKeepsItsTitleAndDeletesItsPhoto() {
        val note = NotebookNote(title = "Scoresheet", type = NotebookNoteType.IMAGE)
        val photo = aPhoto(note.id)
        open(note.copy(imagePath = photo.absolutePath))
        rule.onAllNodes(hasSetTextAction())[0].performTextReplacement("Scoresheet round 3")
        waitForTheNote("the title") { it.title == "Scoresheet round 3" }
        await(hasText(complete("tools_retake_photo")))
        shot("media_photo")
        deleteTheMedia("tools_delete_photo")
        waitForTheNote("the delete") { it.imagePath == null }
        await(hasText(complete("tools_take_photo")))
        await(hasText(complete("tools_no_photo")))
        rule.waitUntil(TIMEOUT_MS) { !photo.exists() }
        assertFalse(photo.exists())
    }

    @Test
    fun aVoiceNoteOffersPlayAndDeletesItsRecording() {
        val note = NotebookNote(title = "Analysis", type = NotebookNoteType.VOICE)
        val recording = File(rule.activity.filesDir, "audio_${note.id}.mp4").apply { writeBytes(ByteArray(256) { it.toByte() }) }
        open(note.copy(audioPath = recording.absolutePath, audioDurationMs = 12_000))
        await(hasText(complete("tools_play")))
        await(hasText(complete("tools_record_again")))
        await(hasText("00:12"))
        shot("media_voice")
        deleteTheMedia("tools_delete_recording")
        waitForTheNote("the delete") { it.audioPath == null && it.audioDurationMs == 0L }
        await(hasText(complete("tools_record")))
        rule.waitUntil(TIMEOUT_MS) { !recording.exists() }
    }

    /**
     * A take recorded through the emulator's microphone: what the recorder and the player went
     * through before they had a class of their own, VoiceSession, and still go through.
     */
    @Test
    fun aVoiceNoteRecordsATakeAndPlaysIt() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(rule.activity.packageName, android.Manifest.permission.RECORD_AUDIO)
        open(NotebookNote(title = "Spoken", type = NotebookNoteType.VOICE))
        rule.onNode(hasText(complete("tools_record")) and hasClickAction()).performClick()
        await(hasText(complete("tools_stop")))
        Thread.sleep(2_000)
        rule.onNode(hasText(complete("tools_stop")) and hasClickAction()).performClick()
        waitForTheNote("the take") { it.audioPath != null && it.audioDurationMs >= 1_000 }
        val take = File(note().audioPath!!)
        rule.waitUntil(TIMEOUT_MS) { take.length() > 0 }
        // Played, it can be stopped; the button comes back.
        await(hasText(complete("tools_play")))
        rule.onNode(hasText(complete("tools_play")) and hasClickAction()).performClick()
        await(hasText(complete("tools_stop")))
        rule.onNode(hasText(complete("tools_stop")) and hasClickAction()).performClick()
        await(hasText(complete("tools_play")))
    }

    private fun note(): NotebookNote = viewModel.settings.value.notebookNotes.single()

    @Test
    fun anEmptyVideoNoteOffersToRecord() {
        open(NotebookNote(title = "Blitz", type = NotebookNoteType.VIDEO))
        await(hasText(complete("tools_record_video")))
        await(hasText(complete("tools_no_video")))
        shot("media_video")
        rule.onAllNodes(hasSetTextAction())[0].performTextReplacement("Blitz final")
        waitForTheNote("the title") { it.title == "Blitz final" }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
