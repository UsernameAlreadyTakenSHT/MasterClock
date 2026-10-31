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
    fun `refuses an archive that carries no settings`() {
        // This used to return factory defaults, and the caller persisted them while reporting
        // "Import successful". Since the notebook lives inside ChessClockSettings, restoring any
        // zip that was not ours -- a photo archive, an empty one, any file renamed .zip -- wiped
        // every note and drawing the user had.
        assertThrows(NotASettingsFileException::class.java) {
            ZipBackupManager.extractBackup(zip("logs.json" to logsJson(1)))
        }
    }

    @Test
    fun `refuses an archive with nothing in it at all`() {
        assertThrows(NotASettingsFileException::class.java) {
            ZipBackupManager.extractBackup(zip())
        }
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

        // Each under the per-entry limit, together past the 256 MB the archive may hold.
        assertThrows(ZipBackupTooLargeException::class.java) {
            ZipBackupManager.extractBackup(zip(*Array(13) { "part$it.bin" to twentyMegabytes }))
        }
    }

    @Test
    fun `rejects an archive with too many entries`() {
        val many = Array(ZipBackupManager.MAX_ENTRIES + 1) { "junk-$it.bin" to text("x") }

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

    // --- presets, Omni and the notebook's media ---
    //
    // The backup used to carry the three JSON entries above and nothing else, while the screen
    // offering it promised the media too: restored onto a new phone, every photo, recording and
    // video note pointed at a file that did not exist, and saved presets and the Omni setup were
    // simply gone.

    /** A sandbox laid out like the app's: audio in filesDir, images and videos in shares/. */
    private fun sandbox(): File = temp.newFolder().also { File(it, "shares").mkdirs() }

    private fun mediaNotes(filesDir: File) = listOf(
        NotebookNote(type = NotebookNoteType.VOICE, audioPath = File(filesDir, "audio_a1.mp4").path),
        NotebookNote(type = NotebookNoteType.IMAGE, imagePath = File(filesDir, "shares/image_b2.jpg").path),
        NotebookNote(type = NotebookNoteType.VIDEO, videoPath = File(filesDir, "shares/video_c3.mp4").path),
    )

    private fun backUp(from: File, notes: List<NotebookNote>): File {
        val archive = temp.newFile()
        archive.outputStream().use { out ->
            ZipBackupManager.writeFullBackup(
                output = out,
                settings = ChessClockSettings(notebookNotes = notes),
                logs = emptyList(),
                scoreboard = ScoreboardSession(),
                presets = listOf(SavedPreset(id = "p1", name = "Blitz", settings = ChessClockSettings())),
                omniSettings = OmniSettings(numberOfPlayers = 6),
                mediaFiles = ZipBackupManager.mediaFilesToBackUp(from, notes),
            )
        }
        return archive
    }

    @Test
    fun `a backup restored onto another device brings its media, presets and Omni setup back`() {
        val source = sandbox()
        val notes = mediaNotes(source)
        File(source, "audio_a1.mp4").writeBytes(text("voice"))
        File(source, "shares/image_b2.jpg").writeBytes(text("photo"))
        File(source, "shares/video_c3.mp4").writeBytes(text("film"))
        val archive = backUp(source, notes)

        val target = sandbox()
        // On the other device the notes arrive naming its own sandbox, as they would after
        // sanitizeImportedSettings; here the two sandboxes stand in for the two devices.
        val arrived = mediaNotes(target)
        val pkg = ZipBackupManager.extractBackup(archive)
        val restored = ZipBackupManager.restoreMedia(
            open = { archive.inputStream() },
            destinations = ZipBackupManager.mediaDestinations(target, arrived),
            stagingDir = File(target, "restore_staging"),
        )

        assertEquals(3, restored)
        assertEquals("voice", File(target, "audio_a1.mp4").readText())
        assertEquals("photo", File(target, "shares/image_b2.jpg").readText())
        assertEquals("film", File(target, "shares/video_c3.mp4").readText())
        assertFalse("staging must not outlive the restore", File(target, "restore_staging").exists())
        assertEquals(listOf("Blitz"), pkg.customPresets?.map { it.name })
        assertEquals(6, pkg.omniSettings?.numberOfPlayers)
    }

    @Test
    fun `a restore never overwrites a file the device already has`() {
        val source = sandbox()
        File(source, "audio_a1.mp4").writeBytes(text("from the backup"))
        val archive = backUp(source, mediaNotes(source))

        val target = sandbox()
        File(target, "audio_a1.mp4").writeBytes(text("the user's own"))
        ZipBackupManager.restoreMedia(
            open = { archive.inputStream() },
            destinations = ZipBackupManager.mediaDestinations(target, mediaNotes(target)),
            stagingDir = File(target, "restore_staging"),
        )

        assertEquals("the user's own", File(target, "audio_a1.mp4").readText())
    }

    @Test
    fun `a media entry no note names is not written anywhere`() {
        val target = sandbox()
        val archive = zip(
            "settings.json" to settingsJson(10),
            "media/audio_zz.mp4" to text("stray"),
            "media/../datastore/settings.preferences_pb" to text("hostile"),
        )

        val restored = ZipBackupManager.restoreMedia(
            open = { archive.inputStream() },
            destinations = ZipBackupManager.mediaDestinations(target, mediaNotes(target)),
            stagingDir = File(target, "restore_staging"),
        )

        assertEquals(0, restored)
        assertFalse(File(target, "audio_zz.mp4").exists())
        assertFalse(File(target, "datastore").exists())
    }

    @Test
    fun `a note cannot aim a restore outside the notebook's own files`() {
        val target = sandbox()
        // The path the notebook's shredder was once aimed at; sanitizeImportedMediaPath refuses it,
        // so no destination exists for it whatever the archive holds.
        val hostile = listOf(NotebookNote(audioPath = File(target, "datastore/settings.preferences_pb").path))
        assertTrue(ZipBackupManager.mediaDestinations(target, hostile).isEmpty())
    }

    @Test
    fun `a backup only ever carries the notebook's own files`() {
        val source = sandbox()
        File(source, "datastore").mkdirs()
        File(source, "datastore/settings.preferences_pb").writeBytes(text("secrets"))
        val notes = listOf(NotebookNote(audioPath = File(source, "datastore/settings.preferences_pb").path))
        assertTrue(ZipBackupManager.mediaFilesToBackUp(source, notes).isEmpty())
    }

    @Test
    fun `a history larger than one entry is written in parts and comes back whole`() {
        val logs = List(60) { i -> GameLog(id = "g$i", settings = ChessClockSettings(), startTime = i.toLong(), events = List(20) { GameEvent(eventType = "MOVE", playerIndex = 1) }) }
        val archive = temp.newFile()
        archive.outputStream().use { out ->
            ZipBackupManager.writeFullBackup(out, ChessClockSettings(), logs, ScoreboardSession(), emptyList(), OmniSettings(), emptyList(), logsChunkBytes = 4_096)
        }
        val names = java.util.zip.ZipFile(archive).use { zip -> zip.entries().toList().map { it.name } }
        assertTrue(names.count { it.startsWith("logs") } > 1)
        assertEquals(logs.map { it.id }, ZipBackupManager.extractBackup(archive).logs?.map { it.id })
    }

    @Test
    fun `one game this version cannot read costs only itself`() {
        val archive = temp.newFile()
        java.util.zip.ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("settings.json")); zip.write("{}".toByteArray()); zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("logs.json"))
            zip.write("[{\"id\":\"a\",\"settings\":{}},{\"id\":\"b\",\"settings\":{},\"startTime\":\"not a number\"},{\"id\":\"c\",\"settings\":{}}]".toByteArray())
            zip.closeEntry()
        }
        assertEquals(listOf("a", "c"), ZipBackupManager.extractBackup(archive).logs?.map { it.id })
    }

    @Test
    fun `a value from a newer version is read as the default, not a failed restore`() {
        val archive = temp.newFile()
        java.util.zip.ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("settings.json"))
            zip.write("{\"numberOfPlayers\":3,\"main\":{\"mode\":\"A_MODE_FROM_THE_FUTURE\"}}".toByteArray())
            zip.closeEntry()
        }
        val settings = ZipBackupManager.extractBackup(archive).settings
        assertEquals(3, settings.numberOfPlayers)
        assertEquals(PlayerSettings().mode, settings.main.mode)
    }

    @Test
    fun `an entry too large to restore fails the export instead of writing it`() {
        val big = ChessClockSettings(notebookNotes = listOf(NotebookNote(content = "x".repeat(21 * 1024 * 1024))))
        assertThrows(ZipBackupTooLargeException::class.java) {
            ZipBackupManager.writeFullBackup(java.io.ByteArrayOutputStream(), big, emptyList(), ScoreboardSession(), emptyList(), OmniSettings(), emptyList())
        }
    }
}
