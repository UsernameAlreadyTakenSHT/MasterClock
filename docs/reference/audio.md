# Settings › Audio

What the clock sounds and feels like. In Complete and Standard. Everything is off on a fresh
install: the clock is silent until it is asked not to be.

- [Sound](#sound) · [Timer Alerts](#timer-alerts) · [Haptics](#haptics) · [Output & Volume](#output--volume) · [Sound Previews](#sound-previews) · [Custom Sounds](#custom-sounds)

## Sound

| Setting | Default | |
|---|---|---|
| **Play sound on player switch** | off | A click each time a move ends and the turn passes. |
| **Voice announcements** | off | The clock speaks, in the app's language, with the device's text-to-speech voice. |
| **Spoken byoyomi** | off | A Japanese byoyomi period is read aloud as a shogi timekeeper reads it. A switch of its own: it speaks with the announcements off, and then also says "Entering byoyomi" and "Periods left: 2". |

What the voice says:

| Announcement | When |
|---|---|
| "One minute remaining", "Thirty seconds remaining", "Ten seconds remaining" | The clock of the player on the move passes those marks. |
| "Time is up for player 2" | A player runs out of time. |
| "Period 2" | FIDE Periods: a new period begins. |
| "Entering byoyomi", "Periods left: 2" | Byoyomi: main time is spent; a period is lost. |
| "Reflect", "Move" | Gong: each phase begins. |
| "Phase 2" | Phases: a new phase begins. |
| "Finished" | The game ends without a flag. |

What Spoken byoyomi says, in [Byoyomi](modes.md#byoyomi)'s Japanese type. "Entering byoyomi" as main time
is spent and "Periods left: 2" as a period goes, then each period:

| Period | Read aloud |
|---|---|
| 1:00 | "Thirty seconds", "forty seconds", "fifty seconds", then "one, two ... nine". |
| 0:30 | "Ten seconds", "twenty seconds", then "one, two ... nine". |
| Any other | The tens of its last half minute, then one to nine over its last ten seconds. Under ten seconds, a count from its start. |

It is the time gone, not the time left, as at a shogi board, and ten is never called: to hear it
is to have lost on time, which here is the period going or the flag. While it reads a period the
announcements leave "thirty seconds remaining" and "ten seconds remaining" unsaid. The numbers are
the voice's own, so they come a moment after the second they belong to, by as much as the device's
speech engine takes to start.

If the device has no text-to-speech voice in the app's language or in English, the app says so
instead of staying silent: one has to be installed in the device's settings.

## Timer Alerts

| Setting | Default | |
|---|---|---|
| **Countdown beep** | off | A beep each second at the end of the clock, over the last **3s** or **10s**. |
| **Triple beep when times up** | off | Three beeps when a clock reaches zero, and once more when an overrun reaches its [Flag Cap](behavior.md). |

In the modes with two clocks — Overtime, Global, Global Shared — the countdown follows whichever
runs out first.

## Haptics

| Setting | Default | |
|---|---|---|
| **Haptic feedback on switch** | off | A vibration at each press that ends a move. A press that does nothing does not vibrate. |
| **Haptic countdown** | off | A vibration each second at the end of the clock, over the last **3s** or **10s**. |

## Output & Volume

| Setting | Default | |
|---|---|---|
| **Use Media Output (Stream)** | on | On, the clock's sounds play as media: at the media volume, and through headphones or a speaker when one is connected. Off, they play as system sounds, at the volume of the device's own notifications and alerts. |
| **Sounds Volume** | 70 % | The volume of the clock's own sounds, within the device's. |
| **Voice Volume** | 70 % | The volume of the voice announcements. |

## Sound Previews

Five buttons that play each sound as it is set now: **Switch**, **Beep**, **Gong**, **Final**, and
**Voice Announcement**, which says "Testing voice volume".

## Custom Sounds

Each of the four sounds can be replaced by a file from the device:

| Sound | Used for |
|---|---|
| **Beep (countdown)** | The countdown beep. |
| **Gong (mode gong)** | The gong of Gong mode. |
| **Final beep (Time up)** | The triple beep. |
| **Switch (player move)** | The sound of a move. |

Each line says **Using default sound** or **Custom sound selected**, with a button to choose a
file, change it, or clear it and go back to the default. The file has to be one the app can keep
access to after a restart; one it cannot is refused, with a message, rather than accepted and lost
the next day. A file the device cannot play as a sound is not silent: the default sound
plays in its place, while the line goes on reading **Custom sound selected**.

The gong of Gong mode is the mode itself, so it sounds whatever the switches above say.
