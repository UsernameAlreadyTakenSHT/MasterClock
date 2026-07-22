package com.masterclock.app.logic

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.masterclock.app.data.Converters
import com.masterclock.app.data.GameDatabase
import com.masterclock.app.data.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds

@Serializable
data class PlayerStateProxy(
    val timeRemainingMs: Long,
    val secondaryTimeMs: Long = 0,
    val delayRemainingMs: Long = 0,
    val isOutOfTime: Boolean = false,
    val isNegative: Boolean = false,
    val isInByoyomi: Boolean = false,
    val byoyomiPeriodsRemaining: Int = 0,
    val movesRemainingInPeriod: Int = 0,
    val currentByoyomiMovesGoal: Int = 0,
    val moveCount: Int = 0,
    val currentPeriodIndex: Int = 0,
    val hasFlagged: Boolean = false,
    val currentPhaseIndex: Int = 0,
    val isInInterPhasePause: Boolean = false,
    val pauseTimeRemainingMs: Long = 0,
    val revealTimeUntilMs: Long = 0,
    val lastRevealPercentage: Int = 101,
    val initialTotalTimeMs: Long = 0,
    val isGongReflectionPhase: Boolean = true
)

@Serializable
data class ChessClockStateProxy(
    val players: List<PlayerStateProxy>,
    val activePlayer: Int? = null,
    val isPaused: Boolean = true,
    val cycleCount: Int = 1,
    val globalTimeMs: Long = 0,
    val isArbitreMode: Boolean = false,
    val firstToFlag: Int? = null
)

fun PlayerState.toProxy() = PlayerStateProxy(
    timeRemainingMs, secondaryTimeMs, delayRemainingMs, isOutOfTime, isNegative, isInByoyomi,
    byoyomiPeriodsRemaining, movesRemainingInPeriod, currentByoyomiMovesGoal, moveCount,
    currentPeriodIndex, hasFlagged, currentPhaseIndex, isInInterPhasePause,
    pauseTimeRemainingMs, revealTimeUntilMs, lastRevealPercentage, initialTotalTimeMs, isGongReflectionPhase
)

fun PlayerStateProxy.toState() = PlayerState(
    timeRemainingMs, secondaryTimeMs, delayRemainingMs, isOutOfTime, isNegative, isInByoyomi,
    byoyomiPeriodsRemaining, movesRemainingInPeriod, currentByoyomiMovesGoal, moveCount,
    currentPeriodIndex, hasFlagged, currentPhaseIndex, isInInterPhasePause,
    pauseTimeRemainingMs, revealTimeUntilMs, lastRevealPercentage, initialTotalTimeMs, isGongReflectionPhase
)

fun ChessClockState.toProxy() = ChessClockStateProxy(
    players.map { it.toProxy() }, activePlayer, isPaused, cycleCount, globalTimeMs, isArbitreMode, firstToFlag
)

fun ChessClockStateProxy.toState() = ChessClockState(
    players.map { it.toState() }, activePlayer, isPaused, cycleCount, globalTimeMs, isArbitreMode, firstToFlag
)

@Serializable
enum class TimerMode {
    SUDDEN_DEATH, FISHER, BRONSTEIN, US_DELAY,
    MOVE_TIMER_STANDARD, MOVE_TIMER_SAVE_CAP, MOVE_TIMER_OVERTIME, MOVE_TIMER_GLOBAL, MOVE_TIMER_SHARED, MOVE_TIMER_GLOBAL_SHARED,
    HOURGLASS, BYOYOMI_JAPANESE, BYOYOMI_CANADIAN, BYOYOMI_PROGRESSIVE,
    CHRONO_COUNTDOWN, CHRONO_COUNTUP, MOVE_COUNTS_UP, MOVE_COUNTS_DOWN,
    FIDE_PERIODS, PHASES, RANDOM, HIDDEN, GONG, FAST_MOVE
}

@Serializable
enum class FlagBehavior { FREEZE, FLAG, NEGATIVE, REVERSE }
@Serializable
enum class AppThemeMode { LIGHT, DARK, AUTO }
@Serializable
enum class BeepCountdownThreshold { OFF, THREE_SEC, TEN_SEC }
@Serializable
enum class LogDurationLimit { ONE_DAY, ONE_WEEK, ONE_MONTH, SIX_MONTHS, ONE_YEAR, INFINITE }
@Serializable
enum class MultiPlayerLayout { BALANCED, INVERTED }

@Serializable
data class GamePhase(
    val name: String = "",
    val timeMs: Long = 300_000,
    val autoAdvance: Boolean = true,
    val flagOnEnd: Boolean = false,
)

@Serializable
data class FidePeriod(
    val timeMs: Long = 5400_000,
    val incrementMs: Long = 30_000,
    val movesToNext: Int = 40,
    val isFischer: Boolean = true,
    val hasDelay: Boolean = false,
)

@Serializable
data class PlayerSettings(
    val initialTimeMs: Long = 600_000,
    val moveTimeMs: Long = 30_000,
    val incrementMs: Long = 10_000,
    val timeCapMs: Long = 120_000,
    val mode: TimerMode = TimerMode.SUDDEN_DEATH,
    val byoyomiPeriods: Int = 5,
    val byoyomiTimeMs: Long = 30_000,
    val byoyomiProgression: Int = 5,
    val maxMoves: Int = 20,
    val fidePeriods: List<FidePeriod> = listOf(FidePeriod()),
    val phases: List<GamePhase> = listOf(GamePhase("Phase 1")),
    val randomMinTimeMs: Long = 60_000,
    val randomMaxTimeMs: Long = 600_000,
    val randomMinIncMs: Long = 0,
    val randomMaxIncMs: Long = 10_000,
    val roundedTime: Boolean = true,
    val showHiddenPercentages: Boolean = true,
    val gongSimultaneous: Boolean = false,
    val gongReflectionMs: Long = 10_000,
    val gongMoveMs: Long = 2000,
    
    // Fast Move settings
    val fastMoveMode: FastMoveType = FastMoveType.ACCELERATE,
    val fastMoveGracePeriodMs: Long = 5000,
    val fastMoveFastPeriodMs: Long = 20_000,
    val fastMoveAccelRate: Float = 0.5f,
    val fastMoveFullAccelRate: Float = 2.0f,
    val fastMoveShrinkDecrementMs: Long = 10_000,
    val fastMoveShrinkFloorMs: Long = 5000,
    val fastMoveTransferCumulative: Boolean = true
)

@Serializable
enum class FastMoveType { ACCELERATE, SHRINK, TRANSFER }

@Serializable
enum class GameType { CHESS, DRAUGHTS, SHOGI }

@Serializable
enum class ClockOrientation { VERTICAL, HORIZONTAL_LEFT, HORIZONTAL_RIGHT }

@Serializable
enum class NotebookNoteType { TEXT, DRAWING, VOICE, IMAGE, VIDEO, BOARD }

@Serializable
data class DrawingPath(
    val points: List<Pair<Float, Float>> = emptyList(),
    val color: Long = 0xFF000000,
    val strokeWidth: Float = 5f,
    val isEraser: Boolean = false
)

@Serializable
data class NotebookNote(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String = "New Note",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val type: NotebookNoteType = NotebookNoteType.TEXT,
    val audioPath: String? = null,
    val audioDurationMs: Long = 0,
    val drawingPaths: List<DrawingPath> = emptyList(),
    val imagePath: String? = null,
    val videoPath: String? = null,
    val boardPosition: List<String> = List(64) { "" }
)

@Serializable
data class ChessClockSettings(
    val gameType: GameType = GameType.CHESS,
    val main: PlayerSettings = PlayerSettings(),
    val p1Custom: PlayerSettings = PlayerSettings(),
    val p2Custom: PlayerSettings = PlayerSettings(),
    val p3Custom: PlayerSettings = PlayerSettings(),
    val p4Custom: PlayerSettings = PlayerSettings(),
    val differentSettingsPerPlayer: Boolean = false,
    val numberOfPlayers: Int = 2,
    val multiPlayerLayout: MultiPlayerLayout = MultiPlayerLayout.BALANCED,
    val playerMapping: List<Int> = listOf(1, 2, 3, 4),
    
    val isOneForAll: Boolean = true,
    val flagBehavior: FlagBehavior = FlagBehavior.FREEZE,
    val confirmReset: Boolean = true,
    val fischerFideFirstMove: Boolean = false,
    val forcedMoveCounter: Boolean = true,
    val triggerOnPress: Boolean = true,
    val pauseOnBackground: Boolean = true,

    val alwaysShowHours: Boolean = false,
    val alwaysShowMinutes: Boolean = true,
    val showTenthsThresholdMs: Long = 10_000,
    val forceScreenOn: Boolean = true,
    val showCurrentPeriod: Boolean = true,
    val alwaysShowMoveCount: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.AUTO,
    val flashOnLowTime: Boolean = false,
    val clockOrientation: ClockOrientation = ClockOrientation.VERTICAL,
    val showHundredths: Boolean = false,
    val showHundredthsOnlyUnder10s: Boolean = false,
    val activePlayerSideBigger: Boolean = false,
    val autoSwitchOnBoardMove: Boolean = false,
    val forceFullBrightness: Boolean = false,
    val fullscreenMode: Boolean = false,

    // Audio - Default OFF for Mindful Zen start
    val hapticFeedback: Boolean = false,
    val playSwitchSound: Boolean = false,
    val voiceAnnouncementsEnabled: Boolean = false,
    val beepThreshold: BeepCountdownThreshold = BeepCountdownThreshold.OFF,
    val tripleBeepTimeUp: Boolean = false,
    val hapticCountdownThreshold: BeepCountdownThreshold = BeepCountdownThreshold.OFF,
    val audioOutputMedia: Boolean = true, // Default to Media stream
    val soundsVolume: Float = 0.7f,
    val voiceVolume: Float = 0.7f,

    // Custom Sounds (URIs)
    val customGongUri: String? = null,
    val customBeepUri: String? = null,
    val customFinalBeepUri: String? = null,
    val customSwitchUri: String? = null,

    // Display Colors (ARGB)
    val activeColor: Long = 0xFF4CAF50,
    val inactiveColor: Long = 0xFF9E9E9E,
    val activeTextColor: Long = 0xFFFFFFFF,
    val inactiveTextColor: Long = 0xFFFFFFFF,
    val secondaryTextColor: Long = 0xFFFFFFFF, // Default to white
    val alertTextColor: Long = 0xFFFFFFFF,    // White for critical alerts
    val lossColor: Long = 0xFFF44336,          // Red for loss
    val reflectionColor: Long = 0xFF2196F3,    // Default to blue for Gong reflection
    val eInkDarkMode: Boolean = false,         // E-Ink Color Reversal

    // Logs
    val logHistoryLimit: Int = 100,
    val logDurationLimit: LogDurationLimit = LogDurationLimit.INFINITE,
    
    val loopPhases: Boolean = false,
    // When true, a manual tap can advance/skip the current phase early (before its own timer
    // expires), on top of the normal auto/manual advance rules -- see AUDIT.md.
    val allowPhaseSkip: Boolean = false,
    val pauseBetweenPhasesMs: Long = 0,
    val notebookNotes: List<NotebookNote> = emptyList()
)

@Serializable
data class GameEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String,
    val playerIndex: Int? = null,
    val timeRemainingMs: Long? = null,
    val moveCount: Int? = null,
    val detail: String? = null,
    val moveNotation: String? = null // For PGN (e.g., "e2e4")
)

@Serializable
data class GameLog(
    val id: String = java.util.UUID.randomUUID().toString(),
    val startTime: Long = System.currentTimeMillis(),
    val settings: ChessClockSettings,
    val events: List<GameEvent> = emptyList(),
    val initialPlayerStates: List<PlayerStateProxy> = emptyList() // Store actual starting times
)

@Serializable
data class SharePackage(
    val settings: ChessClockSettings,
    val logs: List<GameLog>? = null,
    val scoreboard: ScoreboardSession? = null
)

@Serializable
data class ScoreboardGame(
    val id: String = java.util.UUID.randomUUID().toString(),
    val result: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class ScoreboardSession(
    val id: String = java.util.UUID.randomUUID().toString(),
    val player1Name: String = "Player 1",
    val player2Name: String = "Player 2",
    val games: List<ScoreboardGame> = emptyList()
)

data class PlayerState(
    val timeRemainingMs: Long,
    val secondaryTimeMs: Long = 0,
    val delayRemainingMs: Long = 0,
    val isOutOfTime: Boolean = false,
    val isNegative: Boolean = false,
    val isInByoyomi: Boolean = false,
    val byoyomiPeriodsRemaining: Int = 0,
    val movesRemainingInPeriod: Int = 0,
    val currentByoyomiMovesGoal: Int = 0,
    val moveCount: Int = 0,
    val currentPeriodIndex: Int = 0,
    val hasFlagged: Boolean = false,
    val currentPhaseIndex: Int = 0,
    val isInInterPhasePause: Boolean = false,
    val pauseTimeRemainingMs: Long = 0,
    val revealTimeUntilMs: Long = 0,
    val lastRevealPercentage: Int = 101, // Start higher than any threshold
    val initialTotalTimeMs: Long = 0,
    val isGongReflectionPhase: Boolean = true
)

data class ChessClockState(
    val players: List<PlayerState>,
    val activePlayer: Int? = null, // 1-based index
    val isPaused: Boolean = true,
    val cycleCount: Int = 1,
    val globalTimeMs: Long = 0,
    val isArbitreMode: Boolean = false,
    val firstToFlag: Int? = null
)

/**
 * Imported settings (QR / JSON / ZIP share packages) come from an untrusted source and must never
 * be trusted with raw file paths or note ids: [NotebookNote.id] feeds a `contains(id)` file filter
 * on delete (an empty/crafted id can match every file in `filesDir`), and audioPath/imagePath/
 * videoPath/custom*Uri are read and written to directly. See AUDIT.md §3 (HIGH finding).
 */
private fun sanitizeImportedSettings(context: android.content.Context, settings: ChessClockSettings): ChessClockSettings {
    val sanitizedNotes = settings.notebookNotes.map { note ->
        note.copy(
            id = java.util.UUID.randomUUID().toString(),
            audioPath = sanitizeImportedMediaPath(context, note.audioPath),
            imagePath = sanitizeImportedMediaPath(context, note.imagePath),
            videoPath = sanitizeImportedMediaPath(context, note.videoPath),
        )
    }
    val sanitizedNumberOfPlayers = settings.numberOfPlayers.coerceIn(1, 4)
    return settings.copy(
        notebookNotes = sanitizedNotes,
        customBeepUri = sanitizeImportedContentUri(settings.customBeepUri),
        customGongUri = sanitizeImportedContentUri(settings.customGongUri),
        customFinalBeepUri = sanitizeImportedContentUri(settings.customFinalBeepUri),
        customSwitchUri = sanitizeImportedContentUri(settings.customSwitchUri),
        numberOfPlayers = sanitizedNumberOfPlayers,
        // Per-player customization (p3Custom/p4Custom) is only ever offered by the app's own UI for
        // 2 players (SettingsBehaviorPage forces this same rule when toggling "more players"); an
        // import must not be able to sneak past that restriction. See AUDIT.md §7.4.
        differentSettingsPerPlayer = settings.differentSettingsPerPlayer && sanitizedNumberOfPlayers <= 2,
        playerMapping = settings.playerMapping.let { mapping ->
            if (mapping.size == 4 && mapping.all { it in 1..4 }) mapping else listOf(1, 2, 3, 4)
        },
        main = validateImportedPlayerSettings(settings.main),
        p1Custom = validateImportedPlayerSettings(settings.p1Custom),
        p2Custom = validateImportedPlayerSettings(settings.p2Custom),
        p3Custom = validateImportedPlayerSettings(settings.p3Custom),
        p4Custom = validateImportedPlayerSettings(settings.p4Custom),
    )
}

/**
 * Clamps imported PlayerSettings to sane, non-negative bounds. Without this, a crafted
 * randomMinTimeMs > randomMaxTimeMs crashes the app the moment a RANDOM/HIDDEN-mode game is
 * started: `(min..max).random()` in createInitialState() throws IllegalArgumentException on an
 * empty/inverted range. See AUDIT.md §3 (HIGH finding).
 */
private fun validateImportedPlayerSettings(settings: PlayerSettings): PlayerSettings {
    val randomMinTimeMs = settings.randomMinTimeMs.coerceAtLeast(0)
    val randomMaxTimeMs = settings.randomMaxTimeMs.coerceAtLeast(randomMinTimeMs)
    val randomMinIncMs = settings.randomMinIncMs.coerceAtLeast(0)
    val randomMaxIncMs = settings.randomMaxIncMs.coerceAtLeast(randomMinIncMs)
    return settings.copy(
        initialTimeMs = settings.initialTimeMs.coerceAtLeast(0),
        moveTimeMs = settings.moveTimeMs.coerceAtLeast(0),
        incrementMs = settings.incrementMs.coerceAtLeast(0),
        timeCapMs = settings.timeCapMs.coerceAtLeast(0),
        byoyomiPeriods = settings.byoyomiPeriods.coerceAtLeast(0),
        byoyomiTimeMs = settings.byoyomiTimeMs.coerceAtLeast(0),
        byoyomiProgression = settings.byoyomiProgression.coerceAtLeast(0),
        maxMoves = settings.maxMoves.coerceAtLeast(0),
        randomMinTimeMs = randomMinTimeMs,
        randomMaxTimeMs = randomMaxTimeMs,
        randomMinIncMs = randomMinIncMs,
        randomMaxIncMs = randomMaxIncMs,
        gongReflectionMs = settings.gongReflectionMs.coerceAtLeast(0),
        gongMoveMs = settings.gongMoveMs.coerceAtLeast(0),
        fastMoveGracePeriodMs = settings.fastMoveGracePeriodMs.coerceAtLeast(0),
        fastMoveFastPeriodMs = settings.fastMoveFastPeriodMs.coerceAtLeast(0),
        fastMoveAccelRate = settings.fastMoveAccelRate.coerceAtLeast(0f),
        fastMoveFullAccelRate = settings.fastMoveFullAccelRate.coerceAtLeast(0f),
        fastMoveShrinkDecrementMs = settings.fastMoveShrinkDecrementMs.coerceAtLeast(0),
        fastMoveShrinkFloorMs = settings.fastMoveShrinkFloorMs.coerceAtLeast(0),
    )
}

/** Only accept a path that resolves under this app's own sandbox (filesDir/cacheDir); drop anything else. */
private fun sanitizeImportedMediaPath(context: android.content.Context, path: String?): String? {
    if (path.isNullOrBlank()) return null
    return try {
        val canonical = java.io.File(path).canonicalFile
        val allowedRoots = listOf(context.filesDir.canonicalFile, context.cacheDir.canonicalFile)
        val isInsideSandbox = allowedRoots.any { root ->
            canonical == root || canonical.path.startsWith(root.path + java.io.File.separator)
        }
        if (isInsideSandbox) canonical.path else null
    } catch (_: Exception) {
        null
    }
}

/** Only accept content:// URIs (SAF-granted); a bare file:// URI would read an arbitrary sandbox file. */
private fun sanitizeImportedContentUri(uri: String?): String? {
    if (uri.isNullOrBlank()) return null
    return if (uri.startsWith("content://")) uri else null
}

/**
 * Pure per-player time-transition function: given the current [PlayerState] and how much time
 * elapsed, returns the next [PlayerState] for every timer mode except the multi-player-coupled ones
 * (PHASES/GONG/HOURGLASS/CHRONO_COUNTDOWN/CHRONO_COUNTUP/MOVE_TIMER_SHARED/MOVE_TIMER_GLOBAL_SHARED, handled inline in
 * [ChessTimerViewModel.tick]). Has no dependency on Android or ViewModel state, so it's called
 * directly from both [ChessTimerViewModel.tick] and [ChessTimerLogicTest] -- see AUDIT.md §6 (the
 * previous test file duplicated this logic instead of exercising it, so a real bug here could drift
 * undetected).
 */
/**
 * Shared countdown + flag logic used both by [tickPlayer]'s default branch and by every mode that
 * has its own early-return in [ChessTimerViewModel.tick] (PHASES, MOVE_TIMER_SHARED,
 * MOVE_TIMER_GLOBAL_SHARED, HOURGLASS, CHRONO_COUNTDOWN, CHRONO_COUNTUP) -- see AUDIT.md, these modes
 * used to bypass FlagBehavior/audio/voice entirely by never calling tickPlayer at all.
 */
internal fun applyFlagBehaviorDelta(currentTime: Long, isOut: Boolean, isNegative: Boolean, delta: Long, flagBehavior: FlagBehavior): Triple<Long, Boolean, Boolean> {
    var newTime = currentTime
    var out = isOut
    var neg = isNegative
    if (out) {
        if (flagBehavior == FlagBehavior.NEGATIVE || flagBehavior == FlagBehavior.REVERSE) {
            newTime += delta
        }
    } else {
        newTime -= delta
        if (newTime <= 0) {
            out = true
            when (flagBehavior) {
                FlagBehavior.NEGATIVE -> { neg = true; newTime = -newTime }
                FlagBehavior.REVERSE -> { neg = false; newTime = -newTime }
                else -> { newTime = 0 }
            }
        }
    }
    return Triple(newTime, out, neg)
}

internal fun tickPlayer(p: PlayerState, delta: Long, s: PlayerSettings, settings: ChessClockSettings): PlayerState {
    if (p.delayRemainingMs > 0) return p.copy(delayRemainingMs = (p.delayRemainingMs - delta).coerceAtLeast(0))
    return when (s.mode) {
        TimerMode.MOVE_TIMER_OVERTIME -> { val newTime = p.timeRemainingMs - delta; if (newTime < 0) { val newSec = p.secondaryTimeMs + newTime; p.copy(timeRemainingMs = 0, secondaryTimeMs = newSec, isOutOfTime = newSec <= 0) } else p.copy(timeRemainingMs = newTime) }
        TimerMode.MOVE_TIMER_GLOBAL -> { val newTime = p.timeRemainingMs - delta; val newSec = p.secondaryTimeMs - delta; p.copy(timeRemainingMs = newTime, secondaryTimeMs = newSec, isOutOfTime = newTime <= 0 || newSec <= 0) }
        TimerMode.BYOYOMI_JAPANESE -> {
            if (!p.isInByoyomi) { val newTime = p.timeRemainingMs - delta; if (newTime <= 0) p.copy(timeRemainingMs = s.byoyomiTimeMs, isInByoyomi = true) else p.copy(timeRemainingMs = newTime) }
            else { val newTime = p.timeRemainingMs - delta; if (newTime <= 0) { val newPeriods = p.byoyomiPeriodsRemaining - 1; if (newPeriods <= 0) p.copy(timeRemainingMs = 0, byoyomiPeriodsRemaining = 0, isOutOfTime = true) else p.copy(timeRemainingMs = s.byoyomiTimeMs, byoyomiPeriodsRemaining = newPeriods) } else p.copy(timeRemainingMs = newTime) }
        }
        TimerMode.BYOYOMI_CANADIAN, TimerMode.BYOYOMI_PROGRESSIVE -> { if (!p.isInByoyomi) { val newTime = p.timeRemainingMs - delta; if (newTime <= 0) p.copy(timeRemainingMs = s.byoyomiTimeMs, isInByoyomi = true) else p.copy(timeRemainingMs = newTime) } else { val newTime = p.timeRemainingMs - delta; p.copy(timeRemainingMs = newTime, isOutOfTime = newTime <= 0) } }
        TimerMode.FIDE_PERIODS -> {
            var newTime = p.timeRemainingMs - delta
            var isOut = p.isOutOfTime
            var periodIdx = p.currentPeriodIndex
            var flagged = p.hasFlagged
            if (newTime <= 0) {
                if (!settings.forcedMoveCounter && periodIdx < s.fidePeriods.size - 1) {
                    val nextPeriod = s.fidePeriods[periodIdx + 1]
                    newTime = nextPeriod.timeMs
                    periodIdx++
                    flagged = true
                    isOut = false
                } else {
                    newTime = 0
                    isOut = true
                    flagged = true
                }
            }
            p.copy(timeRemainingMs = newTime, isOutOfTime = isOut, currentPeriodIndex = periodIdx, hasFlagged = flagged)
        }
        TimerMode.HIDDEN -> {
            val newTime = p.timeRemainingMs - delta
            var revealUntil = (p.revealTimeUntilMs - delta).coerceAtLeast(0)
            var lastPerc = p.lastRevealPercentage
            if (newTime <= 0) {
                p.copy(timeRemainingMs = 0, isOutOfTime = true, revealTimeUntilMs = 0)
            } else {
                val initial = p.initialTotalTimeMs
                if (initial > 0 && s.showHiddenPercentages) {
                    val currentPerc = ((newTime * 100) / initial).toInt()
                    val thresholds = listOf(50, 25, 10, 5, 2, 1)
                    val trigger = thresholds.find { it in (currentPerc + 1)..lastPerc }
                    if (trigger != null) {
                        revealUntil = 5000L
                        lastPerc = trigger
                    }
                }
                p.copy(timeRemainingMs = newTime, revealTimeUntilMs = revealUntil, lastRevealPercentage = lastPerc)
            }
        }
        TimerMode.MOVE_COUNTS_UP, TimerMode.MOVE_COUNTS_DOWN -> p
        TimerMode.FAST_MOVE -> {
            if (s.fastMoveMode == FastMoveType.ACCELERATE) {
                val timeSpent = p.initialTotalTimeMs - p.timeRemainingMs
                val accel = when {
                    timeSpent >= s.fastMoveFastPeriodMs -> s.fastMoveFullAccelRate
                    timeSpent >= s.fastMoveGracePeriodMs -> s.fastMoveAccelRate
                    else -> 0f
                }
                val effectiveDelta = (delta * (1f + accel)).toLong()
                val newTime = (p.timeRemainingMs - effectiveDelta).coerceAtLeast(0)
                p.copy(timeRemainingMs = newTime, isOutOfTime = newTime <= 0)
            } else {
                val newTime = (p.timeRemainingMs - delta).coerceAtLeast(0)
                p.copy(timeRemainingMs = newTime, isOutOfTime = newTime <= 0)
            }
        }
        else -> {
            val (newTime, isOut, isNeg) = applyFlagBehaviorDelta(p.timeRemainingMs, p.isOutOfTime, p.isNegative, delta, settings.flagBehavior)
            p.copy(timeRemainingMs = newTime, isOutOfTime = isOut, isNegative = isNeg)
        }
    }
}

/**
 * Pure post-move state transition (increments/resets/period-advances the mover after
 * [ChessTimerViewModel.startOrSwitch] records a move) for every mode driven by [applyPostMoveLogic].
 * Exposed at the top level for the same testability reason as [tickPlayer] -- see AUDIT.md §6.
 */
internal fun computePostMoveState(state: ChessClockState, playerIndex: Int, timeSpentOnMove: Long, settings: ChessClockSettings, s: PlayerSettings): ChessClockState {
    val p = state.players[playerIndex - 1]
    val tempP = if (s.mode == TimerMode.MOVE_COUNTS_DOWN) {
        val next = (p.moveCount - 1).coerceAtLeast(0)
        p.copy(moveCount = next, isOutOfTime = next <= 0)
    } else {
        p.copy(moveCount = p.moveCount + 1)
    }

    // FAST_MOVE/TRANSFER updates two players (the mover resets to moveTimeMs, the opponent receives
    // the transferred time), so it can't fit the single-player `newP` slot below; handled as its own
    // early return. (Previously computed into a local `updatedPlayers` list that nothing ever read, so
    // the opponent's transferred time was silently discarded -- see AUDIT.md §6.)
    if (s.mode == TimerMode.FAST_MOVE && s.fastMoveMode == FastMoveType.TRANSFER) {
        val opponentIndex = playerIndex % settings.numberOfPlayers
        val opponent = state.players[opponentIndex]
        // Per-move, not tied to any base "initial time": cumulative keeps stacking onto whatever the
        // opponent already has, non-cumulative replaces it outright with exactly what was just spent.
        val updatedOpponent = if (s.fastMoveTransferCumulative) {
            opponent.copy(timeRemainingMs = opponent.timeRemainingMs + timeSpentOnMove)
        } else {
            opponent.copy(timeRemainingMs = timeSpentOnMove)
        }
        val movedP = tempP.copy(timeRemainingMs = s.moveTimeMs)
        val newList = state.players.toMutableList().apply {
            this[playerIndex - 1] = movedP
            this[opponentIndex] = updatedOpponent
        }
        return state.copy(players = newList)
    }

    val newP = when (s.mode) {
        TimerMode.FISHER -> tempP.copy(timeRemainingMs = p.timeRemainingMs + s.incrementMs)
        TimerMode.RANDOM, TimerMode.HIDDEN -> tempP.copy(timeRemainingMs = p.timeRemainingMs + p.secondaryTimeMs)
        TimerMode.BRONSTEIN -> tempP.copy(timeRemainingMs = p.timeRemainingMs + timeSpentOnMove.coerceAtMost(s.incrementMs))
        TimerMode.MOVE_TIMER_STANDARD, TimerMode.MOVE_TIMER_OVERTIME, TimerMode.MOVE_TIMER_GLOBAL, TimerMode.MOVE_TIMER_GLOBAL_SHARED -> tempP.copy(timeRemainingMs = s.moveTimeMs)
        TimerMode.MOVE_TIMER_SAVE_CAP -> { val newBank = (p.secondaryTimeMs + p.timeRemainingMs.coerceAtLeast(0)).coerceAtMost(s.timeCapMs); tempP.copy(timeRemainingMs = s.moveTimeMs + newBank, secondaryTimeMs = newBank) }
        TimerMode.BYOYOMI_JAPANESE -> if (p.isInByoyomi) tempP.copy(timeRemainingMs = s.byoyomiTimeMs) else tempP
        TimerMode.BYOYOMI_CANADIAN -> if (p.isInByoyomi) { val rem = p.movesRemainingInPeriod - 1; if (rem <= 0) tempP.copy(timeRemainingMs = s.byoyomiTimeMs, movesRemainingInPeriod = s.byoyomiPeriods) else tempP.copy(movesRemainingInPeriod = rem) } else tempP
        TimerMode.BYOYOMI_PROGRESSIVE -> if (p.isInByoyomi) { val rem = p.movesRemainingInPeriod - 1; if (rem <= 0) { val goal = p.currentByoyomiMovesGoal + s.byoyomiProgression; tempP.copy(timeRemainingMs = s.byoyomiTimeMs, movesRemainingInPeriod = goal, currentByoyomiMovesGoal = goal) } else tempP.copy(movesRemainingInPeriod = rem) } else tempP
        TimerMode.FIDE_PERIODS -> {
            val currentPeriod = s.fidePeriods.getOrNull(p.currentPeriodIndex) ?: FidePeriod()
            var nextTime = p.timeRemainingMs
            var periodIdx = p.currentPeriodIndex
            var flagged = p.hasFlagged
            var activePeriod = currentPeriod
            if (currentPeriod.isFischer) nextTime += currentPeriod.incrementMs
            if (settings.forcedMoveCounter && currentPeriod.movesToNext > 0 && tempP.moveCount >= currentPeriod.movesToNext && p.currentPeriodIndex < s.fidePeriods.size - 1) {
                val nextIdx = p.currentPeriodIndex + 1
                val nextPeriod = s.fidePeriods[nextIdx]
                nextTime += nextPeriod.timeMs
                periodIdx = nextIdx
                flagged = true
                activePeriod = nextPeriod
            }
            // Non-Fischer periods can optionally carry a per-move delay (US Chess Delay-style), reusing the
            // same generic delayRemainingMs mechanism US_DELAY uses. hasDelay distinguishes plain Sudden
            // Death (no delay) from US Delay -- both are non-Fischer, only the latter reads incrementMs.
            val nextDelay = if (!activePeriod.isFischer && activePeriod.hasDelay) activePeriod.incrementMs else 0L
            tempP.copy(timeRemainingMs = nextTime, currentPeriodIndex = periodIdx, hasFlagged = flagged, delayRemainingMs = nextDelay)
        }
        TimerMode.FAST_MOVE -> {
            // TRANSFER is handled above (needs to update the opponent too); only SHRINK/ACCELERATE reach here.
            if (s.fastMoveMode == FastMoveType.SHRINK) {
                val nextTurnStartTime = (p.initialTotalTimeMs - s.fastMoveShrinkDecrementMs).coerceAtLeast(s.fastMoveShrinkFloorMs)
                tempP.copy(timeRemainingMs = nextTurnStartTime, initialTotalTimeMs = nextTurnStartTime)
            } else {
                tempP.copy(timeRemainingMs = s.initialTimeMs, initialTotalTimeMs = s.initialTimeMs)
            }
        }
        else -> tempP
    }
    val newList = state.players.toMutableList().apply { this[playerIndex - 1] = newP }
    return state.copy(players = newList)
}

class ChessTimerViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepo = SettingsRepository(application)
    private val gameDao = GameDatabase.getDatabase(application).gameLogDao()
    private val converters = Converters()

    private val _settings = MutableStateFlow(ChessClockSettings())
    val settings: StateFlow<ChessClockSettings> = _settings.asStateFlow()
    private val _uiState = MutableStateFlow(createInitialState(_settings.value))
    val uiState: StateFlow<ChessClockState> = _uiState.asStateFlow()
    private var timerJob: Job? = null
    private var lastTickTime: Long = 0
    private var moveStartTime: Long = 0
    private val soundManager = SoundManager(application)
    private val voiceManager = VoiceManager(application)
    val bluetoothManager = BluetoothBoardManager(application)
    private var lastBeepSecond: Long = -1
    private var lastAutoSaveTime: Long = 0
    
    // Tracking for voice announcements to prevent repetition
    private val lastAnnouncedThreshold = mutableMapOf<Int, Long>() // PlayerIndex -> Threshold

    private val _gameHistory = MutableStateFlow<List<GameLog>>(emptyList())
    val gameHistory: StateFlow<List<GameLog>> = _gameHistory.asStateFlow()
    private var currentLog: GameLog? = null
    private var lastRandomRoll: Pair<Long, Long>? = null

    private val _hasSavedClock = MutableStateFlow(false)
    val hasSavedClock: StateFlow<Boolean> = _hasSavedClock.asStateFlow()

    private val _scoreboard = MutableStateFlow(ScoreboardSession())
    val scoreboard: StateFlow<ScoreboardSession> = _scoreboard.asStateFlow()

    init {
        viewModelScope.launch {
            val savedSettings = settingsRepo.settingsFlow.first()
            _settings.value = savedSettings
            soundManager.loadSounds(savedSettings)
            _uiState.value = createInitialState(savedSettings)
            
            val entities = gameDao.getRecentLogs(savedSettings.logHistoryLimit)
            _gameHistory.value = entities.map { converters.toGameLog(it) }

            _hasSavedClock.value = gameDao.getSavedClock() != null
        }
    }

    private fun createInitialState(settings: ChessClockSettings, reuseRandomRoll: Boolean = false): ChessClockState {
        val sharedRandomBase: Long
        val sharedRandomInc: Long
        val s1 = if (settings.differentSettingsPerPlayer) settings.p1Custom else settings.main

        if (((s1.mode == TimerMode.RANDOM) || (s1.mode == TimerMode.HIDDEN))) {
            val reused = lastRandomRoll
            if (reuseRandomRoll && reused != null) {
                sharedRandomBase = reused.first
                sharedRandomInc = reused.second
            } else {
                sharedRandomBase = try {
                    if (s1.roundedTime) {
                        (s1.randomMinTimeMs / 10_000..s1.randomMaxTimeMs / 10_000).random() * 10_000L
                    } else {
                        (s1.randomMinTimeMs / 1000..s1.randomMaxTimeMs / 1000).random() * 1000L
                    }
                } catch (_: Exception) { 600_000L }
                sharedRandomInc = try {
                    if (s1.roundedTime) {
                        (s1.randomMinIncMs / 1000..s1.randomMaxIncMs / 1000).random() * 1000L
                    } else {
                        (s1.randomMinIncMs / 100..s1.randomMaxIncMs / 100).random() * 100L
                    }
                } catch (_: Exception) { 0L }
                lastRandomRoll = sharedRandomBase to sharedRandomInc
            }
        } else {
            sharedRandomBase = 0
            sharedRandomInc = 0
            lastRandomRoll = null
        }

        fun initP(pSettings: PlayerSettings): PlayerState {
            if (pSettings.mode == TimerMode.RANDOM || pSettings.mode == TimerMode.HIDDEN) {
                return PlayerState(
                    timeRemainingMs = sharedRandomBase, 
                    secondaryTimeMs = sharedRandomInc,
                    initialTotalTimeMs = sharedRandomBase
                )
            }

            val bonus = if (pSettings.mode == TimerMode.FISHER && settings.fischerFideFirstMove) pSettings.incrementMs else 0
            return when (pSettings.mode) {
                TimerMode.MOVE_TIMER_STANDARD, TimerMode.MOVE_TIMER_SHARED -> PlayerState(timeRemainingMs = pSettings.moveTimeMs)
                TimerMode.MOVE_TIMER_SAVE_CAP -> PlayerState(timeRemainingMs = pSettings.moveTimeMs, secondaryTimeMs = 0)
                TimerMode.MOVE_TIMER_OVERTIME, TimerMode.MOVE_TIMER_GLOBAL, TimerMode.MOVE_TIMER_GLOBAL_SHARED -> PlayerState(timeRemainingMs = pSettings.moveTimeMs, secondaryTimeMs = pSettings.initialTimeMs)
                TimerMode.BYOYOMI_JAPANESE -> PlayerState(timeRemainingMs = pSettings.initialTimeMs, byoyomiPeriodsRemaining = pSettings.byoyomiPeriods)
                TimerMode.BYOYOMI_CANADIAN, TimerMode.BYOYOMI_PROGRESSIVE -> PlayerState(timeRemainingMs = pSettings.initialTimeMs, movesRemainingInPeriod = pSettings.byoyomiPeriods, currentByoyomiMovesGoal = pSettings.byoyomiPeriods)
                TimerMode.MOVE_COUNTS_UP -> PlayerState(timeRemainingMs = 0, moveCount = 0)
                TimerMode.MOVE_COUNTS_DOWN -> PlayerState(timeRemainingMs = 0, moveCount = pSettings.maxMoves)
                TimerMode.FIDE_PERIODS -> {
                    val first = pSettings.fidePeriods.firstOrNull() ?: FidePeriod()
                    PlayerState(timeRemainingMs = first.timeMs, currentPeriodIndex = 0)
                }
                TimerMode.PHASES -> {
                    val first = pSettings.phases.firstOrNull() ?: GamePhase()
                    PlayerState(timeRemainingMs = first.timeMs, currentPhaseIndex = 0)
                }
                TimerMode.GONG -> PlayerState(timeRemainingMs = pSettings.gongReflectionMs, isGongReflectionPhase = true)
                TimerMode.FAST_MOVE -> PlayerState(
                    timeRemainingMs = if (pSettings.fastMoveMode == FastMoveType.TRANSFER) pSettings.moveTimeMs else pSettings.initialTimeMs,
                    initialTotalTimeMs = pSettings.initialTimeMs
                )
                else -> PlayerState(timeRemainingMs = pSettings.initialTimeMs + bonus)
            }
        }
        
        val playerList = mutableListOf<PlayerState>()
        val count = settings.numberOfPlayers.coerceIn(1, 4)
        for (i in 1..count) {
            val pSettings = when(i) {
                1 -> if (settings.differentSettingsPerPlayer) settings.p1Custom else settings.main
                2 -> if (settings.differentSettingsPerPlayer) settings.p2Custom else settings.main
                3 -> if (settings.differentSettingsPerPlayer) settings.p3Custom else settings.main
                4 -> if (settings.differentSettingsPerPlayer) settings.p4Custom else settings.main
                else -> settings.main
            }
            playerList.add(initP(pSettings))
        }

        val globalTime = when (s1.mode) {
            TimerMode.CHRONO_COUNTDOWN -> s1.initialTimeMs
            TimerMode.PHASES -> s1.phases.firstOrNull()?.timeMs ?: 0
            else -> 0L
        }
        return ChessClockState(players = playerList, globalTimeMs = globalTime)
    }

    fun startOrSwitch(playerIndex: Int, boardNotation: String? = null) {
        val currentState = _uiState.value
        if (currentState.isArbitreMode) return

        val s = getPlayerSettings(playerIndex)

        if (currentLog == null) {
            val playersInitialStates = _uiState.value.players.map { it.toProxy() }
            currentLog = GameLog(
                settings = settings.value,
                initialPlayerStates = playersInitialStates
            )
            addEvent(GameEvent(eventType = "START", detail = "Game started by P$playerIndex"))
        }

        if (s.mode == TimerMode.GONG) {
            if (currentState.isPaused) resume()
            return
        }

        if (s.mode == TimerMode.PHASES) {
            if (currentState.isPaused) { resume(); return }
            val p1 = currentState.players[0]
            val canAdvance = p1.isInInterPhasePause || p1.isOutOfTime || settings.value.allowPhaseSkip
            if (canAdvance) { _uiState.update { startPhaseTransition(it) } }
            return
        }

        val flagMode = settings.value.flagBehavior
        val playerCount = settings.value.numberOfPlayers.coerceAtLeast(1)
        val outOfTimeCount = currentState.players.count { it.isOutOfTime }
        if (flagMode == FlagBehavior.FREEZE && outOfTimeCount > 0) return
        if (flagMode == FlagBehavior.FLAG && playerCount > 1 && outOfTimeCount >= playerCount - 1) return

        if (currentState.activePlayer == playerIndex) {
            val p = currentState.players.getOrNull(playerIndex - 1) ?: return
            if (p.isInInterPhasePause) {
                advancePhase(playerIndex)
                return
            }
        }

        if (s.mode == TimerMode.MOVE_TIMER_SHARED) return
        if (s.mode.name.startsWith("CHRONO") && settings.value.isOneForAll) return
        if (currentState.activePlayer != null && currentState.activePlayer != playerIndex) return

        if (currentState.isPaused && currentState.activePlayer != null) { resume(); return }
        
        val nextPlayer = if (playerCount > 1) (playerIndex % playerCount) + 1 else 1
        
        if (currentState.activePlayer == null) {
            moveStartTime = System.currentTimeMillis(); startClock(nextPlayer)
            if (settings.value.playSwitchSound) soundManager.playSwitch()
            addEvent(GameEvent(eventType = "INITIAL_PRESS", playerIndex = playerIndex, detail = "P$nextPlayer clock started"))
        } else {
            val p = currentState.players.getOrNull(playerIndex - 1) ?: return
            addEvent(GameEvent(eventType = "MOVE", playerIndex = playerIndex, timeRemainingMs = p.timeRemainingMs, moveCount = p.moveCount + 1, moveNotation = boardNotation))
            applyPostMoveLogic(playerIndex, System.currentTimeMillis() - moveStartTime)
            moveStartTime = System.currentTimeMillis(); startClock(nextPlayer)
            if (settings.value.playSwitchSound) soundManager.playSwitch()
        }
    }

    private fun addEvent(event: GameEvent) {
        currentLog = currentLog?.copy(events = currentLog!!.events + event)
    }

    private fun startClock(playerIndex: Int) {
        timerJob?.cancel()
        timerJob = null
        lastBeepSecond = -1
        _uiState.update { state ->
            val s = getPlayerSettings(playerIndex)
            val newPlayers = state.players.mapIndexed { idx, p ->
                if (idx + 1 == playerIndex && s.mode == TimerMode.US_DELAY) p.copy(delayRemainingMs = s.incrementMs) else p
            }
            state.copy(activePlayer = playerIndex, isPaused = false, players = newPlayers)
        }
        lastTickTime = System.currentTimeMillis()
        timerJob = viewModelScope.launch { 
            while (isActive) { 
                try {
                    delay(10.milliseconds)
                    tick()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("ChessTimerViewModel", "tick() threw, clock may appear stuck", e)
                    yield()
                }
            } 
        }
    }

    private fun tick() {
        val now = System.currentTimeMillis(); val delta = now - lastTickTime; lastTickTime = now
        
        // Auto-save check: every 5 seconds while running
        if (now - lastAutoSaveTime >= 5000) {
            lastAutoSaveTime = now
            saveClockForLater()
        }

        _uiState.update { state ->
            val settings = _settings.value
            if (state.isPaused) return@update state
            val active = state.activePlayer ?: 1
            val s = getPlayerSettings(active)
            
            if (s.mode == TimerMode.PHASES) {
                val p1 = state.players[0]
                if (p1.isInInterPhasePause) {
                    val newPause = (p1.pauseTimeRemainingMs - delta).coerceAtLeast(0)
                    if (newPause <= 0) return@update performPhaseAdvance(state, 1)
                    return@update state.copy(players = state.players.toMutableList().apply { this[0] = p1.copy(pauseTimeRemainingMs = newPause) })
                }
                if (p1.isOutOfTime) {
                    // autoAdvance is off and this phase's time is already up: frozen, waiting for a
                    // manual tap (startOrSwitch) or allowPhaseSkip -- not a real "out of time"/game-over,
                    // just a visual + audio cue that this phase is done. See AUDIT.md.
                    return@update state
                }

                val currentPhase = s.phases.getOrNull(p1.currentPhaseIndex) ?: GamePhase()
                val newGlobal = (state.globalTimeMs - delta).coerceAtLeast(0)
                if (newGlobal <= 0 && currentPhase.autoAdvance) {
                    return@update startPhaseTransition(state)
                }
                val newP1 = p1.copy(isOutOfTime = newGlobal <= 0)
                handleAudio(p1.copy(timeRemainingMs = state.globalTimeMs), newP1.copy(timeRemainingMs = newGlobal), settings, 1)
                handleVoice(p1.copy(timeRemainingMs = state.globalTimeMs), newP1.copy(timeRemainingMs = newGlobal), settings, 1)
                return@update state.copy(globalTimeMs = newGlobal, players = state.players.toMutableList().apply { this[0] = newP1 })
            }


            if (s.mode == TimerMode.MOVE_TIMER_SHARED) {
                val p1 = state.players[0]
                val newTime = p1.timeRemainingMs - delta
                return@update if (newTime <= 0) {
                    val next = (active % settings.numberOfPlayers) + 1
                    val resetPlayers = state.players.map { it.copy(timeRemainingMs = s.moveTimeMs) }
                    val flaggedP = p1.copy(timeRemainingMs = 0, isOutOfTime = true)
                    handleAudio(p1, flaggedP, settings, active)
                    handleVoice(p1, flaggedP, settings, active)
                    state.copy(players = resetPlayers, activePlayer = next, cycleCount = if (next == 1) state.cycleCount + 1 else state.cycleCount)
                } else {
                    handleAudio(p1, p1.copy(timeRemainingMs = newTime), settings, active)
                    state.copy(players = state.players.map { it.copy(timeRemainingMs = newTime) })
                }
            }
            if (s.mode == TimerMode.MOVE_TIMER_GLOBAL_SHARED) {
                val pActive = state.players[active - 1]
                val newMoveTime = pActive.timeRemainingMs - delta
                val newGlobalTime = (pActive.secondaryTimeMs - delta).coerceAtLeast(0)
                val outMove = newMoveTime <= 0
                val outGlobal = newGlobalTime <= 0
                val newPlayers = state.players.mapIndexed { idx, p ->
                    if (idx + 1 == active) p.copy(timeRemainingMs = newMoveTime, secondaryTimeMs = newGlobalTime, isOutOfTime = outMove || outGlobal)
                    else p.copy(secondaryTimeMs = newGlobalTime, isOutOfTime = outGlobal)
                }
                handleAudio(pActive, newPlayers[active - 1], settings, active)
                handleVoice(pActive, newPlayers[active - 1], settings, active)
                return@update state.copy(players = newPlayers, isPaused = settings.flagBehavior == FlagBehavior.FREEZE && (outMove || outGlobal))
            }
            if (s.mode == TimerMode.HOURGLASS) {
                val share = delta / (settings.numberOfPlayers - 1).coerceAtLeast(1)
                val activeP = state.players[active - 1]
                val (newTime, isOut, isNeg) = applyFlagBehaviorDelta(activeP.timeRemainingMs, activeP.isOutOfTime, activeP.isNegative, delta, settings.flagBehavior)
                val newActiveP = activeP.copy(timeRemainingMs = newTime, isOutOfTime = isOut, isNegative = isNeg)
                handleAudio(activeP, newActiveP, settings, active)
                handleVoice(activeP, newActiveP, settings, active)
                val newPlayers = state.players.mapIndexed { idx, p ->
                    if (idx + 1 == active) newActiveP else p.copy(timeRemainingMs = p.timeRemainingMs + share)
                }
                return@update state.copy(players = newPlayers, isPaused = settings.flagBehavior == FlagBehavior.FREEZE && isOut)
            }
            if (s.mode == TimerMode.CHRONO_COUNTDOWN) {
                val p1 = state.players[0]
                val (newGlobal, isOut, isNeg) = applyFlagBehaviorDelta(state.globalTimeMs, p1.isOutOfTime, p1.isNegative, delta, settings.flagBehavior)
                val oldForAudio = p1.copy(timeRemainingMs = state.globalTimeMs)
                val newForAudio = p1.copy(timeRemainingMs = newGlobal, isOutOfTime = isOut, isNegative = isNeg)
                handleAudio(oldForAudio, newForAudio, settings, 1)
                handleVoice(oldForAudio, newForAudio, settings, 1)
                return@update state.copy(globalTimeMs = newGlobal, players = state.players.map { it.copy(isOutOfTime = isOut, isNegative = isNeg) }, isPaused = settings.flagBehavior == FlagBehavior.FREEZE && isOut)
            }
            if (s.mode == TimerMode.CHRONO_COUNTUP) return@update state.copy(globalTimeMs = state.globalTimeMs + delta)

            if (s.mode == TimerMode.GONG) {
                val isSim = s.gongSimultaneous
                val playersToUpdate = if (isSim) (1..settings.numberOfPlayers).toList() else listOf(active)
                var newState = state
                for (idx in playersToUpdate) {
                    val p = newState.players[idx - 1]
                    var newTime = p.timeRemainingMs - delta
                    var isReflection = p.isGongReflectionPhase
                    var nextActiveIdx = newState.activePlayer ?: 1
                    var moveCount = p.moveCount

                    if (newTime <= 0) {
                        if (isReflection) {
                            newTime = s.gongMoveMs
                            isReflection = false
                            if (idx == active) soundManager.playGong()
                        } else {
                            newTime = s.gongReflectionMs
                            isReflection = true
                            moveCount++
                            if (!isSim) { nextActiveIdx = (active % settings.numberOfPlayers) + 1 }
                        }
                    }
                    val updatedP = p.copy(timeRemainingMs = newTime, isGongReflectionPhase = isReflection, moveCount = moveCount)
                    newState = newState.copy(players = newState.players.toMutableList().apply { this[idx - 1] = updatedP }, activePlayer = nextActiveIdx)
                }
                return@update newState
            }

            val pActive = state.players[active - 1]
            val newStateP = tickPlayer(pActive, delta, s, settings)
            handleAudio(pActive, newStateP, settings, active)
            handleVoice(pActive, newStateP, settings, active)
            
            val newFirstFlag = if (newStateP.isOutOfTime && state.firstToFlag == null) active else state.firstToFlag
            val newPlayers = state.players.toMutableList().apply { this[active - 1] = newStateP }
            
            state.copy(
                players = newPlayers, 
                isPaused = settings.flagBehavior == FlagBehavior.FREEZE && newStateP.isOutOfTime,
                firstToFlag = newFirstFlag
            )
        }
    }

    private fun handleAudio(oldState: PlayerState, newState: PlayerState, settings: ChessClockSettings, playerIdx: Int) {
        if (newState.isOutOfTime && !oldState.isOutOfTime) {
            if (settings.tripleBeepTimeUp) soundManager.playTripleBeep()
            addEvent(GameEvent(eventType = "FLAG", playerIndex = playerIdx, detail = "Time Up"))
            return
        }

        val seconds = newState.timeRemainingMs / 1000
        if (seconds != lastBeepSecond) {
            // Audio Beep
            val audioThreshold = when(settings.beepThreshold) {
                BeepCountdownThreshold.OFF -> 0
                BeepCountdownThreshold.THREE_SEC -> 3
                BeepCountdownThreshold.TEN_SEC -> 10
            }
            if (seconds in 1L..audioThreshold.toLong()) {
                soundManager.playShortBeep()
            }

            lastBeepSecond = seconds
        }
    }

    private fun handleVoice(oldState: PlayerState, newState: PlayerState, settings: ChessClockSettings, playerIdx: Int) {
        if (!settings.voiceAnnouncementsEnabled) return

        val vol = settings.voiceVolume
        // 1. Time's Up
        if (newState.isOutOfTime && !oldState.isOutOfTime) {
            voiceManager.speak("Time is up for Player $playerIdx", vol)
            return
        }

        // 2. Thresholds (1m, 30s, 10s)
        val time = newState.timeRemainingMs
        val thresholds = listOf(60000L, 30000L, 10000L)
        for (t in thresholds) {
            if (oldState.timeRemainingMs > t && time <= t) {
                val lastT = lastAnnouncedThreshold[playerIdx] ?: 0L
                if (lastT != t) {
                    val label = if (t >= 60000L) "1 minute" else "${t/1000} seconds"
                    voiceManager.speak("$label remaining", vol)
                    lastAnnouncedThreshold[playerIdx] = t
                }
            }
        }

        // 3. FIDE Periods
        if (newState.currentPeriodIndex > oldState.currentPeriodIndex) {
            voiceManager.speak("Period ${newState.currentPeriodIndex + 1}", vol)
        }

        // 4. Byoyomi Entry
        if (newState.isInByoyomi && !oldState.isInByoyomi) {
            voiceManager.speak("Entering Byoyomi", vol)
        }

        // 5. Phases
        if (newState.currentPhaseIndex > oldState.currentPhaseIndex) {
            val s = getPlayerSettings(playerIdx)
            val phaseName = s.phases.getOrNull(newState.currentPhaseIndex)?.name ?: "Next Phase"
            voiceManager.speak(phaseName, vol)
        }

        // 6. Gong Mode
        if (getPlayerSettings(playerIdx).mode == TimerMode.GONG) {
            if (oldState.isGongReflectionPhase && !newState.isGongReflectionPhase) {
                voiceManager.speak("Move", vol)
            } else if (!oldState.isGongReflectionPhase && newState.isGongReflectionPhase) {
                voiceManager.speak("Reflect", vol)
            }
        }
    }

    override fun onCleared() {
        soundManager.release()
        voiceManager.release()
        bluetoothManager.disconnect()
    }

    private fun applyPostMoveLogic(playerIndex: Int, timeSpentOnMove: Long) {
        _uiState.update { state ->
            val s = getPlayerSettings(playerIndex)
            computePostMoveState(state, playerIndex, timeSpentOnMove, _settings.value, s)
        }
    }

    fun pause() { 
        timerJob?.cancel()
        timerJob = null
        _uiState.update { it.copy(isPaused = true) }
        addEvent(GameEvent(eventType = "PAUSE"))
    }
    fun resume() {
        val active = _uiState.value.activePlayer ?: 1
        if (currentLog == null) {
            val playersInitialStates = _uiState.value.players.map { it.toProxy() }
            currentLog = GameLog(
                settings = settings.value,
                initialPlayerStates = playersInitialStates
            )
            addEvent(GameEvent(eventType = "START", detail = "Game started by P$active"))
        }
        addEvent(GameEvent(eventType = "RESUME"))
        val s = _settings.value
        val mode = getPlayerSettings(active).mode
        if (mode == TimerMode.MOVE_TIMER_SHARED || (mode.name.startsWith("CHRONO") && s.isOneForAll) || mode == TimerMode.PHASES) { startClock(active); return }
        if (_uiState.value.players.none { it.isOutOfTime }) startClock(active)
    }
    fun reset() { 
        timerJob?.cancel()
        timerJob = null
        currentLog?.let { log ->
            addEvent(GameEvent(eventType = "RESET"))
            viewModelScope.launch {
                val limit = _settings.value.logHistoryLimit
                val durationLimit = _settings.value.logDurationLimit
                
                gameDao.insertLog(converters.fromGameLog(log))
                
                // 1. Cleanup by count (skip if infinite)
                if (limit != -1) {
                    gameDao.trimLogs(limit)
                }
                
                // 2. Cleanup by duration
                if (durationLimit != LogDurationLimit.INFINITE) {
                    val days = when(durationLimit) {
                        LogDurationLimit.ONE_DAY -> 1
                        LogDurationLimit.ONE_WEEK -> 7
                        LogDurationLimit.ONE_MONTH -> 30
                        LogDurationLimit.SIX_MONTHS -> 180
                        LogDurationLimit.ONE_YEAR -> 365
                    }
                    if (days > 0) {
                        val threshold = System.currentTimeMillis() - (days * 24 * 3600 * 1000L)
                        gameDao.deleteLogsOlderThan(threshold)
                    }
                }

                val finalLimit = if (limit == -1) 10000 else limit
                val entities = gameDao.getRecentLogs(finalLimit)
                _gameHistory.value = entities.map { converters.toGameLog(it) }
            }
        }
        currentLog = null
        lastAnnouncedThreshold.clear()
        _uiState.update { createInitialState(_settings.value, reuseRandomRoll = true) }
    }
    fun updateSettings(
        newSettings: ChessClockSettings,
        initialStates: List<PlayerStateProxy>? = null,
        logsToImport: List<GameLog>? = null,
        scoreboardToImport: ScoreboardSession? = null,
        isImport: Boolean = false
    ) {
        val oldSettings = _settings.value
        // Shadow the parameter so every use below this line sees the sanitized value; the named
        // argument at call sites still binds by the original parameter name, so this is transparent
        // to callers.
        @Suppress("NAME_SHADOWING")
        val newSettings = if (isImport) sanitizeImportedSettings(getApplication(), newSettings) else newSettings
        _settings.value = newSettings
        
        scoreboardToImport?.let { _scoreboard.value = it }
        if (oldSettings.customBeepUri != newSettings.customBeepUri ||
            oldSettings.customGongUri != newSettings.customGongUri ||
            oldSettings.customFinalBeepUri != newSettings.customFinalBeepUri ||
            oldSettings.customSwitchUri != newSettings.customSwitchUri ||
            oldSettings.soundsVolume != newSettings.soundsVolume ||
            oldSettings.voiceVolume != newSettings.voiceVolume ||
            oldSettings.audioOutputMedia != newSettings.audioOutputMedia) {
            soundManager.loadSounds(newSettings)
        }

        val coreChanged = oldSettings.main != newSettings.main ||
                oldSettings.p1Custom != newSettings.p1Custom ||
                oldSettings.p2Custom != newSettings.p2Custom ||
                // p3Custom/p4Custom were missing here: changing only P4's settings (4-player,
                // differentSettingsPerPlayer) silently failed to refresh the clock until some other
                // tracked field also changed. See AUDIT.md §7.2.
                oldSettings.p3Custom != newSettings.p3Custom ||
                oldSettings.p4Custom != newSettings.p4Custom ||
                oldSettings.numberOfPlayers != newSettings.numberOfPlayers ||
                oldSettings.differentSettingsPerPlayer != newSettings.differentSettingsPerPlayer ||
                oldSettings.gameType != newSettings.gameType ||
                initialStates != null

        if (coreChanged) {
            timerJob?.cancel()
            timerJob = null
            if (initialStates != null) {
                // A "Last Games" preset carries its own already-rolled RANDOM/HIDDEN time in
                // initialStates -- without updating the cache reset() reuses, Reset would silently
                // revert to whatever RANDOM/HIDDEN roll was cached from an earlier, unrelated game.
                val s1 = if (newSettings.differentSettingsPerPlayer) newSettings.p1Custom else newSettings.main
                if ((s1.mode == TimerMode.RANDOM || s1.mode == TimerMode.HIDDEN) && initialStates.isNotEmpty()) {
                    val first = initialStates.first()
                    lastRandomRoll = first.initialTotalTimeMs to first.secondaryTimeMs
                }
                _uiState.value = ChessClockState(players = initialStates.map { it.toState() })
            } else {
                _uiState.value = createInitialState(newSettings)
            }
        }

        logsToImport?.let { logs ->
            viewModelScope.launch {
                // logsToImport only ever originates from a QR/JSON/ZIP import (see call sites), so each
                // log's embedded settings are just as untrusted as the top-level `newSettings` above and
                // must go through the same sanitization before being persisted. Without this, a crafted
                // GameLog.settings.notebookNotes/custom*Uri survives unsanitized in Room and later becomes
                // the live settings verbatim the moment the user picks it from PresetsScreen's "Last
                // Games" tab (onPresetSelected -> updateSettings(set, states), isImport defaults to
                // false there) -- silently reopening the AUDIT.md §3 HIGH finding. See AUDIT.md §6.
                val app = getApplication<Application>()
                logs.forEach { log ->
                    val sanitizedLog = log.copy(settings = sanitizeImportedSettings(app, log.settings))
                    gameDao.insertLog(converters.fromGameLog(sanitizedLog))
                }
                val limit = _settings.value.logHistoryLimit
                val finalLimit = if (limit == -1) 10000 else limit
                val entities = gameDao.getRecentLogs(finalLimit)
                _gameHistory.value = entities.map { converters.toGameLog(it) }
            }
        }

        viewModelScope.launch { settingsRepo.saveSettings(newSettings) }
    }
    fun toggleArbitreMode() { if (_uiState.value.isPaused) _uiState.update { it.copy(isArbitreMode = !it.isArbitreMode) } }
    
    fun previewSwitchSound() = soundManager.playSwitch()
    fun previewBeep() = soundManager.playShortBeep()
    fun previewFinalBeep() = soundManager.playTripleBeep()
    fun previewGong() = soundManager.playGong()
    fun previewVoice() = voiceManager.speak("Testing voice volume", settings.value.voiceVolume)

    fun clearAllLogs() {
        viewModelScope.launch {
            gameDao.clearAllLogs()
            _gameHistory.value = emptyList()
        }
    }

    fun resetAllSettings() {
        val defaultSettings = ChessClockSettings()
        updateSettings(defaultSettings)
    }

    fun adjustTime(playerIndex: Int, amountMs: Long) {
        if (!_uiState.value.isArbitreMode) return
        _uiState.update { state ->
            val newList = state.players.mapIndexed { idx, p ->
                if (idx + 1 == playerIndex) p.copy(timeRemainingMs = (p.timeRemainingMs + amountMs).coerceAtLeast(0))
                else p
            }
            state.copy(players = newList)
        }
    }

    private fun startPhaseTransition(state: ChessClockState): ChessClockState {
        val s = getPlayerSettings(1)
        val p = state.players[0]
        val currentPhase = s.phases.getOrNull(p.currentPhaseIndex) ?: GamePhase()
        if (currentPhase.flagOnEnd) {
            val newList = state.players.toMutableList().apply { this[0] = p.copy(isOutOfTime = true, timeRemainingMs = 0) }
            return state.copy(players = newList, globalTimeMs = 0)
        }
        if (settings.value.pauseBetweenPhasesMs > 0) {
            val pausedPlayer = p.copy(isInInterPhasePause = true, pauseTimeRemainingMs = settings.value.pauseBetweenPhasesMs)
            val newList = state.players.toMutableList().apply { this[0] = pausedPlayer }
            return state.copy(players = newList)
        }
        return performPhaseAdvance(state, 1)
    }

    private fun performPhaseAdvance(state: ChessClockState, playerIndex: Int): ChessClockState {
        val s = getPlayerSettings(playerIndex)
        val p = state.players[playerIndex - 1]
        var nextIdx = p.currentPhaseIndex + 1
        if (nextIdx >= s.phases.size) {
            nextIdx = if (settings.value.loopPhases) {
                0
            } else {
                val newList = state.players.toMutableList().apply { this[playerIndex - 1] = p.copy(isOutOfTime = true, timeRemainingMs = 0) }
                return state.copy(players = newList, globalTimeMs = 0)
            }
        }
        val nextPhase = s.phases[nextIdx]
        val advancedPlayer = p.copy(currentPhaseIndex = nextIdx, timeRemainingMs = nextPhase.timeMs, isOutOfTime = false, isInInterPhasePause = false)
        val newList = state.players.toMutableList().apply { this[playerIndex - 1] = advancedPlayer }
        return state.copy(players = newList, globalTimeMs = nextPhase.timeMs)
    }

    fun advancePhase(playerIndex: Int) { _uiState.update { performPhaseAdvance(it, playerIndex) } }

    fun saveClockForLater() {
        val json = Json { ignoreUnknownKeys = true }
        val settingsStr = json.encodeToString(ChessClockSettings.serializer(), settings.value)
        val stateStr = json.encodeToString(ChessClockStateProxy.serializer(), uiState.value.toProxy())
        
        viewModelScope.launch {
            gameDao.saveClock(com.masterclock.app.data.SavedClockEntity(
                settingsJson = settingsStr,
                stateJson = stateStr
            ))
            _hasSavedClock.value = true
        }
    }

    fun resumeSavedClock() {
        viewModelScope.launch {
            val saved = gameDao.getSavedClock() ?: return@launch
            val json = Json { ignoreUnknownKeys = true }
            try {
                val resumedSettings = json.decodeFromString(ChessClockSettings.serializer(), saved.settingsJson)
                val proxy = json.decodeFromString(ChessClockStateProxy.serializer(), saved.stateJson)
                
                _settings.value = resumedSettings
                _uiState.value = proxy.toState()
                
                gameDao.clearSavedClock()
                _hasSavedClock.value = false
            } catch (e: Exception) {
                Log.w("ChessTimerViewModel", "Failed to resume saved clock, discarding it", e)
                gameDao.clearSavedClock()
                _hasSavedClock.value = false
            }
        }
    }

    fun recordBoardMove(notation: String) {
        val currentState = _uiState.value
        val active = currentState.activePlayer
        if (active != null) {
            val p = currentState.players[active - 1]
            addEvent(GameEvent(
                eventType = "BOARD_MOVE",
                playerIndex = active,
                timeRemainingMs = p.timeRemainingMs,
                moveCount = p.moveCount,
                moveNotation = notation,
                detail = "Move from board: $notation"
            ))
            
            if (settings.value.autoSwitchOnBoardMove && !currentState.isPaused) {
                startOrSwitch(active, boardNotation = notation)
            }
        } else {
            addEvent(GameEvent(eventType = "BOARD_DATA", detail = "Data while paused: $notation"))
        }
    }

    private fun getPlayerSettings(playerIndex: Int): PlayerSettings {
        return if (settings.value.differentSettingsPerPlayer) {
            when(playerIndex) {
                1 -> settings.value.p1Custom
                2 -> settings.value.p2Custom
                3 -> settings.value.p3Custom
                4 -> settings.value.p4Custom
                else -> settings.value.main
            }
        } else settings.value.main
    }

    fun updateScoreboardNames(p1: String, p2: String) {
        _scoreboard.update { it.copy(player1Name = p1, player2Name = p2) }
    }

    fun addScoreboardGame(result: String) {
        _scoreboard.update { current ->
            val newList = current.games + ScoreboardGame(result = result)
            current.copy(games = newList)
        }
    }

    fun resetScoreboard() {
        _scoreboard.value = ScoreboardSession()
    }
}
