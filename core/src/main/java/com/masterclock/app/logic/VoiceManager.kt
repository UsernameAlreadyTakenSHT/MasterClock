package com.masterclock.app.logic

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.annotation.StringRes
import java.util.*

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
        val engineOwn = runCatching { listOfNotNull(engine.defaultVoice?.locale) + engine.availableLanguages.orEmpty() }.getOrDefault(emptyList())
        for (locale in listOf(device, Locale.US) + engineOwn) {
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
