package com.masterclock.app

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Sharing the app itself: a copy of the installed APK, in the cache, handed to the share sheet.
 *
 * The copy cannot be deleted by the share: the app receiving the URI reads it after the chooser has
 * returned, and a Bluetooth transfer can take minutes. So it is left, and [clearStale] reclaims it
 * on a later launch, once it is certainly over.
 */
object ApkShare {
    private const val DIR = "apk_share"
    private const val STALE_MS = 60 * 60 * 1000L

    @Volatile private var cleared = false

    /**
     * Deletes copies more than an hour old, once per process. It ran on every creation of the
     * activity, and the activity is recreated by a switch to dark mode, a change of language, a
     * fold or unfold: a copy could go while the Bluetooth transfer that was sending it ran on.
     */
    fun clearStale(cacheDir: File) {
        if (cleared) return
        cleared = true
        val now = System.currentTimeMillis()
        File(cacheDir, DIR).listFiles()?.forEach { if (now - it.lastModified() > STALE_MS) it.deleteRecursively() }
    }

    /**
     * Copies the installed app into the share folder and returns the intent that sends it.
     *
     * [asZip] puts it in a ZIP: several phones' Bluetooth refuses an incoming .apk, and a ZIP goes
     * through. The recipient extracts the APK to install it.
     *
     * The name says which build it is, flavour and version: every share was "MasterClock.apk",
     * which told the recipient nothing and clashed with the last one in their downloads. Each file
     * is written beside its final name and moved into place, so a second share never truncates a
     * file a first recipient is still reading.
     *
     * A store that installs split APKs (Accrescent does) leaves the app in several files, and the
     * base one alone does not install: every part is sent, in one ZIP or as a set.
     */
    fun prepare(context: Context, asZip: Boolean): Intent {
        val appInfo = context.applicationInfo
        val folder = File(context.cacheDir, DIR).apply { mkdirs() }
        val parts = listOf(File(appInfo.publicSourceDir)) + (appInfo.splitPublicSourceDirs ?: emptyArray()).map(::File)
        val stem = "MasterClock-${BuildConfig.FLAVOR}-${BuildConfig.VERSION_NAME}"
        fun partName(i: Int, part: File) = when {
            parts.size == 1 -> "$stem.apk"
            i == 0 -> "$stem-base.apk"
            else -> "$stem-${part.name}"
        }

        val files = if (asZip) {
            listOf(
                writeAtomically(File(folder, "$stem.zip")) { out ->
                    ZipOutputStream(out).use { zip ->
                        parts.forEachIndexed { i, part ->
                            zip.putNextEntry(ZipEntry(partName(i, part)))
                            part.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
            )
        } else {
            parts.mapIndexed { i, part ->
                writeAtomically(File(folder, partName(i, part))) { out -> part.inputStream().use { it.copyTo(out) } }
            }
        }

        val uris = files.map { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.single())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        intent.type = if (asZip) "application/zip" else "application/vnd.android.package-archive"
        // The grant travels with the ClipData. The chooser copies EXTRA_STREAM into one by itself,
        // but a share sheet of the manufacturer's own need not.
        intent.clipData = ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return intent
    }

    private fun writeAtomically(destination: File, write: (OutputStream) -> Unit): File {
        val staging = File(destination.parentFile, "${destination.name}.part")
        try {
            staging.outputStream().use(write)
            // A recipient still reading the old file keeps it: deleting a name does not close it.
            destination.delete()
            if (!staging.renameTo(destination)) throw java.io.IOException("Could not move ${staging.name} into place")
        } finally {
            staging.delete()
        }
        return destination
    }
}
