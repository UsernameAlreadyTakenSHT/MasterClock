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
