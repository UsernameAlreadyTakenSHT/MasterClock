# Translating MasterClock

Translations are welcome, and you do not need to be able to build the app to contribute one — the
files are plain XML.

The app ships in **English**, **French**, **Spanish**, **German**, **Italian**, **Portuguese** — in
both its European and Brazilian forms — and **Dutch**. Every language the app was prepared for is
now filled in, so what remains is correcting what is there and adding languages that are not listed
yet.

The finished files are a good model to copy from: they cover the same strings you would be starting
on, in the same order as the English source. The German one is the most useful to read first,
because German is the longest language here and its file shows which labels had to be shortened to
fit — see below.

> **None of the translations has been reviewed.** They were translated without a native speaker,
> and checked only by lint, by a mechanical comparison of the format placeholders, and on screen
> for layout. The wording is what to look at first — corrections are very welcome, and are the
> cheapest kind of contribution to make.

### Three labels that have to stay short

Every language so far has run into the same three places, and it is worth knowing about them before
you start rather than after a screenshot:

- **The settings tab for "Behavior".** The bottom tab bar fits about nine characters at full size.
  A longer label no longer breaks: the five shrink together to fit, so one long word makes all of
  them smaller. "Comportement", "Comportamiento" and "Verhalten" would, so French, Spanish and
  German use "Jeu", "Juego" and "Spiel" instead, and French says "Aspect" for Display.
- **The third presets tab, "Last Games".** The three tabs shrink together too. Shorten it
  deliberately: "Dernières", "Últimas", "Letzte".
- **The three buttons under the E-Ink clock**, in `paper` only. They are narrow, and `paper` has its
  own copy of these strings so it can be shortened without touching the phone app. German needed
  both "Reset" for `common_reset` and "Optionen" for `timer_settings`.

Partial translations are fine. Android falls back **string by string**, so a file with ten lines
translated works perfectly — those ten are shown in your language, the rest stay English. There is
no need to finish a language before it is useful.

## Where the files are

| Module | What it covers | English source |
|---|---|---|
| `app` | the phone/tablet app, every edition | `app/src/main/res/values/strings.xml` |
| `app`, Complete only | the tools, the notebook, the manual, the rules, QR codes and the board connection | `app/src/complete/res/values/strings.xml` |
| `core` | what the clock says aloud, and the board connection's error messages | `core/src/main/res/values/strings.xml` |
| `paper` | the separate E-Ink app | `paper/src/main/res/values/strings.xml` |

A language is four files, then, not one. The Complete edition's screens have a file of their own so
that the smaller editions do not carry their text; it holds over three hundred strings, and is
easy to miss.

Your language goes in the folder next to each, named after its code:

```
app/src/main/res/values-fr/strings.xml      ← French
app/src/main/res/values-de/strings.xml      ← German
…
```

Six languages have these files filled in: French, German, Spanish, Italian, Portuguese and Dutch.

## How to translate

Copy a line from `values/strings.xml` into your language's file and translate **only the text
between the tags**. Never change the `name`.

```xml
<!-- values/strings.xml (English, the source — do not edit) -->
<string name="timer_resume">Resume</string>

<!-- values-fr/strings.xml (yours) -->
<string name="timer_resume">Reprendre</string>
```

Keep the lines inside the existing `<resources …>` element.

## Rules that matter

**Placeholders must survive.** `%d` is a number, `%s` is a piece of text. They have to appear in
your translation, or the app crashes when it tries to fill them in.

```xml
<string name="timer_moves">MOVES: %d</string>
<string name="timer_moves">COUPS : %d</string>     <!-- fine -->
<string name="timer_moves">COUPS</string>          <!-- crashes -->
```

If a string has **more than one** placeholder, they are numbered (`%1$s`, `%2$d`) and you may
reorder them freely — that is what the numbers are for.

**Apostrophes must be escaped** as `\'`, otherwise the file will not compile. This bites French
constantly:

```xml
<string name="example">Fin de l\'échange</string>
```

**French spaces before `: ; ? !` and inside `« »` are non-breaking**, written `\u00A0`. A plain
space lets a narrow screen wrap the line just before the `?`, which then starts the next line on
its own. The escape rather than the character itself, because a non-breaking space is invisible in
an editor and the next person to touch the line could not tell it was there:

```xml
<string name="example">Supprimer «\u00A0%s\u00A0»\u00A0?</string>
```

**Some things stay in English on purpose.** Anything marked `translatable="false"` is left alone.
That covers two groups.

*Timing mode names* — `Sudden Death`, `Fischer`, `Bronstein`, `Byoyomi`, `Hourglass`, `Gong`,
`FIDE Periods` and the rest. These are the vocabulary of clock settings, recognised in English by
players everywhere, and they have to match what is printed on physical tournament clocks and
written in federation rulebooks. Their *descriptions* are translated; their names are not.

*Preset names* — `Fisch. 3 + 2s`, `Jap. Byo 20'`, `Armag. 5 / 4`. Same reasoning, and they are
mostly numbers and abbreviations anyway.

**A mode's name is never translated, and neither is the name of one of its variants** —
`Accelerate` and `Transfer` under Fast Move, `Down` and `Up` under Staircase, `Countdown` and
`Countup` under Chronos, and so on. They stay English inside a translated sentence, as the manual
writes them: "Transfer : le prochain coup de l'adversaire reçoit…".

A setting that only *contains* such a name is not a name itself. It keeps the name and translates
the rest: `Cumulative Transfer` is the switch "Transfer cumulatif" in French, "Kumulativer
Transfer" in German, "Transfer acumulativo" in Spanish. "Transfer" there is not a misspelling of
*transfert* or an English word left behind — it is the variant's name, and a reviewer who
"corrects" it breaks the link between the switch and the variant it belongs to.

And wherever the manual speaks of a setting, it names it **as the settings screen shows it in that
language**. It said "Cumulative Transfer" in every language, a label no translated screen has. The
two live in different files — the switch in `app/src/main`, the manual in `app/src/complete` — and
nothing checks one against the other, so when you change a label, search the other three files of
your language for it.

**Counted things are `<plurals>`, not `<string>`.** Anything that varies with a number lives in a
`<plurals>` block, and you add or remove `<item>` elements to match your own language — English
needs two, Russian and Polish need three or four.

French needs **three**, which surprises most French speakers: `one` (which also covers 0),
`many` for round millions — "2 000 000 **de** minutes" — and `other` for everything else. A clock
will never count that high, but lint checks the categories rather than the plausible range, so
leaving `many` out is a warning on every plural.

```xml
<!-- English -->
<plurals name="stats_chart_moves">
    <item quantity="one">%d move</item>
    <item quantity="other">%d moves</item>
</plurals>

<!-- French -->
<plurals name="stats_chart_moves">
    <item quantity="one">%d coup</item>
    <item quantity="many">%d de coups</item>
    <item quantity="other">%d coups</item>
</plurals>
```

Valid `quantity` values are `zero`, `one`, `two`, `few`, `many` and `other`. Use only the ones your
language actually distinguishes; `other` is always required. If you are unsure, the
[Unicode plural rules](https://cldr.unicode.org/index/cldr-spec/plural-rules) list them per
language.

**Watch the length.** Several labels sit in very tight spaces: the buttons under the clock, the
tabs in the credits dialog, the labels on the clock face itself. French and German commonly run
40–80% longer than English. If a natural translation is much longer, prefer a shorter wording — a
truncated label helps nobody.

## Testing it, if you can build

Install the app, then either switch your phone's language, or on Android 13+ go to
**Settings › Apps › MasterClock › Language** and pick yours directly. The languages listed there
come from `res/xml/locales_config.xml`.

If you cannot build, send the file anyway — it will be checked before merging.

## Adding a language that is not listed

1. Create `values-XX/strings.xml` beside each of the four English files listed under "Where the
   files are", copying the header from an existing one.
2. Add `<locale android:name="XX" />` to **both** `app/src/main/res/xml/locales_config.xml` and
   the identical file in `paper`.

Use the plain language code (`pt`, not `pt-BR`) unless the difference genuinely matters for your
language.

### When it does matter: regional variants

Portuguese is the one place where it did. `values-pt` holds the full European translation and
`values-pt-rBR` holds **only the words Brazil says differently** — tela for ecrã, configurações for
definições, compartilhar for partilhar, and so on. Android resolves a pt-BR device as
`values-pt-rBR`, then `values-pt`, then English, string by string, so the override is some 250
strings over the four files rather than a second full translation.

Two things to get right, both of which fail silently:

- **The folder and the config file spell the region differently.** The resource folder is
  `values-pt-rBR`, with the `r` that marks a region qualifier; `locales_config.xml` wants plain
  BCP-47, `<locale android:name="pt-BR" />`. Swap them and nothing breaks at build time — the
  strings simply never appear, or the system language picker offers an entry that resolves to
  nothing.
- **Every key in the override must already exist in the base file.** A typo in an override key is
  invisible on a European device and leaves that one string in English on a Brazilian one.

## Sending it in

Open a pull request on [GitHub](https://github.com/UsernameAlreadyTakenSHT/MasterClock) or
[GitLab](https://gitlab.com/UsernameAlreadyTakenSHT/masterclock), or simply open an issue and
attach the file.

Please say whether you are a native speaker — not a requirement, just useful context for review.

## A note on scale

Every string the interface shows is in `strings.xml`: some 550 in the phone app, about 40 in the
E-Ink app. A few words stay English on purpose and are not there: the names of the timing modes
(Fischer, Byoyomi, Sudden Death…), as tournaments use them, and file formats such as ZIP and APK.
New features add strings, so a language complete today may have a few new ones later; until they
are translated they appear in English, and nothing breaks.
