package com.masterclock.app.logic

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.annotation.StringRes
import java.util.*

/** The languages of core's spoken strings, one per values-XX folder; VoiceCandidatesTest holds them to it. */
internal val TRANSLATED_LANGUAGES = setOf("en", "de", "es", "fr", "it", "nl", "pt")

/**
 * The voices to try, best first: the device's language if the app speaks it, then English, then the
 * engine's own voices in a language the app has, and only then any other, since a voice in the wrong
 * language beats none. The engine's were taken in whatever order its set came in, so a phone in Polish
 * whose engine had no English could get a Polish voice reading English strings with Spanish on offer.
 */
internal fun voiceCandidates(device: Locale, engineDefault: Locale?, engineLanguages: Collection<Locale>): List<Locale> {
    val engine = (listOfNotNull(engineDefault) + engineLanguages.sortedBy { it.toLanguageTag() }).distinct()
    val (translated, other) = engine.partition { it.language in TRANSLATED_LANGUAGES }
    val spoken = if (device.language in TRANSLATED_LANGUAGES) listOf(device) else emptyList()
    return (spoken + Locale.US + translated + other).distinct()
}
private const val TAG = "VoiceManager"

/**
 * The voice announcements.
 *
 * They used to be English sentences spoken with a voice forced to Locale.US: a French user heard
 * English in an app that otherwise speaks French, and on a device with no English voice installed
 * setLanguage failed, nothing was ever initialised, and every announcement -- the volume test
 * included -- was silently dropped.
 *
 * The voice is now the device's language when the engine has it, English otherwise, then whatever
 * the engine offers, and the sentences are read from resources in whichever language that turned
 * out to be, so the words always match the voice saying them. [isAvailable] says whether there is a
 * voice at all.
 */
class VoiceManager(private val context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var speechResources: Resources = context.resources

    /** False until the engine is up with a usable language, and for good if it never is. */
    var isAvailable = false
        private set

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "Speech engine failed to start: $status")
            return
        }
        val engine = tts ?: return
        val device = context.resources.configuration.locales[0]
        // The device's language, then English, then whatever the engine has. An engine other than
        // Google's -- the one GrapheneOS ships, or one from F-Droid -- need not offer either of the
        // first two, and the app then had no voice at all with a working engine on the phone.
        // The device's only if the app says its sentences in it: a phone in Polish had a Polish voice
        // reading the English strings. See voiceCandidates.
        val engineDefault = runCatching { engine.defaultVoice?.locale }.getOrNull()
        val engineLanguages = runCatching { engine.availableLanguages.orEmpty() }.getOrDefault(emptySet())
        for (locale in voiceCandidates(device, engineDefault, engineLanguages)) {
            // LANG_AVAILABLE and better are non-negative; MISSING_DATA and NOT_SUPPORTED are not.
            if (engine.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE) {
                speechResources = resourcesFor(locale)
                isAvailable = true
                return
            }
        }
        Log.w(TAG, "Speech engine has no usable voice")
    }

    private fun resourcesFor(locale: Locale): Resources {
        val config = Configuration(context.resources.configuration).apply { setLocale(locale) }
        return context.createConfigurationContext(config).resources
    }

    /** Speaks one of the announcement strings, in the voice's own language. */
    fun speak(@StringRes text: Int, volume: Float, vararg args: Any) {
        if (isAvailable) speak(speechResources.getString(text, *args), volume)
    }

    /** Speaks text as given -- a phase name the user typed, which has no translation. */
    fun speak(text: String, volume: Float) {
        if (!isAvailable) return
        val params = Bundle()
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, null)
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
