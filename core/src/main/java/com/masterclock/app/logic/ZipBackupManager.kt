package com.masterclock.app.logic

import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipInputStream

/** A backup archive violated one of [ZipBackupManager]'s size/entry-count limits. */
class ZipBackupTooLargeException(message: String) : IOException(message)

object ZipBackupManager {
    private val json = Json { ignoreUnknownKeys = true }

    private const val SETTINGS_ENTRY = "settings.json"
    private const val LOGS_ENTRY = "logs.json"
    private const val SCOREBOARD_ENTRY = "scoreboard.json"
    private const val PRESETS_ENTRY = "presets.json"
    private const val OMNI_ENTRY = "omni.json"

    /**
     * Where the notebook's own media files sit in the archive, under their own file names.
     *
     * The backup used to hold the three JSON entries and nothing else, while the screen offering it
     * said it held the audio and image files too. Restored onto a new phone, every photo, recording
     * and video note came back pointing at a file that did not exist.
     */
    private const val MEDIA_PREFIX = "media/"

    /**
     * The only JSON entries a backup carries; an archive's other entries are skipped unread.
     *
     * The name used to be examined only after the entry had already been buffered in full, so an
     * archive padded with entries this parser was never going to look at still cost the heap in
     * full -- and at the per-entry ceiling, decoding one of those buffers into a String could
     * exhaust it outright.
     */
    private val KNOWN_ENTRIES = setOf(SETTINGS_ENTRY, LOGS_ENTRY, SCOREBOARD_ENTRY, PRESETS_ENTRY, OMNI_ENTRY)

    // Defends against zip bombs: a small compressed file can decompress to gigabytes. entry.size
    // (the declared uncompressed size in the zip header) is attacker-controlled and not trustworthy,
    // so these limits are enforced by counting actual bytes read during decompression.
    //
    // They stay deliberately generous. A legitimate backup has no principled ceiling -- with
    // logHistoryLimit set to -1 the game history is unlimited, and notebook drawings ride along in
    // settings.json -- so a tighter limit would start refusing long-running users their own
    // backups, which is a worse failure than the one being defended against here.
    private const val MAX_ENTRY_SIZE_BYTES = 20L * 1024 * 1024 // 20 MB per entry
    private const val MAX_TOTAL_SIZE_BYTES = 50L * 1024 * 1024 // 50 MB across the whole archive

    // Media has budgets of its own, kept apart from the ones above so that a notebook's videos
    // cannot make room for a bomb hidden in the JSON. Media is never held in memory -- it is
    // streamed to disk -- so these bound disk use and decompression time rather than the heap, and
    // the export refuses a notebook beyond them rather than writing a backup this would refuse.
    const val MAX_MEDIA_ENTRY_BYTES = 1024L * 1024 * 1024 // 1 GB per file
    const val MAX_MEDIA_TOTAL_BYTES = 4L * 1024 * 1024 * 1024 // 4 GB across the archive

    // Five JSON entries and up to three media files for each of the notebook's 500 notes.
    internal const val MAX_ENTRIES = 1600

    /**
     * Writes a full backup to [output].
     *
     * Straight to the destination, where it used to be built in the cache and copied: with the
     * media in it, that doubled the space a backup needed, and a provider handing back no stream
     * at all was reported as "Backup successful!".
     */
    fun writeFullBackup(
        output: OutputStream,
        settings: ChessClockSettings,
        logs: List<GameLog>,
        scoreboard: ScoreboardSession,
        presets: List<SavedPreset>,
        omniSettings: OmniSettings,
        mediaFiles: List<File>,
    ) {
        // Checked before a byte is written, so a notebook too large to restore fails the export
        // outright instead of producing an archive the import would refuse.
        if (mediaFiles.any { it.length() > MAX_MEDIA_ENTRY_BYTES } || mediaFiles.sumOf { it.length() } > MAX_MEDIA_TOTAL_BYTES) {
            throw ZipBackupTooLargeException("The notebook's media exceeds what a backup can restore")
        }
        ZipOutputStream(output).use { zos ->
            addToZip(zos, SETTINGS_ENTRY, json.encodeToString(settings))
            addToZip(zos, LOGS_ENTRY, json.encodeToString(logs))
            addToZip(zos, SCOREBOARD_ENTRY, json.encodeToString(scoreboard))
            addToZip(zos, PRESETS_ENTRY, json.encodeToString(presets))
            addToZip(zos, OMNI_ENTRY, json.encodeToString(omniSettings))
            mediaFiles.forEach { file ->
                zos.putNextEntry(ZipEntry(MEDIA_PREFIX + file.name))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    /**
     * The notebook's media files that exist on this device, for [writeFullBackup].
     *
     * Each path goes through [sanitizeImportedMediaPath] first, so a backup can only ever carry the
     * notebook's own files -- never, whatever a note says, the settings store or anything else in
     * the sandbox.
     */
    fun mediaFilesToBackUp(filesDir: File, notes: List<NotebookNote>): List<File> =
        notes.asSequence()
            .flatMap { sequenceOf(it.audioPath, it.imagePath, it.videoPath) }
            .mapNotNull { sanitizeImportedMediaPath(filesDir, it) }
            .map { File(it) }
            .filter { it.isFile }
            .distinctBy { it.name }
            .toList()

    /**
     * Where each media entry of a backup is restored to, keyed by its file name.
     *
     * Only names some imported note refers to, and only at the place [sanitizeImportedMediaPath]
     * accepts for it -- the same check the note's path itself must pass to be kept -- so an entry
     * can neither choose its destination nor land anywhere a note could not have pointed already.
     */
    fun mediaDestinations(filesDir: File, notes: List<NotebookNote>): Map<String, File> =
        notes.asSequence()
            .flatMap { sequenceOf(it.audioPath, it.imagePath, it.videoPath) }
            .mapNotNull { sanitizeImportedMediaPath(filesDir, it) }
            .map { File(it) }
            .associateBy { it.name }

    fun extractBackup(zipFile: File): SharePackage = extractBackup { zipFile.inputStream() }

    /**
     * Reads the JSON entries of a backup. Writes nothing: media entries are measured and skipped,
     * and only [restoreMedia] -- run once this has accepted the whole archive -- ever writes one.
     */
    fun extractBackup(open: () -> InputStream): SharePackage {
        var settings: ChessClockSettings? = null
        var logs: List<GameLog>? = null
        var scoreboard: ScoreboardSession? = null
        var presets: List<SavedPreset>? = null
        var omniSettings: OmniSettings? = null

        var totalBytesRead = 0L
        var mediaBytesRead = 0L
        var entryCount = 0
        val collected = mutableSetOf<String>()

        try {
            ZipInputStream(open()).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    entryCount++
                    if (entryCount > MAX_ENTRIES) {
                        throw ZipBackupTooLargeException("Backup archive has more than $MAX_ENTRIES entries")
                    }

                    val name = entry.name
                    // collected.add() is what makes this first-one-wins: a second settings.json is
                    // skipped rather than left free to overwrite the one already parsed.
                    val keep = !entry.isDirectory && name in KNOWN_ENTRIES && collected.add(name)
                    val buffer = if (keep) ByteArrayOutputStream() else null

                    // A skipped entry is still measured. Not reading one means not holding it in
                    // memory, not trusting it: a bomb hidden in an entry nobody parses has to count
                    // against the archive's budget like every other. Media counts against the media
                    // budget, everything else against the JSON one.
                    if (!entry.isDirectory && name.startsWith(MEDIA_PREFIX)) {
                        mediaBytesRead += consumeEntryBounded(zis, MAX_MEDIA_ENTRY_BYTES) { _, _ -> }
                        if (mediaBytesRead > MAX_MEDIA_TOTAL_BYTES) {
                            throw ZipBackupTooLargeException("Backup archive exceeds the $MAX_MEDIA_TOTAL_BYTES byte media limit")
                        }
                    } else {
                        totalBytesRead += consumeEntryBounded(zis, MAX_ENTRY_SIZE_BYTES) { chunk, length ->
                            buffer?.write(chunk, 0, length)
                        }
                        if (totalBytesRead > MAX_TOTAL_SIZE_BYTES) {
                            throw ZipBackupTooLargeException("Backup archive exceeds the $MAX_TOTAL_SIZE_BYTES byte total size limit")
                        }
                    }

                    if (buffer != null) {
                        val content = buffer.toString(Charsets.UTF_8.name())
                        when (name) {
                            // settings.json is the point of the backup, so a malformed one fails the
                            // whole import rather than degrading; the other two are extras and are
                            // allowed to drop out on their own.
                            SETTINGS_ENTRY -> settings = json.decodeFromString(content)
                            LOGS_ENTRY -> try { logs = json.decodeFromString(content) } catch (e: Exception) {
                                Log.w("ZipBackupManager", "Failed to parse logs.json from backup, skipping", e)
                            }
                            SCOREBOARD_ENTRY -> try { scoreboard = json.decodeFromString(content) } catch (e: Exception) {
                                Log.w("ZipBackupManager", "Failed to parse scoreboard.json from backup, skipping", e)
                            }
                            PRESETS_ENTRY -> try { presets = json.decodeFromString(content) } catch (e: Exception) {
                                Log.w("ZipBackupManager", "Failed to parse presets.json from backup, skipping", e)
                            }
                            OMNI_ENTRY -> try { omniSettings = json.decodeFromString(content) } catch (e: Exception) {
                                Log.w("ZipBackupManager", "Failed to parse omni.json from backup, skipping", e)
                            }
                        }
                    }

                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: OutOfMemoryError) {
            // Every buffer above is bounded and unreachable by the time this runs, so the heap
            // recovers. Letting the Error through would sail straight past the `catch (e: Exception)`
            // that both import screens rely on, turning a refused backup into a crash.
            throw ZipBackupTooLargeException("Backup archive did not fit in memory: ${e.message}")
        } catch (e: StackOverflowError) {
            // The same argument, for the other Error a hostile archive can raise: kotlinx
            // .serialization parses by recursive descent, so a settings.json nested a few thousand
            // arrays deep exhausts the stack rather than the heap. Size limits do not catch it --
            // the file is tiny -- and it was the one Error left that could reach a screen.
            throw ZipBackupTooLargeException("Backup archive is nested too deeply to parse")
        }

        // No settings.json means this archive is not one of ours. Substituting a default here read
        // as "restore an empty configuration": any zip at all -- a photo archive, an empty one, any
        // file renamed .zip, since ZipInputStream returns null for a bad signature rather than
        // throwing -- came back as factory settings, which the caller then persisted while
        // reporting success. The notebook lives inside ChessClockSettings, so that silently
        // destroyed every note and drawing the user had. The same argument the malformed case
        // already makes above: settings.json is the point of the backup.
        val restored = settings ?: throw NotASettingsFileException(
            "Archive has no $SETTINGS_ENTRY, so it is not a MasterClock backup"
        )

        return SharePackage(
            settings = restored,
            logs = logs,
            scoreboard = scoreboard,
            customPresets = presets,
            omniSettings = omniSettings,
        )
    }

    /**
     * Writes the media entries named in [destinations] to their destinations, and returns how many
     * it wrote.
     *
     * Second pass, for an archive [extractBackup] has already accepted in full. Every file is
     * written under [stagingDir] first and moved into place only once the whole archive has been
     * read within its limits, so an archive refused halfway leaves nothing behind. A destination
     * that already exists is never overwritten: on the device the backup came from it is the same
     * file, and anywhere else it is the user's own.
     *
     * The entry name only selects which destination to use; the destination itself comes from the
     * notes, through [mediaDestinations]. A name with a directory in it cannot match one.
     */
    fun restoreMedia(open: () -> InputStream, destinations: Map<String, File>, stagingDir: File): Int {
        if (destinations.isEmpty()) return 0
        stagingDir.deleteRecursively()
        if (!stagingDir.mkdirs()) throw IOException("Could not create $stagingDir")
        try {
            val staged = mutableMapOf<File, File>()
            var mediaBytesRead = 0L
            var entryCount = 0
            ZipInputStream(open()).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    entryCount++
                    if (entryCount > MAX_ENTRIES) {
                        throw ZipBackupTooLargeException("Backup archive has more than $MAX_ENTRIES entries")
                    }
                    val name = entry.name
                    if (!entry.isDirectory && name.startsWith(MEDIA_PREFIX)) {
                        val destination = destinations[name.removePrefix(MEDIA_PREFIX)]
                        val wanted = destination != null && !destination.exists() && staged.values.none { it == destination }
                        val stagedFile = if (wanted) File(stagingDir, "${staged.size}.part") else null
                        val out = stagedFile?.outputStream()
                        try {
                            mediaBytesRead += consumeEntryBounded(zis, MAX_MEDIA_ENTRY_BYTES) { chunk, length ->
                                out?.write(chunk, 0, length)
                            }
                        } finally {
                            out?.close()
                        }
                        if (mediaBytesRead > MAX_MEDIA_TOTAL_BYTES) {
                            throw ZipBackupTooLargeException("Backup archive exceeds the $MAX_MEDIA_TOTAL_BYTES byte media limit")
                        }
                        if (stagedFile != null && destination != null) staged[stagedFile] = destination
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            var restored = 0
            staged.forEach { (stagedFile, destination) ->
                destination.parentFile?.mkdirs()
                if (!destination.exists() && stagedFile.renameTo(destination)) restored++
            }
            return restored
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    /**
     * Streams [zis]'s current entry through [onChunk] and returns how many bytes it held, aborting
     * once more than [maxBytes] have been decompressed.
     *
     * The bytes are counted whether or not [onChunk] keeps them, which is what lets the caller skip
     * an entry without letting it slip out of the size limits.
     */
    private inline fun consumeEntryBounded(
        zis: ZipInputStream,
        maxBytes: Long,
        onChunk: (ByteArray, Int) -> Unit,
    ): Long {
        val chunk = ByteArray(8192)
        var total = 0L
        while (true) {
            val read = zis.read(chunk)
            if (read == -1) break
            total += read
            if (total > maxBytes) {
                throw ZipBackupTooLargeException("A backup archive entry exceeds the $maxBytes byte size limit")
            }
            onChunk(chunk, read)
        }
        return total
    }

    private fun addToZip(zos: ZipOutputStream, fileName: String, content: String) {
        val entry = ZipEntry(fileName)
        zos.putNextEntry(entry)
        zos.write(content.toByteArray())
        zos.closeEntry()
    }
}
