package com.masterclock.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.masterclock.app.R
import com.masterclock.app.logic.*
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
import java.io.File
import java.util.*

/** What the finger does on a drawing: draw, draw one of the shapes, or rub out. */
private enum class DrawTool(val shape: Int, val label: Int) {
    PEN(DrawingShape.FREE, R.string.tools_pen),
    LINE(DrawingShape.LINE, R.string.tools_line),
    ARROW(DrawingShape.ARROW, R.string.tools_arrow),
    RECTANGLE(DrawingShape.RECTANGLE, R.string.tools_rectangle),
    ELLIPSE(DrawingShape.ELLIPSE, R.string.tools_ellipse),
    ERASER(DrawingShape.FREE, R.string.tools_eraser),
}

/** How far from a shape, on screen, a finger still takes it. */
private val SHAPE_REACH = 12.dp

/** The widths the slider offers, in the canvas's pixels, finer where a difference shows more. */
private val STROKE_WIDTHS = listOf(1f, 2f, 4f, 6f, 8f, 11f, 14f, 18f, 24f, 32f)

/** How much wider the eraser clears than its width, in the drawing's pixels: a pixel each side. */
private const val ERASER_MARGIN = 2f

/** The width a drawing starts with, as it always has. */
private const val DEFAULT_STROKE_WIDTH = 8f

/** The shapes, in the order the Shapes button offers them. */
private val SHAPE_TOOLS = listOf(DrawTool.LINE, DrawTool.ARROW, DrawTool.RECTANGLE, DrawTool.ELLIPSE)

/** How far from a selected shape's handle a finger still takes the handle; and how big it is drawn. */
private val SHAPE_HANDLE = 20.dp
private val SHAPE_HANDLE_DRAWN = 7.dp

/**
 * A drawing note. Strokes of the finger, and shapes: a line, an arrow, a rectangle or an ellipse,
 * drawn by dragging from one corner to the other. A shape just drawn, or tapped, is selected: its two
 * handles resize it, a drag on it moves it, the row under the toolbar makes it another shape or takes
 * it out, and a colour or a width chosen meanwhile is its. Each of these is one step for Undo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrawingNoteEditor(note: NotebookNote, notes: List<NotebookNote>, onUpdate: NoteChange, onBack: () -> Unit) {
    var title by remember { mutableStateOf(note.title) }
    // Strokes and the states Undo steps back through, Clear included; see DrawingEdit.
    var edit by remember { mutableStateOf(DrawingEdit(note.drawingPaths)) }
    val currentPaths = edit.paths
    var currentColor by remember { mutableLongStateOf(0xFF000000L) }
    var currentWidth by remember { mutableFloatStateOf(DEFAULT_STROKE_WIDTH) }
    var tool by remember { mutableStateOf(DrawTool.PEN) }
    // The shape the Shapes button draws, and whether its choice is open under the tools.
    var shapeKind by remember { mutableStateOf(DrawTool.RECTANGLE) }
    var choosingShape by remember { mutableStateOf(false) }
    var choosingColor by remember { mutableStateOf(false) }
    // Whether the pieces are open under the tools, to put one on the drawing.
    var choosingPiece by remember { mutableStateOf(false) }
    // And the emojis, as a text note offers them.
    var choosingEmoji by remember { mutableStateOf(false) }
    // The stroke under the finger is a list added to, not copied: each point of a drag made a new list
    // of every point before it. It stops, as the drawing does, where the store stops keeping them:
    // a longer stroke, or more strokes, were cut at the next launch with nothing said.
    val activePoints = remember { mutableStateListOf<Pair<Float, Float>>() }
    // The shape selected, by its place in the drawing, and the shape being drawn or changed under the
    // finger, shown in its place until the finger lifts.
    var selected by remember { mutableStateOf<Int?>(null) }
    var preview by remember { mutableStateOf<DrawingPath?>(null) }
    // The canvas the strokes belong to, and the one on screen: on another, they are drawn to its scale
    // and new ones are taken back to theirs. A drawing from before its canvas was kept takes this one's.
    var drawnOn by remember { mutableStateOf(if (note.drawingWidth > 0f && note.drawingHeight > 0f) androidx.compose.ui.geometry.Size(note.drawingWidth, note.drawingHeight) else null) }
    // The page, that is: the tallest this box has been at its width. The box itself loses height to
    // whatever the toolbar opens -- a selected shape's row, the pieces, the emoji, the keyboard under
    // the title -- and measured as it stood, the whole drawing shrank each time one opened, and a
    // first sticker was sized from the strip its own picker had left. What a panel covers is covered.
    var canvas by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val scale = drawnOn?.let { drawingScale(it.width, it.height, canvas.width.toFloat(), canvas.height.toFloat()) } ?: 1f
    // Read by the gestures as it stands, and not a key of theirs: a key that changes restarts the
    // gesture in hand, with neither its end nor its cancellation called, and the stroke was lost.
    val liveScale by rememberUpdatedState(scale)
    PersistDebounced(Triple(title, currentPaths, drawnOn)) { (newTitle, paths, size) -> onUpdate { it.copy(title = newTitle, drawingPaths = paths, drawingWidth = size?.width ?: it.drawingWidth, drawingHeight = size?.height ?: it.drawingHeight) } }

    fun selectedShape(): DrawingPath? = selected?.let { edit.paths.getOrNull(it) }?.takeIf { it.isSelectable }
    fun changeSelected(change: (DrawingPath) -> DrawingPath) {
        val index = selected ?: return
        val shape = selectedShape() ?: return
        edit = edit.replace(index, change(shape))
    }
    fun noteTheCanvas() {
        if (drawnOn == null && canvas != androidx.compose.ui.unit.IntSize.Zero) drawnOn = androidx.compose.ui.geometry.Size(canvas.width.toFloat(), canvas.height.toFloat())
    }
    // What Undo, Redo and Clear change may not be the shape selected any more.
    fun step(next: DrawingEdit) { edit = next; selected = null }

    // The background: a copy of a photo note's photo, or of a board note's position, drawn under the
    // strokes. A copy, so that the note it came from can be changed or deleted and the drawing keeps
    // what it was drawn on. Placed on the drawing's own canvas, as its strokes are, so that the two
    // stay together on another screen; chosen before anything is drawn, it takes this one's.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var choosingBackground by remember { mutableStateOf(false) }
    // Bumped on each photo taken as the background: a second lands at the same path, which an image
    // cache would otherwise take for the first.
    var backgroundCount by remember { mutableIntStateOf(0) }
    val pieceLoader = remember { ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build() }
    // A piece or an emoji, put in the middle of the drawing, a fifth of it across, and selected.
    fun placeSticker(sticker: DrawingPath) {
        noteTheCanvas()
        val area = drawnOn ?: return
        if (edit.paths.size >= MAX_DRAWING_PATHS) return
        val side = minOf(area.width, area.height) / 5
        val (cx, cy) = area.width / 2 to area.height / 2
        edit = edit.addStroke(sticker.copy(points = listOf((cx - side / 2) to (cy - side / 2), (cx + side / 2) to (cy + side / 2))))
        selected = edit.paths.lastIndex
    }
    fun photoBackground(source: NotebookNote) {
        val from = source.imagePath ?: return
        val before = note.imagePath
        noteTheCanvas()
        scope.launch {
            // Off the main thread: a photo is megabytes.
            val copied = withContext(Dispatchers.IO) { copyBackgroundPhoto(context, note.id, from) } ?: return@launch
            backgroundCount++
            onUpdate { it.copy(imagePath = copied.absolutePath, drawingBoard = false) }
            // The photo it replaces, where that is another file: a drawing restored from a backup
            // keeps its photo under the id it had then, which no later sweep goes by, so it stayed
            // on the disk for good, unshredded.
            shredReplacedMedia(context, before, copied)
        }
    }
    fun boardBackground(source: NotebookNote) {
        noteTheCanvas()
        val photo = note.imagePath
        onUpdate { it.copy(boardPosition = source.boardPosition, boardVariant = source.boardVariant, drawingBoard = true, imagePath = null) }
        shredNoteMedia(context, photo)
    }
    fun noBackground() {
        val photo = note.imagePath
        onUpdate { it.copy(drawingBoard = false, imagePath = null) }
        shredNoteMedia(context, photo)
    }
    if (choosingBackground) BackgroundChoice(
        notes = notes,
        hasBackground = note.imagePath != null || note.drawingBoard,
        onPhoto = { photoBackground(it); choosingBackground = false },
        onBoard = { boardBackground(it); choosingBackground = false },
        onNone = { noBackground(); choosingBackground = false },
        onDismiss = { choosingBackground = false },
    )

    ToolScaffold(title = stringResource(R.string.tools_draw_note), onBack = onBack, actions = {
        IconButton(onClick = { step(edit.undo()) }, enabled = edit.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.tools_undo)) }
        IconButton(onClick = { step(edit.redo()) }, enabled = edit.canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.tools_redo)) }
        IconButton(onClick = { step(edit.clear()) }) { Icon(Icons.Default.DeleteSweep, stringResource(R.string.common_clear)) }
    }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = title, onValueChange = { title = it.take(MAX_NOTE_TITLE_CHARS) }, label = { Text(stringResource(R.string.tools_title)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
            // As wide as the title above it and the page below, as the text note's toolbar is.
            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    // The colours behind one button, as in a text note: a row of them took a line of
                    // the toolbar for something chosen now and then.
                    if (choosingColor) DrawingColorChoice(current = currentColor.takeIf { tool != DrawTool.ERASER }, used = currentPaths.filter { !it.isEraser && !it.isSticker }.map { it.color }, onDismiss = { choosingColor = false }) { colorVal ->
                        currentColor = colorVal
                        if (tool == DrawTool.ERASER) tool = DrawTool.PEN
                        changeSelected { it.copy(color = colorVal) }
                        choosingColor = false
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Another tool is something else to draw: the shape selected is left as it is.
                        DrawToolButton(DrawTool.PEN, tool == DrawTool.PEN) { tool = DrawTool.PEN; selected = null; choosingShape = false; choosingPiece = false; choosingEmoji = false }
                        // The shapes behind one button, which shows the one it draws: a button each
                        // filled the line, with pieces and emojis still to come.
                        DrawToolButton(shapeKind, tool.shape in DrawingShape.SHAPES, label = R.string.tools_shapes) {
                            selected = null
                            choosingPiece = false
                            choosingEmoji = false
                            if (tool.shape in DrawingShape.SHAPES) choosingShape = !choosingShape
                            else { tool = shapeKind; choosingShape = true }
                        }
                        // Pieces put on the drawing, drawn as the board note draws them.
                        val piecesName = stringResource(R.string.tools_insert_pieces)
                        IconToggleButton(
                            checked = choosingPiece,
                            onCheckedChange = { choosingPiece = !choosingPiece; choosingShape = false; choosingEmoji = false; selected = null },
                            modifier = Modifier.semantics { contentDescription = piecesName },
                            colors = IconButtonDefaults.iconToggleButtonColors(checkedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        ) { BoardPiece("N", 28.dp, pieceLoader, Modifier.clearAndSetSemantics { }) }
                        val emojisName = stringResource(R.string.tools_insert_emojis)
                        IconToggleButton(
                            checked = choosingEmoji,
                            onCheckedChange = { choosingEmoji = !choosingEmoji; choosingShape = false; choosingPiece = false; selected = null },
                            modifier = Modifier.semantics { contentDescription = emojisName },
                            colors = IconButtonDefaults.iconToggleButtonColors(checkedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        ) { Text("🙂", fontFamily = OpenMoji, fontSize = 22.sp, modifier = Modifier.clearAndSetSemantics { }) }
                        DrawToolButton(DrawTool.ERASER, tool == DrawTool.ERASER) { tool = DrawTool.ERASER; selected = null; choosingShape = false; choosingPiece = false; choosingEmoji = false }
                        // In the colour it draws in, so that it says which without being opened.
                        IconButton(onClick = { choosingColor = true }) {
                            Icon(Icons.Default.Palette, stringResource(R.string.tools_color), tint = if (Color(currentColor).luminance() > 0.9f) Color.Gray else Color(currentColor))
                        }
                        VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp))
                        // Not a tool: what the drawing is drawn on. Marked when there is one.
                        IconToggleButton(
                            checked = note.imagePath != null || note.drawingBoard,
                            onCheckedChange = { choosingBackground = true },
                            colors = IconButtonDefaults.iconToggleButtonColors(checkedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        ) { Icon(Icons.Default.Wallpaper, stringResource(R.string.tools_background)) }
                    }
                    if (choosingShape) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            SHAPE_TOOLS.forEach { kind ->
                                DrawToolButton(kind, tool == kind) { tool = kind; shapeKind = kind; choosingShape = false }
                            }
                        }
                    }
                    // An emoji chosen, the same; from the picker a text note has.
                    if (choosingEmoji) EmojiPicker(onInsert = { emoji -> placeSticker(emojiSticker(emoji, 0f, 0f, 0f)); choosingEmoji = false })
                    // A piece chosen goes in the middle of the drawing, selected, to be moved and sized.
                    if (choosingPiece) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally)) {
                            DRAWING_PIECES.forEach { piece ->
                                IconButton(onClick = { placeSticker(pieceSticker(piece, 0f, 0f, 0f)); choosingPiece = false }) { BoardPiece(piece, 34.dp, pieceLoader) }
                            }
                        }
                    }
                    // The width, in ten steps from a hairline to a marker's: three sizes were too few.
                    // Beside it the stroke as it will be drawn, its size and its colour. A shape
                    // selected takes the width when the slider is let go, as one step for Undo.
                    val step = STROKE_WIDTHS.indexOf(currentWidth).takeIf { it >= 0 } ?: STROKE_WIDTHS.indexOf(DEFAULT_STROKE_WIDTH)
                    val widthName = stringResource(R.string.tools_width)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = step.toFloat(),
                            onValueChange = { currentWidth = STROKE_WIDTHS[it.roundToInt().coerceIn(STROKE_WIDTHS.indices)] },
                            onValueChangeFinished = { changeSelected { it.copy(strokeWidth = currentWidth / scale) } },
                            valueRange = 0f..(STROKE_WIDTHS.size - 1).toFloat(),
                            steps = STROKE_WIDTHS.size - 2,
                            modifier = Modifier.weight(1f).semantics { contentDescription = widthName },
                        )
                        // A short stroke rather than a dot, which at the finest width was a pixel.
                        val sample = if (tool == DrawTool.ERASER) MaterialTheme.colorScheme.outline else Color(currentColor)
                        Canvas(Modifier.size(width = 48.dp, height = 40.dp)) {
                            val y = size.height / 2
                            // A white stroke still shows, against a ground of its own.
                            if (sample.luminance() > 0.9f) drawRoundRect(Color.LightGray, cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
                            drawLine(sample, Offset(10.dp.toPx(), y), Offset(size.width - 10.dp.toPx(), y), strokeWidth = currentWidth, cap = StrokeCap.Round)
                        }
                    }
                    // The shape selected, made another one or taken out.
                    val shapeNow = selectedShape()
                    if (shapeNow != null) {
                        HorizontalDivider()
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                            // A shape can be made another; a piece is what it is.
                            if (shapeNow.isShape) {
                                SHAPE_TOOLS.forEach { kind ->
                                    DrawToolButton(kind, shapeNow.shape == kind.shape) { changeSelected { it.copy(shape = kind.shape) } }
                                }
                                VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp))
                            }
                            IconButton(onClick = { selected?.let { edit = edit.remove(it) }; selected = null }) { Icon(Icons.Default.Delete, stringResource(R.string.common_delete)) }
                            IconButton(onClick = { selected = null }) { Icon(Icons.Default.Check, stringResource(R.string.tools_shape_done)) }
                        }
                    }
                }
            }
            val handleColor = MaterialTheme.colorScheme.primary
            Box(modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)).onSizeChanged { canvas = if (it.width == canvas.width) androidx.compose.ui.unit.IntSize(it.width, maxOf(it.height, canvas.height)) else it }
                // A tap selects the shape under it, or lets go of the one selected.
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { offset: Offset -> selected = shapeAt(edit.paths, offset.x / liveScale, offset.y / liveScale, SHAPE_REACH.toPx() / liveScale) })
                }
                .pointerInput(tool, currentColor, currentWidth) {
                    var grip: ShapeGrip? = null
                    var origin = 0f to 0f
                    var drawingShape = false
                    fun reset() { grip = null; drawingShape = false; preview = null; activePoints.clear() }
                    detectDragGestures(
                        onDragStart = { offset ->
                            val at = offset.x / liveScale to offset.y / liveScale
                            origin = at
                            // On the shape selected, or on one of its handles: that shape is what moves.
                            grip = selectedShape()?.let { gripAt(it, at.first, at.second, SHAPE_HANDLE.toPx() / liveScale, SHAPE_REACH.toPx() / liveScale) }
                            when {
                                grip != null -> preview = selectedShape()
                                tool.shape in DrawingShape.SHAPES -> {
                                    selected = null
                                    drawingShape = true
                                    preview = shapePath(tool.shape, at, at, currentColor, currentWidth / liveScale)
                                }
                                else -> {
                                    selected = null
                                    activePoints.clear()
                                    activePoints.add(at)
                                }
                            }
                        },
                        onDrag = { change, _ ->
                            val at = change.position.x / liveScale to change.position.y / liveScale
                            val held = grip
                            when {
                                held != null -> preview = selectedShape()?.gripped(held, at.first - origin.first, at.second - origin.second)
                                drawingShape -> preview = preview?.copy(points = listOf(origin, at))
                                activePoints.size < MAX_DRAWING_POINTS -> activePoints.add(at)
                            }
                        },
                        onDragEnd = {
                            val shown = preview
                            val index = selected
                            when {
                                grip != null -> if (shown != null && index != null) edit = edit.replace(index, shown)
                                drawingShape -> {
                                    // A shape too small to see is a slip of the finger.
                                    val big = shown != null && kotlin.math.hypot(shown.shapeEnd.first - shown.shapeStart.first, shown.shapeEnd.second - shown.shapeStart.second) * liveScale > SHAPE_REACH.toPx() / 2
                                    if (shown != null && big && edit.paths.size < MAX_DRAWING_PATHS) {
                                        noteTheCanvas()
                                        edit = edit.addStroke(shown)
                                        selected = edit.paths.lastIndex
                                    }
                                }
                                activePoints.isNotEmpty() && edit.paths.size < MAX_DRAWING_PATHS -> {
                                    noteTheCanvas()
                                    edit = edit.addStroke(DrawingPath(points = activePoints.toList(), color = currentColor, strokeWidth = currentWidth / liveScale, isEraser = tool == DrawTool.ERASER))
                                }
                            }
                            reset()
                        },
                        onDragCancel = { reset() },
                    )
                }) {
                // Each finished stroke's Path is built once, and kept for as long as the stroke is in the
                // drawing. They were all built again at every frame -- every point of every stroke, for
                // each point the finger added to the one being drawn. By identity: a stroke is never
                // changed, only added, replaced, removed by Undo or cleared.
                val builtPaths = remember { java.util.IdentityHashMap<DrawingPath, Path>() }
                val strokes = remember(currentPaths) {
                    builtPaths.keys.retainAll(java.util.Collections.newSetFromMap(java.util.IdentityHashMap<DrawingPath, Boolean>()).apply { addAll(currentPaths) })
                    currentPaths.withIndex().filter { it.value.points.size > 1 }.map { (i, stroke) -> Triple(i, stroke, builtPaths.getOrPut(stroke) { pathOf(stroke) }) }
                }
                // The eraser takes away what is under it. It painted white instead: right on a white page
                // and wrong on any other -- a white stroke over the background, and over whatever comes
                // to be drawn beneath the strokes. The strokes are drawn on a layer of their own, where
                // the eraser clears them, and the page shows through. Drawings already made keep their
                // eraser strokes, and look the same on the white page.
                // Under the strokes' layer, which is where the eraser clears: the background is never rubbed out.
                DrawingBackground(note, backgroundCount, drawnOn?.let { androidx.compose.ui.geometry.Size(it.width * scale, it.height * scale) })
                val changing = preview != null && selected != null
                Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                    scale(scale, pivot = Offset.Zero) {
                        strokes.forEach { (i, data, path) -> if (!data.isSticker && !(changing && i == selected)) drawStroke(path, data.isEraser, Color(data.color), data.strokeWidth) }
                        preview?.takeIf { !it.isSticker }?.let { drawStroke(pathOf(it), false, Color(it.color), it.strokeWidth) }
                        // The stroke under the finger alone is built again as it grows.
                        if (activePoints.size > 1) drawStroke(strokePath(activePoints), tool == DrawTool.ERASER, Color(currentColor), currentWidth / scale)
                    }
                }
                // Pieces over the strokes: pictures rather than paint, which the eraser leaves alone;
                // Delete takes one out.
                currentPaths.forEachIndexed { i, path -> if (path.isSticker && !(changing && i == selected)) StickerOnDrawing(path, scale, pieceLoader) }
                preview?.takeIf { it.isSticker }?.let { StickerOnDrawing(it, scale, pieceLoader) }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    scale(scale, pivot = Offset.Zero) {
                        // The shape or piece selected: its two handles, the size of a fingertip's aim,
                        // over everything, a piece included.
                        (if (changing) preview else selectedShape())?.let { shape ->
                            val r = SHAPE_HANDLE_DRAWN.toPx() / scale
                            for ((x, y) in listOf(shape.shapeStart, shape.shapeEnd)) {
                                drawCircle(Color.White, r, Offset(x, y))
                                drawCircle(handleColor, r, Offset(x, y), style = Stroke(width = 2.dp.toPx() / scale))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A drawing tool's button: its own small picture, its name for TalkBack. */
@Composable
private fun DrawToolButton(tool: DrawTool, on: Boolean, label: Int = tool.label, onClick: () -> Unit) {
    val name = stringResource(label)
    IconToggleButton(
        checked = on,
        onCheckedChange = { onClick() },
        modifier = Modifier.semantics { contentDescription = name },
        colors = IconButtonDefaults.iconToggleButtonColors(checkedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
    ) { DrawToolGlyph(tool, LocalContentColor.current) }
}

/**
 * A tool's picture, drawn: the icon set has nothing for an arrow at an angle, an outlined box or an
 * ellipse, and its eraser-like icons were a magic wand, which nobody read as an eraser.
 */
@Composable
private fun DrawToolGlyph(tool: DrawTool, color: Color) {
    if (tool == DrawTool.PEN) {
        Icon(Icons.Default.Edit, contentDescription = null, tint = color)
        return
    }
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val line = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (tool) {
            DrawTool.LINE -> drawLine(color, Offset(5 * u, 19 * u), Offset(19 * u, 5 * u), strokeWidth = 2f * u, cap = StrokeCap.Round)
            DrawTool.ARROW -> {
                drawLine(color, Offset(5 * u, 19 * u), Offset(19 * u, 5 * u), strokeWidth = 2f * u, cap = StrokeCap.Round)
                drawPath(Path().apply { moveTo(11 * u, 5 * u); lineTo(19 * u, 5 * u); lineTo(19 * u, 13 * u) }, color, style = line)
            }
            DrawTool.RECTANGLE -> drawRect(color, Offset(4 * u, 6 * u), androidx.compose.ui.geometry.Size(16 * u, 12 * u), style = line)
            DrawTool.ELLIPSE -> drawOval(color, Offset(3 * u, 6 * u), androidx.compose.ui.geometry.Size(18 * u, 12 * u), style = line)
            else -> {
                // An eraser at a slant, its rubber end filled, over the line it rubs on.
                rotate(-45f, pivot = Offset(12 * u, 11 * u)) {
                    val corner = androidx.compose.ui.geometry.CornerRadius(2f * u)
                    drawRoundRect(color, Offset(3 * u, 7 * u), androidx.compose.ui.geometry.Size(18 * u, 8 * u), corner, style = line)
                    drawRoundRect(color, Offset(3 * u, 7 * u), androidx.compose.ui.geometry.Size(7 * u, 8 * u), corner)
                }
                drawLine(color, Offset(9 * u, 21 * u), Offset(21 * u, 21 * u), strokeWidth = 2f * u, cap = StrokeCap.Round)
            }
        }
    }
}

/**
 * One stroke of a drawing: in its colour, or, for the eraser, clearing what was drawn before it.
 *
 * The eraser clears a little wider than it is drawn, [ERASER_MARGIN] more: a stroke's smoothed edge
 * reaches past its width, and an eraser as wide, run along it, left that edge as a faint outline.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(path: Path, eraser: Boolean, color: Color, width: Float) =
    drawPath(
        path = path,
        color = if (eraser) Color.Black else color,
        style = Stroke(width = if (eraser) width + ERASER_MARGIN else width, cap = StrokeCap.Round, join = StrokeJoin.Round),
        blendMode = if (eraser) androidx.compose.ui.graphics.BlendMode.Clear else androidx.compose.ui.graphics.BlendMode.SrcOver,
    )

/** What [stroke] draws: its shape, from its first point to its last, or the line through its points. */
private fun pathOf(stroke: DrawingPath): Path {
    if (!stroke.isShape) return strokePath(stroke.points)
    val (ax, ay) = stroke.shapeStart
    val (bx, by) = stroke.shapeEnd
    return Path().apply {
        when (stroke.shape) {
            DrawingShape.RECTANGLE -> addRect(androidx.compose.ui.geometry.Rect(minOf(ax, bx), minOf(ay, by), maxOf(ax, bx), maxOf(ay, by)))
            DrawingShape.ELLIPSE -> addOval(androidx.compose.ui.geometry.Rect(minOf(ax, bx), minOf(ay, by), maxOf(ax, bx), maxOf(ay, by)))
            else -> {
                moveTo(ax, ay)
                lineTo(bx, by)
                if (stroke.shape == DrawingShape.ARROW) for ((x, y) in arrowHead(stroke.shapeStart, stroke.shapeEnd, stroke.strokeWidth)) {
                    moveTo(bx, by)
                    lineTo(x, y)
                }
            }
        }
    }
}

/** A stroke's points as one line from the first to the last. */
private fun strokePath(points: List<Pair<Float, Float>>): Path = Path().apply {
    val first = points.first()
    moveTo(first.first, first.second)
    for (i in 1 until points.size) lineTo(points[i].first, points[i].second)
}

/**
 * The choice of a drawing's background: a photo note's photo, a board note's position, or none.
 * Each source note by its title; a photo by its picture as well.
 */
@Composable
private fun BackgroundChoice(
    notes: List<NotebookNote>,
    hasBackground: Boolean,
    onPhoto: (NotebookNote) -> Unit,
    onBoard: (NotebookNote) -> Unit,
    onNone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val photos = notes.filter { it.type == NotebookNoteType.IMAGE && it.imagePath != null }
    val boards = notes.filter { it.type == NotebookNoteType.BOARD }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tools_background)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (hasBackground) item { BackgroundRow(stringResource(R.string.tools_background_none), onClick = onNone) { Icon(Icons.Default.HideImage, null) } }
                if (photos.isEmpty() && boards.isEmpty()) item { Text(stringResource(R.string.tools_background_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (photos.isNotEmpty()) item { BackgroundHeading(R.string.tools_image) }
                items(photos, key = { "photo-" + it.id }) { photo ->
                    BackgroundRow(photo.title, onClick = { onPhoto(photo) }) {
                        AsyncImage(model = photo.imagePath, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)))
                    }
                }
                if (boards.isNotEmpty()) item { BackgroundHeading(R.string.tools_board) }
                items(boards, key = { "board-" + it.id }) { board ->
                    BackgroundRow(board.title, onClick = { onBoard(board) }) { Icon(Icons.Default.Grid4x4, null) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
}

@Composable
private fun BackgroundHeading(label: Int) {
    Text(stringResource(label), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
}

@Composable
private fun BackgroundRow(label: String, onClick: () -> Unit, leading: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { leading() }
        Text(label, maxLines = 2)
    }
}

/**
 * A drawing's background, in [area] -- the drawing's own canvas as it is shown, from the top left
 * corner, as its strokes are -- or the whole page when the drawing has none yet. A photo is shown
 * whole; a board as large as fits, centred.
 */
@Composable
private fun DrawingBackground(note: NotebookNote, version: Int, area: androidx.compose.ui.geometry.Size?) {
    val photo = note.imagePath
    if (photo == null && !note.drawingBoard) return
    val context = LocalContext.current
    val place = if (area == null) Modifier.fillMaxSize() else with(LocalDensity.current) { Modifier.size(area.width.toDp(), area.height.toDp()) }
    Box(place, contentAlignment = Alignment.Center) {
        if (photo != null) {
            // Keyed on the file's time as well as its path: a new background lands at the same path.
            val model = remember(photo, version) {
                val key = "$photo#${File(photo).lastModified()}"
                ImageRequest.Builder(context).data(photo).memoryCacheKey(key).diskCacheKey(key).build()
            }
            AsyncImage(model = model, contentDescription = stringResource(R.string.tools_background), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        } else {
            BoardBackground(note.boardPosition, note.boardVariant)
        }
    }
}

/** A board note's position, drawn as the board note draws it, but not to be touched. */
@Composable
private fun BoardBackground(position: List<String>, variant: BoardNoteVariant) {
    val context = LocalContext.current
    val imageLoader = remember { ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build() }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val square = minOf(maxWidth, maxHeight) / variant.side
        Column {
            for (rank in variant.side - 1 downTo 0) {
                Row {
                    for (file in 0 until variant.side) {
                        val index = rank * variant.side + file
                        Box(Modifier.size(square).background(if ((rank + file) % 2 == 0) BOARD_DARK else BOARD_LIGHT), contentAlignment = Alignment.Center) {
                            val piece = position.getOrElse(index) { "" }
                            if (piece.isNotEmpty()) BoardPiece(piece, square * 4 / 5, imageLoader)
                        }
                    }
                }
            }
        }
    }
}

/**
 * [sourcePath], a photo note's photo, copied as [drawingId]'s background: into the drawing's own
 * photo file, written aside and moved into place as a camera take is (see commitTake). Null when
 * there was nothing to copy.
 */
private fun copyBackgroundPhoto(context: android.content.Context, drawingId: String, sourcePath: String): File? {
    val target = File(File(context.filesDir, "shares").apply { mkdirs() }, "image_$drawingId.jpg")
    val source = File(sourcePath)
    if (!source.isFile || runCatching { source.canonicalPath == target.canonicalPath }.getOrDefault(true)) return null
    val pending = pendingMediaFile(target)
    return try {
        source.copyTo(pending, overwrite = true)
        if (commitTake(context, pending, target)) target else null
    } catch (_: Exception) {
        pending.delete()
        null
    }
}

/**
 * A piece or an emoji on a drawing, at its place and size on screen, kept square in its box; see
 * pieceSquare. An emoji in OpenMoji, as a text note draws it -- but from OpenMoji's drawing of it
 * rather than its font. The font holds each emoji as a picture of so many pixels, which is all a
 * line of text asks; a sticker is dragged to any size, and enlarged it blurred. The drawings are
 * the vectors, loaded as the pieces are; tools/openmoji_svg.py puts them in the assets.
 */
@Composable
private fun StickerOnDrawing(sticker: DrawingPath, scale: Float, loader: ImageLoader) {
    val (left, top, side) = sticker.pieceSquare()
    val density = LocalDensity.current
    val size = with(density) { (side * scale).toDp() }
    val place = Modifier.offset { IntOffset((left * scale).roundToInt(), (top * scale).roundToInt()) }
    // An emoji with no drawing here: one from a note made by a later version, with a newer
    // catalog. It is drawn from the font, as every emoji was.
    var undrawn by remember(sticker.label) { mutableStateOf(false) }
    if (sticker.isPiece) {
        BoardPiece(sticker.label, size, loader, place)
    } else if (!undrawn) {
        AsyncImage(
            model = "file:///android_asset/emojis/${emojiDrawingName(sticker.label)}.svg",
            // The emoji itself, which TalkBack names, as it did when this was a text.
            contentDescription = sticker.label,
            modifier = place.size(size),
            imageLoader = loader,
            onError = { undrawn = true },
        )
    } else {
        Box(place.size(size), contentAlignment = Alignment.Center) {
            // A little under the box, as an emoji's glyph has room around it.
            val fontSize = with(density) { (side * scale * 0.82f).toSp() }
            Text(
                sticker.label,
                fontFamily = OpenMoji,
                fontSize = fontSize,
                lineHeight = fontSize,
                style = androidx.compose.ui.text.TextStyle(platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)),
            )
        }
    }
}

/**
 * The colours a drawing is drawn in, by rows of a family: the neutrals, brown among them for the
 * wood of a board and its pieces; the warm ones; the cool ones, which leave the custom colour the
 * last place. In the order they came, eleven in a heap over three lines read as a muddle.
 */
private val DRAWING_COLOR_ROWS = listOf(
    listOf(0xFF000000, 0xFF9E9E9E, 0xFFFFFFFF, 0xFF795548),
    listOf(0xFFF44336, 0xFFFF9800, 0xFFFFEB3B, 0xFFE91E63),
    listOf(0xFF4CAF50, 0xFF2196F3, 0xFF9C27B0),
)
private val DRAWING_COLORS = DRAWING_COLOR_ROWS.flatten()

/**
 * The choice of a drawing's colour: [current] ringed, none with the eraser held. Custom colours the
 * drawing already has come first, read from its strokes, so that one can be taken up again; the last
 * circle opens the picker the settings have.
 */
@Composable
private fun DrawingColorChoice(current: Long?, used: List<Long>, onDismiss: () -> Unit, onChoose: (Long) -> Unit) {
    var custom by remember { mutableStateOf(false) }
    if (custom) {
        ColorPickerDialog(initialColor = Color(current ?: 0xFF000000), onDismiss = { custom = false }) { chosen ->
            custom = false
            onChoose(chosen.toArgb().toLong() and 0xFFFFFFFFL)
        }
        return
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(16.dp)) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.tools_color), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                @Composable
                fun Swatch(value: Long) {
                    val chosen = value == current
                    Surface(
                        modifier = Modifier.size(40.dp).clickable { onChoose(value) }.testTag("drawing_color_$value"),
                        shape = CircleShape,
                        color = Color(value),
                        border = BorderStroke(if (chosen) 3.dp else 1.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    ) {}
                }
                // The custom colours the drawing already has, apart, so the families keep their rows.
                val mine = customColoursIn(used, DRAWING_COLORS)
                if (mine.isNotEmpty()) {
                    Text(stringResource(R.string.tools_custom_colours), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Start))
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.Start)) { mine.forEach { Swatch(it) } }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DRAWING_COLOR_ROWS.forEachIndexed { i, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { Swatch(it) }
                            if (i == DRAWING_COLOR_ROWS.lastIndex) CustomColourButton(Modifier.testTag("drawing_color_custom")) { custom = true }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The colours of [used] that are none of [presets], the most recent last, at most six: what a custom
 * colour chosen before looks like when it is offered again.
 */
internal fun customColoursIn(used: List<Long>, presets: List<Long>): List<Long> =
    used.filter { it != 0L && it !in presets }.distinct().takeLast(6)

/** The last circle of a colour choice: any colour, through the picker the settings have. */
@Composable
internal fun CustomColourButton(modifier: Modifier = Modifier, size: Dp = 40.dp, onClick: () -> Unit) {
    val name = stringResource(R.string.settings_select_custom_color)
    Surface(
        modifier = modifier.size(size).clickable(onClick = onClick).semantics { contentDescription = name },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, null) } }
}
