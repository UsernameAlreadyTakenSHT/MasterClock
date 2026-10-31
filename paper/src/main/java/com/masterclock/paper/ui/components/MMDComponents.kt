/*
 * What the E-Ink build still draws for itself. Everything else comes from Mudita Mindful Design
 * (MMD), Mudita's own component library for its E Ink devices (com.mudita:MMD, Apache 2.0): the
 * theme, the type scale and the buttons, switches, fields, tabs and bars.
 *
 * MMD has no counterpart for what is left here:
 * - an alert dialog. It has bottom sheets and menus, but no dialog, so MMDAlertDialog draws one in
 *   its manner -- no elevation, a hard border -- around MMD's own buttons.
 * - a scrollbar for a plain scrolling Column. MMD's scrollbar lives inside LazyColumnMMD, which
 *   turns drag scrolling off and steps a fixed number of items at a time. That suits a list of
 *   like rows; the settings page is a few panels of very different heights, where one step would
 *   skip most of it. ScrollbarMMD follows MMD's scrollbar, for a ScrollState instead.
 *
 * Neither is copied from MMD's source: both were written from how the interface behaves, before the
 * library was published. The library itself is credited where the E-Ink build lists its licences.
 */
package com.masterclock.paper.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mudita.mmd.components.buttons.ButtonDefaultsMMD
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import kotlinx.coroutines.launch

/** MMD's button corner and border, for the surfaces drawn here that sit beside its buttons. */
object MMDDefaults {
    val CornerRadius = ButtonDefaultsMMD.buttonCornerRadius
    val BorderWidth = ButtonDefaultsMMD.borderWidth
}

/**
 * The border for every [OutlinedButtonMMD] in this app. MMD's own default is `Color.Black` written
 * as a constant rather than read from the scheme, so with reversed colours the outline vanished
 * into the black background and the buttons were bare words. This is the same stroke on the
 * colour MMD fills its other buttons with, which the reversed scheme does flip.
 */
@Composable
fun outlinedButtonBorderMMD(enabled: Boolean = true): BorderStroke {
    val color = MaterialTheme.colorScheme.primaryContainer
    return BorderStroke(
        ButtonDefaultsMMD.borderWidth,
        if (enabled) color else color.copy(alpha = 0.75f),
    )
}

// --- SCROLLBAR ---

object ScrollbarMMDDefaults {
    val TrackWidth = 12.dp
    val TrackBorderWidth = 2.dp
    val TrackCornerRadius = 6.dp
    val ChevronWidth = 18.dp
    val ChevronHeight = 10.dp
    val ChevronStrokeWidth = 2.dp
    /**
     * The drawn chevron is small; the box that catches the tap must not be. 48dp, the target Android
     * recommends: it was 28, with 8dp of padding around it on every side that caught nothing. The
     * padding is inside the target now, so the bar is barely wider than it was.
     */
    val ChevronTapSize = 48.dp
    val ChevronOuterPadding = 0.dp
    val HorizontalPadding = 0.dp
    val MinThumbHeight = 24.dp
}

/**
 * A vertical scrollbar for a [ScrollState], modelled on MMD's own `VerticalScrollbar`.
 *
 * E Ink has none of the cues that normally say "there is more below". MMD turns off native
 * scrolling on its lists and pages instead, so there is no fling, no overscroll stretch, and no
 * scrollbar that fades in while a finger is down. Upstream answers that with a scrollbar that
 * carries its own chevrons and is simply always there while the content overflows.
 *
 * Its width is always kept, overflow or not; only the drawing waits for one. Upstream hands the
 * width back to the content when nothing overflows, and this did too, deciding in composition:
 * but the scroll range is known only once the content has been measured, so the first frame went
 * out with no bar and full-width text, and the next added the bar and wrapped the text 48dp
 * narrower -- a second full refresh of the panel, visibly reflowing, every time the changelog or
 * the settings opened. Read where it is drawn, the range is that frame's own.
 *
 * Chevrons are drawn rather than tinted: upstream swaps a filled glyph for a dotted one at the
 * ends of the list, because greying one out would dither on E Ink. Dashing the stroke is the same
 * idea and keeps every pixel pure black.
 *
 * One more deliberate deviation: the content keeps ordinary drag scrolling. MMD sets
 * `userScrollEnabled = false` and snaps by a fixed item count, which has no meaning for a Column
 * whose children are not a list.
 */
@Composable
fun ScrollbarMMD(
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val color = MaterialTheme.colorScheme.onBackground

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = ScrollbarMMDDefaults.HorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ChevronMMD(
            scrollState = scrollState,
            pointingUp = true,
            color = color,
            onTap = { scope.launch { scrollState.scrollTo(scrollState.value - scrollState.viewportSize) } },
            onLongPress = { scope.launch { scrollState.scrollTo(0) } }
        )

        Canvas(
            modifier = Modifier
                .width(ScrollbarMMDDefaults.TrackWidth)
                .weight(1f)
                .pointerInput(scrollState) {
                    detectTapGestures { offset ->
                        val range = scrollState.scrollRange() ?: return@detectTapGestures
                        val thumb = thumbHeight(size.height.toFloat(), scrollState.viewportSize, range, this)
                        val travel = (size.height - thumb).coerceAtLeast(1f)
                        // Centre the thumb on the tap, as upstream does.
                        val fraction = ((offset.y - thumb / 2f) / travel).coerceIn(0f, 1f)
                        scope.launch { scrollState.scrollTo((fraction * range).toInt()) }
                    }
                }
        ) {
            val range = scrollState.scrollRange() ?: return@Canvas
            val inset = ScrollbarMMDDefaults.TrackBorderWidth.toPx()
            // The track's outline, drawn here rather than as a border modifier so that it waits for
            // the range with the rest.
            drawRoundRect(
                color = color,
                topLeft = Offset(inset / 2f, inset / 2f),
                size = Size((size.width - inset).coerceAtLeast(0f), (size.height - inset).coerceAtLeast(0f)),
                cornerRadius = CornerRadius(ScrollbarMMDDefaults.TrackCornerRadius.toPx()),
                style = Stroke(width = inset)
            )

            val thumb = thumbHeight(size.height, scrollState.viewportSize, range, this)
            val travel = (size.height - thumb).coerceAtLeast(0f)
            val top = travel * (scrollState.value.toFloat() / range.toFloat())
            drawRoundRect(
                color = color,
                topLeft = Offset(inset, top + inset),
                size = Size(
                    width = (size.width - inset * 2).coerceAtLeast(0f),
                    height = (thumb - inset * 2).coerceAtLeast(0f)
                ),
                cornerRadius = CornerRadius(size.width / 2f)
            )
        }

        ChevronMMD(
            scrollState = scrollState,
            pointingUp = false,
            color = color,
            onTap = { scope.launch { scrollState.scrollTo(scrollState.value + scrollState.viewportSize) } },
            onLongPress = { scope.launch { scrollState.scrollTo(scrollState.maxValue) } }
        )
    }
}

/**
 * The scroll range, or null while there is none to show: the content fits, or it has not been
 * measured yet, when maxValue reads Int.MAX_VALUE. Read in draw and in gestures, never in
 * composition; see [ScrollbarMMD].
 */
private fun ScrollState.scrollRange(): Int? = maxValue.takeIf { it in 1 until Int.MAX_VALUE }

/** The thumb's height, never below [ScrollbarMMDDefaults.MinThumbHeight] so it stays grabbable. */
private fun thumbHeight(trackHeight: Float, viewport: Int, range: Int, density: Density): Float {
    val content = (viewport + range).toFloat()
    val proportional = if (content > 0f) trackHeight * (viewport / content) else trackHeight
    val floor = with(density) { ScrollbarMMDDefaults.MinThumbHeight.toPx() }
    return proportional.coerceIn(floor.coerceAtMost(trackHeight), trackHeight)
}

@Composable
private fun ChevronMMD(
    scrollState: ScrollState,
    pointingUp: Boolean,
    color: Color,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    // The gesture outlives the composition it was set up in; the lambdas it calls are the latest.
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    Canvas(
        modifier = Modifier
            .padding(vertical = ScrollbarMMDDefaults.ChevronOuterPadding)
            .size(ScrollbarMMDDefaults.ChevronTapSize)
            // detectTapGestures rather than clickable: no ripple to leave behind on E Ink.
            .pointerInput(scrollState) {
                detectTapGestures(
                    onTap = { if (scrollState.scrollRange() != null) currentOnTap() },
                    onLongPress = { if (scrollState.scrollRange() != null) currentOnLongPress() }
                )
            }
    ) {
        val range = scrollState.scrollRange() ?: return@Canvas
        // Dashed at the end it points to. Read here, where the position changing repaints the
        // chevron without recomposing anything.
        val dashed = if (pointingUp) scrollState.value <= 0 else scrollState.value >= range
        val stroke = ScrollbarMMDDefaults.ChevronStrokeWidth.toPx()
        val width = ScrollbarMMDDefaults.ChevronWidth.toPx()
        val height = ScrollbarMMDDefaults.ChevronHeight.toPx()
        val left = (size.width - width) / 2f
        val top = (size.height - height) / 2f
        val apexY = if (pointingUp) top else top + height
        val baseY = if (pointingUp) top + height else top
        val path = Path().apply {
            moveTo(left, baseY)
            lineTo(left + width / 2f, apexY)
            lineTo(left + width, baseY)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = stroke,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                pathEffect = if (dashed) {
                    PathEffect.dashPathEffect(floatArrayOf(stroke * 1.5f, stroke * 1.5f))
                } else {
                    null
                }
            )
        )
    }
}

// --- DIALOGS ---

/**
 * Keeps the screen behind a dialog as it is. A Compose dialog dims its window by default, which E
 * Ink renders as a grey dither over the whole screen, reversed colours or not. The dialog's hard
 * border already sets it apart.
 */
@Composable
fun NoDialogDim() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect { window?.setDimAmount(0f) }
}

@Composable
fun MMDAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    text: String,
    confirmButtonText: String,
    onConfirm: () -> Unit,
    dismissButtonText: String? = null,
    onDismiss: (() -> Unit)? = null
) {
    Dialog(onDismissRequest = onDismissRequest) {
        NoDialogDim()
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    MMDDefaults.BorderWidth,
                    MaterialTheme.colorScheme.onSurface,
                    RoundedCornerShape(MMDDefaults.CornerRadius)
                ),
            shape = RoundedCornerShape(MMDDefaults.CornerRadius),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Scrolls if it ever outgrows the screen, before the buttons below are squeezed out of
                // it: the failure ChangelogCreditsDialog already had and fixed. Today's one message
                // fits with room to spare.
                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ButtonMMD(
                        onClick = onConfirm,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = confirmButtonText,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (dismissButtonText != null && onDismiss != null) {
                        OutlinedButtonMMD(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            border = outlinedButtonBorderMMD()
                        ) {
                            Text(
                                text = dismissButtonText,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
