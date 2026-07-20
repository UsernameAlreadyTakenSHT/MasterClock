package com.masterclock.paper.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.masterclock.paper.R

/**
 * Official Mudita Font Family: Lato
 */
val LatoFontFamily = FontFamily(
    Font(resId = R.font.lato_regular, weight = FontWeight.Normal),
    Font(resId = R.font.lato_bold, weight = FontWeight.Bold),
    Font(resId = R.font.lato_light, weight = FontWeight.Light),
    Font(resId = R.font.lato_thin, weight = FontWeight.Thin),
    Font(resId = R.font.lato_black, weight = FontWeight.Black)
)

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = LatoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)

/**
 * Official Mudita E-Ink Typography (MMD)
 * All roles use FontWeight.Medium, matching the real MMD type scale. No Lato Medium
 * weight file is bundled (only Thin/Light/Regular/Bold/Black exist), so this renders
 * via the closest matched weight (Regular) until a Medium .ttf is added.
 */
val EInkTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = LatoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = LatoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = LatoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = LatoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = LatoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = LatoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = LatoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = LatoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = LatoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = LatoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    )
)
