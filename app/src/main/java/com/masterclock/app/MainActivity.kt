package com.masterclock.app

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.masterclock.app.logic.*
import com.masterclock.app.ui.navigation.Navigator
import com.masterclock.app.ui.navigation.Route
import com.masterclock.app.ui.navigation.rememberNavigationState
import com.masterclock.app.ui.navigation.toEntries
import com.masterclock.app.ui.screens.*
import com.masterclock.app.ui.theme.MasterClockTheme
import android.widget.Toast
import kotlinx.coroutines.*

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.*
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Sounds play on the media stream (USAGE_MEDIA) by default; without this the hardware
        // volume rocker adjusts the ring stream while in the app, so a muted media stream
        // silences every sound with no way to notice or fix it from inside the app.
        volumeControlStream = AudioManager.STREAM_MUSIC

        // Initialize Core Flavor
        FlavorConfig.currentFlavor = when {
            BuildConfig.FLAVOR.equals("complete", ignoreCase = true) -> AppFlavor.COMPLETE
            BuildConfig.FLAVOR.equals("standard", ignoreCase = true) -> AppFlavor.STANDARD
            BuildConfig.FLAVOR.equals("lite", ignoreCase = true) -> AppFlavor.LITE
            BuildConfig.FLAVOR.equals("mini", ignoreCase = true) -> AppFlavor.MINI
            else -> AppFlavor.COMPLETE
        }

        // The activity has no launchMode, so it defaults to "standard" and a shortcut launch always
        // arrives through onCreate with a fresh intent -- no onNewIntent needed.
        val launchShortcutId = intent?.getStringExtra(PresetShortcuts.EXTRA_SHORTCUT_ID)

        enableEdgeToEdge()
        setContent {
            val timerViewModel: ChessTimerViewModel = viewModel()
            val omniViewModel: OmniTimerViewModel = viewModel()
            val settings by timerViewModel.settings.collectAsState()
            val gameHistory by timerViewModel.gameHistory.collectAsState()
            val customPresets by timerViewModel.customPresets.collectAsState()
            val json = remember { Json { ignoreUnknownKeys = true } }
            val context = LocalContext.current
            // Hoisted: these fire from document-picker callbacks, outside composable scope.
            val backupOkText = stringResource(R.string.toast_backup_ok)
            val importOkText = stringResource(R.string.toast_import_ok)
            val importFailedText = stringResource(R.string.toast_import_failed)
            val exportOkText = stringResource(R.string.toast_export_ok)
            val exportFailedText = stringResource(R.string.toast_export_failed)
            var shouldIncludeLogs by remember { mutableStateOf(false) }

            val scope = rememberCoroutineScope()

            // A shortcut only applies once per launch. The ViewModel holds the id until its own
            // async load finishes, so this does not race the DataStore read.
            LaunchedEffect(Unit) {
                launchShortcutId?.let {
                    timerViewModel.applyShortcut(it)
                    PresetShortcuts.reportUsed(context, it)
                }
            }

            // Republish whenever the sources change: saving, renaming or deleting a preset moves
            // customPresets, and finishing a game moves gameHistory.
            LaunchedEffect(customPresets, gameHistory) {
                PresetShortcuts.publish(context, buildShortcutTargets(gameHistory, customPresets))
            }

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
                    // The package is assembled here, on the main thread, because it reads composition
                    // state; encoding and writing it happen off it. Those were on the main thread
                    // too, and what they encode is the settings plus the entire game history, over a
                    // stream that can belong to a cloud provider -- the backup export beside this one
                    // has always known better.
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
                                // In a finally, like the import's temp file: the archive holds the
                                // settings, every notebook note and the whole game history in the
                                // clear, and an export that failed at openOutputStream used to leave
                                // it in the cache with nothing to remove it.
                                zipFile.delete()
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, backupOkText, Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            // The message used to be shown to the user; it is a Java exception
                            // string, always in English and rarely about anything they can act on.
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
                    // Every outcome here used to be silent. An unreadable file, a format neither
                    // parser recognised, a provider that handed back no stream at all: each was a
                    // log line and nothing more, so the picker closed and the screen sat unchanged,
                    // with no way to tell a refused file from one that imported settings identical
                    // to the current ones. Success said nothing either, which the backup import
                    // three lambdas up has always done.
                    scope.launch(Dispatchers.IO) {
                        // Reading and parsing happen here rather than on the main thread. The size is
                        // bounded, but the stream can belong to a cloud provider, which makes the
                        // read as slow as the network behind it.
                        val parsed = runCatching {
                            val content = context.contentResolver.openInputStream(it)?.use { stream ->
                                // Bounded: readText() would pull a file of any size straight into the heap.
                                readImportText(stream)
                            } ?: error("No input stream for $it")
                            try {
                                json.decodeFromString<SharePackage>(content)
                            } catch (_: Exception) {
                                // Not the current SharePackage format; fall back to the legacy
                                // bare-settings format. A failure here is the real one, and it
                                // reaches runCatching rather than being swallowed.
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
                                    navigator.navigate(Route.Presets) 
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
                                onShareSettings = { includeLogs, useQr ->
                                    if (useQr) {
                                        val pkg = SharePackage(settings = settings.copy(notebookNotes = emptyList()), logs = null, scoreboard = null)
                                        val shareJson = json.encodeToString(pkg)
                                        navigator.navigate(Route.QRShare(shareJson))
                                    } else {
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
                                    }
                                },
                                onToolClick = { routeToNavigate -> navigator.navigate(routeToNavigate) },
                                onBackClick = { 
                                    navigator.goBack() 
                                },
                                onCategoryChanged = { newCat ->
                                    navigator.navigate(Route.Settings(newCat.name), replace = true)
                                }
                            )
                        }
                        entry<Route.CoinToss> { _ -> CoinTossScreen(onBack = { navigator.goBack() }) }
                        entry<Route.DiceRoll> { _ -> DiceRollScreen(onBack = { navigator.goBack() }) }
                        entry<Route.ShortStraw> { _ -> ShortStrawScreen(onBack = { navigator.goBack() }) }
                        entry<Route.RandomCard> { _ -> RandomCardScreen(onBack = { navigator.goBack() }) }
                        entry<Route.Scoreboard> { _ -> ScoreboardScreen(viewModel = timerViewModel, onBack = { navigator.goBack() }) }
                        entry<Route.Notebook> { _ -> NotebookScreen(viewModel = timerViewModel, onBack = { navigator.goBack() }) }
                        entry<Route.StopPrecision> { _ -> StopPrecisionScreen(onBack = { navigator.goBack() }) }
                        entry<Route.BlindfoldTrainer> { _ -> BlindfoldTrainerScreen(onBack = { navigator.goBack() }) }
                        entry<Route.KnightPath> { _ -> KnightPathScreen(onBack = { navigator.goBack() }) }
                        entry<Route.NameSquare> { _ -> NameSquareScreen(onBack = { navigator.goBack() }) }
                        entry<Route.Chess960> { _ -> Chess960Screen(onBack = { navigator.goBack() }) }
                        entry<Route.Statistics> { _ ->
                            StatisticsScreen(
                                history = timerViewModel.gameHistory.collectAsState().value,
                                timePadding = settings.effectiveTimePadding(),
                                onBack = { navigator.goBack() }
                            )
                        }
                        entry<Route.GameLogs> { _ ->
                            GameLogsScreen(
                                history = timerViewModel.gameHistory.collectAsState().value,
                                timePadding = settings.effectiveTimePadding(),
                                onBack = { navigator.goBack() }
                            )
                        }
                        entry<Route.BluetoothBoard> { _ ->
                            BluetoothBoardScreen(
                                viewModel = timerViewModel,
                                onBack = { navigator.goBack() }
                            )
                        }
                        entry<Route.Rules> { _ -> RulesScreen(onBack = { navigator.goBack() }) }
                        entry<Route.ModeGuide> { _ -> ModeGuideScreen(onBack = { navigator.goBack() }) }
                        entry<Route.QRShare> { route -> QRShareScreen(data = route.payload, onBack = { navigator.goBack() }) }
                        entry<Route.QRReceive> { _ ->
                            QRReceiveScreen(
                                onResult = { scanResult ->
                                    try {
                                        val pkg = json.decodeFromString<SharePackage>(scanResult)
                                        timerViewModel.updateSettings(
                                            newSettings = pkg.settings,
                                            logsToImport = pkg.logs,
                                            scoreboardToImport = pkg.scoreboard,
                                            isImport = true
                                        )
                                        navigator.goBack()
                                    } catch (e: Exception) {
                                        Log.w("MainActivity", "Failed to import scanned QR share package", e)
                                    }
                                },
                                onBack = { navigator.goBack() }
                            )
                        }
                        entry<Route.Presets> { _ ->
                            PresetsScreen(
                                history = timerViewModel.gameHistory.collectAsState().value,
                                customPresets = timerViewModel.customPresets.collectAsState().value,
                                // Only the time control is taken from a preset. The built-in ones
                                // are each a fresh ChessClockSettings() with a field or two
                                // overridden, so applying them wholesale used to reset the user's
                                // colours, sounds and other preferences on every tap.
                                onPresetSelected = { set, states ->
                                    timerViewModel.updateSettings(
                                        applyPresetTimeControl(timerViewModel.settings.value, set),
                                        states
                                    )
                                    navigator.goBack()
                                },
                                onSavePreset = { timerViewModel.saveCurrentAsPreset(it) },
                                onRenamePreset = { id, name -> timerViewModel.renamePreset(id, name) },
                                onDeletePreset = { timerViewModel.deletePreset(it) },
                                onStatisticsClick = { navigator.navigate(Route.Statistics) },
                                onBack = { navigator.goBack() }
                            )
                        }
                        entry<Route.OmniTimer> { _ ->
                            OmniTimerScreen(
                                viewModel = omniViewModel,
                                onBack = { navigator.goBack() }
                            )
                        }
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
            // Every share used to write the same path, and a read grant lives as long as the task
            // that received it. So a target still holding the URI from an earlier share -- settings
            // only, say -- could read whatever the next one put there, which may include the game
            // history and text notes it was never given.
            //
            // A directory per share gives each its own URI and keeps the filename meaningful to
            // whoever receives it. The previous ones go at the same time: they are a cleartext copy
            // of the user's configuration sitting in the cache with nothing to remove them.
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
