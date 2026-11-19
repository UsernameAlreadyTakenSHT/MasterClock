# Settings › More

Everything around the clock: the manual, the tools, the notebook, the scoreboard, the board link,
the history, export and import. In Complete only.

- [Help and Support](#help-and-support) · [Game Tools](#game-tools) · [More Tools](#more-tools) · [Board Connection](#board-connection)
- [Game Data](#game-data) · [Data & Sharing](#data--sharing) · [Danger Zone](#danger-zone) · [Version, credits and licences](#version-credits-and-licences)

## Help and Support

**Timing Engines Manual** — the short description of every mode and variant, readable offline. This reference is its long version.

## Game Tools

### Chess variants generator

Draws a starting position and shows it on a board.

| Variant | The position |
|---|---|
| **Chess960** | Symmetric, bishops on opposite colours, king between the rooks: castling is possible. |
| **Double 960** | As Chess960, but each side drawn separately: asymmetric. |
| **Chess2880** | Symmetric, bishops on opposite colours, king anywhere: no castling. |
| **Transcendental** | Asymmetric, bishops on opposite colours, king anywhere. |
| **Shuffle** | Symmetric, king and bishops anywhere. |

A button draws another position, and **COPY FEN** copies the position as text, to paste into another
chess app.

### Some rules

Rulebooks kept in the app, readable without a connection. Opening one needs a PDF viewer on the
device.

| Section | Documents |
|---|---|
| **Chess (official doc)** | FIDE Laws |
| **Draughts (official doc)** | FMJD Annexes, IDF Rules |
| **Shogi (official doc)** | FESA Rules |
| **More Games** | Morris, Tafl |
| **More Resources** | A link to a wiki of strategy games, opened in the browser. |

Each belongs to its author or federation; the credits list them, and the project's
[permission log](../permissions/README.md) says what was asked of whom.

### Scoreboard

A running score between two players. Their names are typed at the top. **1 - 0**, **½ - ½** and
**0 - 1** add a result; **Add result or note…** adds a line of text. The button at the top resets the
scoreboard.

### Notebook

Notes kept with the app. **New Note** offers six kinds:

| Kind | What it is |
|---|---|
| **Text** | Written text with a toolbar: bold, italic, underline, strikethrough; highlight in five colours; a heading; a checkbox; centred lines; bulleted and numbered lists; text colour; chess pieces; annotations (move quality such as `!?`, evaluations, and other symbols); every emoji. Undo, redo and clear are at the top. |
| **Draw** | A drawing: a pen, shapes (line, arrow, rectangle, ellipse), chess and draughts pieces and emojis that can be moved and resized, an eraser, colours, a width, and a background — none, a photo, or one of the board notes. |
| **Audio** | A voice recording. Asks for the microphone the first time. |
| **Image** | A photo, with a caption. Asks for the camera the first time. |
| **Video** | A video of at most one minute. |
| **Board** | A position set up by hand, on a chess board, a 10×10 draughts board or an 8×8 one. |

Every note has a title and the date it was made. The bin beside a note deletes it, after asking. The
notebook has a limit on its size; full, it asks for a note to be deleted before another is added.

## More Tools

Small things a table needs.

| Tool | What it does |
|---|---|
| **Coin Toss** | Heads or tails. |
| **Dice roll** | One die. |
| **Short straw** | Two to ten straws, five by default. **PREPARE** hides a short one among them; each player picks, and the short one is revealed when it is drawn. |
| **Random card** | Draws one playing card. |
| **Stop at right moment** | A stopwatch to be stopped as close as possible to 5.000 seconds. |
| **Blindfold Trainer** | A square is named; say whether it is white or dark. Thirty seconds, one point per right answer. |
| **Knight's Path** | Two squares are given; say the fewest moves a knight needs between them. Thirty seconds. |
| **Name the square** | A coordinate is shown; tap that square on an unlabelled board. Thirty seconds. |

## Board Connection

**Link board (not tested yet)** opens the connection to an electronic chessboard. The app carries
the protocols of DGT, Millennium, Chessnut and Certabo boards, and a generic one, over Bluetooth and
over USB. None of it has been tried on a real board, and it is labelled as untested for that reason.

The screen shows the connection's state, then three lists: **Plugged in** for a board on the USB
port, **Paired** for boards already paired in the system's Bluetooth settings, and **Nearby
Devices**, filled by the refresh button. While a board occupies the USB port, the phone does not
charge.

Once a board has said anything, a **share** button appears in the screen's bar. It hands another
app a text file, `masterclock_board_log.txt`, of what the board and the app said to each other in
the last minutes: when, which way, over which link, under which protocol, and the bytes. It names
the app's version, Android's and the phone's model, and neither the board nor its address. It is
what to send when a board does not work, or for a make the app does not know. The log itself is
kept in memory only and is gone when the app closes; a file you have shared stays in the app's
private cache until the next one replaces it, or until the app's cache is cleared.

A piece slid from one square to another that rests on a square on its way is one move: the turn
passes when it first comes to rest, and the record names the square it ended on.

| Setting | Default | |
|---|---|---|
| **Auto switch turn on move** | off | A move made on the linked board presses the clock, so that nobody has to. Not in the modes where nobody presses. |
| **Game** | Chess | **Chess** or **Draughts**: the game the board and the move records are read as. |

## Game Data

**Games logs & history** — the games played, most recent first, each with its date and its number
of moves. A game opens on its details: every move with the time spent on it and the time left, and
the events of the game — pauses, flags, period changes, the arbiter's corrections. With moves
recorded from a board, the game can be copied as PGN for chess or PDN for draughts. A shogi game is copied as a plain list of its moves.

A game is written to the history when it is reset.

**Statistics** — over the whole history: games played, moves, the average, median and slowest move,
the time on the clock, the moves played under 10 % of the starting clock, the most recent game, and
the modes played.

| Setting | Default | |
|---|---|---|
| **Limit history by count** | on, 100 | Keeps the last **10**, **100**, **1k** or **10k** games. Off, every game is kept. |
| **Limit history by age** | off | Keeps the games of the last day, week, month, six months or year; a month when first turned on. |

## Data & Sharing

### Export / Share

One switch, **Include history & text notes**, then four ways out:

| Action | What leaves |
|---|---|
| **Share as QR Code** | The settings only, as a code another device scans. Settings too large for a code are refused, with a message: share them as a file. |
| **Share File** | A `.json` file, through the system's share sheet. Made for someone else: like the QR code, it leaves out where your custom sounds are stored. |
| **Export to Storage** | The settings as a `.json` file saved to the device, your custom sounds' locations included. |
| **Export All (Full Backup)** | A `.zip` archive: every setting, the history, the presets, and the notebook with its photos, recordings and videos. |

A history too long for a settings file is refused with a message pointing to the full backup.

### Import / Scan

| Action | What comes in |
|---|---|
| **Scan QR Code** | The settings of another device, by pointing the camera at its code. |
| **Import JSON File** | A `.json` file exported earlier. |
| **Import Full Backup** | A `.zip` backup: settings and media. |

Every import asks first. A file replaces the current settings; its games, presets, notes and
scoreboard results are added to those already there, and nothing is deleted. A scanned code
replaces the settings and can add games and a scoreboard: accept one only from someone you trust.
What comes in is checked and bounded before it is used, whatever its source.

### Share Application

Sends the app itself to another phone, as an **APK**, which installs directly, or as a **ZIP** for
phones that refuse APK files over Bluetooth; the recipient extracts the APK from it.

## Danger Zone

| Action | |
|---|---|
| **Clear logs** | Deletes the whole game history, after asking: "All recorded history will be permanently deleted." |
| **Reset settings** | Puts every setting back to its default, the two history limits aside, after asking: "This action cannot be undone." |

## Version, credits and licences

The last line of the page gives the version and its date. Tapping it opens three tabs:

- **Changes** — what each version brought, for this build.
- **Credits** — the icon, the pieces, the emojis, the sounds, the font, and the rulebooks, each with
  its author, its licence and a link.
- **Licenses** — the open-source libraries this build contains.
