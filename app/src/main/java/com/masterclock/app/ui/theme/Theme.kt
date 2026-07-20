package com.masterclock.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.masterclock.app.logic.FlavorConfig

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.drawscope.ContentDrawScope

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
                // Reversed Colors
                EInkColorScheme.copy(
                    primary = EInkWhite,
                    onPrimary = EInkBlack,
                    primaryContainer = EInkWhite,
                    onPrimaryContainer = EInkBlack,
                    background = EInkBlack,
                    onBackground = EInkWhite,
                    surface = EInkBlack,
                    onSurface = EInkWhite,
                    outline = EInkWhite,
                    surfaceContainerLow = EInkBlack
                )
            } else EInkColorScheme
        }
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
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
