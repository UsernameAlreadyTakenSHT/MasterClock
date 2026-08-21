package com.masterclock.app.logic

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Covers [ZipBackupManager.extractBackup], which parses a file the user picked from anywhere on the
 * device and is therefore the least trusted input the app takes.
 *
 * The size limits are exercised with real archives rather than by lowering the constants for the
 * test: the numbers being defended are the numbers that ship.
 */
class ZipBackupManagerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true }

    private fun zip(vararg entries: Pair<String, ByteArray>): File {
        val file = temp.newFile()
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            entries.forEach { (name, bytes) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return file
    }

    /**
     * Builds an archive carrying [name] twice, which [zip] cannot do on its own: Java's
     * ZipOutputStream refuses duplicate names, while nothing stops a crafted archive from holding
     * them and ZipInputStream hands both back as it walks the local headers in order.
     *
     * The second entry is written under a same-length placeholder and renamed in the raw bytes,
     * which is all the streaming reader under test ever looks at.
     */
    private fun zipWithDuplicate(name: String, first: ByteArray, second: ByteArray): File {
        val placeholder = "Z".repeat(name.length)
        val bytes = zip(name to first, placeholder to second).readBytes()
        val from = placeholder.toByteArray(Charsets.US_ASCII)
        val to = name.toByteArray(Charsets.US_ASCII)
        var i = 0
        while (i <= bytes.size - from.size) {
            if ((from.indices).all { bytes[i + it] == from[it] }) {
                from.indices.forEach { bytes[i + it] = to[it] }
                i += from.size
            } else {
                i++
            }
        }
        val patched = temp.newFile()
        patched.writeBytes(bytes)
        return patched
    }

    private fun text(value: String) = value.toByteArray(Charsets.UTF_8)

    private fun settingsJson(historyLimit: Int) =
        text(json.encodeToString(ChessClockSettings(logHistoryLimit = historyLimit)))

    private fun logsJson(count: Int) =
        text(json.encodeToString(List(count) { GameLog(settings = ChessClockSettings()) }))

    private fun scoreboardJson(name: String) =
        text(json.encodeToString(ScoreboardSession(player1Name = name)))

    // --- the happy path ---

    @Test
    fun `reads all three sections of a well-formed backup`() {
        val pkg = ZipBackupManager.extractBackup(
            zip(
                "settings.json" to settingsJson(42),
                "logs.json" to logsJson(3),
                "scoreboard.json" to scoreboardJson("Ada"),
            )
        )

        assertEquals(42, pkg.settings.logHistoryLimit)
        assertEquals(3, pkg.logs?.size)
        assertEquals("Ada", pkg.scoreboard?.player1Name)
    }

    @Test
    fun `falls back to default settings when the archive carries none`() {
        val pkg = ZipBackupManager.extractBackup(zip("logs.json" to logsJson(1)))

        assertEquals(ChessClockSettings().logHistoryLimit, pkg.settings.logHistoryLimit)
        assertEquals(1, pkg.logs?.size)
        assertNull(pkg.scoreboard)
    }

    // --- entries that are not ours ---

    @Test
    fun `ignores entries that are not part of a backup`() {
        val pkg = ZipBackupManager.extractBackup(
            zip(
                "README.txt" to text("hello"),
                "settings.json" to settingsJson(7),
                "nested/settings.json" to settingsJson(999),
                "logs.json" to logsJson(2),
                "payload.bin" to ByteArray(4096),
            )
        )

        // "nested/settings.json" is a different entry name, so it must not win the settings slot.
        assertEquals(7, pkg.settings.logHistoryLimit)
        assertEquals(2, pkg.logs?.size)
    }

    @Test
    fun `keeps the first of a duplicated entry`() {
        val pkg = ZipBackupManager.extractBackup(
            zipWithDuplicate("settings.json", settingsJson(11), settingsJson(22))
        )

        assertEquals(11, pkg.settings.logHistoryLimit)
    }

    // --- the size limits ---
    //
    // These matter most for entries the parser skips. Skipping one has to mean not holding it in
    // memory, never not measuring it -- otherwise a zip bomb just needs an unremarkable file name.

    @Test
    fun `rejects an oversized entry even when it is one it would skip`() {
        val overTheEntryLimit = ByteArray(20 * 1024 * 1024 + 1)

        assertThrows(ZipBackupTooLargeException::class.java) {
            ZipBackupManager.extractBackup(zip("payload.bin" to overTheEntryLimit))
        }
    }

    @Test
    fun `counts skipped entries against the total size limit`() {
        val twentyMegabytes = ByteArray(20 * 1024 * 1024)

        assertThrows(ZipBackupTooLargeException::class.java) {
            ZipBackupManager.extractBackup(
                zip(
                    "one.bin" to twentyMegabytes,
                    "two.bin" to twentyMegabytes,
                    "three.bin" to twentyMegabytes,
                )
            )
        }
    }

    @Test
    fun `rejects an archive with too many entries`() {
        val many = Array(101) { "junk-$it.bin" to text("x") }

        assertThrows(ZipBackupTooLargeException::class.java) {
            ZipBackupManager.extractBackup(zip(*many))
        }
    }

    // --- malformed sections ---

    @Test
    fun `drops a malformed logs section but keeps the settings`() {
        val pkg = ZipBackupManager.extractBackup(
            zip(
                "settings.json" to settingsJson(5),
                "logs.json" to text("{not json at all"),
                "scoreboard.json" to scoreboardJson("Grace"),
            )
        )

        assertEquals(5, pkg.settings.logHistoryLimit)
        assertNull(pkg.logs)
        assertEquals("Grace", pkg.scoreboard?.player1Name)
    }

    @Test
    fun `fails the whole import when the settings section is malformed`() {
        // Unlike logs and scoreboard, settings is the point of the backup: restoring it half-read
        // would silently hand the user a clock that is not the one they saved.
        assertThrows(SerializationException::class.java) {
            ZipBackupManager.extractBackup(zip("settings.json" to text("{not json at all")))
        }
    }
}
