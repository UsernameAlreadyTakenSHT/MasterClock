## v0.8.29 — 2026-09-02

A security release. Nothing here is reachable without importing a file, scanning a code or restoring
an archive that somebody else gave you — but all of it survives a restart once it is in, which is
why it is now stopped at the door rather than cleaned up afterwards.

### Fixed
- **An imported note could aim the notebook's shredder at your settings.** A note's media path was accepted anywhere inside the app's own storage, and the app's settings file lives there too — so a crafted settings file, backup or QR code could point a note at it, and deleting that note overwrote every setting, preset and note you had with random bytes. Unrecoverably, with nothing on screen to say so. A media path now has to look like a file the notebook itself wrote.
- **A crafted import could make the app crash on every launch afterwards.** Three separate ways: a board note whose square list did not match its variant crashed on the first tap; a phase list left empty crashed the clock on the first press; and a game log large enough to exceed the database's row limit made the history fail to load at startup, where nothing caught it. In each case the poisoned settings were saved, so the crash came back on every launch until app data was cleared.
- **An imported file could silently delete your entire game history.** The history limit was the one number that reached the database unchecked, and a value of zero turned the end of every game into "delete everything".
- **A scanned QR code is no longer applied without asking.** Pointing the camera at a code was the whole of the interaction: whatever it carried replaced your settings and added its games immediately, with no preview. A code on a poster works as well as one on a friend's screen. It now shows what it will do and waits for you.
- Custom sound files are kept only if the app actually holds permission to read them, rather than only looking like a valid address.
- **The E-Ink build's import and export were a release behind.** They ran on the drawing thread and said nothing at all, whichever way they went — the phone app's fixes for both had not been carried across.

### Changed
- **The Complete build no longer asks for a network permission.** Nothing in this app declares it; it arrived through a camera library, and the three smaller builds each stripped it while Complete did not. This app makes no network call at all, and now its permissions say so.
- Sharing your settings writes to a fresh location each time. The same path was reused for every share, and a read grant lives as long as the app that received it, so an earlier recipient could read whatever the next share put there.
- A backup archive that fails to export is no longer left behind in the cache in the clear.
- A library that was declared but never used is gone from the Complete build, and its credit with it.
- Ceilings on several things a connected board controls: how much of a game it can record, how many pieces it can teach the app, and how many replies can queue up behind it.
- The backup exclusion rules were missing a required attribute and were most likely being ignored. Backup was already switched off outright, so nothing was ever exposed — but the file now says what it meant.
- The release signing job no longer runs for unprotected branches on GitLab.

## v0.8.28 — 2026-08-31

A whole release of things found by reading the app rather than by using it. Most of them had been
there a long time.

### Fixed
- **A voice note recorded for a full minute was always lost, and the microphone stayed on.** The one-minute limit only ever changed the label: the button went back to "Record", the timer stopped climbing, and the recorder went on recording, because nothing had told it to stop. The take was never attached to the note, and pressing record again started a second recorder on the same file while the first carried on holding the microphone. The ceiling now ends the recording, which is what it always claimed to do.
- **Leaving a voice note while it was still recording threw the recording away.** It was released without being stopped, and those are not the same thing: the file left behind has no index and will not play, and the note kept no reference to it. Walking away now finishes the recording properly and keeps it. A take of under a second is still discarded, because there is nothing in it, but its leftover file is deleted rather than left in place.
- **Importing a JSON file said nothing at all, whether it worked or not.** An unreadable file, a format neither parser recognised, or a perfectly good import all looked identical: the picker closed and the screen was unchanged. Both outcomes are now reported, the way the backup import beside it always has.
- **The precision trainer measured with the wrong clock, and scored the wrong instant.** It used wall-clock time, so a clock correction mid-attempt corrupted the result — on the one screen where the measurement is the whole exercise. And the result it kept was whatever its polling loop had last written, up to a poll old, on a reading it colours in ten-millisecond bands. It now counts from a clock that cannot be set, and takes its reading at the moment you tap.
- **Retaking a video kept showing the old one.** The player was pointed at the file once and never again, and a retake writes over the same file, so nothing about the note changed to tell them apart.
- **A board going out of range could cost the phone a Bluetooth connection slot even when the app had the permission taken away.** Three places skipped closing the connection when the permission was missing, but cleared the reference to it anyway, so nothing was left that could ever close it. They now try, and accept a refusal.

### Changed
- **The clock no longer stops to serialise the notebook mid-game.** The fifteen-second autosave encoded settings — every note and every drawing in them — on the same thread that draws the clock. So did loading the game history at startup, which also had no ceiling in the "unlimited" case: it loaded every game ever played, while the same code elsewhere had settled on ten thousand. Both now happen off the main thread. The JSON export and import move off it too; the backup pair beside them always had.
- **Typing in a note no longer rewrites the whole notebook on every keystroke.** Saving a note re-serialises every note and every drawing stroke, and the editors did that per character, per brush stroke, per piece placed. They now wait for a pause, and still save immediately if you leave.
- The clock's tick rate and what the display actually shows are now decided by one function instead of two conditions in two modules. They had already drifted: with hundredths enabled but shown only near the end, the clock still ticked a hundred times a second from the first move.
- A debug build no longer replaces an installed release build. Nothing changes for anyone installing from a release page; this only affects builds made from source.
- The last four strings the app still spoke English regardless of language are translated: two buttons in the voice note editor, and two error messages that used to show a raw Java exception.

## v0.8.27 — 2026-08-30

### Fixed
- **A phone correcting its own clock could hand a player time, or take it away — and this has been true of every release so far.** Both timers measured with wall-clock time, which answers "what time is it" rather than "how much time has passed". The two differ every time the phone adjusts itself: an NTP sync, a manual change, a carrier correction. Backwards, the player on move was handed however far the phone had drifted; forwards, the whole gap was deducted in a single tick, which is enough to flag somebody who had minutes in hand. Nothing warned, and nothing put it back. Timing now comes from a clock that counts from boot and cannot be set by anyone. The per-move durations written into an exported PGN are measured the same way.
- **The Link board screen could report an error and then take away the only button that could clear it.** Scanning with Bluetooth switched off said "Bluetooth is disabled. Please enable it." and then hid the scan button, so doing exactly what was asked left no way to act on it — reopening the screen was the only way out. The button is now offered whenever nothing is connected, and tapping it clears a failed attempt on all three transports first. A stale USB or serial error could otherwise sit on the status card indefinitely, hiding a Bluetooth scan that was working perfectly well underneath it.
- **A board dropping out could cost the phone a Bluetooth connection slot, permanently.** Going out of range or being switched off left the GATT client open, and Android hands out a fixed number of those; only closing one gives it back. A few disconnections and no further Bluetooth connection would succeed — not in this app, and not necessarily in others — until the phone was restarted. Nothing reported it, which is what made it the worst of the faults found.
- **A move read from the board could be recorded under a game it did not belong to.** With auto-switch off, a move waits for the player's own press to claim it; if the board went out of range in between, the notation simply sat there and was taken by the next press, which could be minutes later or in another game entirely. It is dropped now as soon as no board is connected.
- **Connecting to a second board while one was already connected leaked whatever the first was holding** — a GATT client over Bluetooth, a claimed interface over USB (leaving the device unusable until the app was killed), a socket with a thread parked in a read on it over serial. Each transport now closes what it had before opening anything new.
- **On Bluetooth, an acknowledgement could be silently dropped and the board would fall quiet.** Android permits exactly one outstanding GATT operation and discards a second without a word — no exception, no error, no callback — and the open protocol owes the board an acknowledgement after every move. Subscriptions and writes now go through one queue, so nothing competes; and an operation that never answers no longer blocks every one behind it forever.
- **Two races on the Bluetooth Classic path**, both intermittent by construction: a read loop that could decide it was already obsolete and stop before its first read, leaving a board connected and silent; and a session on its way out closing the socket of the one that had just replaced it. A departing USB read loop could likewise still be feeding frames into the next connection's board position — one board's frames decoded into another's is a wrong move recorded, not merely a wasted one.
- **A Certabo now says what it is waiting for.** It reads a chip under each piece and cannot name a piece type until it has been shown the starting position, so before that it is connected, reporting continuously, and producing nothing — on screen, indistinguishable from a board that does not work. It is also recognised over USB now: protocol matching there went by vendor and product id alone, which a board built around an off-the-shelf serial chip can never claim, so it fell through to raw capture over a cable while working over Bluetooth. Ids are still tried first and still win.
- The credit for the draughts pieces pointed at Wikimedia's chess piece category, where they are not. Anyone checking the public-domain claim would have found a page of GFDL chess pieces instead.

### Changed
- **The clock no longer redraws a hundred times a second.** It ticked every 10 ms whatever was on screen, mostly to redraw digits that had not changed — paid in battery, on a device meant to sit awake on a table for hours. The rate follows the display now: 10 ms while hundredths are showing, 100 ms otherwise, which is most of most games. The accounting is unaffected, since elapsed time is taken from the difference between two readings rather than counted up tick by tick.
- Autosave runs every fifteen seconds instead of every five. At five it was around 1,400 database writes across a two-hour game, for a recovery that almost never happens; fifteen still bounds what a crash costs to fifteen seconds of one clock.
- A Bluetooth scan now stops itself after thirty seconds. It used to run until it was stopped or the screen was left, keeping the radio working for as long as the app was open — and a scan that has found nothing in half a minute will not find it in ten.

## v0.8.26 — 2026-08-29

### Added
- **Link board now does something — but nothing here has ever met a real board.** Every part of it is written and tested against recorded frames; not one byte has been exchanged with a physical device, because there is none to test with. Treat it as an experiment, and please report what happens, whichever way it goes. It is the only way this gets better.
- Five protocols are understood: the open BLE board protocol used by Lichess-compatible and home-built boards, Chessnut, DGT, Millennium ChessLink and Certabo.
- Three ways to connect: Bluetooth LE, USB cable, and Bluetooth Classic for the older DGT e-Boards, which present themselves as a serial port rather than as a BLE device.
- Moves read from a board are recorded with the game and reach the PGN export, and "Auto switch turn on move" presses the clock for you. With it off, the board still names the move your own press records.
- Draughts is covered by the open protocol, which carries moves as text and never interprets them — including the ten-by-ten international game. No manufacturer sells an electronic draughts board any more, so a home-built one is the only route.
- A Certabo learns its pieces from the starting position, the way its own software does: the chips are stuck under the pieces by whoever owns the set, so no table can be shipped.

### Fixed
- The Link board screen previously connected and then sat in silence forever: no notification was ever subscribed to, so nothing a board said could arrive.

## v0.8.25 — 2026-08-29

### Added
- **A board note can hold a draughts position.** Chess is still what a new note opens as; beside it are international draughts on 10×10 and, for the Russian, Brazilian and English games, 8×8. Switching between the two draughts boards keeps every piece that still has a square to stand on.
- **The board note has an undo button.** It takes back clearing the whole board too, which used to be the one mistake that cost the entire position.
- **Pieces can be dragged**: out of the tray onto a square, from one square to another, and off the board altogether to remove one.

### Changed
- Shogi is no longer offered when choosing a game. Nothing is removed — a game already recorded as shogi still opens, still exports as KIF, and its rules document stays. It is withheld because no electronic shogi board is made, so the board-linking work it sits beside has nothing to offer it.
- The Licenses tab lists what each build actually ships rather than what the project depends on. Standard, Lite and Mini were crediting four libraries they do not contain, and three that are shipped were missing.

### Fixed
- **A single piece could not be removed from a board note.** Once a piece was picked from the tray it stayed picked, and an empty hand was the only thing that cleared a square — so wiping the whole board was one tap away while removing one piece was impossible. Tapping the held piece again puts it down, and so does tapping the space around the pieces.
- Standard, Lite and Mini no longer carry the chess and draughts artwork, which only the Complete build can display.

## v0.8.24 — 2026-08-28

### Added
- **Every release now ships a `SHA256SUMS.txt` beside the APKs**, so you can check that what you downloaded is what was published: `sha256sum -c SHA256SUMS.txt`.
- **Release tags are signed from this one on.** The public key is in the repository at `docs/signing-key.asc`, fingerprint `210B 5FD0 0E17 66BB 8797  3EDC A286 94BC BC91 9301`. Tags up to v0.8.23 are unsigned and stay that way — signing them now would mean recreating published tags. [docs/VERIFYING.md](docs/VERIFYING.md) explains what each check proves and what it does not.
- APKs now carry an APK Signature Scheme v3 block alongside v2. The certificate is unchanged, so updates are unaffected; what it adds is the ability to replace the signing key later, which without v3 would have been impossible.

### Changed
- **MasterClock Light is now MasterClock Lite — and if you have it installed, this update will not reach you.** The rename went all the way down to the app's identity, which Android treats as a different application: your existing copy will simply stop being offered updates, silently and for good. To carry on, install the new one from the release page and remove the old one. Nothing else does this — Complete, Standard, Mini and E-Ink update normally. Your settings and history do not transfer automatically; export a backup from the old build first if you want to keep them.
- The Complete build's icon no longer carries a FULL badge. It is the plain clock now: the badge made the main app look like one variant among four, and its own store listing has always called it simply MasterClock.

### Fixed
- Restoring a backup no longer copies the whole file into the cache before checking its size. An archive far past the limit was written to disk in full and rejected afterwards; it is now refused as it arrives, and a failed import no longer leaves its copy behind.
- The Lato typeface used by the E-Ink build is credited in Credits and in the README, alongside the icon, the chess pieces and the sounds.

## v0.8.23 — 2026-08-27

### Fixed
- **A shared settings file could make your game history unopenable.** Imported games were told apart by their start time, so two carrying the same one crashed the history and the "Last games" tab the moment either was opened — and since imported games are stored, it kept crashing after a restart. Clearing the whole history was the only way out. They are told apart by their own identifier now. This affected anything you could import: a `.json` file, a scanned QR code, or a `.zip` backup. Nothing was ever read or sent anywhere; the damage was confined to making your own history unreachable. If a file you were given has already done this, update and your history opens again — you do not need to clear it.
- The same weakness in the scoreboard is closed too, and imported scoreboards now get fresh identifiers.
- **E-Ink: the Close button is back in the version dialog.** On a 4.3" screen the changelog took all the room it asked for and left none for the button, which was not merely cramped but absent from the layout — the dialog could only be dismissed by tapping outside it. The entries give way to the button now.
- **"Time Parameters" no longer appears with nothing under it.** Chronos' Count Up and Move Counts' Count Up have no parameter at all, and both showed the heading anyway. The spacing above it is even across every mode as well.
- The mode options for Hidden, Random, Phases, Gong and Chronos have rounded corners, like every other option in the app.

## v0.8.22 — 2026-08-26

### Added
- **Four more languages: German, Italian, Portuguese and Dutch.** 2695 strings and 54 plural forms, every screen of every build, phone and E-Ink alike. With French and Spanish that is seven languages besides English.
- **Brazilian Portuguese ships alongside European Portuguese.** It is not a second full translation: it carries the 131 strings where the two actually differ and falls back to the European wording for everything else, string by string, the way Android resolves resources. A Brazilian phone gets `usuário` and `tela`; a Portuguese one keeps `utilizador` and `ecrã`.
- **The E-Ink build's settings and changelog scroll with a scrollbar.** E Ink has no fling, no overscroll stretch and no scrollbar that fades in under a finger, so a page that continues below the fold had nothing on screen to say so. The scrollbar carries chevrons that page up and down, long-press to jump to either end, and tap anywhere on the track to seek — and it appears only while the content actually overflows, giving the width back to the page otherwise.
- Timing mode names stay in English, as they have since the first translation: `Sudden Death`, `Fischer`, `Byoyomi`, `FIDE Periods` and the rest are the vocabulary printed on tournament clocks and written in federation rulebooks. Their descriptions are translated.
- **None of the four new translations has been reviewed by a native speaker.** Each was checked by lint, by comparing every format placeholder against the English, and on screen on all four phone builds and on E-Ink — but the wording has had no native reader, and neither has the Spanish from v0.8.21. Corrections are the most useful thing anyone can send: see [TRANSLATING.md](TRANSLATING.md).

### Fixed
- **E-Ink: the settings page no longer buries the time fields.** The system-behaviour switches and the version footer were pinned below the scrolling area, and being unweighted they were measured first — on a 4.3" Mudita screen they took everything, leaving about 95dp for the rest. The mode buttons were followed straight by the System behavior divider, and the hour/minute/second boxes could not be brought into view at any scroll position. The page scrolls as one now.
- **Changes, Credits and Licenses each open at their own first line.** The three tabs shared one scroll position, so leaving one halfway down dropped you into the middle of the next.
- **The launcher icon no longer crowds its mask.** It filled the adaptive-icon safe zone exactly, which is not the same as fitting: a round mask left 3dp of margin and the black stroke ran to the edge. Beside Gmail, Maps or Contacts in the app drawer, ours were visibly the oversized ones. The clock now takes three quarters of the visible area, the proportion its neighbours use. Android 7's unmasked icons are deliberately unchanged.

### Changed
- Android Gradle Plugin updated to 9.3.2, the newest stable release. It changes R8 and the resource shrinker rather than any app code; APK sizes are unchanged.

## v0.8.21 — 2026-08-22

### Added
- **MasterClock speaks French and Spanish.** Every screen of every build, phone and E-Ink alike: 1281 strings and 26 plural forms. On Android 13 and later you can set the language for this app alone, under Settings › Apps › MasterClock › Language; on older versions the app follows the phone's language, so a French phone already gets a French clock.
- Timing mode names stay in English on purpose — `Sudden Death`, `Fischer`, `Byoyomi`, `FIDE Periods` and the rest are the vocabulary printed on tournament clocks and written in federation rulebooks. Their descriptions are translated.
- **The Spanish has not been reviewed by a native speaker.** It was checked by lint, by comparing every format placeholder against the English, and on screen for layout, but the wording has had no native reader. Corrections are the most useful thing anyone can send: see [TRANSLATING.md](TRANSLATING.md).
- Portuguese, Italian, German and Dutch remain open for contributors, and the French and Spanish files are there to copy the shape from. Partial translations work — Android falls back string by string.

### Fixed
- Restoring a backup no longer closes the app when the archive is too large. It is refused with a message, as it always should have been. Archives are also read more carefully: entries the app has no use for are skipped instead of being loaded into memory, while still counting against the size limits.

## v0.8.20 — 2026-08-17

### Changed
- **The clock pads its numbers now: 09:56 where it used to read 9:56.** Display settings has a new "Leading zeros" choice between `01:09:08`, `1:09:08` and `1:9:8` — the first is the new default, the second is what the clock did before, and the third drops every padding zero. It sits with the "always show hours" and "always show minutes" switches, which decide *which* units appear; this one decides how each is written.
- E-Ink, Light and Mini are fixed on the two-digit reading they have always shown, and have no setting to change it.
- Compose, navigation3 and the adaptive layout libraries updated. The last of those had been running on a release candidate; it is on the final release now.

## v0.8.19 — 2026-08-09

### Fixed
- **The web addresses in Credits and Licenses are real links now.** They were printed in the accent colour, so they looked tappable, and nothing happened. Every rulebook source, asset credit and bundled library is now one tap from its page. Screen readers announce them as links too.

### Changed
- Gradle, KSP and navigation3 updated to their current versions. No change you can see; it clears the last outstanding build warnings.
- The World Tafl Federation's permission to bundle their Hnefatafl rules is now recorded in `docs/permissions/`, along with the conditions attached to it.

## v0.8.18 — 2026-08-08

### Fixed
- **The sounds work again.** The four bundled audio files — beep, gong, end of time and the switch click — were corrupt, so the clock played nothing at all where it should have beeped. They have been replaced with working ones. Custom sounds you picked yourself were never affected.

### Changed
- Internal hardening: two places that could have crashed after a future change now fall back gracefully instead.

## v0.8.17 — 2026-08-07

### Added
- **Fullscreen for Light, Mini and E-Ink.** The option existed and worked, but only Complete and Standard had a screen offering it. It now sits in System behavior, just under "Keep screen awake".

### Fixed
- **E-Ink: the row of buttons no longer shifts when you start the clock.** Starting a game added an invisible element above the clock, which pushed the layout down by a few pixels and moved the buttons under your thumb. Introduced in v0.8.13 alongside the screen reader support.

## v0.8.16 — 2026-08-03

### Changed
- **Standard, Light and Mini shrink again — 4.0 MB down to 2.8 MB.** v0.8.15 stopped shipping them the rulebooks; this release stops shipping them the code behind the tools they cannot open either — the notebook, the QR scanner, the Bluetooth board, the trainers and the mode guide, along with the camera and image libraries those need. The saving is larger on the device than the download suggests: Android compiles an app's code when installing it, so unused code costs several times its own size. Expect roughly a third less space used once installed.
- Complete is unchanged and keeps every tool, rulebook and setting.

### Fixed
- Internal cleanup: removed two leftover files and a batch of stale comments referring to a document that was never part of the repository.

## v0.8.15 — 2026-08-03

### Changed
- **Standard, Light and Mini are about three times smaller — 13.1 MB down to 4.0 MB.** All four builds shipped the ten bundled rulebook PDFs, 9.3 MB of documents that only Complete can open: the rules screen is reached from the More tab, which Complete alone has. The three smaller builds no longer carry them. Complete is unchanged and keeps every rulebook.
- The move list in a game log is now drawn as you scroll rather than all at once, so opening a long game no longer builds every row up front.

## v0.8.14 — 2026-08-02

### Changed
- **Standard, Light and Mini no longer ask for the camera, microphone, Bluetooth or location permissions.** All four builds shared one manifest, so every one of them requested permissions that only Complete has a feature for: the notebook's audio, photo and video notes, the QR scanner and the Bluetooth board are reachable from the More tab, which Complete alone has. Complete is unchanged.

### Fixed
- Importing a settings or backup file is now bounded. An oversized or malformed file is rejected instead of being read whole into memory, and a recorded game carrying impossible values can no longer freeze the log screen.
- A game log could show the wrong time against a move when a recording was incomplete.

## v0.8.13 — 2026-08-02

### Added
- **Screen reader support.** The player areas published no accessibility node at all, so with TalkBack running the clock could be read but the turn could never be passed — the app's central function was unreachable. They are labelled buttons now, and flags, turn changes and pauses are announced aloud.
- **Statistics screen.** Games played, total time on the clock, average and median move, slowest move, and the share of moves made under time pressure. Every recorded game also gets a time-per-move chart and a per-move time column in its log.
- **Launcher shortcuts.** Long-press the app icon to start straight from one of your two most recent games or a saved preset; 3+2 and 15+10 pad the list on a fresh install.
- **Groundwork for translation.** The interface now lives in resource files instead of being hardcoded, and French, Spanish, Portuguese, Italian, German and Dutch are ready for contributors to fill in — see TRANSLATING.md. On Android 13 and later the language can be picked per app. No translations ship yet, so every locale still shows English.

### Changed
- **Rename or delete a saved preset by long-pressing it**, instead of two cramped icon buttons crowding the tile.
- **The Fischer mode was misspelled internally.** Correcting it changes a stored value, which **resets your settings to their defaults and clears the recorded moves of older Fischer games.** The games themselves are kept.

### Fixed
- The clock digits no longer get cut off when the system font size is turned up.
- The chess, shogi and both draughts rulebooks opened as blank pages. Replaced with the real federation documents.

## v0.8.12 — 2026-08-01

### Added
- **Custom presets.** Save your current time control under a name from the new "My Presets" tab, then load, rename or delete it. Your presets survive restarts.
- **Re-roll button for Random and Hidden.** Reset deliberately keeps the drawn time so restarting a game does not move it under the players; a dice button now draws a fresh one, without having to switch modes away and back. It hides while the clock runs and returns on pause.

### Changed
- **Presets no longer reset your other settings.** Tapping a built-in preset used to silently restore colours, sounds, theme and every other preference to their defaults. Presets now change the time control only.

### Fixed
- The credits popup's first tab no longer cut its own label.

## v0.8.11 — 2026-08-01

### Added
- The bundled rulebooks now open **offline**. Six buttons on the "Some rules" screen (Morris, Tafl, Quoridor, Abalone, Hex, Santorini) did nothing at all when tapped; they now open their document, and the four federation documents (chess, draughts ×2, shogi) open without a network connection too.
- Every rules document is credited — author or publisher, and a link where there is one — in the credits popup and in the README.
- New **Licenses** tab in the credits popup, listing the open-source libraries the app ships, with their copyright and license.

### Changed
- Replaced the Abalone, Hex, Morris, Quoridor, Santorini and Tafl documents with better sources; Abalone alone drops from 7.4 MB to 192 KB.
- Removed an unused Google Play Services declaration left over from the project template.
- Fixed the project's MIT LICENSE file, which was missing a clause and contained a typo.

## v0.8.10 — 2026-07-25

### Added
- Omni: each player's color is now yours to pick, in the wizard's Players & Order step (P1 blue, P2 red, P3 yellow, P4 green, P5 orange, P6 purple by default). The running session uses your colors instead of a fixed palette that never matched.
- Omni: the Random turn order gained real sub-options — shuffle once per round (new default, everyone plays exactly once) or draw a fresh player every turn, a "never the same player twice in a row" block, and for the per-turn draw a "balance the draw" toggle that favors whoever is behind on turns.

### Fixed
- **No sound at all on some phones**: the volume rocker now adjusts the media stream while in the app. Sounds already played on that stream, but the rocker controlled the ringer instead, so a muted media volume silenced everything with no way to notice or fix it from inside the app.
- Sound loading failures are no longer silent, the sample pool no longer leaks on every volume-slider drag, and Omni now follows the Audio tab's volume and custom sounds live instead of only at app start.
- Omni: the phase clock is reset on every turn, round and game change — it used to freeze at 00:00 from the second turn onwards.
- Omni: leftover turn time is banked only when the turn actually ends, instead of being re-banked at every phase change.
- Omni: a round set to loop now ends when its own clock runs out, instead of looping forever.
- Omni: "Close session" now really ends the session, so Play starts a fresh one instead of resuming a finished one with every clock at zero.
- Omni: the per-phase "Auto advance" toggle is now honored — a phase whose time ran out used to freeze regardless.
- Omni: the Game progress bar uses the current game's own duration, and rounds set to a fixed turn count no longer run extra turns left over from an earlier custom-sequence setup.
- Omni: end-of-level sounds and cutoffs also fire for clocks that run out during a pause between turns, rounds or games.

## v0.8.9 — 2026-07-25

### Fixed
- Light/Mini's System behavior toggles (Keep screen awake/Sound/Haptic feedback) are now in the same order as paper's, and their card now has rounded corners matching Complete/Standard's style instead of a flat rectangle.

## v0.8.8 — 2026-07-25

### Changed
- Light/Mini/paper's "Sound" toggle now also controls the time's-up/flag sound, not just the player-switch sound.
- Omni-Timer is now labeled "(alpha)" in the mode picker.
- "Link board" now reads "not implemented yet" instead of "untested yet".

### Fixed
- Byoyomi's Japanese/Canadian/Progressive sub-modes are now all on one row instead of Progressive being split onto its own line below.
- Mini's Sudden Death/Bonus/Move Timer mode cards are now all on one row instead of Move Timer being split onto its own line below, matching paper's layout.

## v0.8.7 — 2026-07-22

### Fixed
- FIDE Periods now has a real "US Delay" option, distinct from Sudden Death — previously non-Fischer periods silently applied a per-move delay with no way to see or edit it; the editor only offered Sudden Death/Fischer and hid the delay field entirely.
- Mini no longer shows a Bonus Type or Move Timer Type sub-panel with a single, unchangeable option (Fisher-only / Standard-only) — those panels now only appear when there's an actual choice.
- Loading a "Last Games" preset for a Random/Hidden game, then hitting Reset, no longer reverts to a stale random roll from an unrelated earlier game — it now keeps the loaded game's own roll.

## v0.8.6 — 2026-07-21

### Changed
- Each flavor's launcher icon now prints its own short word (FULL/STD/LITE/MINI) in small black text inside the clock face, so the icons are visually distinct too, not just the label.
- **ExtraLight renamed to Mini** everywhere, including the package ID (`...app.extra_light` → `...app.mini`). Like the `io.github.*` rename in v0.8.3, this is a one-time break in the update path: existing ExtraLight installs won't upgrade in place and need a fresh install.

## v0.8.5 — 2026-07-21

### Added
- Each flavor (Complete/Standard/Light/ExtraLight) now shows its own name in the launcher (e.g. "MasterClock Light") instead of all four sharing the plain "MasterClock" label.
- Standard and Light now have access to Omni-Timer, like Complete already did.

### Fixed
- Move Timer (Global Shared) no longer flags every player out of time just because one player's own move timer expired — only the shared pool running out affects everyone now.
- Games played in Gong, Phases, Move Timer (Shared), and Chrono (one-for-all) modes are now correctly saved to game history — they were silently never logged before.
- Gong's "time to move" background now uses the same active-player green as every other mode instead of its own separate color.
- Random/Hidden: hitting Reset now keeps the time already rolled for the current session instead of picking a new random value; switching modes away and back still rerolls, as intended.
- ExtraLight's Bonus and Move Timer submenus no longer expose Bronstein/US Delay or Save & Cap/Overtime/Global/Shared — only Fisher and Standard, matching the flavor's intended scope.
- Labeled the Bluetooth board link as untested, since it can't yet receive real moves from a connected board.

## v0.8.4 — 2026-07-20

### Added
- Light and ExtraLight now show a small "System behavior" section (Sound, Haptic feedback, Keep screen awake) on the Modes page, matching the minimum floor of options the E-Ink (paper) build already had. Previously these two flavors had zero behavior/audio settings at all.

## v0.8.3 — 2026-07-20

### Changed
- Package ID changed from `com.masterclock.*` to `io.github.usernamealreadytakensht.masterclock.*` — required for store submission (F-Droid, Google Play, Accrescent all expect an application ID rooted in a namespace you actually control, and `masterclock.com` isn't owned by this project). This is a one-time break in the update path: earlier installs (e.g. v0.8.2) won't upgrade in place and need a fresh install.
- Added a privacy policy page at https://usernamealreadytakensht.github.io/masterclock-website/, covering data collection (none) and what each runtime permission is used for.

## v0.8.2 — 2026-07-20

### Added
- Omni: configurable auto-cutoff per level (Phase/Turn/Round/Game/Session) — when enabled, that level's own clock reaching zero cuts short whatever turn/round/game is still in progress instead of only advancing on a manual tap.
- Modes: FIDE Periods now supports a per-move delay (US Chess Delay-style) on non-Fischer periods, fixing the "US 80'/40 + 30' + 30s" preset's bonus, which never applied before.
- Modes: Phases gained an "Allow manual skip" setting to let a tap advance the current phase early; without it, tapping only confirms a phase once its own time is already up.
- README: Obtainium badge/deep link.

### Fixed
- Six timer modes (Phases, Move Timer Shared, Move Timer Global Shared, Hourglass, Chrono Countdown, Chrono Countup) previously bypassed all audio/voice feedback entirely; extended to all of them (Gong was already correct and untouched). Negative/Reverse flag behavior also extended to Hourglass and Chrono Countdown, where it maps cleanly onto a single counter.
- Phases: a non-auto-advancing phase reaching zero used to end the game outright, bypassing the Loop and "Flag on end" settings entirely. It now freezes and waits for confirmation instead.
- Phases: tapping the clock always skipped to the next phase regardless of any setting; gated behind the new "Allow manual skip" toggle.
- Paper (E-Ink): 201 Lint warnings resolved (duplicate audio/PDF assets already provided by `core`, unused strings/colors archived or removed).
- Paper: dead QR-share code path cleaned up (was unreachable, referenced screens that do not exist in this module).

### Removed
- Paper: dead `onOmniClick` parameter and a handful of comments that only restated the code below them.

## v0.8.1 — 2026-07-20

### Fixed
- Paper (E-Ink): navigation/top bar ignored the color theme — dark mode ("Reverse colors") now applies everywhere, not just the screen content.
- Paper: disabled switches looked identical to enabled ones — now visibly dimmed.

### Changed
- Paper: typography now uses one consistent weight across all text roles instead of alternating bold/regular.
- Paper: color scheme's secondary/tertiary/inverse roles now match the official Mudita Mindful Design spec.
- Paper: text fields redesigned with an animated focus/error indicator line, replacing the static box border.
- Paper: simplified the ripple-suppression mechanism to a single, non-redundant implementation.
- Version/changelog/credits popup now uses separate Changelog and Credits tabs instead of one long scroll (app + paper).

### Removed
- Unused animation-related imports in the paper module.

## v0.8.0 — 2026-07-20

### Added
- Initial versioned release: Sudden Death, Fischer, Bronstein, US Delay, Byoyomi, FIDE Periods, Hourglass, Gong, Hidden/Random time, move counters, fast-move variants, Omni multi-phase engine.
- Notebook, Scoreboard, mini-games, game history with PGN/PDN/KIF export, QR share/receive, ZIP backup/restore, Bluetooth board connectivity, presets, mode guide.
- Version/changelog/credits popup, accessible from Settings for every flavor.
- CI: automated signed APK builds for all flavors (complete/standard/light/extraLight) + paper on tag push, published as GitHub/GitLab releases.

### Fixed
- Security/build audit: untrusted import paths (QR/JSON/ZIP) sanitized, FileProvider scope restricted, backup exposure closed, ZIP-bomb protection added, release signing configured, dependency versions pinned.
- Flavor gating: More tab restricted to complete; Modes tab now always visible (was hidden entirely for extraLight/eink); Behavior/Display/Audio tabs restricted to complete + standard.
- Adaptive app icon clipped by the OS's mandatory safe-zone inset — rescaled to fit.
