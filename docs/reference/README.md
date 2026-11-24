# MasterClock reference

The complete description of what MasterClock does, screen by screen: everything each one shows,
every option, its default and what it changes. The manual inside the app is the short version,
readable offline at the table; this is the long one.

It is written from the code and its tests, not from memory, and it says so where a behaviour is
experimental or untested. If the app and this reference disagree, the app is right and this is a
bug: please open an issue.

| Page | The screen | What it covers |
|---|---|---|
| [Home](home.md) | The clock | The faces and what they show, the control bar, starting, pausing and resetting, presets, the arbiter's panel. |
| [Modes](modes.md) | Settings › Modes | The fourteen timing modes and their variants, every parameter, what happens when a clock reaches zero, and Omni-Timer: its seven steps and its clock. |
| [Behavior](behavior.md) | Settings › Behavior | Flag behavior, tournament rules, presses, the background, Berserk, per-player settings, three and four players. |
| [Display](display.md) | Settings › Display | Time format, what the faces show, layout, theme and colours. |
| [Audio](audio.md) | Settings › Audio | Sounds, voice, countdowns, vibration, volume, custom sounds. |
| [More](more.md) | Settings › More | The manual, tools, notebook, scoreboard, board link, game data, export and import, reset. |

## Which build has which screen

| | Complete | Standard | Lite | Mini | E-Ink |
|---|---|---|---|---|---|
| Icon | <img src="../../branding/icon-complete.svg" alt="Complete icon" width="56"> | <img src="../../branding/icon-standard.svg" alt="Standard icon" width="56"> | <img src="../../branding/icon-lite.svg" alt="Lite icon" width="56"> | <img src="../../branding/icon-mini.svg" alt="Mini icon" width="56"> | <img src="../../branding/icon-paper.svg" alt="E-Ink icon" width="56"> |
| Modes | ✅ | ✅ | ✅ | 🟡 (3 modes) | 🟡 (3 modes) |
| Home | ✅ | ✅ | ✅ | ✅ | ✅ (its own) |
| Presets, arbiter's panel | ✅ | ✅ | ✅ | ❌ | ❌ |
| Behavior, Display, Audio | ✅ | ✅ | ❌ | ❌ | ❌ |
| More | ✅ | ❌ | ❌ | ❌ | ❌ |

🟡 in part: the three modes of Mini and E-Ink are Sudden Death, Fischer and Move Timer Standard. The E-Ink app
has a short settings page of its own, described at the end of [Home](home.md).

## Conventions

- **Bold names** are written as the app shows them in English. Mode names stay in English in every
  language, as they are the names printed on tournament clocks.
- *Default* is what a fresh install has.
- Times are written `h:mm:ss` or `m:ss`.
