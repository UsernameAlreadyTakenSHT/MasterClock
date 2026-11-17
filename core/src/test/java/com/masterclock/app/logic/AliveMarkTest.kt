package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The mark a running clock leaves of when it was last alive, and the clock a saved game is run on to by it. */
class AliveMarkTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun running(vararg times: Long) = ChessClockState(
        players = times.map { PlayerState(timeRemainingMs = it) },
        activePlayer = 1,
        isPaused = false,
    )

    @Test
    fun `a mark is read back as written, the last one written`() {
        val file = folder.newFile("alive_mark")
        val mark = AliveMark(file)
        mark.write(saveId = 1_700_000_000_000, aliveAtElapsedMs = 5_000)
        mark.write(saveId = 1_700_000_000_000, aliveAtElapsedMs = 5_200)
        // By another object, as after the app was closed and opened again.
        assertEquals(AliveMark.Reading(1_700_000_000_000, 5_200), AliveMark(file).read())
        mark.close()
    }

    @Test
    fun `no file, a short one and a mark caught half written are no mark`() {
        assertNull(AliveMark(java.io.File(folder.root, "absent")).read())
        val file = folder.newFile("alive_mark")
        assertNull(AliveMark(file).read())
        file.writeBytes(ByteArray(10))
        assertNull(AliveMark(file).read())
        // A whole mark, then its moment changed without the number that vouches for it.
        AliveMark(file).apply { write(7, 1_000); close() }
        val bytes = file.readBytes()
        bytes[15] = (bytes[15] + 1).toByte()
        file.writeBytes(bytes)
        assertNull(AliveMark(file).read())
    }

    @Test
    fun `the clock ran on from its save to its mark, and only by a mark of that save`() {
        val mark = AliveMark.Reading(saveId = 42, aliveAtElapsedMs = 61_800)
        assertEquals(1_800L, aliveGapMs(saveId = 42, savedAtElapsedMs = 60_000, wasRunning = true, mark = mark))
        // A clock saved paused has nothing to run on to.
        assertEquals(0L, aliveGapMs(42, 60_000, wasRunning = false, mark = mark))
        // Another save's mark: a move was played since, or it is another game.
        assertEquals(0L, aliveGapMs(43, 60_000, wasRunning = true, mark = mark))
        assertEquals(0L, aliveGapMs(42, 60_000, wasRunning = true, mark = null))
        // A save from before there were marks.
        assertEquals(0L, aliveGapMs(42, 0, wasRunning = true, mark = mark))
        // A mark from before its save, or further off than any save interval explains.
        assertEquals(0L, aliveGapMs(42, 70_000, wasRunning = true, mark = mark))
        assertEquals(0L, aliveGapMs(42, 60_000, wasRunning = true, mark = AliveMark.Reading(42, 60_000 + MAX_ALIVE_GAP_MS + 1)))
        assertEquals(MAX_ALIVE_GAP_MS, aliveGapMs(42, 60_000, wasRunning = true, mark = AliveMark.Reading(42, 60_000 + MAX_ALIVE_GAP_MS)))
    }

    @Test
    fun `run on, the clock is the one the ticker would have left`() {
        val settings = ChessClockSettings()
        val saved = running(60_000, 60_000)
        // In one step or tick by tick, the same clock.
        var ticked = saved
        repeat(18) { ticked = tickClock(ticked, 100, settings).state }
        val ranOn = advanceClock(saved, 1_800, settings)
        assertEquals(ticked, ranOn)
        assertEquals(58_200L, ranOn.players[0].timeRemainingMs)
        assertEquals(60_000L, ranOn.players[1].timeRemainingMs)
    }

    @Test
    fun `nothing to run on leaves the clock as saved`() {
        val settings = ChessClockSettings()
        val saved = running(60_000, 60_000)
        assertSame(saved, advanceClock(saved, 0, settings))
        val paused = saved.copy(isPaused = true)
        assertSame(paused, advanceClock(paused, 1_800, settings))
        val unstarted = saved.copy(activePlayer = null)
        assertSame(unstarted, advanceClock(unstarted, 1_800, settings))
    }

    @Test
    fun `a flag that fell before the app was closed has fallen when the game comes back`() {
        val settings = ChessClockSettings()
        val ranOn = advanceClock(running(1_000, 60_000), 1_800, settings)
        assertEquals(0L, ranOn.players[0].timeRemainingMs)
        assertEquals(true, ranOn.players[0].isOutOfTime)
    }
}
