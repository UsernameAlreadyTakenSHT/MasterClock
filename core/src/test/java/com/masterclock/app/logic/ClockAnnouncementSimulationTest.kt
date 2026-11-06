package com.masterclock.app.logic

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * What a screen reader is told, checked after every action of thousands of random games.
 *
 * A live region speaks only when its text changes, so a turn that passes without the announcement
 * changing goes by in silence, and nothing on a screen would show it. [ClockAnnouncementTest] pins
 * the cases written down; these rules hold in every mode, flag behaviour and number of players.
 */
class ClockAnnouncementSimulationTest {

    @Test
    fun `no random game leaves a screen reader without its announcement`() {
        val failures = linkedMapOf<String, String>()
        val r = Random(20261106L)
        repeat(GAMES) {
            val settings = randomSimSettings(r)
            val sim = ClockSim(settings)
            val played = mutableListOf<SimAction>()
            for (i in 0 until ACTIONS_PER_GAME) {
                val action = randomSimAction(r, sim)
                played += action
                val before = sim.state
                val wasRunning = sim.running
                action.applyTo(sim)
                val broken = check(before, sim.state, wasRunning, sim.running) ?: continue
                failures.getOrPut(broken) {
                    "$broken\n  ${settings.main.mode} ${settings.flagBehavior} players=${settings.numberOfPlayers} " +
                        "oneForAll=${settings.isOneForAll}\n  last actions: ${played.takeLast(6)}"
                }
                break
            }
        }
        assertTrue(failures.values.joinToString("\n\n"), failures.isEmpty())
    }

    /** The rule [after] breaks, or null. */
    private fun check(before: ChessClockState, after: ChessClockState, wasRunning: Boolean, running: Boolean): String? {
        val said = clockAnnouncement(before)
        val saying = clockAnnouncement(after)
        return when {
            // The first announcement of a game: there was nothing, and a clock is now running.
            before.activePlayer == null && running && saying == null -> "a game started with nothing to say"
            // The turn passing.
            running && wasRunning && before.activePlayer != after.activePlayer && said == saying -> "a turn passed in silence"
            // Coming back from a pause. Not for a player already out of time and playing on, under
            // NEGATIVE: that outranks Paused, because it has to outlast the pause ending a game, so
            // their clock says the same thing stopped or running.
            running && !wasRunning && before.activePlayer != null && said == saying &&
                saying !is ClockAnnouncement.OutOfTime -> "a resume passed in silence"
            saying is ClockAnnouncement.ToMove && saying.playerIndex != after.activePlayer -> "the wrong player was named"
            saying is ClockAnnouncement.ToMove && after.isPaused -> "a stopped clock was announced as running"
            else -> null
        }
    }

    private companion object {
        const val GAMES = 6_000
        const val ACTIONS_PER_GAME = 160
    }
}
