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
