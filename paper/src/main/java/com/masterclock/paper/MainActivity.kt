package com.masterclock.paper

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.masterclock.app.logic.*
import com.masterclock.paper.ui.navigation.Navigator
import com.masterclock.paper.ui.navigation.Route
import com.masterclock.paper.ui.navigation.rememberNavigationState
import com.masterclock.paper.ui.navigation.toEntries
import com.masterclock.paper.ui.screens.*
import com.masterclock.paper.ui.theme.MasterClockTheme
import android.widget.Toast
import kotlinx.coroutines.*

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.*
import kotlin.time.Duration.Companion.milliseconds
import com.masterclock.paper.R

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Sounds play on the media stream (USAGE_MEDIA) by default; without this the hardware
        // volume rocker adjusts the ring stream while in the app, so a muted media stream
        // silences every sound with no way to notice or fix it from inside the app.
        volumeControlStream = AudioManager.STREAM_MUSIC

        // Initialize Core Flavor
        FlavorConfig.currentFlavor = AppFlavor.E_INK

        enableEdgeToEdge()
        setContent {
            val timerViewModel: ChessTimerViewModel = viewModel()
            val omniViewModel: OmniTimerViewModel = viewModel()
            val settings by timerViewModel.settings.collectAsState()
            val gameHistory by timerViewModel.gameHistory.collectAsState()
            val json = remember { Json { ignoreUnknownKeys = true } }
            val context = LocalContext.current
            // Hoisted: these fire from launcher callbacks, outside composable scope.
            val backupOkText = stringResource(R.string.toast_backup_ok)
            val importOkText = stringResource(R.string.toast_import_ok)
            val importFailedText = stringResource(R.string.toast_import_failed)
            val exportOkText = stringResource(R.string.toast_export_ok)
            val exportFailedText = stringResource(R.string.toast_export_failed)
            var shouldIncludeLogs by remember { mutableStateOf(false) }

            val scope = rememberCoroutineScope()

            // Force Screen Awake, Brightness & Fullscreen logic
            LaunchedEffect(settings.forceScreenOn, settings.forceFullBrightness, settings.fullscreenMode) {
                val window = (context as? Activity)?.window
                window?.let { w ->
                    if (settings.forceScreenOn) {
                        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }

                    val layoutParams = w.attributes
                    layoutParams.screenBrightness = if (settings.forceFullBrightness) 1.0f else -1.0f
                    w.attributes = layoutParams

                    val controller = WindowCompat.getInsetsController(w, w.decorView)
                    if (settings.fullscreenMode) {
                        controller.hide(WindowInsetsCompat.Type.systemBars())
                        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    } else {
                        controller.show(WindowInsetsCompat.Type.systemBars())
                    }
                }
            }

            val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                uri?.let {
                    // Assembled here because it reads composition state; encoded and written off the
                    // main thread, which is where all of it used to happen. See the same treatment
                    // in app/src/main/java/com/masterclock/app/MainActivity.kt.
                    val pkg = if (shouldIncludeLogs) {
                        SharePackage(
                            settings = settings.copy(
                                notebookNotes = settings.notebookNotes.filter { it.type == NotebookNoteType.TEXT }
                            ),
                            logs = gameHistory,
                            scoreboard = timerViewModel.scoreboard.value
                        )
                    } else {
                        SharePackage(
                            settings = settings.copy(notebookNotes = emptyList()),
                            logs = null,
                            scoreboard = null
                        )
                    }
                    scope.launch(Dispatchers.IO) {
                        val written = runCatching {
                            val bytes = json.encodeToString(pkg).toByteArray()
                            context.contentResolver.openOutputStream(it)?.use { stream ->
                                stream.write(bytes)
                            } ?: error("No output stream for $it")
                        }
                        written.exceptionOrNull()?.let { e ->
                            Log.w("MainActivity", "Failed to export settings file", e)
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                if (written.isSuccess) exportOkText else exportFailedText,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            }

            val mediaExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
                uri?.let {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val zipFile = ZipBackupManager.createFullBackup(context, settings, gameHistory, timerViewModel.scoreboard.value)
                            try {
                                context.contentResolver.openOutputStream(it)?.use { output ->
                                    zipFile.inputStream().use { input ->
                                        input.copyTo(output)
                                    }
                                }
                            } finally {
                                // A failed export used to leave the cleartext archive in the cache.
                                zipFile.delete()
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, backupOkText, Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            // The message used to go to the user: a Java exception string, always in
                            // English and rarely about anything they can act on.
                            Log.w("MainActivity", "Failed to export backup archive", e)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, exportFailedText, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }

            val mediaImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                uri?.let {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val tempFile = File(context.cacheDir, "import_backup.zip")
                            try {
                                context.contentResolver.openInputStream(it)?.use { input ->
                                    // Bounded: copyTo wrote the picked file to the cache in full
                                    // before any of the extractor's limits applied.
                                    tempFile.outputStream().use { output ->
                                        copyImportArchive(input, output)
                                    }
                                }
                                val pkg = ZipBackupManager.extractBackup(tempFile)
                                withContext(Dispatchers.Main) {
                                    timerViewModel.updateSettings(
                                        newSettings = pkg.settings,
                                        logsToImport = pkg.logs,
                                        scoreboardToImport = pkg.scoreboard,
                                        isImport = true
                                    )
                                    Toast.makeText(context, importOkText, Toast.LENGTH_SHORT).show()
                                }
                            } finally {
                                // In a finally, not after the import: a refused or malformed
                                // archive used to be left behind in the cache.
                                tempFile.delete()
                            }
                        } catch (e: Exception) {
                            Log.w("MainActivity", "Failed to import backup archive", e)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, importFailedText, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }

            val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                uri?.let {
                    // Every outcome here used to be a log line and nothing else: the picker closed
                    // and the screen was unchanged whether the file had been applied, refused or
                    // never opened. Reading and parsing also happened on the main thread, over a
                    // stream that can belong to a cloud provider. Both were fixed in the phone app
                    // and not ported here.
                    scope.launch(Dispatchers.IO) {
                        val parsed = runCatching {
                            val content = context.contentResolver.openInputStream(it)?.use { stream ->
                                // Bounded: readText() would pull a file of any size straight into the heap.
                                readImportText(stream)
                            } ?: error("No input stream for $it")
                            try {
                                json.decodeFromString<SharePackage>(content)
                            } catch (_: Exception) {
                                // Not the current SharePackage format; fall back to the legacy
                                // bare-settings format. A failure there is the real one.
                                SharePackage(settings = json.decodeFromString<ChessClockSettings>(content))
                            }
                        }
                        parsed.exceptionOrNull()?.let { e ->
                            Log.w("MainActivity", "Failed to import settings file", e)
                        }
                        withContext(Dispatchers.Main) {
                            parsed.getOrNull()?.let { pkg ->
                                timerViewModel.updateSettings(
                                    newSettings = pkg.settings,
                                    logsToImport = pkg.logs,
                                    scoreboardToImport = pkg.scoreboard,
                                    isImport = true
                                )
                            }
                            Toast.makeText(
                                context,
                                if (parsed.isSuccess) importOkText else importFailedText,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            }
            
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_PAUSE) {
                        if (settings.pauseOnBackground) {
                            timerViewModel.pause()
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            val isDarkTheme = when (settings.themeMode) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.AUTO -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            val navigationState = rememberNavigationState(
                startRoute = Route.Timer,
                topLevelRoutes = setOf(Route.Timer),
            )
            val navigator = remember { Navigator(navigationState) }

            BackHandler {
                navigator.goBack()
            }

            MasterClockTheme(
                darkTheme = isDarkTheme,
                eInkDarkMode = settings.eInkDarkMode
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val entryProvider = entryProvider<NavKey> {
                        entry<Route.Timer> { _ ->
                            TimerScreen(
                                viewModel = timerViewModel,
                                onSettingsClick = { 
                                    navigator.navigate(Route.Settings("MODES")) 
                                },
                                onPresetsClick = { 
                                    // navigator.navigate(Route.Presets) 
                                }
                            )
                        }
                        entry<Route.Settings> { route ->
                            SettingsScreen(
                                viewModel = timerViewModel,
                                currentSettings = settings,
                                category = SettingsCategory.fromRoute(route.category),
                                onSettingsChanged = { timerViewModel.updateSettings(it) },
                                onClearLogs = { timerViewModel.clearAllLogs() },
                                onResetSettings = { timerViewModel.resetAllSettings() },
                                onExportSettings = { includeLogs -> 
                                    shouldIncludeLogs = includeLogs
                                    exportLauncher.launch("master_clock_settings.json") 
                                },
                                onExportMedia = { mediaExportLauncher.launch("master_clock_full_backup.zip") },
                                onImportSettings = { importLauncher.launch(arrayOf("application/json")) },
                                onImportMedia = { mediaImportLauncher.launch(arrayOf("application/zip")) },
                                onShareSettings = { includeLogs, _ ->
                                    // paper has no QRShareScreen/QRReceiveScreen (see the note below on
                                    // missing screens), so useQr is ignored -- always falls back to a
                                    // file share, matching the app module's non-QR path.
                                    val pkg = if (includeLogs) {
                                        SharePackage(
                                            settings = settings.copy(
                                                notebookNotes = settings.notebookNotes.filter { it.type == NotebookNoteType.TEXT }
                                            ),
                                            logs = gameHistory,
                                            scoreboard = timerViewModel.scoreboard.value
                                        )
                                    } else {
                                        SharePackage(
                                            settings = settings.copy(notebookNotes = emptyList()),
                                            logs = null,
                                            scoreboard = null
                                        )
                                    }
                                    val shareJson = json.encodeToString(pkg)
                                    shareData(context, shareJson)
                                },
                                onToolClick = { routeToNavigate -> /* navigator.navigate(routeToNavigate) */ },
                                onBackClick = { 
                                    navigator.goBack() 
                                },
                                onCategoryChanged = { newCat ->
                                    navigator.navigate(Route.Settings(newCat.name), replace = true)
                                }
                            )
                        }
                        // Missing screens in :paper module are commented out for now to allow building
                    }

                    NavDisplay(
                        entries = navigationState.toEntries(entryProvider),
                        onBack = { navigator.goBack() }
                    )
                }
            }
        }
    }

    private fun shareData(context: Context, jsonData: String) {
        try {
            val cacheDir = File(context.cacheDir, "shares")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            // A directory per share, so a target still holding an earlier URI cannot read what the
            // next share writes. See app/src/main/java/com/masterclock/app/MainActivity.kt.
            cacheDir.listFiles()?.filter { it.isDirectory && it.name.startsWith("config-") }
                ?.forEach { it.deleteRecursively() }
            val shareDir = File(cacheDir, "config-${java.util.UUID.randomUUID()}").apply { mkdirs() }
            val shareFile = File(shareDir, "master_clock_config.json")
            FileOutputStream(shareFile).use { it.write(jsonData.toByteArray()) }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shareFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_settings)))
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to share settings", e)
        }
    }
}
