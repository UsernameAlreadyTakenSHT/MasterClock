package com.masterclock.app.logic

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Everything exchanged with an electronic board, as it crossed the link: the bytes, which way they
 * went, when, over what and under which protocol.
 *
 * A board that does not work could only be described -- "it connects and nothing happens" -- and a
 * make this app has never met could not be added without having one in hand: the raw-capture mode
 * showed the last payload on screen and kept nothing. The log is what a player sends instead, and
 * what a protocol is worked out from. It is also what a simulated board can play back.
 *
 * Bytes as the transport delivered them, before any framing: where a message is cut wrongly, the
 * cut is the thing to see. No device name and no address, which say who owns the board and nothing
 * about what it said.
 *
 * Kept in memory, the last [capacity] frames, and gone with the app: a board talks for as long as
 * it is connected, several times a second on some makes.
 */
class BoardFrameLog(private val capacity: Int = DEFAULT_CAPACITY, private val now: () -> Long = System::currentTimeMillis) {

    enum class Direction(val mark: Char) { FROM_BOARD('<'), TO_BOARD('>') }

    /** One frame; [atMs] is counted from the first frame of the log. */
    data class Frame(val atMs: Long, val direction: Direction, val link: String, val protocol: String, val hex: String)

    private val frames = ArrayDeque<Frame>()
    private var startedAt = 0L
    private var dropped = 0L

    private val _count = MutableStateFlow(0)
    /** How many frames are held, for a screen to offer the log once there is one. */
    val count: StateFlow<Int> = _count.asStateFlow()

    @Synchronized
    fun record(direction: Direction, link: String, protocol: String, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val at = now()
        if (frames.isEmpty() && dropped == 0L) startedAt = at
        if (frames.size >= capacity) {
            frames.removeFirst()
            dropped++
        }
        frames.addLast(Frame((at - startedAt).coerceAtLeast(0), direction, link.clean(), protocol.clean(), bytes.toHex()))
        _count.value = frames.size
    }

    @Synchronized
    fun frames(): List<Frame> = frames.toList()

    @Synchronized
    fun clear() {
        frames.clear()
        dropped = 0
        _count.value = 0
    }

    /**
     * The log as a text file: a header saying what wrote it, then a line a frame. [about] is the
     * app's version and the device, which whoever reads the log needs and the log cannot know.
     */
    @Synchronized
    fun text(about: String): String = buildString {
        append(HEADER).append('\n')
        append("# ").append(about.clean()).append('\n')
        append("# ").append(frames.size).append(" frames")
        if (dropped > 0) append(", ").append(dropped).append(" earlier ones not kept")
        append('\n')
        append("# seconds  < from the board, > to it  link  protocol  bytes\n")
        frames.forEach { append(it.line()).append('\n') }
    }

    companion object {
        const val DEFAULT_CAPACITY = 2_000
        /** As much of one frame as is kept: a board dump is under a hundred bytes on every make known. */
        const val MAX_FRAME_BYTES = 512
        const val HEADER = "MasterClock board frame log, version 1"

        private fun ByteArray.toHex(): String =
            take(MAX_FRAME_BYTES).joinToString(" ") { "%02X".format(it.toInt() and 0xFF) } + if (size > MAX_FRAME_BYTES) " ..." else ""

        /** One word, so that a line splits on spaces whatever a protocol or a link is called. */
        private fun String.clean(): String = trim().replace(Regex("\\s+"), "_").ifEmpty { "-" }

        private fun Frame.line(): String =
            "%d.%03d %c %s %s %s".format(atMs / 1000, atMs % 1000, direction.mark, link, protocol, hex)

        /**
         * The frames of a log written by [text], for playing it back. A line that is not a frame --
         * the header, a comment, anything else in a file passed from hand to hand -- is skipped,
         * and so is a frame whose bytes are not bytes: a log is read as far as it can be.
         */
        fun parse(text: String): List<Frame> = text.lineSequence().mapNotNull { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line == HEADER) return@mapNotNull null
            val parts = line.split(' ').filter { it.isNotEmpty() }
            if (parts.size < 5) return@mapNotNull null
            val seconds = parts[0].split('.')
            val atMs = runCatching { seconds[0].toLong() * 1000 + (seconds.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toLong() ?: 0) }.getOrNull()
                ?: return@mapNotNull null
            val direction = Direction.entries.firstOrNull { it.mark.toString() == parts[1] } ?: return@mapNotNull null
            val bytes = parts.drop(4).takeWhile { it != "..." }
            if (bytes.isEmpty() || bytes.size > MAX_FRAME_BYTES || bytes.any { it.length != 2 || it.toIntOrNull(16) == null }) return@mapNotNull null
            Frame(atMs.coerceAtLeast(0), direction, parts[2], parts[3], bytes.joinToString(" ") { it.uppercase() })
        }.take(MAX_PARSED_FRAMES).toList()

        /** A file from outside is read no further than this, however long it is. */
        const val MAX_PARSED_FRAMES = 20_000

        /** The bytes of a frame, as they were. */
        fun bytesOf(frame: Frame): ByteArray =
            frame.hex.split(' ').filter { it.length == 2 }.mapNotNull { it.toIntOrNull(16)?.toByte() }.toByteArray()
    }
}
