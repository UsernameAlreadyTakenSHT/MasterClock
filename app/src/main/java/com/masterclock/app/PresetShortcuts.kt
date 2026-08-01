package com.masterclock.app

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.masterclock.app.logic.FlavorConfig
import com.masterclock.app.logic.ShortcutTarget

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
                .setShortLabel(target.label.take(18))
                .setLongLabel(target.label.take(40))
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
     * Tells the launcher a shortcut was actually used, so it can rank and predict them. Without
     * this the shortcut list is static from the system's point of view.
     */
    fun reportUsed(context: Context, id: String) {
        runCatching { ShortcutManagerCompat.reportShortcutUsed(context, id) }
    }
}
