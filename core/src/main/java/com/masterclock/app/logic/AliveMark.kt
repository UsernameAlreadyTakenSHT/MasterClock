package com.masterclock.app.logic

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * The last moment a running clock was known to be alive, kept beside the saved game.
 *
 * A save is the whole time control, encoded and written to the database: too much to do several
 * times a second, so it was done every two seconds and a game closed by surprise came back up to
 * that much earlier -- two seconds given back to whoever was on the move, which in a serious game is
 * a clock that is no longer right. The mark is three numbers written over themselves five times a
 * second, ten where the face shows tenths, saying only "the game of this save was still running at
 * this moment". Coming back, the saved clock is run on from its save to that moment, by the same
 * engine that runs it in play; see [aliveGapMs] and advanceClock.
 *
 * Not synced to the disk: what is closed is the app, not the phone, and a write the system has
 * taken survives the process that made it. A mark that cannot be written or read is no mark, and
 * the game comes back from its save alone, as it always did.
 */
class AliveMark(private val file: File) {

    /** The save the mark belongs to, by its timestamp, and when it was last alive on the elapsed-time clock. */
    data class Reading(val saveId: Long, val aliveAtElapsedMs: Long)

    private var out: RandomAccessFile? = null
    private val bytes = ByteBuffer.allocate(SIZE)

    @Synchronized
    fun write(saveId: Long, aliveAtElapsedMs: Long) {
        try {
            val target = out ?: RandomAccessFile(file, "rw").also { out = it }
            bytes.clear()
            // The third number is the other two folded together: a mark caught half written reads
            // as no mark rather than as a moment that never was.
            bytes.putLong(saveId).putLong(aliveAtElapsedMs).putLong(saveId xor aliveAtElapsedMs)
            target.seek(0)
            target.write(bytes.array())
        } catch (e: Exception) {
            runCatching { out?.close() }
            out = null
        }
    }

    @Synchronized
    fun read(): Reading? = try {
        val stored = file.takeIf { it.isFile && it.length() >= SIZE }?.inputStream()?.use { input ->
            ByteArray(SIZE).takeIf { input.read(it) == SIZE }
        }
        stored?.let { ByteBuffer.wrap(it) }?.let { buffer ->
            val saveId = buffer.long
            val aliveAt = buffer.long
            Reading(saveId, aliveAt).takeIf { buffer.long == saveId xor aliveAt }
        }
    } catch (e: Exception) {
        null
    }

    @Synchronized
    fun close() {
        runCatching { out?.close() }
        out = null
    }

    private companion object {
        const val SIZE = 24
    }
}

/**
 * The players whose flag fell between [saved] and [back], the clock run on from it: out of time
 * now and not then. Their flag is the game's to record; see resumeSavedClock.
 */
internal fun flaggedWhileRunningOn(saved: ChessClockState, back: ChessClockState): List<Int> =
    back.players.indices.filter { back.players[it].isOutOfTime && saved.players.getOrNull(it)?.isOutOfTime == false }.map { it + 1 }

/** How often the mark is written while a clock runs on whole seconds: what a game closed by surprise can lose. */
const val ALIVE_MARK_EVERY_MS = 200L

/** And where the ticker itself runs in tenths or faster: never oftener than the face can show. */
const val ALIVE_MARK_FAST_MS = 100L

/**
 * How long until the next mark. Five a second where the ticker wakes once, and as often as the
 * ticker where it runs in tenths: the last ten seconds of a clock, by default, which is where a
 * tenth given back matters most and where the app is awake that often already. A player who shows
 * tenths throughout has the mark that fine throughout, as they have the face.
 */
internal fun aliveMarkEveryMs(tickerRunsInTenths: Boolean): Long =
    if (tickerRunsInTenths) ALIVE_MARK_FAST_MS else ALIVE_MARK_EVERY_MS

/**
 * Further than this from its save, a mark is not believed. Saves are taken every few seconds while
 * a clock runs, so a longer gap is a mark that does not mean what it says, and the save alone is
 * the safer clock.
 */
const val MAX_ALIVE_GAP_MS = 60_000L

/**
 * How long the saved clock went on running after its save, by its mark: nothing for a clock saved
 * paused, for a mark of another save -- a move was played or the game changed since -- for a save
 * from before there were marks, or for a gap no save interval explains.
 */
internal fun aliveGapMs(saveId: Long, savedAtElapsedMs: Long, wasRunning: Boolean, mark: AliveMark.Reading?): Long {
    if (!wasRunning || mark == null || savedAtElapsedMs <= 0 || mark.saveId != saveId) return 0
    return (mark.aliveAtElapsedMs - savedAtElapsedMs).takeIf { it in 1..MAX_ALIVE_GAP_MS } ?: 0
}
