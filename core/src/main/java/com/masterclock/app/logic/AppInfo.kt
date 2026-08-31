package com.masterclock.app.logic

// Single source of truth for the version footer shown in Settings (More or Modes page,
// depending on flavor). Update BUILD_DATE and append to CHANGELOG on every release.
object AppInfo {
    const val BUILD_DATE = "2026-08-31"

    data class ChangelogEntry(
        val version: String,
        val date: String,
        val notes: List<String>,
    )

    val CHANGELOG = listOf(
        ChangelogEntry(
            version = "0.8.28",
            date = "2026-08-31",
            notes = listOf(
                "A voice note recorded for the full minute was always lost, and the microphone kept recording after the screen said it had stopped. The one-minute limit only changed the label.",
                "Leaving a voice note while it was still recording threw the recording away. It is kept now.",
                "Importing a JSON file said nothing at all, whether it worked or not. Both outcomes are reported now.",
                "The precision trainer measured with a clock that can be reset mid-attempt, and kept the reading from its last poll rather than from your tap — on the one screen where the measurement is the whole point.",
                "Retaking a video kept showing the previous one.",
                "The clock no longer stops to serialise the notebook every fifteen seconds, and the game history no longer loads on the drawing thread — nor without limit when history is set to unlimited.",
                "Typing in a note no longer rewrites the entire notebook on every keystroke. Edits are saved after a pause, and immediately if you leave.",
                "A debug build no longer replaces an installed release build. This only affects builds made from source.",
            ),
        ),
        ChangelogEntry(
            version = "0.8.27",
            date = "2026-08-30",
            notes = listOf(
                "A phone correcting its own clock could hand a player time or take it away, and this was true of every release so far. Timing came from wall-clock time, so an NTP sync or a manual change landed straight on whoever was on move. It now uses a clock that cannot be set by anyone.",
                "The clock no longer redraws a hundred times a second. The tick follows the display — fast only while hundredths are showing — which costs less battery over a long game. Accuracy is unchanged.",
                "Link board: an error no longer hides the scan button, so a scan that failed because Bluetooth was off can be retried once it is on.",
                "Link board: a board going out of range used to keep a Bluetooth connection slot for good, until the phone was restarted.",
                "Link board: a move read from the board is dropped when the board goes away, instead of being recorded against a later game.",
                "Link board: reconnecting no longer leaks the previous connection, acknowledgements are no longer silently dropped, and a Certabo now says it is waiting for the pieces to be set up.",
                "The credit for the draughts pieces pointed at Wikimedia's chess piece category instead of the draughts one.",
            ),
        ),
        ChangelogEntry(
            version = "0.8.26",
            date = "2026-08-29",
            notes = listOf(
                "Link board works now — but nothing in it has ever met a real board. It is written and tested against recorded frames only, because there is no device here to test with. Treat it as an experiment, and please report what happens either way.",
                "Five protocols are understood: the open BLE board protocol, Chessnut, DGT, Millennium ChessLink and Certabo.",
                "Three ways to connect: Bluetooth LE, USB cable, and Bluetooth Classic for the older DGT e-Boards.",
                "Moves read from a board are recorded with the game and reach the PGN export. \"Auto switch turn on move\" presses the clock for you; with it off, the board still names the move your own press records.",
                "Draughts is covered, ten-by-ten included, on boards speaking the open protocol.",
                "Fixed: the Link board screen used to connect and then stay silent forever, because it never subscribed to anything.",
            ),
        ),
        ChangelogEntry(
            version = "0.8.25",
            date = "2026-08-29",
            notes = listOf(
                "Board notes can hold a draughts position: international draughts on 10x10, or 8x8 for the Russian, Brazilian and English games. Chess is still what a new note opens as.",
                "The board note editor has an undo button, which takes back clearing the board too.",
                "Pieces can be dragged: out of the tray onto a square, between squares, and off the board to remove one.",
                "A single piece can be removed again. Once a piece was picked it stayed picked, so wiping the whole board was one tap away while removing one piece was not.",
                "Shogi is no longer offered when choosing a game, since no electronic shogi board exists. Games already recorded as shogi still open and still export.",
                "The Licenses tab now lists what each build actually ships.",
            ),
        ),
        ChangelogEntry(
            version = "0.8.24",
            date = "2026-08-28",
            notes = listOf(
                "MasterClock Light is now MasterClock Lite. If you use it, this update will not reach you: the rename changed the app's identity, so Android treats it as a different app. Install the new one from the release page and remove the old one. The other builds update normally.",
                "The Complete build's icon no longer carries a FULL badge.",
                "Releases now include a SHA256SUMS.txt so you can check what you downloaded, and release tags are signed from this version on.",
                "Restoring a backup: an oversized archive is refused as it arrives instead of being written to the cache in full first.",
                "The Lato typeface used by the E-Ink build is credited in Credits.",
            ),
        ),
        ChangelogEntry(
            version = "0.8.23",
            date = "2026-08-27",
            notes = listOf(
                "A shared settings file could make your game history unopenable, and keep it that way after a restart. Imported games are told apart by their own identifier now, so it cannot happen.",
                "The same weakness in the scoreboard is closed too.",
                "E-Ink: the Close button is back in the version dialog on small screens; the changelog was taking all the room.",
                "Time Parameters no longer appears with nothing under it on Chronos Count Up and Move Counts Count Up, and the spacing above it is even across modes.",
                "The mode options for Hidden, Random, Phases, Gong and Chronos have rounded corners like every other option.",
            ),
        ),
        ChangelogEntry(
            version = "0.8.22",
            date = "2026-08-26",
            notes = listOf(
                "The app is now available in German, Italian, Portuguese and Dutch as well, seven languages besides English.",
                "Brazilian Portuguese follows the European translation and overrides only the words where the two differ.",
                "None of the new translations has been checked by a native speaker yet; corrections are welcome.",
                "E-Ink: the settings page scrolls as one, so the time fields can no longer be pushed off a small screen by the options below them.",
                "E-Ink: settings and the changelog now have a scrollbar, with chevrons to page up and down.",
                "Changes, Credits and Licenses each open at their own first line instead of sharing one scroll position.",
                "The launcher icon is smaller inside its mask, so it no longer crowds the edge next to other apps.",
            ),
        ),
        ChangelogEntry(
            version = "0.8.21",
            date = "2026-08-22",
            notes = listOf(
                "The app is now available in French and Spanish, as well as English.",
                "On Android 13 and later you can pick the language for MasterClock alone, under Settings, Apps, MasterClock, Language. On older versions it follows the phone's language.",
                "The Spanish has not been checked by a native speaker yet; corrections are welcome.",
                "Restoring a backup: an archive that is too large is now refused with a message instead of closing the app.",
            ),
        ),
        ChangelogEntry(
            version = "0.8.20",
            date = "2026-08-17",
            notes = listOf(
                "The clock now writes 09:56 where it wrote 9:56. Display settings let you choose 01:09:08, 1:09:08 or 1:9:8 instead.",
                "E-Ink, Light and Mini keep the two-digit reading they have always had.",
            ),
        ),
        ChangelogEntry(
            version = "0.8.19",
            date = "2026-08-09",
            notes = listOf(
                "The web addresses in Credits and Licenses now open when you tap them, instead of just looking like links.",
            ),
        ),
        ChangelogEntry(
            version = "0.8.18",
            date = "2026-08-08",
            notes = listOf(
                "The sounds work again. The bundled beep, gong, end-of-time and switch files were corrupt and silently played nothing; they have been replaced.",
            ),
        ),
        ChangelogEntry(
            version = "0.8.17",
            date = "2026-08-07",
            notes = listOf(
                "E-Ink: the row of buttons no longer shifts when you start the clock.",
                "Light, Mini and E-Ink gain the Fullscreen option, which until now only Complete and Standard could switch on.",
            ),
        ),
        ChangelogEntry(
            version = "0.8.16",
            date = "2026-08-03",
            notes = listOf(
                "Standard, Light and Mini shrink again: 4.0 MB down to 2.8 MB to download, and roughly a third less once installed. They were still carrying the code for tools only Complete can open.",
                "Complete is unchanged and keeps every tool, rulebook and setting.",
            ),
        ),
        ChangelogEntry(
            version = "0.8.15",
            date = "2026-08-03",
            notes = listOf(
                "Standard, Light and Mini are about three times smaller: 13.1 MB down to 4.0 MB. They were carrying the ten bundled rulebooks, which only Complete can open.",
                "The game log's move list is drawn as you scroll instead of all at once, so a long game opens without the wait.",
            ),
        ),
        ChangelogEntry(
            version = "0.8.14",
            date = "2026-08-02",
            notes = listOf(
                "Standard, Light and Mini no longer ask for the camera, microphone, Bluetooth or location permissions. They never had a feature that could use them; only Complete does.",
                "Importing a settings or backup file is now bounded, so an oversized or malformed one can no longer freeze the app instead of being rejected.",
                "A game log could show the wrong time against a move when the recording was incomplete.",
            ),
        ),
        ChangelogEntry(
            version = "0.8.13",
            date = "2026-08-02",
            notes = listOf(
                "The clock now works with a screen reader. The player areas were invisible to TalkBack, so the turn could never be passed; they are labelled buttons, and flags, turns and pauses are spoken.",
                "Statistics: average and slowest move, time under pressure, and a time-per-move chart for each recorded game.",
                "Long-press the app icon to start from a recent game or a saved preset.",
                "Rename or delete a saved preset by long-pressing it, instead of a button crowding the tile.",
                "The clock no longer gets cut off when the system font size is turned up.",
                "The chess, shogi and both draughts rulebooks opened blank. Replaced with the real documents.",
                "Groundwork for translation: the interface now lives in resource files, and French, Spanish, Portuguese, Italian, German and Dutch are ready to be filled in. On Android 13 and later the language can be picked per app.",
                "The Fischer mode was misspelled internally. Correcting it resets your settings and clears the moves from older Fischer games.",
            ),
        ),
        ChangelogEntry(
            version = "0.8.12",
            date = "2026-08-01",
            notes = listOf(
                "Custom presets: save your time control under a name, then load, rename or delete it.",
                "Random/Hidden: a dice button draws a new time without switching modes away and back.",
                "Presets no longer reset your colours, sounds and other preferences — they change the time control only.",
            ),
        ),
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
            title = "Draughts stones",
            detail = "Antonsusi, Wikimedia Commons — public domain (too simple a shape to be copyrighted)",
            url = "https://commons.wikimedia.org/wiki/Category:SVG_Draughts_pieces",
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
            title = "Font — Lato",
            detail = "Łukasz Dziedzic — SIL Open Font License 1.1. Bundled by the E-Ink build.",
            url = "https://www.latofonts.com/",
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
     * Services into an app distributed on F-Droid.
     *
     * [completeOnly] marks what only the Complete build ships, so no other build claims credit for
     * code it does not contain. Which build ships what is settled by R8, not by the dependency
     * blocks: `paper` declares Coil and `core` declares ZXing, yet neither reaches any APK but
     * Complete's, because nothing outside `src/complete` calls them. Check this list against the
     * `mapping.txt` R8 writes per variant, never against `build.gradle.kts`.
     */
    data class OssLicense(
        val name: String,
        val copyright: String,
        val license: String,
        val url: String,
        val completeOnly: Boolean = false,
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
            completeOnly = true,
        ),
        OssLicense(
            name = "Accompanist Drawable Painter",
            copyright = "Google LLC",
            license = "Apache License 2.0",
            url = "https://github.com/google/accompanist",
            completeOnly = true,
        ),
        OssLicense(
            name = "Accompanist Permissions",
            copyright = "Google LLC",
            license = "Apache License 2.0",
            url = "https://github.com/google/accompanist",
            completeOnly = true,
        ),
        OssLicense(
            name = "AndroidSVG",
            copyright = "Paul LeBeau",
            license = "Apache License 2.0",
            url = "https://github.com/BigBadaboom/androidsvg",
            completeOnly = true,
        ),
        OssLicense(
            name = "OkHttp",
            copyright = "Square, Inc.",
            license = "Apache License 2.0",
            url = "https://square.github.io/okhttp/",
            completeOnly = true,
        ),
        OssLicense(
            name = "Okio",
            copyright = "Square, Inc.",
            license = "Apache License 2.0",
            url = "https://square.github.io/okio/",
            completeOnly = true,
        ),
        OssLicense(
            name = "ZXing Core",
            copyright = "ZXing Authors",
            license = "Apache License 2.0",
            url = "https://github.com/zxing/zxing",
            completeOnly = true,
        ),
        OssLicense(
            name = "ZXing Android Embedded",
            copyright = "JourneyApps",
            license = "Apache License 2.0",
            url = "https://github.com/journeyapps/zxing-android-embedded",
            completeOnly = true,
        ),
        // Reached only through CameraX, which the QR scanner and the notebook's photo notes pull
        // in. A handful of classes each -- ListenableFuture, Dagger's lazy-init helpers and the
        // Provider interface they implement -- but shipped all the same, so credited all the same.
        OssLicense(
            name = "Guava",
            copyright = "The Guava Authors",
            license = "Apache License 2.0",
            url = "https://github.com/google/guava",
            completeOnly = true,
        ),
        OssLicense(
            name = "Dagger",
            copyright = "Google LLC",
            license = "Apache License 2.0",
            url = "https://github.com/google/dagger",
            completeOnly = true,
        ),
        OssLicense(
            name = "javax.inject (JSR-330)",
            copyright = "The JSR-330 Expert Group",
            license = "Apache License 2.0",
            url = "https://github.com/javax-inject/javax-inject",
            completeOnly = true,
        ),
    )

    /** The libraries actually shipped by the running build. */
    fun ossLicenses(): List<OssLicense> =
        OSS_LICENSES.filter { !it.completeOnly || FlavorConfig.currentFlavor == AppFlavor.COMPLETE }
}
