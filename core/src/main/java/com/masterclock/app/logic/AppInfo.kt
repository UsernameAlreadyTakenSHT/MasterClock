package com.masterclock.app.logic

// Single source of truth for the version footer shown in Settings (More or Modes page,
// depending on flavor). Update BUILD_DATE and append to CHANGELOG on every release.
object AppInfo {
    const val BUILD_DATE = "2026-07-21"

    data class ChangelogEntry(
        val version: String,
        val date: String,
        val notes: List<String>,
    )

    val CHANGELOG = listOf(
        ChangelogEntry(
            version = "0.8.6",
            date = "2026-07-21",
            notes = listOf(
                "Each flavor's launcher icon now prints its own word (FULL/STD/LITE/MINI) inside the clock face.",
                "ExtraLight renamed to Mini everywhere, including the package ID — reinstall needed if updating from ExtraLight.",
            ),
        ),
        ChangelogEntry(
            version = "0.8.5",
            date = "2026-07-21",
            notes = listOf(
                "Each flavor now shows its own name in the launcher.",
                "Standard and Light now have access to Omni-Timer.",
                "Fixed Move Timer (Global Shared) flagging every player out on a single move-timer expiry.",
                "Fixed Gong/Phases/Move Timer (Shared)/Chrono (one-for-all) games never being saved to history.",
                "Gong's move-phase background now matches the classic active-player green.",
                "Random/Hidden: Reset keeps the already-rolled time instead of re-rolling.",
                "ExtraLight's Bonus/Move Timer submenus restricted to Fisher/Standard only.",
            ),
        ),
        ChangelogEntry(
            version = "0.8.4",
            date = "2026-07-20",
            notes = listOf(
                "Light and ExtraLight now have Sound/Haptic/Keep-screen-awake toggles.",
            ),
        ),
        ChangelogEntry(
            version = "0.8.3",
            date = "2026-07-20",
            notes = listOf(
                "App ID renamed (io.github.usernamealreadytakensht.masterclock.*) for store submission — reinstall needed if updating from an older version.",
                "Added a privacy policy page.",
            ),
        ),
        ChangelogEntry(
            version = "0.8.2",
            date = "2026-07-20",
            notes = listOf(
                "Omni: added optional auto-cutoff per level (Phase/Turn/Round/Game/Session).",
                "Fixed sound and voice feedback missing on several timer modes.",
                "Fixed FIDE Periods delay bonus not applying on the US preset.",
                "Fixed Phases mode getting stuck instead of waiting for confirmation.",
            ),
        ),
        ChangelogEntry(
            version = "0.8.1",
            date = "2026-07-20",
            notes = listOf(
                "Fixed dark mode not applying to the e-ink navigation bar.",
                "Refined e-ink design components to match Mudita guidelines.",
                "Changelog & credits popup now has separate tabs.",
            ),
        ),
        ChangelogEntry(
            version = "0.8.0",
            date = "2026-07-20",
            notes = listOf(
                "Initial release.",
            ),
        ),
    )

    data class CreditEntry(
        val title: String,
        val detail: String,
    )

    val CREDITS = listOf(
        CreditEntry(
            title = "Logo icon",
            detail = "Paweł Kuna (opensvg.dev, v3.44.0) — MIT License",
        ),
        CreditEntry(
            title = "Chess pieces",
            detail = "\"Cburnett\" style, Wikimedia Commons — GFDL and CC BY-SA 3.0",
        ),
        CreditEntry(
            title = "Audio — Gong",
            detail = "Zen Gong, Alex_Jauk (Pixabay)",
        ),
        CreditEntry(
            title = "Audio — Beep",
            detail = "Beep, u_edtmwfwu7c (Pixabay)",
        ),
        CreditEntry(
            title = "Audio — Final Beep",
            detail = "Public Domain Beep Sound, qubodup (Pixabay)",
        ),
        CreditEntry(
            title = "Audio — Switch",
            detail = "Light Switch (Pixabay)",
        ),
        CreditEntry(
            title = "License",
            detail = "Project licensed under the MIT License.",
        ),
    )
}
