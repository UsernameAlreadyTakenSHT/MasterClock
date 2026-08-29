# MasterClock

> [!WARNING]
> For transparency: this project is developed with the help of AI (Claude Code). Review the code accordingly before relying on it.

> [!NOTE]
> The app is still in testing. Expect bugs, and please report anything you run into.

A chess clock app for chess, wargames, and tabletop games. Handles classic time controls as well as more complex multi-phase formats (Omni mode).

<a href="https://github.com/UsernameAlreadyTakenSHT/MasterClock/releases/latest"><img src="branding/get-it-on-github.png" alt="Get it on GitHub" height="55"></a>
<a href="https://gitlab.com/UsernameAlreadyTakenSHT/masterclock/-/releases"><img src="branding/get-it-on-gitlab.png" alt="Get it on GitLab" height="55"></a>
<a href="https://apps.obtainium.imranr.dev/redirect.html?r=obtainium%3A%2F%2Fadd%2Fhttps%3A%2F%2Fgithub.com%2FUsernameAlreadyTakenSHT%2FMasterClock"><img src="branding/get-it-on-obtainium.png" alt="Get it on Obtainium" height="55"></a>

## Features

- Classic time controls: Sudden Death, Fischer, Bronstein, US Delay, Byoyomi (Japanese/Canadian/Progressive), Hourglass, Gong, Hidden/Random time, move counters, time banking.
- Omni mode: sequential game lists, custom round behavior, multi-phase turns (Think > Move > Resolve).
- Notebook, scoreboard, mini-games, game history with PGN/PDN/KIF export.
- Four build flavors (Complete, Standard, Lite, Mini), plus a dedicated E-Ink version for Mudita devices with a minimal black-and-white UI.

## Flavors

| | Complete | Standard | Lite | Mini | E-Ink |
|---|---|---|---|---|---|
| Modes settings | ✅ | ✅ | ✅ | ✅ (3 modes) | ✅ (3 modes) |
| Behavior / Display / Audio settings | ✅ | ✅ | ❌ | ❌ | ❌ |
| More (tools, backup, notebook…) | ✅ | ❌ | ❌ | ❌ | ❌ |
| Presets | ✅ | ✅ | ✅ | ❌ | ❌ |
| Arbitre mode | ✅ | ✅ | ✅ | ❌ | ❌ |

"3 modes" means Sudden Death, Fischer, and Move Timer Standard only — everything else supports the full set of timing modes.

## Translations

The app is in **English, French, Spanish, German, Italian, Portuguese** — in both its European and
Brazilian forms — **and Dutch**. All but the French are unreviewed by a native speaker, so
corrections are especially welcome, and are the easiest contribution to make: the files are plain
XML and you do not need to build the app. See **[TRANSLATING.md](TRANSLATING.md)**, which also
covers adding a language that is not listed yet.

Partial translations are useful: Android falls back string by string, so ten translated lines are
ten lines in your language and the rest stay English. See **[TRANSLATING.md](TRANSLATING.md)**.

On Android 13 and later the language can be picked per app, under
Settings › Apps › MasterClock › Language.

## Privacy

MasterClock collects nothing: no analytics, no ads, no trackers, and no network access at all.
Settings, game logs and notebook entries stay on the device. The
[privacy policy](https://usernamealreadytakensht.github.io/masterclock-website/) says what each
runtime permission is for and when it is asked for.

## Verifying a release

Releases ship a `SHA256SUMS.txt` beside the APKs, and tags from v0.8.24 on are GPG-signed with the
key in [`docs/signing-key.asc`](docs/signing-key.asc) — fingerprint
`210B 5FD0 0E17 66BB 8797  3EDC A286 94BC BC91 9301`. [docs/VERIFYING.md](docs/VERIFYING.md) explains
what each check proves and what it does not.

## Credits & Licensing

- **Logo icon**: clock icon by [Paweł Kuna](https://opensvg.dev/icons) (v3.44.0), MIT License.
- **Chess pieces**: "Cburnett" style, from [Wikimedia Commons](https://commons.wikimedia.org/wiki/Category:SVG_chess_pieces), by [Cburnett](https://en.wikipedia.org/wiki/User:Cburnett/GFDL_images/Chess). GFDL and CC BY-SA 3.0.
- **Audio**:
  - Gong: [Zen Gong – Alex_Jauk](https://pixabay.com/sound-effects/film-special-effects-zen-gong-199844/)
  - Beep: [Beep – u_edtmwfwu7c](https://pixabay.com/sound-effects/film-special-effects-beep-329314/)
  - Final beep: [Public Domain Beep Sound – qubodup](https://pixabay.com/sound-effects/public-domain-beep-sound-100267/)
  - Switch: [Light Switch – Pixabay](https://pixabay.com/sound-effects/film-special-effects-light-switch-82388/)
- **Font**: [Lato](https://www.latofonts.com/) by Łukasz Dziedzic, [SIL Open Font License 1.1](https://openfontlicense.org/). Bundled by the E-Ink build.

### Rules documents

The Complete build bundles these rulebooks so they can be read offline from the "Some rules" screen. Each remains the property of its author or publisher; credit below is the attribution carried by the document itself. If you hold rights to one of these and would like it removed, open an issue and it will be taken out.

- **Chess** — Laws of Chess, [FIDE](https://www.fide.com/), compiled by Alex Holowczak.
- **Draughts (international)** — FMJD Annexes 2024, [Fédération Mondiale du Jeu de Dames](https://www.fmjd.org/), by Ada Dorgelo, Frank Teer and Jacek Pawlicki.
- **Draughts (64)** — Official Rules of the Game, [International Draughts Federation](https://idf64.org/).
- **Shogi** — FESA Rules, [Federation of European Shogi Associations](https://fesashogi.eu/).
- **Nine Men's Morris** — the game is in the public domain; the rulebook and "Stacking Morris" are © 2022 Kanare Kato.
- **Tafl** — Historical Hnefatafl rules, [World Tafl Federation](https://aagenielsen.dk/).
- **Quoridor** — © & ® 1997 [Gigamic](https://www.gigamic.com/), from a concept by Mirko Marchesi.
- **Abalone** — © Abalone S.A., France (registered trademark, patent DM/012362), distributed by [FoxMind](https://www.foxmind.com/). All rights reserved.
- **Hex** — David Beckwith, June 2021.
- **Santorini** — © 2007 Dr. Gordon Hamilton; the document states it may be reproduced for non-commercial purposes.

### Open-source licenses

Also listed in-app under the version footer → Licenses tab.

| Library | Copyright | License |
|---|---|---|
| [AndroidX / Jetpack Compose](https://developer.android.com/jetpack) | The Android Open Source Project | Apache-2.0 |
| [Kotlin & kotlinx](https://github.com/JetBrains/kotlin) | JetBrains s.r.o. and Kotlin contributors | Apache-2.0 |
| [Material Components for Android](https://github.com/material-components/material-components-android) | Google LLC | Apache-2.0 |
| [Coil](https://github.com/coil-kt/coil) | Coil Contributors | Apache-2.0 |
| [Accompanist](https://github.com/google/accompanist) (Drawable Painter, Permissions) | Google LLC | Apache-2.0 |
| [AndroidSVG](https://github.com/BigBadaboom/androidsvg) | Paul LeBeau | Apache-2.0 |
| [OkHttp](https://square.github.io/okhttp/) · [Okio](https://square.github.io/okio/) | Square, Inc. | Apache-2.0 |
| [ZXing Core](https://github.com/zxing/zxing) | ZXing Authors | Apache-2.0 |
| [ZXing Android Embedded](https://github.com/journeyapps/zxing-android-embedded) | JourneyApps | Apache-2.0 |
| [Guava](https://github.com/google/guava) | The Guava Authors | Apache-2.0 |
| [Dagger](https://github.com/google/dagger) | Google LLC | Apache-2.0 |
| [javax.inject (JSR-330)](https://github.com/javax-inject/javax-inject) | The JSR-330 Expert Group | Apache-2.0 |

Only the first three ship in every build. Everything below them reaches Complete alone: R8 strips
each one from Standard, Lite, Mini and E-Ink, none of which ever call it. The last three arrive
through CameraX and amount to a handful of classes, but they are shipped and so are credited.

Project licensed under the MIT License.
