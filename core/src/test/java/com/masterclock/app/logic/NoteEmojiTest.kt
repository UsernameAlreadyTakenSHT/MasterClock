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
}
