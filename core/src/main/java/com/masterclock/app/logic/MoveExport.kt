package com.masterclock.app.logic

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A game's moves as PGN, for chess.
 *
 * The export used to write two tags, no result and "???" for every move without a notation, which
 * is every move of a game played without a board -- so what it produced was rejected by the chess
 * tools it was meant for. It now writes the Seven Tag Roster PGN requires, in its order, ends the
 * movetext with a result, and never writes a token PGN has no meaning for.
 *
 * The result is always "*" (unknown). A flag is not a result in itself -- the opponent may lack
 * mating material, or the arbiter may rule otherwise -- and the clock knows nothing else about how
 * the game ended. The players' names are not something the clock records either.
 */

/**
 * A date and time as the app writes them everywhere, year first: 2026-10-27 14:30 -- the order
 * the changelog uses, the same in every language, and one that sorts as it reads. The PGN keeps its
 * own "yyyy.MM.dd", which its standard prescribes.
 */
fun isoDateTime(epochMs: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date(epochMs))

fun generatePgn(log: GameLog): String = movetextExport(log, event = "Casual game")

/** The same for draughts. PDN takes PGN's tags and reads "*" as an unfinished game too. */
fun generatePdn(log: GameLog): String = movetextExport(log, event = "Draughts game")

/** Anything that would break the movetext apart: whitespace, comment and tag delimiters, quotes. */
private val MOVE_TOKEN = Regex("""[^\s{}\[\]";]+""")

/** PGN's export format asks for lines of at most 79 characters. */
private const val PGN_LINE_LIMIT = 79

private fun movetextExport(log: GameLog, event: String): String {
    val date = SimpleDateFormat("yyyy.MM.dd", Locale.US).format(Date(log.startTime))
    val tags = listOf(
        "Event" to event,
        "Site" to "?",
        "Date" to date,
        "Round" to "-",
        "White" to "?",
        "Black" to "?",
        "Result" to "*",
    )

    val moves = log.events.filter { it.eventType == "MOVE" }
    // A move with no notation cannot be written, and the ones after it would then be numbered
    // against the wrong positions -- so the movetext stops at the first gap and says so, in a
    // comment, which every PGN reader skips.
    val recorded = moves.map { it.moveNotation?.trim() }.takeWhile { it != null && MOVE_TOKEN.matches(it) }.map { it!! }
    val tokens = mutableListOf<String>()
    recorded.forEachIndexed { i, san ->
        if (i % 2 == 0) tokens += "${i / 2 + 1}."
        tokens += san
    }
    val missing = moves.size - recorded.size
    when {
        moves.isEmpty() -> {}
        recorded.isEmpty() -> tokens += "{No move notation was recorded}"
        missing == 1 -> tokens += "{1 further move was not recorded}"
        missing > 1 -> tokens += "{$missing further moves were not recorded}"
    }
    tokens += "*"

    return buildString {
        tags.forEach { (name, value) -> append("[$name \"$value\"]\n") }
        append('\n')
        var lineLength = 0
        tokens.forEach { token ->
            if (lineLength > 0 && lineLength + 1 + token.length > PGN_LINE_LIMIT) {
                append('\n'); lineLength = 0
            } else if (lineLength > 0) {
                append(' '); lineLength++
            }
            append(token); lineLength += token.length
        }
        append('\n')
    }
}
