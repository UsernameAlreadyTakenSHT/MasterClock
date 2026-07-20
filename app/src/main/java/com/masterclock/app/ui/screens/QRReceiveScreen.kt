package com.masterclock.app.ui.screens

import android.Manifest
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun QRReceiveScreen(
    onResult: (String) -> Unit,
    onBack: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        cameraPermissionState.launchPermissionRequest()
    }

    ToolScaffold(
        title = "Scan QR Code",
        onBack = onBack
    ) { pad ->
        if (cameraPermissionState.status.isGranted) {
            Box(Modifier.fillMaxSize().padding(pad)) {
                AndroidView(
                    factory = { ctx ->
                        // Guards against onResult() firing more than once: setAnalyzer() keeps
                        // delivering frames (and can decode successfully on several in a row) until
                        // the caller actually unbinds the camera in response to the first onResult(),
                        // which doesn't happen instantly (AUDIT.md §7.3).
                        val hasScanned = java.util.concurrent.atomic.AtomicBoolean(false)
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                        
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                val buffer = imageProxy.planes[0].buffer
                                val data = ByteArray(buffer.remaining())
                                buffer.get(data)
                                
                                val source = PlanarYUVLuminanceSource(
                                    data, imageProxy.width, imageProxy.height,
                                    0, 0, imageProxy.width, imageProxy.height, false
                                )
                                val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                                
                                try {
                                    val reader = MultiFormatReader()
                                    val result = reader.decode(binaryBitmap)
                                    if (hasScanned.compareAndSet(false, true)) {
                                        onResult(result.text)
                                    }
                                } catch (_: Exception) {
                                    // No code found
                                } finally {
                                    imageProxy.close()
                                }
                            }

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner, cameraSelector, preview, imageAnalysis
                                )
                            } catch (e: Exception) {
                                Log.e("QRReceiveScreen", "Failed to bind camera preview", e)
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Overlay instructions
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                ) {
                    Text(
                        "Align code within the camera view",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("Camera permission is required to scan codes.")
            }
        }
    }
}
