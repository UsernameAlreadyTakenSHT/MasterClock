package com.masterclock.app.logic

// Single source of truth for the version footer shown in Settings (More or Modes page,
// depending on flavor). Update BUILD_DATE and append to CHANGELOG on every release.
object AppInfo {
    const val BUILD_DATE = "2026-11-08"

    /**
     * One release's notes. [notes] are every build's, unless [perFlavor] gives a build its own: the
     * changes are mostly in core and reach all five, but Berserk is not in Mini, nor the board in
     * Standard, and every build read every note. An empty list is a release that changed nothing in
     * that build: it is still listed, so the version on the screen can be found, with [SHARED_ONLY].
     */
    data class ChangelogEntry(
        val version: String,
        val date: String,
        val notes: List<Note>,
        val perFlavor: Map<AppFlavor, List<String>> = emptyMap(),
    ) {
        fun notesFor(flavor: AppFlavor): List<String> = perFlavor[flavor] ?: notes.filter { flavor in it.flavors }.map { it.text }
    }

    /** One line of a release's notes, and the builds it is about: every build unless it says otherwise. */
    data class Note(val text: String, val flavors: Set<AppFlavor> = ALL)

    /** One release as a build's changelog shows it. */
    data class ChangelogView(val version: String, val date: String, val notes: List<String>)

    /** What a build's changelog says for a release with nothing of its own in it. */
    const val SHARED_ONLY = "Shared update, with nothing specific to this build."

    /** The changelog as [flavor] has it: its own notes, and [SHARED_ONLY] for a release that left it untouched. */
    fun changelogFor(flavor: AppFlavor = FlavorConfig.currentFlavor): List<ChangelogView> =
        CHANGELOG.map { entry -> ChangelogView(entry.version, entry.date, entry.notesFor(flavor).ifEmpty { listOf(SHARED_ONLY) }) }

    // Who a note is about, by what each build has: every mode, presets, the arbiter, Omni and Last
    // Games are Complete, Standard and Lite's, since Mini and E-Ink keep three modes; the Behaviour and
    // Display tabs are Complete and Standard's; the notebook, boards, QR codes, the full backup, rules
    // and game logs Complete's -- import and export too, which E-Ink never had. A note names a build
    // when it was about one, and says what that build had then: statistics, before 0.8.32, were in
    // Standard and Lite as well, and every build showed every credit before 0.8.30.
    private val ALL = AppFlavor.entries.toSet()
    private val PHONE = setOf(AppFlavor.COMPLETE, AppFlavor.STANDARD, AppFlavor.LITE, AppFlavor.MINI)
    private val FULL_MODES = setOf(AppFlavor.COMPLETE, AppFlavor.STANDARD, AppFlavor.LITE)
    private val BEHAVIOUR_TAB = setOf(AppFlavor.COMPLETE, AppFlavor.STANDARD)
    private val COMPLETE_ONLY = setOf(AppFlavor.COMPLETE)
    private val E_INK_ONLY = setOf(AppFlavor.E_INK)
    /** A note that was wrong: 0.8.29 said the E-Ink build's import and export were fixed, and it has neither. */
    private val NOWHERE = emptySet<AppFlavor>()

    val CHANGELOG = listOf(
        ChangelogEntry(
            version = "0.8.38",
            date = "2026-11-08",
            notes = listOf(
                Note("Berserk, in Behavior: before their first move a player gives up half their time and their increment, as on Lichess."),
                Note("The arbiter's corrections act on the clock that needs them, and a penalty past zero runs on into byoyomi or the next period."),
                Note("A game left survives the app being closed. Settings and Presets pause a running game, and a time control edited mid-game asks first."),
                Note("The clock is silent until asked, TalkBack reads a running clock by steps, and the digits grow on tablets and foldables."),
                Note("Many fixes across the clocks at zero, Omni, the notebook and the board connections. The board is still untested with a real one."),
            ),
            perFlavor = mapOf(
                AppFlavor.STANDARD to listOf(
                    "Berserk, in Behavior: before their first move a player gives up half their time and their increment, as on Lichess.",
                    "The arbiter's corrections act on the clock that needs them, and a penalty past zero runs on into byoyomi or the next period.",
                    "A game left survives the app being closed. Settings and Presets pause a running game, and a time control edited mid-game asks first.",
                    "The clock is silent until asked, TalkBack reads a running clock by steps, and the digits grow on tablets and foldables.",
                    "Many fixes across the clocks at zero and Omni.",
                ),
                AppFlavor.LITE to listOf(
                    "The arbiter's corrections act on the clock that needs them, and a penalty past zero runs on into byoyomi or the next period.",
                    "A game left survives the app being closed. Settings and Presets pause a running game, and a time control edited mid-game asks first.",
                    "The clock is silent until asked, TalkBack reads a running clock by steps, and the digits grow on tablets and foldables.",
                    "Many fixes across the clocks at zero and Omni.",
                ),
                AppFlavor.MINI to listOf(
                    "The clock is silent until asked: no beep sounds with the sound off.",
                    "TalkBack reads a running clock by steps, and the digits grow on tablets and foldables.",
                    "Fixes across the clocks at zero and the time fields.",
                ),
                AppFlavor.E_INK to listOf(
                    "Settings pause a running game, and a time control edited mid-game asks first.",
                    "The clock is silent until asked: no beep sounds with the sound off.",
                    "Fixes across the clocks at zero.",
                ),
            ),
        ),
        ChangelogEntry(
            version = "0.8.37",
            date = "2026-11-01",
            notes = listOf(
                Note("Fast Move: Accelerate is one clock for the whole game, and Transfer gives the move time plus the time the opponent just spent.", FULL_MODES),
                Note("FIDE periods: the arbiter can correct a player's move count, and a count typed per period is understood.", FULL_MODES),
                Note("Backups hold any history, and restore it in full on a new phone. To restore a large one on another phone, update the app there first.", COMPLETE_ONLY),
                Note("Picking a preset during a game asks first. Phases, Omni, the shared modes and the notebook had many fixes.", FULL_MODES),
                Note("Sharing the app offers an APK or a ZIP. Every language was checked and corrected."),
            ),
        ),
        ChangelogEntry(
            version = "0.8.36",
            date = "2026-10-31",
            notes = listOf(
                Note("E-Ink: the clock's digits fit the screen whatever the font size, and a turn switches on touch.", E_INK_ONLY),
                Note("E-Ink: screens change at once, with no grey fade, and reversed colours start black with no white flash.", E_INK_ONLY),
                Note("E-Ink: easier with TalkBack, the keyboard and a finger. The other builds have no changes.", E_INK_ONLY),
            ),
        ),
        ChangelogEntry(
            version = "0.8.35",
            date = "2026-10-30",
            notes = listOf(
                Note("A full review of the app fixed close to a hundred issues, across every mode, the game history, presets, imports and the notebook.", FULL_MODES),
                Note("Play charges the first move from the moment it is pressed. It was charged the time since the phone was switched on."),
                Note("A finished game stops ticking, and resuming a saved game or resetting the settings no longer loses your notes.", FULL_MODES),
                Note("The arbiter's adjustments reach the clock on screen, and can give a flagged player time back.", FULL_MODES),
                Note("Back pressed twice on the clock leaves the app."),
                Note("Texts that were in English in every language are translated. Electronic boards are handled more reliably."),
                Note("The game history is converted once on the first launch; nothing you can see is lost.", FULL_MODES),
            ),
        ),
        ChangelogEntry(
            version = "0.8.34",
            date = "2026-10-17",
            notes = listOf(
                Note("A Phases game that runs through all its phases reads FINISHED, not FLAGGED, unless its last phase is set to flag at its end.", FULL_MODES),
                Note("Random and Hidden times are never drawn below the minimum you set. Rounded time could go up to ten seconds under it.", FULL_MODES),
                Note("TalkBack no longer says \"your turn\" once the game is over."),
            ),
        ),
        ChangelogEntry(
            version = "0.8.33",
            date = "2026-10-16",
            notes = listOf(
                Note("Pauses no longer count as thinking time. In Bronstein a pause was even paid back, and histories and statistics counted pauses as moves.", FULL_MODES),
                Note("A move is charged exactly up to the press. The time since the clock's last refresh was charged to no one, about three seconds a player over sixty moves."),
                Note("Hidden mode no longer gives its time away through beeps, vibration, the voice, the low-time flash or TalkBack.", FULL_MODES),
                Note("A game that plays on after a flag can be resumed after a pause. Negative time works in byoyomi, FIDE periods, Hidden, Global and Overtime.", FULL_MODES),
                Note("Phases, Gong, FIDE periods, Move Counts, the hourglass and Omni fixed, with their sounds and voice lines.", FULL_MODES),
                Note("Voice announcements speak the device's language when a voice for it is installed, and are heard every time a threshold is crossed.", BEHAVIOUR_TAB),
                Note("QR codes carry accented and non-Latin text intact. Pressing Play then Reset no longer adds an empty game.", FULL_MODES),
                Note("E-Ink: built with Mudita Mindful Design, Mudita's own interface library, which is credited in Licenses.", E_INK_ONLY),
            ),
        ),
        ChangelogEntry(
            version = "0.8.32",
            date = "2026-10-16",
            notes = listOf(
                Note("A game that did not end with Reset was never recorded: closing the app after a flag, a crash, changing the time control mid-game or a re-roll all lost it. Games are now written to the history as they are played.", FULL_MODES),
                Note("Resuming a saved clock lost every move made before it. It now continues the same game.", FULL_MODES),
                Note("Importing a settings file or a QR code emptied your notebook. An import now only ever adds notes.", COMPLETE_ONLY),
                Note("The full backup now really contains your photos, recordings and videos, as well as your presets and Omni setup.", COMPLETE_ONLY),
                Note("Just after launch the screens were live before your settings had loaded, and saving a preset then could erase every other one. Nothing can be tapped until loading is done."),
                Note("The scoreboard is kept when the app closes, and a launcher shortcut no longer re-applies itself when the screen is rebuilt.", FULL_MODES),
                Note("Importing someone else's settings no longer changes how much of your history is kept, and a day-long retention no longer deletes the game just saved.", COMPLETE_ONLY),
                Note("The notebook says it is full instead of deleting its 501st note at the next launch, and a Random range can no longer invert.", FULL_MODES),
                Note("PGN export produces files chess software accepts, and importing a file asks first.", COMPLETE_ONLY),
                Note("Statistics are in the Complete build only. Mini and the E-Ink build no longer record games they have no screen to show."),
            ),
        ),
        ChangelogEntry(
            version = "0.8.31",
            date = "2026-09-10",
            notes = listOf(
                Note("Save & Cap shows its ceiling rather than its bank. The bank is the clock minus one move, so a two-minute cap with thirty-second moves settled at 1:30 — a number nobody typed, under a clock that was stopping at 2:00.", FULL_MODES),
                Note("The E-Ink build credits the design its interface is modelled on: MMD, the design language Mudita's own software uses.", E_INK_ONLY),
            ),
        ),
        ChangelogEntry(
            version = "0.8.30",
            date = "2026-09-10",
            notes = listOf(
                Note("Connecting a Bluetooth board has never worked in a released build. The app asked for the connection through a part of Android that no phone has yet, so it failed instantly on every device.", COMPLETE_ONLY),
                Note("Importing the wrong file destroyed your notebook. Any JSON file at all, and any .zip holding no backup, was accepted as a settings export and reported as a success — and your notes and drawings live inside your settings.", COMPLETE_ONLY),
                Note("Last release's fix for the notebook shredder only protected clean installs: settings were checked on the way in and never on the way out, so an install that had already taken a bad file kept it. What comes out of storage is now checked too.", COMPLETE_ONLY),
                Note("Your game history could be deleted at the end of every game, from a stored history limit of zero that was refused on read and obeyed on trim.", FULL_MODES),
                Note("The QR scanner left the camera running after you left it, and a code entering the frame afterwards still asked to apply itself.", COMPLETE_ONLY),
                Note("Resuming a saved clock gave you a clock that looked like it was running and counted nothing, and the first press charged the mover for the time since the phone was switched on.", FULL_MODES),
                Note("US Delay gave the delay back in full on every pause and play — and with pause-in-the-background on, on every screen lock.", FULL_MODES),
                Note("A FIDE period with a delay gave no delay on the first move of each player.", FULL_MODES),
                Note("Save & Cap counted its bank twice from the second move on, and its ceiling now applies to the clock rather than to the bank alone: thirty-second moves with a two-minute cap stop at two minutes, not two and a half.", FULL_MODES),
                Note("Turning Bluetooth off with a board connected crashed the app, and so did opening it on a device with no Bluetooth hardware at all."),
                Note("The mode guide described Overtime backwards: it draws on the reserve when you go past your move time, it does not add unused time to it.", COMPLETE_ONLY),
                Note("The four builds with no board screen no longer reach for Bluetooth and USB hardware at launch, and credits now list only what your build actually contains."),
            ),
        ),
        ChangelogEntry(
            version = "0.8.29",
            date = "2026-09-02",
            notes = listOf(
                Note("A security release. Nothing here is reachable without importing a file, scanning a code or restoring an archive somebody else gave you — but all of it survives a restart once it is in."),
                Note("An imported note could point the notebook's shredder at your own settings file, so deleting that note destroyed every setting, preset and note you had, unrecoverably.", COMPLETE_ONLY),
                Note("A crafted import could make the app crash on every launch afterwards — a board note sized wrong, an empty phase list, or a game log too large for the database — until app data was cleared.", COMPLETE_ONLY),
                Note("An imported file could silently delete your entire game history by setting the history limit to zero.", COMPLETE_ONLY),
                Note("A scanned QR code now shows what it will do and waits for you, instead of replacing your settings the moment the camera sees it.", COMPLETE_ONLY),
                Note("The Complete build no longer asks for a network permission. It arrived through a camera library and this app makes no network call at all.", COMPLETE_ONLY),
                Note("The E-Ink build's import and export were a release behind: they ran on the drawing thread and reported nothing either way.", NOWHERE),
                Note("Sharing your settings writes to a fresh location each time, and a failed backup export no longer leaves the archive in the cache.", COMPLETE_ONLY),
            ),
        ),
        ChangelogEntry(
            version = "0.8.28",
            date = "2026-08-31",
            notes = listOf(
                Note("A voice note recorded for the full minute was always lost, and the microphone kept recording after the screen said it had stopped. The one-minute limit only changed the label.", COMPLETE_ONLY),
                Note("Leaving a voice note while it was still recording threw the recording away. It is kept now.", COMPLETE_ONLY),
                Note("Importing a JSON file said nothing at all, whether it worked or not. Both outcomes are reported now.", COMPLETE_ONLY),
                Note("The precision trainer measured with a clock that can be reset mid-attempt, and kept the reading from its last poll rather than from your tap — on the one screen where the measurement is the whole point.", COMPLETE_ONLY),
                Note("Retaking a video kept showing the previous one.", COMPLETE_ONLY),
                Note("The clock no longer stops to serialise the notebook every fifteen seconds, and the game history no longer loads on the drawing thread — nor without limit when history is set to unlimited."),
                Note("Typing in a note no longer rewrites the entire notebook on every keystroke. Edits are saved after a pause, and immediately if you leave.", COMPLETE_ONLY),
                Note("A debug build no longer replaces an installed release build. This only affects builds made from source."),
            ),
        ),
        ChangelogEntry(
            version = "0.8.27",
            date = "2026-08-30",
            notes = listOf(
                Note("A phone correcting its own clock could hand a player time or take it away, and this was true of every release so far. Timing came from wall-clock time, so an NTP sync or a manual change landed straight on whoever was on move. It now uses a clock that cannot be set by anyone."),
                Note("The clock no longer redraws a hundred times a second. The tick follows the display — fast only while hundredths are showing — which costs less battery over a long game. Accuracy is unchanged."),
                Note("Link board: an error no longer hides the scan button, so a scan that failed because Bluetooth was off can be retried once it is on.", COMPLETE_ONLY),
                Note("Link board: a board going out of range used to keep a Bluetooth connection slot for good, until the phone was restarted.", COMPLETE_ONLY),
                Note("Link board: a move read from the board is dropped when the board goes away, instead of being recorded against a later game.", COMPLETE_ONLY),
                Note("Link board: reconnecting no longer leaks the previous connection, acknowledgements are no longer silently dropped, and a Certabo now says it is waiting for the pieces to be set up.", COMPLETE_ONLY),
                Note("The credit for the draughts pieces pointed at Wikimedia's chess piece category instead of the draughts one."),
            ),
        ),
        ChangelogEntry(
            version = "0.8.26",
            date = "2026-08-29",
            notes = listOf(
                Note("Link board works now — but nothing in it has ever met a real board. It is written and tested against frames built to each protocol's description only, because there is no device here to test with. Treat it as an experiment, and please report what happens either way.", COMPLETE_ONLY),
                Note("Five protocols are understood: the open BLE board protocol, Chessnut, DGT, Millennium ChessLink and Certabo.", COMPLETE_ONLY),
                Note("Three ways to connect: Bluetooth LE, USB cable, and Bluetooth Classic for the older DGT e-Boards.", COMPLETE_ONLY),
                Note("Moves read from a board are recorded with the game and reach the PGN export. \"Auto switch turn on move\" presses the clock for you; with it off, the board still names the move your own press records.", COMPLETE_ONLY),
                Note("Draughts is covered, ten-by-ten included, on boards speaking the open protocol.", COMPLETE_ONLY),
                Note("Fixed: the Link board screen used to connect and then stay silent forever, because it never subscribed to anything.", COMPLETE_ONLY),
            ),
        ),
        ChangelogEntry(
            version = "0.8.25",
            date = "2026-08-29",
            notes = listOf(
                Note("Board notes can hold a draughts position: international draughts on 10x10, or 8x8 for the Russian, Brazilian and English games. Chess is still what a new note opens as.", COMPLETE_ONLY),
                Note("The board note editor has an undo button, which takes back clearing the board too.", COMPLETE_ONLY),
                Note("Pieces can be dragged: out of the tray onto a square, between squares, and off the board to remove one.", COMPLETE_ONLY),
                Note("A single piece can be removed again. Once a piece was picked it stayed picked, so wiping the whole board was one tap away while removing one piece was not.", COMPLETE_ONLY),
                Note("Shogi is no longer offered when choosing a game, since no electronic shogi board exists. Games already recorded as shogi still open and still export.", COMPLETE_ONLY),
                Note("The Licenses tab now lists what each build actually ships."),
            ),
        ),
        ChangelogEntry(
            version = "0.8.24",
            date = "2026-08-28",
            notes = listOf(
                Note("MasterClock Light is now MasterClock Lite. If you use it, this update will not reach you: the rename changed the app's identity, so Android treats it as a different app. Install the new one from the release page and remove the old one. The other builds update normally.", setOf(AppFlavor.LITE)),
                Note("The Complete build's icon no longer carries a FULL badge.", COMPLETE_ONLY),
                Note("Releases now include a SHA256SUMS.txt so you can check what you downloaded, and release tags are signed from this version on."),
                Note("Restoring a backup: an oversized archive is refused as it arrives instead of being written to the cache in full first.", COMPLETE_ONLY),
                Note("The Lato typeface used by the E-Ink build is credited in Credits."),
            ),
        ),
        ChangelogEntry(
            version = "0.8.23",
            date = "2026-08-27",
            notes = listOf(
                Note("A shared settings file could make your game history unopenable, and keep it that way after a restart. Imported games are told apart by their own identifier now, so it cannot happen.", COMPLETE_ONLY),
                Note("The same weakness in the scoreboard is closed too.", COMPLETE_ONLY),
                Note("E-Ink: the Close button is back in the version dialog on small screens; the changelog was taking all the room.", E_INK_ONLY),
                Note("Time Parameters no longer appears with nothing under it on Chronos Count Up and Move Counts Count Up, and the spacing above it is even across modes.", FULL_MODES),
                Note("The mode options for Hidden, Random, Phases, Gong and Chronos have rounded corners like every other option.", FULL_MODES),
            ),
        ),
        ChangelogEntry(
            version = "0.8.22",
            date = "2026-08-26",
            notes = listOf(
                Note("The app is now available in German, Italian, Portuguese and Dutch as well, seven languages besides English."),
                Note("Brazilian Portuguese follows the European translation and overrides only the words where the two differ."),
                Note("None of the new translations has been checked by a native speaker yet; corrections are welcome."),
                Note("E-Ink: the settings page scrolls as one, so the time fields can no longer be pushed off a small screen by the options below them.", E_INK_ONLY),
                Note("E-Ink: settings and the changelog now have a scrollbar, with chevrons to page up and down.", E_INK_ONLY),
                Note("Changes, Credits and Licenses each open at their own first line instead of sharing one scroll position."),
                Note("The launcher icon is smaller inside its mask, so it no longer crowds the edge next to other apps."),
            ),
        ),
        ChangelogEntry(
            version = "0.8.21",
            date = "2026-08-22",
            notes = listOf(
                Note("The app is now available in French and Spanish, as well as English."),
                Note("On Android 13 and later you can pick the language for MasterClock alone, under Settings, Apps, MasterClock, Language. On older versions it follows the phone's language."),
                Note("The Spanish has not been checked by a native speaker yet; corrections are welcome."),
                Note("Restoring a backup: an archive that is too large is now refused with a message instead of closing the app.", COMPLETE_ONLY),
            ),
        ),
        ChangelogEntry(
            version = "0.8.20",
            date = "2026-08-17",
            notes = listOf(
                Note("The clock now writes 09:56 where it wrote 9:56. Display settings let you choose 01:09:08, 1:09:08 or 1:9:8 instead.", BEHAVIOUR_TAB),
                Note("E-Ink, Light and Mini keep the two-digit reading they have always had.", setOf(AppFlavor.LITE, AppFlavor.MINI, AppFlavor.E_INK)),
            ),
        ),
        ChangelogEntry(
            version = "0.8.19",
            date = "2026-08-09",
            notes = listOf(
                Note("The web addresses in Credits and Licenses now open when you tap them, instead of just looking like links."),
            ),
        ),
        ChangelogEntry(
            version = "0.8.18",
            date = "2026-08-08",
            notes = listOf(
                Note("The sounds work again. The bundled beep, gong, end-of-time and switch files were corrupt and silently played nothing; they have been replaced."),
            ),
        ),
        ChangelogEntry(
            version = "0.8.17",
            date = "2026-08-07",
            notes = listOf(
                Note("E-Ink: the row of buttons no longer shifts when you start the clock.", E_INK_ONLY),
                Note("Light, Mini and E-Ink gain the Fullscreen option, which until now only Complete and Standard could switch on.", setOf(AppFlavor.LITE, AppFlavor.MINI, AppFlavor.E_INK)),
            ),
        ),
        ChangelogEntry(
            version = "0.8.16",
            date = "2026-08-03",
            notes = listOf(
                Note("Standard, Light and Mini shrink again: 4.0 MB down to 2.8 MB to download, and roughly a third less once installed. They were still carrying the code for tools only Complete can open.", setOf(AppFlavor.STANDARD, AppFlavor.LITE, AppFlavor.MINI)),
                Note("Complete is unchanged and keeps every tool, rulebook and setting.", COMPLETE_ONLY),
            ),
        ),
        ChangelogEntry(
            version = "0.8.15",
            date = "2026-08-03",
            notes = listOf(
                Note("Standard, Light and Mini are about three times smaller: 13.1 MB down to 4.0 MB. They were carrying the ten bundled rulebooks, which only Complete can open.", setOf(AppFlavor.STANDARD, AppFlavor.LITE, AppFlavor.MINI)),
                Note("The game log's move list is drawn as you scroll instead of all at once, so a long game opens without the wait.", COMPLETE_ONLY),
            ),
        ),
        ChangelogEntry(
            version = "0.8.14",
            date = "2026-08-02",
            notes = listOf(
                Note("Standard, Light and Mini no longer ask for the camera, microphone, Bluetooth or location permissions. They never had a feature that could use them; only Complete does.", setOf(AppFlavor.STANDARD, AppFlavor.LITE, AppFlavor.MINI)),
                Note("Importing a settings or backup file is now bounded, so an oversized or malformed one can no longer freeze the app instead of being rejected.", COMPLETE_ONLY),
                Note("A game log could show the wrong time against a move when the recording was incomplete.", COMPLETE_ONLY),
            ),
        ),
        ChangelogEntry(
            version = "0.8.13",
            date = "2026-08-02",
            notes = listOf(
                Note("The clock now works with a screen reader. The player areas were invisible to TalkBack, so the turn could never be passed; they are labelled buttons, and flags, turns and pauses are spoken."),
                Note("Statistics: average and slowest move, time under pressure, and a time-per-move chart for each recorded game.", FULL_MODES),
                Note("Long-press the app icon to start from a recent game or a saved preset.", FULL_MODES),
                Note("Rename or delete a saved preset by long-pressing it, instead of a button crowding the tile.", FULL_MODES),
                Note("The clock no longer gets cut off when the system font size is turned up."),
                Note("The chess, shogi and both draughts rulebooks opened blank. Replaced with the real documents.", COMPLETE_ONLY),
                Note("Groundwork for translation: the interface now lives in resource files, and French, Spanish, Portuguese, Italian, German and Dutch are ready to be filled in. On Android 13 and later the language can be picked per app."),
                Note("The Fischer mode was misspelled internally. Correcting it resets your settings and clears the moves from older Fischer games."),
            ),
        ),
        ChangelogEntry(
            version = "0.8.12",
            date = "2026-08-01",
            notes = listOf(
                Note("Custom presets: save your time control under a name, then load, rename or delete it.", FULL_MODES),
                Note("Random/Hidden: a dice button draws a new time without switching modes away and back.", FULL_MODES),
                Note("Presets no longer reset your colours, sounds and other preferences — they change the time control only.", FULL_MODES),
            ),
        ),
        ChangelogEntry(
            version = "0.8.11",
            date = "2026-08-01",
            notes = listOf(
                Note("The bundled rulebooks now open offline — six buttons on the rules screen used to do nothing at all.", COMPLETE_ONLY),
                Note("Every rules document is credited to its author or publisher, in the app and in the README."),
                Note("New Licenses tab listing the open-source libraries the app ships."),
            ),
        ),
        ChangelogEntry(
            version = "0.8.10",
            date = "2026-07-25",
            notes = listOf(
                Note("Fixed no sound at all on some phones: the volume rocker now adjusts the media stream in the app."),
                Note("Omni: pick each player's color in the wizard.", FULL_MODES),
                Note("Omni: Random turn order now has real sub-options (shuffle per round or draw per turn, no repeats, balanced draw).", FULL_MODES),
                Note("Omni: fixed the phase clock freezing at 00:00, time banking counted twice, endless looping rounds, and Close session not ending the session.", FULL_MODES),
            ),
        ),
        ChangelogEntry(
            version = "0.8.9",
            date = "2026-07-25",
            notes = listOf(
                Note("Light/Mini's System behavior toggles reordered to match paper's, and the card now has rounded corners like Complete/Standard.", setOf(AppFlavor.LITE, AppFlavor.MINI)),
            ),
        ),
        ChangelogEntry(
            version = "0.8.8",
            date = "2026-07-25",
            notes = listOf(
                Note("Light/Mini/paper's \"Sound\" toggle now also controls the time's-up/flag sound.", setOf(AppFlavor.LITE, AppFlavor.MINI, AppFlavor.E_INK)),
                Note("Omni-Timer is now labeled \"(alpha)\" in the mode picker.", FULL_MODES),
                Note("Byoyomi's 3 sub-modes and Mini's 3 mode cards are now all on one row instead of splitting one onto its own line.", PHONE),
            ),
        ),
        ChangelogEntry(
            version = "0.8.7",
            date = "2026-07-22",
            notes = listOf(
                Note("FIDE Periods now has a real \"US Delay\" option, distinct from Sudden Death.", FULL_MODES),
                Note("Mini no longer shows a Bonus/Move Timer sub-panel with a single, unchangeable option.", setOf(AppFlavor.MINI)),
                Note("Fixed Reset reverting to a stale random roll after loading a Random/Hidden game from Last Games.", FULL_MODES),
            ),
        ),
        ChangelogEntry(
            version = "0.8.6",
            date = "2026-07-21",
            notes = listOf(
                Note("Each flavor's launcher icon now prints its own word (FULL/STD/LITE/MINI) inside the clock face.", PHONE),
                Note("ExtraLight renamed to Mini everywhere, including the package ID — reinstall needed if updating from ExtraLight.", setOf(AppFlavor.MINI)),
            ),
        ),
        ChangelogEntry(
            version = "0.8.5",
            date = "2026-07-21",
            notes = listOf(
                Note("Each flavor now shows its own name in the launcher.", PHONE),
                Note("Standard and Light now have access to Omni-Timer.", setOf(AppFlavor.STANDARD, AppFlavor.LITE)),
                Note("Fixed Move Timer (Global Shared) flagging every player out on a single move-timer expiry.", FULL_MODES),
                Note("Fixed Gong/Phases/Move Timer (Shared)/Chrono (one-for-all) games never being saved to history.", FULL_MODES),
                Note("Gong's move-phase background now matches the classic active-player green.", FULL_MODES),
                Note("Random/Hidden: Reset keeps the already-rolled time instead of re-rolling.", FULL_MODES),
                Note("ExtraLight's Bonus/Move Timer submenus restricted to Fisher/Standard only.", setOf(AppFlavor.MINI)),
            ),
        ),
        ChangelogEntry(
            version = "0.8.4",
            date = "2026-07-20",
            notes = listOf(
                Note("Light and ExtraLight now have Sound/Haptic/Keep-screen-awake toggles.", setOf(AppFlavor.LITE, AppFlavor.MINI)),
            ),
        ),
        ChangelogEntry(
            version = "0.8.3",
            date = "2026-07-20",
            notes = listOf(
                Note("App ID renamed (io.github.usernamealreadytakensht.masterclock.*) for store submission — reinstall needed if updating from an older version."),
                Note("Added a privacy policy page."),
            ),
        ),
        ChangelogEntry(
            version = "0.8.2",
            date = "2026-07-20",
            notes = listOf(
                Note("Omni: added optional auto-cutoff per level (Phase/Turn/Round/Game/Session).", COMPLETE_ONLY),
                Note("Fixed sound and voice feedback missing on several timer modes.", FULL_MODES),
                Note("Fixed FIDE Periods delay bonus not applying on the US preset.", FULL_MODES),
                Note("Fixed Phases mode getting stuck instead of waiting for confirmation.", FULL_MODES),
            ),
        ),
        ChangelogEntry(
            version = "0.8.1",
            date = "2026-07-20",
            notes = listOf(
                Note("Fixed dark mode not applying to the e-ink navigation bar.", E_INK_ONLY),
                Note("Refined e-ink design components to match Mudita guidelines.", E_INK_ONLY),
                Note("Changelog & credits popup now has separate tabs."),
            ),
        ),
        ChangelogEntry(
            version = "0.8.0",
            date = "2026-07-20",
            notes = listOf(
                Note("Initial release."),
            ),
        ),
    )

    /**
     * An asset or document shipped in the app, and whoever it belongs to.
     *
     * [completeOnly] and [eInkOnly] do for assets what [OssLicense.completeOnly] does for libraries.
     * That mechanism existed for code and for nothing else, so every build rendered this whole list
     * and claimed credit for files it does not contain -- Mini crediting the Cburnett chess pieces,
     * which live in `app/src/complete/assets/pieces/`, and paper crediting them too while itself
     * bundling the one font nothing else does.
     */
    data class CreditEntry(
        val title: String,
        val detail: String,
        /** Shown under the detail line when set; the README already carried these links. */
        val url: String? = null,
        val completeOnly: Boolean = false,
        val eInkOnly: Boolean = false,
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
            completeOnly = true,
        ),
        CreditEntry(
            title = "Draughts stones",
            detail = "Antonsusi, Wikimedia Commons — public domain (too simple a shape to be copyrighted)",
            url = "https://commons.wikimedia.org/wiki/Category:SVG_Draughts_pieces",
            completeOnly = true,
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
            title = "E-Ink interface",
            detail = "Built with Mudita Mindful Design (MMD), Mudita's own component library for its E Ink devices.",
            url = "https://github.com/mudita/MMD",
            // Only the E-Ink build links the library; its licence is listed with the others.
            eInkOnly = true,
        ),
        CreditEntry(
            title = "Font — Lato",
            detail = "Łukasz Dziedzic — SIL Open Font License 1.1. Bundled by the E-Ink build, through MMD.",
            url = "https://www.latofonts.com/",
            // The faces come in MMD's own res/font now, and only paper links MMD, so no other
            // build carries any of them.
            eInkOnly = true,
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
        val eInkOnly: Boolean = false,
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
        // Built on Material 3, whose copyright its sources keep beside Mudita's.
        OssLicense(
            name = "Mudita Mindful Design (MMD)",
            copyright = "Mudita Sp. z o.o., The Android Open Source Project",
            license = "Apache License 2.0",
            url = "https://github.com/mudita/MMD",
            eInkOnly = true,
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
        // No OkHttp entry. It arrived with Coil 2, which bundled its network fetcher; Coil 3 moved
        // networking into separate modules this app does not take, and R8's mapping for the Complete
        // build has had no okhttp3 class since the move. An HTTP client was never something an app
        // that makes no network call should have been shipping. Okio stays: Coil 3 still reads files
        // through it.
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
        OSS_LICENSES.filter { lib ->
            when {
                lib.completeOnly -> FlavorConfig.currentFlavor == AppFlavor.COMPLETE
                lib.eInkOnly -> FlavorConfig.isEInk()
                else -> true
            }
        }

    /** [CREDITS], less anything this build does not ship. */
    fun credits(): List<CreditEntry> = CREDITS.filter { entry ->
        when {
            entry.completeOnly -> FlavorConfig.currentFlavor == AppFlavor.COMPLETE
            entry.eInkOnly -> FlavorConfig.isEInk()
            else -> true
        }
    }

    /**
     * [RULES_CREDITS], or nothing at all outside the Complete build.
     *
     * The ten rulebooks live in `core`'s `res/raw` and so are compiled into every consumer, but the
     * Rules screen that opens them is `app/src/complete` only -- the reduced source set has a stub
     * and paper has no such screen -- so the resource shrinker strips every PDF from the other four
     * APKs. Their `resources.txt` says `raw:rules_chess... is not reachable`. Crediting a rulebook
     * that a build does not contain is the same mistake as crediting a library it does not link.
     */
    fun rulesCredits(): List<CreditEntry> =
        if (FlavorConfig.currentFlavor == AppFlavor.COMPLETE) RULES_CREDITS else emptyList()
}
