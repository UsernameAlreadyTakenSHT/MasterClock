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
