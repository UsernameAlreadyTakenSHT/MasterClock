# Home: the clock

The screen the app opens on. Two faces, one per player, and a control bar between them.

- [The faces](#the-faces) · [The control bar](#the-control-bar) · [A game from start to end](#a-game-from-start-to-end)
- [Presets](#presets) · [The arbiter's panel](#the-arbiters-panel)
- [In the background](#in-the-background) · [Three and four players](#three-and-four-players) · [The E-Ink app](#the-e-ink-app)

## The faces

Each player has a half of the screen. The top one is drawn upside down, so that two players sitting
across the phone each read their own.

A face is a button: pressing it ends that player's move. What it shows:

| On the face | When |
|---|---|
| The time | Always, except in Move Counts (a number of moves) and Hidden (`??:??`). Tenths and hundredths appear as [Display](display.md) sets them. A minus sign in front means an overrun under the Negative flag behavior. |
| `MOVES: 12` | FIDE Periods: the moves left to the next period. Move Counts, and Canadian and Progressive byoyomi, where it is the moves left in the block. With **Always show moves** on, each player's count is under the clock as `P1 MOVES: 12`. |
| `PERIOD: 1/3` | Japanese byoyomi: the period being played, with **Show period** on. FIDE Periods: the period, when no move count is running. |
| `PERIODS: 3` | Japanese byoyomi with **Show period** off: the periods left. |
| `DELAY: 5.0` | US Delay, and FIDE periods with a delay: the seconds of delay left, on the face of the player on the move. |
| `OT: 9:40`, `CAP: 2:00` | Move Timer Overtime: the reserve. Save & Cap: the ceiling. |
| The phase's name | Phases. |
| `REFLECT`, then `MOVE!` | Gong. |
| `PERIOD FLAG` | FIDE Periods: a period ended on its time and play went on into the next. |
| `FLAGGED` | The player is out of time. |
| `FINISHED` | The game ended without a flag: Phases ran through, or Chronos reached zero. |
| A Berserk mark | The player went Berserk; see [Behavior](behavior.md#berserk). |

The colours say whose turn it is: the active side in one colour, the waiting side in another, a side
that lost in a third. All are chosen in [Display](display.md#colours); green, grey and red by
default. With **Flash on low time** on, the active face flashes in its last ten seconds.

For TalkBack each face is one element, read as "Player 1, 9 minutes 57 seconds", with "your turn"
for the player on the move.

## The control bar

Five round buttons across the middle. It turns with the clock when the orientation is changed.

| Button | What it does |
|---|---|
| **Presets** | Opens the list of time controls; see [Presets](#presets). Complete, Standard, Lite. |
| **Reset** | Ends the game and puts the clocks back to the start. With **Confirm reset** on it asks first. A game is written to the history when it is reset. |
| **Start** / **Pause** / **Resume** | The large button. Start begins the game and starts player 1's clock. |
| **Arbiter** | Opens the arbiter's panel. It opens only while the game is paused. Complete, Standard, Lite. |
| **Settings** | Opens the settings, on the Modes tab. |

In Random and Hidden a sixth button, a die, draws a new time. It restarts the game, so it asks first
when Confirm reset is on.

Back, pressed once, says **Press Back again to exit**; the second press leaves the app.

## A game from start to end

1. **Start** starts player 1's clock.
2. Each player ends their move by pressing their own face, which starts the next clock. In the modes that run one clock for the table
   — Gong, Shared (Auto), Phases, Chronos with One for All — nobody presses.
3. **Pause** stops every clock; **Resume** starts the same one again.
4. The game ends when a clock runs out, as [Flag Behavior](modes.md#when-a-clock-reaches-zero-flag-behavior)
   decides, or when the mode itself ends it. The face says `FLAGGED` or `FINISHED`.
5. **Reset** clears the game and records it.

Complete, Standard and Lite save the game in progress every fifteen seconds and at each pause. If
Android closes the app, the arbiter's panel offers to resume it. Mini and the E-Ink app do not: a
game there does not survive the app being closed.

## Presets

A list of ready-made time controls, on three tabs.

- **All Presets** — the 21 built in, below. Tapping one applies it. If a game is in progress the
  app asks whether to end it.
- **My Presets** — your own. **Save current time control** stores the clock as it is set now, under
  a name. Long-press one to rename or delete it.
- **Last Games** — the time controls of the games most recently played, with their date. **View
  statistics** opens the statistics of the whole history.

Applying a preset changes the time control only. Appearance, sounds, behavior and the notebook are
left as they are.

| Preset | Time control |
|---|---|
| 1 min, 5 min, 10 min, 30 min, 60 min | Sudden Death, that long |
| Fisch. 3 + 2s, Fisch. 15 + 10s | Fischer 3:00 +2 s, 15:00 +10 s |
| 90'/40 + 30' + 30s | FIDE: 90 min for 40 moves, then 30 min, Fischer 30 s |
| 120'/40 + 60'/20 + 15' + 30s | FIDE: 120 min for 40, 60 min for 20, then 15 min, Fischer 30 s from move 61 |
| US 5 + 2s, US 25 + 5s | Sudden Death 5:00 with a 2 s delay, 25:00 with 5 s |
| US 80'/40 + 30' d30s | FIDE: 80 min for 40 moves, then 30 min, delay 30 s |
| Bronst. 90 + 5s | Bronstein 90:00, delay 5 s |
| Jap. Byo 20', Jap. Byo 60' | Japanese byoyomi: 20 min + 1 × 30 s, 60 min + 3 × 20 s |
| Can. Byo 60' | Canadian byoyomi: 60 min + 5 min for 20 moves |
| Armag. 5 / 4 | Armageddon: White 5:00, Black 4:00 |
| Hourgl. 1 min | Hourglass, 1:00 each |
| 30s / Move | Move Timer Standard, 30 s a move |
| Scrabble 25' | Sudden Death 25:00, flag behavior Reverse |
| Random (1/10) | Random, 1 to 10 min, rounded |

A long press on the app's icon offers up to four shortcuts: the time controls of the two most recent
games first, then your own presets, then a few built-in blitz controls. One opens the app on that time control.

## The arbiter's panel

For corrections during a game: a penalty, time given back, a move miscounted. It opens from the
control bar while the game is paused, over the clock.

For each player it shows the player's name, their clock exactly as their face shows it, and their
move count. Under them:

| Control | What it does |
|---|---|
| **1 min**, with − and + | Takes a minute from that player's clock, or adds one. |
| **1 s**, with − and + | The same, by a second. |
| **Moves**, with − and + | FIDE Periods with the move counter on: corrects the move count. A move that reaches a period's count starts the next period, with its time; a move taken back undoes it. |
| **Save** | Keeps the clocks as they stand, to come back to. |
| **Resume** | Brings the saved clocks back. Shown when there are some. |
| **Back to Game** | Closes the panel. The game stays paused: it is for the players to resume. |

In the modes with one clock for the table there is one set of buttons, named **Clock**. In Move
Counts the time buttons are off: a move count is not a time. In Hidden the reading stays `??:??`.

Time given to a flagged player puts them back in play, and time taken past zero is a flag, or an
overrun under Negative and Reverse. Every correction is written to the game's log.

## In the background

What happens when the app is left is set in [Behavior](behavior.md#interaction): by default the game
pauses. With **Keep running in the background** a notification keeps the clock going and shows whose
turn it is.

The clock's time is measured from the device's own clock, so it is right when the app comes back,
whatever happened meanwhile. A sound due while the screen is off can come late.

## Three and four players

With **Enable more players** ([Behavior](behavior.md#advanced), experimental) the screen is split
in three or four faces. With three, **Single player at top** chooses whether the lone face is at the
top or at the bottom. A drawing of the screen in the settings shows who sits where and lets the
seats be changed.

Turns go round the table. Under the Flag behavior a player who is out is skipped, and the game goes
on until one is left.

## The E-Ink app

A separate app for E Ink devices, black on white, with nothing that animates. Its clock has the two
faces and three buttons between them: **Reset**, **Start** / **Pause** / **Resume**, and
**Settings**. Reset asks first.

Its settings are one page:

| Setting | Default | |
|---|---|---|
| Mode: **Timer**, **Fischer**, **Move** | Timer | Sudden Death, Fischer, Move Timer Standard. |
| **Initial time** | 10:00 | Minutes and seconds, up to 60:00. Timer and Fischer. |
| **Increment** | 0:10 | Fischer. |
| **Move time** | 0:30 | Move. |
| **Reverse colors** | off | White on black. |
| **Keep screen awake** | on | |
| **Fullscreen** | off | Hides the status bar. |
| **Sound** | off | A sound at each move. |
| **Haptic feedback** | off | A vibration at each move. |

It has no presets, no arbiter's panel, no history and no autosave.
