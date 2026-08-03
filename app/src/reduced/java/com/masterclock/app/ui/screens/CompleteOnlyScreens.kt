package com.masterclock.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.masterclock.app.logic.ChessTimerViewModel
import com.masterclock.app.logic.GameLog

/**
 * Stand-ins for the screens only the Complete build ships.
 *
 * Every one of these lives behind the More tab, which renders under FlavorConfig.hasMoreTab() and
 * is COMPLETE only, so none of them is reachable here. Keeping the real signatures is what lets
 * MainActivity register the whole navigation graph unconditionally: no per-flavor `entry<>` wiring,
 * and therefore no way to mis-declare a route and only find out at runtime. If a signature ever
 * drifts, the three reduced flavors stop compiling, which is the loud failure we want.
 *
 * The point of moving them out is the size of the compiled code. Standard, Light and Mini used to
 * carry CameraX, ZXing, media3 and Coil plus every tool screen, and an APK's dex expands roughly
 * four and a half times once Android compiles it ahead of time -- so dead code costs far more on
 * the device than it does in the download.
 *
 * They back out immediately rather than drawing a blank page, so an entry point added here by
 * mistake strands nobody.
 */

@Composable
private fun Unavailable(onBack: () -> Unit) {
    LaunchedEffect(Unit) { onBack() }
}

@Composable
fun CoinTossScreen(onBack: () -> Unit) = Unavailable(onBack)

@Composable
fun DiceRollScreen(onBack: () -> Unit) = Unavailable(onBack)

@Composable
fun ShortStrawScreen(onBack: () -> Unit) = Unavailable(onBack)

@Composable
fun RandomCardScreen(onBack: () -> Unit) = Unavailable(onBack)

@Composable
fun StopPrecisionScreen(onBack: () -> Unit) = Unavailable(onBack)

@Composable
fun BlindfoldTrainerScreen(onBack: () -> Unit) = Unavailable(onBack)

@Composable
fun KnightPathScreen(onBack: () -> Unit) = Unavailable(onBack)

@Composable
fun NameSquareScreen(onBack: () -> Unit) = Unavailable(onBack)

@Composable
fun Chess960Screen(onBack: () -> Unit) = Unavailable(onBack)

@Composable
fun ModeGuideScreen(onBack: () -> Unit) = Unavailable(onBack)

@Composable
fun ScoreboardScreen(viewModel: ChessTimerViewModel, onBack: () -> Unit) = Unavailable(onBack)

@Composable
fun NotebookScreen(viewModel: ChessTimerViewModel, onBack: () -> Unit) = Unavailable(onBack)

@Composable
fun BluetoothBoardScreen(viewModel: ChessTimerViewModel, onBack: () -> Unit) = Unavailable(onBack)

@Composable
fun GameLogsScreen(history: List<GameLog>, onBack: () -> Unit) = Unavailable(onBack)

@Composable
fun QRShareScreen(data: String, onBack: () -> Unit) = Unavailable(onBack)

@Composable
fun QRReceiveScreen(onResult: (String) -> Unit, onBack: () -> Unit) = Unavailable(onBack)
