package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * What the clock sounds on its own: the flag, a period run out, PHASES changing stage, the countdown,
 * Gong's gong and Shared (Auto)'s handover.
 *
 * Every sound is off until asked for, and five beeps -- a FIDE period, a byoyomi period, and three
 * PHASES stages -- sounded whatever the settings said. A tick's sounds are [tickSounds]', a tap's in
 * PHASES [phaseTransitionSounds]', a flag on a press [cueSound]'s, and the ViewModel plays exactly what
 * they answer, so these games call them rather than a copy. The speaker itself cannot be heard from
 * here. The gong is the one sound with no switch: it is Gong.
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
        val expected = listOf(ClockSound.SHORT_BEEP, ClockSound.SHORT_BEEP, ClockSound.SHORT_BEEP, ClockSound.TRIPLE_BEEP)
        for ((stage, sound) in stages.zip(expected)) {
            val (name, change) = stage
            assertNull("$name sounded with every sound off", phaseSound(change.first, change.second, silent))
            assertEquals(name, sound, phaseSound(change.first, change.second, timeUp))
        }
    }

    @Test
    fun `a stage reached from a pause or a tap does not beep twice`() {
        val next = ChessClockState(players = listOf(PlayerState(60_000, currentPhaseIndex = 1)))
        assertNull(phaseSound(PlayerState(0, isInInterPhasePause = true), next, timeUp))
        assertNull(phaseSound(PlayerState(0, isPhaseAwaitingTap = true), next, timeUp))
    }

    @Test
    fun `a phase that flags before the last sounds as a flag`() {
        val flagged = ChessClockState(players = listOf(PlayerState(0, isOutOfTime = true)))
        assertEquals(listOf(ClockSound.TRIPLE_BEEP), phaseTransitionSounds(PlayerState(1_000), flagged, timeUp))
        assertEquals(emptyList<ClockSound>(), phaseTransitionSounds(PlayerState(1_000), flagged, silent))
    }

    @Test
    fun `Shared (Auto)'s handover follows the switch sound, and the gong follows nothing`() {
        val state = ChessClockState(players = listOf(PlayerState(10_000), PlayerState(10_000)), activePlayer = 2)
        val handover = ClockTick(state, coupled = CoupledTick(state, handedOverBy = 1))
        assertEquals(emptyList<ClockSound>(), tickSounds(handover, silent, -1))
        assertEquals(listOf(ClockSound.SHORT_BEEP), tickSounds(handover, silent.copy(playSwitchSound = true), -1))
        val gong = ClockTick(state, coupled = CoupledTick(state, playGong = true))
        assertEquals(listOf(ClockSound.GONG), tickSounds(gong, silent, -1))
    }

    @Test
    fun `no random game makes a sound with every sound off, and every one is heard with the switch on`() {
        val broken = linkedMapOf<String, String>()
        val reached = mutableMapOf<String, Int>().withDefault { 0 }
        val r = Random(20261108L)
        repeat(GAMES) {
            // The countdown is off in both: it has a switch of its own, the threshold, pinned above.
            val base = randomSimSettings(r).copy(beepThreshold = BeepCountdownThreshold.OFF)
            val silentGame = base.copy(tripleBeepTimeUp = false, playSwitchSound = false)
            val seed = r.nextLong()
            for (s in listOf(silentGame, base.copy(tripleBeepTimeUp = true))) {
                for (failure in play(ClockSim(s), Random(seed), reached)) {
                    broken.getOrPut(failure) { "$failure\n  ${s.main.mode} ${s.flagBehavior} players=${s.numberOfPlayers}" }
                }
            }
        }
        assertTrue(broken.values.joinToString("\n\n"), broken.isEmpty())
        // Or the rules above proved nothing: the games do reach each of these.
        for (kind in listOf("flag", "FIDE period", "byoyomi period", "phase stage")) {
            assertTrue("no game reached a $kind", reached.getValue(kind) > 0)
        }
    }

    /**
     * Plays one game and returns the rules its sounds broke. The ViewModel plays a tick's [tickSounds],
     * a PHASES tap's [phaseTransitionSounds] and a flag on a press [cueSound]; so does this, with the
     * second last counted down left out, the countdown being off.
     */
    private fun play(sim: ClockSim, r: Random, reached: MutableMap<String, Int>): List<String> {
        val s = sim.settings
        val switchOn = s.tripleBeepTimeUp
        val broken = mutableListOf<String>()
        fun heard(sounds: List<ClockSound>, mode: TimerMode, what: String) {
            if (ClockSound.GONG in sounds && mode != TimerMode.GONG) broken += "a gong outside Gong ($what)"
            if (!switchOn && sounds.any { it != ClockSound.GONG }) broken += "a sound with every sound off ($what: $sounds)"
        }
        fun count(kind: String) { if (switchOn) reached[kind] = reached.getValue(kind) + 1 }
        repeat(ACTIONS_PER_GAME) {
            val action = randomSimAction(r, sim)
            val before = sim.state
            action.applyTo(sim)
            val tick = sim.lastTick
            if (action is SimAction.Tick && tick != null) {
                val sounds = tickSounds(tick, s, lastBeepSecond = -1)
                val mode = s.playerSettings(tick.cue?.playerIndex ?: before.activePlayer ?: 1).mode
                heard(sounds, mode, "tick")
                tick.cue?.let { c ->
                    val period = c.after.currentPeriodIndex > c.before.currentPeriodIndex
                    val byoyomi = byoyomiPeriodLost(c.before, c.after)
                    val flag = c.after.isOutOfTime && !c.before.isOutOfTime
                    if (period) count("FIDE period")
                    if (byoyomi) count("byoyomi period")
                    if (flag) count("flag")
                    if (switchOn && (period || byoyomi) && !flag && ClockSound.SHORT_BEEP !in sounds) broken += "a period ran out unheard"
                    if (switchOn && flag && ClockSound.TRIPLE_BEEP !in sounds) broken += "a flag fell unheard"
                }
                if (tick.phaseChangedFrom != null) count("phase stage")
            }
            if (action is SimAction.Press) {
                val mode = s.playerSettings(action.player).mode
                if (mode == TimerMode.PHASES) {
                    if (sim.state != before) heard(phaseTransitionSounds(before.players[0], sim.state, s), mode, "tap")
                } else {
                    val was = before.players.getOrNull(action.player - 1)
                    val now = sim.state.players.getOrNull(action.player - 1)
                    if (was != null && now != null && !was.isOutOfTime && now.isOutOfTime) {
                        heard(listOfNotNull(cueSound(was, now, s, mode, lastBeepSecond = -1)), mode, "press")
                    }
                }
            }
        }
        return broken
    }

    private companion object {
        const val GAMES = 3_000
        const val ACTIONS_PER_GAME = 160
    }
}
