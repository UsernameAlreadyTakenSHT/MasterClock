package com.masterclock.app.logic

import android.app.Application
import android.os.SystemClock
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
    SUDDEN_DEATH, FISCHER, BRONSTEIN, US_DELAY,
    MOVE_TIMER_STANDARD, MOVE_TIMER_SAVE_CAP, MOVE_TIMER_OVERTIME, MOVE_TIMER_GLOBAL, MOVE_TIMER_SHARED, MOVE_TIMER_GLOBAL_SHARED,
    HOURGLASS, BYOYOMI_JAPANESE, BYOYOMI_CANADIAN, BYOYOMI_PROGRESSIVE,
    CHRONO_COUNTDOWN, CHRONO_COUNTUP, MOVE_COUNTS_UP, MOVE_COUNTS_DOWN,
    FIDE_PERIODS, PHASES, RANDOM, HIDDEN, GONG, FAST_MOVE
}

/**
 * What a screen reader should hear when the clock changes state.
 *
 * A description of the event rather than a sentence: core has no Context and therefore no
 * resources, so the wording belongs to whichever UI renders it. Keeping it structured also lets
 * the announcement logic stay covered by plain unit tests, with no Android on the classpath.
 */
sealed interface ClockAnnouncement {
    data class OutOfTime(val playerIndex: Int) : ClockAnnouncement
    data object Paused : ClockAnnouncement
    /** [byoyomiPeriodsRemaining] is null outside byoyomi, and 0 when in it but out of periods. */
    data class ToMove(val playerIndex: Int, val byoyomiPeriodsRemaining: Int?) : ClockAnnouncement
}

/**
 * The state change worth announcing, or null when there is nothing to say yet.
 *
 * Deliberately a function of *state transitions only* -- whose turn it is, a flag, a pause, a
 * byoyomi period -- and never of the remaining time. A live region announces whenever its text
 * changes, so including the countdown would make the reader recite the seconds and drown out
 * everything else.
 */
fun clockAnnouncement(state: ChessClockState): ClockAnnouncement? {
    val flagged = state.firstToFlag
        ?: state.players.indexOfFirst { it.isOutOfTime }.takeIf { it >= 0 }?.plus(1)
    if (flagged != null) return ClockAnnouncement.OutOfTime(flagged)

    val active = state.activePlayer ?: return null
    if (state.isPaused) return ClockAnnouncement.Paused

    val player = state.players.getOrNull(active - 1) ?: return null
    return ClockAnnouncement.ToMove(
        playerIndex = active,
        byoyomiPeriodsRemaining = if (player.isInByoyomi) player.byoyomiPeriodsRemaining else null,
    )
}

/** Whether [clockAnnouncement] is urgent enough to interrupt whatever the reader is saying. */
fun isUrgentAnnouncement(state: ChessClockState): Boolean =
    state.firstToFlag != null || state.players.any { it.isOutOfTime }

/**
 * A duration split into the parts a screen reader should say, for the same reason as
 * [ClockAnnouncement]: the words live in the UI, the arithmetic lives here.
 *
 * Clock faces are formatted as "05:03", which TalkBack reads out digit by digit -- hard to follow
 * when the number changes every second. Components that are zero are reported as such so the UI
 * can drop them and avoid opening with "0 hours".
 */
data class SpokenDuration(
    val hours: Long,
    val minutes: Long,
    val seconds: Long,
    /** True once the clock has run out, where there is no duration left to say. */
    val isExpired: Boolean,
)

fun spokenDuration(ms: Long): SpokenDuration {
    if (ms <= 0L) return SpokenDuration(0, 0, 0, isExpired = true)
    val totalSeconds = ms / 1000
    return SpokenDuration(
        hours = totalSeconds / 3600,
        minutes = (totalSeconds % 3600) / 60,
        seconds = totalSeconds % 60,
        isExpired = false,
    )
}

/**
 * The name to show players for a mode.
 *
 * Deriving it from the constant mangles initialisms ("Us delay", "Fide periods"), so those are
 * spelled out rather than computed; everything else falls through to the generic prettifier.
 */
fun TimerMode.displayName(): String = when (this) {
    TimerMode.US_DELAY -> "US delay"
    TimerMode.FIDE_PERIODS -> "FIDE periods"
    TimerMode.BYOYOMI_JAPANESE -> "Byoyomi (Japanese)"
    TimerMode.BYOYOMI_CANADIAN -> "Byoyomi (Canadian)"
    TimerMode.BYOYOMI_PROGRESSIVE -> "Byoyomi (progressive)"
    else -> name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
}

@Serializable
enum class FlagBehavior { FREEZE, FLAG, NEGATIVE, REVERSE }
@Serializable
enum class AppThemeMode { LIGHT, DARK, AUTO }

/**
 * How wide each unit of a clock reading is written.
 *
 * Orthogonal to [ChessClockSettings.alwaysShowHours] and [ChessClockSettings.alwaysShowMinutes]:
 * those decide which units appear at all, this decides how each one is spelled. With hours forced
 * on at nine minutes past, the three give "00:09:08", "0:09:08" and "0:9:8".
 *
 * MINIMAL is the only one whose width changes as the clock runs -- 10 seconds is two characters
 * and 9 is one -- so the reading shifts on every crossing. That is the point of offering it, not
 * an oversight.
 */
enum class TimePadding {
    /** Every unit two digits: 01:09:08. */
    FULL,

    /** Leading unit as-is, the rest padded: 1:09:08. The usual clock convention. */
    STANDARD,

    /** Nothing padded: 1:9:8. */
    MINIMAL,
}

/**
 * Formats one unit of a clock reading under [padding].
 *
 * [isLeading] marks the largest unit actually being shown, which is the only one STANDARD and FULL
 * disagree about.
 */
/**
 * The padding a build actually renders with.
 *
 * Only Complete and Standard offer the choice; Lite, Mini and the E-Ink build are fixed on FULL,
 * which is what their clocks have always shown. Resolved here rather than by simply leaving the
 * setting out of their UI, because a settings file or QR share from another device carries the
 * stored value with it -- without this, importing one could put a Lite install into a format it
 * has no way to leave.
 */
fun ChessClockSettings.effectiveTimePadding(): TimePadding =
    if (FlavorConfig.hasFullSettingsTabs()) timePadding else TimePadding.FULL

fun padTimeUnit(value: Long, isLeading: Boolean, padding: TimePadding): String = when (padding) {
    TimePadding.MINIMAL -> value.toString()
    TimePadding.FULL -> value.toString().padStart(2, '0')
    TimePadding.STANDARD -> if (isLeading) value.toString() else value.toString().padStart(2, '0')
}

/**
 * A whole hours/minutes/seconds reading, joined by colons.
 *
 * [showHours] and [showMinutes] come from the caller because the two clocks decide them
 * differently: the main one honours the always-show settings, the log always shows minutes.
 */
fun formatClockReading(
    hours: Long,
    minutes: Long,
    seconds: Long,
    showHours: Boolean,
    showMinutes: Boolean,
    padding: TimePadding,
): String = buildString {
    if (showHours) append(padTimeUnit(hours, isLeading = true, padding = padding)).append(':')
    if (showMinutes) append(padTimeUnit(minutes, isLeading = !showHours, padding = padding)).append(':')
    append(padTimeUnit(seconds, isLeading = !showMinutes && !showHours, padding = padding))
}
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
enum class GameType {
    CHESS,
    DRAUGHTS,

    /**
     * Hidden from the game picker for now; see [GameType.isOfferable].
     *
     * Nothing about shogi is removed -- the KIF export, the rules document and the spoken labels
     * all stay, so a game already recorded as shogi still opens and still exports. It is only
     * withdrawn from the choices offered, because the electronic-board work that surrounds that
     * picker has no shogi to offer: no manufacturer makes a shogi board, and no protocol here
     * speaks one.
     */
    SHOGI;

    /**
     * Whether this game can be picked.
     *
     * A game already set stays offered even when withdrawn, or anyone who had chosen it would be
     * unable to see what they had chosen, let alone change it.
     */
    fun isOfferable(current: GameType): Boolean = this != SHOGI || current == SHOGI
}

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
    val boardPosition: List<String> = List(64) { "" },
    val boardVariant: BoardNoteVariant = BoardNoteVariant.CHESS,
)

/**
 * Which board a board note draws.
 *
 * Notes written before this existed have no variant stored and no geometry other than eight by
 * eight, so chess is the default and they keep working untouched -- the events blob is read with
 * unknown keys ignored, and a missing one falls back here.
 *
 * Draughts pieces use the letters the open board protocol settled on: `m`/`M` for men, `d`/`D` for
 * dames, lower case for black. Reusing them means one vocabulary across the app rather than a
 * second one invented for the notebook.
 */
@Serializable
enum class BoardNoteVariant(val side: Int) {
    CHESS(8),

    /** International draughts, and the default when draughts is picked: ten by ten. */
    DRAUGHTS_INTERNATIONAL(10),

    /** Russian, Brazilian and English draughts, all played on eight by eight. */
    DRAUGHTS_SMALL(8);

    val squareCount: Int get() = side * side

    val isDraughts: Boolean get() = this != CHESS
}

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
    val timePadding: TimePadding = TimePadding.FULL,
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
    // expires), on top of the normal auto/manual advance rules.
    val allowPhaseSkip: Boolean = false,
    val pauseBetweenPhasesMs: Long = 0,
    val notebookNotes: List<NotebookNote> = emptyList()
)

/** A time control the player saved themselves, listed alongside the built-in presets. */
@Serializable
data class SavedPreset(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val settings: ChessClockSettings,
)

/**
 * Copies just the time control from [preset] onto [current], leaving colours, sounds, display and
 * behaviour alone.
 *
 * Presets describe *how long players get*, not how the app should look or sound. Applying the
 * stored settings wholesale would drag a snapshot of every preference along with them — which is
 * what the built-in presets used to do, each being a fresh ChessClockSettings() with two fields
 * overridden, silently resetting the user's colours and audio on every tap.
 */
fun applyPresetTimeControl(current: ChessClockSettings, preset: ChessClockSettings): ChessClockSettings =
    current.copy(
        gameType = preset.gameType,
        main = preset.main,
        p1Custom = preset.p1Custom,
        p2Custom = preset.p2Custom,
        p3Custom = preset.p3Custom,
        p4Custom = preset.p4Custom,
        differentSettingsPerPlayer = preset.differentSettingsPerPlayer,
        numberOfPlayers = preset.numberOfPlayers,
        playerMapping = preset.playerMapping,
        multiPlayerLayout = preset.multiPlayerLayout,
        isOneForAll = preset.isOneForAll,
        flagBehavior = preset.flagBehavior,
        fischerFideFirstMove = preset.fischerFideFirstMove,
    )

/**
 * A time control reachable from a launcher shortcut.
 *
 * [id] carries its own source so the intent handler resolves one without guessing: "preset:<uuid>"
 * for the player's own presets, "game:<startTime>" for a recent game, "builtin:<key>" for the
 * blitz fallbacks below.
 */
data class ShortcutTarget(val id: String, val label: String, val settings: ChessClockSettings)

/**
 * Blitz fallbacks that pad the shortcut list on a fresh install, where there is neither a saved
 * preset nor any game history yet.
 *
 * These duplicate two of the 21 built-ins in PresetsScreen rather than reusing them, because those
 * are constructed inline in the composable and have no stable identity to key a shortcut on.
 */
val BUILTIN_SHORTCUT_PRESETS: List<ShortcutTarget> = listOf(
    ShortcutTarget(
        id = "builtin:fischer_3_2",
        label = "3 + 2",
        settings = ChessClockSettings(
            main = PlayerSettings(initialTimeMs = 180_000, incrementMs = 2_000, mode = TimerMode.FISCHER)
        ),
    ),
    ShortcutTarget(
        id = "builtin:fischer_15_10",
        label = "15 + 10",
        settings = ChessClockSettings(
            main = PlayerSettings(initialTimeMs = 900_000, incrementMs = 10_000, mode = TimerMode.FISCHER)
        ),
    ),
)

/** Short "5 min" / "3 min + 2s" label for a shortcut, falling back to the mode name. */
private fun shortcutLabelFor(settings: ChessClockSettings): String {
    val s = if (settings.differentSettingsPerPlayer) settings.p1Custom else settings.main

    // A drawn mode gets a fresh roll on every launch, so naming its configured base time would
    // promise a number the player will not actually see.
    if (s.mode == TimerMode.RANDOM || s.mode == TimerMode.HIDDEN) {
        val name = if (s.mode == TimerMode.RANDOM) "Random" else "Hidden"
        val low = s.randomMinTimeMs / 60_000
        val high = s.randomMaxTimeMs / 60_000
        return if (high > 0) "$name $low-$high min" else name
    }

    // The move-timer family ignores initialTimeMs entirely.
    if (s.mode == TimerMode.MOVE_TIMER_STANDARD ||
        s.mode == TimerMode.MOVE_TIMER_SHARED ||
        s.mode == TimerMode.MOVE_TIMER_GLOBAL_SHARED
    ) {
        if (s.moveTimeMs > 0) return "${s.moveTimeMs / 1000}s / move"
    }

    if (s.initialTimeMs <= 0L) {
        return s.mode.displayName()
    }
    val minutes = s.initialTimeMs / 60_000
    val seconds = (s.initialTimeMs % 60_000) / 1000
    val base = when {
        minutes == 0L -> "${seconds}s"
        seconds == 0L -> "$minutes min"
        else -> "${minutes}m${seconds}s"
    }
    val increment = s.incrementMs / 1000
    return if (increment > 0) "$base + ${increment}s" else base
}

/**
 * Picks the at most [max] time controls worth a launcher shortcut.
 *
 * Recent games come first because "same as last time" is the common case, the player's own presets
 * follow, and the blitz built-ins pad whatever is left so the list is never short. Recent games
 * contribute their settings only -- never their initialPlayerStates, which hold a pre-rolled RANDOM
 * time that would otherwise be replayed identically on every launch.
 */
fun buildShortcutTargets(
    history: List<GameLog>,
    presets: List<SavedPreset>,
    max: Int = 4,
): List<ShortcutTarget> {
    val targets = mutableListOf<ShortcutTarget>()
    history.sortedByDescending { it.startTime }.take(2).forEach { log ->
        targets += ShortcutTarget("game:${log.startTime}", shortcutLabelFor(log.settings), log.settings)
    }
    presets.sortedByDescending { it.createdAt }.forEach { preset ->
        targets += ShortcutTarget("preset:${preset.id}", preset.name, preset.settings)
    }
    targets += BUILTIN_SHORTCUT_PRESETS
    return targets.filter { it.label.isNotBlank() }.distinctBy { it.id }.take(max)
}

@Serializable
data class GameEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String,
    val playerIndex: Int? = null,
    val timeRemainingMs: Long? = null,
    val moveCount: Int? = null,
    val detail: String? = null,
    val moveNotation: String? = null, // For PGN (e.g., "e2e4")
    /**
     * Wall-clock time the player actually spent on this move.
     *
     * Only set on MOVE events, and only on games recorded from v0.8.13 onwards -- [moveDurations]
     * falls back to timestamp differences for older logs. Adding it here needs no Room migration
     * because events live in a JSON blob read with ignoreUnknownKeys.
     */
    val timeSpentMs: Long? = null
)

/**
 * What a move reported by a linked board should do to the clock.
 *
 * The board supplies notation and the clock supplies timing, and the two do not necessarily arrive
 * together: with [ChessClockSettings.autoSwitchOnBoardMove] off, the player still presses the clock
 * themselves, some moments after lifting the piece. Whichever arrives second completes the pair, so
 * a notation that cannot be used immediately is held rather than dropped -- otherwise every MOVE
 * event records `null` and an exported PGN is a column of "???".
 */
sealed interface BoardMoveOutcome {
    /**
     * Press the clock now, stamping the MOVE event with [notation].
     *
     * [playerIndex] is carried rather than looked up again by the caller: it is only ever produced
     * when a player is on move, so holding it here is what makes that guarantee readable instead of
     * something the caller has to assert.
     */
    data class SwitchNow(val notation: String, val playerIndex: Int) : BoardMoveOutcome

    /** Keep [notation] until the player presses the clock, and stamp that MOVE with it. */
    data class HoldForNextPress(val notation: String, val playerIndex: Int) : BoardMoveOutcome

    /** No clock is running, so there is no move to attach this to. Record it and move on. */
    data class NoGameRunning(val notation: String) : BoardMoveOutcome
}

fun boardMoveOutcome(
    notation: String,
    activePlayer: Int?,
    isPaused: Boolean,
    autoSwitchOnBoardMove: Boolean,
): BoardMoveOutcome = when {
    activePlayer == null -> BoardMoveOutcome.NoGameRunning(notation)
    // A paused clock must not be resumed by the board: pausing is deliberate, and a piece knocked
    // over while the players are away would otherwise restart the game behind their backs.
    autoSwitchOnBoardMove && !isPaused -> BoardMoveOutcome.SwitchNow(notation, activePlayer)
    else -> BoardMoveOutcome.HoldForNextPress(notation, activePlayer)
}

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
 * videoPath/custom*Uri are read and written to directly.
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
        // import must not be able to sneak past that restriction.
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
 * empty/inverted range.
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
 * The longest recorded competitive chess game ran 269 moves, so a log carrying more than this many
 * events is not a game anyone played. The cap matters because the log detail screen renders one
 * composable Row per move inside a scrolling Column rather than a lazy list: without it, a crafted
 * backup with a hundred thousand MOVE events freezes the app the moment the log is opened.
 */
const val MAX_IMPORTED_EVENTS_PER_LOG = 2_000

/** A month per move is already absurd; anything past it is a crafted value, not a slow player. */
private const val MAX_IMPORTED_DURATION_MS = 30L * 24 * 60 * 60 * 1000

/** Long enough for any real SAN/USI/PDN move, short enough not to be a payload. */
private const val MAX_MOVE_NOTATION_CHARS = 32

/**
 * Bounds the parts of an imported [GameLog] that [sanitizeImportedSettings] does not reach.
 *
 * Only `settings` used to be sanitized, which was the right call for the finding it answered (raw
 * file paths and note ids). But `events` and `initialPlayerStates` were passed through to Room
 * verbatim, and they now feed the statistics screen and its chart. Nothing there can escape the
 * sandbox -- [GameEvent] holds no path or URI -- so the exposure is denial of service and nonsense
 * arithmetic rather than data theft, which is why the limits below are generous rather than strict.
 */
internal fun sanitizeImportedLog(log: GameLog): GameLog = log.copy(
    events = log.events.take(MAX_IMPORTED_EVENTS_PER_LOG).map { event ->
        event.copy(
            // moveDurations() drops a MOVE with a null playerIndex while the log screen used to
            // keep it, so an out-of-range index desynchronised the two lists. Normalising here
            // means the display never has to guess.
            playerIndex = event.playerIndex?.takeIf { it in 1..4 },
            timeRemainingMs = event.timeRemainingMs?.coerceIn(0L, MAX_IMPORTED_DURATION_MS),
            timeSpentMs = event.timeSpentMs?.coerceIn(0L, MAX_IMPORTED_DURATION_MS),
            moveCount = event.moveCount?.coerceIn(0, MAX_IMPORTED_EVENTS_PER_LOG),
            moveNotation = event.moveNotation?.take(MAX_MOVE_NOTATION_CHARS),
        )
    },
    // The app supports at most four players, so a longer list can only come from outside it.
    initialPlayerStates = log.initialPlayerStates.take(4).map { state ->
        state.copy(timeRemainingMs = state.timeRemainingMs.coerceIn(0L, MAX_IMPORTED_DURATION_MS))
    },
)

/**
 * The scoreboard was the one import channel reaching the UI with no validation at all.
 *
 * Its games live in memory rather than in Room, so unlike [GameLog] -- whose id is a primary key,
 * and whose uniqueness the database therefore enforces -- nothing else stops two imported games
 * from carrying the same id. The scoreboard list is keyed on that id, and Compose throws on a
 * duplicate key, so a crafted session would crash the screen the moment it was opened.
 *
 * Fresh ids on the way in, which is the same treatment [sanitizeImportedSettings] gives
 * [NotebookNote.id]. No cap on the number of games: the list renders lazily, the session is never
 * persisted, and [MAX_IMPORT_TEXT_BYTES] already bounds how much can arrive -- a limit here would
 * be a number invented to look thorough, and would silently drop a real user's own tally.
 */
internal fun sanitizeImportedScoreboard(session: ScoreboardSession): ScoreboardSession = session.copy(
    id = java.util.UUID.randomUUID().toString(),
    games = session.games.map { game -> game.copy(id = java.util.UUID.randomUUID().toString()) },
)

/**
 * Pure per-player time-transition function: given the current [PlayerState] and how much time
 * elapsed, returns the next [PlayerState] for every timer mode except the multi-player-coupled ones
 * (PHASES/GONG/HOURGLASS/CHRONO_COUNTDOWN/CHRONO_COUNTUP/MOVE_TIMER_SHARED/MOVE_TIMER_GLOBAL_SHARED, handled inline in
 * [ChessTimerViewModel.tick]). Has no dependency on Android or ViewModel state, so it's called
 * directly from both [ChessTimerViewModel.tick] and [ChessTimerLogicTest]: the previous test file
 * duplicated this logic instead of exercising it, so a real bug here could drift undetected.
 */
/**
 * Shared countdown + flag logic used both by [tickPlayer]'s default branch and by every mode that
 * has its own early-return in [ChessTimerViewModel.tick] (PHASES, MOVE_TIMER_SHARED,
 * MOVE_TIMER_GLOBAL_SHARED, HOURGLASS, CHRONO_COUNTDOWN, CHRONO_COUNTUP). These modes used to
 * bypass FlagBehavior/audio/voice entirely by never calling tickPlayer at all.
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
 * Exposed at the top level for the same testability reason as [tickPlayer].
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
    // the opponent's transferred time was silently discarded.)
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
        TimerMode.FISCHER -> tempP.copy(timeRemainingMs = p.timeRemainingMs + s.incrementMs)
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

/** Tick rate while the smallest digit on screen is hundredths of a second. */
private const val FAST_TICK_MS = 10L

/** Tick rate the rest of the time: enough for tenths, and ten times cheaper. */
private const val SLOW_TICK_MS = 100L

/** How long a crash may cost a running game. */
private const val AUTO_SAVE_INTERVAL_MS = 15_000L

/**
 * What "unlimited" history actually means when reading it back.
 *
 * A ceiling has to exist somewhere: every row is decoded into memory, so the alternative is a
 * startup cost that grows without bound for as long as someone keeps playing.
 */
private const val MAX_UNLIMITED_HISTORY = 10_000

class ChessTimerViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepo = SettingsRepository(application)
    private val gameDao = GameDatabase.getDatabase(application).gameLogDao()
    private val converters = Converters()

    /**
     * One instance, because a Json builds and caches a serializer lookup and the saved clock was
     * constructing a fresh one every fifteen seconds to throw it away again.
     */
    private val json = Json { ignoreUnknownKeys = true }

    private val _settings = MutableStateFlow(ChessClockSettings())
    val settings: StateFlow<ChessClockSettings> = _settings.asStateFlow()
    private val _uiState = MutableStateFlow(createInitialState(_settings.value))
    val uiState: StateFlow<ChessClockState> = _uiState.asStateFlow()
    private var timerJob: Job? = null

    /**
     * Both are [SystemClock.elapsedRealtime], never wall-clock time.
     *
     * A clock measures how much time has passed, and wall-clock time does not answer that question:
     * it can be corrected by NTP, changed by the user, or adjusted by the phone at any moment. A
     * correction backwards makes the difference between two readings negative -- which would hand
     * the player on move however much time the phone had drifted -- and one forwards deducts it in
     * a single tick, which can flag someone who had minutes left.
     *
     * elapsedRealtime counts from boot, cannot be set, and keeps counting through sleep, so the
     * difference between two readings is the time that actually elapsed and nothing else. The
     * timestamps recorded in game logs stay on wall-clock time, because those really are dates.
     */
    private var lastTickTime: Long = 0
    private var moveStartTime: Long = 0

    /**
     * Notation reported by a linked board that no MOVE event has claimed yet. See
     * [BoardMoveOutcome]; the next clock press consumes it. Only ever holds the most recent one --
     * if the board reports twice before the player presses, the first is stale.
     */
    private var pendingBoardNotation: String? = null
    private val soundManager = SoundManager(application)
    private val voiceManager = VoiceManager(application)
    val bluetoothManager = BluetoothBoardManager(application)
    val usbBoardManager = UsbBoardManager(application)
    val bluetoothSerialBoardManager = BluetoothSerialBoardManager(application)
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

    private val _customPresets = MutableStateFlow<List<SavedPreset>>(emptyList())
    val customPresets: StateFlow<List<SavedPreset>> = _customPresets.asStateFlow()

    /**
     * Reads the game history back and decodes it, off the main thread.
     *
     * viewModelScope runs on Dispatchers.Main and [Converters.toGameLog] parses three JSON columns
     * per row, so decoding where the rows arrived meant deserialising the entire history on the UI
     * thread. At startup that is precisely when the first frame is due.
     *
     * The unlimited case is clamped here as well. SQLite reads a negative LIMIT as no limit at all,
     * so passing logHistoryLimit through unchanged made "unlimited" mean every game ever played --
     * on the one path that did it, while the two others had already settled on ten thousand. One
     * function now, so the three cannot drift again.
     */
    private suspend fun loadHistory(limit: Int): List<GameLog> {
        val entities = gameDao.getRecentLogs(if (limit == -1) MAX_UNLIMITED_HISTORY else limit)
        return withContext(Dispatchers.Default) { entities.map { converters.toGameLog(it) } }
    }

    /** Set once the init block below has finished; see [applyShortcut]. */
    private var isLoaded = false
    private var pendingShortcutId: String? = null

    init {
        // A notation waiting for a press must not outlive the board that supplied it. Held while
        // auto-switch is off, it is claimed by the player's next press -- and if the board goes out
        // of range or is unplugged in between, that press might be minutes later, or in another
        // game entirely. The move recorded would name something nobody played, in an exported PGN,
        // with nothing on screen to suggest anything had gone wrong.
        viewModelScope.launch {
            combine(
                bluetoothManager.connectionState,
                usbBoardManager.connectionState,
                bluetoothSerialBoardManager.connectionState,
            ) { states -> states.any { it is ConnectionState.Connected } }
                .distinctUntilChanged()
                .collect { anyBoardConnected -> if (!anyBoardConnected) pendingBoardNotation = null }
        }

        viewModelScope.launch {
            val savedSettings = settingsRepo.settingsFlow.first()
            _settings.value = savedSettings
            soundManager.loadSounds(savedSettings)
            _uiState.value = createInitialState(savedSettings)

            _gameHistory.value = loadHistory(savedSettings.logHistoryLimit)

            _hasSavedClock.value = gameDao.getSavedClock() != null
            _customPresets.value = settingsRepo.customPresetsFlow.first()

            isLoaded = true
            pendingShortcutId?.let { id ->
                pendingShortcutId = null
                applyShortcutNow(id)
            }
        }
    }

    /**
     * Applies the time control behind a launcher shortcut.
     *
     * The init block above loads settings, history and presets asynchronously and nothing exposes a
     * readiness flag, so a shortcut arriving at startup would be applied against defaults and then
     * silently overwritten when DataStore resolves. Holding the id until the load finishes makes
     * that ordering explicit rather than leaving it to composition timing.
     */
    fun applyShortcut(id: String) {
        if (!isLoaded) {
            pendingShortcutId = id
            return
        }
        applyShortcutNow(id)
    }

    private fun applyShortcutNow(id: String) {
        val target = resolveShortcut(id) ?: return
        updateSettings(applyPresetTimeControl(_settings.value, target))
    }

    /** A shortcut can outlive the preset or game it points at, hence the nullable result. */
    private fun resolveShortcut(id: String): ChessClockSettings? = when {
        id.startsWith("preset:") -> _customPresets.value.firstOrNull { "preset:${it.id}" == id }?.settings
        id.startsWith("game:") -> _gameHistory.value.firstOrNull { "game:${it.startTime}" == id }?.settings
        else -> BUILTIN_SHORTCUT_PRESETS.firstOrNull { it.id == id }?.settings
    }

    private fun persistCustomPresets(presets: List<SavedPreset>) {
        _customPresets.value = presets
        viewModelScope.launch { settingsRepo.saveCustomPresets(presets) }
    }

    /**
     * Snapshots the current time control under [name].
     *
     * notebookNotes holds paths to media in the app sandbox and the custom*Uri fields are content
     * URI grants -- neither means anything in a preset, and both would be dead weight to carry
     * around, so they are dropped here the same way the share path drops them.
     */
    fun saveCurrentAsPreset(name: String) {
        val snapshot = _settings.value.copy(
            notebookNotes = emptyList(),
            customGongUri = null,
            customBeepUri = null,
            customFinalBeepUri = null,
            customSwitchUri = null,
        )
        persistCustomPresets(_customPresets.value + SavedPreset(name = name.trim(), settings = snapshot))
    }

    fun renamePreset(id: String, name: String) {
        persistCustomPresets(_customPresets.value.map { if (it.id == id) it.copy(name = name.trim()) else it })
    }

    fun deletePreset(id: String) {
        persistCustomPresets(_customPresets.value.filterNot { it.id == id })
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

            val bonus = if (pSettings.mode == TimerMode.FISCHER && settings.fischerFideFirstMove) pSettings.incrementMs else 0
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
            moveStartTime = SystemClock.elapsedRealtime(); startClock(nextPlayer)
            if (settings.value.playSwitchSound) soundManager.playSwitch()
            addEvent(GameEvent(eventType = "INITIAL_PRESS", playerIndex = playerIndex, detail = "P$nextPlayer clock started"))
        } else {
            val p = currentState.players.getOrNull(playerIndex - 1) ?: return
            val timeSpentOnMove = SystemClock.elapsedRealtime() - moveStartTime
            // A press with no notation of its own claims whatever the board reported since the last
            // move, which is how the notation reaches the PGN when auto-switch is off.
            val notation = boardNotation ?: pendingBoardNotation
            pendingBoardNotation = null
            addEvent(GameEvent(eventType = "MOVE", playerIndex = playerIndex, timeRemainingMs = p.timeRemainingMs, moveCount = p.moveCount + 1, moveNotation = notation, timeSpentMs = timeSpentOnMove))
            applyPostMoveLogic(playerIndex, timeSpentOnMove)
            moveStartTime = SystemClock.elapsedRealtime(); startClock(nextPlayer)
            if (settings.value.playSwitchSound) soundManager.playSwitch()
        }
    }

    private fun addEvent(event: GameEvent) {
        currentLog = currentLog?.let { it.copy(events = it.events + event) }
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
        lastTickTime = SystemClock.elapsedRealtime()
        timerJob = viewModelScope.launch {
            while (isActive) {
                try {
                    delay(tickIntervalMs().milliseconds)
                    tick()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("ChessTimerViewModel", "tick() threw, clock may appear stuck", e)
                    yield()
                }
            } 
        }
    }

    /**
     * How often to tick, which is how often the screen is asked to redraw.
     *
     * Every tick publishes a new state, so ticking at 10ms recomposes the timer a hundred times a
     * second for the whole game -- most of it spent redrawing digits that did not change. The
     * accounting does not care: time is taken from the difference between two readings, so a
     * coarser tick is just as accurate, only later.
     *
     * The display does care, but only when it shows hundredths. Tenths change every 100ms and
     * seconds every 1000, so 100ms keeps both exact.
     *
     * The condition mirrors the one TimerDisplay uses to decide whether hundredths appear, and the
     * two have to agree: tick slower than the display and the smallest digit moves in jumps.
     */
    private fun tickIntervalMs(): Long {
        val settings = _settings.value
        if (!settings.showHundredths) return SLOW_TICK_MS
        if (!settings.showHundredthsOnlyUnder10s) return FAST_TICK_MS

        val active = _uiState.value.activePlayer ?: return SLOW_TICK_MS
        val remaining = _uiState.value.players.getOrNull(active - 1)?.timeRemainingMs ?: return SLOW_TICK_MS
        return if (kotlin.math.abs(remaining) < 10_000L) FAST_TICK_MS else SLOW_TICK_MS
    }

    private fun tick() {
        val now = SystemClock.elapsedRealtime(); val delta = now - lastTickTime; lastTickTime = now
        
        // Saved so a game survives the app being killed. Every five seconds meant serialising the
        // settings and the whole state, then writing to the database, some 1,400 times over a
        // two-hour game -- for a recovery that almost never happens. Fifteen bounds what a crash
        // costs to fifteen seconds of one player's clock, which is a fair price for a third of the
        // writes.
        if (now - lastAutoSaveTime >= AUTO_SAVE_INTERVAL_MS) {
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
                    // just a visual + audio cue that this phase is done.
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
        // release(), not disconnect(): the USB manager also holds a registered broadcast receiver,
        // and both it and the serial one own a coroutine scope.
        usbBoardManager.release()
        bluetoothSerialBoardManager.release()
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
        // Anything the board reported but no press claimed belongs to the game being ended, not to
        // the next one.
        pendingBoardNotation = null
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

                _gameHistory.value = loadHistory(limit)
            }
        }
        currentLog = null
        lastAnnouncedThreshold.clear()
        _uiState.update { createInitialState(_settings.value, reuseRandomRoll = true) }
    }

    /**
     * Resets with a brand new random time, for RANDOM/HIDDEN.
     *
     * [reset] deliberately reuses the roll so restarting a game does not change the time under the
     * players; that left no way at all to get a fresh one short of toggling a setting to invalidate
     * the cache. Dropping [lastRandomRoll] first makes createInitialState draw again.
     */
    fun rerollRandomTime() {
        lastRandomRoll = null
        currentLog = null
        lastAnnouncedThreshold.clear()
        _uiState.update { createInitialState(_settings.value) }
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
        
        // isImport guards this for the same reason it guards newSettings above: the scoreboard
        // arrives from the same untrusted QR/JSON/ZIP payload.
        scoreboardToImport?.let { _scoreboard.value = if (isImport) sanitizeImportedScoreboard(it) else it }
        // Volume changes only need the play() volume updated, not a full sample reload:
        // the Slider fires per drag-frame, and reloading on every frame accumulated
        // duplicate samples in the SoundPool until load() started failing silently.
        // (voiceVolume needs nothing at all — it's passed per-speak call.)
        if (oldSettings.customBeepUri != newSettings.customBeepUri ||
            oldSettings.customGongUri != newSettings.customGongUri ||
            oldSettings.customFinalBeepUri != newSettings.customFinalBeepUri ||
            oldSettings.customSwitchUri != newSettings.customSwitchUri ||
            oldSettings.audioOutputMedia != newSettings.audioOutputMedia) {
            soundManager.loadSounds(newSettings)
        } else if (oldSettings.soundsVolume != newSettings.soundsVolume) {
            soundManager.setVolume(newSettings.soundsVolume)
        }

        val coreChanged = oldSettings.main != newSettings.main ||
                oldSettings.p1Custom != newSettings.p1Custom ||
                oldSettings.p2Custom != newSettings.p2Custom ||
                // p3Custom/p4Custom were missing here: changing only P4's settings (4-player,
                // differentSettingsPerPlayer) silently failed to refresh the clock until some other
                // tracked field also changed.
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
                // false there) -- silently reopening the path-sanitisation hole this guards against.
                val app = getApplication<Application>()
                logs.forEach { log ->
                    val sanitizedLog = sanitizeImportedLog(log)
                        .copy(settings = sanitizeImportedSettings(app, log.settings))
                    gameDao.insertLog(converters.fromGameLog(sanitizedLog))
                }
                _gameHistory.value = loadHistory(_settings.value.logHistoryLimit)
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

    /**
     * Called from [tick], which runs on the main thread, so nothing here may encode on the caller.
     *
     * It used to. Both encodeToString calls sat outside the launch, and what they encode is the
     * whole of [ChessClockSettings] -- every notebook note and every drawing stroke ride along in
     * it. Every fifteen seconds, for the length of a game, the clock stopped to serialise a
     * notebook.
     *
     * The two values are read here rather than inside the coroutine on purpose: this is what the
     * clock looked like at the moment the save was due, not whenever the encode happens to run.
     */
    fun saveClockForLater() {
        val settingsSnapshot = settings.value
        val stateSnapshot = uiState.value.toProxy()

        viewModelScope.launch {
            val entity = withContext(Dispatchers.Default) {
                com.masterclock.app.data.SavedClockEntity(
                    settingsJson = json.encodeToString(ChessClockSettings.serializer(), settingsSnapshot),
                    stateJson = json.encodeToString(ChessClockStateProxy.serializer(), stateSnapshot),
                )
            }
            gameDao.saveClock(entity)
            _hasSavedClock.value = true
        }
    }

    fun resumeSavedClock() {
        viewModelScope.launch {
            val saved = gameDao.getSavedClock() ?: return@launch
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
        val outcome = boardMoveOutcome(
            notation = notation,
            activePlayer = currentState.activePlayer,
            isPaused = currentState.isPaused,
            autoSwitchOnBoardMove = settings.value.autoSwitchOnBoardMove,
        )
        when (outcome) {
            // The MOVE event this raises carries the notation, so a separate BOARD_MOVE alongside it
            // would be the same move logged twice.
            is BoardMoveOutcome.SwitchNow -> startOrSwitch(outcome.playerIndex, boardNotation = outcome.notation)

            is BoardMoveOutcome.HoldForNextPress -> {
                pendingBoardNotation = outcome.notation
                val p = currentState.players[outcome.playerIndex - 1]
                addEvent(GameEvent(
                    eventType = "BOARD_MOVE",
                    playerIndex = outcome.playerIndex,
                    timeRemainingMs = p.timeRemainingMs,
                    moveCount = p.moveCount,
                    moveNotation = outcome.notation,
                    detail = "Move from board: ${outcome.notation}",
                ))
            }

            is BoardMoveOutcome.NoGameRunning ->
                addEvent(GameEvent(eventType = "BOARD_DATA", detail = "Data with no clock running: ${outcome.notation}"))
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
