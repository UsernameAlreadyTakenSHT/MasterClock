package com.masterclock.app.ui.screens

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.masterclock.app.R

@Composable
fun QRShareScreen(
    data: String,
    onBack: () -> Unit
) {
    // Drawn off the main thread: a quarter of a million pixels, set one by one where the screen
    // was being opened. Null while it is drawn, which is not the same as a code that cannot be.
    val qr by produceState<QrImage?>(initialValue = null, data) {
        value = withContext(Dispatchers.Default) { QrImage(generateQRCode(data)) }
    }

    ToolScaffold(
        title = stringResource(R.string.qr_share_title),
        onBack = onBack
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                stringResource(R.string.qr_share_hint),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(Modifier.height(32.dp))

            Surface(
                modifier = Modifier
                    .size(320.dp)
                    .padding(4.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 2.dp
            ) {
                val qrBitmap = qr?.bitmap
                if (qr == null) Box(Modifier.fillMaxSize())
                else qrBitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = stringResource(R.string.qr_code),
                        modifier = Modifier.fillMaxSize().padding(12.dp)
                    )
                } ?: Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    // Shown once generateQRCode() has answered, so a null result here isn't
                    // "still loading" -- it's a permanent failure (most commonly: the payload is too
                    // large to fit in a QR code's capacity). Previously this showed an infinite,
                    // misleading spinner.
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.qr_too_large),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            
            Text(
                stringResource(R.string.qr_share_includes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A code once drawn: its image, or none when the content does not fit one. */
private class QrImage(val bitmap: Bitmap?)

private fun generateQRCode(content: String): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        // Without a character set zxing encodes byte mode as ISO-8859-1, and anything it cannot
        // map becomes '?': a phase named in Cyrillic, CJK or with an emoji arrived as "????" --
        // inside JSON that stayed valid, so it imported silently corrupted. UTF-8 is written with
        // an ECI marker, which tells the reader which character set to decode with.
        val hints = mapOf(EncodeHintType.CHARACTER_SET to Charsets.UTF_8.name())
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height) { i ->
            if (bitMatrix.get(i % width, i / width)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        createBitmap(width, height, Bitmap.Config.RGB_565).apply { setPixels(pixels, 0, width, 0, 0, width, height) }
    } catch (_: Exception) {
        null
    }
}
