package com.masterclock.app.ui.screens

import android.graphics.Paint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masterclock.app.R
import com.masterclock.app.logic.EmojiGroup
import com.masterclock.app.logic.NOTE_EMOJIS
import com.masterclock.app.logic.NoteInserts
import com.masterclock.app.logic.parseEmojiCatalog

/** What a text note can have put in at the cursor, each behind its own button of the toolbar. */
enum class InsertKind { PIECES, ANNOTATIONS, EMOJIS }

/**
 * The panel of one [InsertKind]. One button with three tabs behind it hid two of them: the user
 * asked for a button each, which opens its panel straight away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InsertPanel(kind: InsertKind, onInsert: (String) -> Unit) {
    // Only what this phone's font can draw: a symbol it lacks would go into the note as an empty box.
    val drawable = remember {
        val paint = Paint()
        // Character by character: hasGlyph answers for a single glyph, and "!?" or "0-0" is several,
        // each of which the font draws.
        fun drawn(symbol: String) = symbol.codePoints().toArray().all { paint.hasGlyph(String(Character.toChars(it))) }
        fun List<String>.drawn() = filter { drawn(it) }
        DrawableInserts(
            chessPieces = NoteInserts.CHESS_PIECES.drawn(),
            draughtsPieces = NoteInserts.DRAUGHTS_PIECES.drawn(),
            quality = NoteInserts.MOVE_QUALITY.drawn(),
            evaluation = NoteInserts.EVALUATION.drawn(),
            other = NoteInserts.OTHER.drawn(),
        )
    }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        // Its own height, so that the note keeps the rest of the screen.
        if (kind == InsertKind.EMOJIS) {
            EmojiPicker(onInsert)
        } else {
            Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (kind == InsertKind.PIECES) {
                    Symbols(drawable.chessPieces, onInsert, large = true)
                    if (drawable.draughtsPieces.isNotEmpty()) Symbols(drawable.draughtsPieces, onInsert, large = true)
                } else {
                    Section(R.string.tools_insert_quality) { Symbols(drawable.quality, onInsert) }
                    Section(R.string.tools_insert_evaluation) { Symbols(drawable.evaluation, onInsert) }
                    Section(R.string.tools_insert_other) { Symbols(drawable.other, onInsert) }
                }
            }
        }
    }
}

/**
 * Every emoji, in OpenMoji as the note draws them, so they need no checking against the phone: the
 * game's own first, then Unicode's groups, one at a time. A row of groups over a grid, as a keyboard
 * has it; the grid is lazy, since there are close to two thousand.
 */
@Composable
private fun EmojiPicker(onInsert: (String) -> Unit) {
    val resources = LocalContext.current.resources
    val groups = remember {
        listOf(EmojiGroup(GAME_GROUP, NOTE_EMOJIS)) +
            parseEmojiCatalog(resources.openRawResource(R.raw.emojis).bufferedReader().use { it.readText() })
    }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val group = groups[selected.coerceIn(groups.indices)]
    Column {
        LazyRow(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(groups) { i, it ->
                val name = groupName(it.name)
                Surface(
                    onClick = { selected = i },
                    selected = i == selected,
                    shape = RoundedCornerShape(8.dp),
                    color = if (i == selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    modifier = Modifier.size(44.dp).semantics { contentDescription = name },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // The group's first emoji stands for it, which TalkBack reads by its name instead.
                        Text(it.emojis.first(), fontSize = 22.sp, fontFamily = OpenMoji, modifier = Modifier.clearAndSetSemantics { })
                    }
                }
            }
        }
        // The grid starts at the top of each group.
        val grid = rememberLazyGridState()
        LaunchedEffect(selected) { grid.scrollToItem(0) }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(48.dp),
            state = grid,
            modifier = Modifier.height(200.dp),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(group.emojis, key = { it }) { emoji ->
                Surface(onClick = { onInsert(emoji) }, shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = 26.sp, fontFamily = OpenMoji) }
                }
            }
        }
    }
}

private const val GAME_GROUP = "Game"

/** A group's name in the app's language; the catalog has Unicode's English ones. */
@Composable
private fun groupName(name: String): String = stringResource(
    when (name) {
        GAME_GROUP -> R.string.tools_emoji_game
        "Smileys & Emotion" -> R.string.tools_emoji_smileys
        "People & Body" -> R.string.tools_emoji_people
        "Animals & Nature" -> R.string.tools_emoji_animals
        "Food & Drink" -> R.string.tools_emoji_food
        "Travel & Places" -> R.string.tools_emoji_travel
        "Activities" -> R.string.tools_emoji_activities
        "Objects" -> R.string.tools_emoji_objects
        "Symbols" -> R.string.tools_emoji_symbols
        else -> R.string.tools_emoji_flags
    },
)

private class DrawableInserts(val chessPieces: List<String>, val draughtsPieces: List<String>, val quality: List<String>, val evaluation: List<String>, val other: List<String>)

@Composable
private fun Section(title: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        content()
    }
}

/** A button per symbol, which puts it in. TalkBack reads the symbol itself, as it would in the text. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Symbols(symbols: List<String>, onInsert: (String) -> Unit, large: Boolean = false) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        symbols.forEach { symbol ->
            Surface(
                onClick = { onInsert(symbol) },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.heightIn(min = 44.dp).widthIn(min = 44.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                    Text(symbol, fontSize = if (large) 26.sp else 18.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
