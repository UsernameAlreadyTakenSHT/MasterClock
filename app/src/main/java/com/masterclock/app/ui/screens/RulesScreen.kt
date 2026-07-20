package com.masterclock.app.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream

@Composable
fun RulesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    ToolScaffold(
        title = "Some rules",
        onBack = onBack
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text("Reference documents for offline use.", style = MaterialTheme.typography.bodyMedium)

            // --- CHESS ---
            RulesGroup("Chess (official doc)") {
                RuleButton("FIDE Laws", Modifier.fillMaxWidth()) {
                    openPdf(context, "https://rcc.fide.com/wp-content/uploads/2022/12/20230101Laws-of-Chess.pdf")
                }
            }

            // --- DRAUGHTS ---
            RulesGroup("Draughts (official doc)") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuleButton("FMJD Annexes", Modifier.weight(1f)) {
                        openPdf(context, "https://www.fmjd.org/downloads/FMJD_Annexes_2024_8-sig.pdf")
                    }
                    RuleButton("IDF Rules", Modifier.weight(1f)) {
                        openPdf(context, "https://idf64.org/wp-content/uploads/2016/09/Official-Rules-of-the-game.pdf")
                    }
                }
            }

            // --- SHOGI ---
            RulesGroup("Shogi (official doc)") {
                RuleButton("FESA Rules", Modifier.fillMaxWidth()) {
                    openPdf(context, "https://fesashogi.eu/wp-content/uploads/2025/05/FESA-Rules-ver-2024-09-23.pdf")
                }
            }

            // --- MORE GAMES ---
            RulesGroup("More Games (unofficial docs)") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RuleButton("Morris", Modifier.weight(1f)) { openPdf(context, "") }
                        RuleButton("Tablut", Modifier.weight(1f)) { openPdf(context, "") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RuleButton("Quoridor", Modifier.weight(1f)) { openPdf(context, "") }
                        RuleButton("Abalone", Modifier.weight(1f)) { openPdf(context, "") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RuleButton("Hex", Modifier.weight(1f)) { openPdf(context, "") }
                        RuleButton("Santorini", Modifier.weight(1f)) { openPdf(context, "") }
                    }
                }
            }

            // --- RESOURCES ---
            RulesGroup("More Resources") {
                RuleButton(
                    text = "Wiki Strategy Games", 
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = Icons.AutoMirrored.Filled.OpenInNew
                ) {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, "https://en.wikipedia.org/wiki/List_of_abstract_strategy_games".toUri())
                    context.startActivity(intent)
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun RulesGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        content()
    }
}

@Composable
private fun RuleButton(
    text: String, 
    modifier: Modifier = Modifier, 
    trailingIcon: ImageVector? = null,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text, fontWeight = FontWeight.SemiBold)
            if (trailingIcon != null) {
                Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun openPdf(context: android.content.Context, url: String) {
    try {
        if (url.isBlank()) return
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, url.toUri())
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.w("RulesScreen", "Failed to open document: $url", e)
    }
}
