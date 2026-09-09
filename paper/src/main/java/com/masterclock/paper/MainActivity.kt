package com.masterclock.paper

import android.app.Activity
import android.media.AudioManager
import android.os.Bundle
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
            val settings by timerViewModel.settings.collectAsState()
            val context = LocalContext.current

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
                                currentSettings = settings,
                                category = SettingsCategory.fromRoute(route.category),
                                onSettingsChanged = { timerViewModel.updateSettings(it) },
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
}
