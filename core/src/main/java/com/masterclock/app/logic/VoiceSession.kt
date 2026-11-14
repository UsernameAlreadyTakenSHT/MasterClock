package com.masterclock.app.logic

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** How long a voice note may record: a minute, as the screen has always said. */
const val MAX_VOICE_MS = 60_000L

/** The microphone, as a voice note uses it. The app's own wraps MediaRecorder. */
interface VoiceRecorder {
    /** Starts recording into [outputPath]; throws when the recorder will not start. */
    fun start(outputPath: String)

    /** Stops; throws when nothing was captured yet, which is any take under about a second. */
    fun stop()

    /** Gives the microphone back. */
    fun release()
}

/** What plays a voice note back. The app's own wraps MediaPlayer. */
interface VoicePlayer {
    /** Plays [path], calling [onDone] at its end; throws when the file will not play. */
    fun start(path: String, onDone: () -> Unit)

    /** Where playback is, in milliseconds; may throw once the player is released. */
    val positionMs: Int

    fun stop()
    fun release()
}

/** Whether a voice note is recording or playing, which its screen shows. */
data class VoiceState(val recording: Boolean = false, val playing: Boolean = false)

/**
 * A voice note's recorder and player, and what each must not be allowed to do: hold the microphone
 * after a failed start, stop the same recorder twice, offer a take that captured nothing, or play
 * two recordings at once. The editor keeps the files -- where a take is written, and moving it into
 * place -- and the screen; this keeps the order things happen in, so that it can be tested.
 *
 * [now] is a clock in milliseconds, which only differences are taken of.
 */
class VoiceSession(
    private val newRecorder: () -> VoiceRecorder,
    private val newPlayer: () -> VoicePlayer,
    private val now: () -> Long,
) {
    private val _state = MutableStateFlow(VoiceState())
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private var recorder: VoiceRecorder? = null
    private var startedAt = 0L
    private var player: VoicePlayer? = null

    /** How long the take in progress has run, or 0 with none. */
    val recordingMs: Long get() = if (recorder != null) now() - startedAt else 0L

    /** Where playback is, or 0 with nothing playing. */
    val playbackMs: Int get() = player?.let { runCatching { it.positionMs }.getOrDefault(0) } ?: 0

    /**
     * Starts recording into [outputPath]; false when it would not start or is already recording.
     * A recorder that failed to start still holds the microphone until it is released, which once
     * nothing did: the next attempt, or another app, then found it busy.
     */
    fun startRecording(outputPath: String): Boolean {
        if (recorder != null) return false
        stopPlayback()
        val started = newRecorder()
        return try {
            started.start(outputPath)
            recorder = started
            startedAt = now()
            _state.update { it.copy(recording = true) }
            true
        } catch (_: Exception) {
            started.release()
            false
        }
    }

    /**
     * Stops the take, and says how long it ran, measured; null when there is no take to keep: none
     * was recording, or it stopped before it caught anything. Cleared before it is stopped, so that
     * a second call -- the minute's end and leaving the screen can come together -- finds nothing
     * to stop rather than stopping the same recorder twice.
     */
    fun stopRecording(): Long? {
        val active = recorder ?: return null
        recorder = null
        _state.update { it.copy(recording = false) }
        val captured = runCatching { active.stop() }.isSuccess
        active.release()
        return if (captured) now() - startedAt else null
    }

    /** Plays [path] from its start, after whatever was playing; false when it will not play. */
    fun startPlayback(path: String): Boolean {
        if (recorder != null) return false
        stopPlayback()
        val started = newPlayer()
        return try {
            started.start(path) { finished(started) }
            player = started
            _state.update { it.copy(playing = true) }
            true
        } catch (_: Exception) {
            started.release()
            false
        }
    }

    fun stopPlayback() {
        val active = player ?: return
        player = null
        runCatching { active.stop() }
        active.release()
        _state.update { it.copy(playing = false) }
    }

    private fun finished(done: VoicePlayer) {
        if (player !== done) return
        player = null
        done.release()
        _state.update { it.copy(playing = false) }
    }
}
