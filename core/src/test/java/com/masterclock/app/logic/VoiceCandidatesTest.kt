package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.Locale

/** Which voice the announcements get, and the languages they can be said in. */
class VoiceCandidatesTest {

    private val polish = Locale.forLanguageTag("pl-PL")
    private val spanish = Locale.forLanguageTag("es-ES")
    private val french = Locale.forLanguageTag("fr-FR")

    @Test
    fun `the device's language comes first, if the app speaks it`() {
        assertEquals(listOf(french, Locale.US), voiceCandidates(french, null, emptyList()).take(2))
        assertEquals(Locale.US, voiceCandidates(polish, null, emptyList()).first())
    }

    @Test
    fun `an engine's voice in a language the app has beats one it does not`() {
        // A Polish phone whose engine has Polish and Spanish, and no English: Spanish, read in Spanish,
        // rather than a Polish voice reading English strings.
        val tried = voiceCandidates(polish, polish, setOf(polish, spanish))
        assertEquals(listOf(Locale.US, spanish, polish), tried)
    }

    @Test
    fun `any voice is still tried before none`() {
        assertEquals(listOf(Locale.US, polish), voiceCandidates(polish, null, setOf(polish)))
    }

    @Test
    fun `the languages are the ones core has strings in`() {
        // Kept by hand beside the folders, so held to them: a language added to res and not here was
        // never spoken, and one listed with no folder read English in its voice.
        // From core, as Gradle runs it, or from the project, as an IDE configuration may.
        val res = listOf(File("src/main/res"), File("core/src/main/res")).first { it.isDirectory }
        val folders = res.listFiles().orEmpty().map { it.name }
            .filter { it.startsWith("values-") }
            .map { it.removePrefix("values-").substringBefore("-r") }
            .toSet() + "en"
        assertEquals(folders, TRANSLATED_LANGUAGES)
    }
}
