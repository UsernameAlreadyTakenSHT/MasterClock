package com.masterclock.paper.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.mudita.mmd.eInkColorScheme

/**
 * The light scheme is MMD's own `eInkColorScheme`, used as it is.
 *
 * MMD has no dark one, and the E-Ink build offers reversed colours ("E-Ink dark mode"), so this is
 * MMD's scheme with black and white exchanged role for role -- nothing else changed, so the two stay
 * the same design. The surface containers are set too: MMD leaves most of them unspecified, and any
 * Material component that reads them would otherwise fall back to Material's tinted greys.
 */
val EInkDarkColorScheme: ColorScheme = eInkColorScheme.invert()

private fun ColorScheme.invert(): ColorScheme {
    fun Color.flip(): Color = when (this) {
        Color.Black -> Color.White
        Color.White -> Color.Black
        else -> this
    }
    return copy(
        primary = primary.flip(), onPrimary = onPrimary.flip(),
        primaryContainer = primaryContainer.flip(), onPrimaryContainer = onPrimaryContainer.flip(),
        inversePrimary = inversePrimary.flip(),
        secondary = secondary.flip(), onSecondary = onSecondary.flip(),
        secondaryContainer = secondaryContainer.flip(), onSecondaryContainer = onSecondaryContainer.flip(),
        tertiary = tertiary.flip(), onTertiary = onTertiary.flip(),
        tertiaryContainer = tertiaryContainer.flip(), onTertiaryContainer = onTertiaryContainer.flip(),
        background = background.flip(), onBackground = onBackground.flip(),
        surface = surface.flip(), onSurface = onSurface.flip(),
        surfaceVariant = surfaceVariant.flip(), onSurfaceVariant = onSurfaceVariant.flip(),
        surfaceTint = surfaceTint.flip(),
        inverseSurface = inverseSurface.flip(), inverseOnSurface = inverseOnSurface.flip(),
        error = error.flip(), onError = onError.flip(),
        errorContainer = errorContainer.flip(), onErrorContainer = onErrorContainer.flip(),
        outline = outline.flip(), outlineVariant = outlineVariant.flip(),
        scrim = scrim.flip(),
        surfaceBright = Color.Black, surfaceDim = Color.Black,
        surfaceContainer = Color.Black, surfaceContainerHigh = Color.Black,
        surfaceContainerHighest = Color.Black, surfaceContainerLow = Color.Black,
        surfaceContainerLowest = Color.Black,
    )
}

/**
 * MMD's light scheme with its surface containers made white, for the same reason as above: MMD
 * leaves them unspecified, which a Material component reading them renders as transparent or
 * falls back from.
 */
val EInkLightColorScheme: ColorScheme = eInkColorScheme.copy(
    surfaceBright = Color.White, surfaceDim = Color.White,
    surfaceContainer = Color.White, surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White, surfaceContainerLow = Color.White,
    surfaceContainerLowest = Color.White,
)
