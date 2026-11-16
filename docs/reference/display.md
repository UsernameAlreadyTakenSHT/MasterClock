# Settings › Display

How the clock looks. In Complete and Standard.

- [Time Format](#time-format) · [Information](#information) · [Layout & Scale](#layout--scale) · [Theme](#theme) · [Colours](#colours)

## Time Format

| Setting | Default | |
|---|---|---|
| **Always show hours** | off | On, the hours are shown even when they are zero: `0:09:58`. Off, they appear only from one hour up. |
| **Always show minutes** | on | On, the minutes are shown even under a minute: `0:42`. Off, the last minute shows seconds alone: `42`. |
| **Leading zeros** | `01:09:08` | How each unit is written. `01:09:08` pads every unit to two digits. `1:09:08` leaves the first one as it is. `1:9:8` pads nothing. |
| **Show tenths of seconds** | on, under 10 s | Tenths appear below a threshold: the last **10s**, **20s**, **30s**, **60s**, or **All** the time. |
| **Show hundredths** | off | Available with tenths on. Hundredths instead of tenths, either wherever tenths would show (**Like tenths**) or only in the last ten seconds (**Under 10s**). |

The clock updates as often as the smallest digit on show needs, and no more often.

## Information

| Setting | Default | |
|---|---|---|
| **Show period (1/3)** | on | Japanese byoyomi: on, the face shows the period being played and how many there are (`PERIOD: 2/5`); off, the periods left (`PERIODS: 4`). It does not change FIDE Periods. |
| **Always show moves** | off | The number of moves each player has made, on the face, in every mode. |

## Layout & Scale

| Setting | Default | |
|---|---|---|
| **Active side bigger** | off | The face of the player on the move takes more of the screen than the other. |
| **Orientation** | Vert | **Vert**: the two faces one above the other, the top one upside down, for players across the phone. **Left** and **Right**: the clock turned a quarter turn, for a phone lying on its side, the control bar with it. |
| **Flash on low time (<10s)** | off | The active face flashes in its last ten seconds. Never in Hidden, where it would give the time away, nor on a count-up. |
| **Keep screen awake** | on | The screen does not turn off while the app is open. |
| **Force full brightness** | off | The screen is at full brightness while the app is open, whatever the system setting. |
| **Fullscreen** | off | Hides the status bar and the navigation bar. |

## Theme

**Light**, **Dark**, or **Auto**, which follows the system. *Auto* by default. It colours the
settings and the dialogs; the clock's own colours are below.

## Colours

Each is one of eight ready colours — green, blue, red, yellow, orange, black, grey, white — or a
custom one, set by its red, green and blue values.

| Setting | Default | Where |
|---|---|---|
| **Active side background** | green | The face of the player on the move. |
| **Inactive side background** | grey | The face of a player waiting. |
| **Active clock text** | white | The time on the active face. |
| **Inactive clock text** | white | The time on a waiting face. |
| **Label text color (Moves, Periods, Delay)** | white | The small labels on a face. |
| **Alert text color (FLAGGED, MOVE!)** | white | The alerts on a face. |
| **Loss state background** | red | The face of a player who is out of time. |
| **Gong: Reflection background** | blue | Gong: the face during reflection time, the active colour being the time to move. |
