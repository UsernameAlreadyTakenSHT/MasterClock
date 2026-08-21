package com.masterclock.app.logic

import android.content.Context
import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
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

    /**
     * The only three entries a backup carries; an archive's other entries are skipped unread.
     *
     * The name used to be examined only after the entry had already been buffered in full, so an
     * archive padded with entries this parser was never going to look at still cost the heap in
     * full -- and at the per-entry ceiling, decoding one of those buffers into a String could
     * exhaust it outright.
     */
    private val KNOWN_ENTRIES = setOf(SETTINGS_ENTRY, LOGS_ENTRY, SCOREBOARD_ENTRY)

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
    private const val MAX_ENTRIES = 100

    fun createFullBackup(
        context: Context,
        settings: ChessClockSettings,
        logs: List<GameLog>,
        scoreboard: ScoreboardSession
    ): File {
        val backupFile = File(context.cacheDir, "master_clock_backup_${System.currentTimeMillis()}.zip")
        ZipOutputStream(FileOutputStream(backupFile)).use { zos ->
            // 1. Settings
            addToZip(zos, SETTINGS_ENTRY, json.encodeToString(settings))
            // 2. Logs
            addToZip(zos, LOGS_ENTRY, json.encodeToString(logs))
            // 3. Scoreboard
            addToZip(zos, SCOREBOARD_ENTRY, json.encodeToString(scoreboard))
        }
        return backupFile
    }

    fun extractBackup(zipFile: File): SharePackage {
        var settings: ChessClockSettings? = null
        var logs: List<GameLog>? = null
        var scoreboard: ScoreboardSession? = null

        var totalBytesRead = 0L
        var entryCount = 0
        val collected = mutableSetOf<String>()

        try {
            ZipInputStream(zipFile.inputStream()).use { zis ->
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
                    // against the archive's budget like every other.
                    totalBytesRead += consumeEntryBounded(zis, MAX_ENTRY_SIZE_BYTES) { chunk, length ->
                        buffer?.write(chunk, 0, length)
                    }
                    if (totalBytesRead > MAX_TOTAL_SIZE_BYTES) {
                        throw ZipBackupTooLargeException("Backup archive exceeds the $MAX_TOTAL_SIZE_BYTES byte total size limit")
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
        }

        return SharePackage(
            settings = settings ?: ChessClockSettings(),
            logs = logs,
            scoreboard = scoreboard
        )
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
