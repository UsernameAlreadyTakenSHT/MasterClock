package com.masterclock.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.ImageLoader
import coil3.svg.SvgDecoder
import com.masterclock.app.R
import com.masterclock.app.logic.*
import java.util.*

/** How many board states the editor can step back through. Far more than anyone undoes in a row. */
private const val MAX_BOARD_HISTORY = 50

/** A board note's squares, also a drawing's when it is drawn over one. */
internal val BOARD_DARK = Color(0xFF769656)
internal val BOARD_LIGHT = Color(0xFFEEEED2)

/** Side of one square in the board note's grid. */
private val BOARD_SQUARE_SIZE = 40.dp

/** The smallest a square gets to leave room for the palette: below it a piece is too small to aim at. */
private val MIN_BOARD_SQUARE = 24.dp

/**
 * A piece being dragged, and where the finger currently is in the screen's own coordinates.
 *
 * Root coordinates rather than any one container's, because a drag that starts in the palette ends
 * on the board: the two have no common frame otherwise, and translating between them at drop time
 * is how the piece would land a row off.
 *
 * [fromSquare] is null when the piece came from the palette, which is what distinguishes placing a
 * new piece from moving one that was already on the board.
 */
private data class BoardDrag(val piece: String, val fromSquare: Int?, val position: Offset)

/** The square under [offset], measured from the grid's top-left corner, or null if outside it. */
private fun squareAt(offset: Offset, squarePx: Float, side: Int): Int? {
    val file = (offset.x / squarePx).toInt()
    val rowFromTop = (offset.y / squarePx).toInt()
    val last = side - 1
    if (offset.x < 0f || offset.y < 0f || file !in 0..last || rowFromTop !in 0..last) return null
    // The grid is drawn from the far rank down, so the top row is the last one in the note's index.
    return (last - rowFromTop) * side + file
}

/** Pieces the palette offers for a board, in the order they are laid out. */
private fun paletteFor(variant: BoardNoteVariant): List<String> = if (variant.isDraughts) {
    listOf("M", "D", "m", "d")
} else {
    listOf("K", "Q", "R", "B", "N", "P", "k", "q", "r", "b", "n", "p")
}

/**
 * Keeps a board's contents when the variant changes, where the geometry allows it.
 *
 * Switching between the two draughts boards is a resize, and someone who set up an eight-by-eight
 * position and then realised they wanted ten should not lose it. Switching to or from chess is not:
 * the pieces mean different things, so that starts empty.
 */
private fun resizeBoard(squares: List<String>, from: BoardNoteVariant, to: BoardNoteVariant): List<String> {
    if (from == to) return squares
    if (!from.isDraughts || !to.isDraughts) return List(to.squareCount) { "" }

    val resized = MutableList(to.squareCount) { "" }
    val side = minOf(from.side, to.side)
    for (rank in 0 until side) {
        for (file in 0 until side) {
            resized[rank * to.side + file] = squares.getOrElse(rank * from.side + file) { "" }
        }
    }
    return resized
}

/**
 * One piece on a board, chess or draughts.
 *
 * Both come from the same Wikimedia set and load the same way; [getPieceSvgPath] is what knows
 * which file a letter means.
 */
@Composable
internal fun BoardPiece(piece: String, size: Dp, imageLoader: ImageLoader, modifier: Modifier = Modifier) {
    AsyncImage(
        model = getPieceSvgPath(piece),
        // Its name rather than the letter it is stored as, which TalkBack read out: "q" for a queen.
        contentDescription = PIECE_NAMES[piece]?.let { stringResource(it) } ?: piece,
        modifier = modifier.size(size),
        imageLoader = imageLoader,
    )
}

/**
 * One piece in the palette below the board.
 *
 * Tapping the piece already held puts it down, which is the only way back to the mode where a tap
 * on a square clears it.
 */
@Composable
private fun PieceChoice(
    piece: String,
    selected: Boolean,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    // Where this button sits on screen, so a drag leaving it can be drawn from the piece itself
    // rather than jumping to the finger.
    var centre by remember { mutableStateOf(Offset.Zero) }
    // The drag outlives the composition it started in, and finishing it reads the board's square size.
    // Keyed on the piece alone, a switch between the 10x10 and 8x8 draughts boards -- the same
    // pieces -- kept the old size: a piece dropped on the 8x8 board's right-hand files fell off it,
    // and on the 10x10 one it landed on the wrong file.
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                CircleShape,
            )
            .onGloballyPositioned { centre = it.boundsInRoot().center }
            .pointerInput(piece) {
                detectDragGestures(
                    onDragStart = { offset -> currentOnDragStart(centre + offset - Offset(size.width / 2f, size.height / 2f)) },
                    onDrag = { change, amount -> change.consume(); currentOnDrag(amount) },
                    onDragEnd = { currentOnDragEnd() },
                    onDragCancel = { currentOnDragCancel() },
                )
            },
    ) {
        BoardPiece(piece, 32.dp, imageLoader)
    }
}

@Composable
fun BoardNoteEditor(note: NotebookNote, onUpdate: NoteChange, onBack: () -> Unit) {
    var title by remember { mutableStateOf(note.title) }
    var variant by remember { mutableStateOf(note.boardVariant) }
    var board by remember { mutableStateOf(note.boardPosition) }
    var selectedPiece by remember { mutableStateOf<String?>(null) }
    val piecesList = paletteFor(variant)

    // Previous board states, oldest first, so a misplaced piece costs one tap rather than a hunt
    // for where it used to be. Capped because setting up a position is a long series of small
    // edits and the whole history would otherwise sit in memory for as long as the note is open.
    // The variant travels with the squares, so changing board is undoable like any other edit --
    // and it is the edit that destroys the most, since the two geometries share no squares.
    var history by remember { mutableStateOf(EditHistory<Pair<BoardNoteVariant, List<String>>>(limit = MAX_BOARD_HISTORY)) }
    val editBoard: (List<String>) -> Unit = { next ->
        if (next != board) {
            history = history.record(variant to board)
            board = next
        }
    }
    val changeVariant: (BoardNoteVariant) -> Unit = { next ->
        if (next != variant) {
            history = history.record(variant to board)
            board = resizeBoard(board, variant, next)
            variant = next
            // A chess piece cannot be held over a draughts board, nor the other way round.
            selectedPiece = null
        }
    }

    var drag by remember { mutableStateOf<BoardDrag?>(null) }
    var boardBounds by remember { mutableStateOf<Rect?>(null) }
    // Ten squares of forty would run off a phone, so the bigger board gets smaller squares and both
    // end up the same width.
    //
    // The height too: on a 360x640 screen at a large font, the title, the three games and a board
    // sized by the width alone filled the screen, and the palette was under its foot, out of reach
    // on a page that does not scroll. Without it no piece can be put down. The page and what sits
    // around the board are measured, and the board takes what they leave; a frame late on first
    // show, as a measure is. Where even squares of MIN_BOARD_SQUARE do not fit, the board stays at
    // that size and the page scrolls instead, by the title or the palette: the board's own drag
    // keeps the finger.
    var pageHeight by remember { mutableIntStateOf(0) }
    var aroundBoard by remember { mutableStateOf(IntArray(3)) }
    val byWidth = boardSquareSize(if (variant.side > 8) BOARD_SQUARE_SIZE * 8 / variant.side else BOARD_SQUARE_SIZE, variant.side, chrome = 50.dp)
    val density = LocalDensity.current
    // Three gaps of 16 between the four parts, and the board's own 8 of padding a side and border.
    val byHeight = if (pageHeight == 0 || aroundBoard.any { it == 0 }) null
        else (with(density) { (pageHeight - aroundBoard.sum()).toDp() } - 16.dp * 3 - 18.dp) / variant.side
    val pageScrolls = byHeight != null && byHeight < MIN_BOARD_SQUARE && byWidth > MIN_BOARD_SQUARE
    val squareSize = when {
        byHeight == null -> byWidth
        pageScrolls -> MIN_BOARD_SQUARE
        else -> minOf(byWidth, byHeight)
    }
    val squarePx = with(LocalDensity.current) { squareSize.toPx() }
    val measured: (Int, Int) -> Unit = { part, height ->
        if (aroundBoard[part] != height) aroundBoard = aroundBoard.copyOf().also { it[part] = height }
    }

    // Read inside the gesture detector rather than captured when it starts, so a drag is not
    // interrupted and restarted every time the board changes underneath it.
    val currentBoard by rememberUpdatedState(board)

    /** Applies a finished drag: move, place, or -- dropped off the board -- remove. */
    val finishDrag: () -> Unit = {
        drag?.let { d ->
            val bounds = boardBounds
            val target = bounds?.let { squareAt(d.position - it.topLeft, squarePx, variant.side) }
            // Sized to the variant before anything is written into it. The grid is laid out from
            // variant.side, so a board list of any other length -- which a note imported before the
            // sanitiser normalised them still has, stored in settings -- would be indexed past its
            // end here and on the tap handler below.
            val next = currentBoard.toMutableList().apply {
                while (size < variant.squareCount) add("")
                while (size > variant.squareCount) removeAt(size - 1)
            }
            when {
                target != null -> {
                    if (d.fromSquare != null && d.fromSquare in next.indices) next[d.fromSquare] = ""
                    if (target in next.indices) next[target] = d.piece
                }
                // A piece carried off the board is a piece taken off the board. Doing nothing here
                // would leave no way to remove one without putting the palette down first.
                d.fromSquare != null && d.fromSquare in next.indices -> next[d.fromSquare] = ""
            }
            editBoard(next)
        }
        drag = null
    }

    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }

    PersistDebounced(Triple(title, board, variant)) { (newTitle, newBoard, newVariant) ->
        onUpdate { it.copy(title = newTitle, boardPosition = newBoard, boardVariant = newVariant) }
    }
    ToolScaffold(
        title = stringResource(R.string.tools_edit_board),
        onBack = onBack,
        actions = {
            IconButton(
                onClick = {
                    history.undo(variant to board)?.let { (state, rest) ->
                        if (state.first != variant) selectedPiece = null
                        variant = state.first
                        board = state.second
                        history = rest
                    }
                },
                enabled = history.canUndo,
            ) { Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.tools_undo)) }
            IconButton(
                onClick = {
                    history.redo(variant to board)?.let { (state, rest) ->
                        if (state.first != variant) selectedPiece = null
                        variant = state.first
                        board = state.second
                        history = rest
                    }
                },
                enabled = history.canRedo,
            ) { Icon(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.tools_redo)) }
            // Clearing goes through the history too: emptying a board by accident is the one
            // mistake here that costs the most to make good by hand.
            IconButton(onClick = { editBoard(List(variant.squareCount) { "" }) }) {
                Icon(Icons.Default.DeleteSweep, stringResource(R.string.tools_clear_board))
            }
        },
    ) { padding ->
      // The dragged piece is drawn in this Box rather than in the board or the palette, so it can
      // travel between the two and over everything else on the way.
      Box(Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp).onSizeChanged { pageHeight = it.height }
            .then(if (pageScrolls) Modifier.verticalScroll(rememberScrollState()) else Modifier), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(value = title, onValueChange = { title = it.take(MAX_NOTE_TITLE_CHARS) }, label = { Text(stringResource(R.string.tools_title)) }, modifier = Modifier.fillMaxWidth().onSizeChanged { measured(0, it.height) }, shape = RoundedCornerShape(12.dp), singleLine = true)
            // Chess first because that is what a board note has always been; ten by ten before
            // eight because international draughts is the one people mean by "draughts".
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.onSizeChanged { measured(1, it.height) }) {
                VariantCard(stringResource(R.string.game_chess), variant == BoardNoteVariant.CHESS, Modifier.weight(1f)) {
                    changeVariant(BoardNoteVariant.CHESS)
                }
                VariantCard(stringResource(R.string.tools_board_variant_draughts_10), variant == BoardNoteVariant.DRAUGHTS_INTERNATIONAL, Modifier.weight(1f)) {
                    changeVariant(BoardNoteVariant.DRAUGHTS_INTERNATIONAL)
                }
                VariantCard(stringResource(R.string.tools_board_variant_draughts_8), variant == BoardNoteVariant.DRAUGHTS_SMALL, Modifier.weight(1f)) {
                    changeVariant(BoardNoteVariant.DRAUGHTS_SMALL)
                }
            }
            // Weighted, so the column measures the palette before it: last in line, the palette was
            // measured in whatever the board had left, nothing at all on a small screen, and the
            // board was then sized against a palette of no height.
            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = if (pageScrolls) Modifier else Modifier.weight(1f, fill = false)) {
                Column(
                    Modifier
                        .padding(8.dp)
                        .onGloballyPositioned { boardBounds = it.boundsInRoot() }
                        // The whole grid handles the drag, not each square: a square that has just
                        // been left cannot report where the finger went next.
                        .pointerInput(variant) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val from = squareAt(offset, squarePx, variant.side)
                                    val piece = from?.let { currentBoard[it] }.orEmpty()
                                    if (piece.isNotEmpty()) {
                                        val origin = boardBounds?.topLeft ?: Offset.Zero
                                        drag = BoardDrag(piece, from, origin + offset)
                                    }
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    drag = drag?.let { it.copy(position = it.position + amount) }
                                },
                                onDragEnd = { finishDrag() },
                                onDragCancel = { drag = null },
                            )
                        }
                ) {
                    for (rank in variant.side - 1 downTo 0) {
                        Row {
                            for (file in 0 until variant.side) {
                                val index = rank * variant.side + file
                                val isDark = (rank + file) % 2 == 0
                                Box(
                                    modifier = Modifier
                                        .size(squareSize)
                                        .background(if (isDark) BOARD_DARK else BOARD_LIGHT)
                                        .clickable {
                                            // Same reason as finishDrag: the list may be shorter
                                            // than the grid for a note stored before imports were
                                            // normalised to the variant.
                                            val squares = board.toMutableList().apply {
                                                while (size < variant.squareCount) add("")
                                            }
                                            squares[index] = selectedPiece ?: ""
                                            editBoard(squares)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    // The square a piece is being lifted from reads as empty while
                                    // the drag lasts, so the piece is not in two places at once.
                                    // getOrElse: a variant change resizes the board a frame before
                                    // the grid is rebuilt around it.
                                    val piece = if (drag?.fromSquare == index) "" else board.getOrElse(index) { "" }
                                    if (piece.isNotEmpty()) {
                                        BoardPiece(piece, squareSize * 4 / 5, imageLoader)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { measured(2, it.height) }
                    // Tapping the tray anywhere but on a piece puts the palette down. The piece
                    // buttons consume their own taps, so this only fires on the space around them.
                    // Without indication: a ripple across the whole tray would read as a control.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = stringResource(R.string.tools_board_deselect),
                    ) { selectedPiece = null },
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // The label doubles as the only sign of which mode the board is in: with no
                    // piece held, a tap on a square clears it, and nothing else would say so. One
                    // line, so the pieces below do not jump when the label changes: in Portuguese
                    // the eraser's took two.
                    OneLineText(
                        stringResource(
                            if (selectedPiece == null) R.string.tools_board_eraser_hint else R.string.tools_select_piece
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        minSize = 9.sp,
                    )
                    // One row per colour, whatever the game: six chess pieces or two draughts ones.
                    piecesList.chunked(piecesList.size / 2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                            row.forEach { p ->
                                PieceChoice(
                                    piece = p,
                                    selected = selectedPiece == p,
                                    imageLoader = imageLoader,
                                    onClick = { selectedPiece = if (selectedPiece == p) null else p },
                                    onDragStart = { from -> drag = BoardDrag(p, fromSquare = null, position = from) },
                                    onDrag = { amount -> drag = drag?.let { it.copy(position = it.position + amount) } },
                                    onDragEnd = finishDrag,
                                    onDragCancel = { drag = null },
                                )
                            }
                        }
                    }
                }
            }
        }

        drag?.let { d ->
            val half = with(LocalDensity.current) { (squareSize / 2).roundToPx() }
            BoardPiece(
                piece = d.piece,
                size = squareSize,
                imageLoader = imageLoader,
                modifier = Modifier.offset { IntOffset(d.position.x.toInt() - half, d.position.y.toInt() - half) },
            )
        }
      }
    }
}
