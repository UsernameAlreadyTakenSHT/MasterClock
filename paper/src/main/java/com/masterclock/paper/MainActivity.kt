package com.masterclock.paper

import android.app.Activity
import android.media.AudioManager
import android.os.Bundle
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.ComponentActivity
import com.masterclock.paper.ui.components.MMDAlertDialog
import com.masterclock.paper.R
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalAccessibilityManager
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
import com.masterclock.paper.ui.components.MessageMMD
import com.mudita.mmd.components.snackbar.SnackbarDurationMMD
import com.mudita.mmd.components.snackbar.SnackbarHostMMD
import com.mudita.mmd.components.snackbar.SnackbarHostStateMMD
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
                    // A recreation is not leaving the app, as on the phone: the colours reversed, the
                    // language or the font size changed, and the game was paused.
                    if (event == Lifecycle.Event.ON_PAUSE && !isChangingConfigurations) {
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
            //
            // What the app has to say in passing, by MMD's own message: a rule and a line of text at
            // the foot of the screen, there and then gone. They were the system's toasts, which fade
            // in and out and are drawn outside the app, in the system's colours whatever these are.
            val messages = remember { SnackbarHostStateMMD() }
            val messageScope = rememberCoroutineScope()
            // One at a time, the newest. Each waited its turn behind the one showing: three presses
            // on a link, on a device with no browser, held "No browser" for twelve seconds, and a
            // "Press Back again" waiting behind it was shown late or not at all, its two seconds
            // counted from the press.
            var standing by remember { mutableStateOf<Job?>(null) }
            val say: (String) -> Unit = { text ->
                standing?.cancel()
                standing = messageScope.launch { messages.showSnackbar(text) }
            }
            // The message that asks for a second Back stands for as long as that second press is
            // taken, and no longer: left to the snackbar's own four seconds it went on asking for two
            // seconds after a press had stopped counting.

            // Two seconds, or as long as the system says a message with text needs: with TalkBack the
            // message asking for the second press takes most of two seconds to be spoken, and the
            // press that followed came late, counted as a first, and was asked for again.
            val backWindowMs = LocalAccessibilityManager.current
                ?.calculateRecommendedTimeoutMillis(DOUBLE_BACK_WINDOW_MS, containsText = true) ?: DOUBLE_BACK_WINDOW_MS
            var lastRootBackAt by remember { mutableLongStateOf(0L) }
            val pressBackAgainText = stringResource(R.string.back_press_again_to_exit)
            BackHandler {
                if (!navigator.goBack()) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastRootBackAt < backWindowMs) {
                        lastRootBackAt = 0L
                        standing?.cancel()
                        moveTaskToBack(true)
                    } else {
                        lastRootBackAt = now
                        standing?.cancel()
                        standing = messageScope.launch {
                            withTimeoutOrNull(backWindowMs) {
                                messages.showSnackbar(pressBackAgainText, duration = SnackbarDurationMMD.Indefinite)
                            }
                        }
                    }
                }
            }

            // The settings asked for, not a function that applies them, so that it can be saved: a font
            // size or a language changed while the question stood recreated the screen, the question
            // went, and the change with it, nothing said. The clock's own question is saved the same.
            var pendingChange by androidx.compose.runtime.saveable.rememberSaveable(stateSaver = PendingSettingsSaver) {
                mutableStateOf<com.masterclock.app.logic.ChessClockSettings?>(null)
            }
            MasterClockTheme(eInkDarkMode = reversed, appFont = if (isReady) settings.appFont else com.masterclock.app.logic.AppFont.SYSTEM) {
                // Asked once the stored state is in, and only over a game. The question is saved
                // across the app being killed and the game is not: it came back at once, before the
                // settings had loaded -- confirmed then, the change was written over defaults -- and
                // over a clock with no game on it. With nothing at stake the change is simply made.
                LaunchedEffect(isReady, pendingChange) {
                    val wanted = pendingChange
                    if (isReady && wanted != null && !timerViewModel.hasGameAtStake()) {
                        pendingChange = null
                        timerViewModel.updateSettings(wanted)
                    }
                }
                pendingChange?.takeIf { isReady && timerViewModel.hasGameAtStake() }?.let { wanted ->
                    val apply = { timerViewModel.updateSettings(wanted) }
                    MMDAlertDialog(
                        onDismissRequest = { pendingChange = null },
                        title = stringResource(R.string.timer_reset_title),
                        text = stringResource(R.string.timer_reset_message),
                        confirmButtonText = stringResource(R.string.common_reset),
                        onConfirm = { pendingChange = null; apply() },
                        dismissButtonText = stringResource(R.string.common_cancel),
                        onDismiss = { pendingChange = null },
                    )
                }
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val entryProvider = entryProvider<NavKey> {
                        entry<Route.Timer> { _ ->
                            TimerScreen(
                                viewModel = timerViewModel,
                                onSettingsClick = {
                                    navigator.navigate(Route.Settings)
                                },
                            )
                        }
                        entry<Route.Settings> { _ ->
                            SettingsScreen(
                                currentSettings = settings,
                                // A game in play asks first, as on the phone.
                                onSettingsChanged = { new ->
                                    val current = timerViewModel.settings.value
                                    if (current.confirmReset && timerViewModel.hasGameAtStake() && settingsEndTheGame(current, new)) {
                                        pendingChange = new
                                    } else timerViewModel.updateSettings(new)
                                },
                                onBackClick = {
                                    navigator.goBack()
                                },
                                onAboutClick = { navigator.navigate(Route.About) },
                            )
                        }
                        entry<Route.About> { _ ->
                            AboutScreen(onBackClick = { navigator.goBack() }, onMessage = say)
                        }
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
                // Over whichever screen is up, and clear of the system's bars. It takes no touch:
                // see MessageMMD, which is drawn in place of MMD's snackbar for that.
                Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.BottomCenter) {
                    SnackbarHostMMD(messages) { MessageMMD(it.visuals.message) }
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

/** A settings change waiting on its question, kept across a recreation as text; none is the empty string. */
private val PendingSettingsSaver = androidx.compose.runtime.saveable.Saver<com.masterclock.app.logic.ChessClockSettings?, String>(
    save = { it?.let { s -> PendingSettingsJson.encodeToString(com.masterclock.app.logic.ChessClockSettings.serializer(), s) } ?: "" },
    restore = { text ->
        if (text.isEmpty()) null
        else runCatching { PendingSettingsJson.decodeFromString(com.masterclock.app.logic.ChessClockSettings.serializer(), text) }.getOrNull()
    },
)

private val PendingSettingsJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; coerceInputValues = true }
