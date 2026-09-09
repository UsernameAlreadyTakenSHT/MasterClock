package com.masterclock.app.logic

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Covers [decodeSharePackage], which decides whether a file picked in the document picker is a
 * MasterClock settings export at all.
 *
 * The reason this needs a test of its own: every field of [ChessClockSettings] has a default and
 * the decoder ignores unknown keys, so decoding straight into it accepted any JSON object
 * whatsoever and produced factory settings. The caller persisted those and reported success, and
 * the notebook lives inside [ChessClockSettings] -- so picking the wrong file destroyed every note
 * and drawing the user had. These tests are the ones that fail if that acceptance comes back.
 */
class DecodeSharePackageTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `reads the current share format`() {
        val original = SharePackage(settings = ChessClockSettings(numberOfPlayers = 3))
        val decoded = decodeSharePackage(json.encodeToString(original))
        assertEquals(3, decoded.settings.numberOfPlayers)
    }

    @Test
    fun `reads the legacy bare-settings format`() {
        val decoded = decodeSharePackage(json.encodeToString(ChessClockSettings(numberOfPlayers = 4)))
        assertEquals(4, decoded.settings.numberOfPlayers)
    }

    @Test
    fun `keeps logs and scoreboard when the file carries them`() {
        val original = SharePackage(
            settings = ChessClockSettings(),
            logs = listOf(GameLog(settings = ChessClockSettings())),
            scoreboard = ScoreboardSession(),
        )
        val decoded = decodeSharePackage(json.encodeToString(original))
        assertEquals(1, decoded.logs?.size)
        assertNotNull(decoded.scoreboard)
    }

    @Test
    fun `refuses a JSON file belonging to another application`() {
        // A package.json is the shape a user actually has lying around, and it decoded cleanly.
        val foreign = """{"name":"my-app","version":"1.0.0","dependencies":{"react":"^18.0.0"}}"""
        assertThrows(NotASettingsFileException::class.java) { decodeSharePackage(foreign) }
    }

    @Test
    fun `refuses an object with no field this app knows`() {
        assertThrows(NotASettingsFileException::class.java) { decodeSharePackage("""{"a":1}""") }
    }

    @Test
    fun `refuses an empty object`() {
        assertThrows(NotASettingsFileException::class.java) { decodeSharePackage("{}") }
    }

    @Test
    fun `refuses JSON that is not an object`() {
        assertThrows(NotASettingsFileException::class.java) { decodeSharePackage("[1,2,3]") }
        assertThrows(NotASettingsFileException::class.java) { decodeSharePackage(""""just a string"""") }
    }

    @Test
    fun `accepts a legacy file carrying a single known field`() {
        // Older exports omit defaulted fields, so one recognised key has to be enough.
        val decoded = decodeSharePackage("""{"numberOfPlayers":3}""")
        assertEquals(3, decoded.settings.numberOfPlayers)
    }
}
