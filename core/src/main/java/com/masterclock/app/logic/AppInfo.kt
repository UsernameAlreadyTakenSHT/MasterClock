package com.masterclock.app.logic

// Single source of truth for the version footer shown in Settings (More or Modes page,
// depending on flavor). Update BUILD_DATE and append to CHANGELOG on every release.
object AppInfo {
    const val BUILD_DATE = "2026-08-01"

    data class ChangelogEntry(
        val version: String,
        val date: String,
        val notes: List<String>,
    )

    val CHANGELOG = listOf(
        ChangelogEntry(
            version = "0.8.11",
            date = "2026-08-01",
            notes = listOf(
                "The bundled rulebooks now open offline — six buttons on the rules screen used to do nothing at all.",
                "Every rules document is credited to its author or publisher, in the app and in the README.",
                "New Licenses tab listing the open-source libraries the app ships.",
            ),
        ),
        ChangelogEntry(
            version = "0.8.10",
            date = "2026-07-25",
            notes = listOf(
                "Fixed no sound at all on some phones: the volume rocker now adjusts the media stream in the app.",
                "Omni: pick each player's color in the wizard.",
                "Omni: Random turn order now has real sub-options (shuffle per round or draw per turn, no repeats, balanced draw).",
                "Omni: fixed the phase clock freezing at 00:00, time banking counted twice, endless looping rounds, and Close session not ending the session.",
            ),
        ),
        ChangelogEntry(
            version = "0.8.9",
            date = "2026-07-25",
            notes = listOf(
                "Light/Mini's System behavior toggles reordered to match paper's, and the card now has rounded corners like Complete/Standard.",
            ),
        ),
        ChangelogEntry(
            version = "0.8.8",
            date = "2026-07-25",
            notes = listOf(
                "Light/Mini/paper's \"Sound\" toggle now also controls the time's-up/flag sound.",
                "Omni-Timer is now labeled \"(alpha)\" in the mode picker.",
                "Byoyomi's 3 sub-modes and Mini's 3 mode cards are now all on one row instead of splitting one onto its own line.",
            ),
        ),
        ChangelogEntry(
            version = "0.8.7",
            date = "2026-07-22",
            notes = listOf(
                "FIDE Periods now has a real \"US Delay\" option, distinct from Sudden Death.",
                "Mini no longer shows a Bonus/Move Timer sub-panel with a single, unchangeable option.",
                "Fixed Reset reverting to a stale random roll after loading a Random/Hidden game from Last Games.",
            ),
        ),
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
        /** Shown under the detail line when set; the README already carried these links. */
        val url: String? = null,
    )

    val CREDITS = listOf(
        CreditEntry(
            title = "Logo icon",
            detail = "Paweł Kuna (opensvg.dev, v3.44.0) — MIT License",
            url = "https://opensvg.dev/icons",
        ),
        CreditEntry(
            title = "Chess pieces",
            detail = "\"Cburnett\" style, Wikimedia Commons — GFDL and CC BY-SA 3.0",
            url = "https://commons.wikimedia.org/wiki/Category:SVG_chess_pieces",
        ),
        CreditEntry(
            title = "Audio — Gong",
            detail = "Zen Gong, Alex_Jauk (Pixabay)",
            url = "https://pixabay.com/sound-effects/film-special-effects-zen-gong-199844/",
        ),
        CreditEntry(
            title = "Audio — Beep",
            detail = "Beep, u_edtmwfwu7c (Pixabay)",
            url = "https://pixabay.com/sound-effects/film-special-effects-beep-329314/",
        ),
        CreditEntry(
            title = "Audio — Final Beep",
            detail = "Public Domain Beep Sound, qubodup (Pixabay)",
            url = "https://pixabay.com/sound-effects/public-domain-beep-sound-100267/",
        ),
        CreditEntry(
            title = "Audio — Switch",
            detail = "Light Switch (Pixabay)",
            url = "https://pixabay.com/sound-effects/film-special-effects-light-switch-82388/",
        ),
        CreditEntry(
            title = "License",
            detail = "Project licensed under the MIT License.",
        ),
    )

    /**
     * The rules documents bundled in `core`'s `res/raw` and opened from the Rules screen.
     * Each credit is the attribution printed in, or embedded in, that document itself.
     */
    val RULES_CREDITS = listOf(
        CreditEntry(
            title = "Chess — Laws of Chess",
            detail = "FIDE, compiled by Alex Holowczak",
            url = "https://www.fide.com/",
        ),
        CreditEntry(
            title = "Draughts — FMJD Annexes 2024",
            detail = "Fédération Mondiale du Jeu de Dames — Ada Dorgelo, Frank Teer, Jacek Pawlicki",
            url = "https://www.fmjd.org/",
        ),
        CreditEntry(
            title = "Draughts (64) — Official Rules",
            detail = "International Draughts Federation",
            url = "https://idf64.org/",
        ),
        CreditEntry(
            title = "Shogi — FESA Rules",
            detail = "Federation of European Shogi Associations",
            url = "https://fesashogi.eu/",
        ),
        CreditEntry(
            title = "Nine Men's Morris",
            detail = "The game is in the public domain. Rulebook and \"Stacking Morris\" © 2022 Kanare Kato",
        ),
        CreditEntry(
            title = "Tafl — Historical Hnefatafl",
            detail = "World Tafl Federation",
            url = "https://aagenielsen.dk/",
        ),
        CreditEntry(
            title = "Quoridor",
            detail = "© & ® 1997 Gigamic, from a concept by Mirko Marchesi",
            url = "https://www.gigamic.com/",
        ),
        CreditEntry(
            title = "Abalone",
            detail = "© Abalone S.A., France — registered trademark, patent DM/012362. Distributed by FoxMind. All rights reserved",
            url = "https://www.foxmind.com/",
        ),
        CreditEntry(
            title = "Hex",
            detail = "David Beckwith, June 2021",
        ),
        CreditEntry(
            title = "Santorini",
            detail = "© 2007 Dr. Gordon Hamilton — may be reproduced for non-commercial purposes",
        ),
    )

    /**
     * A third-party library shipped in the app.
     *
     * Hand-maintained on purpose: Google's play-services-oss-licenses plugin would drag Play
     * Services into an app distributed on F-Droid. [appOnly] marks libraries the E-Ink build does
     * not ship, so it does not claim credit for code it does not contain.
     */
    data class OssLicense(
        val name: String,
        val copyright: String,
        val license: String,
        val url: String,
        val appOnly: Boolean = false,
    )

    val OSS_LICENSES = listOf(
        OssLicense(
            name = "AndroidX / Jetpack Compose",
            copyright = "The Android Open Source Project",
            license = "Apache License 2.0",
            url = "https://developer.android.com/jetpack",
        ),
        OssLicense(
            name = "Kotlin & kotlinx",
            copyright = "JetBrains s.r.o. and Kotlin contributors",
            license = "Apache License 2.0",
            url = "https://github.com/JetBrains/kotlin",
        ),
        OssLicense(
            name = "Material Components for Android",
            copyright = "Google LLC",
            license = "Apache License 2.0",
            url = "https://github.com/material-components/material-components-android",
        ),
        OssLicense(
            name = "Coil",
            copyright = "Coil Contributors",
            license = "Apache License 2.0",
            url = "https://github.com/coil-kt/coil",
        ),
        OssLicense(
            name = "Accompanist Drawable Painter",
            copyright = "Google LLC",
            license = "Apache License 2.0",
            url = "https://github.com/google/accompanist",
        ),
        OssLicense(
            name = "AndroidSVG",
            copyright = "Paul LeBeau",
            license = "Apache License 2.0",
            url = "https://github.com/BigBadaboom/androidsvg",
        ),
        OssLicense(
            name = "OkHttp",
            copyright = "Square, Inc.",
            license = "Apache License 2.0",
            url = "https://square.github.io/okhttp/",
        ),
        OssLicense(
            name = "Okio",
            copyright = "Square, Inc.",
            license = "Apache License 2.0",
            url = "https://square.github.io/okio/",
        ),
        OssLicense(
            name = "ZXing Core",
            copyright = "ZXing Authors",
            license = "Apache License 2.0",
            url = "https://github.com/zxing/zxing",
        ),
        OssLicense(
            name = "Accompanist Permissions",
            copyright = "Google LLC",
            license = "Apache License 2.0",
            url = "https://github.com/google/accompanist",
            appOnly = true,
        ),
        OssLicense(
            name = "ZXing Android Embedded",
            copyright = "JourneyApps",
            license = "Apache License 2.0",
            url = "https://github.com/journeyapps/zxing-android-embedded",
            appOnly = true,
        ),
    )

    /** The libraries actually shipped by the running build. */
    fun ossLicenses(): List<OssLicense> =
        OSS_LICENSES.filter { !it.appOnly || !FlavorConfig.isEInk() }
}
