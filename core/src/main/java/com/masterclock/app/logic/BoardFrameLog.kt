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
 * what a protocol is worked out from. It is also what the debug build's simulated board plays
 * back; see `SimulatedBoard`, in this module's debug sources and in no build anyone installs.
 *
 * Bytes as the transport delivered them, before any framing: where a message is cut wrongly, the
 * cut is the thing to see. No device name and no address, which say who owns the board and nothing
 * about what it said.
 *
 * Kept in memory, the last [capacity] frames and no more than [MAX_LOG_CHARS] of them, and gone
 * with the app. A board talks for as long as it is connected, and some never stop: a ChessLink
 * sends its whole position at every scan, some twenty-five times a second and four slices each
 * over Bluetooth. So the log is the last few minutes, not the game -- what a player does after
 * "it does not work" is connect, move a piece and share, and that fits.
 */
class BoardFrameLog(private val capacity: Int = DEFAULT_CAPACITY, private val now: () -> Long = System::currentTimeMillis) {

    /**
     * [NOT_SENT] is a write the link refused: the bytes the app meant for the board, and did not
     * get to it. Written down as sent, they told whoever read the log that the board had been asked
     * and had not answered, when it had never been asked. Over Bluetooth LE the board itself may
     * refuse a write the phone took: the same bytes then stand twice, sent and then not sent.
     */
    enum class Direction(val mark: Char) { FROM_BOARD('<'), TO_BOARD('>'), NOT_SENT('!') }

    /** One frame; [atMs] is counted from the first frame of the log. */
    data class Frame(val atMs: Long, val direction: Direction, val link: String, val protocol: String, val hex: String)

    private val frames = ArrayDeque<Frame>()
    private var chars = 0L
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
        val frame = Frame((at - startedAt).coerceAtLeast(0), direction, link.clean(), protocol.clean(), bytes.toHex())
        frames.addLast(frame)
        chars += frame.hex.length
        while (frames.size > capacity || (chars > MAX_LOG_CHARS && frames.size > 1)) {
            chars -= frames.removeFirst().hex.length
            dropped++
        }
        _count.value = frames.size
    }

    @Synchronized
    fun frames(): List<Frame> = frames.toList()

    @Synchronized
    fun clear() {
        frames.clear()
        chars = 0
        dropped = 0
        _count.value = 0
    }

    /**
     * The log as a text file: a header saying what wrote it, then a line a frame. [about] is the
     * app's version and the device, which whoever reads the log needs and the log cannot know.
     */
    fun text(about: String): String {
        // The frames are taken under the lock and written out of it. Twenty thousand lines take a
        // moment to format, and every frame that arrives meanwhile waits on the lock: over
        // Bluetooth LE that is the main thread, and the screen stood still for the length of it.
        val (held, lost) = synchronized(this) { frames.toList() to dropped }
        return buildString {
            append(HEADER).append('\n')
            append("# ").append(about.clean()).append('\n')
            append("# ").append(held.size).append(" frames")
            if (lost > 0) append(", ").append(lost).append(" earlier ones not kept")
            append('\n')
            append("# seconds  < from the board, > to it, ! meant for it and not sent  link  protocol  bytes\n")
            held.forEach { append(it.line()).append('\n') }
        }
    }

    companion object {
        const val DEFAULT_CAPACITY = 20_000
        /** What the frames held may weigh as text, whatever their number: the file shared is about this size. */
        const val MAX_LOG_CHARS = 2_000_000L
        /**
         * As much of one frame as is kept. A Certabo line is 320 numbers, over a thousand bytes when
         * a serial link hands it over whole; nothing any make sends is longer.
         */
        const val MAX_FRAME_BYTES = 2_048
        const val HEADER = "MasterClock board frame log, version 1"

        private fun ByteArray.toHex(): String =
            take(MAX_FRAME_BYTES).joinToString(" ") { "%02X".format(java.util.Locale.ROOT, it.toInt() and 0xFF) } + if (size > MAX_FRAME_BYTES) " ..." else ""

        /** One word, so that a line splits on spaces whatever a protocol or a link is called. */
        private fun String.clean(): String = trim().replace(Regex("\\s+"), "_").ifEmpty { "-" }

        /** A protocol's or a link's name as a frame carries it, for finding in a log the make that wrote it. */
        fun nameInLog(name: String): String = name.clean()

        private fun Frame.line(): String =
            // Locale.ROOT: a phone set to Arabic or Persian wrote the seconds in its own digits, in a
            // file meant for whoever maintains the app, or for a script.
            "%d.%03d %c %s %s %s".format(java.util.Locale.ROOT, atMs / 1000, atMs % 1000, direction.mark, link, protocol, hex)

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
            // Two hex digits each: "-5" and "+A" read as numbers in base 16 and were taken for bytes.
            if (bytes.isEmpty() || bytes.size > MAX_FRAME_BYTES || bytes.any { it.length != 2 || !it.all { c -> c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F' } }) return@mapNotNull null
            Frame(atMs.coerceAtLeast(0), direction, parts[2], parts[3], bytes.joinToString(" ") { it.uppercase() })
        }.take(MAX_PARSED_FRAMES).toList()

        /** A file from outside is read no further than this, however long it is. */
        const val MAX_PARSED_FRAMES = 20_000

        /** The bytes of a frame, as they were. */
        fun bytesOf(frame: Frame): ByteArray =
            frame.hex.split(' ').filter { it.length == 2 }.mapNotNull { it.toIntOrNull(16)?.toByte() }.toByteArray()
    }
}
