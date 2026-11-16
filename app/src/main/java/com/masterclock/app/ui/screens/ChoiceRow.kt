package com.masterclock.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/** The check mark of the chosen segment and the gap after it, which every segment is sized for. */
private val CheckSize = 18.dp
private val CheckGap = 6.dp
private val SegmentPadding = 10.dp
private val SegmentMinHeight = 40.dp
private const val CHOSEN = "chosen"

/**
 * A row of segments to choose one from, which stays one row while every label fits on a line and
 * breaks into several rows once one does not.
 *
 * Material's segmented row gives each segment an equal share whatever its label needs. At a large
 * font, on a narrow screen, or with a long word -- "Negativ / e", "Linéair / e", "Umgeke / hrt" --
 * the label broke where its share ran out, in the middle of a word, and that segment grew taller
 * than its neighbours. Here each segment is asked the width its label takes unbroken; the row holds
 * as many per line as can all have it, in lines of equal length, and every segment is as tall as
 * the tallest.
 *
 * Which segment is chosen never changes the arrangement: a row that fitted or broke by where the
 * check mark was would move under the finger that chose. One line holds while the bare labels fit,
 * and the chosen segment shows its check mark only if its share has room for it; the fill tells
 * the choice either way. Broken into lines, every segment is sized for the mark.
 *
 * The outline is the row's and the segments are plain rectangles, which is what lets the same
 * segments sit in one line or in a grid: Material's take a shape each from their place in the line.
 */
@Composable
fun ChoiceRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val outline = MaterialTheme.colorScheme.outline
    val shape = RoundedCornerShape(20.dp)
    Layout(
        content = content,
        modifier = modifier.selectableGroup().clip(shape).border(1.dp, outline, shape),
    ) { measurables, constraints ->
        if (measurables.isEmpty()) return@Layout layout(constraints.minWidth, constraints.minHeight) {}
        val count = measurables.size
        // Neighbours share a border: each segment draws its own, one over the other.
        val overlap = 1.dp.roundToPx()
        val checkRoom = (CheckSize + CheckGap).roundToPx()
        // The chosen segment's own width counts its mark; the bare label is what is left.
        val bare = measurables.maxOf { m ->
            m.maxIntrinsicWidth(Constraints.Infinity) - if (m.layoutId == CHOSEN) checkRoom else 0
        }
        val wanted = bare + checkRoom
        val bounded = constraints.hasBoundedWidth
        fun share(perLine: Int) = (constraints.maxWidth + overlap * (perLine - 1)) / perLine
        // Lines of equal length: 4 goes to 2 and 2, never 3 and 1.
        var perLine = count
        if (bounded && bare > share(count)) {
            for (lines in 2..count) {
                perLine = ceil(count / lines.toFloat()).toInt()
                if (wanted <= share(perLine)) break
            }
        }
        val natural = wanted * perLine - overlap * (perLine - 1)
        val width = if (bounded && perLine < count) constraints.maxWidth else natural.coerceIn(constraints.minWidth, constraints.maxWidth)
        val lines = ceil(count / perLine.toFloat()).toInt()
        // The last line may be shorter; its segments share the whole width all the same.
        val widths = List(count) { i ->
            val inLine = if (i / perLine == lines - 1) count - perLine * (lines - 1) else perLine
            val column = i % perLine
            val cell = (width + overlap * (inLine - 1)) / inLine
            if (column == inLine - 1) width - (cell - overlap) * column else cell
        }
        val height = measurables.mapIndexed { i, m -> m.maxIntrinsicHeight(widths[i]) }.max()
        val placeables = measurables.mapIndexed { i, m -> m.measure(Constraints.fixed(widths[i], height)) }
        layout(width, height * lines - overlap * (lines - 1)) {
            var x = 0
            placeables.forEachIndexed { i, p ->
                if (i % perLine == 0) x = 0
                p.placeRelative(x, (i / perLine) * (height - overlap))
                x += p.width - overlap
            }
        }
    }
}

/**
 * One segment of a [ChoiceRow]. [shape] is taken and not used: it is what Material's segment was
 * given from its place in the line, and the row draws the outline here.
 */
@Composable
fun ChoiceSegment(
    selected: Boolean,
    onClick: () -> Unit,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val content = (if (selected) colors.onSecondaryContainer else colors.onSurface).copy(alpha = if (enabled) 1f else 0.38f)
    Box(
        modifier = modifier
            .layoutId(if (selected) CHOSEN else "")
            .background(if (selected) colors.secondaryContainer else Color.Transparent)
            .border(1.dp, colors.outline)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .defaultMinSize(minHeight = SegmentMinHeight)
            .padding(horizontal = SegmentPadding, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides content) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                MarkedLabel(selected, label)
            }
        }
    }
}

/**
 * The label, with the check mark before it when chosen and when there is room for both: asked its
 * width, it answers with the mark; given less, it drops the mark sooner than break the label.
 */
@Composable
private fun MarkedLabel(selected: Boolean, label: @Composable () -> Unit) {
    Layout(content = {
        if (selected) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.layoutId(CHOSEN))
        Box { label() }
    }) { measurables, constraints ->
        val mark = measurables.firstOrNull { it.layoutId == CHOSEN }
        val text = measurables.first { it.layoutId != CHOSEN }
        val markSize = CheckSize.roundToPx()
        val markRoom = markSize + CheckGap.roundToPx()
        val marked = mark != null && (!constraints.hasBoundedWidth || constraints.maxWidth >= markRoom + text.maxIntrinsicWidth(Constraints.Infinity))
        val lead = if (marked) markRoom else 0
        val textPlaceable = text.measure(Constraints(maxWidth = if (constraints.hasBoundedWidth) (constraints.maxWidth - lead).coerceAtLeast(0) else Constraints.Infinity))
        val markPlaceable = if (marked) mark!!.measure(Constraints.fixed(markSize, markSize)) else null
        val height = maxOf(textPlaceable.height, markPlaceable?.height ?: 0)
        layout(lead + textPlaceable.width, height) {
            markPlaceable?.placeRelative(0, (height - markSize) / 2)
            textPlaceable.placeRelative(lead, (height - textPlaceable.height) / 2)
        }
    }
}
