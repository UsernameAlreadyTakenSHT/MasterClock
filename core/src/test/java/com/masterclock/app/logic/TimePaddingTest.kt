package com.masterclock.app.logic

import org.junit.After
import org.junit.Test
import org.junit.Assert.*

/**
 * Covers [padTimeUnit] and [formatClockReading].
 *
 * The interesting cases are all about which unit counts as leading, since that is the only thing
 * STANDARD and FULL disagree about, and it moves as the always-show settings change.
 */
class TimePaddingTest {

    // --- padTimeUnit ---

    @Test
    fun `FULL pads every unit`() {
        assertEquals("01", padTimeUnit(1, isLeading = true, padding = TimePadding.FULL))
        assertEquals("09", padTimeUnit(9, isLeading = false, padding = TimePadding.FULL))
    }

    @Test
    fun `STANDARD pads everything but the leading unit`() {
        assertEquals("1", padTimeUnit(1, isLeading = true, padding = TimePadding.STANDARD))
        assertEquals("09", padTimeUnit(9, isLeading = false, padding = TimePadding.STANDARD))
    }

    @Test
    fun `MINIMAL pads nothing`() {
        assertEquals("1", padTimeUnit(1, isLeading = true, padding = TimePadding.MINIMAL))
        assertEquals("9", padTimeUnit(9, isLeading = false, padding = TimePadding.MINIMAL))
    }

    @Test
    fun `a two-digit value is never widened further`() {
        TimePadding.entries.forEach { padding ->
            assertEquals("59", padTimeUnit(59, isLeading = false, padding = padding))
        }
    }

    @Test
    fun `an hour count past two digits is not truncated`() {
        // A 100-hour correspondence clock is absurd but must not lose a digit.
        assertEquals("100", padTimeUnit(100, isLeading = true, padding = TimePadding.FULL))
    }

    // --- formatClockReading, hours shown ---

    @Test
    fun `the three paddings spell one hour nine minutes eight seconds`() {
        fun read(p: TimePadding) = formatClockReading(1, 9, 8, showHours = true, showMinutes = true, padding = p)
        assertEquals("01:09:08", read(TimePadding.FULL))
        assertEquals("1:09:08", read(TimePadding.STANDARD))
        assertEquals("1:9:8", read(TimePadding.MINIMAL))
    }

    // --- the leading unit moves with what is shown ---

    @Test
    fun `minutes lead when hours are hidden`() {
        fun read(p: TimePadding) = formatClockReading(0, 9, 8, showHours = false, showMinutes = true, padding = p)
        assertEquals("09:08", read(TimePadding.FULL))
        assertEquals("9:08", read(TimePadding.STANDARD))
        assertEquals("9:8", read(TimePadding.MINIMAL))
    }

    @Test
    fun `seconds lead when they are alone`() {
        fun read(p: TimePadding) = formatClockReading(0, 0, 8, showHours = false, showMinutes = false, padding = p)
        assertEquals("08", read(TimePadding.FULL))
        assertEquals("8", read(TimePadding.STANDARD))
        assertEquals("8", read(TimePadding.MINIMAL))
    }

    @Test
    fun `forcing hours on keeps hours leading even at zero`() {
        // What "always show hours" produces nine minutes past the hour: the zero is the leading
        // unit, so STANDARD leaves it bare while FULL widens it.
        fun read(p: TimePadding) = formatClockReading(0, 9, 8, showHours = true, showMinutes = true, padding = p)
        assertEquals("00:09:08", read(TimePadding.FULL))
        assertEquals("0:09:08", read(TimePadding.STANDARD))
        assertEquals("0:9:8", read(TimePadding.MINIMAL))
    }

    @Test
    fun `STANDARD is what the app rendered before the setting existed`() {
        assertEquals("1:09:08", formatClockReading(1, 9, 8, showHours = true, showMinutes = true, padding = TimePadding.STANDARD))
        assertEquals("9:08", formatClockReading(0, 9, 8, showHours = false, showMinutes = true, padding = TimePadding.STANDARD))
    }

    @Test
    fun `FULL is what the paper clock rendered before the setting existed`() {
        // paper is minutes and seconds only, and always two digits each.
        assertEquals("09:08", formatClockReading(0, 9, 8, showHours = false, showMinutes = true, padding = TimePadding.FULL))
        assertEquals("00:00", formatClockReading(0, 0, 0, showHours = false, showMinutes = true, padding = TimePadding.FULL))
    }

    // --- the stored default ---

    @Test
    fun `settings default to FULL`() {
        assertEquals(TimePadding.FULL, ChessClockSettings().timePadding)
    }

    // --- which builds honour the setting ---

    @After
    fun restoreFlavor() {
        FlavorConfig.currentFlavor = AppFlavor.COMPLETE
    }

    @Test
    fun `Complete and Standard honour the stored value`() {
        val stored = ChessClockSettings(timePadding = TimePadding.MINIMAL)
        listOf(AppFlavor.COMPLETE, AppFlavor.STANDARD).forEach { flavor ->
            FlavorConfig.currentFlavor = flavor
            assertEquals("$flavor should honour it", TimePadding.MINIMAL, stored.effectiveTimePadding())
        }
    }

    @Test
    fun `Light Mini and E-Ink stay on FULL whatever is stored`() {
        // These offer no way to change it, so a value can only reach them through an imported
        // settings file or a scanned QR share -- which must not strand them in a format they
        // cannot leave.
        val stored = ChessClockSettings(timePadding = TimePadding.MINIMAL)
        listOf(AppFlavor.LIGHT, AppFlavor.MINI, AppFlavor.E_INK).forEach { flavor ->
            FlavorConfig.currentFlavor = flavor
            assertEquals("$flavor should be pinned to FULL", TimePadding.FULL, stored.effectiveTimePadding())
        }
    }
}
