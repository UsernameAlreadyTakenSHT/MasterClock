package com.masterclock.app.ui.screens

import androidx.activity.compose.BackHandler
import android.content.Intent
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.ImageLoader
import coil3.svg.SvgDecoder
import com.masterclock.app.R
import com.masterclock.app.logic.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import kotlin.random.Random
import android.content.Context as AndroidContext
import android.content.ClipData
import android.content.ClipboardManager

@Composable
fun GameLogsScreen(history: List<GameLog>, historyLoaded: Boolean, timePadding: TimePadding, onBack: () -> Unit) {
    // Hoisted: used from the share button callback, outside composable scope.
    val shareLogTitle = stringResource(R.string.tools_share_log)
    val shareFailedText = stringResource(R.string.toast_export_failed)
    val context = LocalContext.current
    val clipboardManager = remember { context.getSystemService(AndroidContext.CLIPBOARD_SERVICE) as ClipboardManager }
    // By id, so it survives a recreation; and Back closes the detail first. The detail was local
    // state with no BackHandler, so the system Back left Game Logs altogether from it.
    var selectedLogId by rememberSaveable { mutableStateOf<String?>(null) }
    val openLog = history.firstOrNull { it.id == selectedLogId }
    BackHandler(enabled = openLog != null) { selectedLogId = null }
    if (openLog == null) {
        val sortedHistory = remember(history) { history.sortedByDescending { it.startTime } }
        ToolScaffold(title = stringResource(R.string.tools_game_logs), onBack = onBack) { padding ->
            // Nothing while the history is still being read, rather than "no games" over a full one.
            if (!historyLoaded) return@ToolScaffold
            if (sortedHistory.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.tools_no_games), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Keyed on id, not startTime: startTime arrives verbatim from an imported log
                    // and two logs sharing one crashed this list outright -- Compose throws on a
                    // duplicate key. id is the entity's Room primary key, so the database itself
                    // guarantees it is unique here.
                    items(sortedHistory, key = { it.id }) { logEntry ->
                        val date = isoDateTime(logEntry.startTime)
                        Card(
                            onClick = { selectedLogId = logEntry.id },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.AutoMirrored.Filled.TextSnippet, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(8.dp))
                                    val moveTotal = logEntry.events.count { it.eventType == "MOVE" }
                                    Text(pluralStringResource(R.plurals.tools_moves_total, moveTotal, moveTotal), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        val log = openLog
        ToolScaffold(
            title = stringResource(R.string.tools_log_details), 
            onBack = { selectedLogId = null },
            actions = {
                IconButton(onClick = { 
                    val shareText = generateTxtLog(log, timePadding)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    // Caught: a game from an import can hold more text than an intent carries, and
                    // the refusal closed the app. A game played here is some fifty kilobytes.
                    runCatching { context.startActivity(Intent.createChooser(intent, shareLogTitle)) }
                        .onFailure { android.widget.Toast.makeText(context, shareFailedText, android.widget.Toast.LENGTH_LONG).show() }
                }) { Icon(Icons.Default.Share, stringResource(R.string.common_share)) }
            }
        ) { padding ->
            // One source for both the chart and the table below. These used to be two lists
            // zipped together, which silently misaligned every following row's time whenever
            // moveDurations() skipped a MOVE the display list kept. Hoisted out of the lazy
            // builder below, whose scope is not composable.
            val durations = remember(log) { moveDurations(log) }
            // Read in composition: the click handler is not, and lint rightly refuses resource
            // reads through LocalContext there.
            val exportFormat = moveExportFormatLabel(log.settings.gameType)
            val copiedText = stringResource(R.string.tools_copied_to_clipboard, exportFormat)

            // A lazy list, not a scrolling Column: this screen composes one Row per move, and a
            // long game -- or an imported log sitting at the sanitiser's event ceiling -- would
            // otherwise compose every one of them up front just to show the first screenful.
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
            ) {
                item {
                    Text(stringResource(R.string.tools_match_summary), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            log.initialPlayerStates.forEachIndexed { i, p ->
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.common_player_n, i + 1), fontWeight = FontWeight.Bold)
                                    Text(formatHms(p.timeRemainingMs, timePadding))
                                }
                            }
                        }
                    }
                }

                if (durations.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(24.dp))
                        Text(stringResource(R.string.tools_time_per_move), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        MoveDurationChart(durations, timePadding, Modifier.fillMaxWidth())
                    }
                }

                item {
                    Spacer(Modifier.height(24.dp))
                    Text(stringResource(R.string.tools_detailed_moves), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        Text("", modifier = Modifier.width(28.dp))
                        Text(stringResource(R.string.tools_move), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.tools_spent), modifier = Modifier.width(LOG_TIME_COLUMN), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.tools_left), modifier = Modifier.width(LOG_TIME_COLUMN), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // (player, moveNumber) is unique by construction: moveDurations() numbers each
                // player's moves from one, so the key survives any reordering.
                items(durations, key = { "${it.playerIndex}-${it.moveNumber}" }) { duration ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.tools_player_short, duration.playerIndex), modifier = Modifier.width(28.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(duration.moveNotation ?: stringResource(R.string.tools_move), modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                        Text(
                            formatMoveSpent(duration.durationMs, timePadding),
                            modifier = Modifier.width(LOG_TIME_COLUMN),
                            maxLines = 1,
                            textAlign = TextAlign.End,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            duration.timeRemainingMs?.let { formatHms(it, timePadding) } ?: "",
                            modifier = Modifier.width(LOG_TIME_COLUMN),
                            maxLines = 1,
                            textAlign = TextAlign.End,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }

                item {
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = {
                            val clip = ClipData.newPlainText("Match $exportFormat", generateMoveExport(log))
                            clipboardManager.setPrimaryClip(clip)
                            Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.tools_copy_format, moveExportFormatLabel(log.settings.gameType)))
                    }
                }
            }
        }
    }
}

/**
 * Says a tool's result once it has landed. The coin, the die and the card show it only at the end of
 * their animation, and nothing announced it: a TalkBack user who tapped heard nothing until they went
 * looking. It is the state of the control that was tapped, which a live region speaks as it
 * changes; empty while the animation runs, as the coin's face turns over many times a toss. It was
 * a node of its own, which was one more stop, silent or repeating the result, for a finger
 * exploring the screen.
 */
private fun Modifier.announcesResult(text: String): Modifier = semantics {
    liveRegion = LiveRegionMode.Polite
    if (text.isNotEmpty()) stateDescription = text
}

@Composable
fun CoinTossScreen(onBack: () -> Unit) {
    var isSpinning by remember { mutableStateOf(false) }
    var landedHeads by remember { mutableStateOf<Boolean?>(null) }
    val headsText = stringResource(R.string.tools_heads)
    val tailsText = stringResource(R.string.tools_tails)
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }

    ToolScaffold(title = stringResource(R.string.settings_more_coin_toss), onBack = onBack) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .graphicsLayer {
                        rotationY = rotation.value
                        cameraDistance = 12f * density
                    }
                    .announcesResult(if (isSpinning) "" else landedHeads?.let { if (it) headsText else tailsText }.orEmpty())
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = !isSpinning,
                        onClickLabel = stringResource(R.string.tools_toss_coin),
                    ) {
                        isSpinning = true
                        scope.launch {
                            val targetHeads = Random.nextBoolean()
                            val currentPos = rotation.value % 360f
                            rotation.snapTo(currentPos)
                            val totalDegrees = (30..40).random() * 360f + (if (targetHeads) 0f else 180f)
                            
                            rotation.animateTo(
                                targetValue = totalDegrees,
                                animationSpec = tween(
                                    durationMillis = 2500,
                                    easing = CubicBezierEasing(0.15f, 0f, 0.2f, 1f)
                                )
                            )
                            landedHeads = targetHeads
                            isSpinning = false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val currentRotation = (rotation.value % 360f + 360f) % 360f
                val isHeadsSide = currentRotation < 90f || currentRotation > 270f
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(8.dp, MaterialTheme.colorScheme.primary),
                    shadowElevation = 16.dp
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                rotationY = -rotation.value
                            }
                    ) {
                        Text(
                            text = if (isHeadsSide) stringResource(R.string.tools_heads) else stringResource(R.string.tools_tails),
                            fontWeight = FontWeight.Black,
                            fontSize = 36.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DiceRollScreen(onBack: () -> Unit) {
    var result by remember { mutableIntStateOf(1) }
    var rolled by remember { mutableStateOf(false) }
    var isRolling by remember { mutableStateOf(false) }
    val rotX = remember { Animatable(0f) }
    val rotY = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }

    ToolScaffold(title = stringResource(R.string.tools_dice_roll), onBack = onBack) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .scale(scale.value)
                    .graphicsLayer {
                        rotationX = rotX.value
                        rotationY = rotY.value
                        cameraDistance = 12f * density
                    }
                    .announcesResult(if (isRolling || !rolled) "" else result.toString())
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = !isRolling,
                        onClickLabel = stringResource(R.string.tools_roll_dice),
                    ) {
                        isRolling = true
                        scope.launch {
                            val newResult = (1..6).random()
                            rotX.snapTo(rotX.value % 360f)
                            rotY.snapTo(rotY.value % 360f)
                            
                            launch { 
                                scale.animateTo(1.2f, tween(500, easing = FastOutSlowInEasing))
                                scale.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) 
                            }
                            launch {
                                rotX.animateTo(
                                    rotX.value + (15..20).random() * 360f,
                                    tween(2000, easing = CubicBezierEasing(0.15f, 0f, 0.2f, 1f))
                                )
                            }
                            launch {
                                rotY.animateTo(
                                    rotY.value + (15..20).random() * 360f,
                                    tween(2000, easing = CubicBezierEasing(0.15f, 0f, 0.2f, 1f))
                                )
                            }
                            delay(2000)
                            result = newResult
                            rolled = true
                            isRolling = false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(6.dp, MaterialTheme.colorScheme.primary),
                    shadowElevation = 12.dp
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                rotationX = -rotX.value
                                rotationY = -rotY.value
                            }
                    ) {
                        if (isRolling) {
                            Text(
                                "?",
                                fontSize = 80.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
                            )
                        } else {
                            DieDots(value = result, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DieDots(value: Int, color: Color) {
    Box(Modifier.size(100.dp)) {
        when (value) {
            1 -> Box(Modifier.size(22.dp).background(color, CircleShape).align(Alignment.Center))
            2 -> { Box(Modifier.size(18.dp).background(color, CircleShape).align(Alignment.TopEnd)); Box(Modifier.size(18.dp).background(color, CircleShape).align(Alignment.BottomStart)) }
            3 -> { Box(Modifier.size(18.dp).background(color, CircleShape).align(Alignment.TopEnd)); Box(Modifier.size(18.dp).background(color, CircleShape).align(Alignment.Center)); Box(Modifier.size(18.dp).background(color, CircleShape).align(Alignment.BottomStart)) }
            4 -> { Box(Modifier.size(18.dp).background(color, CircleShape).align(Alignment.TopStart)); Box(Modifier.size(18.dp).background(color, CircleShape).align(Alignment.TopEnd)); Box(Modifier.size(18.dp).background(color, CircleShape).align(Alignment.BottomStart)); Box(Modifier.size(18.dp).background(color, CircleShape).align(Alignment.BottomEnd)) }
            5 -> { Box(Modifier.size(18.dp).background(color, CircleShape).align(Alignment.TopStart)); Box(Modifier.size(18.dp).background(color, CircleShape).align(Alignment.TopEnd)); Box(Modifier.size(18.dp).background(color, CircleShape).align(Alignment.Center)); Box(Modifier.size(18.dp).background(color, CircleShape).align(Alignment.BottomStart)); Box(Modifier.size(18.dp).background(color, CircleShape).align(Alignment.BottomEnd)) }
            6 -> { Box(Modifier.size(18.dp).background(color, CircleShape).align(Alignment.TopStart)); Box(Modifier.size(18.dp).background(color, CircleShape).align(Alignment.TopEnd)); Box(Modifier.size(18.dp).background(color, CircleShape).align(Alignment.CenterStart)); Box(Modifier.size(18.dp).background(color, CircleShape).align(Alignment.CenterEnd)); Box(Modifier.size(18.dp).background(color, CircleShape).align(Alignment.BottomStart)); Box(Modifier.size(18.dp).background(color, CircleShape).align(Alignment.BottomEnd)) }
        }
    }
}

@Composable
fun ShortStrawScreen(onBack: () -> Unit) {
    var strawCount by remember { mutableIntStateOf(5) }
    var shortStrawIndex by remember { mutableIntStateOf(-1) }
    val revealedIndices = remember { mutableStateListOf<Int>() }
    val interactionSource = remember { MutableInteractionSource() }

    ToolScaffold(title = stringResource(R.string.tools_short_straw), onBack = onBack) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            if (shortStrawIndex == -1) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        pluralStringResource(R.plurals.tools_straws, strawCount, strawCount),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(32.dp))
                    Slider(
                        value = strawCount.toFloat(),
                        onValueChange = { strawCount = Math.round(it) },
                        valueRange = 2f..10f,
                        // Seven stops between the ends for the nine values; see the Omni player count.
                        steps = 7,
                        modifier = Modifier.padding(horizontal = 48.dp)
                    )
                    Spacer(Modifier.height(64.dp))
                    Surface(
                        onClick = { shortStrawIndex = (0 until strawCount).random(); revealedIndices.clear() },
                        modifier = Modifier.size(width = 240.dp, height = 72.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 8.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.tools_prepare), fontWeight = FontWeight.Black, fontSize = 24.sp, color = MaterialTheme.colorScheme.onPrimary, letterSpacing = 2.sp)
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClickLabel = stringResource(R.string.tools_draw_again)
                        ) {
                            if (revealedIndices.contains(shortStrawIndex)) {
                                shortStrawIndex = -1
                                revealedIndices.clear()
                            }
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 100.dp, start = 24.dp, end = 24.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        val strawShort = stringResource(R.string.tools_straw_short)
                        val strawLong = stringResource(R.string.tools_straw_long)
                        repeat(strawCount) { i ->
                            val isRevealed = revealedIndices.contains(i)
                            val isShort = i == shortStrawIndex
                            val height by animateDpAsState(
                                targetValue = if (isRevealed) (if (isShort) 120.dp else 240.dp) else 300.dp,
                                animationSpec = tween(1000, easing = CubicBezierEasing(0.175f, 0.885f, 0.32f, 1.275f)),
                                label = "strawHeight"
                            )
                            val color by animateColorAsState(
                                targetValue = if (isRevealed && isShort) MaterialTheme.colorScheme.error 
                                              else MaterialTheme.colorScheme.primary,
                                label = "strawColor"
                            )
                            Box(
                                modifier = Modifier
                                    // As wide as before where there is room, narrower where ten of them have none: 320 dp of straws in 312.
                                    // A width asked for, which the share it is given holds down: at most
                                    // 32 dp was no width at all, a straw having nothing in it to be as wide as.
                                    .weight(1f, fill = false)
                                    .padding(horizontal = 2.dp)
                                    .width(32.dp)
                                    .height(height)
                                    // Short or long, said as it is drawn: the coin, the die and the card speak, and a straw only changed height.
                                    .announcesResult(if (isRevealed) (if (isShort) strawShort else strawLong) else "")
                                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                    .background(color)
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null,
                                        enabled = !isRevealed && !revealedIndices.contains(shortStrawIndex),
                                        onClickLabel = stringResource(R.string.tools_pick_straw, i + 1)
                                    ) {
                                        revealedIndices.add(i)
                                    }
                                    .border(
                                        width = if (isRevealed) 2.dp else 0.dp,
                                        color = Color.White.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                                    )
                                    .shadow(if (isRevealed) 8.dp else 0.dp, CircleShape)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RandomCardScreen(onBack: () -> Unit) {
    val suits = listOf("♠", "♣", "♥", "♦")
    // The card by name for TalkBack, which read "A ♠" as "a black spade suit", and Q, J, K as letters.
    val suitNames = mapOf("♠" to stringResource(R.string.card_suit_spades), "♣" to stringResource(R.string.card_suit_clubs), "♥" to stringResource(R.string.card_suit_hearts), "♦" to stringResource(R.string.card_suit_diamonds))
    val rankNames = mapOf("A" to stringResource(R.string.card_rank_ace), "J" to stringResource(R.string.card_rank_jack), "Q" to stringResource(R.string.card_rank_queen), "K" to stringResource(R.string.card_rank_king))
    val cardPattern = stringResource(R.string.card_name)
    fun cardName(value: String, suit: String) = String.format(Locale.ROOT, cardPattern, rankNames[value] ?: value, suitNames[suit] ?: suit)
    val values = listOf("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K")
    var currentCard by remember { mutableStateOf<Pair<String, String>?>(null) }
    var isAnimating by remember { mutableStateOf(false) }
    val rotation = remember { Animatable(0f) }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }

    ToolScaffold(title = stringResource(R.string.tools_random_card), onBack = onBack) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(width = 200.dp, height = 280.dp)
                    .graphicsLayer {
                        rotationY = rotation.value
                        translationX = offsetX.value
                        cameraDistance = 14f * density
                    }
                    .announcesResult(if (isAnimating) "" else currentCard?.let { (value, suit) -> cardName(value, suit) }.orEmpty())
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = !isAnimating,
                        onClickLabel = stringResource(R.string.tools_draw_again),
                    ) {
                        isAnimating = true
                        scope.launch {
                            if (currentCard != null) {
                                launch { rotation.animateTo(0f, tween(300, easing = FastOutSlowInEasing)) }
                            }
                            repeat(4) {
                                offsetX.animateTo(30f, tween(100, easing = LinearEasing))
                                offsetX.animateTo(-30f, tween(100, easing = LinearEasing))
                            }
                            offsetX.animateTo(0f, tween(100, easing = FastOutSlowInEasing))
                            currentCard = Pair(values.random(), suits.random())
                            rotation.animateTo(180f, tween(600, easing = FastOutSlowInEasing))
                            isAnimating = false
                        }
                    }
            ) {
                val isBack = rotation.value < 90f
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(16.dp),
                    color = if (isBack) MaterialTheme.colorScheme.primary else Color.White,
                    shadowElevation = 12.dp,
                    border = BorderStroke(2.dp, if(isBack) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.1f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                rotationY = -rotation.value
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val card = currentCard
                        if (isBack || card == null) {
                            Icon(Icons.Default.Style, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f))
                        } else {
                            val color = if (card.second == "♥" || card.second == "♦") Color(0xFFD32F2F) else Color(0xFF212121)
                            Box(Modifier.fillMaxSize().padding(20.dp)) {
                                Text(card.first + card.second, color = color, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.align(Alignment.TopStart))
                                Text(card.second, color = color, fontSize = 90.sp, modifier = Modifier.align(Alignment.Center))
                                Text(card.first + card.second, color = color, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.align(Alignment.BottomEnd).graphicsLayer { rotationZ = 180f })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BlindfoldTrainerScreen(onBack: () -> Unit) {
    val ranks = listOf("1", "2", "3", "4", "5", "6", "7", "8")
    val files = listOf("a", "b", "c", "d", "e", "f", "g", "h")
    var target by remember { mutableStateOf("e4") }
    var score by remember { mutableIntStateOf(0) }
    var timeLeft by remember { mutableIntStateOf(30) }
    var isPlaying by remember { mutableStateOf(false) }
    
    fun nextRound() { target = files.random() + ranks.random() }
    
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            timeLeft = 30
            while (timeLeft > 0) { delay(1000); timeLeft-- }
            isPlaying = false
        }
    }

    fun checkColor(isWhite: Boolean) {
        if (!isPlaying) return
        val fileIdx = files.indexOf(target.substring(0, 1))
        val rankIdx = ranks.indexOf(target.substring(1, 2))
        val correctIsWhite = (fileIdx + rankIdx) % 2 != 0
        if (isWhite == correctIsWhite) score++
        nextRound()
    }

    ToolScaffold(title = stringResource(R.string.settings_more_blindfold), onBack = onBack) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.tools_score, score), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("${timeLeft}s", color = if (timeLeft < 10) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                }

                if (!isPlaying) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (timeLeft == 0) {
                            Text(stringResource(R.string.tools_game_over), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black)
                            Text(stringResource(R.string.tools_final_score, score), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.height(32.dp))
                        }
                        StartTrialButton { score = 0; isPlaying = true; nextRound() }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(target.uppercase(Locale.US), style = MaterialTheme.typography.displayLarge.copy(fontSize = 120.sp), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(48.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Surface(onClick = { checkColor(true) }, modifier = Modifier.weight(1f).height(100.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.secondaryContainer, border = BorderStroke(4.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)), shadowElevation = 8.dp) {
                                Box(contentAlignment = Alignment.Center) { Text(stringResource(R.string.tools_white_square), color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Black, fontSize = 22.sp) }
                            }
                            Surface(onClick = { checkColor(false) }, modifier = Modifier.weight(1f).height(100.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primary, border = BorderStroke(4.dp, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)), shadowElevation = 8.dp) {
                                Box(contentAlignment = Alignment.Center) { Text(stringResource(R.string.tools_dark_square), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black, fontSize = 22.sp) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun KnightPathScreen(onBack: () -> Unit) {
    val ranks = listOf("1", "2", "3", "4", "5", "6", "7", "8")
    val files = listOf("a", "b", "c", "d", "e", "f", "g", "h")
    var startPos by remember { mutableStateOf("a1") }
    var targetPos by remember { mutableStateOf("h8") }
    var score by remember { mutableIntStateOf(0) }
    var timeLeft by remember { mutableIntStateOf(30) }
    var isPlaying by remember { mutableStateOf(false) }
    fun nextRound() { startPos = files.random() + ranks.random(); do { targetPos = files.random() + ranks.random() } while (targetPos == startPos) }
    LaunchedEffect(isPlaying) { if (isPlaying) { timeLeft = 30; while (timeLeft > 0) { delay(1000); timeLeft-- }; isPlaying = false } }
    fun getMinMoves(s: String, t: String): Int {
        val sx = files.indexOf(s[0].toString()); val sy = ranks.indexOf(s[1].toString()); val tx = files.indexOf(t[0].toString()); val ty = ranks.indexOf(t[1].toString())
        val queue = mutableListOf(Triple(sx, sy, 0)); val visited = mutableSetOf(Pair(sx, sy))
        val dx = intArrayOf(2, 1, -1, -2, -2, -1, 1, 2); val dy = intArrayOf(1, 2, 2, 1, -1, -2, -2, -1)
        while (queue.isNotEmpty()) { val (cx, cy, dist) = queue.removeAt(0); if (cx == tx && cy == ty) return dist; for (i in 0..7) { val nx = cx + dx[i]; val ny = cy + dy[i]; if (nx in 0..7 && ny in 0..7 && visited.add(Pair(nx, ny))) queue.add(Triple(nx, ny, dist + 1)) } }
        return -1
    }
    fun checkAnswer(ans: Int) { if (!isPlaying) return; if (ans == getMinMoves(startPos, targetPos)) score++; nextRound() }

    ToolScaffold(title = stringResource(R.string.settings_more_knight), onBack = onBack) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.tools_score, score), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("${timeLeft}s", color = if (timeLeft < 10) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                }
                if (!isPlaying) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (timeLeft == 0) { Text(stringResource(R.string.tools_game_over), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black); Text(stringResource(R.string.tools_final_score, score), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(32.dp)) }
                        StartTrialButton { score = 0; isPlaying = true; nextRound() }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(stringResource(R.string.tools_start), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary); Text(startPos.uppercase(Locale.US), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black) }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(stringResource(R.string.tools_target), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error); Text(targetPos.uppercase(Locale.US), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black) }
                        }
                        Spacer(Modifier.height(32.dp)); Text(stringResource(R.string.tools_min_moves), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(16.dp))
                        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalArrangement = Arrangement.spacedBy(16.dp)) { (1..6).forEach { n -> Surface(onClick = { checkAnswer(n) }, modifier = Modifier.size(60.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary), shadowElevation = 4.dp) { Box(contentAlignment = Alignment.Center) { Text(n.toString(), fontWeight = FontWeight.Black, fontSize = 24.sp) } } } }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun StopPrecisionScreen(onBack: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    var isRunning by remember { mutableStateOf(false) }
    var startTime by remember { mutableLongStateOf(0L) }
    var displayTime by remember { mutableLongStateOf(0L) }
    // elapsedRealtime, not currentTimeMillis, for the same reason the two clocks use it: wall-clock
    // time answers what time it is, not how much has passed, and an NTP sync or a manual change
    // moves it mid-attempt. Here that lands on a reading the exercise scores to the millisecond --
    // a correction backwards can even show a negative elapsed time.
    // The loop only feeds the number on screen, so it runs at roughly one frame rather than the 200
    // times a second it used to: nothing can display more than the screen refreshes. What gets
    // scored is read at the tap instead -- see the button below -- which is also what makes the
    // slower poll safe. Taking the result from the last poll was leaving it up to a poll period
    // stale, on an exercise that colours the answer by ten-millisecond bands.
    LaunchedEffect(isRunning) { if (isRunning) { startTime = SystemClock.elapsedRealtime(); while (isRunning) { displayTime = SystemClock.elapsedRealtime() - startTime; delay(16) } } }
    ToolScaffold(title = stringResource(R.string.tools_stop_precision), onBack = onBack) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.tools_target_time), modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(modifier = Modifier.height(60.dp), contentAlignment = Alignment.Center) { if (!isRunning && displayTime > 0) { val diff = Math.abs(displayTime - 5000L); val color = when { diff <= 10 -> Color(0xFF2196F3); diff <= 20 -> Color(0xFF4CAF50); diff <= 50 -> Color(0xFFFFEB3B); diff <= 100 -> Color(0xFFFF9800); else -> MaterialTheme.colorScheme.error }; Text(text = stringResource(R.string.tools_offset, if (displayTime > 5000) "+" else "-", diff), color = color, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall) } }
                OneLineText(text = String.format(locale, "%.3fs", displayTime / 1000f), modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.displayLarge.copy(fontSize = 90.sp, fontFamily = FontFamily.Monospace), minSize = 40.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(72.dp)); Surface(onClick = { if (isRunning) { displayTime = SystemClock.elapsedRealtime() - startTime; isRunning = false } else { displayTime = 0; isRunning = true } }, modifier = Modifier.size(width = 240.dp, height = 72.dp), shape = RoundedCornerShape(20.dp), color = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, shadowElevation = 8.dp) { Box(contentAlignment = Alignment.Center) { Text(if (isRunning) stringResource(R.string.tools_stop) else stringResource(R.string.tools_start), fontWeight = FontWeight.Black, fontSize = 24.sp, color = MaterialTheme.colorScheme.onPrimary, letterSpacing = 2.sp) } }
                Spacer(Modifier.height(48.dp))
            }
        }
    }
}

@Composable
fun NameSquareScreen(onBack: () -> Unit) {
    val ranks = listOf("1", "2", "3", "4", "5", "6", "7", "8"); val files = listOf("a", "b", "c", "d", "e", "f", "g", "h"); var target by remember { mutableStateOf("e4") }; var score by remember { mutableIntStateOf(0) }; var timeLeft by remember { mutableIntStateOf(30) }; var isPlaying by remember { mutableStateOf(false) }
    fun nextRound() { target = files.random() + ranks.random() }
    LaunchedEffect(isPlaying) { if (isPlaying) { timeLeft = 30; while (timeLeft > 0) { delay(1000); timeLeft-- }; isPlaying = false } }
    ToolScaffold(title = stringResource(R.string.tools_name_square_title), onBack = onBack) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
            // It scrolls: on a short screen or at a large font the board's last ranks were off it, and a square asked for there could not be tapped.
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.tools_score, score), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black); Text("${timeLeft}s", color = if (timeLeft < 10) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black) }
                if (!isPlaying) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (timeLeft == 0) { Text(stringResource(R.string.tools_game_over), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black); Text(stringResource(R.string.tools_final_score, score), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(32.dp)) }
                        StartTrialButton { score = 0; isPlaying = true; nextRound() }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(target.uppercase(Locale.US), style = MaterialTheme.typography.displayLarge.copy(fontSize = 110.sp), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(32.dp))
                        val nameSquareSize = boardSquareSize(42.dp, 8, chrome = 60.dp)
                        Column(Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(6.dp).shadow(4.dp, RoundedCornerShape(12.dp))) { for (r in 8 downTo 1) { Row { for (f in 1..8) { val square = files[f-1] + ranks[r-1]; val isDark = (r + f) % 2 == 0; Box(modifier = Modifier.size(nameSquareSize).background(if (isDark) Color(0xFF769656) else Color(0xFFEEEED2)).semantics { contentDescription = square }.clickable { if (square == target) { score++; nextRound() } }) } } } }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun Chess960Screen(onBack: () -> Unit) {
    val fenCopiedText = stringResource(R.string.toast_fen_copied)
    // Saveable, all three: a theme or language change, split screen, a fold or the process being
    // killed recreates the screen, and a plain remember then drew a new position -- in the middle of
    // setting up a board, with the old FEN gone -- and went back to Chess960.
    var variant by rememberSaveable { mutableStateOf(ChessVariant.CHESS960) }
    // One draw for both sides, as refresh() makes for a symmetric variant. Each side was drawn on its
    // own, so the first position shown -- labelled Chess960 -- was not one, nor was its FEN. A back
    // rank is kept as its eight letters, which a Bundle holds as they are.
    val firstDraw = remember { generateChessPosition(ChessVariant.CHESS960).joinToString("") }
    var whiteRank by rememberSaveable { mutableStateOf(firstDraw) }
    var blackRank by rememberSaveable { mutableStateOf(firstDraw) }
    val whitePos = whiteRank.map { it.toString() }
    val blackPos = blackRank.map { it.toString() }

    fun refresh() {
        val white = generateChessPosition(variant).joinToString("")
        whiteRank = white
        blackRank = if (variant.isSymmetric) white else generateChessPosition(variant).joinToString("")
    }

    val context = LocalContext.current
    val clipboardManager = remember { context.getSystemService(AndroidContext.CLIPBOARD_SERVICE) as ClipboardManager }

    ToolScaffold(title = stringResource(R.string.tools_variant_generator), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                // It scrolls: the cards, the board and the buttons are some 740 dp tall at 360 dp
                // wide, a 360 x 800 screen leaves about 650, and the two buttons, measured last,
                // were given what was left -- nothing.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            // Closer together than they were, so that the two buttons are on the screen at 360 dp
            // without scrolling for them; the scroll is for a shorter screen or a larger font.
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Layout Row 1: Chess960 (Increased Height)
            VariantCard(
                label = "Chess960", 
                selected = variant == ChessVariant.CHESS960,
                modifier = Modifier.fillMaxWidth().height(56.dp) 
            ) { variant = ChessVariant.CHESS960; refresh() }

            // Layout Row 2: Double 960 (L) + Chess2880 (R)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                VariantCard(
                    label = "Double 960", 
                    selected = variant == ChessVariant.DOUBLE_CHESS960,
                    modifier = Modifier.weight(1f)
                ) { variant = ChessVariant.DOUBLE_CHESS960; refresh() }
                
                VariantCard(
                    label = "Chess2880", 
                    selected = variant == ChessVariant.CHESS2880,
                    modifier = Modifier.weight(1f)
                ) { variant = ChessVariant.CHESS2880; refresh() }
            }

            // Layout Row 3: Transcendental (L) + Shuffle (R)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                VariantCard(
                    label = "Transcendental", 
                    selected = variant == ChessVariant.TRANSCENDENTAL,
                    modifier = Modifier.weight(1f)
                ) { variant = ChessVariant.TRANSCENDENTAL; refresh() }

                VariantCard(
                    label = "Shuffle", 
                    selected = variant == ChessVariant.SHUFFLE,
                    modifier = Modifier.weight(1f)
                ) { variant = ChessVariant.SHUFFLE; refresh() }
            }

            // At least two lines' height, so the board does not jump between variants; more where a
            // large font needs it, which a fixed height cut.
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when(variant) { 
                        ChessVariant.CHESS960 -> stringResource(R.string.variant_chess960_desc)
                        ChessVariant.SHUFFLE -> stringResource(R.string.variant_shuffle_desc)
                        ChessVariant.TRANSCENDENTAL -> stringResource(R.string.variant_transcendental_desc)
                        ChessVariant.CHESS2880 -> stringResource(R.string.variant_chess2880_desc)
                        ChessVariant.DOUBLE_CHESS960 -> stringResource(R.string.variant_double960_desc)
                    }, 
                    style = MaterialTheme.typography.bodySmall, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant, 
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            Spacer(Modifier.height(4.dp))
            FullChessBoardDisplay(whitePos, blackPos)
            
            // Removed Piece Code Display (letters) but kept logic for FEN

            // Action Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { refresh() },
                    modifier = Modifier.weight(1.5f).height(64.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(R.string.tools_generate), fontWeight = FontWeight.Black, fontSize = 20.sp)
                }
                
                Surface(
                    onClick = { 
                        val clip = ClipData.newPlainText("Chess FEN", generateFen(whitePos, blackPos, castling = variant.kingBetweenRooks))
                        clipboardManager.setPrimaryClip(clip)
                        Toast.makeText(context, fenCopiedText, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f).height(64.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.tools_copy_fen), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun ScoreboardScreen(viewModel: ChessTimerViewModel, onBack: () -> Unit) {
    val session by viewModel.scoreboard.collectAsState(); var newResultText by remember { mutableStateOf("") }; val sortedGames = remember(session.games) { session.games.asReversed() }
    // Asked first: one tap on the icon in the bar wiped every result, with no way back.
    var confirmingReset by rememberSaveable { mutableStateOf(false) }
    if (confirmingReset) ConfirmDestructiveDialog(
        title = stringResource(R.string.tools_reset_scoreboard),
        confirmLabel = stringResource(R.string.common_reset),
        onConfirm = { viewModel.resetScoreboard() },
        onDismiss = { confirmingReset = false },
    )
    ToolScaffold(title = stringResource(R.string.settings_more_scoreboard), onBack = onBack, actions = { IconButton(onClick = { confirmingReset = true }) { Icon(Icons.Default.Refresh, stringResource(R.string.tools_reset_scoreboard)) } }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Typed here and written a moment after the last key, as a note is: each character wrote
            // the whole scoreboard, every game on it, to the store. Held to the length the store keeps,
            // which the next launch cut a longer name to. Keyed by the session, which Reset replaces.
            var names by remember(session.id) { mutableStateOf(session.player1Name to session.player2Name) }
            PersistDebounced(names) { (first, second) -> viewModel.updateScoreboardNames(first, second) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = names.first, onValueChange = { names = names.copy(first = it.take(MAX_SCOREBOARD_TEXT_CHARS)) }, label = { Text(stringResource(R.string.common_player_n, 1)) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), singleLine = true); OutlinedTextField(value = names.second, onValueChange = { names = names.copy(second = it.take(MAX_SCOREBOARD_TEXT_CHARS)) }, label = { Text(stringResource(R.string.common_player_n, 2)) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), singleLine = true) }
            Card(modifier = Modifier.weight(1f).fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                if (session.games.isEmpty()) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.tools_no_games), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                else { LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(sortedGames, key = { it.id }) { game -> Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp), border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)) { Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(game.result, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold); Text(isoDateTime(game.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } } }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(value = newResultText, onValueChange = { newResultText = it.take(MAX_SCOREBOARD_TEXT_CHARS) }, placeholder = { OneLineText(stringResource(R.string.tools_add_result_placeholder)) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), singleLine = true); Button(onClick = { if (newResultText.isNotBlank()) { viewModel.addScoreboardGame(newResultText); newResultText = "" } }, shape = RoundedCornerShape(12.dp), modifier = Modifier.height(56.dp)) { Icon(Icons.Default.Add, stringResource(R.string.tools_add_result)) } }
            // Each result on one line: in Noto Sans Mono, on a narrow screen or at a large font, "1 -"
            // stood over "0". Narrower padding first, then the label shrinks rather than wrap.
            val resultPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("1 - 0", "½ - ½", "0 - 1").forEach { result ->
                    Button(onClick = { viewModel.addScoreboardGame(result) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), contentPadding = resultPadding) { OneLineText(result) }
                }
            }
        }
    }
}

/**
 * The width of a game log's time columns. At 64dp a clock of an hour or more -- "1:23:45" in
 * monospace -- did not fit, and wrapped onto a second line.
 */
private val LOG_TIME_COLUMN = 80.dp

/**
 * The side of a square on a board [squares] across: [preferred], or less where the window is too
 * narrow for it once [chrome] -- the page's padding and the board's own, both sides -- is taken off.
 * The boards were drawn at a fixed size, eight squares of 40 or 42dp, and on a 360dp phone the files
 * on the right were cut off: the h-file of a Chess960 position, and squares Name Square asks for.
 */
@Composable
internal fun boardSquareSize(preferred: Dp, squares: Int, chrome: Dp): Dp {
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    return minOf(preferred, (windowWidth - chrome) / squares)
}

@Composable
fun FullChessBoardDisplay(whitePos: List<String>, blackPos: List<String>) {
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }
    val square = boardSquareSize(40.dp, 8, chrome = 66.dp)
    // One description for the board, the two back ranks by name. Each piece was its own node,
    // described by its letter: TalkBack read thirty-two letters, "K" and "k" alike, and no squares.
    val pieceNames = mapOf(
        "K" to stringResource(R.string.a11y_piece_king),
        "Q" to stringResource(R.string.a11y_piece_queen),
        "R" to stringResource(R.string.a11y_piece_rook),
        "B" to stringResource(R.string.a11y_piece_bishop),
        "N" to stringResource(R.string.a11y_piece_knight),
    )
    fun spoken(rank: List<String>) = rank.joinToString(", ") { pieceNames[it.uppercase(Locale.US)] ?: it }
    val boardDescription = stringResource(R.string.a11y_board_back_ranks, spoken(whitePos), spoken(blackPos))
    Surface(
        modifier = Modifier.clearAndSetSemantics { contentDescription = boardDescription },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(8.dp)) {
            for (rank in 8 downTo 1) {
                Row {
                    for (file in 1..8) {
                        val squareColor = if ((rank + file) % 2 == 0) Color(0xFF769656) else Color(0xFFEEEED2)
                        val rawPiece = when (rank) {
                            8 -> blackPos[file - 1].lowercase(Locale.US) 
                            7 -> "p"
                            2 -> "P"
                            1 -> whitePos[file - 1].uppercase(Locale.US)
                            else -> ""
                        }
                        
                        Box(
                            modifier = Modifier.size(square).background(squareColor), 
                            contentAlignment = Alignment.Center
                        ) {
                            if (rawPiece.isNotEmpty()) {
                                AsyncImage(
                                    model = getPieceSvgPath(rawPiece),
                                    contentDescription = null,
                                    modifier = Modifier.size(square * 4 / 5),
                                    imageLoader = imageLoader
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun getPieceSvgPath(p: String): String {
    val type = when(p.lowercase(Locale.US)) {
        "p" -> "p"; "n" -> "n"; "b" -> "b"; "r" -> "r"; "q" -> "q"; "k" -> "k"
        // Draughts, from the same Wikimedia set and named the same way: one stone for a man, two
        // stacked for a dame.
        "m" -> "1"; "d" -> "2"
        else -> "p"
    }
    val color = if (p.uppercase(Locale.US) == p) "l" else "d"
    return "file:///android_asset/pieces/Chess_${type}${color}t45.svg"
}

@Composable
fun VariantCard(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    ) {
        Box(contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
    }
}

/** Compact think-time for one move: "8.4s" below a minute, "2m05s" above it. */
/**
 * How long a move took, as a duration rather than a clock reading: "1m05s", "4.2s".
 *
 * Only MINIMAL changes anything here. The leading unit is never widened, because "01m05s" is not a
 * form anyone writes -- the leading zero of [TimePadding.FULL] belongs to clock readings.
 */
fun formatMoveSpent(ms: Long, padding: TimePadding = TimePadding.FULL): String =
    if (ms >= 60_000) "${ms / 60_000}m${padTimeUnit((ms % 60_000) / 1000, isLeading = false, padding = padding)}s"
    else String.format(Locale.US, "%.1fs", ms / 1000f)

/**
 * A clock reading for the game log. Minutes are always shown, hours only when there are any --
 * independent of the always-show settings, which govern the live clock rather than the record.
 */
fun formatHms(ms: Long, padding: TimePadding = TimePadding.FULL): String {
    val s = (ms / 1000) % 60; val m = (ms / 60000) % 60; val h = ms / 3600000
    return formatClockReading(h, m, s, showHours = h > 0, showMinutes = true, padding = padding)
}

/**
 * A game's date, year first as everywhere in the app; see isoDateTime. It was Date.toString(),
 * "Mon Oct 26 14:30:00 CET 2026", which sorts by weekday and reads in English alone.
 */
private fun logDate(startTime: Long): String = isoDateTime(startTime)

fun generateTxtLog(log: GameLog, padding: TimePadding = TimePadding.FULL): String {
    val sb = StringBuilder("MasterClock Log\nDate: ${logDate(log.startTime)}\n\n")
    // A clock reading only for events that carry one. PAUSE, FLAG and the like have none, and they
    // all used to read "at 00:00" -- which for a flag looks like a time, and is not one.
    log.events.forEach { e ->
        val who = if (e.playerIndex != null) "P${e.playerIndex}" else "SYSTEM"
        val detail = e.detail?.let { " $it" } ?: ""
        val at = e.timeRemainingMs?.let { " at ${formatHms(it, padding)}" } ?: ""
        sb.append("$who: ${e.eventType}$detail$at\n")
    }
    return sb.toString()
}

// generatePgn and generatePdn live in core (MoveExport.kt), where they are tested.

/**
 * A shogi game's moves as plain numbered text. It was called KIF, and is not: KIF has its own
 * header and columns, and shogi software refused what this wrote. It is offered as text for what
 * it is -- shogi is withdrawn from the game picker, so only a game recorded before reaches it.
 */
fun generateMoveListText(log: GameLog): String {
    val sb = StringBuilder("MasterClock\n${logDate(log.startTime)}\n\n")
    log.events.filter { it.eventType == "MOVE" }.forEachIndexed { i, m -> sb.append("${i+1}: ${m.moveNotation ?: "?"}\n") }
    return sb.toString()
}

/** Move-list export format for a log's game, matching its [GameType] (was always PGN regardless). */
fun moveExportFormatLabel(gameType: GameType): String = when (gameType) {
    GameType.CHESS -> "PGN"
    GameType.DRAUGHTS -> "PDN"
    GameType.SHOGI -> "Text"
}

fun generateMoveExport(log: GameLog): String = when (log.settings.gameType) {
    GameType.CHESS -> generatePgn(log)
    GameType.DRAUGHTS -> generatePdn(log)
    GameType.SHOGI -> generateMoveListText(log)
}

/** Asks before something that cannot be undone; the message is the one the media deletions use. */
@Composable
internal fun ConfirmDestructiveDialog(title: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(stringResource(R.string.tools_delete_media_message)) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/**
 * The trainers' start button. It was 240dp whatever it said, and "VERSUCH STARTEN" filled it to
 * both edges on two lines: it now grows with its label, keeps a margin, and shrinks the label only
 * where the screen itself is too narrow.
 */
@Composable
private fun StartTrialButton(onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.widthIn(min = 240.dp).height(72.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primary, shadowElevation = 8.dp) {
        Box(Modifier.padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
            OneLineText(stringResource(R.string.tools_start_trial), style = LocalTextStyle.current.copy(fontSize = 24.sp), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimary, letterSpacing = 2.sp, minSize = 14.sp)
        }
    }
}
