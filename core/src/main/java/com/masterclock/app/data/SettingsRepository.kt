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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

class SettingsRepository(private val context: Context) {
    private val json = storedJson
    private val SETTINGS_KEY = stringPreferencesKey("chess_clock_settings")
    private val OMNI_SETTINGS_KEY = stringPreferencesKey("omni_timer_settings")
    private val CUSTOM_PRESETS_KEY = stringPreferencesKey("custom_presets")
    private val SCOREBOARD_KEY = stringPreferencesKey("scoreboard_session")

    val settingsFlow: Flow<ChessClockSettings> = context.dataStore.data.map { preferences ->
        preferences[SETTINGS_KEY]?.let(::decodeStoredSettings) ?: ChessClockSettings()
    }

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
        context.dataStore.edit { preferences ->
            preferences[SCOREBOARD_KEY] = json.encodeToString(session)
        }
    }

    suspend fun saveCustomPresets(presets: List<SavedPreset>) {
        context.dataStore.edit { preferences ->
            preferences[CUSTOM_PRESETS_KEY] = json.encodeToString(presets)
        }
    }

    suspend fun saveSettings(settings: ChessClockSettings) {
        context.dataStore.edit { preferences ->
            preferences[SETTINGS_KEY] = json.encodeToString(settings)
        }
    }

    suspend fun saveOmniSettings(settings: OmniSettings) {
        context.dataStore.edit { preferences ->
            preferences[OMNI_SETTINGS_KEY] = json.encodeToString(settings)
        }
    }
}
