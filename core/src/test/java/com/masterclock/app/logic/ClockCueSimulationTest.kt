package com.masterclock.app.logic

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * What the sound and the voice are told, checked on every tick of thousands of random games.
 *
 * The clock cannot be heard from a test, but everything it says goes through the cue a tick hands
 * over. Most of its silent flags were a flag falling on a path that built no cue: the player learned
 * of it from the screen, and the log never had it. So each rule here is about the cue, not the sound.
 */
class ClockCueSimulationTest {

    @Test
    fun `no random game lets a flag fall in silence`() {
        val failures = linkedMapOf<String, String>()
        val r = Random(20261107L)
        repeat(GAMES) {
            val settings = randomSimSettings(r)
            val sim = ClockSim(settings)
            val played = mutableListOf<SimAction>()
            for (i in 0 until ACTIONS_PER_GAME) {
                val action = randomSimAction(r, sim)
                played += action
                val before = sim.state
                action.applyTo(sim)
                if (action !is SimAction.Tick) continue
                val broken = check(before, sim, settings) ?: continue
                failures.getOrPut(broken) {
                    "$broken\n  ${settings.main.mode} ${settings.flagBehavior} players=${settings.numberOfPlayers} " +
                        "oneForAll=${settings.isOneForAll}\n  last actions: ${played.takeLast(6)}"
                }
                break
            }
        }
        assertTrue(failures.values.joinToString("\n\n"), failures.isEmpty())
    }

    /** The rule this tick breaks, or null. */
    private fun check(before: ChessClockState, sim: ClockSim, settings: ChessClockSettings): String? {
        val tick = sim.lastTick ?: return null
        val active = before.activePlayer ?: return null
        val mode = settings.playerSettings(active).mode
        // PHASES speaks through its transition, and a tick that changed none has nothing to say.
        if (mode == TimerMode.PHASES) return null
        val fell = before.players[active - 1].isOutOfTime.not() && tick.state.players[active - 1].isOutOfTime
        val cue = tick.cue
        return when {
            fell && cue == null -> "a flag fell with no cue"
            fell && cue?.after?.isOutOfTime != true -> "a flag fell and the cue says otherwise"
            // handleAudio sounds and logs a flag only as it falls: a cue whose before was already out
            // is silent too.
            fell && cue?.before?.isOutOfTime == true -> "a flag fell and the cue says it had already"
            cue != null && cue.playerIndex != active && tick.coupled == null -> "a cue named the wrong player"
            else -> null
        }
    }

    private companion object {
        const val GAMES = 6_000
        const val ACTIONS_PER_GAME = 160
    }
}
