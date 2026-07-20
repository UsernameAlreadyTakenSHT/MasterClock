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

    // Defends against zip bombs: a small compressed file can decompress to gigabytes. entry.size
    // (the declared uncompressed size in the zip header) is attacker-controlled and not trustworthy,
    // so these limits are enforced by counting actual bytes read during decompression. See AUDIT.md
    // §3 (M3).
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
            addToZip(zos, "settings.json", json.encodeToString(settings))
            // 2. Logs
            addToZip(zos, "logs.json", json.encodeToString(logs))
            // 3. Scoreboard
            addToZip(zos, "scoreboard.json", json.encodeToString(scoreboard))
        }
        return backupFile
    }

    fun extractBackup(zipFile: File): SharePackage {
        var settings: ChessClockSettings? = null
        var logs: List<GameLog>? = null
        var scoreboard: ScoreboardSession? = null

        var totalBytesRead = 0L
        var entryCount = 0

        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                entryCount++
                if (entryCount > MAX_ENTRIES) {
                    throw ZipBackupTooLargeException("Backup archive has more than $MAX_ENTRIES entries")
                }

                val (content, entryBytes) = readEntryBounded(zis, MAX_ENTRY_SIZE_BYTES)
                totalBytesRead += entryBytes
                if (totalBytesRead > MAX_TOTAL_SIZE_BYTES) {
                    throw ZipBackupTooLargeException("Backup archive exceeds the $MAX_TOTAL_SIZE_BYTES byte total size limit")
                }

                when (entry.name) {
                    "settings.json" -> settings = json.decodeFromString(content)
                    "logs.json" -> try { logs = json.decodeFromString(content) } catch (e: Exception) {
                        Log.w("ZipBackupManager", "Failed to parse logs.json from backup, skipping", e)
                    }
                    "scoreboard.json" -> try { scoreboard = json.decodeFromString(content) } catch (e: Exception) {
                        Log.w("ZipBackupManager", "Failed to parse scoreboard.json from backup, skipping", e)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        return SharePackage(
            settings = settings ?: ChessClockSettings(),
            logs = logs,
            scoreboard = scoreboard
        )
    }

    /** Reads [zis]'s current entry into memory, aborting once more than [maxBytes] have been decompressed. */
    private fun readEntryBounded(zis: ZipInputStream, maxBytes: Long): Pair<String, Long> {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var total = 0L
        while (true) {
            val read = zis.read(chunk)
            if (read == -1) break
            total += read
            if (total > maxBytes) {
                throw ZipBackupTooLargeException("A backup archive entry exceeds the $maxBytes byte size limit")
            }
            buffer.write(chunk, 0, read)
        }
        return buffer.toString(Charsets.UTF_8.name()) to total
    }

    private fun addToZip(zos: ZipOutputStream, fileName: String, content: String) {
        val entry = ZipEntry(fileName)
        zos.putNextEntry(entry)
        zos.write(content.toByteArray())
        zos.closeEntry()
    }
}
