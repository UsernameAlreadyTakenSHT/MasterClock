package com.masterclock.paper.ui.theme

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.mudita.mmd.ThemeMMD
import com.mudita.mmd.eInkTypography

/**
 * The E-Ink build's theme: Mudita's own ThemeMMD, which brings their monochrome scheme, the Lato
 * type scale and ripples turned off everywhere. The colours, type scale and indication used to be
 * written out here by hand in their shape; they come from the library now, and only the reversed
 * scheme, which MMD does not offer, is this app's.
 */
@Composable
fun MasterClockTheme(
    eInkDarkMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        // Both bars: only the status bar followed, so with reversed colours the navigation bar's
        // buttons stayed dark on black.
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !eInkDarkMode
            isAppearanceLightNavigationBars = !eInkDarkMode
        }
    }

    ThemeMMD(
        colorScheme = if (eInkDarkMode) EInkDarkColorScheme else EInkLightColorScheme,
        typography = eInkTypography,
        content = content,
    )
}
