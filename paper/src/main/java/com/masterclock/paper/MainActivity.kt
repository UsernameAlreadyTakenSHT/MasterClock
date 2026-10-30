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
import androidx.core.content.edit
import androidx.core.graphics.drawable.toDrawable
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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

        // The reversed colours as last seen, read before the first frame. The stored settings load
        // after it, so the window and the theme began white whatever was set: with reversed colours
        // every launch flashed white -- one more full refresh on E Ink, and a ghost of it after.
        val uiPrefs = getSharedPreferences(UI_PREFS, MODE_PRIVATE)
        val startReversed = uiPrefs.getBoolean(KEY_REVERSED, false)
        if (startReversed) window.setBackgroundDrawable(android.graphics.Color.BLACK.toDrawable())

        enableEdgeToEdge()
        setContent {
            val timerViewModel: ChessTimerViewModel = viewModel()
            val settings by timerViewModel.settings.collectAsState()
            val isReady by timerViewModel.isReady.collectAsState()
            val context = LocalContext.current
            val reversed = if (isReady) settings.eInkDarkMode else startReversed
            LaunchedEffect(isReady, settings.eInkDarkMode) {
                if (!isReady) return@LaunchedEffect
                uiPrefs.edit { putBoolean(KEY_REVERSED, settings.eInkDarkMode) }
                window.setBackgroundDrawable((if (settings.eInkDarkMode) android.graphics.Color.BLACK else android.graphics.Color.WHITE).toDrawable())
                // The splash screen comes before even that: the system draws it from the theme
                // before the app runs, white. Android 13 lets an app choose the theme it uses on
                // later launches; Android 12 does not, and keeps the white one.
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    splashScreen.setSplashScreenTheme(if (settings.eInkDarkMode) R.style.Theme_MasterClock_Reversed else android.content.res.Resources.ID_NULL)
                }
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


            val navigationState = rememberNavigationState(
                startRoute = Route.Timer,
                topLevelRoutes = setOf(Route.Timer),
            )
            val navigator = remember { Navigator(navigationState) }

            // Back on the clock -- the root, where there is nothing to go back to -- leaves the app on
            // a second press within two seconds; the first says so. It did nothing at all, however
            // often it was pressed. Two presses, because the clock is held in the hands during a game
            // and one stray press must not take the players out of it. The app goes to the
            // background, as Android sends a root activity there itself, so the game is kept.
            var lastRootBackAt by remember { mutableLongStateOf(0L) }
            val pressBackAgainText = stringResource(R.string.back_press_again_to_exit)
            BackHandler {
                if (!navigator.goBack()) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastRootBackAt < DOUBLE_BACK_WINDOW_MS) {
                        lastRootBackAt = 0L
                        moveTaskToBack(true)
                    } else {
                        lastRootBackAt = now
                        android.widget.Toast.makeText(context, pressBackAgainText, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }

            MasterClockTheme(eInkDarkMode = reversed) {
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

                    // Nothing to tap until the stored state is in; see ChessTimerViewModel.isReady.
                    if (isReady) {
                        // No transition. Navigation 3 cross-fades every change of screen over 700ms by
                        // default, and E Ink renders each half-faded frame: a grey, ghosting change,
                        // every time Settings was opened or left. A page turns at once here.
                        NavDisplay(
                            entries = navigationState.toEntries(entryProvider),
                            onBack = { navigator.goBack() },
                            transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                            popTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                            predictivePopTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                        )
                    }
                }
            }
        }
    }
}

/** Where the reversed-colours flag is kept for the next launch's first frame. */
private const val UI_PREFS = "eink_ui"
private const val KEY_REVERSED = "reversed"

/** How soon a second Back on the clock must follow the first to leave the app. */
private const val DOUBLE_BACK_WINDOW_MS = 2_000L
