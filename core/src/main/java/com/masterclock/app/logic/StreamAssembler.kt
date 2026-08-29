package com.masterclock.app.logic

/**
 * How a make's messages are delimited within a byte stream.
 *
 * Only needed where the transport does not preserve message boundaries. A serial line is a stream
 * of bytes and nothing more: it will happily hand over half a board dump, or two field updates at
 * once, and a decoder fed those directly sees garbage.
 */
fun interface StreamFraming {
    /**
     * The length of the message starting at the front of [buffer].
     *
     * Returns null while there are too few bytes to tell, and [RESYNC] when the front of the buffer
     * cannot begin a message -- which happens after a disconnect mid-message, or when the board was
     * already talking before the app started listening.
     */
    fun messageLength(buffer: ByteArray): Int?

    companion object {
        /** Drop the leading byte and look again. */
        const val RESYNC = 0
    }
}

/**
 * Turns a stream of arbitrary chunks into whole messages.
 *
 * One per connection. Holding leftovers is the entire job: without it, the first read of a board
 * dump is decoded as a malformed frame and thrown away, and the rest is decoded as junk.
 */
class StreamAssembler(private val framing: StreamFraming) {

    private var buffer = ByteArray(0)

    /**
     * Guards against a stream that never yields a valid message -- a device talking a protocol this
     * is not, or a length field read from noise. Well past the largest message any make sends.
     */
    private val maxBufferBytes = 4096

    fun offer(chunk: ByteArray): List<ByteArray> {
        buffer += chunk
        val messages = mutableListOf<ByteArray>()

        while (buffer.isNotEmpty()) {
            val length = framing.messageLength(buffer)
            if (length == null) break // Wait for more bytes.
            if (length == StreamFraming.RESYNC) {
                buffer = buffer.copyOfRange(1, buffer.size)
                continue
            }
            if (length > buffer.size) break
            messages += buffer.copyOfRange(0, length)
            buffer = buffer.copyOfRange(length, buffer.size)
        }

        if (buffer.size > maxBufferBytes) buffer = ByteArray(0)
        return messages
    }

    fun reset() {
        buffer = ByteArray(0)
    }
}
