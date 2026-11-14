package com.masterclock.app.logic

/**
 * Where a text holds emojis, as start and end offsets, for the editor to draw them in OpenMoji.
 *
 * A note's emojis came out in whatever the phone draws them with, and an emoji newer than the phone
 * as an empty box. Drawn in the font the app carries, they look the same on every phone. Only
 * emojis: the boxes, the bullets, the pieces and the arrows of an annotation are text and stay in the
 * text's own font, though some of them have an emoji form too.
 *
 * An emoji is a character of the emoji planes, one of the older characters drawn as an emoji by
 * default, or a character asked to be one by the variation selector that follows it. What joins it
 * to the next -- a skin tone, a zero-width joiner, a keycap -- belongs to the same emoji.
 */
fun emojiRanges(text: String): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        val next = i + Character.charCount(cp)
        val asked = text.codePointAtOrNull(next) == VARIATION_EMOJI
        if (isEmojiByDefault(cp) || asked) {
            var end = next
            // A flag is two regional letters, one emoji.
            if (cp in REGIONAL_LETTERS && text.codePointAtOrNull(next)?.let { it in REGIONAL_LETTERS } == true) end += 2
            // The rest of the emoji: its selector, skin tones, keycaps, and whatever a joiner adds.
            while (end < text.length) {
                val c = text.codePointAt(end)
                end += when {
                    c == VARIATION_EMOJI || c in SKIN_TONES || c == KEYCAP || c in TAGS -> Character.charCount(c)
                    c == JOINER && end + 1 < text.length -> {
                        val joined = text.codePointAt(end + 1)
                        1 + Character.charCount(joined)
                    }
                    else -> break
                }
            }
            ranges += i until end
            i = end
        } else {
            i = next
        }
    }
    return ranges
}

private fun String.codePointAtOrNull(at: Int): Int? = if (at < length) codePointAt(at) else null

private const val VARIATION_EMOJI = 0xFE0F
private const val JOINER = 0x200D
private const val KEYCAP = 0x20E3
private val SKIN_TONES = 0x1F3FB..0x1F3FF
private val TAGS = 0xE0020..0xE007F
private val REGIONAL_LETTERS = 0x1F1E6..0x1F1FF

/**
 * Drawn as an emoji with no selector after it: everything in the emoji planes, and the older
 * characters Unicode gives an emoji presentation by default. The rest of the older symbols -- the
 * chess pieces, the boxes, the arrows -- are text unless asked otherwise.
 */
private fun isEmojiByDefault(cp: Int): Boolean =
    cp in 0x1F000..0x1FAFF || EMOJI_PRESENTATION_BMP.any { cp in it }

private val EMOJI_PRESENTATION_BMP = listOf(
    0x231A..0x231B, 0x23E9..0x23EC, 0x23F0..0x23F0, 0x23F3..0x23F3, 0x25FD..0x25FE, 0x2614..0x2615,
    0x2648..0x2653, 0x267F..0x267F, 0x2693..0x2693, 0x26A1..0x26A1, 0x26AA..0x26AB, 0x26BD..0x26BE,
    0x26C4..0x26C5, 0x26CE..0x26CE, 0x26D4..0x26D4, 0x26EA..0x26EA, 0x26F2..0x26F3, 0x26F5..0x26F5,
    0x26FA..0x26FA, 0x26FD..0x26FD, 0x2705..0x2705, 0x270A..0x270B, 0x2728..0x2728, 0x274C..0x274C,
    0x274E..0x274E, 0x2753..0x2755, 0x2757..0x2757, 0x2795..0x2797, 0x27B0..0x27B0, 0x27BF..0x27BF,
    0x2B1B..0x2B1C, 0x2B50..0x2B50, 0x2B55..0x2B55,
)

/** A group of the Insert panel's emojis, under the name Unicode gives it. */
data class EmojiGroup(val name: String, val emojis: List<String>)

/**
 * The emoji catalog Complete carries: a line `@Group` opens a group, each line after it holds that
 * group's emojis separated by spaces, and `#` starts a comment. The file is generated from Unicode's
 * emoji-test.txt, kept to what OpenMoji draws, in Unicode's own order, which keyboards follow too.
 */
fun parseEmojiCatalog(text: String): List<EmojiGroup> {
    val groups = mutableListOf<EmojiGroup>()
    for (line in text.lineSequence().map { it.trim() }) {
        when {
            line.isEmpty() || line.startsWith("#") -> Unit
            line.startsWith("@") -> groups += EmojiGroup(line.substring(1).trim(), emptyList())
            groups.isNotEmpty() -> groups[groups.lastIndex] = groups.last().let { it.copy(emojis = it.emojis + line.split(' ').filter(String::isNotEmpty)) }
        }
    }
    return groups.filter { it.emojis.isNotEmpty() }
}

/** The emojis the Insert panel offers first, for a game and what is said about it. */
val NOTE_EMOJIS = listOf(
    "🏆", "🥇", "🥈", "🥉", "🎉", "👏", "👍", "👎", "🤝", "👑",
    "😀", "😅", "😮", "😱", "🤔", "😤", "😴", "🙈",
    "⏱️", "⏰", "⌛", "🔥", "⚡", "💥", "🎯", "🧠", "💡", "⭐",
    "❤️", "💔", "⚠️", "❌", "✅", "❓", "❗", "📌", "📝", "📚",
    "🔍", "🗓️", "🍀", "🐢", "🐇", "🛡️", "⚔️", "🏁", "🚩", "☕",
)
