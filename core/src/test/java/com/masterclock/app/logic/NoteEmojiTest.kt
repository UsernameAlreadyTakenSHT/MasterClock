package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class NoteEmojiTest {

    private fun emojis(text: String) = emojiRanges(text).map { text.substring(it.first, it.last + 1) }

    @Test
    fun `emojis are found whole, and nothing else`() {
        assertEquals(listOf("🏆", "⏱️", "❤️"), emojis("Won 🏆 on time ⏱️ ❤️"))
        // A skin tone, a family joined into one, a flag of two letters.
        assertEquals(listOf("👍🏽", "👨‍👩‍👧", "🇫🇷"), emojis("👍🏽 👨‍👩‍👧 🇫🇷"))
    }

    @Test
    fun `the note's own symbols stay text`() {
        assertTrue(emojis("♔ ♟ ☐ ☑ • → ↑ ⇆ ± ∞ !? 0-0").isEmpty())
    }

    @Test
    fun `every emoji offered is found as one`() {
        for (emoji in NOTE_EMOJIS) assertEquals(emoji, listOf(emoji), emojis(emoji))
    }

    @Test
    fun `the catalog is read by group, comments left out`() {
        val groups = parseEmojiCatalog("# a comment\n@Smileys & Emotion\n😀 😃\n😄\n@Empty\n@Flags\n🇫🇷\n")
        assertEquals(listOf(EmojiGroup("Smileys & Emotion", listOf("😀", "😃", "😄")), EmojiGroup("Flags", listOf("🇫🇷"))), groups)
    }

    /**
     * The catalog Complete ships, read where the app keeps it: an emoji the note did not find whole
     * would be drawn half in OpenMoji and half in the phone's font, or as two.
     */
    @Test
    fun `every emoji of the shipped catalog is found whole`() {
        val file = File("../app/src/complete/res/raw/emojis.txt")
        assumeTrue("the catalog is not where this test was run from", file.exists())
        val groups = parseEmojiCatalog(file.readText())
        assertEquals(9, groups.size)
        val all = groups.flatMap { it.emojis }
        assertTrue(all.size > 1500)
        assertEquals("an emoji is listed twice", all.size, all.toSet().size)
        for (emoji in all) assertEquals(emoji, listOf(emoji), emojis(emoji))
        // And side by side, as they are in a note: none runs into the next.
        assertEquals(all, emojis(all.joinToString("")))
    }

    @Test
    fun `an emoji's drawing is named by its code points, the variation selector left out`() {
        assertEquals("1F600", emojiDrawingName("😀"))
        // The heart is U+2764 and the selector, and OpenMoji's file is 2764.
        assertEquals("2764", emojiDrawingName("❤️"))
        assertEquals("2764", emojiDrawingName("❤"))
        // A keycap keeps its other two, a family its joiners.
        assertEquals("0023-20E3", emojiDrawingName("#️⃣"))
        assertEquals("1F468-200D-1F469-200D-1F467", emojiDrawingName("👨‍👩‍👧"))
        assertEquals("", emojiDrawingName(""))
    }

    /**
     * A sticker on a drawing is drawn from OpenMoji's own drawing of it, and falls back to the font
     * only for one it does not have. A catalog regenerated without the drawings would leave its new
     * emojis blurring as they are enlarged, as all of them did, and nothing else would say so.
     */
    @Test
    fun `every emoji offered has its drawing`() {
        val catalog = File("../app/src/complete/res/raw/emojis.txt")
        val drawings = File("../app/src/complete/assets/emojis")
        assumeTrue("the catalog is not where this test was run from", catalog.exists())
        val offered = parseEmojiCatalog(catalog.readText()).flatMap { it.emojis } + NOTE_EMOJIS
        val missing = offered.filterNot { File(drawings, emojiDrawingName(it) + ".svg").exists() }
        assertTrue("no drawing for $missing: run tools/openmoji_svg.py", missing.isEmpty())
        // And none left over from an emoji no longer offered.
        assertEquals(offered.map { emojiDrawingName(it) }.toSet().size, drawings.list { _, name -> name.endsWith(".svg") }!!.size)
    }
}
