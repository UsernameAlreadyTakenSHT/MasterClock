package com.masterclock.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How [ButtonRow] shares its width out while its buttons fit side by side. */
enum class ButtonRowStyle {
    /** Equal widths across the whole row; stacked, each takes the full width. */
    Fill,
    /** The first at the start, the last at the end, at their own widths; stacked, full width. */
    Ends,
    /** Packed at the end at their own widths, as a dialog's actions are; stacked, still at the end. */
    End,
}

/**
 * Buttons side by side while each label fits on one line, and one above the other once it does not.
 *
 * A `Row` gives each button its share whatever the label needs, and a label too long for its share
 * breaks where it runs out: "Enregi / strer", "Repren / dre", "Zurücksetze / n" at a large font or a
 * narrow screen, in every language with a long word. What is asked of each button is the width its
 * content takes unbroken; if the row cannot give that, nothing is squeezed and the buttons stack.
 *
 * Stacked, [primaryLast] puts the last button on top: the action a reader came for, which sits at
 * the end of a row and at the head of a column.
 *
 * [wrapLabels] is for labels of several words, which may take two lines side by side: what is
 * asked of a button is then the width of its longest word, so that "Exporter / partager" wraps at
 * the slash and stays in the row, while "Importieren" stacks sooner than lose its last letter.
 */
@Composable
fun ButtonRow(
    modifier: Modifier = Modifier,
    style: ButtonRowStyle = ButtonRowStyle.Fill,
    spacing: Dp = 8.dp,
    primaryLast: Boolean = false,
    wrapLabels: Boolean = false,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        if (measurables.isEmpty()) return@Layout layout(constraints.minWidth, constraints.minHeight) {}
        val gap = spacing.roundToPx()
        val count = measurables.size
        val bounded = constraints.hasBoundedWidth
        val width = constraints.maxWidth
        val wanted = measurables.map { if (wrapLabels) it.minIntrinsicWidth(Constraints.Infinity) else it.maxIntrinsicWidth(Constraints.Infinity) }
        val share = if (bounded) (width - gap * (count - 1)) / count else 0
        val fits = !bounded || when (style) {
            ButtonRowStyle.Fill -> wanted.all { it <= share }
            ButtonRowStyle.Ends, ButtonRowStyle.End -> wanted.sum() + gap * (count - 1) <= width
        }
        if (fits) {
            // Equal heights under Fill: with wrapLabels one label may take two lines and its
            // neighbour one.
            val fillHeight = if (style == ButtonRowStyle.Fill && bounded) measurables.maxOf { it.maxIntrinsicHeight(share) } else 0
            val placeables = measurables.mapIndexed { i, m ->
                if (style == ButtonRowStyle.Fill && bounded) m.measure(Constraints.fixed(share, fillHeight))
                else m.measure(Constraints(maxWidth = wanted[i]))
            }
            val used = placeables.sumOf { it.width } + gap * (count - 1)
            val rowWidth = if (bounded) width else used
            val height = placeables.maxOf { it.height }
            layout(rowWidth, height) {
                if (style == ButtonRowStyle.Ends && count > 1) {
                    val free = (rowWidth - placeables.sumOf { it.width }) / (count - 1)
                    var x = 0
                    placeables.forEach { p -> p.placeRelative(x, (height - p.height) / 2); x += p.width + free }
                } else {
                    // A lone button under Ends is the one that goes forward, and stays at the end.
                    var x = if (style == ButtonRowStyle.Fill) 0 else rowWidth - used
                    placeables.forEach { p -> p.placeRelative(x, (height - p.height) / 2); x += p.width + gap }
                }
            }
        } else {
            val ordered = if (primaryLast) measurables.reversed() else measurables
            val placeables = ordered.map { m ->
                if (style == ButtonRowStyle.End) m.measure(Constraints(maxWidth = width)) else m.measure(Constraints.fixedWidth(width))
            }
            val height = placeables.sumOf { it.height } + gap * (count - 1)
            layout(width, height) {
                var y = 0
                placeables.forEach { p ->
                    p.placeRelative(if (style == ButtonRowStyle.End) width - p.width else 0, y)
                    y += p.height + gap
                }
            }
        }
    }
}
