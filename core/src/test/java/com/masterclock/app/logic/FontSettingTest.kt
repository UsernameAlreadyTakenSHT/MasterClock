package com.masterclock.app.logic

import com.masterclock.app.data.decodeStoredSettings
import com.masterclock.app.data.storedJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The font is a matter of looks alone. Everything here holds today through general rules -- a
 * missing field takes its default, an unknown value is coerced, the game-ending check names its
 * fields -- and nothing else would notice the day one of them stopped covering the font.
 */
class FontSettingTest {

    private val mono = ChessClockSettings(appFont = AppFont.NOTO_SANS_MONO)

    @Test
    fun `a new install draws in the system's font`() {
        assertEquals(AppFont.SYSTEM, ChessClockSettings().appFont)
    }

    @Test
    fun `the chosen font reads back as it was stored`() {
        val stored = storedJson.encodeToString(ChessClockSettings.serializer(), mono)
        assertEquals(AppFont.NOTO_SANS_MONO, decodeStoredSettings(stored).appFont)
    }

    @Test
    fun `settings stored before there was a font read back in the system's`() {
        val stored = storedJson.encodeToString(ChessClockSettings.serializer(), ChessClockSettings(confirmReset = false))
        val older = stored.replace(Regex(",?\"appFont\":\"[A-Z_]+\""), "")
        val read = decodeStoredSettings(older)
        assertEquals(AppFont.SYSTEM, read.appFont)
        assertFalse(read.confirmReset)
    }

    @Test
    fun `a font this version does not know falls back to the system's and keeps the rest`() {
        val stored = storedJson.encodeToString(ChessClockSettings.serializer(), mono.copy(confirmReset = false))
        val newer = stored.replace("\"NOTO_SANS_MONO\"", "\"A_FONT_FROM_LATER\"")
        val read = decodeStoredSettings(newer)
        assertEquals(AppFont.SYSTEM, read.appFont)
        assertFalse(read.confirmReset)
    }

    @Test
    fun `changing the font does not end the game on the clock`() {
        AppFont.entries.forEach { assertFalse(settingsEndTheGame(mono, mono.copy(appFont = it))) }
    }

    @Test
    fun `a preset changes the time control and keeps the font`() {
        val preset = ChessClockSettings(main = ChessClockSettings().main.copy(initialTimeMs = 180_000))
        val after = applyPresetTimeControl(mono, preset)
        assertEquals(180_000L, after.main.initialTimeMs)
        assertEquals(AppFont.NOTO_SANS_MONO, after.appFont)
    }

    @Test
    fun `reset settings puts the font back to the system's, as it does the theme`() {
        assertEquals(AppFont.SYSTEM, settingsAfterReset(mono).appFont)
    }
}
