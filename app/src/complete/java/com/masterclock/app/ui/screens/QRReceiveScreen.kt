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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.masterclock.app.R
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
        title = stringResource(R.string.qr_scan_title),
        onBack = onBack
    ) { pad ->
        if (cameraPermissionState.status.isGranted) {
            // Guards against onResult() firing more than once: setAnalyzer() keeps delivering
            // frames (and can decode successfully on several in a row) until the camera is actually
            // unbound in response to the first onResult(), which doesn't happen instantly. It lives
            // out here rather than in the factory so that leaving the screen can set it too.
            val hasScanned = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
            // The camera is bound to the Activity's lifecycle, not this screen's: the navigation
            // stack installs a saveable-state decorator and no lifecycle decorator, so
            // LocalLifecycleOwner here is the Activity. Nothing therefore ended the session on the
            // way out -- backing out without scanning left the camera open and analysing for the
            // life of the Activity, with the indicator lit and one executor leaked per visit, and a
            // code entering frame afterwards still called onResult from whatever screen the user
            // had reached by then.
            val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
            val cameraProvider = remember { java.util.concurrent.atomic.AtomicReference<ProcessCameraProvider?>(null) }
            // The analyzer is built once by the factory below, so it would otherwise hold the first
            // composition's callback for as long as it runs.
            val currentOnResult by rememberUpdatedState(onResult)

            DisposableEffect(Unit) {
                onDispose {
                    hasScanned.set(true)
                    runCatching { cameraProvider.getAndSet(null)?.unbindAll() }
                    analysisExecutor.shutdown()
                }
            }

            Box(Modifier.fillMaxSize().padding(pad)) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                        
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val provider = cameraProviderFuture.get()
                            // The provider arrives asynchronously, so the screen can have been left
                            // -- or a code already read -- by the time it does. Binding then would
                            // open a camera that nothing is left to close.
                            if (hasScanned.get()) {
                                return@addListener
                            }
                            cameraProvider.set(provider)
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
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
                                        currentOnResult(result.text)
                                    }
                                } catch (_: Exception) {
                                    // No code found
                                } finally {
                                    imageProxy.close()
                                }
                            }

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            try {
                                provider.unbindAll()
                                provider.bindToLifecycle(
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
                        stringResource(R.string.qr_align),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.qr_camera_permission))
            }
        }
    }
}
