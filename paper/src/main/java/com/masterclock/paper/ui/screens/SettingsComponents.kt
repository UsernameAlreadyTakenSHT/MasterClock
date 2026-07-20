package com.masterclock.paper.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masterclock.app.logic.*
import com.masterclock.paper.ui.components.*
import com.masterclock.paper.BuildConfig

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title, 
            style = MaterialTheme.typography.titleSmall, 
            color = MaterialTheme.colorScheme.onBackground, 
            fontWeight = FontWeight.Medium, 
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp, top = 16.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

@Composable
fun ModeCard(title: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (selected) {
        ButtonMMD(
            onClick = onClick,
            modifier = modifier.height(44.dp)
        ) {
            Text(
                text = title, 
                style = MaterialTheme.typography.labelLarge
            )
        }
    } else {
        OutlinedButtonMMD(
            onClick = onClick,
            modifier = modifier.height(44.dp)
        ) {
            Text(
                text = title, 
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
fun BehaviorSwitch(
    label: String, 
    checked: Boolean, 
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )
        SwitchMMD(
            checked = checked, 
            onCheckedChange = onCheckedChange, 
            enabled = enabled
        )
    }
}

@Composable
fun MSInput(label: String, timeMs: Long, onTimeChange: (Long) -> Unit) {
    val totalSeconds = timeMs / 1000
    val m = (totalSeconds / 60).toInt()
    val s = (totalSeconds % 60).toInt()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = label, 
            style = MaterialTheme.typography.labelSmall, 
            color = MaterialTheme.colorScheme.onBackground, // AVOID GREY: 100% opacity
            fontWeight = FontWeight.Medium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeField(m.toString(), "m", Modifier.weight(1f)) {
                val newM = it.toIntOrNull()?.coerceIn(0, 60) ?: 0
                onTimeChange((newM * 60000L) + (s * 1000L))
            }
            TimeField(s.toString(), "s", Modifier.weight(1f)) {
                val newS = it.toIntOrNull()?.coerceIn(0, 59) ?: 0
                onTimeChange((m * 60000L) + (newS * 1000L))
            }
        }
    }
}

@Composable
fun TimeField(value: String, suffix: String, modifier: Modifier = Modifier, onValueChange: (String) -> Unit) {
    MMDTextField(
        value = if (value == "0") "" else value,
        onValueChange = { if (it.length <= 2) onValueChange(it) },
        modifier = modifier,
        placeholder = "", // NO PLACEHOLDER (Clean/Minimal)
        suffix = suffix,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.bodyLarge
    )
}

@Composable
fun ModeSelectionPanel(p: PlayerSettings, onUpdateP: (PlayerSettings) -> Unit) {
    val mainMode = when (p.mode) { 
        TimerMode.SUDDEN_DEATH -> 0
        TimerMode.FISHER -> 1
        TimerMode.MOVE_TIMER_STANDARD -> 2
        else -> 0 
    }

    // Mode selection pills in a single row
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ModeCard("Timer", mainMode == 0, Modifier.weight(1f)) { onUpdateP(p.copy(mode = TimerMode.SUDDEN_DEATH)) }
        ModeCard("Fischer", mainMode == 1, Modifier.weight(1f)) { onUpdateP(p.copy(mode = TimerMode.FISHER)) }
        ModeCard("Move", mainMode == 2, Modifier.weight(1f)) { onUpdateP(p.copy(mode = TimerMode.MOVE_TIMER_STANDARD)) }
    }
    
    // Inputs follow directly based on logic
    Spacer(Modifier.height(8.dp))
    
    if (mainMode != 2) { // NO Initial Time for Move Timer
        MSInput("Initial time", p.initialTimeMs) { onUpdateP(p.copy(initialTimeMs = it)) }
    }
    
    if (mainMode == 1) { 
        MSInput("Increment", p.incrementMs) { onUpdateP(p.copy(incrementMs = it)) } 
    }
    if (mainMode == 2) { 
        MSInput("Move time", p.moveTimeMs) { onUpdateP(p.copy(moveTimeMs = it)) }
    }
}

@Composable
fun ColorRow(selectedColor: Long, onColorSelected: (Long) -> Unit) {
    val colors = remember { listOf(0xFF000000, 0xFFFFFFFF) }
    
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        colors.forEach { colorVal ->
            val isSelected = selectedColor == colorVal
            Surface(
                onClick = { if (!isSelected) onColorSelected(colorVal) }, 
                modifier = Modifier.size(48.dp), 
                shape = CircleShape, 
                color = Color(colorVal), 
                border = BorderStroke(width = if (isSelected) 4.dp else 2.dp, color = MaterialTheme.colorScheme.onBackground),
                interactionSource = remember { MutableInteractionSource() }
            ) {}
        }
    }
}

@Composable
fun ChangelogCreditsDialog(onDismiss: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Changelog", "Credits")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    MMDDefaults.BorderWidth,
                    MaterialTheme.colorScheme.onSurface,
                    RoundedCornerShape(MMDDefaults.CornerRadius)
                ),
            shape = RoundedCornerShape(MMDDefaults.CornerRadius),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "MasterClock ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        AppInfo.BUILD_DATE,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
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
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                entry.notes.forEach { note ->
                                    Text("• $note", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    } else {
                        AppInfo.CREDITS.forEach { credit ->
                            Column {
                                Text(credit.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                                Text(credit.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }

                ButtonMMD(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Close", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
