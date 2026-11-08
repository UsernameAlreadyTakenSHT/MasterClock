package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * What the clock sounds on its own: the flag, a period run out, PHASES changing stage, the countdown.
 *
 * Every sound is off until asked for, and five beeps -- a FIDE period, a byoyomi period, and three
 * PHASES stages -- sounded whatever the settings said. These go through [cueSound] and [phaseSound],
 * which the ViewModel plays exactly as they answer; the speaker itself cannot be heard from here.
 */
class ClockSoundTest {

    private val silent = ChessClockSettings()
    private val timeUp = ChessClockSettings(tripleBeepTimeUp = true)

    private fun cue(before: PlayerState, after: PlayerState, s: ChessClockSettings, mode: TimerMode = TimerMode.FIDE_PERIODS) =
        cueSound(before, after, s, mode, lastBeepSecond = -1)

    @Test
    fun `the defaults make no sound at all`() {
        assertEquals(false, silent.tripleBeepTimeUp)
        assertEquals(BeepCountdownThreshold.OFF, silent.beepThreshold)
        assertEquals(false, silent.playSwitchSound)
    }

    @Test
    fun `a FIDE period run out beeps only under the time-up switch`() {
        val before = PlayerState(100, currentPeriodIndex = 0)
        val after = PlayerState(60_000, currentPeriodIndex = 1)
        assertNull(cue(before, after, silent))
        assertEquals(ClockSound.SHORT_BEEP, cue(before, after, timeUp))
    }

    @Test
    fun `a byoyomi period lost beeps only under the time-up switch`() {
        val before = PlayerState(100, isInByoyomi = true, byoyomiPeriodsRemaining = 3)
        val after = PlayerState(30_000, isInByoyomi = true, byoyomiPeriodsRemaining = 2)
        assertNull(cue(before, after, silent, TimerMode.BYOYOMI_JAPANESE))
        assertEquals(ClockSound.SHORT_BEEP, cue(before, after, timeUp, TimerMode.BYOYOMI_JAPANESE))
    }

    @Test
    fun `the flag is the triple beep, under the same switch`() {
        val before = PlayerState(100)
        val after = PlayerState(0, isOutOfTime = true)
        assertNull(cue(before, after, silent))
        assertEquals(ClockSound.TRIPLE_BEEP, cue(before, after, timeUp))
    }

    @Test
    fun `the countdown follows its own threshold, not the time-up switch`() {
        val before = PlayerState(3_100)
        val after = PlayerState(2_900)
        assertNull(cue(before, after, timeUp, TimerMode.SUDDEN_DEATH))
        val counting = ChessClockSettings(beepThreshold = BeepCountdownThreshold.THREE_SEC)
        assertEquals(ClockSound.SHORT_BEEP, cue(before, after, counting, TimerMode.SUDDEN_DEATH))
        assertNull("each second once", cueSound(before, after, counting, TimerMode.SUDDEN_DEATH, lastBeepSecond = 2))
        assertNull("Hidden counts nothing down", cue(before, after, counting, TimerMode.HIDDEN))
    }

    @Test
    fun `each PHASES stage beeps only under the time-up switch`() {
        val running = PlayerState(10_000)
        val stages = listOf(
            "a phase over, awaiting its tap" to (running to ChessClockState(players = listOf(PlayerState(0, isPhaseAwaitingTap = true)))),
            "the pause after a phase" to (running to ChessClockState(players = listOf(PlayerState(0, isInInterPhasePause = true)))),
            "the next phase" to (running to ChessClockState(players = listOf(PlayerState(60_000, currentPhaseIndex = 1)))),
            "the last phase's end" to (running to ChessClockState(players = listOf(PlayerState(0, isOutOfTime = true)), phasesFinished = true)),
        )
        for ((name, change) in stages) {
            assertNull("$name sounded with every sound off", phaseSound(change.first, change.second, silent))
            assertTrue("$name made no sound under the switch", phaseSound(change.first, change.second, timeUp) != null)
        }
    }

    @Test
    fun `a stage reached from a pause or a tap does not beep twice`() {
        val next = ChessClockState(players = listOf(PlayerState(60_000, currentPhaseIndex = 1)))
        assertNull(phaseSound(PlayerState(0, isInInterPhasePause = true), next, timeUp))
        assertNull(phaseSound(PlayerState(0, isPhaseAwaitingTap = true), next, timeUp))
    }

    @Test
    fun `no random game makes a sound with every sound off`() {
        val heard = mutableMapOf<String, String>()
        var heardWithSwitch = 0
        val r = Random(20261108L)
        repeat(GAMES) {
            val base = randomSimSettings(r)
            for (s in listOf(base.copy(tripleBeepTimeUp = false, beepThreshold = BeepCountdownThreshold.OFF), base.copy(tripleBeepTimeUp = true))) {
                val sounds = play(ClockSim(s), Random(r.nextLong()))
                if (!s.tripleBeepTimeUp && sounds.isNotEmpty()) heard.getOrPut(sounds.first()) { "${s.main.mode} ${s.flagBehavior}: ${sounds.first()}" }
                if (s.tripleBeepTimeUp) heardWithSwitch += sounds.size
            }
        }
        assertTrue(heard.values.joinToString("\n"), heard.isEmpty())
        // Or the silence above proves nothing: the games do reach flags, periods and phases.
        assertTrue(heardWithSwitch > 0)
    }

    /** What one game sounds, played as the ViewModel plays it: each tick's cue and phase, and a press's flag. */
    private fun play(sim: ClockSim, r: Random): List<String> {
        val sounds = mutableListOf<String>()
        var lastBeepSecond = -1L
        repeat(ACTIONS_PER_GAME) {
            val action = randomSimAction(r, sim)
            val before = sim.state
            action.applyTo(sim)
            val tick = sim.lastTick
            if (action is SimAction.Tick && tick != null) {
                tick.cue?.let { c ->
                    cueSound(c.before, c.after, sim.settings, sim.settings.playerSettings(c.playerIndex).mode, lastBeepSecond)?.let { sounds += "cue $it" }
                    lastBeepSecond = c.after.timeRemainingMs / 1000
                }
                tick.phaseChangedFrom?.let { from ->
                    lastBeepSecond = -1
                    phaseSound(from, tick.state, sim.settings)?.let { sounds += "phase $it" }
                    // A phase run out that is not the last goes to handleAudio, as a flag.
                    val after = tick.state.players[0]
                    if (after.isOutOfTime && !from.isOutOfTime && !tick.state.phasesFinished) {
                        cueSound(from, after, sim.settings, TimerMode.PHASES, -1)?.let { sounds += "phase flag $it" }
                    }
                }
            }
            if (action is SimAction.Press) {
                val p = action.player - 1
                val was = before.players.getOrNull(p)
                val now = sim.state.players.getOrNull(p)
                if (was != null && now != null && !was.isOutOfTime && now.isOutOfTime) {
                    cueSound(was, now, sim.settings, sim.settings.playerSettings(action.player).mode, lastBeepSecond)?.let { sounds += "press $it" }
                }
            }
        }
        return sounds
    }

    private companion object {
        const val GAMES = 3_000
        const val ACTIONS_PER_GAME = 160
    }
}
