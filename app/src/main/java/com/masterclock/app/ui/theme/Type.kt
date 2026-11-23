package com.masterclock.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.masterclock.app.logic.AppFont
import com.masterclock.core.R as CoreR

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)

val EInkTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
    )
)

/**
 * Noto Sans and Noto Sans Mono, 400 and 700, shipped with the app, for the font chosen in the
 * settings ([AppFont]). Noto Sans has the whole of Latin, Greek and Cyrillic: Polish, Turkish,
 * Russian and Ukrainian among the languages being weighed, where the E-Ink build's Lato lacks
 * Turkish's dotted capital I, its g and s with their marks, and Cyrillic altogether. Its numerals
 * are all one width too, so a clock drawn in it does not shift from one second to the next.
 *
 * Two weights each. A weight asked for between them takes the nearer one; a bold faked from the
 * regular, as one weight alone would have meant, smears on an E Ink panel. Scripts outside these
 * files -- Japanese, Chinese, Arabic, Devanagari -- are drawn by the system's own fonts, letter by
 * letter, whichever is chosen.
 */
val NotoSans = FontFamily(
    Font(CoreR.font.noto_sans_regular, FontWeight.Normal),
    Font(CoreR.font.noto_sans_bold, FontWeight.Bold),
)

/** See [NotoSans]. */
val NotoSansMono = FontFamily(
    Font(CoreR.font.noto_sans_mono_regular, FontWeight.Normal),
    Font(CoreR.font.noto_sans_mono_bold, FontWeight.Bold),
)

/** Every style of [this] in [family], its sizes and weights kept. */
fun Typography.inFamily(family: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

/** [base] in the chosen font; [base] itself for [AppFont.SYSTEM]. */
fun AppFont.typography(base: Typography): Typography = when (this) {
    AppFont.SYSTEM -> base
    AppFont.NOTO_SANS -> base.inFamily(NotoSans)
    AppFont.NOTO_SANS_MONO -> base.inFamily(NotoSansMono)
}

/**
 * The family a clock's reading is drawn in. Monospace for [AppFont.SYSTEM], as always; the
 * chosen face otherwise -- Noto Sans's numerals are all one width, as a clock needs.
 */
fun AppFont.clockFamily(): FontFamily = when (this) {
    AppFont.SYSTEM -> FontFamily.Monospace
    AppFont.NOTO_SANS -> NotoSans
    AppFont.NOTO_SANS_MONO -> NotoSansMono
}

/** The family of every clock reading on screen, set by the theme from the chosen font. */
val LocalClockFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }
