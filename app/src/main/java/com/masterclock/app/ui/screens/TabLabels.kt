package com.masterclock.app.ui.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * The style at which every one of [labels] fits [cellWidth] on one line: [style] itself, or the
 * same style smaller -- one size for the whole group, down to [tabLabelFloor].
 *
 * Shrunk one by one, the labels of a row of tabs came out at as many sizes as they had lengths:
 * "Nouveautés" small beside a full-size "Crédits", and in the bottom bar the one long word
 * smaller than its four neighbours. A row of tabs reads as one thing, so it shrinks as one.
 */
@Composable
fun sharedLabelStyle(labels: List<String>, style: TextStyle, cellWidth: Dp): TextStyle {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val floor = tabLabelFloor()
    return remember(labels, style, cellWidth, density.density, density.fontScale) {
        val room = with(density) { cellWidth.toPx() }
        val widest = labels.maxOfOrNull { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width } ?: 0
        if (!style.fontSize.isSpecified || room <= 0f || widest <= room) style
        // A little under the ratio: letter spacing does not shrink with the size.
        else style.copy(fontSize = (style.fontSize.value * room / widest * 0.96f).coerceAtLeast(floor.value).sp)
    }
}

/** A tab's label in a style from [sharedLabelStyle]: one line, and an ellipsis only past the floor. */
@Composable
fun TabLabel(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = style, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
}
