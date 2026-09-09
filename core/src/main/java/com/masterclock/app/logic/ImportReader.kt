package com.masterclock.app.logic

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** A settings/share file was larger than [MAX_IMPORT_TEXT_BYTES]. */
class ImportTooLargeException(message: String) : IOException(message)

/**
 * Ceiling for a settings or share JSON file.
 *
 * A real export is a few kilobytes; 10 MB is far past any legitimate one while staying well under
 * what the heap can hold. The ZIP path has had its own limits since the zip-bomb fix, but the plain
 * JSON path read the whole stream with readText() and no bound at all, so picking a large file was
 * an out-of-memory crash.
 */
const val MAX_IMPORT_TEXT_BYTES = 10L * 1024 * 1024

/**
 * Reads [input] as UTF-8 text, refusing anything past [maxBytes].
 *
 * Counts bytes actually read rather than asking the stream how long it is: a content:// provider
 * reports whatever size it likes, and that number is exactly as untrusted as the data behind it.
 */
fun readImportText(input: InputStream, maxBytes: Long = MAX_IMPORT_TEXT_BYTES): String {
    // Bytes are collected first and decoded once at the end. Decoding each chunk as it arrives
    // would corrupt any multi-byte character that straddles a chunk boundary, which player names
    // and note text can easily contain.
    val buffer = ByteArrayOutputStream()
    val chunk = ByteArray(8192)
    var total = 0L
    while (true) {
        val read = input.read(chunk)
        if (read == -1) break
        total += read
        if (total > maxBytes) {
            throw ImportTooLargeException("Import file exceeds the $maxBytes byte limit")
        }
        buffer.write(chunk, 0, read)
    }
    return buffer.toString(Charsets.UTF_8.name())
}

/**
 * Ceiling for a backup archive on its way into the cache.
 *
 * Matches the total the extractor will read back out of it. Compression does not meaningfully
 * expand data, so an archive bigger than this cannot decompress to something [ZipBackupManager]
 * would have accepted anyway -- the limit refuses nothing a real backup needs, and refuses it
 * before the bytes reach the disk rather than after.
 */
const val MAX_IMPORT_ARCHIVE_BYTES = 50L * 1024 * 1024

/**
 * Streams [input] into [output], refusing anything past [maxBytes].
 *
 * The ZIP import used copyTo, so the picked file was written to the cache in full before any of
 * the extractor's limits applied: a 10 GB file chosen from the picker filled the cache directory
 * first and was rejected second. Same counting rule as [readImportText] -- bytes actually read,
 * never a size the provider claims.
 */
fun copyImportArchive(input: InputStream, output: OutputStream, maxBytes: Long = MAX_IMPORT_ARCHIVE_BYTES) {
    val chunk = ByteArray(8192)
    var total = 0L
    while (true) {
        val read = input.read(chunk)
        if (read == -1) break
        total += read
        if (total > maxBytes) {
            throw ImportTooLargeException("Backup archive exceeds the $maxBytes byte limit")
        }
        output.write(chunk, 0, read)
    }
}

/** A file was valid JSON but held nothing this app recognises, so it is not a MasterClock export. */
class NotASettingsFileException(message: String) : IOException(message)

private val importJson = Json { ignoreUnknownKeys = true }

/**
 * The property names [ChessClockSettings] actually declares, read off the serializer so the set
 * cannot drift as fields are added or renamed.
 */
@OptIn(ExperimentalSerializationApi::class)
private val settingsFieldNames: Set<String> =
    ChessClockSettings.serializer().descriptor.elementNames.toSet()

/**
 * Decodes an imported settings file, in either the current or the legacy bare-settings format.
 *
 * The point of doing it here rather than at each call site is the second half. Every field of
 * [ChessClockSettings] has a default and the decoder ignores unknown keys, so decoding straight
 * into it accepted *any* JSON object: a package.json, an exported contacts file, `{"a":1}`. Each
 * import screen then persisted the resulting all-defaults settings and reported "Import
 * successful", and because the notebook lives inside [ChessClockSettings], the price of picking the
 * wrong file in the document picker was every note and drawing the user had.
 *
 * So a legacy file now has to carry at least one field this app knows. The import chain had been
 * hardened against hostile content and never against irrelevant content -- and irrelevant content
 * is the one an ordinary mis-tap reaches.
 */
fun decodeSharePackage(content: String): SharePackage {
    val root = importJson.parseToJsonElement(content) as? JsonObject
        ?: throw NotASettingsFileException("Import file is not a JSON object")

    if ("settings" in root) {
        // The current format. A failure here falls through rather than aborting, so a legacy file
        // that happens to carry a "settings" field of its own is still read below.
        runCatching { importJson.decodeFromJsonElement(SharePackage.serializer(), root) }
            .onSuccess { return it }
    }

    if (root.keys.none { it in settingsFieldNames }) {
        throw NotASettingsFileException("Import file holds no MasterClock settings")
    }
    return SharePackage(settings = importJson.decodeFromJsonElement(ChessClockSettings.serializer(), root))
}
