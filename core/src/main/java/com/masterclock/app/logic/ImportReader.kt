package com.masterclock.app.logic

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

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

/** A file was valid JSON but held nothing this app recognises, so it is not a MasterClock export. */
class NotASettingsFileException(message: String) : IOException(message)

/**
 * The decoder every import reads with: a settings file, a QR code, a backup.
 *
 * coerceInputValues, as the store already reads with: a file from a newer version can carry an
 * enum value this one does not know -- a new mode, a new kind of note -- and without it that one
 * value failed the whole file. A field that has a default takes it instead.
 */
internal val importJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

/**
 * Each element of a JSON array that decodes, the others left out; null when there is no array.
 *
 * A list decoded whole is lost whole: one game or preset this version cannot read took every other
 * one with it -- in a backup, under "Import successful".
 */
internal fun <T> decodeEach(serializer: KSerializer<T>, element: JsonElement?): List<T>? =
    (element as? JsonArray)?.mapNotNull { item -> runCatching { importJson.decodeFromJsonElement(serializer, item) }.getOrNull() }

/** One optional part of a package, or null when it is missing or cannot be read. */
private fun <T> decodeOptional(serializer: KSerializer<T>, element: JsonElement?): T? =
    element?.let { runCatching { importJson.decodeFromJsonElement(serializer, it) }.getOrNull() }

/**
 * A share package, its parts read one by one: the settings must decode, and the rest -- the games,
 * the scoreboard, the presets, Omni -- each drop out on their own, element by element for the lists.
 */
private fun decodeSharePackageObject(root: JsonObject): SharePackage = SharePackage(
    settings = importJson.decodeFromJsonElement(ChessClockSettings.serializer(), root.getValue("settings")),
    logs = decodeEach(GameLog.serializer(), root["logs"]),
    scoreboard = decodeOptional(ScoreboardSession.serializer(), root["scoreboard"]),
    customPresets = decodeEach(SavedPreset.serializer(), root["customPresets"]),
    omniSettings = decodeOptional(OmniSettings.serializer(), root["omniSettings"]),
)

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

    // The current format, held to the same test as the legacy one below: any object under a
    // "settings" key decodes, every field having a default, so another app's {"settings": {...}}
    // used to come through as factory settings and "Import successful". Empty is allowed -- it is
    // what this app writes for settings left entirely at their defaults. A failure falls through
    // rather than aborting, so a legacy file that carries a "settings" field of its own is still
    // read below.
    val wrapped = root["settings"] as? JsonObject
    if (wrapped != null && (wrapped.isEmpty() || wrapped.keys.any { it in settingsFieldNames })) {
        runCatching { decodeSharePackageObject(root) }
            .onSuccess { return it }
    }

    if (root.keys.none { it in settingsFieldNames }) {
        throw NotASettingsFileException("Import file holds no MasterClock settings")
    }
    return SharePackage(settings = importJson.decodeFromJsonElement(ChessClockSettings.serializer(), root))
}
