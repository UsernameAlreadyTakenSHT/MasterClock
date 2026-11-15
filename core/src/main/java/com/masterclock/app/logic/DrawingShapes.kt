package com.masterclock.app.logic

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * The shapes a drawing note can hold beside strokes of the finger. A shape is a [DrawingPath] of two
 * points, where it was started and where it was let go: a line or an arrow runs between them, a
 * rectangle or an ellipse fills the box they span. Two points are all it takes to move it, to resize
 * it, or to make it another shape, which is what the editor does with it once drawn.
 */
object DrawingShape {
    const val FREE = 0
    const val LINE = 1
    const val ARROW = 2
    const val RECTANGLE = 3
    const val ELLIPSE = 4
    /** A piece's picture, [DrawingPath.label] naming it, in the box of its two points. */
    const val PIECE = 5
    /** An emoji, [DrawingPath.label] holding it, in the box of its two points. */
    const val EMOJI = 6
    val SHAPES = LINE..ELLIPSE
    val KNOWN = FREE..EMOJI
}

/** The shapes that are pictures named by their label rather than drawn. */
val STICKER_SHAPES = setOf(DrawingShape.PIECE, DrawingShape.EMOJI)

/** No emoji is longer than this, a family of four with its skin tones included. */
private const val MAX_EMOJI_CHARS = 32

/** Whether [text] is one emoji and nothing else, as a drawing's emoji must be. */
fun isOneEmoji(text: String): Boolean =
    text.length in 1..MAX_EMOJI_CHARS && emojiRanges(text).singleOrNull() == 0 until text.length

/**
 * The pieces a drawing can have put on it, as a board note writes them: chess, white in capitals,
 * then draughts, a man and a dame of each colour.
 */
val DRAWING_PIECES = listOf("K", "Q", "R", "B", "N", "P", "k", "q", "r", "b", "n", "p", "M", "D", "m", "d")

/** Whether [this] is a shape -- a line, an arrow, a rectangle or an ellipse -- rather than a stroke. */
val DrawingPath.isShape: Boolean get() = shape in DrawingShape.SHAPES && points.size >= 2 && !isEraser

/** Whether [this] is a piece put on the drawing, a picture in a box rather than something drawn. */
val DrawingPath.isPiece: Boolean get() = shape == DrawingShape.PIECE && points.size >= 2 && !isEraser && label in DRAWING_PIECES

/** Whether [this] is an emoji put on the drawing, as a piece is. */
val DrawingPath.isEmoji: Boolean get() = shape == DrawingShape.EMOJI && points.size >= 2 && !isEraser && isOneEmoji(label)

/** A picture put on the drawing, a piece or an emoji, rather than something drawn. */
val DrawingPath.isSticker: Boolean get() = isPiece || isEmoji

/** What the editor can select, move and resize: a shape, a piece or an emoji. */
val DrawingPath.isSelectable: Boolean get() = isShape || isSticker

/** An emoji put on a drawing, [side] across, centred on [centreX], [centreY]. */
fun emojiSticker(emoji: String, centreX: Float, centreY: Float, side: Float) =
    pieceSticker(emoji, centreX, centreY, side).copy(shape = DrawingShape.EMOJI)

/** A piece put on a drawing, [side] across, centred on [centreX], [centreY]. */
fun pieceSticker(piece: String, centreX: Float, centreY: Float, side: Float) = DrawingPath(
    points = listOf((centreX - side / 2) to (centreY - side / 2), (centreX + side / 2) to (centreY + side / 2)),
    shape = DrawingShape.PIECE,
    label = piece,
)

/**
 * Where a piece's picture goes in its box: the largest square that fits, centred, so that a box
 * stretched one way does not stretch the piece. Left, top and side.
 */
fun DrawingPath.pieceSquare(): Triple<Float, Float, Float> {
    val (ax, ay) = shapeStart
    val (bx, by) = shapeEnd
    val side = minOf(abs(bx - ax), abs(by - ay))
    return Triple((ax + bx) / 2 - side / 2, (ay + by) / 2 - side / 2, side)
}

val DrawingPath.shapeStart: Pair<Float, Float> get() = points.first()
val DrawingPath.shapeEnd: Pair<Float, Float> get() = points.last()

/** A shape from [start] to [end]. */
fun shapePath(shape: Int, start: Pair<Float, Float>, end: Pair<Float, Float>, color: Long, width: Float) =
    DrawingPath(points = listOf(start, end), color = color, strokeWidth = width, shape = shape)

/** What of a selected shape a finger has taken hold of. */
enum class ShapeGrip { START, END, BODY }

/**
 * What of [shape] a finger put down at [x], [y] holds: one of its two handles, within [handle] of
 * it, or the shape itself, within [reach] of its line. Null when it is neither.
 */
fun gripAt(shape: DrawingPath, x: Float, y: Float, handle: Float, reach: Float): ShapeGrip? = when {
    distance(shape.shapeEnd, x, y) <= handle -> ShapeGrip.END
    distance(shape.shapeStart, x, y) <= handle -> ShapeGrip.START
    touches(shape, x, y, reach) -> ShapeGrip.BODY
    else -> null
}

/** [shape] with what [grip] holds taken from where the finger went down by [dx], [dy]. */
fun DrawingPath.gripped(grip: ShapeGrip, dx: Float, dy: Float): DrawingPath {
    fun Pair<Float, Float>.by() = (first + dx) to (second + dy)
    return copy(points = when (grip) {
        ShapeGrip.START -> listOf(shapeStart.by(), shapeEnd)
        ShapeGrip.END -> listOf(shapeStart, shapeEnd.by())
        ShapeGrip.BODY -> listOf(shapeStart.by(), shapeEnd.by())
    })
}

/**
 * Whether a tap at [x], [y] is on [shape], within [reach] of what is drawn: the line of a line or
 * an arrow, the outline of a rectangle or an ellipse, or anywhere inside these two, which are hard
 * to tap on their edge when thin.
 */
fun touches(shape: DrawingPath, x: Float, y: Float, reach: Float): Boolean {
    if (!shape.isSelectable) return false
    val (ax, ay) = shape.shapeStart
    val (bx, by) = shape.shapeEnd
    val r = reach + shape.strokeWidth / 2
    return when (shape.shape) {
        DrawingShape.LINE, DrawingShape.ARROW -> segmentDistance(ax, ay, bx, by, x, y) <= r
        else -> x in min(ax, bx) - r..max(ax, bx) + r && y in min(ay, by) - r..max(ay, by) + r
    }
}

/** The topmost shape of [paths] at [x], [y], by its index; null where there is none. */
fun shapeAt(paths: List<DrawingPath>, x: Float, y: Float, reach: Float): Int? =
    paths.indices.reversed().firstOrNull { touches(paths[it], x, y, reach) }

/**
 * The two strokes of an arrow's head at [end], coming from [start]: as long as the line allows, and
 * no shorter than what reads as an arrow at [width].
 */
fun arrowHead(start: Pair<Float, Float>, end: Pair<Float, Float>, width: Float): List<Pair<Float, Float>> {
    val dx = end.first - start.first
    val dy = end.second - start.second
    val length = hypot(dx, dy)
    if (length == 0f) return emptyList()
    val head = min(max(width * 4f, 16f), length / 2f)
    val ux = dx / length
    val uy = dy / length
    // 30 degrees either side of the line, back from its end.
    val cos = 0.8660254f
    val sin = 0.5f
    val left = (end.first - head * (ux * cos - uy * sin)) to (end.second - head * (uy * cos + ux * sin))
    val right = (end.first - head * (ux * cos + uy * sin)) to (end.second - head * (uy * cos - ux * sin))
    return listOf(left, right)
}

private fun distance(p: Pair<Float, Float>, x: Float, y: Float) = hypot(p.first - x, p.second - y)

private fun segmentDistance(ax: Float, ay: Float, bx: Float, by: Float, x: Float, y: Float): Float {
    val dx = bx - ax
    val dy = by - ay
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared == 0f) return hypot(x - ax, y - ay)
    val t = (((x - ax) * dx + (y - ay) * dy) / lengthSquared).coerceIn(0f, 1f)
    return abs(hypot(x - (ax + t * dx), y - (ay + t * dy)))
}
