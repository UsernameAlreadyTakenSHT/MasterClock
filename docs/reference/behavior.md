# Settings › Behavior

How the game itself behaves, whatever the mode. In Complete and Standard.

- [Flag Behavior](#flag-behavior) · [Tournament Rules](#tournament-rules) · [Interaction](#interaction) · [Berserk](#berserk) · [Advanced](#advanced)

## Flag Behavior

What a clock reaching zero means. One choice of four; *Freeze* by default.

| Setting | At zero | The game |
|---|---|---|
| **Freeze** | The clock stops at 0:00 and the player is out. | Over as soon as one player is out: every clock stops. |
| **Flag** | The first clock to run out is flagged: that player has lost, and their face turns to the loss colour. | Not over. Turns still alternate, as at a chess clock: the flagged player's clock stays at 0:00 on their turn, and their press starts the next clock. A clock that reaches zero after the first stops at 0:00 without being flagged. The game is over when every clock has run out. |
| **Negative** | The clock goes on below zero, with a minus sign. | Not over: play goes on and the overrun is counted. |
| **Reverse** | The clock counts back up from zero, with no sign. | Not over, as under Negative. |

It applies in every mode, and [Modes](modes.md#when-a-clock-reaches-zero-flag-behavior) says how
each one counts an overrun.

## Tournament Rules

Two switches, each for one mode only.

| Setting | Default | Applies to | |
|---|---|---|---|
| **Fischer FIDE (Bonus on move 0)** | off | Bonus › Fischer | On, the increment is on the clock before the first move, as FIDE counts it: 3+2 starts at 3:02. Off, it is first added after move 1. |
| **Forced move counter** | on | FIDE Periods | On, a period ends when its move count is reached, and its time running out first is a flag. Off, a period ends when its time runs out and no moves are counted. |

## Interaction

| Setting | Default | |
|---|---|---|
| **Confirm reset** | on | Reset asks "Reset Game?" before clearing a game. It covers the die of Random and Hidden too, which restarts the game. |
| **Trigger on Press (else on Release)** | on | On, a move ends when the finger touches the face. Off, when it lifts. |
| **Pause when in background** | on | Leaving the app, or the screen turning off, pauses the game. |
| **Keep running in the background** | off | Available once the pause above is off. A notification keeps the clock going while the app is in the background, so that Android does not close it. It shows whose turn it is and their time, with a Pause button. |

With both of the last two off, the game goes on in the background without a notification, and
Android may close the app; the time is still right when it comes back, since it is measured from the
device's own clock.

Android 13 and later ask for permission to show the notification when **Keep running in the
background** is first turned on. Refused, the clock still runs in the background; only the
notification is hidden.

## Berserk

**Allow Berserk**, off by default. An option of the game, not a timing mode. It is offered in Sudden
Death and in Fischer only.

Before their first move, a player can give up half their time and their increment for the game. A
win is then worth one extra point, from 7 moves on. The clock asks first, and says what it costs:
"Half their time and their increment go, for this game. It cannot be undone."

- It costs the player who chose it only; the other keeps their time and increment.
- It is refused unless the clock can pay it: no clock starts at zero.
- At 1+2, where halving would leave nothing to play with, it keeps the minute and takes the
  increment.
- With **Fischer FIDE** on, the bonus given before the first move goes with the increment.
- The second player can still go Berserk once the first has moved. It cannot be done after one's own
  first move, nor twice.
- A clock with no time of its player's own — a shared one — has none to give.

A saved game remembers it, and replaying a game starts from the clock Berserk left.

## Advanced

| Setting | Default | |
|---|---|---|
| **Different settings per player** | off | The two players play the same mode with different times, set on a tab each in Modes. Off again, both play player 1's. Not available with three or four players, nor in the modes with one clock for the table. |
| **Enable more players (experimental)** | off | Three or four players instead of two. Turning it on turns per-player settings off. |

With more players on, two more settings appear:

| Setting | Default | |
|---|---|---|
| **Players** | 3 | Three or four. |
| **Single player at top** | on | With three players: the lone face is at the top, the other two side by side below. Off, the other way round. |

And a section **Visuals**: a drawing of the clock as it will be laid out, showing where each player
sits.
