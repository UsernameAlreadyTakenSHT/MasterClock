package com.masterclock.app

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.masterclock.app.logic.FlavorConfig
import com.masterclock.app.logic.ShortcutTarget
import com.masterclock.app.logic.takeWholeChars

/**
 * Publishes the launcher's long-press shortcuts.
 *
 * ShortcutManagerCompat no-ops below API 25, so no version guard is needed here -- but the publish
 * can still be rejected (rate limiting, or a launcher that does not support shortcuts), which is
 * why the call is wrapped rather than assumed to succeed.
 */
object PresetShortcuts {

    const val EXTRA_SHORTCUT_ID = "com.masterclock.app.SHORTCUT_ID"

    /** Most launchers surface four; asking for more just gets silently trimmed. */
    private const val DESIRED_COUNT = 4

    fun publish(context: Context, targets: List<ShortcutTarget>) {
        // Mini and the E-Ink build have no presets screen at all, so they must publish nothing --
        // including clearing anything a previous install of the same flavor left behind.
        if (!FlavorConfig.hasPresets()) {
            runCatching { ShortcutManagerCompat.removeAllDynamicShortcuts(context) }
            return
        }

        val allowed = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)
            .takeIf { it > 0 } ?: DESIRED_COUNT
        val shortcuts = targets.take(minOf(DESIRED_COUNT, allowed)).map { target ->
            ShortcutInfoCompat.Builder(context, target.id)
                .setShortLabel(target.label.takeWholeChars(18))
                .setLongLabel(target.label.takeWholeChars(40))
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(
                    Intent(context, MainActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        putExtra(EXTRA_SHORTCUT_ID, target.id)
                    }
                )
                .build()
        }

        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }

    /**
     * Disables a shortcut whose target is gone, pinned copies included: the launcher greys it out
     * and shows [message] when it is tapped.
     */
    fun disable(context: Context, id: String, message: String) {
        runCatching { ShortcutManagerCompat.disableShortcuts(context, listOf(id), message) }
    }

    /**
     * Disables the pinned shortcuts of games no longer in the history, [liveIds] being those still
     * there. Trimmed or cleared, a game's shortcut stayed enabled, and tapping it opened the app and
     * did nothing. Only for a history that has been loaded: from the empty list it starts as, this
     * would disable all of them.
     */
    fun disableGoneGames(context: Context, liveIds: Set<String>, message: String) {
        runCatching {
            val gone = ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
                .filter { it.id.startsWith("game:") && it.id !in liveIds && it.isEnabled }
                .map { it.id }
            if (gone.isNotEmpty()) ShortcutManagerCompat.disableShortcuts(context, gone, message)
        }
    }

    /**
     * Tells the launcher a shortcut was actually used, so it can rank and predict them. Without
     * this the shortcut list is static from the system's point of view.
     */
    fun reportUsed(context: Context, id: String) {
        runCatching { ShortcutManagerCompat.reportShortcutUsed(context, id) }
    }
}
