# Translating MasterClock

Translations are welcome, and you do not need to be able to build the app to contribute one — the
files are plain XML.

The app ships in **English**, **French**, **Spanish**, **German** and **Italian**. Two more are
prepared — Portuguese and Dutch — with their files in place but still empty, so those fall back to
English until someone fills them in.

The finished files are a good model to copy from: they cover the same strings you would be starting
on, in the same order as the English source. The German one is the most useful to read first,
because German is the longest language here and its file shows which labels had to be shortened to
fit — see below.

> **The Spanish, German and Italian are unreviewed.** They were translated without a native
> speaker, and checked only by lint, by a mechanical comparison of the format placeholders, and on
> screen for layout. The wording is what to look at first — corrections are very welcome, and are
> the cheapest kind of contribution to make. The French was reviewed by a native speaker.

### Three labels that have to stay short

Every language so far has run into the same three places, and it is worth knowing about them before
you start rather than after a screenshot:

- **The settings tab for "Behavior".** The bottom tab bar fits about nine characters. "Comportement",
  "Comportamiento" and "Verhalten" all crowd or wrap it, so French, Spanish and German use "Jeu",
  "Juego" and "Spiel" instead.
- **The third presets tab, "Last Games".** It truncates silently, which reads fine until the row
  changes. Shorten it deliberately: "Dernières", "Últimas", "Letzte".
- **The three buttons under the E-Ink clock**, in `paper` only. They are narrow, and `paper` has its
  own copy of these strings so it can be shortened without touching the phone app. German needed
  both "Reset" for `common_reset` and "Optionen" for `timer_settings`.

Partial translations are fine. Android falls back **string by string**, so a file with ten lines
translated works perfectly — those ten are shown in your language, the rest stay English. There is
no need to finish a language before it is useful.

## Where the files are

| Module | What it covers | English source |
|---|---|---|
| `app` | the phone/tablet app | `app/src/main/res/values/strings.xml` |
| `paper` | the separate E-Ink app | `paper/src/main/res/values/strings.xml` |

Your language goes in the folder next to it, named after its code:

```
app/src/main/res/values-fr/strings.xml      ← French
app/src/main/res/values-de/strings.xml      ← German
…
```

Those files already exist, with a header comment and nothing else.

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

**Some things stay in English on purpose.** Anything marked `translatable="false"` is left alone.
That covers two groups.

*Timing mode names* — `Sudden Death`, `Fischer`, `Bronstein`, `Byoyomi`, `Hourglass`, `Gong`,
`FIDE Periods` and the rest. These are the vocabulary of clock settings, recognised in English by
players everywhere, and they have to match what is printed on physical tournament clocks and
written in federation rulebooks. Their *descriptions* are translated; their names are not.

*Preset names* — `Fisch. 3 + 2s`, `Jap. Byo 20'`, `Armag. 5 / 4`. Same reasoning, and they are
mostly numbers and abbreviations anyway.

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

1. Create `app/src/main/res/values-XX/strings.xml`, copying the header from an existing one.
2. Add `<locale android:name="XX" />` to **both** `app/src/main/res/xml/locales_config.xml` and
   the identical file in `paper`.

Use the plain language code (`pt`, not `pt-BR`) unless the difference genuinely matters for your
language.

## Sending it in

Open a pull request on [GitHub](https://github.com/UsernameAlreadyTakenSHT/MasterClock) or
[GitLab](https://gitlab.com/UsernameAlreadyTakenSHT/masterclock), or simply open an issue and
attach the file.

Please say whether you are a native speaker — not a requirement, just useful context for review.

## A note on scale

Most of the interface is still hardcoded English inside the Kotlin sources and is being moved into
`strings.xml` screen by screen. So `values/strings.xml` will keep growing for a while, and a
language that looks complete today will have new strings to pick up later. Nothing breaks in the
meantime: new strings simply appear in English until translated.
