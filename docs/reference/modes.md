# Timing modes

Set under **Settings › Modes**. Fourteen modes, several with variants chosen just below the mode
grid, and Omni-Timer, a clock of its own. Mode names are in English in every language.

The six most played are always in sight: Sudden Death, Bonus, FIDE Periods, Byoyomi, Move Timer and
Hourglass. **More modes** opens the others, and stays open while one of them is the mode in use.
Mini has three modes and no such line. This page follows the order of the grid.

- [Before the modes: presses, players, and what happens at zero](#before-the-modes)
- [Sudden Death](#sudden-death) · [Bonus](#bonus) · [FIDE Periods](#fide-periods) · [Byoyomi](#byoyomi) · [Move Timer](#move-timer) · [Hourglass](#hourglass)
- [Move Counts](#move-counts) · [Gong](#gong) · [Chronos](#chronos) · [Phases](#phases) · [Fast Move](#fast-move) · [Staircase](#staircase) · [Random](#random) · [Hidden](#hidden)
- [Omni-Timer](#omni-timer): [the seven steps](#step-1--players--order) · [the Omni clock](#the-omni-clock) · [at zero](#what-happens-at-zero) · [leaving and coming back](#leaving-and-coming-back)

## Before the modes

### A press

Pressing your own half of the screen ends your move and starts the next player's clock. With
**Trigger on Press** off (Settings › Behavior), it is releasing the finger that counts.

In four situations a press does not pass the turn, because the clock is the table's and not a
player's: Gong, Move Timer **Shared (Auto)**, Phases, and Chronos with **One for All** on.

### Players

Two by default. **Enable more players** ([Behavior](behavior.md#advanced), experimental) allows three
or four.

**Different settings per player** ([Behavior](behavior.md#advanced)) gives each of two players their
own times, on a tab each. The mode is the game's: player 2 plays player 1's mode, and with it Fast
Move's variant, Transfer's cumulative switch and Gong's simultaneous one. It is off with three or four players, and in the modes that run
one clock for the whole table — Shared (Auto), Global Shared, Phases, Chronos — where player 1's
settings become everyone's.

### When a clock reaches zero: Flag Behavior

Settings › Behavior › **Flag Behavior** decides what running out of time means, in every mode.

| Setting | At zero | The game |
|---|---|---|
| **Freeze** (default) | The clock stops at 0:00 and the player is out. | Over as soon as one player is out: every clock stops. |
| **Flag** | The first clock to run out is flagged: that player has lost, and their face turns to the loss colour. | Not over. Turns still alternate, as at a chess clock: the flagged player's clock stays at 0:00 on their turn, and their press starts the next clock. A clock that reaches zero after the first stops at 0:00 without being flagged. The game is over when every clock has run out. |
| **Negative** | The clock goes on below zero, shown with a minus sign. | Not over: play goes on, and the overrun is counted. |
| **Reverse** | The clock counts back up from zero, with no sign. | Not over, as under Negative. This is the Scrabble convention: the overrun is the penalty. |

Under Negative and Reverse, time credited later pays the overrun off first: a Fischer increment
takes an overrun back towards zero, and one larger than the overrun gives the player time again. A
player eliminated under Flag is not brought back by a move.

A game that ends by something other than a flag — Chronos running out, Phases running through, Move
Counts reaching its limit — is over whatever this setting says.

---

## Sudden Death

One clock per player, counting down. No variant.

| Parameter | Default | |
|---|---|---|
| **Initial Time** | 10:00 | Each player's time for the whole game. |

The clock runs during your turn only. Reaching 0:00 is a flag.

## Bonus

A main time, and time given back at each move. **Bonus Type** picks how.

| Parameter | Default | |
|---|---|---|
| **Initial Time** | 10:00 | |
| **Increment / Bonus** | 0:10 | The amount per move: the increment under Fischer, the delay under Bronstein and US Delay. |

- **Fischer** — the increment is added after every move, whatever the move took. With **Fischer
  FIDE (Bonus on move 0)** on (Settings › Behavior › Tournament Rules), the increment is also on
  the clock before the first move, as FIDE counts it.
- **Bronstein** — the time spent on the move is given back, up to the delay and never more. A move
  quicker than the delay leaves the clock exactly where it was; time never accumulates.
- **US Delay** — the clock waits for the delay before it starts counting down. The face shows
  `DELAY` while it runs. A move made inside the delay costs nothing.

## FIDE Periods

Tournament time controls made of periods, such as 90 minutes for 40 moves, then 30 minutes for the
rest, with 30 seconds per move.

Each period has:

| Parameter | Default | |
|---|---|---|
| Kind | Fischer | **Sudden Death** (no bonus), **Fischer** (an increment after each move) or **US Delay** (a delay before each move). |
| **Duration** | 1:30:00 | Time added when the period begins. |
| **Increment** or **Delay** | 0:30 | For Fischer and US Delay periods. |
| **Total moves before period N**; on the last period, **Moves to unlock next (0 = Final)** | 40; 0 on the last | The move count that ends the period. 0 marks the last period, which has none; the one period a new setup starts with is the last. |

**Add Period** adds one. Move counts are totals from the start of the game: 40, then 60, for
"40 moves, then 20 more". A count typed per period instead -- 20 after 40 -- is understood as 60.

A Fischer period's bonus for move 1 is on the clock from the start, and reaching the move count adds
the next period's time together with the bonus for the move to come.

**Forced move counter** (Settings › Behavior › Tournament Rules, on by default) decides how a period
ends:

- **On** — a period ends when its move count is reached. Its time running out before that is a
  flag: the player is out, and the face reads `FLAGGED`. A middle period with no move count still
  ends on its time.
- **Off** — a period ends when its time runs out, and the next one begins; moves are still counted,
  but they end nothing.

Where a period ends on its time and play goes on into the next, the face keeps `PERIOD FLAG` as the
mark of the period that ran out.

Above the clock, the face counts down the moves left to the next period (`MOVES: 12`) while a count
is running, and shows the period (`PERIOD: 2/3`) when none is: in the last period, or with the
counter off. The **Show period** switch of Display is for byoyomi, not for these periods.

## Byoyomi

A main time, then extra time in periods, as in go and shogi. **Byoyomi Type** picks one of three.

| Parameter | Default | |
|---|---|---|
| **Initial Time** | 10:00 | Main time. Byoyomi begins when it is spent. |
| **Byoyomi Time** | 0:30 | The length of one period (Japanese) or of one block (Canadian, Progressive). |
| **Periods / Moves Goal** | 5 | Japanese: how many periods. Canadian and Progressive: how many moves the block must cover. |
| **Progression (moves)** | 5 | Progressive only: how many moves are added to the goal after each block. |

- **Japanese** — a number of periods. A move made inside a period gives the period back whole. A
  period that runs out is lost, and the next one starts; losing the last one is a flag. The face
  shows the period being played (`PERIOD: 2/5`), or with **Show period** off in Display, the periods
  left (`PERIODS: 4`).
- **Canadian** — one block of time for a number of moves. Each move counts off the goal without
  refilling the clock; the last move of the goal refills the block and the count starts again.
  Running out before the goal is met is a flag.
- **Progressive** — Canadian, with the goal growing by the progression each time a block is
  completed: 5 moves, then 10, then 15, in the same block of time.

Zero periods is no byoyomi at all: main time running out is the flag. A goal of zero moves is read
as one.

## Move Timer

A time for each move rather than for the game. **Move Timer Type** picks one of six.

| Variant | Parameters (default) | How it works |
|---|---|---|
| **Standard** | **Move Time** (0:30) | Every move starts with the full move time. Nothing is kept from a quick move. Running out is a flag. |
| **Save & Cap** | **Move Time** (0:30), **Time Cap** (2:00) | What a move does not use is carried to the next, so the clock grows over a run of quick moves — up to the cap, which limits the whole clock and not only the saved part. A turn that ran out saves nothing. A cap below the move time saves nothing rather than shortening the move. |
| **Overtime** | **Move Time** (0:30), a reserve set as **Initial Time** (10:00) | The move clock runs first. Once it is spent, the reserve is burned instead; the face shows it as `OT`. Each move gets a fresh move clock, and the reserve carries over. The flag is the reserve running out, not the move clock. |
| **Global** | **Move Time** (0:30), a total set as **Initial Time** (10:00) | The move clock and the player's total run down together. Either reaching zero is a flag. |
| **Shared (Auto)** | **Move Time** (0:30) | One clock for the table. Nobody presses: when the move time runs out the turn passes by itself and the clock starts again. It ends a move rather than flagging. |
| **Global Shared** | **Move Time** (0:30), a shared reserve set as **Initial Time** (10:00) | Each player has their own move clock; one reserve is shared by all and drained by whoever is on the move. Running out of move time puts that player out; the shared reserve reaching zero puts everyone out at once. |

## Hourglass

What one player's clock loses, the others gain. No variant.

| Parameter | Default | |
|---|---|---|
| **Initial Time** | 10:00 | Each player's starting time. |

With two players the time moves across exactly, so the total on the table never changes. With three
or four, the active player's loss is shared out among the others. A player who is out gives and
receives nothing more. Under Negative and Reverse, time received pays a player's overrun off first.

## Move Counts

A count of moves instead of a time. **Move Counts Type**:

- **Count Up** — shows how many moves the player has made.
- **Count Down (Max)** — starts at **Max Moves** (default 20) and counts down. The game ends once
  every player has played their moves, under every Flag Behavior: a player at zero waits for the
  others' last moves.

## Gong

Forced moves at a fixed rhythm: a time to think, then a short time to move, announced by a gong.

| Parameter | Default | |
|---|---|---|
| **Reflection Time** | 0:10 | Thinking time. The face shows `REFLECT`. |
| **Time to Move** | 0:02 | The window in which the move must be played. |
| **Simultaneous Play** | off | Off: players take turns, each going through reflection then move. On: every player goes through both at the same moment. |

Nobody presses: the phases follow each other by themselves. A phase set to nothing still lasts one
second. The gong is the mode itself, so it sounds whatever the sound settings say.

## Chronos

One clock for the table, not one per player. **Chronos Type**:

- **Countdown** — counts down from **Initial Time** (default 10:00). Reaching zero ends the game for
  everyone; it is nobody's flag.
- **Countup** — a stopwatch, counting up from zero. It never ends by itself.

**One for All (no clicks)**, on by default: nobody is on the move and presses do nothing. Off, a
press still passes the turn and counts a move, while the one clock keeps running.

## Phases

A sequence of timers for the whole table, each with its own length: preparation 5 minutes, game 20
minutes, and so on. One clock, shown to everyone.

Each phase has:

| Parameter | Default | |
|---|---|---|
| **Phase Name** | empty | Shown on the face. |
| **Duration** | 5:00 | |
| **Wait for a tap at the end** | off | The phase stops at 0:00 and waits for a tap instead of moving on by itself. |
| **Flag on end (Defeat)** | off | The phase ending is a flag rather than a step to the next. |

And for the sequence:

| Parameter | Default | |
|---|---|---|
| **Repeat Phases (Loop)** | off | After the last phase, start again at the first. |
| **Pause between phases** | 0:00 | A countdown between two phases. |
| **Pause between phases until a tap** | off | The pause lasts until someone taps, for a break whose length nobody knows beforehand. |
| **Allow manual skip** | off | A tap ends the current phase early. |

Running through the last phase finishes the game without a flag. What a tick ran past the end of a
phase comes off the next, so a sequence keeps its total length.

## Fast Move

Rewards quick moves. **Fast Move Type**:

### Accelerate

One clock per player for the whole game; the longer a move takes, the faster the clock runs.

| Parameter | Default | |
|---|---|---|
| **Initial Time** | 10:00 | |
| **Grace Period (Standard speed)** | 0:05 | At the start of each move the clock runs at normal speed. |
| **Fast Acceleration Rate** | +0.5 sec/sec | After the grace period, each second costs this much more: 1.5 seconds per second. |
| **Full Fast Period starts at** | 0:20 | Once the move has taken this much off the clock, the full rate applies. It is measured on the clock, which is already running fast by then, so it comes sooner than that in real time. Never before the grace period ends. |
| **Full Fast Accel Rate** | +2.0 sec/sec | 3 seconds per second. |

The acceleration starts again with every move.

### Transfer

A move time; what one player spends goes to the next.

| Parameter | Default | |
|---|---|---|
| **Time Per Move** | 0:30 | |
| **Cumulative Transfer** | on | See below. |

- **Off** — your opponent's next move gets the move time plus the time you just spent, and what
  they had left is dropped. Your own next move starts again from the move time.
- **On** — your opponent's next move gets the move time plus the time you just spent, added to what
  they had left, and you keep what you had: 8 s left, a 30 s move time and 3 s spent make 41 s. The
  reserve grows over the game by design; Hourglass is the mode for a fixed total.

With more than two players, the time goes to the player who moves next. Under Flag a player who
has flagged keeps their turn and is given nothing; their own press passes on a move's time and the
time their turn took, as anyone's does.

## Staircase

Each move's time changes by a fixed step. **Staircase Type**:

| Variant | Parameters (default) | |
|---|---|---|
| **Down** | **Starting Move Time** (10:00), **Decrement per move** (0:10), **Minimum Time Floor** (0:05) | Each move gets less time than the last, down to the floor. |
| **Up** | **Starting Move Time** (10:00), **Increase per move** (0:10), **Maximum Time Ceiling** (0:05) | Each move gets more, up to the ceiling. A ceiling below the starting time keeps every move at the starting time: as it is first set, Up does not grow until the ceiling is raised. |

Every move starts with its own time whole; nothing is carried over.

---

## Random

The time is drawn at random within a range, and shown.

| Parameter | Default | |
|---|---|---|
| **Minimum Duration** / **Maximum Duration** | 1:00 / 10:00 | The range of the main time. |
| **Minimum Increment** / **Maximum Increment** | 0:00 / 0:10 | The range of the Fischer increment drawn with it. |
| **Rounded Time (minutes/seconds)** | on | Draws round values: ten-second steps where the range holds one, whole seconds otherwise. |

Both players get the same draw, set from player 1's ranges. The drawn increment is added after each
move, as in Fischer. A range starting at zero never draws a clock of zero.

The die on the clock draws a new time. It restarts the game, so with **Confirm reset** on it asks
first.

## Hidden

Random, with the time kept from the players: the face shows `??:??`.

| Parameter | Default | |
|---|---|---|
| The four ranges and **Rounded Time** | as Random | |
| **Show Percentage Alerts** | on | The share of time left shows for five seconds as it passes 50, 25, 10, 5, 2 and 1 %. |

The low-time flash is off in this mode, since it would give the time away. Once a player is out the
face shows the time again. The arbiter's panel keeps it hidden too.

## Omni-Timer

The fifteenth card of the mode grid, marked **beta**: it works and is tested, and its screens are
still being finished. In Complete, Standard and Lite.

It is not a variant of the chess clock but a clock of its own, for games with a structure: a
**session** made of **games**, each made of **rounds**, each made of **turns**, each of which can
be cut into **phases**. Every level has its own clock, which can be on or off, and the five run
down together.

Tapping the card opens a wizard of seven steps. **Next Step** and **Previous** move through it; the
last step launches the session.

### Step 1 — Players & Order

| Setting | Default | |
|---|---|---|
| **Count** | 2 | The number of players, from 1 to 20. |
| A colour per player | | Each player's colour on the clock, from the same eight colours as Display, or a custom one. |
| **Turn Order** | Linear | Who plays when; see below. |

| Order | With three players, round by round | |
|---|---|---|
| **Linear** | 123, 123, 123 | The same order every round. |
| **Snake** | 123, 321, 123 | Every other round runs backwards. |
| **Rotate** | 123, 231, 312 | The first player moves on by one each round. |
| **Random** | 231, 312, 123 | Drawn. Two ways, below. |

Random has three options of its own:

| Setting | Default | |
|---|---|---|
| **Shuffle per round** / **Draw per turn** | Shuffle per round | Shuffled once per round, everyone plays exactly once per round. Drawn at every turn, a player can be skipped or picked twice. |
| **Never the same player twice in a row** | on | Holds across the end of a round and of a game too. |
| **Balance the draw** | on | With Draw per turn: whoever is behind on turns is likelier to come up next, so gaps close instead of piling up. Off, every player is equally likely every turn, and one of them really can take most of a round. |

### Step 2 — Session & Games

| Setting | Default | |
|---|---|---|
| **Enable Session Timer** | off | A clock for the whole session. **Total Duration**, 5:00:00 by default. |
| **Force cutoff when session time runs out** | off | The session ends the moment its clock reaches zero. |
| **Enable Game Timer** | on | A clock for each game. |
| **Force cutoff when game time runs out** | off | The game ends at zero and the next one begins. |
| Each game | one game | A **Game Name** and a **Duration**, 45:00 by default. **Add Game** adds one; the bin deletes it. |

### Step 3 — Rounds

For each game:

| Setting | Default | |
|---|---|---|
| **Enable Round Timer** | on | A clock for each round. |
| **Force cutoff when round time runs out** | off | The round ends at zero and the next one begins. |
| Each round | one round | A **Round Name** and a **Duration**, 10:00 by default. **Add Round** adds one. |

### Step 4 — Turns

For each round:

| Setting | Default | |
|---|---|---|
| **Enable Turn Timer** | on | A clock for each turn. Off, the step says **Turn clock disabled.** |
| **Force cutoff when turn time runs out** | off | The turn ends at zero and the next player's begins. |
| **End Round** / **Loop Turns** | End Round | End Round: the round ends when its turns have been played. Loop Turns: the turns start over, pass after pass, until the round's clock reaches zero. |
| Each turn | **Turn 1** | A **Duration**, 10:00 by default. **Add Turn** adds one. |

A round holds one turn per player. With one turn set, that is the round. With a second, every
player plays their first turn before anyone plays their second, and so on: three turns for four
players is a round of twelve.

Loop Turns needs the round's clock: it is that clock reaching zero that ends the loop. With the
round timer off, or a round of 0:00, a looping round ends after its turns like any other.

### Step 5 — Phases

| Setting | Default | |
|---|---|---|
| **Enable Phase Timer** | off | Cuts each turn into phases, each with its own clock. Off, the step says **Phase clock disabled.** |
| **Force cutoff when phase time runs out** | off | A phase ends at zero and the next one begins. It never ends the turn: the last phase waits for Next, or for the turn's own cutoff. |
| Each phase | | A **Phase Name** and a **Duration**. **Add Phase** adds one to the turn. |

The step shows the phases' total against the turn's time (**Total / Limit**), and step 7 warns when
the phases of a turn exceed it. A turn with no phases is one phase as long as the turn.

### Step 6 — Advanced Rules

**Transitions & Pauses**

| Setting | Default | |
|---|---|---|
| **Enable Pauses (Buffers)** | off | Puts a pause between turns, between rounds and between games. Turning it on sets them to 0:05, 1:00 and 5:00; turning it off sets them back to nothing. |
| **Turn Pause**, **Round Pause**, **Game Pause** | 0:05, 1:00, 5:00 | The length of each, once pauses are on. A pause of 0:00 is no pause at all. |
| **Automatic Restart** | on | On, play resumes by itself when the pause is over. Off, the pause waits for **I AM READY**. |

**Pressure & Economy**

| Setting | Default | |
|---|---|---|
| **Pause deducts Global**, **Game**, **Round** | off | The session, game or round clock keeps running during the pauses. Off, pauses cost nothing. Only clocks that are on are touched. |
| **Time Bank** | Off | What becomes of the time a turn did not use; see below. |
| **Clear Bank at end of:** | Round | How long banked time lasts. |

**Time Bank.** When a turn ends with time left, that time is banked, and added to a later turn.

- **Off** — nothing is kept; every turn starts with its own time.
- **Per-player** — each player has their own bank, drawn on their next turn.
- **Shared pool** — one bank for the table, drawn by whoever plays next.

The clock shows banked time as **+0:20 in reserve**. A phase ending banks nothing: the turn goes on
with the time it has. The bank is emptied at the end of the level chosen — turn, round, game — or
kept for the whole session. Banked for a turn, the shared pool lasts exactly one: the next turn draws it or it
is gone. A player's own bank waits through the other players' turns for its owner's next one, and is
dropped only if that player ends a turn without having drawn it. With per-player banks, in a round where nobody plays twice, the bank is carried into the next
round even when it is cleared by round, since a player's next turn is always there.

**Audio Alerts**

| Setting | Default | |
|---|---|---|
| **Turn Beep** | on | A beep when a turn's clock, or a phase's, reaches zero. |
| **Round Gong** | on | A gong when a round's clock reaches zero. |
| **Final Beep** | on | A triple beep when a game's clock or the session's reaches zero. |

They use the sounds and the volume set in [Audio](audio.md).

### Step 7 — Ready to Play

| Setting | Default | |
|---|---|---|
| **Launch Countdown** | 10s | **Off**, **10s**, **30s**, **1m**, **2m** or **5m** of **GET READY** before the first turn. No clock runs during it. |

A **Summary** recalls the players, the games, the rounds per game and the order. **Launch Game**,
in the bar at the bottom, starts the session. If one is already under way, the app asks: **RESUME** it as it stands, or start
a **NEW SESSION** with the settings as they are now.

### The Omni clock

| On the screen | |
|---|---|
| **Omni: Round** | The name of the current round, in the title. |
| **Session**, **Game 1/3**, **Round 2/4** | A bar and a time for each level whose clock is on. |
| **PLAYER 2** | Whose turn it is, in that player's colour. |
| The large time | The turn's clock; the phase's name and clock beside it when phases are on. |
| **Next** | Ends the phase, or the turn when it is the last one. |
| **Pause** / play, **Stop the session**, **Reset the session** | At the bottom, with **RUNNING** or **PAUSED**. Stop and Reset ask first. |

Between two turns, rounds or games with a pause set, the screen says **TURN FINISHED**, **ROUND
FINISHED** or **GAME FINISHED**, who is next (**Next: Player 3**, **Prepare for Round 2**), and
either counts the pause down or waits for **I AM READY**. At the end: **SESSION COMPLETE**, and
**CLOSE SESSION**.

### What happens at zero

A clock without a cutoff stops at 0:00, sounds, and waits: the turn, round or game goes on until
someone presses **Next**. A clock with **Force cutoff** moves the session on by itself, cutting
short whatever was in progress inside it. When several reach zero together the outermost wins: the
session before the game, the game before the round, the round before the turn.

With a cutoff on every level that has a clock, a session runs from start to end with nobody
touching it.

### Leaving and coming back

Leaving the Omni screen pauses the session. It is saved at every pause, at every turn ended and
every fifteen seconds of play, so a session comes back even after Android has closed the app,
paused where it was. With **Keep running in the background** ([Behavior](behavior.md#interaction)) a
running session keeps its notification, as a game does.

Settings changed while a session is paused apply to the rest of it: fewer players, fewer rounds, a
round set to loop. The session keeps its place where the new structure still has one, and ends
where it does not. **NEW SESSION** is the way to start over with the new settings.

Berserk is an option of the game rather than a mode: see [Behavior](behavior.md#berserk).
