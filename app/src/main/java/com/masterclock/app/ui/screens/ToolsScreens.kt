package com.masterclock.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.toColorInt
import coil.compose.AsyncImage
import coil.ImageLoader
import coil.decode.SvgDecoder
import com.masterclock.app.logic.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random
import android.content.Context as AndroidContext
import android.content.ClipData
import android.content.ClipboardManager

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
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = actions
            )
        },
        content = content
    )
}

@Composable
fun GameLogsScreen(history: List<GameLog>, onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = remember { context.getSystemService(AndroidContext.CLIPBOARD_SERVICE) as ClipboardManager }
    val locale = LocalConfiguration.current.locales[0]
    var selectedLog by remember { mutableStateOf<GameLog?>(null) }

    if (selectedLog == null) {
        val sortedHistory = remember(history) { history.sortedByDescending { it.startTime } }
        ToolScaffold(title = "Game Logs", onBack = onBack) { padding ->
            if (sortedHistory.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("No games recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sortedHistory, key = { it.startTime }) { logEntry ->
                        val date = SimpleDateFormat("dd MMM yyyy, HH:mm", locale).format(Date(logEntry.startTime))
                        Card(
                            onClick = { selectedLog = logEntry },
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
                                    Text("${logEntry.events.count { it.eventType == "MOVE" }} moves total", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        val log = selectedLog!!
        ToolScaffold(
            title = "Log Details", 
            onBack = { selectedLog = null },
            actions = {
                IconButton(onClick = { 
                    val shareText = generateTxtLog(log, locale)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share Log"))
                }) { Icon(Icons.Default.Share, "Share") }
            }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text("Match Summary", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        log.initialPlayerStates.forEachIndexed { i, p ->
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Player ${i+1}", fontWeight = FontWeight.Bold)
                                Text(formatHms(p.timeRemainingMs, locale))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                Text("Detailed Moves", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))

                log.events.filter { it.eventType == "MOVE" }.forEach { event ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("P${event.playerIndex}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(event.moveNotation ?: "Move", modifier = Modifier.weight(1f).padding(horizontal = 16.dp))
                        Text(formatHms(event.timeRemainingMs ?: 0, locale), fontFamily = FontFamily.Monospace)
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        val format = moveExportFormatLabel(log.settings.gameType)
                        val clip = ClipData.newPlainText("Match $format", generateMoveExport(log))
                        clipboardManager.setPrimaryClip(clip)
                        Toast.makeText(context, "$format copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Copy ${moveExportFormatLabel(log.settings.gameType)}")
                }
            }
        }
    }
}

@Composable
fun CoinTossScreen(onBack: () -> Unit) {
    var isSpinning by remember { mutableStateOf(false) }
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }

    ToolScaffold(title = "Coin Toss", onBack = onBack) { padding ->
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
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = !isSpinning
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
                            text = if (isHeadsSide) "HEADS" else "TAILS",
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
    var isRolling by remember { mutableStateOf(false) }
    val rotX = remember { Animatable(0f) }
    val rotY = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }

    ToolScaffold(title = "Dice Roll", onBack = onBack) { padding ->
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
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = !isRolling
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

    ToolScaffold(title = "Short Straw", onBack = onBack) { padding ->
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
                        "Straws: $strawCount",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(32.dp))
                    Slider(
                        value = strawCount.toFloat(),
                        onValueChange = { strawCount = it.toInt() },
                        valueRange = 2f..10f,
                        steps = 8,
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
                            Text("PREPARE", fontWeight = FontWeight.Black, fontSize = 24.sp, color = MaterialTheme.colorScheme.onPrimary, letterSpacing = 2.sp)
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
                            indication = null
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
                                    .width(32.dp)
                                    .height(height)
                                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                    .background(color)
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null,
                                        enabled = !isRevealed && !revealedIndices.contains(shortStrawIndex)
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
    val values = listOf("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K")
    var currentCard by remember { mutableStateOf<Pair<String, String>?>(null) }
    var isAnimating by remember { mutableStateOf(false) }
    val rotation = remember { Animatable(0f) }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }

    ToolScaffold(title = "Random Card", onBack = onBack) { padding ->
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
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = !isAnimating
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
                        if (isBack || currentCard == null) {
                            Icon(Icons.Default.Style, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f))
                        } else {
                            val card = currentCard!!
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

    ToolScaffold(title = "Blindfold Trainer", onBack = onBack) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Score: $score", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("${timeLeft}s", color = if (timeLeft < 10) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                }

                if (!isPlaying) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (timeLeft == 0) {
                            Text("GAME OVER", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black)
                            Text("Final Score: $score", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.height(32.dp))
                        }
                        Surface(onClick = { score = 0; isPlaying = true; nextRound() }, modifier = Modifier.size(width = 240.dp, height = 72.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primary, shadowElevation = 8.dp) {
                            Box(contentAlignment = Alignment.Center) { Text("START TRIAL", fontWeight = FontWeight.Black, fontSize = 24.sp, color = MaterialTheme.colorScheme.onPrimary, letterSpacing = 2.sp) }
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(target.uppercase(Locale.US), style = MaterialTheme.typography.displayLarge.copy(fontSize = 120.sp), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(48.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Surface(onClick = { checkColor(true) }, modifier = Modifier.weight(1f).height(100.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.secondaryContainer, border = BorderStroke(4.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)), shadowElevation = 8.dp) {
                                Box(contentAlignment = Alignment.Center) { Text("WHITE", color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Black, fontSize = 22.sp) }
                            }
                            Surface(onClick = { checkColor(false) }, modifier = Modifier.weight(1f).height(100.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primary, border = BorderStroke(4.dp, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)), shadowElevation = 8.dp) {
                                Box(contentAlignment = Alignment.Center) { Text("DARK", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black, fontSize = 22.sp) }
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

    ToolScaffold(title = "Knight's Path", onBack = onBack) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Score: $score", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("${timeLeft}s", color = if (timeLeft < 10) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                }
                if (!isPlaying) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (timeLeft == 0) { Text("GAME OVER", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black); Text("Final Score: $score", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(32.dp)) }
                        Surface(onClick = { score = 0; isPlaying = true; nextRound() }, modifier = Modifier.size(width = 240.dp, height = 72.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primary, shadowElevation = 8.dp) { Box(contentAlignment = Alignment.Center) { Text("START TRIAL", fontWeight = FontWeight.Black, fontSize = 24.sp, color = MaterialTheme.colorScheme.onPrimary, letterSpacing = 2.sp) } }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("START", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary); Text(startPos.uppercase(Locale.US), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black) }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("TARGET", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error); Text(targetPos.uppercase(Locale.US), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black) }
                        }
                        Spacer(Modifier.height(32.dp)); Text("Min moves?", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(16.dp))
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
    LaunchedEffect(isRunning) { if (isRunning) { startTime = System.currentTimeMillis(); while (isRunning) { displayTime = System.currentTimeMillis() - startTime; delay(5) } } }
    ToolScaffold(title = "Stop Precision", onBack = onBack) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
            Text("Target: 5.000s", modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(modifier = Modifier.height(60.dp), contentAlignment = Alignment.Center) { if (!isRunning && displayTime > 0) { val diff = Math.abs(displayTime - 5000L); val color = when { diff <= 10 -> Color(0xFF2196F3); diff <= 20 -> Color(0xFF4CAF50); diff <= 50 -> Color(0xFFFFEB3B); diff <= 100 -> Color(0xFFFF9800); else -> MaterialTheme.colorScheme.error }; Text(text = "Offset: ${if (displayTime > 5000) "+" else "-"}${diff}ms", color = color, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall) } }
                Text(text = String.format(locale, "%.3fs", displayTime / 1000f), style = MaterialTheme.typography.displayLarge.copy(fontSize = 90.sp), fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(72.dp)); Surface(onClick = { if (isRunning) isRunning = false else { displayTime = 0; isRunning = true } }, modifier = Modifier.size(width = 240.dp, height = 72.dp), shape = RoundedCornerShape(20.dp), color = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, shadowElevation = 8.dp) { Box(contentAlignment = Alignment.Center) { Text(if (isRunning) "STOP" else "START", fontWeight = FontWeight.Black, fontSize = 24.sp, color = MaterialTheme.colorScheme.onPrimary, letterSpacing = 2.sp) } }
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
    ToolScaffold(title = "Name the Square", onBack = onBack) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Score: $score", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black); Text("${timeLeft}s", color = if (timeLeft < 10) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black) }
                if (!isPlaying) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (timeLeft == 0) { Text("GAME OVER", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black); Text("Final Score: $score", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(32.dp)) }
                        Surface(onClick = { score = 0; isPlaying = true; nextRound() }, modifier = Modifier.size(width = 240.dp, height = 72.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primary, shadowElevation = 8.dp) { Box(contentAlignment = Alignment.Center) { Text("START TRIAL", fontWeight = FontWeight.Black, fontSize = 24.sp, color = MaterialTheme.colorScheme.onPrimary, letterSpacing = 2.sp) } }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(target.uppercase(Locale.US), style = MaterialTheme.typography.displayLarge.copy(fontSize = 110.sp), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(32.dp))
                        Column(Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(6.dp).shadow(4.dp, RoundedCornerShape(12.dp))) { for (r in 8 downTo 1) { Row { for (f in 1..8) { val square = files[f-1] + ranks[r-1]; val isDark = (r + f) % 2 == 0; Box(modifier = Modifier.size(42.dp).background(if (isDark) Color(0xFF769656) else Color(0xFFEEEED2)).clickable { if (square == target) { score++; nextRound() } }) } } } }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Serializable
enum class ChessVariant(
    val label: String, 
    val isSymmetric: Boolean, 
    val kingBetweenRooks: Boolean, 
    val oppositeBishops: Boolean
) {
    CHESS960("Chess960", true, true, true),
    SHUFFLE("Shuffle", true, false, false),
    TRANSCENDENTAL("Transcendental", false, false, true),
    CHESS2880("Chess2880", true, false, true),
    DOUBLE_CHESS960("Double 960", false, true, true)
}

@Composable
fun Chess960Screen(onBack: () -> Unit) {
    var variant by remember { mutableStateOf(ChessVariant.CHESS960) }
    var whitePos by remember { mutableStateOf(generateChessPosition(ChessVariant.CHESS960)) }
    var blackPos by remember { mutableStateOf(generateChessPosition(ChessVariant.CHESS960)) }

    fun refresh() {
        whitePos = generateChessPosition(variant)
        blackPos = if (variant.isSymmetric) whitePos else generateChessPosition(variant)
    }

    val context = LocalContext.current
    val clipboardManager = remember { context.getSystemService(AndroidContext.CLIPBOARD_SERVICE) as ClipboardManager }

    ToolScaffold(title = "Variant Generator", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
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

            // Fixed Height Container for Description to prevent UI jumping
            Box(
                modifier = Modifier.fillMaxWidth().height(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when(variant) { 
                        ChessVariant.CHESS960 -> "Symmetric, Opposite Bishops, King between Rooks (Castling)."
                        ChessVariant.SHUFFLE -> "Symmetric, King and Bishops anywhere (Wild randomness)."
                        ChessVariant.TRANSCENDENTAL -> "Asymmetric, Opposite Bishops, King anywhere (High tactics)."
                        ChessVariant.CHESS2880 -> "Symmetric, Opposite Bishops, King anywhere (No castling)."
                        ChessVariant.DOUBLE_CHESS960 -> "Asymmetric, Opposite Bishops, King between Rooks (Pro tactics)."
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
            Spacer(Modifier.weight(1f))

            // Action Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { refresh() },
                    modifier = Modifier.weight(1.5f).height(64.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("GENERATE", fontWeight = FontWeight.Black, fontSize = 20.sp)
                }
                
                Surface(
                    onClick = { 
                        val clip = ClipData.newPlainText("Chess FEN", generateFen(whitePos, blackPos))
                        clipboardManager.setPrimaryClip(clip)
                        Toast.makeText(context, "FEN copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f).height(64.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("COPY FEN", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun ScoreboardScreen(viewModel: ChessTimerViewModel, onBack: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val session by viewModel.scoreboard.collectAsState(); var newResultText by remember { mutableStateOf("") }; val sortedGames = remember(session.games) { session.games.asReversed() }
    ToolScaffold(title = "Scoreboard", onBack = onBack, actions = { IconButton(onClick = { viewModel.resetScoreboard() }) { Icon(Icons.Default.Refresh, "Reset Scoreboard") } }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = session.player1Name, onValueChange = { viewModel.updateScoreboardNames(it, session.player2Name) }, label = { Text("Player 1") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), singleLine = true); OutlinedTextField(value = session.player2Name, onValueChange = { viewModel.updateScoreboardNames(session.player1Name, it) }, label = { Text("Player 2") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), singleLine = true) }
            Card(modifier = Modifier.weight(1f).fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                if (session.games.isEmpty()) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No games recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                else { LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(sortedGames, key = { it.timestamp }) { game -> Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp), border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)) { Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(game.result, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold); Text(SimpleDateFormat("HH:mm", locale).format(Date(game.timestamp)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } } }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(value = newResultText, onValueChange = { newResultText = it }, placeholder = { Text("Add result or note...") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), singleLine = true); Button(onClick = { if (newResultText.isNotBlank()) { viewModel.addScoreboardGame(newResultText); newResultText = "" } }, shape = RoundedCornerShape(12.dp), modifier = Modifier.height(56.dp)) { Icon(Icons.Default.Add, null) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { viewModel.addScoreboardGame("1 - 0") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("1 - 0") }; Button(onClick = { viewModel.addScoreboardGame("½ - ½") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("½ - ½") }; Button(onClick = { viewModel.addScoreboardGame("0 - 1") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("0 - 1") } }
        }
    }
}

@Composable
fun NotebookScreen(viewModel: ChessTimerViewModel, onBack: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedNoteId by remember { mutableStateOf<String?>(null) }
    var showTypeSelection by remember { mutableStateOf(false) }
    val selectedNote = remember(selectedNoteId, settings.notebookNotes) { settings.notebookNotes.find { it.id == selectedNoteId } }
    if (selectedNote == null) {
        val sortedNotes = remember(settings.notebookNotes) { settings.notebookNotes.sortedByDescending { it.timestamp } }
        ToolScaffold(title = "Notebook", onBack = onBack, actions = { IconButton(onClick = { showTypeSelection = true }) { Icon(Icons.Default.Add, "New Note") } }) { padding ->
            if (sortedNotes.isEmpty()) { Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("No notes yet. Tap + to create one.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            else { 
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding), 
                    contentPadding = PaddingValues(16.dp), 
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) { 
                    items(sortedNotes, key = { it.id }) { note -> 
                        Card(
                            onClick = { selectedNoteId = note.id }, 
                            modifier = Modifier.fillMaxWidth(), 
                            shape = RoundedCornerShape(12.dp), 
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) { 
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { 
                                Icon(
                                    imageVector = when(note.type) { 
                                        NotebookNoteType.TEXT -> Icons.Default.Description
                                        NotebookNoteType.DRAWING -> Icons.Default.Brush
                                        NotebookNoteType.VOICE -> Icons.Default.Mic
                                        NotebookNoteType.IMAGE -> Icons.Default.PhotoCamera
                                        NotebookNoteType.VIDEO -> Icons.Default.Videocam
                                        NotebookNoteType.BOARD -> Icons.Default.Grid4x4 
                                    }, 
                                    contentDescription = null, 
                                    tint = MaterialTheme.colorScheme.primary, 
                                    modifier = Modifier.padding(end = 16.dp)
                                )
                                Column(Modifier.weight(1f)) { 
                                    Text(note.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        SimpleDateFormat("dd MMM yyyy, HH:mm", locale).format(Date(note.timestamp)),
                                        style = MaterialTheme.typography.labelSmall, 
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    ) 
                                }
                                IconButton(onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        val sandboxRoots = listOf(context.filesDir.canonicalFile, context.cacheDir.canonicalFile)
                                        note.audioPath?.let { shredFile(File(it), sandboxRoots) }
                                        note.imagePath?.let { shredFile(File(it), sandboxRoots) }
                                        note.videoPath?.let { shredFile(File(it), sandboxRoots) }
                                        if (note.id.isNotBlank()) {
                                            val sweepDirs = listOf(context.filesDir, File(context.filesDir, "shares"))
                                            sweepDirs.forEach { dir ->
                                                dir.listFiles()?.filter { it.name.contains(note.id) }?.forEach { shredFile(it, sandboxRoots) }
                                            }
                                        }
                                        withContext(Dispatchers.Main) {
                                            viewModel.updateSettings(settings.copy(notebookNotes = settings.notebookNotes.filter { it.id != note.id }))
                                            Toast.makeText(context, "Note deleted", Toast.LENGTH_SHORT).show() 
                                        } 
                                    } 
                                }) { 
                                    Icon(Icons.Default.DeleteForever, "Shred", tint = MaterialTheme.colorScheme.error) 
                                } 
                            } 
                        } 
                    } 
                } 
            }
        }
        if (showTypeSelection) { Dialog(onDismissRequest = { showTypeSelection = false }) { Surface(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) { Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) { Text(text = "New Note", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold);                         FlowRow(
                            modifier = Modifier.fillMaxWidth(), 
                            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally), 
                            verticalArrangement = Arrangement.spacedBy(24.dp), 
                            maxItemsInEachRow = 3
                        ) { 
                            TypeButton("Text", Icons.Default.Description) { val newNote = NotebookNote(type = NotebookNoteType.TEXT); viewModel.updateSettings(settings.copy(notebookNotes = settings.notebookNotes + newNote)); selectedNoteId = newNote.id; showTypeSelection = false }; 
                            TypeButton("Draw", Icons.Default.Brush) { val newNote = NotebookNote(type = NotebookNoteType.DRAWING, title = "New Draw Note"); viewModel.updateSettings(settings.copy(notebookNotes = settings.notebookNotes + newNote)); selectedNoteId = newNote.id; showTypeSelection = false }; 
                            TypeButton("Audio", Icons.Default.Mic) { val newNote = NotebookNote(type = NotebookNoteType.VOICE, title = "New Audio Note"); viewModel.updateSettings(settings.copy(notebookNotes = settings.notebookNotes + newNote)); selectedNoteId = newNote.id; showTypeSelection = false }; 
                            TypeButton("Image", Icons.Default.PhotoCamera) { val newNote = NotebookNote(type = NotebookNoteType.IMAGE, title = "New Image Note"); viewModel.updateSettings(settings.copy(notebookNotes = settings.notebookNotes + newNote)); selectedNoteId = newNote.id; showTypeSelection = false }; 
                            TypeButton("Video", Icons.Default.Videocam) { val newNote = NotebookNote(type = NotebookNoteType.VIDEO, title = "New Video Note"); viewModel.updateSettings(settings.copy(notebookNotes = settings.notebookNotes + newNote)); selectedNoteId = newNote.id; showTypeSelection = false }; 
                            TypeButton("Board", Icons.Default.Grid4x4) { val newNote = NotebookNote(type = NotebookNoteType.BOARD, title = "New Board Note"); viewModel.updateSettings(settings.copy(notebookNotes = settings.notebookNotes + newNote)); selectedNoteId = newNote.id; showTypeSelection = false } 
                        }; TextButton(onClick = { showTypeSelection = false }, modifier = Modifier.align(Alignment.End)) { Text("Cancel") } } } } }
    } else {
        when (selectedNote.type) {
            NotebookNoteType.DRAWING -> DrawingNoteEditor(note = selectedNote, onUpdate = { updated -> val newList = settings.notebookNotes.map { if (it.id == updated.id) updated else it }; viewModel.updateSettings(settings.copy(notebookNotes = newList)) }, onBack = { selectedNoteId = null })
            NotebookNoteType.VOICE -> VoiceNoteEditor(note = selectedNote, onUpdate = { updated -> val newList = settings.notebookNotes.map { if (it.id == updated.id) updated else it }; viewModel.updateSettings(settings.copy(notebookNotes = newList)) }, onBack = { selectedNoteId = null })
            NotebookNoteType.IMAGE -> ImageNoteEditor(note = selectedNote, onUpdate = { updated -> val newList = settings.notebookNotes.map { if (it.id == updated.id) updated else it }; viewModel.updateSettings(settings.copy(notebookNotes = newList)) }, onBack = { selectedNoteId = null })
            NotebookNoteType.VIDEO -> VideoNoteEditor(note = selectedNote, onUpdate = { updated -> val newList = settings.notebookNotes.map { if (it.id == updated.id) updated else it }; viewModel.updateSettings(settings.copy(notebookNotes = newList)) }, onBack = { selectedNoteId = null })
            NotebookNoteType.BOARD -> BoardNoteEditor(note = selectedNote, onUpdate = { updated -> val newList = settings.notebookNotes.map { if (it.id == updated.id) updated else it }; viewModel.updateSettings(settings.copy(notebookNotes = newList)) }, onBack = { selectedNoteId = null })
            else -> {
                var title by remember(selectedNoteId) { mutableStateOf(selectedNote.title) }; var rawText by remember(selectedNoteId) { mutableStateOf(selectedNote.content) }; var contentValue by remember(selectedNoteId) { mutableStateOf(TextFieldValue(annotatedString = parseMarkdownToAnnotatedString(selectedNote.content), selection = TextRange(selectedNote.content.length))) }; var showColorPicker by remember { mutableStateOf(false) }
                LaunchedEffect(title, rawText) { val newList = settings.notebookNotes.map { if (it.id == selectedNoteId) it.copy(title = title, content = rawText) else it }; viewModel.updateSettings(settings.copy(notebookNotes = newList)) }
                fun applyFormat(prefix: String, suffix: String = prefix) { val selection = contentValue.selection; val text = rawText; val selectedText = text.substring(selection.start, selection.end); val newText = text.replaceRange(selection.start, selection.end, "$prefix$selectedText$suffix"); rawText = newText; val newCursorPos = selection.start + prefix.length + selectedText.length + suffix.length; contentValue = contentValue.copy(annotatedString = parseMarkdownToAnnotatedString(newText), selection = TextRange(newCursorPos)) }
                ToolScaffold(title = "Edit Note", onBack = { selectedNoteId = null }) { padding ->
                    Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true, textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp)) {
                            Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { applyFormat("**") }) { Icon(Icons.Default.FormatBold, "Bold") }; IconButton(onClick = { applyFormat("*") }) { Icon(Icons.Default.FormatItalic, "Italic") }; IconButton(onClick = { applyFormat("<u>", "</u>") }) { Icon(Icons.Default.FormatUnderlined, "Underline") }; VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp)); IconButton(onClick = { applyFormat("\n# ", "\n") }) { Icon(Icons.Default.FormatSize, "Large") }; IconButton(onClick = { applyFormat("\n## ", "\n") }) { Icon(Icons.Default.Title, "Normal") }; IconButton(onClick = { applyFormat("\n[ ] ", "") }) { Icon(Icons.Default.CheckBoxOutlineBlank, "Checkbox") }; VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp)); IconButton(onClick = { showColorPicker = true }) { Icon(Icons.Default.Palette, "Color", tint = MaterialTheme.colorScheme.primary) }
                            }
                        }
                        if (showColorPicker) { Dialog(onDismissRequest = { showColorPicker = false }) { Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(16.dp)) { Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("Select Text Color", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(16.dp)); val colors = listOf(0xFF4CAF50, 0xFF2196F3, 0xFFF44336, 0xFFFFEB3B, 0xFFFF9800, 0xFF000000, 0xFF9E9E9E, 0xFFFFFFFF); FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { colors.forEach { colorVal -> Surface(modifier = Modifier.size(40.dp).clickable { val hex = String.format("%08X", colorVal); applyFormat("<color:$hex>", "</color>"); showColorPicker = false }, shape = CircleShape, color = Color(colorVal), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {} } }; Spacer(Modifier.height(16.dp)); TextButton(onClick = { showColorPicker = false }) { Text("Cancel") } } } } }
                        OutlinedTextField(value = contentValue, onValueChange = { contentValue = it; rawText = it.text; contentValue = it.copy(annotatedString = parseMarkdownToAnnotatedString(it.text)) }, modifier = Modifier.fillMaxWidth().weight(1f), placeholder = { Text("Select text and use toolbar to format...") }, shape = RoundedCornerShape(12.dp))
                    }
                }
            }
        }
    }
}

fun parseMarkdownToAnnotatedString(text: String): AnnotatedString {
    return buildAnnotatedString {
        val boldRegex = Regex("\\*\\*(.*?)\\*\\*"); val italicRegex = Regex("\\*(.*?)\\*"); val underlineRegex = Regex("<u>(.*?)</u>"); val h1Regex = Regex("^# (.*)$", RegexOption.MULTILINE); val h2Regex = Regex("^## (.*)$", RegexOption.MULTILINE); val colorRegex = Regex("<color:([0-9A-F]{8})>(.*?)</color>")
        append(text)
        val markerRegex = Regex("[*#]|<u>|</u>|<color:[0-9A-F]{8}>|</color>|\\[[ x]\\]")
        markerRegex.findAll(text).forEach { addStyle(SpanStyle(color = Color.Transparent, fontSize = 0.1.sp), it.range.first, it.range.last + 1) }
        boldRegex.findAll(text).forEach { match -> match.groups[1]?.range?.let { range -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), range.first, range.last + 1) } }
        italicRegex.findAll(text).forEach { match -> match.groups[1]?.range?.let { range -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), range.first, range.last + 1) } }
        underlineRegex.findAll(text).forEach { match -> match.groups[1]?.range?.let { range -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), range.first, range.last + 1) } }
        h1Regex.findAll(text).forEach { match -> match.groups[1]?.range?.let { range -> addStyle(SpanStyle(fontWeight = FontWeight.Black, fontSize = 24.sp), range.first, range.last + 1) } }
        h2Regex.findAll(text).forEach { match -> match.groups[1]?.range?.let { range -> addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp), range.first, range.last + 1) } }
        colorRegex.findAll(text).forEach { match -> val colorHex = match.groupValues[1]; match.groups[2]?.range?.let { range -> try { val color = Color("#$colorHex".toColorInt()); addStyle(SpanStyle(color = color), range.first, range.last + 1) } catch (_: Exception) {} } }
    }
}

@Composable
fun VoiceNoteEditor(note: NotebookNote, onUpdate: (NotebookNote) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    var title by remember { mutableStateOf(note.title) }; var isRecording by remember { mutableStateOf(false) }; var isPlaying by remember { mutableStateOf(false) }; var recorder by remember { mutableStateOf<MediaRecorder?>(null) }; var player by remember { mutableStateOf<MediaPlayer?>(null) }; var recordingTime by remember { mutableIntStateOf(0) }; var playbackTime by remember { mutableIntStateOf(0) }
    // Saves the title continuously instead of only on the toolbar back arrow: MainActivity's system
    // back handler (gesture/button) calls navigator.goBack() directly and never runs ToolScaffold's
    // onBack lambda, so a title edit followed by a system back used to be silently lost (AUDIT.md §7.3).
    LaunchedEffect(title) { onUpdate(note.copy(title = title)) }
    val scope = rememberCoroutineScope()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted -> if (isGranted) Toast.makeText(context, "Permission granted. Tap Record again.", Toast.LENGTH_SHORT).show() else Toast.makeText(context, "Microphone permission is required.", Toast.LENGTH_SHORT).show() }
    LaunchedEffect(isRecording) { if (isRecording) { recordingTime = 0; while (isRecording && recordingTime < 60) { delay(1000); recordingTime++; if (recordingTime >= 60) isRecording = false } } }
    DisposableEffect(Unit) { onDispose { recorder?.release(); player?.release() } }
    fun startRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        try {
            val file = File(context.filesDir, "audio_${note.id}.mp4")
            @Suppress("DEPRECATION")
            val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }
            newRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = newRecorder
            isRecording = true
        } catch (_: Exception) {
            Toast.makeText(context, "Failed to start", Toast.LENGTH_SHORT).show()
        }
    }
    fun stopRecording() { try { recorder?.apply { stop(); release() }; recorder = null; isRecording = false; val file = File(context.filesDir, "audio_${note.id}.mp4"); onUpdate(note.copy(title = title, audioPath = file.absolutePath, audioDurationMs = recordingTime * 1000L)) } catch (_: Exception) { isRecording = false } }
    fun startPlayback() { if (note.audioPath == null) return; try { val newPlayer = MediaPlayer().apply { setDataSource(note.audioPath); prepare(); start(); setOnCompletionListener { isPlaying = false } }; player = newPlayer; isPlaying = true; scope.launch { while (isPlaying) { playbackTime = newPlayer.currentPosition / 1000; delay(100) }; playbackTime = 0 } } catch (_: Exception) { Toast.makeText(context, "Failed to play audio", Toast.LENGTH_SHORT).show() } }
    fun stopPlayback() { player?.stop(); player?.release(); player = null; isPlaying = false; playbackTime = 0 }

    ToolScaffold(title = "Audio Note", onBack = onBack) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true); Spacer(Modifier.weight(1f))
            Box(modifier = Modifier.size(160.dp).clip(CircleShape).background(if (isRecording) MaterialTheme.colorScheme.error.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Icon(imageVector = if (isRecording) Icons.Default.Mic else Icons.Default.MicNone, contentDescription = null, modifier = Modifier.size(64.dp).scale(if (isRecording) 1.2f else 1f), tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
            if (isRecording) Text(text = String.format(locale, "%02d:%02d / 01:00", recordingTime / 60, recordingTime % 60), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            else if (note.audioPath != null) Text(text = if (isPlaying) String.format(locale, "%02d:%02d", playbackTime / 60, playbackTime % 60) else String.format(locale, "%02d:%02d", (note.audioDurationMs / 1000) / 60, (note.audioDurationMs / 1000) % 60), style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) { if (!isPlaying) Button(onClick = { if (isRecording) stopRecording() else startRecording() }, colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(16.dp), modifier = Modifier.height(56.dp).weight(1f)) { Icon(if (isRecording) Icons.Default.Stop else Icons.Default.Mic, null); Spacer(Modifier.width(8.dp)); Text(if (isRecording) "Stop" else "Record") }; if (note.audioPath != null && !isRecording) Button(onClick = { if (isPlaying) stopPlayback() else startPlayback() }, shape = RoundedCornerShape(16.dp), modifier = Modifier.height(56.dp).weight(1f)) { Icon(if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(if (isPlaying) "Stop" else "Play") } }
            if (note.audioPath != null && !isRecording && !isPlaying) TextButton(onClick = { onUpdate(note.copy(audioPath = null, audioDurationMs = 0)) }) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(8.dp)); Text("Delete Recording") }; Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
fun ImageNoteEditor(note: NotebookNote, onUpdate: (NotebookNote) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current; var title by remember { mutableStateOf(note.title) }; val photoFile = remember { File(File(context.filesDir, "shares").apply { mkdirs() }, "image_${note.id}.jpg") }; val photoUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile) }
    // Saves the title continuously; see the matching comment in VoiceNoteEditor (AUDIT.md §7.3).
    LaunchedEffect(title) { onUpdate(note.copy(title = title)) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success -> if (success) onUpdate(note.copy(title = title, imagePath = photoFile.absolutePath)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted -> if (isGranted) cameraLauncher.launch(photoUri) else Toast.makeText(context, "Camera permission required", Toast.LENGTH_SHORT).show() }

    ToolScaffold(title = "Image Note", onBack = onBack) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
            Card(modifier = Modifier.weight(1f).fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) ) {
                if (note.imagePath != null) AsyncImage(model = note.imagePath, contentDescription = "Note image", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.AddAPhoto, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Text("No photo yet", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            }
            Button(onClick = { if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) cameraLauncher.launch(photoUri) else permissionLauncher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) { Icon(if (note.imagePath == null) Icons.Default.PhotoCamera else Icons.Default.Replay, null); Spacer(Modifier.width(8.dp)); Text(if (note.imagePath == null) "Take Photo" else "Retake Photo") }
        }
    }
}

@Composable
fun VideoNoteEditor(note: NotebookNote, onUpdate: (NotebookNote) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current; var title by remember { mutableStateOf(note.title) }; val videoFile = remember { File(File(context.filesDir, "shares").apply { mkdirs() }, "video_${note.id}.mp4") }; val videoUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", videoFile) }
    // Saves the title continuously; see the matching comment in VoiceNoteEditor (AUDIT.md §7.3).
    LaunchedEffect(title) { onUpdate(note.copy(title = title)) }
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { success -> if (success) onUpdate(note.copy(title = title, videoPath = videoFile.absolutePath)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted -> if (isGranted) videoLauncher.launch(videoUri) else Toast.makeText(context, "Camera permission required", Toast.LENGTH_SHORT).show() }

    ToolScaffold(title = "Video Note", onBack = onBack) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
            Card(modifier = Modifier.weight(1f).fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                if (note.videoPath != null) AndroidView(factory = { ctx -> VideoView(ctx).apply { setVideoPath(note.videoPath); val controller = MediaController(ctx); controller.setAnchorView(this); setMediaController(controller); start() } }, modifier = Modifier.fillMaxSize())
                else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.VideoCall, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Text("No video yet (Max 1 min)", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            }
            Button(onClick = { if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) videoLauncher.launch(videoUri) else permissionLauncher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) { Icon(if (note.videoPath == null) Icons.Default.Videocam else Icons.Default.Replay, null); Spacer(Modifier.width(8.dp)); Text(if (note.videoPath == null) "Record Video" else "Retake Video") }
        }
    }
}

@Composable
fun DrawingNoteEditor(note: NotebookNote, onUpdate: (NotebookNote) -> Unit, onBack: () -> Unit) {
    var title by remember { mutableStateOf(note.title) }; var currentPaths by remember { mutableStateOf(note.drawingPaths) }; var currentColor by remember { mutableLongStateOf(0xFF000000L) }; var currentWidth by remember { mutableFloatStateOf(8f) }; var isEraser by remember { mutableStateOf(false) }; var activePoints by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    LaunchedEffect(title, currentPaths) { onUpdate(note.copy(title = title, drawingPaths = currentPaths)) }
    ToolScaffold(title = "Draw Note", onBack = onBack, actions = { IconButton(onClick = { if (currentPaths.isNotEmpty()) currentPaths = currentPaths.dropLast(1) }) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }; IconButton(onClick = { currentPaths = emptyList() }) { Icon(Icons.Default.DeleteSweep, "Clear") } }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val colors = listOf(0xFF4CAF50, 0xFF2196F3, 0xFFF44336, 0xFFFFEB3B, 0xFFFF9800, 0xFF000000, 0xFF9E9E9E, 0xFFFFFFFF)
                        colors.forEach { colorVal -> Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(colorVal)).border(width = if (currentColor == colorVal && !isEraser) 2.dp else 0.5.dp, color = if (currentColor == colorVal && !isEraser) MaterialTheme.colorScheme.primary else Color.Gray, shape = CircleShape).clickable { currentColor = colorVal; isEraser = false }) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf(2f, 8f, 20f).forEach { size -> IconButton(onClick = { currentWidth = size }, modifier = Modifier.size(32.dp).background(if (currentWidth == size) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent, CircleShape)) { Box(Modifier.size((size / 2 + 4).dp).background(MaterialTheme.colorScheme.onSurface, CircleShape)) } }
                        VerticalDivider(Modifier.height(24.dp)); IconButton(onClick = { isEraser = !isEraser }, modifier = Modifier.background(if (isEraser) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent, CircleShape)) { Icon(imageVector = if (isEraser) Icons.Default.AutoFixHigh else Icons.Default.AutoFixOff, contentDescription = "Eraser") }
                    }
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)).pointerInput(isEraser, currentColor, currentWidth) { detectDragGestures(onDragStart = { offset -> activePoints = listOf(offset.x to offset.y) }, onDrag = { change, _ -> activePoints = activePoints + (change.position.x to change.position.y) }, onDragEnd = { if (activePoints.isNotEmpty()) { currentPaths = currentPaths + DrawingPath(points = activePoints, color = currentColor, strokeWidth = currentWidth, isEraser = isEraser); activePoints = emptyList() } }) }) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    currentPaths.forEach { pathData -> if (pathData.points.size > 1) { val path = Path().apply { val first = pathData.points.first(); moveTo(first.first, first.second); pathData.points.drop(1).forEach { lineTo(it.first, it.second) } }; drawPath(path = path, color = if (pathData.isEraser) Color.White else Color(pathData.color), style = Stroke(width = pathData.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)) } }
                    if (activePoints.size > 1) { val path = Path().apply { val first = activePoints.first(); moveTo(first.first, first.second); activePoints.drop(1).forEach { lineTo(it.first, it.second) } }; drawPath(path = path, color = if (isEraser) Color.White else Color(currentColor), style = Stroke(width = currentWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
                }
            }
        }
    }
}

@Composable
fun BoardNoteEditor(note: NotebookNote, onUpdate: (NotebookNote) -> Unit, onBack: () -> Unit) {
    var title by remember { mutableStateOf(note.title) }; var board by remember { mutableStateOf(note.boardPosition) }; var selectedPiece by remember { mutableStateOf<String?>(null) }; val piecesList = listOf("K", "Q", "R", "B", "N", "P", "k", "q", "r", "b", "n", "p")
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }
    
    LaunchedEffect(title, board) { onUpdate(note.copy(title = title, boardPosition = board)) }
    ToolScaffold(title = "Edit Board", onBack = onBack, actions = { IconButton(onClick = { board = List(64) { "" } }) { Icon(Icons.Default.DeleteSweep, "Clear Board") } }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(8.dp)) {
                    for (rank in 7 downTo 0) {
                        Row {
                            for (file in 0..7) {
                                val index = rank * 8 + file
                                val isDark = (rank + file) % 2 == 0
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(if (isDark) Color(0xFF769656) else Color(0xFFEEEED2))
                                        .clickable {
                                            val newBoard = board.toMutableList()
                                            newBoard[index] = selectedPiece ?: ""
                                            board = newBoard
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val piece = board[index]
                                    if (piece.isNotEmpty()) {
                                        AsyncImage(
                                            model = getPieceSvgPath(piece),
                                            contentDescription = piece,
                                            modifier = Modifier.size(32.dp),
                                            imageLoader = imageLoader
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Select Piece", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                        piecesList.subList(0, 6).forEach { p ->
                            IconButton(
                                onClick = { selectedPiece = p },
                                modifier = Modifier.background(if (selectedPiece == p) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent, CircleShape)
                            ) {
                                AsyncImage(model = getPieceSvgPath(p), contentDescription = p, modifier = Modifier.size(32.dp), imageLoader = imageLoader)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                        piecesList.subList(6, 12).forEach { p ->
                            IconButton(
                                onClick = { selectedPiece = p },
                                modifier = Modifier.background(if (selectedPiece == p) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent, CircleShape)
                            ) {
                                AsyncImage(model = getPieceSvgPath(p), contentDescription = p, modifier = Modifier.size(32.dp), imageLoader = imageLoader)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TypeButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.size(80.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(icon, null, modifier = Modifier.size(32.dp)); Spacer(Modifier.height(4.dp)); Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun FullChessBoardDisplay(whitePos: List<String>, blackPos: List<String>) {
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }
    Surface(
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
                            modifier = Modifier.size(40.dp).background(squareColor), 
                            contentAlignment = Alignment.Center
                        ) {
                            if (rawPiece.isNotEmpty()) {
                                AsyncImage(
                                    model = getPieceSvgPath(rawPiece),
                                    contentDescription = rawPiece,
                                    modifier = Modifier.size(32.dp),
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
        "p" -> "p"; "n" -> "n"; "b" -> "b"; "r" -> "r"; "q" -> "q"; "k" -> "k"; else -> "p"
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

fun generateFen(white: List<String>, black: List<String>): String {
    val b = black.joinToString("").lowercase(Locale.US)
    val w = white.joinToString("")
    return "$b/pppppppp/8/8/8/8/PPPPPPPP/$w w KQkq - 0 1"
}

fun formatHms(ms: Long, locale: Locale = Locale.US): String {
    val s = (ms / 1000) % 60; val m = (ms / 60000) % 60; val h = ms / 3600000
    return if (h > 0) String.format(locale, "%d:%02d:%02d", h, m, s) else String.format(locale, "%02d:%02d", m, s)
}

fun generateTxtLog(log: GameLog, locale: Locale = Locale.US): String {
    val sb = StringBuilder("MasterClock Log\nDate: ${Date(log.startTime)}\n\n")
    log.events.forEach { e -> sb.append("${if (e.playerIndex != null) "P${e.playerIndex}" else "SYSTEM"}: ${e.eventType} ${e.detail ?: ""} at ${formatHms(e.timeRemainingMs ?: 0, locale)}\n") }
    return sb.toString()
}

fun generatePgn(log: GameLog): String {
    val sb = StringBuilder("[Event \"Casual Match\"]\n[Date \"${SimpleDateFormat("yyyy.MM.dd", Locale.US).format(Date(log.startTime))}\"]\n\n")
    val moves = log.events.filter { it.eventType == "MOVE" }
    moves.forEachIndexed { i, m -> if (i % 2 == 0) sb.append("${(i/2)+1}. "); sb.append("${m.moveNotation ?: "???"} ") }
    return sb.toString()
}

fun generatePdn(log: GameLog): String {
    val sb = StringBuilder("[Event \"Draughts Match\"]\n[Date \"${SimpleDateFormat("yyyy.MM.dd", Locale.US).format(Date(log.startTime))}\"]\n\n")
    val moves = log.events.filter { it.eventType == "MOVE" }
    moves.forEachIndexed { i, m -> if (i % 2 == 0) sb.append("${(i/2)+1}. "); sb.append("${m.moveNotation ?: "???"} ") }
    return sb.toString()
}

fun generateKif(log: GameLog): String {
    val sb = StringBuilder("# Shogi Match Log\nStart: ${Date(log.startTime)}\n\n")
    log.events.filter { it.eventType == "MOVE" }.forEachIndexed { i, m -> sb.append("${i+1}: ${m.moveNotation ?: "???"}\n") }
    return sb.toString()
}

/** Move-list export format for a log's game, matching its [GameType] (was always PGN regardless -- AUDIT.md §7.3). */
fun moveExportFormatLabel(gameType: GameType): String = when (gameType) {
    GameType.CHESS -> "PGN"
    GameType.DRAUGHTS -> "PDN"
    GameType.SHOGI -> "KIF"
}

fun generateMoveExport(log: GameLog): String = when (log.settings.gameType) {
    GameType.CHESS -> generatePgn(log)
    GameType.DRAUGHTS -> generatePdn(log)
    GameType.SHOGI -> generateKif(log)
}

fun generateChessPosition(v: ChessVariant): List<String> {
    if (v.oppositeBishops) {
        val darkSquares = listOf(0, 2, 4, 6)
        val lightSquares = listOf(1, 3, 5, 7)
        val b1 = darkSquares.random()
        val b2 = lightSquares.random()
        val pos = MutableList(8) { "" }
        pos[b1] = "B"
        pos[b2] = "B"
        
        val remainingSquares = (0..7).filter { pos[it] == "" }.toMutableList()
        val otherPieces = mutableListOf("N", "N", "Q", "R", "R", "K").apply { shuffle() }
        
        if (v.kingBetweenRooks) {
            val rrkIndices = remainingSquares.shuffled().take(3).sorted()
            val finalOtherPieces = otherPieces.filter { it != "R" && it != "K" }.toMutableList().apply { shuffle() }
            
            var otherIdx = 0
            val bIndices = listOf(b1, b2)
            for (i in 0..7) {
                if (bIndices.contains(i)) continue
                if (i == rrkIndices[0] || i == rrkIndices[2]) {
                    pos[i] = "R"
                } else if (i == rrkIndices[1]) {
                    pos[i] = "K"
                } else {
                    pos[i] = finalOtherPieces[otherIdx++]
                }
            }
        } else {
            remainingSquares.forEachIndexed { index, square ->
                pos[square] = otherPieces[index]
            }
        }
        return pos
    } else {
        return mutableListOf("R", "N", "B", "Q", "K", "B", "N", "R").apply { shuffle() }
    }
}

/**
 * Overwrites [file] with random bytes before deleting it. Bounded to [sandboxRoots] (the app's own
 * filesDir/cacheDir): notebook note paths can originate from an imported settings file, so without
 * this check a crafted path could point the shredder at any file this app can write to. See
 * AUDIT.md §3 (HIGH finding).
 */
private fun shredFile(file: File, sandboxRoots: List<File>) {
    val canonical = try { file.canonicalFile } catch (_: Exception) { return }
    val isInsideSandbox = sandboxRoots.any { root ->
        canonical == root || canonical.path.startsWith(root.path + File.separator)
    }
    if (!isInsideSandbox || !canonical.exists()) return
    try {
        val length = canonical.length()
        if (length > 0) {
            RandomAccessFile(canonical, "rws").use { raf ->
                val buffer = ByteArray(4096)
                var written = 0L
                while (written < length) {
                    Random.nextBytes(buffer)
                    val toWrite = minOf(buffer.size.toLong(), length - written).toInt()
                    raf.write(buffer, 0, toWrite)
                    written += toWrite
                }
            }
        }
        canonical.delete()
    } catch (_: Exception) {
        canonical.delete()
    }
}
