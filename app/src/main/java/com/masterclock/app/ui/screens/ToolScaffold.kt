package com.masterclock.app.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.masterclock.app.R

/**
 * Title bar and back arrow shared by every full-screen tool.
 *
 * It lives in its own file, and in src/main rather than beside the tools themselves, because the
 * tool screens are COMPLETE only while StatisticsScreen -- which Standard and Lite also reach --
 * uses the same scaffold. Leaving it in ToolsScreens.kt would have pinned that whole file into
 * every flavor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable (RowScope.() -> Unit) = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                // Undo, redo and clear leave a title little room, and "Modifica la scacchiera" took
                // two lines of the bar.
                title = { OneLineText(title, fontWeight = FontWeight.Bold, minSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
                },
                actions = actions
            )
        },
        content = content
    )
}

/**
 * The smallest a tab's label is drawn at: a size on the screen, not in the reader's font scale.
 *
 * A floor in sp grows with the font scale, so at 130 % a 12 sp label had one step to shrink by
 * before it was cut -- "Affichag / e", "Les" for "Les miens". Nine dp is a little under what the
 * label measures at the normal scale, which is where a tab laid out for it still fits.
 */
@Composable
fun tabLabelFloor(): TextUnit = with(LocalDensity.current) { 9.dp.toSp() }

/**
 * A label kept to one line: it shrinks, down to [minSize], rather than wrap where a translation
 * runs longer than the English it was laid out for, and only past that is it cut short.
 */
@Composable
fun OneLineText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    minSize: TextUnit = 11.sp,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
) {
    val full = if (style.fontSize.isSpecified) style.fontSize else 14.sp
    Text(
        text,
        modifier = modifier,
        style = style,
        color = color,
        fontWeight = fontWeight,
        letterSpacing = letterSpacing,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = if (full.value > minSize.value) TextAutoSize.StepBased(minFontSize = minSize, maxFontSize = full) else null,
    )
}
