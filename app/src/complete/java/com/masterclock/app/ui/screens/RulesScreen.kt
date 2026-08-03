package com.masterclock.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.masterclock.core.R as CoreR
import java.io.File
import java.io.FileOutputStream
import com.masterclock.app.R

@Composable
fun RulesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    ToolScaffold(
        title = stringResource(R.string.rules_title),
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
            Text(stringResource(R.string.rules_intro), style = MaterialTheme.typography.bodyMedium)

            // --- CHESS ---
            RulesGroup(stringResource(R.string.rules_group_chess)) {
                RuleButton("FIDE Laws", Modifier.fillMaxWidth()) {
                    openBundledPdf(context, CoreR.raw.rules_chess, "chess.pdf")
                }
            }

            // --- DRAUGHTS ---
            RulesGroup(stringResource(R.string.rules_group_draughts)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuleButton("FMJD Annexes", Modifier.weight(1f)) {
                        openBundledPdf(context, CoreR.raw.rules_draughts_fmjd, "draughts_fmjd.pdf")
                    }
                    RuleButton("IDF Rules", Modifier.weight(1f)) {
                        openBundledPdf(context, CoreR.raw.rules_draughts_idf, "draughts_idf.pdf")
                    }
                }
            }

            // --- SHOGI ---
            RulesGroup(stringResource(R.string.rules_group_shogi)) {
                RuleButton("FESA Rules", Modifier.fillMaxWidth()) {
                    openBundledPdf(context, CoreR.raw.rules_shogi, "shogi.pdf")
                }
            }

            // --- MORE GAMES ---
            RulesGroup(stringResource(R.string.rules_group_more_games)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RuleButton("Morris", Modifier.weight(1f)) {
                            openBundledPdf(context, CoreR.raw.rules_morris, "morris.pdf")
                        }
                        RuleButton("Tafl", Modifier.weight(1f)) {
                            openBundledPdf(context, CoreR.raw.rules_tafl, "tafl.pdf")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RuleButton("Quoridor", Modifier.weight(1f)) {
                            openBundledPdf(context, CoreR.raw.rules_quoridor, "quoridor.pdf")
                        }
                        RuleButton("Abalone", Modifier.weight(1f)) {
                            openBundledPdf(context, CoreR.raw.rules_abalone, "abalone.pdf")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RuleButton("Hex", Modifier.weight(1f)) {
                            openBundledPdf(context, CoreR.raw.rules_hex, "hex.pdf")
                        }
                        RuleButton("Santorini", Modifier.weight(1f)) {
                            openBundledPdf(context, CoreR.raw.rules_santorini, "santorini.pdf")
                        }
                    }
                }
            }

            // --- RESOURCES ---
            RulesGroup(stringResource(R.string.rules_group_more_resources)) {
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

/**
 * Opens a rules document bundled in `core`'s `res/raw`.
 *
 * The raw resource is copied into `cacheDir/pdfs/` on first use and handed to a viewer through the
 * FileProvider both apps already declare (authority `${applicationId}.fileprovider`, with `pdfs/`
 * whitelisted in `res/xml/file_paths.xml`) -- a raw resource has no path a viewer could open on its
 * own. Serving from the APK is what makes this screen's "offline use" promise true.
 */
private fun openBundledPdf(context: Context, rawResId: Int, fileName: String) {
    try {
        val file = File(File(context.cacheDir, "pdfs").apply { mkdirs() }, fileName)
        if (!file.exists() || file.length() == 0L) {
            context.resources.openRawResource(rawResId).use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
    } catch (e: ActivityNotFoundException) {
        // Failing silently here is exactly the bug this screen used to have.
        Log.w("RulesScreen", "No PDF viewer available for $fileName", e)
        Toast.makeText(context, context.getString(R.string.toast_no_pdf_viewer), Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Log.w("RulesScreen", "Failed to open bundled document: $fileName", e)
        Toast.makeText(context, context.getString(R.string.toast_pdf_open_failed), Toast.LENGTH_SHORT).show()
    }
}
