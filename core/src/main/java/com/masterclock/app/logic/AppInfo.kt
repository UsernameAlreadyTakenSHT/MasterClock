package com.masterclock.app.logic

// Single source of truth for the version footer shown in Settings (More or Modes page,
// depending on flavor). Update BUILD_DATE and append to CHANGELOG on every release.
object AppInfo {
    const val BUILD_DATE = "2026-07-20"

    data class ChangelogEntry(
        val version: String,
        val date: String,
        val notes: List<String>,
    )

    val CHANGELOG = listOf(
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
