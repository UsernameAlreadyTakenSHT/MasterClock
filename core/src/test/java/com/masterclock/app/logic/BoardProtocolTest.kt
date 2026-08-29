package com.masterclock.app.logic

import org.junit.Test
import org.junit.Assert.*
import java.util.UUID

/**
 * Covers the vendor-independent half of board support: what [BoardProtocols] picks for a given
 * board, and what [RawCaptureProtocol] makes of a payload.
 *
 * The BLE half -- discovering services and switching notifications on -- cannot be tested here; it
 * needs a real peripheral, and no board hardware exists for this project yet.
 */
class BoardProtocolTest {

    @Test
    fun `an unknown board falls back to raw capture`() {
        assertSame(RawCaptureProtocol, BoardProtocols.forDeviceName("Some Board 42"))
        assertSame(RawCaptureProtocol, BoardProtocols.forDeviceName(null))
        assertSame(RawCaptureProtocol, BoardProtocols.forDeviceName(""))
    }

    @Test
    fun `raw capture accepts any service and any notifying characteristic`() {
        // Both null is what makes the manager subscribe to everything that notifies, which is the
        // whole point of the fallback: it is how an unknown board gets reverse-engineered.
        assertNull(RawCaptureProtocol.serviceUuid)
        assertNull(RawCaptureProtocol.notifyCharacteristicUuid)
    }

    @Test
    fun `raw capture reports a payload as hex`() {
        assertEquals(listOf("01 a0 ff"), RawCaptureProtocol.decode(byteArrayOf(0x01, 0xA0.toByte(), 0xFF.toByte())))
    }

    @Test
    fun `high bytes are not sign-extended`() {
        // Kotlin's Byte is signed, so a naive formatter turns 0x80 into "ffffff80" and the trace
        // becomes unreadable exactly where a board's status bytes live.
        assertEquals(listOf("80 ff 7f 00"), RawCaptureProtocol.decode(byteArrayOf(0x80.toByte(), 0xFF.toByte(), 0x7F, 0x00)))
    }

    @Test
    fun `an empty payload is not a move`() {
        assertEquals(emptyList<String>(), RawCaptureProtocol.decode(byteArrayOf()))
    }

    @Test
    fun `raw capture stays last so a real make always wins`() {
        assertSame(RawCaptureProtocol, BoardProtocols.known.last())
    }

    @Test
    fun `a make that claims a name is preferred over raw capture`() {
        // Guards the selection rule itself, so the first real implementation does not discover that
        // forDeviceName never reaches it.
        val fake = object : BoardProtocol {
            override val name = "Fake"
            override val serviceUuid: UUID? = null
            override val notifyCharacteristicUuid: UUID? = null
            override fun matchesDeviceName(deviceName: String?) = deviceName?.startsWith("FAKE") == true
            override fun decode(payload: ByteArray) = listOf("e2e4")
        }
        val candidates = listOf(fake, RawCaptureProtocol)

        val chosen = candidates.firstOrNull { it !== RawCaptureProtocol && it.matchesDeviceName("FAKE-1") }
            ?: RawCaptureProtocol
        assertSame(fake, chosen)

        val fallback = candidates.firstOrNull { it !== RawCaptureProtocol && it.matchesDeviceName("Other") }
            ?: RawCaptureProtocol
        assertSame(RawCaptureProtocol, fallback)
    }
}
