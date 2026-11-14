package com.masterclock.app.ui.screens

import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masterclock.app.R
import com.masterclock.app.logic.NoteInserts
import com.masterclock.app.logic.SquareBoard
import com.masterclock.app.logic.isDarkSquare
import com.masterclock.app.logic.squareName

/**
 * What a text note can have put in at the cursor, in tabs: pieces, annotations, and the names of a
 * board's squares. Room is kept for more tabs -- emojis are next.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InsertPanel(onInsert: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
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
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp)) {
        Column {
            PrimaryTabRow(selectedTabIndex = tab) {
                listOf(R.string.tools_insert_pieces, R.string.tools_insert_annotations, R.string.tools_insert_squares).forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(label), maxLines = 1) })
                }
            }
            // Its own height, so that the note keeps the rest of the screen.
            Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (tab) {
                    0 -> {
                        Symbols(drawable.chessPieces, onInsert, large = true)
                        if (drawable.draughtsPieces.isNotEmpty()) Symbols(drawable.draughtsPieces, onInsert, large = true)
                    }
                    1 -> {
                        Section(R.string.tools_insert_quality) { Symbols(drawable.quality, onInsert) }
                        Section(R.string.tools_insert_evaluation) { Symbols(drawable.evaluation, onInsert) }
                        Section(R.string.tools_insert_other) { Symbols(drawable.other, onInsert) }
                    }
                    else -> Squares(onInsert)
                }
            }
        }
    }
}

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

/** A board to touch a square of, whose name goes in: "e4", or a draughts board's number. */
@Composable
private fun Squares(onInsert: (String) -> Unit) {
    var boardIndex by rememberSaveable { mutableIntStateOf(0) }
    val board = SquareBoard.entries[boardIndex]
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val labels = listOf(stringResource(R.string.tools_insert_chess), stringResource(R.string.tools_board_variant_draughts_10), stringResource(R.string.tools_board_variant_draughts_8))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = boardIndex == i,
                    onClick = { boardIndex = i },
                    shape = SegmentedButtonDefaults.itemShape(i, labels.size),
                    label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                )
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val square = minOf(maxWidth / board.side, 34.dp)
            Column {
                for (row in 0 until board.side) {
                    Row {
                        for (column in 0 until board.side) {
                            val name = squareName(board, row, column)
                            val dark = isDarkSquare(row, column)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(square)
                                    .background(if (dark) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface)
                                    .then(
                                        if (name != null) Modifier.clickable(role = Role.Button) { onInsert(name) }.semantics { contentDescription = name }
                                        else Modifier
                                    ),
                            ) {
                                if (name != null) Text(name, fontSize = 9.sp, color = if (dark) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }
    }
}
