package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSessionTest {

    private var clock = 1_000L

    private class FakeRecorder(val failStart: Boolean = false, val failStop: Boolean = false) : VoiceRecorder {
        var started: String? = null
        var stops = 0
        var released = 0
        override fun start(outputPath: String) { if (failStart) error("busy"); started = outputPath }
        override fun stop() { stops++; if (failStop) error("nothing captured") }
        override fun release() { released++ }
    }

    private class FakePlayer(val failStart: Boolean = false) : VoicePlayer {
        var onDone: (() -> Unit)? = null
        var stopped = 0
        var released = 0
        override fun start(path: String, onDone: () -> Unit) { if (failStart) error("unplayable"); this.onDone = onDone }
        override val positionMs: Int get() = if (released > 0) error("released") else 4_200
        override fun stop() { stopped++ }
        override fun release() { released++ }
    }

    private val recorders = mutableListOf<FakeRecorder>()
    private val players = mutableListOf<FakePlayer>()
    private var nextRecorder: () -> FakeRecorder = { FakeRecorder() }
    private var nextPlayer: () -> FakePlayer = { FakePlayer() }
    private val session = VoiceSession({ nextRecorder().also { recorders += it } }, { nextPlayer().also { players += it } }, { clock })

    @Test
    fun `a take is measured, and stopping twice stops once`() {
        assertTrue(session.startRecording("pending.mp4"))
        assertTrue(session.state.value.recording)
        assertEquals("pending.mp4", recorders.single().started)
        clock += 12_345
        assertEquals(12_345L, session.recordingMs)
        assertEquals(12_345L, session.stopRecording())
        assertFalse(session.state.value.recording)
        assertNull(session.stopRecording())
        assertEquals(1, recorders.single().stops)
        assertEquals(1, recorders.single().released)
        assertEquals(0L, session.recordingMs)
        // Not while one is running.
        assertTrue(session.startRecording("a"))
        assertFalse(session.startRecording("b"))
    }

    @Test
    fun `a recorder that will not start gives the microphone back`() {
        nextRecorder = { FakeRecorder(failStart = true) }
        assertFalse(session.startRecording("pending.mp4"))
        assertFalse(session.state.value.recording)
        assertEquals(1, recorders.single().released)
        assertNull(session.stopRecording())
    }

    @Test
    fun `a take that caught nothing is not kept, and the recorder is released`() {
        nextRecorder = { FakeRecorder(failStop = true) }
        session.startRecording("pending.mp4")
        clock += 300
        assertNull(session.stopRecording())
        assertEquals(1, recorders.single().released)
        assertFalse(session.state.value.recording)
    }

    @Test
    fun `playback ends by itself or when stopped, one recording at a time`() {
        assertTrue(session.startPlayback("take.mp4"))
        assertTrue(session.state.value.playing)
        assertEquals(4_200, session.playbackMs)
        // Another started: the first is stopped and released first.
        assertTrue(session.startPlayback("take.mp4"))
        assertEquals(1, players[0].stopped)
        assertEquals(1, players[0].released)
        // The first one's end, coming late, changes nothing.
        players[0].onDone?.invoke()
        assertTrue(session.state.value.playing)
        // The second's end stops it.
        players[1].onDone?.invoke()
        assertFalse(session.state.value.playing)
        assertEquals(1, players[1].released)
        assertEquals(0, session.playbackMs)
        // Stopped by hand.
        session.startPlayback("take.mp4")
        session.stopPlayback()
        assertFalse(session.state.value.playing)
        assertEquals(1, players[2].released)
    }

    @Test
    fun `a recording that will not play is released, and nothing plays while recording`() {
        nextPlayer = { FakePlayer(failStart = true) }
        assertFalse(session.startPlayback("broken.mp4"))
        assertFalse(session.state.value.playing)
        assertEquals(1, players.single().released)
        nextPlayer = { FakePlayer() }
        session.startRecording("pending.mp4")
        assertFalse(session.startPlayback("take.mp4"))
    }

    @Test
    fun `recording stops what was playing`() {
        session.startPlayback("take.mp4")
        session.startRecording("pending.mp4")
        assertFalse(session.state.value.playing)
        assertEquals(1, players.single().released)
    }
}
