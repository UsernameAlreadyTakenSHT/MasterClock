package com.masterclock.paper.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch

object MMDDefaults {
    val CornerRadius = 8.dp
    val BorderWidth = 2.dp
    val SwitchTrackWidth = 52.dp
    val SwitchTrackHeight = 32.dp
    val SwitchThumbSize = 20.dp
    val MinButtonWidth = 50.dp
    val MinButtonHeight = 32.dp
    val ContentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
}

// --- BUTTONS ---

@Composable
fun ButtonMMD(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(MMDDefaults.CornerRadius),
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        disabledContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
        disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
    ),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = MMDDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        modifier = modifier.defaultMinSize(
            minWidth = MMDDefaults.MinButtonWidth,
            minHeight = MMDDefaults.MinButtonHeight,
        ),
        contentPadding = contentPadding,
        shape = shape,
        border = border,
        colors = colors,
        elevation = null,
        enabled = enabled,
        interactionSource = remember { MutableInteractionSource() },
        onClick = onClick,
        content = content
    )
}

@Composable
fun OutlinedButtonMMD(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(MMDDefaults.CornerRadius),
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primaryContainer,
        disabledContainerColor = Color.Transparent,
        disabledContentColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
    ),
    border: BorderStroke = BorderStroke(
        width = MMDDefaults.BorderWidth,
        color = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
    ),
    contentPadding: PaddingValues = MMDDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    ButtonMMD(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        border = border,
        contentPadding = contentPadding,
        content = content
    )
}

// --- SCROLLBAR ---

object ScrollbarMMDDefaults {
    val TrackWidth = 12.dp
    val TrackBorderWidth = 2.dp
    val TrackCorners = RoundedCornerShape(6.dp)
    val ChevronWidth = 18.dp
    val ChevronHeight = 10.dp
    val ChevronStrokeWidth = 2.dp
    /** The drawn chevron is small; the box that catches the tap must not be. */
    val ChevronTapSize = 28.dp
    val ChevronOuterPadding = 8.dp
    val HorizontalPadding = 8.dp
    val MinThumbHeight = 24.dp
}

/**
 * A vertical scrollbar for a [ScrollState], modelled on MMD's own `VerticalScrollbar`.
 *
 * E Ink has none of the cues that normally say "there is more below". MMD turns off native
 * scrolling on its lists and pages instead, so there is no fling, no overscroll stretch, and no
 * scrollbar that fades in while a finger is down. Upstream answers that with a scrollbar that
 * carries its own chevrons and is simply always there -- but only while the content actually
 * overflows: `if (isScrollable && isScrollbarVisible)`, otherwise the width goes back to the
 * content. Both rules are kept here.
 *
 * Chevrons are drawn rather than tinted: upstream swaps a filled glyph for a dotted one at the
 * ends of the list, because greying one out would dither on E Ink. Dashing the stroke is the same
 * idea and keeps every pixel pure black.
 *
 * One deliberate deviation: the content keeps ordinary drag scrolling. MMD sets
 * `userScrollEnabled = false` and snaps by a fixed item count, which has no meaning for a Column
 * whose children are not a list.
 */
@Composable
fun ScrollbarMMD(
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    val range = scrollState.maxValue
    // maxValue reads as Int.MAX_VALUE until the first measurement lands.
    if (range <= 0 || range == Int.MAX_VALUE) return

    val scope = rememberCoroutineScope()
    val color = MaterialTheme.colorScheme.onBackground
    val viewport = scrollState.viewportSize
    val atTop = scrollState.value <= 0
    val atBottom = scrollState.value >= range

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = ScrollbarMMDDefaults.HorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ChevronMMD(
            pointingUp = true,
            dashed = atTop,
            color = color,
            onTap = { scope.launch { scrollState.scrollTo(scrollState.value - viewport) } },
            onLongPress = { scope.launch { scrollState.scrollTo(0) } }
        )

        Canvas(
            modifier = Modifier
                .width(ScrollbarMMDDefaults.TrackWidth)
                .weight(1f)
                .border(
                    width = ScrollbarMMDDefaults.TrackBorderWidth,
                    color = color,
                    shape = ScrollbarMMDDefaults.TrackCorners
                )
                .pointerInput(range, viewport) {
                    detectTapGestures { offset ->
                        val thumb = thumbHeight(size.height.toFloat(), viewport, range, this)
                        val travel = (size.height - thumb).coerceAtLeast(1f)
                        // Centre the thumb on the tap, as upstream does.
                        val fraction = ((offset.y - thumb / 2f) / travel).coerceIn(0f, 1f)
                        scope.launch { scrollState.scrollTo((fraction * range).toInt()) }
                    }
                }
        ) {
            val thumb = thumbHeight(size.height, viewport, range, this)
            val travel = (size.height - thumb).coerceAtLeast(0f)
            val top = travel * (scrollState.value.toFloat() / range.toFloat())
            val inset = ScrollbarMMDDefaults.TrackBorderWidth.toPx()

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
            pointingUp = false,
            dashed = atBottom,
            color = color,
            onTap = { scope.launch { scrollState.scrollTo(scrollState.value + viewport) } },
            onLongPress = { scope.launch { scrollState.scrollTo(range) } }
        )
    }
}

/** The thumb's height, never below [ScrollbarMMDDefaults.MinThumbHeight] so it stays grabbable. */
private fun thumbHeight(trackHeight: Float, viewport: Int, range: Int, density: Density): Float {
    val content = (viewport + range).toFloat()
    val proportional = if (content > 0f) trackHeight * (viewport / content) else trackHeight
    val floor = with(density) { ScrollbarMMDDefaults.MinThumbHeight.toPx() }
    return proportional.coerceIn(floor.coerceAtMost(trackHeight), trackHeight)
}

@Composable
private fun ChevronMMD(
    pointingUp: Boolean,
    dashed: Boolean,
    color: Color,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    Canvas(
        modifier = Modifier
            .padding(vertical = ScrollbarMMDDefaults.ChevronOuterPadding)
            .size(ScrollbarMMDDefaults.ChevronTapSize)
            // detectTapGestures rather than clickable: no ripple to leave behind on E Ink.
            .pointerInput(dashed) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress() }
                )
            }
    ) {
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

// --- SWITCHER ---

@Composable
fun SwitchMMD(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colorScheme = MaterialTheme.colorScheme
    val disabledColor = colorScheme.onSurface.copy(alpha = 0.25f)
    val trackColor = when {
        !enabled -> disabledColor
        checked -> colorScheme.primaryContainer
        else -> colorScheme.background
    }
    val thumbColor = when {
        !enabled -> disabledColor
        checked -> colorScheme.onPrimaryContainer
        else -> colorScheme.primaryContainer
    }
    val borderColor = if (enabled) colorScheme.primaryContainer else disabledColor

    Box(
        modifier = modifier
            .size(MMDDefaults.SwitchTrackWidth, MMDDefaults.SwitchTrackHeight)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .border(MMDDefaults.BorderWidth, borderColor, RoundedCornerShape(50))
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .size(MMDDefaults.SwitchThumbSize)
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}

// --- TEXT FIELD (AVOID GREY) ---

@Composable
fun MMDTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    placeholder: String = "",
    suffix: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge
) {
    val colorScheme = MaterialTheme.colorScheme
    val textColor = colorScheme.onSurface
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val indicatorColor by animateColorAsState(
        targetValue = when {
            !enabled -> colorScheme.onSurface.copy(alpha = 0.25f)
            isError -> colorScheme.error
            isFocused -> colorScheme.onSurface
            else -> colorScheme.outline
        },
        label = "MMDTextFieldIndicator"
    )
    val indicatorThickness = if (isFocused) MMDDefaults.BorderWidth else 1.dp

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier.defaultMinSize(minWidth = 280.dp, minHeight = 56.dp),
        textStyle = textStyle.copy(color = textColor),
        keyboardOptions = keyboardOptions,
        cursorBrush = SolidColor(textColor),
        decorationBox = { innerTextField ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            // AVOID GREY: Use Ink color at full opacity
                            Text(
                                text = placeholder,
                                style = textStyle,
                                color = textColor
                            )
                        }
                        innerTextField()
                    }
                    if (suffix.isNotEmpty()) {
                        Text(
                            text = suffix,
                            style = textStyle,
                            color = textColor,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                HorizontalDivider(thickness = indicatorThickness, color = indicatorColor)
            }
        }
    )
}

// --- DIALOGS ---
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
                Column(
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
                            modifier = Modifier.fillMaxWidth()
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
