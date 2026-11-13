package com.masterclock.app.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.masterclock.app.logic.ChessClockSettings
import com.masterclock.app.logic.NotebookNote
import com.masterclock.app.logic.OmniSettings
import com.masterclock.app.logic.SavedPreset
import com.masterclock.app.logic.ScoreboardSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.encodeToString

/**
 * Replaced when it cannot be read as a file at all. Without a handler the exception reached the first
 * read, in the init of the ViewModel: a crash at every launch, which only clearing the app's storage
 * ended and which took the same data with it. An ordinary read failure, a full disk say, still throws,
 * since treating that as empty would write the defaults over a store that is intact.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { e ->
        Log.e("SettingsRepository", "Stored settings are corrupt and were reset", e)
        emptyPreferences()
    },
)

/**
 * How everything stored here is read. [Json.coerceInputValues] makes a value this build does not
 * know -- an enum constant from a newer version, after a downgrade -- fall back to that field's
 * default, where it used to fail the whole object.
 */
internal val storedJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

/**
 * The stored settings, as much of them as can be read. Taking the defaults whole dropped every note
 * and every preference, and the next save wrote that over the stored copy: one setting this build
 * could not read cost the player their notebook. So each field is tried on its own, and each note,
 * and one that cannot be read costs only itself.
 */
internal fun decodeStoredSettings(text: String): ChessClockSettings = try {
    storedJson.decodeFromString<ChessClockSettings>(text)
} catch (_: Exception) {
    val fields = runCatching { storedJson.parseToJsonElement(text).jsonObject }.getOrNull().orEmpty()
    val notes = runCatching {
        fields["notebookNotes"]?.jsonArray?.mapNotNull { element ->
            runCatching { storedJson.decodeFromJsonElement(NotebookNote.serializer(), element) }.getOrNull()
        }
    }.getOrNull().orEmpty()
    val readable = fields.filter { (name, value) ->
        name != "notebookNotes" && runCatching {
            storedJson.decodeFromJsonElement(ChessClockSettings.serializer(), JsonObject(mapOf(name to value)))
        }.isSuccess
    }
    storedJson.decodeFromJsonElement(ChessClockSettings.serializer(), JsonObject(readable)).copy(notebookNotes = notes)
}

/** The notebook as stored under its own key, each note read on its own; see [decodeStoredSettings]. */
internal fun decodeStoredNotebook(text: String): List<NotebookNote> = runCatching {
    storedJson.parseToJsonElement(text).jsonArray.mapNotNull { element ->
        runCatching { storedJson.decodeFromJsonElement(NotebookNote.serializer(), element) }.getOrNull()
    }
}.getOrDefault(emptyList())

/**
 * The settings as the store holds them: the settings under one key and the notebook under another.
 *
 * The notebook was a field of the settings, stored with them, so every write of a setting -- a
 * colour, each frame of a slider -- encoded every note and every stroke of every drawing again. It
 * is still a field of [ChessClockSettings] in memory, where everything reads it; only the store
 * keeps the two apart, and writes the notebook when the notebook changed.
 *
 * A store from before the split holds its notes in [settingsText], and they are read from there
 * until the first write moves them. Notes found in both -- an older build ran on this store and
 * wrote some into the settings again -- are all kept, the notebook's own first.
 */
internal fun storedSettingsOf(settingsText: String?, notebookText: String?): ChessClockSettings {
    val settings = settingsText?.let(::decodeStoredSettings) ?: ChessClockSettings()
    if (notebookText == null) return settings
    val notes = decodeStoredNotebook(notebookText)
    val known = notes.mapTo(HashSet()) { it.id }
    return settings.copy(notebookNotes = notes + settings.notebookNotes.filter { known.add(it.id) })
}

class SettingsRepository(private val context: Context) {
    private val json = storedJson
    private val SETTINGS_KEY = stringPreferencesKey("chess_clock_settings")
    private val NOTEBOOK_KEY = stringPreferencesKey("notebook_notes")
    private val OMNI_SETTINGS_KEY = stringPreferencesKey("omni_timer_settings")
    private val CUSTOM_PRESETS_KEY = stringPreferencesKey("custom_presets")
    private val SCOREBOARD_KEY = stringPreferencesKey("scoreboard_session")

    val settingsFlow: Flow<ChessClockSettings> = context.dataStore.data.map { preferences ->
        storedSettingsOf(preferences[SETTINGS_KEY], preferences[NOTEBOOK_KEY])
    }

    /**
     * The settings without the notebook, for what follows them as they change and has no use for
     * it. Decoded only when the settings themselves were written: the store is one file, and a note
     * edited, a preset saved or a score added each emitted here and were decoded whole.
     */
    val settingsWithoutNotebookFlow: Flow<ChessClockSettings> = context.dataStore.data
        .map { preferences -> preferences[SETTINGS_KEY] }
        .distinctUntilChanged()
        .map { text -> (text?.let(::decodeStoredSettings) ?: ChessClockSettings()).copy(notebookNotes = emptyList()) }

    /**
     * One write at a time, in the order asked. Each encodes off the caller's thread, which is the
     * main one: DataStore runs an edit's block in its caller's context, so the encoding sat on the
     * main thread for every write. Encoded outside the edit, two writes could reach the store in
     * either order, and the older settings land last; the lock keeps them in line.
     */
    private val writes = Mutex()

    /** The notes as last written by this instance, by identity: a settings change keeps the same list. */
    private var writtenNotes: List<NotebookNote>? = null

    val omniSettingsFlow: Flow<OmniSettings> = context.dataStore.data.map { preferences ->
        val jsonStr = preferences[OMNI_SETTINGS_KEY]
        if (jsonStr != null) {
            try {
                json.decodeFromString<OmniSettings>(jsonStr)
            } catch (_: Exception) {
                OmniSettings()
            }
        } else {
            OmniSettings()
        }
    }

    val customPresetsFlow: Flow<List<SavedPreset>> = context.dataStore.data.map { preferences ->
        val jsonStr = preferences[CUSTOM_PRESETS_KEY]
        if (jsonStr != null) {
            // One preset at a time: a single one that no longer decodes -- an enum value renamed in an
            // update, or a downgrade -- made the whole list come back empty, and the next save wrote
            // that empty list over every preset the player had.
            try {
                json.parseToJsonElement(jsonStr).jsonArray.mapNotNull { element ->
                    runCatching { json.decodeFromJsonElement(SavedPreset.serializer(), element) }.getOrNull()
                }
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    val scoreboardFlow: Flow<ScoreboardSession> = context.dataStore.data.map { preferences ->
        val jsonStr = preferences[SCOREBOARD_KEY]
        if (jsonStr != null) {
            try {
                json.decodeFromString<ScoreboardSession>(jsonStr)
            } catch (_: Exception) {
                ScoreboardSession()
            }
        } else {
            ScoreboardSession()
        }
    }

    suspend fun saveScoreboard(session: ScoreboardSession) {
        writes.withLock {
            val text = withContext(Dispatchers.Default) { json.encodeToString(session) }
            context.dataStore.edit { preferences -> preferences[SCOREBOARD_KEY] = text }
        }
    }

    suspend fun saveCustomPresets(presets: List<SavedPreset>) {
        writes.withLock {
            val text = withContext(Dispatchers.Default) { json.encodeToString(presets) }
            context.dataStore.edit { preferences -> preferences[CUSTOM_PRESETS_KEY] = text }
        }
    }

    suspend fun saveSettings(settings: ChessClockSettings) {
        writes.withLock {
            val notes = settings.notebookNotes
            // By identity: a changed setting is a copy that keeps the same list, and comparing two
            // notebooks stroke by stroke would cost what is being saved here.
            val notebookChanged = notes !== writtenNotes
            val (settingsText, notebookText) = withContext(Dispatchers.Default) {
                json.encodeToString(settings.copy(notebookNotes = emptyList())) to
                    if (notebookChanged) json.encodeToString(notes) else null
            }
            context.dataStore.edit { preferences ->
                preferences[SETTINGS_KEY] = settingsText
                if (notebookText != null) preferences[NOTEBOOK_KEY] = notebookText
            }
            writtenNotes = notes
        }
    }

    suspend fun saveOmniSettings(settings: OmniSettings) {
        writes.withLock {
            val text = withContext(Dispatchers.Default) { json.encodeToString(settings) }
            context.dataStore.edit { preferences -> preferences[OMNI_SETTINGS_KEY] = text }
        }
    }
}
