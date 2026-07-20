package com.masterclock.paper.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.masterclock.app.logic.FlavorConfig

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.runtime.CompositionLocalProvider

private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode {
        return object : DelegatableNode, androidx.compose.ui.Modifier.Node() {}
    }
    override fun hashCode(): Int = -1
    override fun equals(other: Any?): Boolean = other === this
}

@Composable
fun MasterClockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    eInkDarkMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val isEInk = FlavorConfig.isEInk()
    
    val colorScheme = when {
        isEInk -> {
            if (eInkDarkMode) {
                // Inverted BW scheme for Reverse Colors
                darkColorScheme(
                    primary = EInkWhite,
                    onPrimary = EInkBlack,
                    primaryContainer = EInkWhite, // Main action color in dark mode
                    onPrimaryContainer = EInkBlack,
                    inversePrimary = EInkBlack,
                    secondary = EInkBlack,
                    onSecondary = EInkWhite,
                    secondaryContainer = EInkBlack,
                    onSecondaryContainer = EInkWhite,
                    tertiary = EInkBlack,
                    onTertiary = EInkWhite,
                    tertiaryContainer = EInkBlack,
                    onTertiaryContainer = EInkWhite,
                    background = EInkBlack,
                    onBackground = EInkWhite,
                    surface = EInkBlack,
                    onSurface = EInkWhite,
                    surfaceVariant = EInkBlack,
                    onSurfaceVariant = EInkWhite,
                    outline = EInkWhite,
                    outlineVariant = EInkWhite,
                    error = EInkWhite,
                    onError = EInkBlack
                )
            } else EInkColorScheme
        }
        else -> {
            // Standard mobile theme logic omitted for clarity or if needed
            if (darkTheme) darkColorScheme() else lightColorScheme()
        }
    }

    val typography = if (isEInk) EInkTypography else Typography

    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = (!darkTheme && !isEInk) || (isEInk && !eInkDarkMode)
    }

    CompositionLocalProvider(
        LocalIndication provides if (isEInk) NoIndication else LocalIndication.current
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
