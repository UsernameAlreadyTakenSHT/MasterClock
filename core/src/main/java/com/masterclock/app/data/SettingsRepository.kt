package com.masterclock.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.masterclock.app.logic.ChessClockSettings
import com.masterclock.app.logic.OmniSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val SETTINGS_KEY = stringPreferencesKey("chess_clock_settings")
    private val OMNI_SETTINGS_KEY = stringPreferencesKey("omni_timer_settings")

    val settingsFlow: Flow<ChessClockSettings> = context.dataStore.data.map { preferences ->
        val settingsJson = preferences[SETTINGS_KEY]
        if (settingsJson != null) {
            try {
                json.decodeFromString<ChessClockSettings>(settingsJson)
            } catch (_: Exception) {
                ChessClockSettings()
            }
        } else {
            ChessClockSettings()
        }
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
