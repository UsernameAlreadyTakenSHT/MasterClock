/*
 * What the E-Ink build still draws for itself. Everything else comes from Mudita Mindful Design
 * (MMD), Mudita's own component library for its E Ink devices (com.mudita:MMD, Apache 2.0): the
 * theme, the type scale and the buttons, switches, fields, tabs and bars.
 *
 * MMD has no counterpart for what is left here:
 * - an alert dialog. It has bottom sheets and menus, but no dialog, so MMDAlertDialog draws one in
 *   its manner -- no elevation, a hard border -- around MMD's own buttons.
 * - a scrollbar for a plain scrolling Column. MMD's scrollbar is private to its lazy lists, and
 *   made for a list of like rows: its thumb is sized by the count of items and its chevrons move
 *   by a number of them, where the settings page is a few panels of very different heights. Since
 *   1.0.2 such a list can follow the finger rather than step, but the scrollbar is the same.
 *   ScrollbarMMD follows MMD's scrollbar, for a ScrollState instead.
 *
 * Neither is copied from MMD's source: both were written from how the interface behaves, before the
 * library was published. The library itself is credited where the E-Ink build lists its licences.
 */
package com.masterclock.paper.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
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
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mudita.mmd.components.buttons.ButtonDefaultsMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
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
fun outlinedButtonBorderMMD(): BorderStroke =
    // One ink, with no fainter one for a button out of use: nothing here is ever out of use, and a
    // border at three quarters is a grey, which E Ink dithers and Mudita's guide asks to avoid.
    BorderStroke(ButtonDefaultsMMD.borderWidth, MaterialTheme.colorScheme.primaryContainer)

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
 * One more deliberate deviation: the content is dragged by Compose's own scrolling, held to the
 * finger; see [verticalScrollMMD]. MMD's lists turn the native one off and move themselves, by a
 * count of items or, since 1.0.2, by the finger's own distance; neither is there for a Column whose
 * children are not a list.
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
 * Keeps the screen behind a dialog as it is, and has the dialog there at once. A Compose dialog dims
 * its window by default, which E Ink renders as a grey dither over the whole screen, reversed
 * colours or not; the dialog's hard border already sets it apart. And its window comes and goes by
 * the system's fade, every step of which E Ink draws: Mudita's guide has what opens over a screen
 * shown instantly.
 */
@Composable
fun NoDialogDim() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        window?.setDimAmount(0f)
        window?.setWindowAnimations(0)
    }
}

// --- MESSAGE ---

/**
 * What the app says in passing, drawn as MMD's snackbar is -- a rule and a line of text -- and
 * taking no touch. MMD's own sits on a Material surface, and a surface takes every touch on it
 * so that none reaches what lies beneath: at the foot of the clock that is the lower player's
 * side, and their press was lost while a message stood there. This one is a background and a
 * text, which a touch goes through.
 */
@Composable
fun MessageMMD(text: String) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        HorizontalDividerMMD()
        Text(
            text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

// --- SCROLLING ---

/**
 * Scrolls a Column as MMD's own lists move: by the finger and no further. Compose carries a list
 * on after the finger lifts and stretches it at its ends, two animations among the ones MMD turns
 * off in its lists and its guide asks an E Ink screen to be spared; every frame of them is a
 * partial refresh that leaves its ghost. The scrollbar beside the content says where it ends.
 */
fun Modifier.verticalScrollMMD(state: ScrollState): Modifier =
    verticalScroll(state, overscrollEffect = null, flingBehavior = NoFling)

private object NoFling : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float = 0f
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
                // it: the failure the version dialog had and fixed, while there was one. Today's one message
                // fits with room to spare.
                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScrollMMD(rememberScrollState()),
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
                    // Both at the 48dp every other button of the app is held to: left at the library's
                    // own height they stood a fifth lower than the three under the clock, on the one
                    // question where a press beside the mark resets a game.
                    ButtonMMD(
                        onClick = onConfirm,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
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
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
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

/**
 * The style at which every one of [labels] fits [cellWidth] on one line: [style] itself, or the
 * same style smaller -- one size for the whole group, down to a floor on the screen.
 *
 * The three buttons under the clock and the three mode cards share it. At a large font the label
 * ran from edge to edge of its button, or was cut: "Fisch" for "Fischer".
 * A row of buttons reads as one thing, so it shrinks as one; the floor is a size on the screen,
 * because one in sp grows with the very font scale that made the label too wide.
 *
 * The floor is 14dp, the size of the smallest style MMD has, and was 9: a label is not made
 * smaller than anything else the library would set on this screen. One still too wide there goes
 * onto a second line, which the buttons that use this allow.
 */
@Composable
fun sharedLabelStyle(labels: List<String>, style: TextStyle, cellWidth: Dp): TextStyle {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(labels, style, cellWidth, density.density, density.fontScale) {
        val room = with(density) { cellWidth.toPx() }
        val floor = with(density) { 14.dp.toSp() }
        val widest = labels.maxOfOrNull { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width } ?: 0
        if (!style.fontSize.isSpecified || room <= 0f || widest <= room) style
        // A little under the ratio: letter spacing does not shrink with the size.
        else style.copy(fontSize = (style.fontSize.value * room / widest * 0.96f).coerceAtLeast(floor.value).sp)
    }
}
