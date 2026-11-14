package com.masterclock.app.ui.screens

import androidx.compose.animation.core.*
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
import androidx.compose.ui.unit.dp
import com.masterclock.app.R
import com.masterclock.app.logic.*
import kotlinx.coroutines.launch
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
fun DrawingNoteEditor(note: NotebookNote, onUpdate: NoteChange, onBack: () -> Unit) {
    var title by remember { mutableStateOf(note.title) }
    // Strokes and the states Undo steps back through, Clear included; see DrawingEdit.
    var edit by remember { mutableStateOf(DrawingEdit(note.drawingPaths)) }
    val currentPaths = edit.paths
    var currentColor by remember { mutableLongStateOf(0xFF000000L) }
    var currentWidth by remember { mutableFloatStateOf(8f) }
    var tool by remember { mutableStateOf(DrawTool.PEN) }
    // The shape the Shapes button draws, and whether its choice is open under the tools.
    var shapeKind by remember { mutableStateOf(DrawTool.RECTANGLE) }
    var choosingShape by remember { mutableStateOf(false) }
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
    var canvas by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val scale = drawnOn?.let { drawingScale(it.width, it.height, canvas.width.toFloat(), canvas.height.toFloat()) } ?: 1f
    PersistDebounced(Triple(title, currentPaths, drawnOn)) { (newTitle, paths, size) -> onUpdate { it.copy(title = newTitle, drawingPaths = paths, drawingWidth = size?.width ?: it.drawingWidth, drawingHeight = size?.height ?: it.drawingHeight) } }

    fun selectedShape(): DrawingPath? = selected?.let { edit.paths.getOrNull(it) }?.takeIf { it.isShape }
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val colors = listOf(0xFF4CAF50, 0xFF2196F3, 0xFFF44336, 0xFFFFEB3B, 0xFFFF9800, 0xFF000000, 0xFF9E9E9E, 0xFFFFFFFF)
                        colors.forEach { colorVal ->
                            val chosen = currentColor == colorVal && tool != DrawTool.ERASER
                            Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(colorVal)).border(width = if (chosen) 2.dp else 0.5.dp, color = if (chosen) MaterialTheme.colorScheme.primary else Color.Gray, shape = CircleShape).clickable {
                                currentColor = colorVal
                                if (tool == DrawTool.ERASER) tool = DrawTool.PEN
                                changeSelected { it.copy(color = colorVal) }
                            })
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Another tool is something else to draw: the shape selected is left as it is.
                        DrawToolButton(DrawTool.PEN, tool == DrawTool.PEN) { tool = DrawTool.PEN; selected = null; choosingShape = false }
                        // The shapes behind one button, which shows the one it draws: a button each
                        // filled the line, with pieces and emojis still to come.
                        DrawToolButton(shapeKind, tool.shape in DrawingShape.SHAPES, label = R.string.tools_shapes) {
                            selected = null
                            if (tool.shape in DrawingShape.SHAPES) choosingShape = !choosingShape
                            else { tool = shapeKind; choosingShape = true }
                        }
                        DrawToolButton(DrawTool.ERASER, tool == DrawTool.ERASER) { tool = DrawTool.ERASER; selected = null; choosingShape = false }
                    }
                    if (choosingShape) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            SHAPE_TOOLS.forEach { kind ->
                                DrawToolButton(kind, tool == kind) { tool = kind; shapeKind = kind; choosingShape = false }
                            }
                        }
                    }
                    // The widths on a line of their own: after the tools, one was left at the end of
                    // their line and the other two alone under it.
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf(2f, 8f, 20f).forEach { size ->
                            IconButton(onClick = { currentWidth = size; changeSelected { it.copy(strokeWidth = size / scale) } }, modifier = Modifier.size(40.dp).background(if (currentWidth == size) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent, CircleShape)) {
                                Box(Modifier.size((size / 2 + 4).dp).background(MaterialTheme.colorScheme.onSurface, CircleShape))
                            }
                        }
                    }
                    // The shape selected, made another one or taken out.
                    val shapeNow = selectedShape()
                    if (shapeNow != null) {
                        HorizontalDivider()
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                            SHAPE_TOOLS.forEach { kind ->
                                DrawToolButton(kind, shapeNow.shape == kind.shape) { changeSelected { it.copy(shape = kind.shape) } }
                            }
                            VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp))
                            IconButton(onClick = { selected?.let { edit = edit.remove(it) }; selected = null }) { Icon(Icons.Default.Delete, stringResource(R.string.common_delete)) }
                            IconButton(onClick = { selected = null }) { Icon(Icons.Default.Check, stringResource(R.string.tools_shape_done)) }
                        }
                    }
                }
            }
            val handleColor = MaterialTheme.colorScheme.primary
            Box(modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)).onSizeChanged { canvas = it }
                // A tap selects the shape under it, or lets go of the one selected.
                .pointerInput(scale) {
                    detectTapGestures(onTap = { offset: Offset -> selected = shapeAt(edit.paths, offset.x / scale, offset.y / scale, SHAPE_REACH.toPx() / scale) })
                }
                .pointerInput(tool, currentColor, currentWidth, scale) {
                    var grip: ShapeGrip? = null
                    var origin = 0f to 0f
                    var drawingShape = false
                    fun reset() { grip = null; drawingShape = false; preview = null; activePoints.clear() }
                    detectDragGestures(
                        onDragStart = { offset ->
                            val at = offset.x / scale to offset.y / scale
                            origin = at
                            // On the shape selected, or on one of its handles: that shape is what moves.
                            grip = selectedShape()?.let { gripAt(it, at.first, at.second, SHAPE_HANDLE.toPx() / scale, SHAPE_REACH.toPx() / scale) }
                            when {
                                grip != null -> preview = selectedShape()
                                tool.shape in DrawingShape.SHAPES -> {
                                    selected = null
                                    drawingShape = true
                                    preview = shapePath(tool.shape, at, at, currentColor, currentWidth / scale)
                                }
                                else -> {
                                    selected = null
                                    activePoints.clear()
                                    activePoints.add(at)
                                }
                            }
                        },
                        onDrag = { change, _ ->
                            val at = change.position.x / scale to change.position.y / scale
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
                                    val big = shown != null && kotlin.math.hypot(shown.shapeEnd.first - shown.shapeStart.first, shown.shapeEnd.second - shown.shapeStart.second) * scale > SHAPE_REACH.toPx() / 2
                                    if (shown != null && big && edit.paths.size < MAX_DRAWING_PATHS) {
                                        noteTheCanvas()
                                        edit = edit.addStroke(shown)
                                        selected = edit.paths.lastIndex
                                    }
                                }
                                activePoints.isNotEmpty() && edit.paths.size < MAX_DRAWING_PATHS -> {
                                    noteTheCanvas()
                                    edit = edit.addStroke(DrawingPath(points = activePoints.toList(), color = currentColor, strokeWidth = currentWidth / scale, isEraser = tool == DrawTool.ERASER))
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
                Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                    scale(scale, pivot = Offset.Zero) {
                        val changing = preview != null && selected != null
                        strokes.forEach { (i, data, path) -> if (!(changing && i == selected)) drawStroke(path, data.isEraser, Color(data.color), data.strokeWidth) }
                        preview?.let { drawStroke(pathOf(it), false, Color(it.color), it.strokeWidth) }
                        // The stroke under the finger alone is built again as it grows.
                        if (activePoints.size > 1) drawStroke(strokePath(activePoints), tool == DrawTool.ERASER, Color(currentColor), currentWidth / scale)
                        // The shape selected: its two handles, the size of a fingertip's aim.
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

/** One stroke of a drawing: in its colour, or, for the eraser, clearing what was drawn before it. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(path: Path, eraser: Boolean, color: Color, width: Float) =
    drawPath(
        path = path,
        color = if (eraser) Color.Black else color,
        style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round),
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
