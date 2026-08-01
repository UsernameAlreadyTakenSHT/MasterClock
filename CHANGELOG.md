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
