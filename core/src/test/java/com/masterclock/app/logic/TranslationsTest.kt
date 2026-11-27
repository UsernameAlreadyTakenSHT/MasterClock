package com.masterclock.app.logic

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every translation held to its English source, in all four string files: the app's, Complete's, the
 * E-Ink app's and core's.
 *
 * A placeholder that differs -- one too many, one missing, %s where English has %d -- builds, passes
 * lint, and throws when the sentence is shown, in that language only: the screen or the voice that
 * says it goes down with it. That was checked by hand, once, as each language was translated. Now
 * every language is checked every time, the ones to come included.
 *
 * Also: a file that does not parse, a translation of a string English no longer has, and a plural
 * missing a form its language needs -- Polish, Russian and Ukrainian count one, few, many and other.
 */
class TranslationsTest {

    /** The project root: Gradle runs a module's tests from the module, an IDE may from the root. */
    private val root = listOf(File(".."), File(".")).first { File(it, "app/src/main/res").isDirectory }

    private val resDirs = listOf("app/src/main/res", "app/src/complete/res", "paper/src/main/res", "core/src/main/res")
        .map { File(root, it) }

    /** The forms a language's plurals must have, beyond "other", which every one needs. */
    private val requiredQuantities = mapOf(
        "pl" to setOf("one", "few", "many"),
        "ru" to setOf("one", "few", "many"),
        "uk" to setOf("one", "few", "many"),
    )

    private class Entry(val kind: String, val texts: Map<String, String>, val formatted: Boolean)

    private fun read(file: File): Map<String, Entry> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val out = LinkedHashMap<String, Entry>()
        val children = doc.documentElement.childNodes
        for (i in 0 until children.length) {
            val el = children.item(i) as? Element ?: continue
            val name = el.getAttribute("name")
            if (el.getAttribute("translatable") == "false") continue
            val formatted = el.getAttribute("formatted") != "false"
            when (el.tagName) {
                "string" -> out[name] = Entry("string", mapOf("" to el.textContent), formatted)
                "plurals" -> {
                    val items = el.getElementsByTagName("item")
                    out[name] = Entry("plurals", (0 until items.length).associate { j ->
                        val item = items.item(j) as Element
                        item.getAttribute("quantity") to item.textContent
                    }, formatted)
                }
            }
        }
        return out
    }

    /** %1$s, %d, %.1f, %02d -- what String.format will read -- as (argument, conversion), in order of argument. */
    private fun placeholders(text: String): List<Pair<Int, Char>> {
        var next = 0
        return PLACEHOLDER.findAll(text.replace("%%", "")).map { m ->
            val index = m.groupValues[1].takeIf { it.isNotEmpty() }?.dropLast(1)?.toInt() ?: (++next)
            index to m.groupValues[2].last()
        }.toList().sortedWith(compareBy({ it.first }, { it.second }))
    }

    @Test
    fun `every translation has its English source's placeholders, a parsable file, no stray string and every plural form`() {
        val problems = mutableListOf<String>()
        var checked = 0
        for (res in resDirs) {
            val english = read(File(res, "values/strings.xml"))
            val translated = res.listFiles().orEmpty().filter { it.name.startsWith("values-") && File(it, "strings.xml").isFile }
            for (dir in translated) {
                val file = File(dir, "strings.xml")
                val where = "${res.relativeTo(root).invariantSeparatorsPath}/${dir.name}"
                val language = dir.name.removePrefix("values-").substringBefore("-r")
                val entries = try { read(file) } catch (e: Exception) { problems += "$where: does not parse: ${e.message}"; continue }
                for ((name, entry) in entries) {
                    val source = english[name]
                    if (source == null) { problems += "$where: $name is not in English"; continue }
                    if (source.kind != entry.kind) { problems += "$where: $name is a ${entry.kind}, English has a ${source.kind}"; continue }
                    checked++
                    if (!source.formatted) continue
                    if (entry.kind == "string") {
                        val want = placeholders(source.texts.getValue(""))
                        val got = placeholders(entry.texts.getValue(""))
                        if (want != got) problems += "$where: $name has $got, English $want"
                    } else {
                        // Each form against English "other", which carries the full set; a "one" may leave the
                        // number out ("One minute"), so a form may have fewer, never others.
                        val want = placeholders(source.texts["other"].orEmpty()).toSet()
                        for ((quantity, text) in entry.texts) {
                            val got = placeholders(text).toSet()
                            if (!want.containsAll(got)) problems += "$where: $name/$quantity has $got, English $want"
                        }
                        if (!entry.texts.containsKey("other")) problems += "$where: $name has no \"other\""
                        // pt-BR overrides only some words; its plurals, if any, are whole.
                        val missing = requiredQuantities[language].orEmpty() - entry.texts.keys
                        if (missing.isNotEmpty()) problems += "$where: $name lacks $missing"
                    }
                }
            }
        }
        assertTrue("checked nothing: are the resource folders where this test looks? ${resDirs.map { it.path }}", checked > 1_000)
        assertTrue("${problems.size} problem(s):\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `the placeholder reading sees what String format sees`() {
        assertTrue(placeholders("Player %1\$d, %2\$s") == listOf(1 to 'd', 2 to 's'))
        assertTrue(placeholders("%2\$s then %1\$d") == listOf(1 to 'd', 2 to 's'))
        assertTrue(placeholders("%d moves, %s left") == listOf(1 to 'd', 2 to 's'))
        assertTrue(placeholders("%.1f s and %02d") == listOf(1 to 'f', 2 to 'd'))
        // A percent sign that is no placeholder.
        assertTrue(placeholders("100%% sure, 50 % off").isEmpty())
    }

    private companion object {
        val PLACEHOLDER = Regex("""%(\d+\$)?[-0]?\d*(?:\.\d+)?([sdf])""")
    }
}
