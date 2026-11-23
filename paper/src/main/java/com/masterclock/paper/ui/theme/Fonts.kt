package com.masterclock.paper.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.masterclock.core.R as CoreR

/**
 * Noto Sans and Noto Sans Mono, 400 and 700, shipped with the app rather than taken from the
 * device. Noto Sans has the whole of Latin, Greek and Cyrillic: Polish, Turkish, Russian and
 * Ukrainian among the languages being weighed, where the E-Ink build's Lato lacks Turkish's
 * dotted capital I, its g and s with their marks, and Cyrillic altogether -- letters the system
 * then drew in a face of its own, mismatched. Noto Sans Mono gives the clock's digits one width
 * and one look on every device, where "monospace" was whatever the phone had.
 *
 * Two weights each. A weight asked for between them takes the nearer one; a bold faked from the
 * regular, as one weight alone would have meant, smears on an E Ink panel. Writing scripts outside
 * these -- Japanese, Chinese, Arabic, Devanagari -- are not in these files, and the system's own
 * fonts draw them, letter by letter, as before.
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
