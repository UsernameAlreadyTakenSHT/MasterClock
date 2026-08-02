package com.masterclock.app.logic

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
