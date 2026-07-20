package com.masterclock.paper.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * MASTERCLOCK COLOR PALETTE
 * Strict BW palette for E-Ink, aligned with Mudita standards.
 */

val EInkBlack = Color(0xFF000000)
val EInkWhite = Color(0xFFFFFFFF)

val EInkColorScheme = lightColorScheme(
    primary = EInkBlack,
    onPrimary = EInkWhite,
    primaryContainer = EInkBlack, // Main action color
    onPrimaryContainer = EInkWhite,
    inversePrimary = EInkWhite,
    secondary = EInkWhite,
    onSecondary = EInkBlack,
    // Matches real ColorMMD.kt: containers stay black regardless of role.
    secondaryContainer = EInkBlack,
    onSecondaryContainer = EInkWhite,
    tertiary = EInkWhite,
    onTertiary = EInkBlack,
    tertiaryContainer = EInkBlack,
    onTertiaryContainer = EInkWhite,
    background = EInkWhite,
    onBackground = EInkBlack,
    surface = EInkWhite,
    onSurface = EInkBlack,
    surfaceVariant = EInkWhite,
    onSurfaceVariant = EInkBlack,
    surfaceTint = EInkWhite,
    // Matches real ColorMMD.kt: not a true color inversion (avoids a jarring
    // full-swap redraw on e-ink hardware) — same mapping as the normal surface.
    inverseSurface = EInkWhite,
    inverseOnSurface = EInkBlack,
    outline = EInkBlack,
    outlineVariant = EInkBlack,
    scrim = EInkBlack,
    error = EInkBlack,
    onError = EInkWhite,
    errorContainer = EInkWhite,
    onErrorContainer = EInkBlack
)
