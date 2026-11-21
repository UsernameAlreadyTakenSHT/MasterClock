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
 * out to be, so the words always match the voice saying them. [hasNoVoice] says when there is no
 * voice at all, and [onNoVoice] is told the moment the engine answers so: the answer comes after
 * whatever asked for the voice has returned, and nothing was there to hear it.
 *
 * The engine is started when it is first needed, by [start] or by something to say. It was started
 * with the clock, at every launch of every build: a service bound for announcements that are off by
 * default, and for good in the builds whose settings cannot turn them on.
 */
class VoiceManager(private val context: Context, private val onNoVoice: () -> Unit = {}) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var started = false
    private var speechResources: Resources = context.resources

    /** False until the engine is up with a usable language, and for good if it never is. */
    private var isAvailable = false

    /** True once the engine has answered and there is no voice to speak with. */
    var hasNoVoice = false
        private set

    /**
     * Told of everything the clock asks to have said, as it is asked: the announcement's name and
     * what it is filled with, or the words themselves, and whether it waits for the one before.
     * Before the engine, so with no voice on the device and none yet started. Nothing in the app
     * listens; it is how a test hears a game, which no test could -- "twenty seconds" cut short by
     * "one" was found by a probe put in by hand.
     */
    var heard: ((what: String, with: List<Any>, afterTheLast: Boolean) -> Unit)? = null

    /** What was to be said while the engine was starting; the latest only, as the queue flushes. */
    private var waiting: (() -> Unit)? = null

    /**
     * Starts the engine again if it had said it has no voice, as [start] does not. The message that
     * says so sends the user to install one in the device's settings, and back from there nothing
     * asked again: the test button and every announcement stayed silent until the app was killed.
     *
     * Called where the user asks for the voice -- the test button, the switch turned on -- and not
     * from what the clock has to say. Asked again by every announcement, a device with no voice
     * rebuilt its engine and showed "no voice" again every fifteen seconds of a game.
     */
    fun askAgain() {
        if (hasNoVoice) {
            runCatching { tts?.shutdown() }
            tts = null
            started = false
            hasNoVoice = false
        }
        start()
    }

    /** Starts the engine, once. Called ahead of the first announcement where one is expected. */
    fun start() {
        if (started) return
        started = true
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "Speech engine failed to start: $status")
            noVoice()
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
                waiting?.invoke()
                waiting = null
                return
            }
        }
        Log.w(TAG, "Speech engine has no usable voice")
        noVoice()
    }

    private fun noVoice() {
        hasNoVoice = true
        waiting = null
        onNoVoice()
    }

    private fun resourcesFor(locale: Locale): Resources {
        val config = Configuration(context.resources.configuration).apply { setLocale(locale) }
        return context.createConfigurationContext(config).resources
    }

    /** Says it now, or as soon as the engine is up if this is what started it. */
    private fun whenReady(say: () -> Unit) {
        start()
        when {
            isAvailable -> say()
            !hasNoVoice -> waiting = say
        }
    }

    /** Speaks one of the announcement strings, in the voice's own language. */
    fun speak(@StringRes text: Int, volume: Float, vararg args: Any) {
        heard?.invoke(context.resources.getResourceEntryName(text), args.toList(), false)
        // The words are read once the voice is known: its language decides them.
        whenReady { utter(speechResources.getString(text, *args), volume) }
    }

    /**
     * Speaks text as given -- a phase name the user typed, which has no translation. [afterTheLast]
     * lets what is being said finish first, where everything else cuts it short: the "one" that
     * follows "twenty seconds" a second later, which took the end off it.
     */
    fun speak(text: String, volume: Float, afterTheLast: Boolean = false) {
        heard?.invoke(text, emptyList(), afterTheLast)
        whenReady { utter(text, volume, afterTheLast) }
    }

    private fun utter(text: String, volume: Float, afterTheLast: Boolean = false) {
        val params = Bundle()
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        tts?.speak(text, if (afterTheLast) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH, params, null)
    }

    fun release() {
        waiting = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
