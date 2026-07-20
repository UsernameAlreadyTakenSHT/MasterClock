package com.masterclock.app.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masterclock.app.logic.*
import com.masterclock.app.BuildConfig

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Column {
            content()
        }
    }
}

@Composable
fun ModeCard(title: String, selected: Boolean, modifier: Modifier = Modifier, compact: Boolean = false, onClick: () -> Unit) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary 
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), 
        label = "cardBg",
        animationSpec = tween()
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary 
        else MaterialTheme.colorScheme.onSurfaceVariant, 
        label = "cardContent",
        animationSpec = tween()
    )
    
    Surface(
        onClick = onClick, 
        modifier = modifier.height(if (compact) 40.dp else 48.dp).fillMaxWidth(), 
        color = bgColor, 
        contentColor = contentColor, 
        shape = RoundedCornerShape(12.dp)
    ) { 
        Box(contentAlignment = Alignment.Center) { 
            Text(
                text = title, 
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium, 
                fontWeight = if (selected) FontWeight.Black else FontWeight.Medium
            ) 
        } 
    }
}

@Composable
fun BehaviorSwitch(
    label: String, 
    checked: Boolean, 
    enabled: Boolean = true,
    showDivider: Boolean = false,
    topRounded: Boolean = false,
    bottomRounded: Boolean = false,
    onCheckedChange: (Boolean) -> Unit
) {
    val radius = 12.dp
    val shape = remember(topRounded, bottomRounded, radius) {
        when {
            topRounded && bottomRounded -> RoundedCornerShape(radius)
            topRounded -> RoundedCornerShape(topStart = radius, topEnd = radius)
            bottomRounded -> RoundedCornerShape(bottomStart = radius, bottomEnd = radius)
            else -> androidx.compose.ui.graphics.RectangleShape
        }
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .clickable(
                    enabled = enabled,
                    indication = androidx.compose.foundation.LocalIndication.current,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
            Switch(
                checked = checked, 
                onCheckedChange = onCheckedChange, 
                enabled = enabled, 
                modifier = Modifier.scale(0.85f),
                colors = SwitchDefaults.colors()
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun HMSInput(label: String, timeMs: Long, onTimeChange: (Long) -> Unit) {
    val totalSeconds = timeMs / 1000
    val h = (totalSeconds / 3600).toInt()
    val m = ((totalSeconds % 3600) / 60).toInt()
    val s = (totalSeconds % 60).toInt()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeField(h.toString(), "h", Modifier.weight(1f)) {
                val newH = it.toIntOrNull()?.coerceIn(0, 23) ?: 0
                onTimeChange((newH * 3600000L) + (m * 60000L) + (s * 1000L))
            }
            TimeField(m.toString(), "m", Modifier.weight(1f)) {
                val newM = it.toIntOrNull()?.coerceIn(0, 59) ?: 0
                onTimeChange((h * 3600000L) + (newM * 60000L) + (s * 1000L))
            }
            TimeField(s.toString(), "s", Modifier.weight(1f)) {
                val newS = it.toIntOrNull()?.coerceIn(0, 59) ?: 0
                onTimeChange((h * 3600000L) + (m * 60000L) + (newS * 1000L))
            }
        }
    }
}

@Composable
fun TimeField(value: String, suffix: String, modifier: Modifier = Modifier, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = if (value == "0") "" else value,
        onValueChange = { if (it.length <= 2) onValueChange(it) },
        modifier = modifier,
        minLines = 1,
        placeholder = { Text("00") },
        suffix = { Text(suffix, style = MaterialTheme.typography.bodySmall) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FidePeriodsPanel(p: PlayerSettings, onUpdate: (PlayerSettings) -> Unit) {
    SettingsSection("FIDE Periods Configuration") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val periods = p.fidePeriods

            periods.forEachIndexed { index, period ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Period ${index + 1}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (periods.size > 1) {
                                IconButton(
                                    onClick = {
                                        val newList = periods.toMutableList().apply { removeAt(index) }
                                        onUpdate(p.copy(fidePeriods = newList))
                                    }
                                ) { Icon(Icons.Default.Delete, "Remove") }
                            }
                        }

                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = !period.isFischer,
                                onClick = {
                                    val newList = periods.toMutableList().apply { this[index] = period.copy(isFischer = false) }
                                    onUpdate(p.copy(fidePeriods = newList))
                                },
                                shape = SegmentedButtonDefaults.itemShape(0, 2),
                                label = { Text("Sudden Death") }
                            )
                            SegmentedButton(
                                selected = period.isFischer,
                                onClick = {
                                    val newList = periods.toMutableList().apply { this[index] = period.copy(isFischer = true) }
                                    onUpdate(p.copy(fidePeriods = newList))
                                },
                                shape = SegmentedButtonDefaults.itemShape(1, 2),
                                label = { Text("Fischer") }
                            )
                        }

                        HMSInput("Duration", period.timeMs) { time ->
                            val newList = periods.toMutableList().apply { this[index] = period.copy(timeMs = time) }
                            onUpdate(p.copy(fidePeriods = newList))
                        }

                        if (period.isFischer) {
                            HMSInput("Increment", period.incrementMs) { inc ->
                                val newList = periods.toMutableList().apply { this[index] = period.copy(incrementMs = inc) }
                                onUpdate(p.copy(fidePeriods = newList))
                            }
                        }

                        if (index < periods.size - 1 || periods.size < 5) {
                            val isLast = index == periods.size - 1
                            OutlinedTextField(
                                value = if (period.movesToNext == 0) "" else period.movesToNext.toString(),
                                onValueChange = {
                                    val moves = it.toIntOrNull() ?: 0
                                    val newList = periods.toMutableList().apply { this[index] = period.copy(movesToNext = moves) }
                                    onUpdate(p.copy(fidePeriods = newList))
                                },
                                label = {
                                    Text(if (isLast) "Moves to unlock next (0 = Final)" else "Moves until period ${index + 2}")
                                },
                                placeholder = { Text(if (isLast) "Optional" else "40") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                supportingText = if (isLast && period.movesToNext == 0) {
                                    { Text("This will be the final period of the game.") }
                                } else null,
                                colors = OutlinedTextFieldDefaults.colors()
                            )
                        }
                    }
                }
            }

            if (periods.size < 5) {
                Button(
                    onClick = {
                        val newList = periods + FidePeriod()
                        onUpdate(p.copy(fidePeriods = newList))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Period")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhasesPanel(
    p: PlayerSettings,
    loopPhases: Boolean,
    pauseMs: Long,
    allowPhaseSkip: Boolean,
    onUpdateP: (PlayerSettings) -> Unit,
    onUpdateGlobal: (Boolean, Long, Boolean) -> Unit
) {
    SettingsSection("Phases Configuration") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BehaviorSwitch("Repeat Phases (Loop)", loopPhases) { onUpdateGlobal(it, pauseMs, allowPhaseSkip) }

            HMSInput("Pause between phases", pauseMs) { onUpdateGlobal(loopPhases, it, allowPhaseSkip) }

            BehaviorSwitch("Allow manual skip (tap to advance early)", allowPhaseSkip) { onUpdateGlobal(loopPhases, pauseMs, it) }

            val phases = p.phases
            phases.forEachIndexed { index, phase ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = phase.name,
                                onValueChange = { name ->
                                    val newList = phases.toMutableList().apply { this[index] = phase.copy(name = name) }
                                    onUpdateP(p.copy(phases = newList))
                                },
                                label = { Text("Phase ${index + 1} Name") },
                                placeholder = { Text("Preparation, Round X...") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors()
                            )
                            if (phases.size > 1) {
                                IconButton(onClick = {
                                    val newList = phases.toMutableList().apply { removeAt(index) }
                                    onUpdateP(p.copy(phases = newList))
                                }) { Icon(Icons.Default.Delete, "Remove") }
                            }
                        }

                        HMSInput("Duration", phase.timeMs) { time ->
                            val newList = phases.toMutableList().apply { this[index] = phase.copy(timeMs = time) }
                            onUpdateP(p.copy(phases = newList))
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BehaviorSwitch("Auto Advance", phase.autoAdvance) { auto ->
                                val newList = phases.toMutableList().apply { this[index] = phase.copy(autoAdvance = auto) }
                                onUpdateP(p.copy(phases = newList))
                            }
                        }

                        BehaviorSwitch("Flag on end (Defeat)", checked = phase.flagOnEnd) { flag ->
                            val newList = phases.toMutableList().apply { this[index] = phase.copy(flagOnEnd = flag) }
                            onUpdateP(p.copy(phases = newList))
                        }
                    }
                }
            }

            if (phases.size < 10) {
                Button(
                    onClick = {
                        val newList = phases + GamePhase("Phase ${phases.size + 1}")
                        onUpdateP(p.copy(phases = newList))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Phase")
                }
            }
        }
    }
}

@Composable
fun ToolCard(
    title: String, 
    icon: ImageVector, 
    modifier: Modifier = Modifier,
    showDivider: Boolean = false,
    topRounded: Boolean = false,
    bottomRounded: Boolean = false,
    onClick: () -> Unit
) {
    val radius = 12.dp
    val shape = remember(topRounded, bottomRounded, radius) {
        when {
            topRounded && bottomRounded -> RoundedCornerShape(radius)
            topRounded -> RoundedCornerShape(topStart = radius, topEnd = radius)
            bottomRounded -> RoundedCornerShape(bottomStart = radius, bottomEnd = radius)
            else -> androidx.compose.ui.graphics.RectangleShape
        }
    }

    Column {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun RandomModePanel(p: PlayerSettings, onUpdate: (PlayerSettings) -> Unit) {
    SettingsSection(if (p.mode == TimerMode.RANDOM) "Random Mode Configuration" else "Hidden Mode Configuration") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BehaviorSwitch("Rounded Time (minutes/seconds)", p.roundedTime) {
                onUpdate(p.copy(roundedTime = it))
            }

            if (p.mode == TimerMode.HIDDEN) {
                BehaviorSwitch("Show Percentage Alerts", p.showHiddenPercentages) {
                    onUpdate(p.copy(showHiddenPercentages = it))
                }
            }

            Text("Base Duration Range", style = MaterialTheme.typography.labelLarge)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HMSInput("Minimum Duration", p.randomMinTimeMs) { onUpdate(p.copy(randomMinTimeMs = it)) }
                HMSInput("Maximum Duration", p.randomMaxTimeMs) { onUpdate(p.copy(randomMaxTimeMs = it)) }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Text("Increment Range (Fischer)", style = MaterialTheme.typography.labelLarge)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HMSInput("Minimum Increment", p.randomMinIncMs) { onUpdate(p.copy(randomMinIncMs = it)) }
                HMSInput("Maximum Increment", p.randomMaxIncMs) { onUpdate(p.copy(randomMaxIncMs = it)) }
            }
        }
    }
}

@Composable
fun ExportDataDialog(
    onDismiss: () -> Unit,
    onExportSettings: (Boolean) -> Unit,
    onExportAll: () -> Unit,
    onShare: (Boolean, Boolean) -> Unit
) {
    var includeLogs by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FileUpload, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Export & Share")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Choose how you want to share or backup your data.", style = MaterialTheme.typography.bodyMedium)
                
                BehaviorSwitch(
                    label = "Include history & notes",
                    checked = includeLogs,
                    topRounded = true,
                    bottomRounded = true
                ) { includeLogs = it }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Actions", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    
                    ExportOptionItem(
                        title = "Share as QR Code",
                        description = "Instant scan for another device (Settings only).",
                        icon = Icons.Default.QrCode
                    ) { onShare(false, true) }

                    ExportOptionItem(
                        title = "Share File",
                        description = "Send a .json configuration file to a friend.",
                        icon = Icons.Default.Share
                    ) { onShare(includeLogs, false) }

                    ExportOptionItem(
                        title = "Export to Storage",
                        description = "Save the .json file to your device's storage.",
                        icon = Icons.Default.Save
                    ) { onExportSettings(includeLogs) }

                    ExportOptionItem(
                        title = "Export All (Full Backup)",
                        description = "Includes all settings, history, AND custom audio/images files in a .zip archive.",
                        icon = Icons.Default.PermMedia
                    ) { onExportAll() }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun ImportDataDialog(
    onDismiss: () -> Unit,
    onImportSettings: () -> Unit,
    onImportAll: () -> Unit,
    onScanQr: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FileDownload, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Import Data")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Restore your settings or import from another device.", style = MaterialTheme.typography.bodyMedium)
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportOptionItem(
                        title = "Scan QR Code",
                        description = "Quickly import settings by scanning another screen.",
                        icon = Icons.Default.QrCodeScanner
                    ) { onScanQr() }

                    ExportOptionItem(
                        title = "Import JSON File",
                        description = "Load a previously exported .json configuration file.",
                        icon = Icons.Default.Description
                    ) { onImportSettings() }

                    ExportOptionItem(
                        title = "Import Full Backup",
                        description = "Restore everything (settings + media) from a .zip archive.",
                        icon = Icons.Default.UploadFile
                    ) { onImportAll() }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun ExportOptionItem(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ColorRow(selectedColor: Long, onColorSelected: (Long) -> Unit) {
    val colors = remember { listOf(0xFF4CAF50, 0xFF2196F3, 0xFFF44336, 0xFFFFEB3B, 0xFFFF9800, 0xFF000000, 0xFF9E9E9E, 0xFFFFFFFF) }
    var showPicker by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(onClick = { showPicker = true }, modifier = Modifier.size(36.dp), shape = CircleShape, color = Color.Transparent, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            val gradient = remember { androidx.compose.ui.graphics.Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)) }
            Box(modifier = Modifier.fillMaxSize().background(brush = gradient), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ColorLens, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        VerticalDivider(modifier = Modifier.height(24.dp))
        colors.forEach { colorVal ->
            val isSelected = selectedColor == colorVal
            Surface(
                onClick = { if (!isSelected) onColorSelected(colorVal) }, 
                modifier = Modifier.size(32.dp), 
                shape = CircleShape, 
                color = Color(colorVal), 
                border = BorderStroke(width = if (isSelected) 2.dp else 1.dp, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {}
        }
    }

    if (showPicker) {
        ColorPickerDialog(
            initialColor = Color(selectedColor),
            onDismiss = { showPicker = false },
            onColorSelected = {
                onColorSelected(it.toArgb().toLong() and 0xFFFFFFFFL)
                showPicker = false
            }
        )
    }
}

@Composable
fun ColorPickerDialog(initialColor: Color, onDismiss: () -> Unit, onColorSelected: (Color) -> Unit) {
    var r by remember { mutableFloatStateOf(initialColor.red) }
    var g by remember { mutableFloatStateOf(initialColor.green) }
    var b by remember { mutableFloatStateOf(initialColor.blue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Custom Color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(8.dp)).background(Color(r, g, b)))
                Text("Red: ${(r * 255).toInt()}")
                Slider(value = r, onValueChange = { r = it })
                Text("Green: ${(g * 255).toInt()}")
                Slider(value = g, onValueChange = { g = it })
                Text("Blue: ${(b * 255).toInt()}")
                Slider(value = b, onValueChange = { b = it })
            }
        },
        confirmButton = { Button(onClick = { onColorSelected(Color(r, g, b)) }) { Text("Select") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun SimulatedScreen(settings: ChessClockSettings, onSettingsChanged: (ChessClockSettings) -> Unit) {
    val count = settings.numberOfPlayers
    val mapping = if (settings.playerMapping.size < 4) listOf(1, 2, 3, 4) else settings.playerMapping

    fun cycleSlot(idx: Int) {
        val currentP = mapping[idx]
        val nextP = (currentP % count) + 1
        val newMapping = mapping.toMutableList()
        val otherIdx = newMapping.indexOf(nextP)
        if (otherIdx != -1) newMapping[otherIdx] = currentP
        newMapping[idx] = nextP
        onSettingsChanged(settings.copy(playerMapping = newMapping))
    }

    Surface(
        modifier = Modifier.fillMaxWidth().height(200.dp).padding(horizontal = 40.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.fillMaxSize().padding(8.dp)) {
            if (count == 3) {
                if (settings.multiPlayerLayout == MultiPlayerLayout.BALANCED) {
                    SimSlot(Modifier.weight(1f), "P${mapping[0]}") { cycleSlot(0) }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SimSlot(Modifier.weight(1f), "P${mapping[1]}") { cycleSlot(1) }
                        SimSlot(Modifier.weight(1f), "P${mapping[2]}") { cycleSlot(2) }
                    }
                } else {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SimSlot(Modifier.weight(1f), "P${mapping[0]}") { cycleSlot(0) }
                        SimSlot(Modifier.weight(1f), "P${mapping[1]}") { cycleSlot(1) }
                    }
                    SimSlot(Modifier.weight(1f), "P${mapping[2]}") { cycleSlot(2) }
                }
            } else { // 4 Players
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SimSlot(Modifier.weight(1f), "P${mapping[0]}") { cycleSlot(0) }
                    SimSlot(Modifier.weight(1f), "P${mapping[1]}") { cycleSlot(1) }
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SimSlot(Modifier.weight(1f), "P${mapping[2]}") { cycleSlot(2) }
                    SimSlot(Modifier.weight(1f), "P${mapping[3]}") { cycleSlot(3) }
                }
            }
        }
    }
}

@Composable
fun SimSlot(modifier: Modifier, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontWeight = FontWeight.Black, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun CustomSoundItem(
    label: String,
    uri: String?,
    showDivider: Boolean = false,
    topRounded: Boolean = false,
    bottomRounded: Boolean = false,
    onClear: () -> Unit,
    onPick: () -> Unit
) {
    val shape = remember(topRounded, bottomRounded) {
        when {
            topRounded && bottomRounded -> RoundedCornerShape(12.dp)
            topRounded -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
            bottomRounded -> RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
            else -> androidx.compose.ui.graphics.RectangleShape
        }
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = if (uri != null) "Custom sound selected" else "Using default sound",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (uri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (uri != null) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Clear, "Clear custom sound", tint = MaterialTheme.colorScheme.error)
                }
            }
            IconButton(onClick = onPick) {
                Icon(if (uri != null) Icons.Default.Edit else Icons.Default.Add, null)
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun ModeSelectionPanel(p: PlayerSettings, isOneForAll: Boolean, onUpdateP: (PlayerSettings) -> Unit, onUpdateOneForAll: (Boolean) -> Unit, onOmniClick: () -> Unit) {
    val mainMode = when { 
        p.mode == TimerMode.SUDDEN_DEATH -> 0
        p.mode in listOf(TimerMode.FISHER, TimerMode.BRONSTEIN, TimerMode.US_DELAY) -> 1
        p.mode.name.startsWith("MOVE_TIMER") -> 2
        p.mode == TimerMode.HOURGLASS -> 3
        p.mode.name.startsWith("BYOYOMI") -> 4
        p.mode.name.startsWith("CHRONO") -> 5
        p.mode.name.startsWith("MOVE_COUNTS") -> 6
        p.mode == TimerMode.GONG -> 8
        p.mode == TimerMode.FIDE_PERIODS -> 9
        p.mode == TimerMode.PHASES -> 10
        p.mode == TimerMode.RANDOM -> 11
        p.mode == TimerMode.HIDDEN -> 12
        p.mode == TimerMode.FAST_MOVE -> 13
        else -> 0 
    }

    SettingsSection("Select Game Mode") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (FlavorConfig.isModeAllowed(TimerMode.SUDDEN_DEATH)) {
                    ModeCard("Sudden Death", mainMode == 0, Modifier.weight(1f)) { onUpdateP(p.copy(mode = TimerMode.SUDDEN_DEATH)) }
                }
                if (FlavorConfig.isModeAllowed(TimerMode.FISHER)) {
                    ModeCard("Bonus", mainMode == 1, Modifier.weight(1f)) { onUpdateP(p.copy(mode = TimerMode.FISHER)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (FlavorConfig.isModeAllowed(TimerMode.MOVE_TIMER_STANDARD)) {
                    ModeCard("Move Timer", mainMode == 2, Modifier.weight(1f)) { onUpdateP(p.copy(mode = TimerMode.MOVE_TIMER_STANDARD)) }
                }
                if (FlavorConfig.isModeAllowed(TimerMode.BYOYOMI_JAPANESE)) {
                    ModeCard("Byoyomi", mainMode == 4, Modifier.weight(1f)) { onUpdateP(p.copy(mode = TimerMode.BYOYOMI_JAPANESE)) }
                }
            }
            if (FlavorConfig.currentFlavor != AppFlavor.EXTRA_LIGHT) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModeCard("Hourglass", mainMode == 3, Modifier.weight(1f)) { onUpdateP(p.copy(mode = TimerMode.HOURGLASS)) }
                    ModeCard("Chronos", mainMode == 5, Modifier.weight(1f)) { onUpdateP(p.copy(mode = TimerMode.CHRONO_COUNTDOWN)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModeCard("Move Counts", mainMode == 6, Modifier.weight(1f)) { onUpdateP(p.copy(mode = TimerMode.MOVE_COUNTS_UP)) }
                    ModeCard("Gong", mainMode == 8, Modifier.weight(1f)) { onUpdateP(p.copy(mode = TimerMode.GONG)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModeCard("FIDE Periods", mainMode == 9, Modifier.weight(1f)) { onUpdateP(p.copy(mode = TimerMode.FIDE_PERIODS)) }
                    ModeCard("Phases", mainMode == 10, Modifier.weight(1f)) { onUpdateP(p.copy(mode = TimerMode.PHASES)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModeCard("Random", mainMode == 11, Modifier.weight(1f)) { onUpdateP(p.copy(mode = TimerMode.RANDOM)) }
                    ModeCard("Hidden", mainMode == 12, Modifier.weight(1f)) { onUpdateP(p.copy(mode = TimerMode.HIDDEN)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModeCard("Fast Move", mainMode == 13, Modifier.weight(1f)) { onUpdateP(p.copy(mode = TimerMode.FAST_MOVE)) }
                    if (FlavorConfig.hasMoreTab()) {
                        ModeCard("Omni-Timer", false, Modifier.weight(1f), compact = false, onClick = onOmniClick)
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    when (mainMode) {
        1 -> SettingsSection("Bonus Type") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeCard("Fisher", p.mode == TimerMode.FISHER, Modifier.weight(1f), compact = true) { onUpdateP(p.copy(mode = TimerMode.FISHER)) }
                ModeCard("Bronstein", p.mode == TimerMode.BRONSTEIN, Modifier.weight(1f), compact = true) { onUpdateP(p.copy(mode = TimerMode.BRONSTEIN)) }
                ModeCard("US Delay", p.mode == TimerMode.US_DELAY, Modifier.weight(1f), compact = true) { onUpdateP(p.copy(mode = TimerMode.US_DELAY)) }
            }
        }
        2 -> SettingsSection("Move Timer Type") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeCard("Standard", p.mode == TimerMode.MOVE_TIMER_STANDARD, Modifier.weight(1f), compact = true) { onUpdateP(p.copy(mode = TimerMode.MOVE_TIMER_STANDARD)) }
                    ModeCard("Save & Cap", p.mode == TimerMode.MOVE_TIMER_SAVE_CAP, Modifier.weight(1f), compact = true) { onUpdateP(p.copy(mode = TimerMode.MOVE_TIMER_SAVE_CAP)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeCard("Overtime", p.mode == TimerMode.MOVE_TIMER_OVERTIME, Modifier.weight(1f), compact = true) { onUpdateP(p.copy(mode = TimerMode.MOVE_TIMER_OVERTIME)) }
                    ModeCard("Global", p.mode == TimerMode.MOVE_TIMER_GLOBAL, Modifier.weight(1f), compact = true) { onUpdateP(p.copy(mode = TimerMode.MOVE_TIMER_GLOBAL)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeCard("Shared (Auto)", p.mode == TimerMode.MOVE_TIMER_SHARED, Modifier.weight(1f), compact = true) { onUpdateP(p.copy(mode = TimerMode.MOVE_TIMER_SHARED)) }
                    ModeCard("Global Shared", p.mode == TimerMode.MOVE_TIMER_GLOBAL_SHARED, Modifier.weight(1f), compact = true) { onUpdateP(p.copy(mode = TimerMode.MOVE_TIMER_GLOBAL_SHARED)) }
                }
            }
        }
        4 -> SettingsSection("Byoyomi Type") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeCard("Japanese", p.mode == TimerMode.BYOYOMI_JAPANESE, Modifier.weight(1f), compact = true) { onUpdateP(p.copy(mode = TimerMode.BYOYOMI_JAPANESE)) }
                    ModeCard("Canadian", p.mode == TimerMode.BYOYOMI_CANADIAN, Modifier.weight(1f), compact = true) { onUpdateP(p.copy(mode = TimerMode.BYOYOMI_CANADIAN)) }
                }
                ModeCard("Progressive", p.mode == TimerMode.BYOYOMI_PROGRESSIVE, compact = true) { onUpdateP(p.copy(mode = TimerMode.BYOYOMI_PROGRESSIVE)) }
            }
        }
        5 -> SettingsSection("Chrono Type") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeCard("Countdown", p.mode == TimerMode.CHRONO_COUNTDOWN, Modifier.weight(1f), compact = true) { onUpdateP(p.copy(mode = TimerMode.CHRONO_COUNTDOWN)) }
                    ModeCard("Countup", p.mode == TimerMode.CHRONO_COUNTUP, Modifier.weight(1f), compact = true) { onUpdateP(p.copy(mode = TimerMode.CHRONO_COUNTUP)) }
                }
                BehaviorSwitch("One for All (no clicks)", isOneForAll) { onUpdateOneForAll(it) }
            }
        }
        6 -> SettingsSection("Move Counts Type") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeCard("Count Up", p.mode == TimerMode.MOVE_COUNTS_UP, Modifier.weight(1f), compact = true) { onUpdateP(p.copy(mode = TimerMode.MOVE_COUNTS_UP)) }
                ModeCard("Count Down (Max)", p.mode == TimerMode.MOVE_COUNTS_DOWN, Modifier.weight(1f), compact = true) { onUpdateP(p.copy(mode = TimerMode.MOVE_COUNTS_DOWN)) }
            }
        }
        13 -> SettingsSection("Fast Move Type") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeCard("Accelerate", p.fastMoveMode == FastMoveType.ACCELERATE, Modifier.weight(1f), compact = true) { onUpdateP(p.copy(fastMoveMode = FastMoveType.ACCELERATE)) }
                ModeCard("Shrink", p.fastMoveMode == FastMoveType.SHRINK, Modifier.weight(1f), compact = true) { onUpdateP(p.copy(fastMoveMode = FastMoveType.SHRINK)) }
                ModeCard("Transfer", p.fastMoveMode == FastMoveType.TRANSFER, Modifier.weight(1f), compact = true) { onUpdateP(p.copy(fastMoveMode = FastMoveType.TRANSFER)) }
            }
        }
    }
}

@Composable
fun TimeParameterPanel(p: PlayerSettings, loopPhases: Boolean, pauseMs: Long, allowPhaseSkip: Boolean, onUpdate: (PlayerSettings) -> Unit, onUpdateGlobal: (Boolean, Long, Boolean) -> Unit) {
    val mode = p.mode
    SettingsSection("Time Parameters") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val mainModeIdx = when { 
                mode == TimerMode.SUDDEN_DEATH -> 0
                mode in listOf(TimerMode.FISHER, TimerMode.BRONSTEIN, TimerMode.US_DELAY) -> 1
                mode.name.startsWith("MOVE_TIMER") -> 2
                mode == TimerMode.HOURGLASS -> 3
                mode.name.startsWith("BYOYOMI") -> 4
                mode == TimerMode.CHRONO_COUNTDOWN -> 5
                mode == TimerMode.FAST_MOVE -> 13
                else -> -1 
            }
            val showInitial = (mainModeIdx == 0 || mainModeIdx == 1 || mainModeIdx == 3 || mainModeIdx == 4 || 
                            (mainModeIdx == 2 && mode in listOf(TimerMode.MOVE_TIMER_OVERTIME, TimerMode.MOVE_TIMER_GLOBAL, TimerMode.MOVE_TIMER_GLOBAL_SHARED)) ||
                            (mainModeIdx == 5 && mode == TimerMode.CHRONO_COUNTDOWN) ||
                            (mode == TimerMode.FAST_MOVE && p.fastMoveMode != FastMoveType.TRANSFER))

            if (showInitial) {
                val label = if (mode == TimerMode.FAST_MOVE && p.fastMoveMode == FastMoveType.SHRINK) "Starting Move Time" else "Initial Time"
                HMSInput(label, p.initialTimeMs) { onUpdate(p.copy(initialTimeMs = it)) }
            }
            if (mainModeIdx == 2 || mainModeIdx == 4 || (mode == TimerMode.FAST_MOVE && p.fastMoveMode == FastMoveType.TRANSFER)) { 
                val label = if (mainModeIdx == 2) "Move Time" 
                           else if (mode == TimerMode.FAST_MOVE) "Time Per Move"
                           else "Byoyomi Time"
                val currentTime = if (mainModeIdx == 2) p.moveTimeMs else if (mode == TimerMode.FAST_MOVE) p.moveTimeMs else p.byoyomiTimeMs
                HMSInput(label, currentTime) { onUpdate(if (mainModeIdx == 2 || mode == TimerMode.FAST_MOVE) p.copy(moveTimeMs = it) else p.copy(byoyomiTimeMs = it)) } 
            }
            if (mainModeIdx == 1) { HMSInput("Increment / Bonus", p.incrementMs) { onUpdate(p.copy(incrementMs = it)) } }
            if (mainModeIdx == 4) { NumericInput("Periods / Moves Goal", p.byoyomiPeriods) { onUpdate(p.copy(byoyomiPeriods = it)) } }
            if (mode == TimerMode.MOVE_TIMER_SAVE_CAP) { HMSInput("Time Cap", p.timeCapMs) { onUpdate(p.copy(timeCapMs = it)) } }
            if (mode == TimerMode.BYOYOMI_PROGRESSIVE) { NumericInput("Progression (moves)", p.byoyomiProgression) { onUpdate(p.copy(byoyomiProgression = it)) } }
            if (mode == TimerMode.MOVE_COUNTS_DOWN) { NumericInput("Max Moves", p.maxMoves) { onUpdate(p.copy(maxMoves = it)) } }
            if (mode == TimerMode.GONG) { GongPanel(p, onUpdate) }
            if (mode == TimerMode.FIDE_PERIODS) { FidePeriodsPanel(p, onUpdate) }
            if (mode == TimerMode.PHASES) { PhasesPanel(p = p, loopPhases = loopPhases, pauseMs = pauseMs, allowPhaseSkip = allowPhaseSkip, onUpdateP = onUpdate, onUpdateGlobal = onUpdateGlobal) }
            if (mode == TimerMode.RANDOM || mode == TimerMode.HIDDEN) { RandomModePanel(p, onUpdate) }
            if (mode == TimerMode.FAST_MOVE) { FastMovePanel(p, onUpdate) }
        }
    }
}

@Composable
fun FastMovePanel(p: PlayerSettings, onUpdate: (PlayerSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (p.fastMoveMode == FastMoveType.ACCELERATE) {
            HMSInput("Grace Period (Standard speed)", p.fastMoveGracePeriodMs) { onUpdate(p.copy(fastMoveGracePeriodMs = it)) }
            
            Column {
                val rateDisplay = String.format(java.util.Locale.US, "%.1f", p.fastMoveAccelRate)
                Text("Fast Acceleration Rate (+$rateDisplay sec/sec)", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = p.fastMoveAccelRate,
                    onValueChange = { onUpdate(p.copy(fastMoveAccelRate = it)) },
                    valueRange = 0.1f..20.0f
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            HMSInput("Full Fast Period starts at", p.fastMoveFastPeriodMs) { onUpdate(p.copy(fastMoveFastPeriodMs = it)) }

            Column {
                val fullRateDisplay = String.format(java.util.Locale.US, "%.1f", p.fastMoveFullAccelRate)
                Text("Full Fast Accel Rate (+$fullRateDisplay sec/sec)", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = p.fastMoveFullAccelRate,
                    onValueChange = { onUpdate(p.copy(fastMoveFullAccelRate = it)) },
                    valueRange = 0.1f..20.0f
                )
            }
        } else if (p.fastMoveMode == FastMoveType.SHRINK) {
            HMSInput("Decrement per move", p.fastMoveShrinkDecrementMs) { onUpdate(p.copy(fastMoveShrinkDecrementMs = it)) }
            HMSInput("Minimum Time Floor", p.fastMoveShrinkFloorMs) { onUpdate(p.copy(fastMoveShrinkFloorMs = it)) }
        } else if (p.fastMoveMode == FastMoveType.TRANSFER) {
            BehaviorSwitch(
                label = "Cumulative Transfer",
                checked = p.fastMoveTransferCumulative,
                topRounded = true,
                bottomRounded = true
            ) { onUpdate(p.copy(fastMoveTransferCumulative = it)) }
            Text(
                "If off, only the time spent on the very last move is given to the opponent.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
fun NumericInput(label: String, value: Int, onValueChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = if (value == 0) "" else value.toString(),
            onValueChange = { onValueChange(it.toIntOrNull() ?: 0) },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.bodyMedium,
            placeholder = { Text("0") },
            colors = OutlinedTextFieldDefaults.colors()
        )
    }
}

@Composable
fun GongPanel(p: PlayerSettings, onUpdate: (PlayerSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BehaviorSwitch("Simultaneous Play", p.gongSimultaneous) { onUpdate(p.copy(gongSimultaneous = it)) }
        HMSInput("Reflection Time", p.gongReflectionMs) { onUpdate(p.copy(gongReflectionMs = it)) }
        HMSInput("Time to Move", p.gongMoveMs) { onUpdate(p.copy(gongMoveMs = it)) }
    }
}

@Composable
fun ChangelogCreditsDialog(onDismiss: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Changelog", "Credits")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("MasterClock ${BuildConfig.VERSION_NAME}")
                Text(
                    AppInfo.BUILD_DATE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column {
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, label ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(label) }
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (selectedTab == 0) {
                        AppInfo.CHANGELOG.forEach { entry ->
                            Column {
                                Text(
                                    "${entry.version} — ${entry.date}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                entry.notes.forEach { note ->
                                    Text("• $note", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    } else {
                        AppInfo.CREDITS.forEach { credit ->
                            Column {
                                Text(credit.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Text(credit.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
