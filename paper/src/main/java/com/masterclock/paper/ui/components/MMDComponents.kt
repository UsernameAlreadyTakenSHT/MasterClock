package com.masterclock.paper.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.Interaction
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * MMD-style interaction source that disables ripples.
 */
class NoRippleInteractionSource : MutableInteractionSource {
    override val interactions: Flow<Interaction> = emptyFlow()
    override suspend fun emit(interaction: Interaction) {}
    override fun tryEmit(interaction: Interaction): Boolean = true
}

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
        interactionSource = remember { NoRippleInteractionSource() },
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
                interactionSource = remember { NoRippleInteractionSource() },
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
                // Official MMD Dialog: Centered Title and Body
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

                // Official MMD Dialog: Vertically Stacked Full-Width Buttons
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
