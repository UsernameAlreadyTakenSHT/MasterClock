package com.masterclock.app.logic

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.masterclock.core.R as CoreR
import com.masterclock.app.data.Converters
import com.masterclock.app.data.GameDatabase
import com.masterclock.app.data.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
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
    val isGongReflectionPhase: Boolean = true,
    // See PlayerState.isPhaseAwaitingTap. False in snapshots older than the field, which stored a
    // phase waiting for its tap as out of time.
    val isPhaseAwaitingTap: Boolean = false,
)

@Serializable
data class ChessClockStateProxy(
    val players: List<PlayerStateProxy>,
    val activePlayer: Int? = null,
    val isPaused: Boolean = true,
    val cycleCount: Int = 1,
    val globalTimeMs: Long = 0,
    val isArbitreMode: Boolean = false,
    val firstToFlag: Int? = null,
    // Which stored game this clock belongs to, so that resuming it goes on writing that game rather
    // than starting a new one whose "starting" times are the ones it had mid-game. Only the
    // autosave sets it; null in snapshots older than the field, which then resume as before.
    val gameLogId: String? = null,
    // How long the move in progress had been running. Resuming started the move's clock from
    // zero, so Bronstein credited only what was spent after the interruption -- and the logged
    // time for that move lost everything before it.
    val moveElapsedMs: Long = 0,
    // See ChessClockState.phasesFinished. False in snapshots older than the field: a finished
    // PHASES game saved by them comes back reading as flagged, as it did when it was saved.
    val phasesFinished: Boolean = false,
)

fun PlayerState.toProxy() = PlayerStateProxy(
    timeRemainingMs, secondaryTimeMs, delayRemainingMs, isOutOfTime, isNegative, isInByoyomi,
    byoyomiPeriodsRemaining, movesRemainingInPeriod, currentByoyomiMovesGoal, moveCount,
    currentPeriodIndex, hasFlagged, currentPhaseIndex, isInInterPhasePause,
    pauseTimeRemainingMs, revealTimeUntilMs, lastRevealPercentage, initialTotalTimeMs, isGongReflectionPhase,
    isPhaseAwaitingTap,
)

fun PlayerStateProxy.toState() = PlayerState(
    timeRemainingMs, secondaryTimeMs, delayRemainingMs, isOutOfTime, isNegative, isInByoyomi,
    byoyomiPeriodsRemaining, movesRemainingInPeriod, currentByoyomiMovesGoal, moveCount,
    currentPeriodIndex, hasFlagged, currentPhaseIndex, isInInterPhasePause,
    pauseTimeRemainingMs, revealTimeUntilMs, lastRevealPercentage, initialTotalTimeMs, isGongReflectionPhase,
    isPhaseAwaitingTap,
)

fun ChessClockState.toProxy() = ChessClockStateProxy(
    players.map { it.toProxy() }, activePlayer, isPaused, cycleCount, globalTimeMs, isArbitreMode, firstToFlag,
    phasesFinished = phasesFinished,
)

fun ChessClockStateProxy.toState() = ChessClockState(
    players.map { it.toState() }, activePlayer, isPaused, cycleCount, globalTimeMs, isArbitreMode, firstToFlag,
    phasesFinished = phasesFinished,
)

@Serializable
enum class TimerMode {
    SUDDEN_DEATH, FISCHER, BRONSTEIN, US_DELAY,
    MOVE_TIMER_STANDARD, MOVE_TIMER_SAVE_CAP, MOVE_TIMER_OVERTIME, MOVE_TIMER_GLOBAL, MOVE_TIMER_SHARED, MOVE_TIMER_GLOBAL_SHARED,
    HOURGLASS, BYOYOMI_JAPANESE, BYOYOMI_CANADIAN, BYOYOMI_PROGRESSIVE,
    CHRONO_COUNTDOWN, CHRONO_COUNTUP, MOVE_COUNTS_UP, MOVE_COUNTS_DOWN,
    FIDE_PERIODS, PHASES, RANDOM, HIDDEN, GONG, FAST_MOVE;

    /**
     * The modes that run one clock for the whole table: a single move time, and in Global Shared a
     * single reserve, that every player draws on in turn, and the single clock of PHASES. Per-player
     * settings have nothing to give them. The shared modes read the move time from whoever was on
     * the clock, so player 2's setting timed player 3's turn and a reserve set per player was
     * ignored outright; PHASES ran player 1's phases while the screen went by the main settings and
     * player 2's phase editor changed nothing. They are turned off for these modes; see
     * [wholeTableSettings].
     */
    val isWholeTable: Boolean get() = this == MOVE_TIMER_SHARED || this == MOVE_TIMER_GLOBAL_SHARED || this == PHASES
}

/**
 * The settings with per-player customisation turned off when the game's mode runs one clock for
 * the table ([TimerMode.isWholeTable]), Player 1's settings becoming everyone's. Applied to every
 * settings change and to what is read back from storage, so neither the settings screens, a
 * preset nor an import can combine the two.
 */
fun ChessClockSettings.wholeTableSettings(): ChessClockSettings =
    if (differentSettingsPerPlayer && p1Custom.mode.isWholeTable) copy(differentSettingsPerPlayer = false, main = p1Custom, p2Custom = p1Custom, p3Custom = p1Custom, p4Custom = p1Custom)
    else this

/**
 * What a screen reader should hear when the clock changes state.
 *
 * A description of the event rather than a sentence: core has no Context and therefore no
 * resources, so the wording belongs to whichever UI renders it. Keeping it structured also lets
 * the announcement logic stay covered by plain unit tests, with no Android on the classpath.
 */
sealed interface ClockAnnouncement {
    data class OutOfTime(val playerIndex: Int) : ClockAnnouncement
    /** A PHASES game run to its end with no flag on its last phase; see [ChessClockState.phasesFinished]. */
    data object Finished : ClockAnnouncement
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
/** What the table's clock starts a game at: CHRONO's countdown, or the first phase of PHASES. */
internal fun initialGlobalTimeMs(s1: PlayerSettings): Long = when (s1.mode) {
    TimerMode.CHRONO_COUNTDOWN -> s1.initialTimeMs
    TimerMode.PHASES -> s1.phases.firstOrNull()?.timeMs ?: 0
    else -> 0L
}

/**
 * Takes what a tick ran past the end of a phase or a pause off whatever comes next, the next
 * phase or the pause. Both clocks were floored at zero, and the overshoot -- up to a whole tick --
 * went nowhere at every boundary, the loss settleRunningClock exists to prevent for a press.
 */
internal fun ChessClockState.carryPhaseOvershoot(ms: Long): ChessClockState {
    val p1 = players.firstOrNull() ?: return this
    if (ms <= 0 || phasesFinished || isPaused || p1.isOutOfTime || p1.isPhaseAwaitingTap) return this
    return if (p1.isInInterPhasePause) copy(players = players.toMutableList().apply { this[0] = p1.copy(pauseTimeRemainingMs = (p1.pauseTimeRemainingMs - ms).coerceAtLeast(0)) })
    else copy(globalTimeMs = (globalTimeMs - ms).coerceAtLeast(0))
}

/**
 * Moves [playerIndex] on to the next of [phases], or ends the game after the last one unless
 * [loop]. Running out of phases is a finish, not a flag: it sets [ChessClockState.phasesFinished].
 * A phase that flags on its own end never gets here -- startPhaseTransition stops the game first.
 */
internal fun nextPhaseState(state: ChessClockState, playerIndex: Int, phases: List<GamePhase>, loop: Boolean): ChessClockState {
    val p = state.players[playerIndex - 1]
    // With no phases at all there is nothing to advance to, and the loop branch below would
    // otherwise send phases[0] into an empty list. Settings are persisted, so a crafted import
    // reaching here once would crash the clock on every launch after it. Not a finish: there was
    // no game to finish.
    if (phases.isEmpty()) {
        val newList = state.players.toMutableList()
            .apply { this[playerIndex - 1] = p.copy(isOutOfTime = true, timeRemainingMs = 0) }
        return state.copy(players = newList, globalTimeMs = 0)
    }
    var nextIdx = p.currentPhaseIndex + 1
    if (nextIdx >= phases.size) {
        // Phases that all last nothing would loop once a tick for ever -- a cue and a spoken name
        // ten times a second, with nothing on the clock to stop it. Looping them finishes instead.
        nextIdx = if (loop && phases.any { it.timeMs > 0 }) {
            0
        } else {
            val newList = state.players.toMutableList().apply { this[playerIndex - 1] = p.copy(isOutOfTime = true, timeRemainingMs = 0, isPhaseAwaitingTap = false, isInInterPhasePause = false) }
            return state.copy(players = newList, globalTimeMs = 0, phasesFinished = true)
        }
    }
    val nextPhase = phases[nextIdx]
    val advancedPlayer = p.copy(currentPhaseIndex = nextIdx, timeRemainingMs = nextPhase.timeMs, isOutOfTime = false, isInInterPhasePause = false, isPhaseAwaitingTap = false)
    val newList = state.players.toMutableList().apply { this[playerIndex - 1] = advancedPlayer }
    return state.copy(players = newList, globalTimeMs = nextPhase.timeMs, phasesFinished = false)
}

/**
 * A random time from [minMs]..[maxMs] that is a multiple of [stepMs], or of [fineStepMs] when no
 * multiple of [stepMs] falls in the range, or [minMs] itself when no multiple of either does.
 *
 * Both bounds used to be divided down, so the minimum was rounded *down* to a step: a 1:05-10:00
 * range could draw 1:00, and 12-15 seconds drew 10 every time -- below the minimum the player had
 * set. The minimum now rounds up and the maximum down, so the draw always stays inside the range.
 */
internal fun drawInRange(
    minMs: Long,
    maxMs: Long,
    stepMs: Long,
    fineStepMs: Long,
    random: kotlin.random.Random = kotlin.random.Random,
): Long {
    for (step in listOf(stepMs, fineStepMs)) {
        val low = Math.floorDiv(minMs + step - 1, step)
        val high = Math.floorDiv(maxMs, step)
        if (low <= high) return random.nextLong(low, high + 1) * step
    }
    return minMs
}

fun clockAnnouncement(state: ChessClockState): ClockAnnouncement? {
    if (state.phasesFinished) return ClockAnnouncement.Finished
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

/**
 * The time a player's clock face shows. CHRONO and PHASES run one clock for everyone, kept in
 * [ChessClockState.globalTimeMs] (or, between phases, in the pause countdown), while each player's
 * own timeRemainingMs sits still. The tick rate, the haptic countdown, the low-time flash and the
 * TalkBack label all read timeRemainingMs regardless: hundredths stepped in tenths below ten
 * seconds, nothing vibrated or flashed, and TalkBack read out "5 minutes" for the whole game.
 * Everything that is about the time on screen asks here now, the screen included.
 */
fun displayedTimeMs(state: ChessClockState, player: PlayerState, mode: TimerMode): Long = when {
    mode == TimerMode.PHASES && player.isInInterPhasePause -> player.pauseTimeRemainingMs
    mode == TimerMode.PHASES || mode == TimerMode.CHRONO_COUNTDOWN || mode == TimerMode.CHRONO_COUNTUP -> state.globalTimeMs
    else -> player.timeRemainingMs
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

/**
 * Whether a reading of [absTimeMs] is drawn with tenths.
 *
 * A threshold of [Long.MAX_VALUE] means always; zero means never, which is why it is checked
 * separately rather than left to the comparison.
 */
fun ChessClockSettings.showsTenthsAt(absTimeMs: Long): Boolean =
    showTenthsThresholdMs == Long.MAX_VALUE || (absTimeMs < showTenthsThresholdMs && showTenthsThresholdMs > 0)

/**
 * Whether a reading of [absTimeMs] is drawn with hundredths.
 *
 * Two callers, and they have to agree: TimerDisplay decides what to draw with it, and
 * [ChessTimerViewModel.tickIntervalMs] decides how often to redraw. Tick slower than the display
 * and the smallest digit moves in visible jumps.
 *
 * They used to be two conditions in two modules, kept in step by a comment. The tick's copy was the
 * looser of the two -- it ignored the tenths threshold entirely -- so a clock set to show hundredths
 * only near the end still ticked a hundred times a second from the first move, which is most of the
 * battery the coarser tick was meant to save. Erring that way was deliberate and safe to look at,
 * but one function means neither can drift from the other at all.
 */
fun ChessClockSettings.showsHundredthsAt(absTimeMs: Long): Boolean =
    showHundredths && (if (showHundredthsOnlyUnder10s) absTimeMs < 10_000L else showsTenthsAt(absTimeMs))

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
    val phases: List<GamePhase> = listOf(GamePhase()),
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
    // The pause between phases lasts until a tap rather than for pauseBetweenPhasesMs: for a
    // break whose length nobody can know beforehand.
    val pauseUntilTap: Boolean = false,
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
        // A preset for everyone sets main alone, and the per-player copies kept the default sudden
        // death: turning on per-player settings after it threw the preset away -- a byoyomi preset
        // became 10 minutes sudden death, on both Modes tabs. Everyone starts from its settings.
        p1Custom = if (preset.differentSettingsPerPlayer) preset.p1Custom else preset.main,
        p2Custom = if (preset.differentSettingsPerPlayer) preset.p2Custom else preset.main,
        p3Custom = if (preset.differentSettingsPerPlayer) preset.p3Custom else preset.main,
        p4Custom = if (preset.differentSettingsPerPlayer) preset.p4Custom else preset.main,
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

    // These move timers ignore initialTimeMs entirely. Global Shared does not -- it is the reserve
    // the table shares -- and is labelled with both below.
    if (s.mode == TimerMode.MOVE_TIMER_STANDARD || s.mode == TimerMode.MOVE_TIMER_SHARED) {
        if (s.moveTimeMs > 0) return "${s.moveTimeMs / 1000}s / move"
    }

    // FIDE periods ignore initialTimeMs and incrementMs, and were labelled from them: a 90'/40 +
    // 30' + 30s game read "10 min + 10s", the defaults those fields keep. In the notation a
    // control is written in, with each period's moves counted within it.
    if (s.mode == TimerMode.FIDE_PERIODS && s.fidePeriods.isNotEmpty()) {
        val last = s.fidePeriods.lastIndex
        val parts = s.fidePeriods.mapIndexed { i, period ->
            val movesBefore = if (i == 0) 0 else s.fidePeriods[i - 1].movesToNext
            val moves = period.movesToNext - movesBefore
            "${period.timeMs / 60_000}'" + if (i < last && moves > 0) "/$moves" else ""
        }
        val first = s.fidePeriods.first()
        val bonus = if (first.isFischer && first.incrementMs > 0) " + ${first.incrementMs / 1000}s" else ""
        return parts.joinToString(" + ") + bonus
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
    if (s.mode == TimerMode.MOVE_TIMER_GLOBAL_SHARED && s.moveTimeMs > 0) return "${s.moveTimeMs / 1000}s / move, $base"
    // Byoyomi's own time was left out, so a 10-minute game with five 30-second periods read
    // "10 min" -- and, with the increment field's default, "10 min + 10s".
    val byoyomi = s.byoyomiTimeMs.let { if (it % 60_000 == 0L && it > 0) "${it / 60_000}min" else "${it / 1000}s" }
    when (s.mode) {
        TimerMode.BYOYOMI_JAPANESE -> if (s.byoyomiPeriods > 0) return "$base + ${s.byoyomiPeriods}×$byoyomi"
        TimerMode.BYOYOMI_CANADIAN, TimerMode.BYOYOMI_PROGRESSIVE -> return "$base + ${s.byoyomiPeriods.coerceAtLeast(1)}/$byoyomi"
        else -> {}
    }
    // Only the modes that have one. incrementMs keeps its default of ten seconds in every other
    // mode, so a plain 10-minute sudden death read "10 min + 10s".
    val increment = if (s.mode == TimerMode.FISCHER || s.mode == TimerMode.BRONSTEIN || s.mode == TimerMode.US_DELAY) s.incrementMs / 1000 else 0
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
    val scoreboard: ScoreboardSession? = null,
    // Only a full backup carries these two; a settings file or QR code leaves them null.
    val customPresets: List<SavedPreset>? = null,
    val omniSettings: OmniSettings? = null,
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
    val isGongReflectionPhase: Boolean = true,
    /**
     * PHASES: this phase's time is up, it does not advance on its own, and the clock waits for a
     * tap to move on. It used to be told apart by isOutOfTime alone, so a phase merely waiting was
     * a flag to everything that reads one -- the triple beep, "time is up" spoken, a FLAG in the
     * log, the loss colour -- and under FREEZE the button that was to move it on was disabled,
     * leaving Reset as the only way out of the game.
     */
    val isPhaseAwaitingTap: Boolean = false,
)

data class ChessClockState(
    val players: List<PlayerState>,
    val activePlayer: Int? = null, // 1-based index
    val isPaused: Boolean = true,
    val cycleCount: Int = 1,
    val globalTimeMs: Long = 0,
    val isArbitreMode: Boolean = false,
    val firstToFlag: Int? = null,
    /**
     * A PHASES game that ran through its last phase, with no flag set on that phase: it is over,
     * but nobody lost. Player 1 is still marked out of time, so everything that stops a finished
     * game stops this one; this only changes what the game is called. It read "FLAGGED" in loss
     * colour and the voice said time was up, which is what [GamePhase.flagOnEnd] is there to mean.
     */
    val phasesFinished: Boolean = false,
)

/**
 * Imported settings (QR / JSON / ZIP share packages) come from an untrusted source and must never
 * be trusted with raw file paths or note ids: [NotebookNote.id] feeds a `contains(id)` file filter
 * on delete (an empty/crafted id can match every file in `filesDir`), and audioPath/imagePath/
 * videoPath/custom*Uri are read and written to directly.
 */
private fun sanitizeImportedSettings(context: android.content.Context, settings: ChessClockSettings): ChessClockSettings =
    sanitizeSettings(context, settings, freshNoteIds = true)

/**
 * Re-checks settings that were read back from DataStore, rather than ones arriving from outside.
 *
 * Every guard above ran on `isImport = true` and nowhere else, so all of them were doors with no
 * room behind them: an import that landed before the guard existed was persisted, and the app read
 * it straight back into [_settings] on every launch afterwards without looking at it again. The
 * v0.8.29 fix for the note that aims the notebook's shredder at `settings.preferences_pb` therefore
 * protected clean installs and did nothing at all for an install that already carried the payload.
 *
 * Note ids are the one thing that must survive: they are what the notebook's own files are named
 * after, so regenerating them on read would orphan every recording and photo on every launch.
 * Everything else here is idempotent, which is what makes it safe to run each time.
 */
private fun sanitizeStoredSettings(context: android.content.Context, settings: ChessClockSettings): ChessClockSettings =
    sanitizeSettings(context, settings, freshNoteIds = false)

private fun sanitizeSettings(
    context: android.content.Context,
    settings: ChessClockSettings,
    freshNoteIds: Boolean,
): ChessClockSettings {
    val sanitizedNotes = settings.notebookNotes.take(MAX_IMPORTED_NOTES).map { note ->
        note.copy(
            id = if (freshNoteIds) java.util.UUID.randomUUID().toString() else note.id,
            title = note.title.take(MAX_IMPORTED_NOTE_TITLE_CHARS),
            content = note.content.take(MAX_IMPORTED_NOTE_CONTENT_CHARS),
            drawingPaths = note.drawingPaths.take(MAX_IMPORTED_DRAWING_PATHS).map { stroke ->
                stroke.copy(points = stroke.points.take(MAX_IMPORTED_DRAWING_POINTS))
            },
            // The grid is laid out from boardVariant.side, so an imported list of any other length
            // means the tap and drag handlers index past its end the first time the board is
            // touched. Normalising here is what keeps the two in step; the editor's own resize
            // already does the same when the variant changes.
            boardPosition = note.boardPosition.take(note.boardVariant.squareCount).let { squares ->
                if (squares.size == note.boardVariant.squareCount) squares
                else squares + List(note.boardVariant.squareCount - squares.size) { "" }
            },
            audioPath = sanitizeImportedMediaPath(context.filesDir, note.audioPath),
            imagePath = sanitizeImportedMediaPath(context.filesDir, note.imagePath),
            videoPath = sanitizeImportedMediaPath(context.filesDir, note.videoPath),
        )
    }
    val sanitizedNumberOfPlayers = settings.numberOfPlayers.coerceIn(1, 4)
    return settings.copy(
        notebookNotes = sanitizedNotes,
        customBeepUri = sanitizeImportedContentUri(context, settings.customBeepUri),
        customGongUri = sanitizeImportedContentUri(context, settings.customGongUri),
        customFinalBeepUri = sanitizeImportedContentUri(context, settings.customFinalBeepUri),
        customSwitchUri = sanitizeImportedContentUri(context, settings.customSwitchUri),
        numberOfPlayers = sanitizedNumberOfPlayers,
        // The one integer that reaches SQL directly, and the one this function used to miss.
        // trimLogs(0) runs DELETE ... WHERE id NOT IN (SELECT id ... LIMIT 0), whose subquery
        // selects nothing, so every row goes -- silently, at the end of the next game. And SQLite
        // reads any negative LIMIT as no limit, so a value like -2 slips past the ceiling in
        // loadHistory, which only recognised -1. The UI offers -1 or 10..10000; nothing else is a
        // value a user could have chosen.
        logHistoryLimit = if (settings.logHistoryLimit == -1) -1
            else settings.logHistoryLimit.coerceIn(1, MAX_UNLIMITED_HISTORY),
        // Per-player customization (p3Custom/p4Custom) is only ever offered by the app's own UI for
        // 2 players (SettingsBehaviorPage forces this same rule when toggling "more players"); an
        // import must not be able to sneak past that restriction.
        differentSettingsPerPlayer = settings.differentSettingsPerPlayer && sanitizedNumberOfPlayers <= 2,
        playerMapping = settings.playerMapping.let { mapping ->
            if (mapping.size == 4 && mapping.all { it in 1..4 }) mapping else listOf(1, 2, 3, 4)
        },
        main = validateImportedPlayerSettings(settings.main),
        p1Custom = validateImportedPlayerSettings(settings.p1Custom),
        // One mode per game, chosen by Player 1: the modes page writes P1's mode into the others
        // and offers no way to change theirs. A file can still carry different ones, and with P2 in
        // RANDOM under a P1 that is not, P2's time was never drawn -- it started at 0:00 and
        // flagged on its first tick. The others follow P1 here too.
        p2Custom = validateImportedPlayerSettings(settings.p2Custom).copy(mode = settings.p1Custom.mode),
        p3Custom = validateImportedPlayerSettings(settings.p3Custom).copy(mode = settings.p1Custom.mode),
        p4Custom = validateImportedPlayerSettings(settings.p4Custom).copy(mode = settings.p1Custom.mode),
    )
}

/**
 * The notebook after an import: every note the device already has, plus each imported note it
 * does not have yet.
 *
 * The notebook lives inside [ChessClockSettings], and an import replaced the settings whole, so it
 * replaced the notebook with whatever the file carried -- and no export but the ZIP carries all of
 * it. A QR code and a settings file without history carry no notes at all, and one with history
 * carries the text notes only. Importing any of them, a user's own export included, deleted every
 * note, drawing and recording on the device, with "Import successful!" on screen.
 *
 * Merging means an import can only ever add to the notebook. A note counts as already present
 * when everything but its id matches, since an import regenerates ids: that keeps re-importing
 * one's own export, or restoring a backup onto the device it came from, from doubling every note.
 *
 * Imported notes stop at [MAX_NOTEBOOK_NOTES]. The device's own notes come first and are never
 * the ones cut; past the ceiling, the launch-time sanitiser would otherwise cut the newest.
 */
internal fun mergeImportedNotes(local: List<NotebookNote>, imported: List<NotebookNote>): List<NotebookNote> {
    val present = local.mapTo(HashSet()) { it.copy(id = "") }
    val room = (MAX_NOTEBOOK_NOTES - local.size).coerceAtLeast(0)
    return local + imported.filter { present.add(it.copy(id = "")) }.take(room)
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
        // Bounded above as well: a huge progression overflowed the growing goal into unlimited time.
        byoyomiPeriods = settings.byoyomiPeriods.coerceIn(0, MAX_BYOYOMI_COUNT),
        byoyomiTimeMs = settings.byoyomiTimeMs.coerceIn(0, MAX_IMPORTED_DURATION_MS),
        byoyomiProgression = settings.byoyomiProgression.coerceIn(0, MAX_BYOYOMI_COUNT),
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
        // Every scalar above was clamped and both lists were left alone. Their length drives how
        // much the settings screen composes at once, and phases additionally drives the clock:
        // see performPhaseAdvance, which loops back to phases[0] and needs there to be one.
        phases = settings.phases.take(MAX_IMPORTED_PERIODS).map { phase ->
            phase.copy(timeMs = phase.timeMs.coerceAtLeast(0), name = phase.name.take(MAX_IMPORTED_NOTE_TITLE_CHARS))
        },
        // Bounded above too: a period near Long.MAX_VALUE overflowed as its bonus was added, into a
        // negative clock that flagged on the next tick. An empty list fell back to a default period
        // here and there while the face counted "PERIOD: 1/0"; it gets one stated outright.
        fidePeriods = settings.fidePeriods.take(MAX_IMPORTED_PERIODS).map { period ->
            period.copy(
                timeMs = period.timeMs.coerceIn(0, MAX_IMPORTED_DURATION_MS),
                incrementMs = period.incrementMs.coerceIn(0, MAX_IMPORTED_DURATION_MS),
                movesToNext = period.movesToNext.coerceIn(0, MAX_FIDE_MOVES),
            )
        }.ifEmpty { listOf(FidePeriod()) },
    )
}

/**
 * The three shapes the notebook ever writes a media file in. Nothing else is a note's own media,
 * whatever directory it sits in.
 */
private val NOTE_AUDIO_NAME = Regex("""audio_[A-Za-z0-9-]{1,64}\.mp4""")
private val NOTE_IMAGE_NAME = Regex("""image_[A-Za-z0-9-]{1,64}\.jpg""")
private val NOTE_VIDEO_NAME = Regex("""video_[A-Za-z0-9-]{1,64}\.mp4""")

/**
 * Accept an imported note media path only if it names a file the notebook itself could have
 * written: `filesDir/audio_<id>.mp4`, `filesDir/shares/image_<id>.jpg` or
 * `filesDir/shares/video_<id>.mp4`.
 *
 * Containment in the sandbox used to be the whole test, and that is not the same question. The
 * app's own DataStore lives at `filesDir/datastore/settings.preferences_pb`, which is inside
 * filesDir and therefore passed. The notebook's delete button hands these three fields to
 * `shredFile`, which overwrites a file with random bytes and unlinks it -- so a note carrying that
 * path turned one tap on a trash icon into the irrecoverable loss of every setting, preset and note
 * the user had. Five independent reviews found this same path.
 *
 * Matching the naming convention rather than dropping the fields outright keeps the case that is
 * legitimate: restoring a backup, whose media entries [ZipBackupManager.restoreMedia] writes back
 * to exactly the paths this function accepts -- or finds still present, on the device the backup
 * came from. This function is also what decides where those entries may land, through
 * [ZipBackupManager.mediaDestinations], so the two cannot disagree.
 *
 * Residual, and accepted: since note ids are regenerated on import, a path cannot be tied back to
 * the note that owns it, so one note can still name another note's recording. That costs a media
 * file rather than the settings store, and closing it would mean giving up relinking entirely.
 */
internal fun sanitizeImportedMediaPath(filesDirRaw: java.io.File, path: String?): String? {
    if (path.isNullOrBlank()) return null
    return try {
        val canonical = java.io.File(path).canonicalFile
        val filesDir = filesDirRaw.canonicalFile
        val sharesDir = java.io.File(filesDir, "shares").canonicalFile
        val parent = canonical.parentFile ?: return null
        val name = canonical.name
        val allowed = when {
            parent == filesDir -> NOTE_AUDIO_NAME.matches(name)
            parent == sharesDir -> NOTE_IMAGE_NAME.matches(name) || NOTE_VIDEO_NAME.matches(name)
            else -> false
        }
        if (allowed) canonical.path else null
    } catch (_: Exception) {
        null
    }
}

/**
 * Keep an imported custom-sound URI only if this app actually holds a persisted read grant for it.
 *
 * The test used to be `startsWith("content://")`, which is a check on spelling rather than on
 * access: any provider URI on the device survived it, and SoundManager then called
 * openFileDescriptor on whatever arrived, at every settings load.
 *
 * SettingsAudioPage takes a persistable read permission whenever the user picks a sound, so a URI
 * the user chose always has one and a URI that came from somewhere else does not. Dropping the
 * latter also makes the state honest: without a grant the sound could never have played anyway, so
 * the setting silently fell back to the default while still displaying a custom file.
 */
private fun sanitizeImportedContentUri(context: android.content.Context, uri: String?): String? {
    if (uri.isNullOrBlank() || !uri.startsWith("content://")) return null
    val held = try {
        context.contentResolver.persistedUriPermissions.any { it.isReadPermission && it.uri.toString() == uri }
    } catch (_: Exception) {
        false
    }
    return if (held) uri else null
}

/**
 * The longest recorded competitive chess game ran 269 moves, so a log carrying more than this many
 * events is not a game anyone played. The cap matters because the log detail screen renders one
 * composable Row per move inside a scrolling Column rather than a lazy list: without it, a crafted
 * backup with a hundred thousand MOVE events freezes the app the moment the log is opened.
 */
const val MAX_IMPORTED_EVENTS_PER_LOG = 2_000

/**
 * Ceilings on the parts of an imported notebook that the UI composes eagerly.
 *
 * A drawing is rendered by rebuilding a Path from its points on every frame, and the notebook list
 * and note editors are plain Columns. The 10 MB the reader allows is a great many coordinates, and
 * the settings blob is re-encoded to DataStore on every later change, so an oversized import is
 * paid for again and again rather than once. These are deliberately far above anything a person
 * would produce by hand.
 */
/**
 * How many notes the notebook holds, enforced where notes are made as well as where they arrive.
 *
 * It used to be a ceiling on imports alone, while the notebook let a user go on adding notes past
 * it. sanitizeStoredSettings applies the same ceiling at every launch and new notes are appended
 * last, so the 501st note a user wrote was deleted the next time the app started -- with its media
 * left orphaned on disk. The screen now refuses the note instead, and a merging import stops at
 * the ceiling rather than filling the notebook past it.
 */
const val MAX_NOTEBOOK_NOTES = 500
private const val MAX_IMPORTED_NOTES = MAX_NOTEBOOK_NOTES
private const val MAX_IMPORTED_PRESETS = 500
/**
 * The longest note or phase title, and the longest note text, the app keeps. Public so the screens
 * that write them stop there: the stored-settings check cuts anything longer at every launch, and
 * the fields used to accept it -- so a long paste was silently shortened the next day.
 */
const val MAX_NOTE_TITLE_CHARS = 200
const val MAX_NOTE_CONTENT_CHARS = 100_000
private const val MAX_IMPORTED_NOTE_TITLE_CHARS = MAX_NOTE_TITLE_CHARS
private const val MAX_IMPORTED_NOTE_CONTENT_CHARS = MAX_NOTE_CONTENT_CHARS
private const val MAX_IMPORTED_DRAWING_PATHS = 5_000
private const val MAX_IMPORTED_DRAWING_POINTS = 10_000

/**
 * A game has one time control, and a period or phase is a thing a person types in one at a time.
 * Both lists are rendered by a `forEachIndexed` inside a scrolling Column, one Card with several
 * text fields each, so a few hundred thousand of them is an unresponsive settings screen that
 * persists across restarts.
 */
private const val MAX_IMPORTED_PERIODS = 64

/** Room reads a row through a CursorWindow of about 2 MB, and every event of a game shares one column. */
private const val MAX_IMPORTED_EVENT_DETAIL_CHARS = 256

/** Long enough for any event name this app writes; short enough not to be a payload. */
private const val MAX_IMPORTED_EVENT_TYPE_CHARS = 64

/** More than anyone has played, and far below what makes the history screen or the database suffer. */
private const val MAX_IMPORTED_LOGS = 10_000

/** A month per move is already absurd; anything past it is a crafted value, not a slow player. */
private const val MAX_IMPORTED_DURATION_MS = 30L * 24 * 60 * 60 * 1000

/** A move count no game reaches; the editor's field takes four digits. */
const val MAX_FIDE_MOVES = 9_999

/** The same for byoyomi's periods, block moves and progression, entered in the same kind of field. */
const val MAX_BYOYOMI_COUNT = 9_999

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
// The id is deliberately left alone, unlike NotebookNote's and ScoreboardGame's. A review argued
// for regenerating it, because insertLog is OnConflictStrategy.REPLACE and an imported log carrying
// the id of a recorded game overwrites it. That is true, and the cure is worse: keeping the id is
// what makes restoring a backup idempotent, so a user who restores the same archive twice gets
// their history back rather than two of everything. What it buys an attacker is the replacement of
// one local game record, with no path out of the sandbox.
internal fun sanitizeImportedLog(log: GameLog): GameLog = log.copy(
    // The history is trimmed by count keeping the newest rows by startTime, so an imported log
    // dated in the future ranks above every game played from then on: ten of them dated 2100, under
    // a limit of ten, and each new game was deleted by the trim that stored it. Nothing imported
    // can have started later than the moment it arrives.
    startTime = log.startTime.coerceIn(0L, System.currentTimeMillis()),
    events = log.events.take(MAX_IMPORTED_EVENTS_PER_LOG).map { event ->
        event.copy(
            // Neither of these was bounded. Every event of a game shares one JSON column, and Room
            // reads a row through a CursorWindow of about 2 MB: exceed it and getRecentLogs throws
            // where nothing catches it, on every launch, until the app's data is cleared.
            eventType = event.eventType.take(MAX_IMPORTED_EVENT_TYPE_CHARS),
            detail = event.detail?.take(MAX_IMPORTED_EVENT_DETAIL_CHARS),
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
 * [NotebookNote.id]. No cap on the number of games: the list renders lazily, and
 * [MAX_IMPORT_TEXT_BYTES] already bounds how much can arrive -- a limit here would be a number
 * invented to look thorough, and would silently drop a real user's own tally.
 *
 * The stored session goes through this too at every launch, since a restore is what usually wrote
 * it. Regenerating its ids there costs nothing: nothing refers to a scoreboard game by id but the
 * list's own keys.
 */
/**
 * Saved presets from a backup, each one's settings through the same sanitiser an imported
 * configuration gets: applying a preset copies its time control onto the clock, so a crafted one
 * is exactly as dangerous as a crafted settings file. Presets never carry a notebook.
 */
internal fun sanitizeImportedPresets(context: android.content.Context, presets: List<SavedPreset>): List<SavedPreset> =
    presets.take(MAX_IMPORTED_PRESETS).map { preset ->
        preset.copy(
            name = preset.name.take(MAX_IMPORTED_NOTE_TITLE_CHARS),
            settings = sanitizeImportedSettings(context, preset.settings).copy(notebookNotes = emptyList()),
        )
    }

/**
 * The device's presets plus each restored one it does not already have, by id -- a preset keeps
 * its id through a backup, so restoring onto the same device doubles nothing, and nothing the user
 * saved since is lost.
 */
internal fun mergeImportedPresets(local: List<SavedPreset>, imported: List<SavedPreset>): List<SavedPreset> {
    val present = local.mapTo(HashSet()) { it.id }
    return local + imported.filter { present.add(it.id) }
}

/**
 * Omni settings from a backup. The engine already falls back to a default for every list it finds
 * empty, so what is bounded here is what the settings page composes from: one row per player, per
 * game, per round, per turn and per phase.
 */
internal fun sanitizeImportedOmniSettings(settings: OmniSettings): OmniSettings = settings.copy(
    // The settings page's slider runs from 1 to 20.
    numberOfPlayers = settings.numberOfPlayers.coerceIn(1, 20),
    playerColors = settings.playerColors.take(20),
    globalDurationMs = settings.globalDurationMs.coerceAtLeast(0),
    gameDurationMs = settings.gameDurationMs.coerceAtLeast(0),
    interTurnPauseMs = settings.interTurnPauseMs.coerceAtLeast(0),
    interRoundPauseMs = settings.interRoundPauseMs.coerceAtLeast(0),
    interGamePauseMs = settings.interGamePauseMs.coerceAtLeast(0),
    launchCountdownMs = settings.launchCountdownMs.coerceAtLeast(0),
    games = settings.games.take(MAX_IMPORTED_PERIODS).map { game ->
        game.copy(
            name = game.name.take(MAX_IMPORTED_NOTE_TITLE_CHARS),
            durationMs = game.durationMs.coerceAtLeast(0),
            rounds = game.rounds.take(MAX_IMPORTED_PERIODS).map { round ->
                round.copy(
                    name = round.name.take(MAX_IMPORTED_NOTE_TITLE_CHARS),
                    durationMs = round.durationMs.coerceAtLeast(0),
                    turnDurationMs = round.turnDurationMs.coerceAtLeast(0),
                    fixedTurnPhases = round.fixedTurnPhases.take(MAX_IMPORTED_PERIODS).map { phase ->
                        phase.copy(name = phase.name.take(MAX_IMPORTED_NOTE_TITLE_CHARS), durationMs = phase.durationMs.coerceAtLeast(0))
                    },
                    customTurns = round.customTurns.take(MAX_IMPORTED_PERIODS).map { turn ->
                        turn.copy(
                            durationMs = turn.durationMs.coerceAtLeast(0),
                            phases = turn.phases.take(MAX_IMPORTED_PERIODS).map { phase ->
                                phase.copy(
                                    name = phase.name.take(MAX_IMPORTED_NOTE_TITLE_CHARS),
                                    durationMs = phase.durationMs.coerceAtLeast(0),
                                )
                            },
                        )
                    },
                )
            },
        )
    },
)

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

/**
 * The part of a tick left over when one countdown runs out goes on to the next one, rather than
 * being thrown away. A tick that finished the US delay with 20ms of it left, or emptied the main
 * clock into byoyomi, or ran a Japanese period out, used to drop the rest of its time: up to a
 * tick's worth (100ms) per move, never charged to anyone.
 *
 * Each step that carries a remainder over strictly reduces what is left to run -- the delay is
 * gone, main time is gone, a period is consumed -- so the recursion ends, even with a byoyomi
 * period of zero length.
 */
/**
 * One tick of the move clock of MOVE_TIMER_GLOBAL and MOVE_TIMER_GLOBAL_SHARED.
 *
 * These were the only clocks that never went through [applyFlagBehaviorDelta]: the move clock just
 * kept falling below zero, with isNegative never set, and the display -- which draws the absolute
 * value and relies on isNegative for the sign -- showed an overrun as 0:01, 0:02, 0:03 counting up
 * under the FLAGGED label, like time being gained.
 *
 * Their isOutOfTime also covers the total clock running out, so whether the move clock itself is
 * already past zero has to be read off the move clock: under NEGATIVE that is isNegative, and
 * under FREEZE and FLAG a move clock that has run out sits at zero.
 *
 * REVERSE marks an overrun with nothing but the flag, and this read it as time left: a move clock
 * counting its overrun up was counted down again, flagged at zero, came back up a tick and went
 * down again, and sat at 0:00 for the rest of the move. Under REVERSE a player who is out is
 * counting an overrun -- of the move clock, or of the total, which [reverseTotalGone] starts from
 * zero for the same reason.
 */
private fun globalMoveClockTick(p: PlayerState, delta: Long, flagBehavior: FlagBehavior): Triple<Long, Boolean, Boolean> {
    val moveOverrun = p.isNegative || (p.isOutOfTime && (p.timeRemainingMs <= 0 || flagBehavior == FlagBehavior.REVERSE))
    return applyFlagBehaviorDelta(p.timeRemainingMs, moveOverrun, p.isNegative, delta, flagBehavior)
}

/**
 * Whether this tick is the one that empties the total (or the shared reserve) under REVERSE, so
 * the move clock is to start counting the overrun from zero: a player out under REVERSE counts up
 * from whatever the move clock shows, and the move time still left on it is not an overrun.
 */
/**
 * A Global or Global Shared player as the countdown cues should see them: with the clock that will
 * run out first, the move clock or the total (the shared reserve). The cues read the move clock
 * alone, so a reserve running down below the move time reached zero -- and flagged the player, or
 * everyone at once in Global Shared -- with no beep and no voice line on the way.
 */
internal fun PlayerState.withBindingClock(): PlayerState =
    if (isOutOfTime || isNegative) this else copy(timeRemainingMs = minOf(timeRemainingMs, secondaryTimeMs))

private fun reverseTotalGone(before: Long, after: Long, flagBehavior: FlagBehavior): Boolean =
    flagBehavior == FlagBehavior.REVERSE && before > 0 && after <= 0

/**
 * The modes whose clocks run in stages of their own -- byoyomi periods, FIDE periods, Hidden's
 * draw -- and so never reached [applyFlagBehaviorDelta]. Under NEGATIVE they ignored it: Canadian
 * and Progressive byoyomi let the clock fall below zero with no sign, so an overrun read 0:01,
 * 0:02 counting up; Japanese byoyomi, FIDE and Hidden froze at 0:00 and never counted it at all.
 */
private val STAGED_MODES = setOf(TimerMode.BYOYOMI_JAPANESE, TimerMode.BYOYOMI_CANADIAN, TimerMode.BYOYOMI_PROGRESSIVE, TimerMode.FIDE_PERIODS, TimerMode.HIDDEN)

/**
 * The last stage running out: an overrun to count under NEGATIVE and REVERSE, a clock held at zero
 * otherwise. REVERSE held it at zero too, and the next tick, finding the clock at 0:00 and nothing
 * marking an overrun, ran the stage out again: the clock sat at 0:00 for the rest of the game.
 */
private fun PlayerState.finalFlag(overrunMs: Long, flagBehavior: FlagBehavior): PlayerState = when (flagBehavior) {
    FlagBehavior.NEGATIVE -> copy(timeRemainingMs = overrunMs.coerceAtLeast(0), isOutOfTime = true, isNegative = true)
    FlagBehavior.REVERSE -> copy(timeRemainingMs = overrunMs.coerceAtLeast(0), isOutOfTime = true, isNegative = false)
    else -> copy(timeRemainingMs = 0, isOutOfTime = true)
}

internal fun tickPlayer(p: PlayerState, delta: Long, s: PlayerSettings, settings: ChessClockSettings): PlayerState {
    if (p.delayRemainingMs > 0) {
        if (delta <= p.delayRemainingMs) return p.copy(delayRemainingMs = p.delayRemainingMs - delta)
        return tickPlayer(p.copy(delayRemainingMs = 0), delta - p.delayRemainingMs, s, settings)
    }
    // An overrun past the last stage goes on growing; see finalFlag.
    if (p.isOverrun(settings.flagBehavior) && s.mode in STAGED_MODES) return p.copy(timeRemainingMs = p.timeRemainingMs + delta)
    return when (s.mode) {
        TimerMode.MOVE_TIMER_OVERTIME -> {
            // The reserve running out was always the end, whatever FlagBehavior said: the clock
            // froze at 0:00 under NEGATIVE and REVERSE too. Past the reserve the overrun now goes on
            // the move clock and follows FlagBehavior like every other clock -- negative and growing
            // under NEGATIVE, counting up under REVERSE, held at zero under FREEZE and FLAG.
            if (p.isOutOfTime) {
                val (t, out, neg) = applyFlagBehaviorDelta(p.timeRemainingMs, true, p.isNegative, delta, settings.flagBehavior)
                return p.copy(timeRemainingMs = t, isOutOfTime = out, isNegative = neg)
            }
            val newTime = p.timeRemainingMs - delta
            if (newTime >= 0) return p.copy(timeRemainingMs = newTime)
            val newSec = p.secondaryTimeMs + newTime
            if (newSec > 0) return p.copy(timeRemainingMs = 0, secondaryTimeMs = newSec)
            val overrun = -newSec
            when (settings.flagBehavior) {
                FlagBehavior.NEGATIVE -> p.copy(timeRemainingMs = overrun, secondaryTimeMs = 0, isOutOfTime = true, isNegative = true)
                FlagBehavior.REVERSE -> p.copy(timeRemainingMs = overrun, secondaryTimeMs = 0, isOutOfTime = true, isNegative = false)
                else -> p.copy(timeRemainingMs = 0, secondaryTimeMs = 0, isOutOfTime = true)
            }
        }
        TimerMode.MOVE_TIMER_GLOBAL -> {
            // The move clock honours FlagBehavior like every other clock; see globalMoveClockTick.
            val (moveTime, moveOut, moveNeg) = globalMoveClockTick(p, delta, settings.flagBehavior)
            val newSec = (p.secondaryTimeMs - delta).coerceAtLeast(0)
            val fromZero = !moveOut && reverseTotalGone(p.secondaryTimeMs, newSec, settings.flagBehavior)
            p.copy(timeRemainingMs = if (fromZero) 0 else moveTime, isNegative = moveNeg, secondaryTimeMs = newSec, isOutOfTime = moveOut || newSec <= 0)
        }
        TimerMode.BYOYOMI_JAPANESE -> {
            val newTime = p.timeRemainingMs - delta
            if (newTime > 0) return p.copy(timeRemainingMs = newTime)
            // Worked out at once rather than a period at a time. The recursion that did it went one
            // call deeper per period the tick ran through, and a period of 0s runs through all of
            // them in a single tick: a million periods of nothing overflowed the stack the moment
            // main time ran out, and closed the app.
            val overrun = -newTime
            val period = s.byoyomiTimeMs
            // byoyomiPeriodsRemaining counts the period being played; once it has run out, those
            // left are the ones not yet begun. Main time running out begins the first of them --
            // and with no periods at all that is the end. It used to hand out a full period anyway,
            // so 0 periods played as 1.
            val notBegun = if (p.isInByoyomi) p.byoyomiPeriodsRemaining - 1 else p.byoyomiPeriodsRemaining
            val usedUp = if (period > 0) overrun / period else Long.MAX_VALUE
            if (notBegun <= 0 || usedUp >= notBegun) {
                val past = if (period > 0) overrun - notBegun.coerceAtLeast(0) * period else overrun
                return p.copy(isInByoyomi = true, byoyomiPeriodsRemaining = 0).finalFlag(past, settings.flagBehavior)
            }
            p.copy(isInByoyomi = true, byoyomiPeriodsRemaining = (notBegun - usedUp).toInt(), timeRemainingMs = period - (overrun - usedUp * period))
        }
        TimerMode.BYOYOMI_CANADIAN, TimerMode.BYOYOMI_PROGRESSIVE -> {
            val newTime = p.timeRemainingMs - delta
            when {
                p.isInByoyomi -> if (newTime > 0) p.copy(timeRemainingMs = newTime) else p.finalFlag(-newTime, settings.flagBehavior)
                newTime > 0 -> p.copy(timeRemainingMs = newTime)
                else -> tickPlayer(p.copy(timeRemainingMs = s.byoyomiTimeMs, isInByoyomi = true), -newTime, s, settings)
            }
        }
        TimerMode.FIDE_PERIODS -> {
            val newTime = p.timeRemainingMs - delta
            if (newTime > 0) return p.copy(timeRemainingMs = newTime)
            val periodIdx = p.currentPeriodIndex
            // A period ends on its time when no move count ends it: always without the move
            // counter, and with it for a period that has no count. Such a middle period -- one
            // left at 0 in the editor, or the old last one when a period is added after it -- used
            // to flag the player instead, and the periods after it were never reached.
            val endsOnTime = !settings.forcedMoveCounter || (s.fidePeriods.getOrNull(periodIdx)?.movesToNext ?: 0) <= 0
            if (endsOnTime && periodIdx < s.fidePeriods.size - 1) {
                // What the tick ran past the end comes off the next period, which it used to start
                // in full; a long tick runs on through it into the one after, as byoyomi does.
                val next = p.copy(timeRemainingMs = s.fidePeriods[periodIdx + 1].timeMs, currentPeriodIndex = periodIdx + 1, hasFlagged = true)
                tickPlayer(next, -newTime, s, settings)
            } else {
                p.copy(hasFlagged = true).finalFlag(-newTime, settings.flagBehavior)
            }
        }
        TimerMode.HIDDEN -> {
            val newTime = p.timeRemainingMs - delta
            var revealUntil = (p.revealTimeUntilMs - delta).coerceAtLeast(0)
            var lastPerc = p.lastRevealPercentage
            if (newTime <= 0) {
                p.copy(revealTimeUntilMs = 0).finalFlag(-newTime, settings.flagBehavior)
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
            val effectiveDelta = if (s.fastMoveMode == FastMoveType.ACCELERATE && !p.isOutOfTime) {
                val timeSpent = p.initialTotalTimeMs - p.timeRemainingMs
                val accel = when {
                    timeSpent >= s.fastMoveFastPeriodMs -> s.fastMoveFullAccelRate
                    timeSpent >= s.fastMoveGracePeriodMs -> s.fastMoveAccelRate
                    else -> 0f
                }
                (delta * (1f + accel)).toLong()
            } else delta
            // Through FlagBehavior like every other clock. It was clamped at 0:00 whatever the setting:
            // under NEGATIVE and REVERSE no overrun was counted, so the move had no limit at all, and
            // the next tick, finding time "left" at zero, cleared the flag.
            val (newTime, isOut, isNeg) = applyFlagBehaviorDelta(p.timeRemainingMs, p.isOutOfTime, p.isNegative, effectiveDelta, settings.flagBehavior)
            p.copy(timeRemainingMs = newTime, isOutOfTime = isOut, isNegative = isNeg)
        }
        else -> {
            val (newTime, isOut, isNeg) = applyFlagBehaviorDelta(p.timeRemainingMs, p.isOutOfTime, p.isNegative, delta, settings.flagBehavior)
            p.copy(timeRemainingMs = newTime, isOutOfTime = isOut, isNegative = isNeg)
        }
    }
}

/**
 * Whether flags have ended the game. FREEZE stops at the first flag; FLAG plays on until one
 * player is left (or the only one is out); NEGATIVE and REVERSE never stop on a flag -- the
 * overrun is shown and play continues.
 */
internal fun flagsEndGame(players: List<PlayerState>, flagBehavior: FlagBehavior, numberOfPlayers: Int): Boolean {
    val out = players.count { it.isOutOfTime }
    return when (flagBehavior) {
        FlagBehavior.FREEZE -> out > 0
        FlagBehavior.FLAG -> if (numberOfPlayers <= 1) out > 0 else out >= numberOfPlayers - 1
        FlagBehavior.NEGATIVE, FlagBehavior.REVERSE -> false
    }
}

/**
 * Whether the game on the clock is over: a PHASES game that ran through its phases, or a flag
 * that [flagsEndGame] says stops play. The player who was to move stays active once it is, so
 * TalkBack went on telling them "your turn" -- "Player 1, out of time, your turn".
 */
fun isGameOver(state: ChessClockState, flagBehavior: FlagBehavior, numberOfPlayers: Int): Boolean =
    state.phasesFinished || flagsEndGame(state.players, flagBehavior, numberOfPlayers.coerceAtLeast(1))

/**
 * Who moves after [current]: the next player round the table, skipping those FLAG has eliminated.
 *
 * Turns went round every seat regardless, so with three or four players under FLAG an eliminated
 * player kept being handed the clock -- and since their clock no longer runs once out, each of
 * those turns was unlimited time. Under the other behaviours a player who is out still plays (the
 * overrun is just shown), and is not skipped.
 */
internal fun nextPlayerInTurn(current: Int, players: List<PlayerState>, playerCount: Int, flagBehavior: FlagBehavior): Int {
    if (playerCount <= 1) return 1
    val plainNext = (current % playerCount) + 1
    if (flagBehavior != FlagBehavior.FLAG) return plainNext
    var candidate = current
    repeat(playerCount) {
        candidate = (candidate % playerCount) + 1
        if (players.getOrNull(candidate - 1)?.isOutOfTime != true) return candidate
    }
    return plainNext
}

/**
 * Records who flagged first, if this state is where it happened.
 *
 * Only the per-player tick path set firstToFlag, and under FLAG the loss colour depends on it: in
 * HOURGLASS, CHRONO_COUNTDOWN and the shared move timers the player who flagged got the FLAGGED
 * label and never the colour. The active player is preferred when several go out at once -- one
 * shared CHRONO clock running out puts everyone out together.
 */
internal fun ChessClockState.withFirstFlag(before: ChessClockState, active: Int): ChessClockState {
    if (firstToFlag != null) return this
    val newlyOut = players.indices.filter { players[it].isOutOfTime && before.players.getOrNull(it)?.isOutOfTime != true }.map { it + 1 }
    if (newlyOut.isEmpty()) return this
    return copy(firstToFlag = if (active in newlyOut) active else newlyOut.first())
}

/**
 * What each other player gains in an HOURGLASS tick, by player index: exactly what the active
 * player lost, and only that.
 *
 * The share was the tick divided between the other players with integer division, and the
 * remainder went nowhere: at four players a 100ms tick handed out 99ms, and with hundredths showing
 * (10ms ticks) 9ms of every 10 -- a tenth of all the time in the game draining away. The remainder
 * now goes a millisecond at a time to whoever holds the least, which keeps the total exact without
 * favouring a seat.
 *
 * What the active player loses is the time actually taken off a clock with time on it: nothing once
 * it is out, so a flagged player's clock -- which no longer runs -- stops feeding the others. Those
 * eliminated under FLAG take no share either; they used to go on gaining, and with two players the
 * survivor's clock climbed for ever after the game had ended.
 */
private fun hourglassGains(players: List<PlayerState>, active: Int, before: PlayerState, after: PlayerState, flagBehavior: FlagBehavior): Map<Int, Long> {
    val had = if (before.isOutOfTime) 0L else before.timeRemainingMs
    val has = if (after.isOutOfTime) 0L else after.timeRemainingMs
    val lost = (had - has).coerceAtLeast(0)
    val recipients = players.indices.filter { it != active - 1 && !(flagBehavior == FlagBehavior.FLAG && players[it].isOutOfTime) }
    if (lost == 0L || recipients.isEmpty()) return emptyMap()
    val share = lost / recipients.size
    val remainder = (lost % recipients.size).toInt()
    val leastFirst = recipients.sortedBy { players[it].signedTimeMs(flagBehavior) }
    return recipients.associateWith { idx -> share + if (leastFirst.indexOf(idx) < remainder) 1 else 0 }
}

/** A player's state before and after a tick, for the audio and voice cues to compare. */
internal class TickCue(val before: PlayerState, val after: PlayerState, val playerIndex: Int)

/**
 * What [tickCoupled] decided: the next state, and the side effects the ViewModel is to carry out
 * for it -- a cue to compare for beeps and the voice, the gong, and in Shared (Auto) the player
 * whose move the clock has just ended ([handedOverBy]), for a beep and a MOVE in the log.
 */
internal class CoupledTick(
    val state: ChessClockState,
    val cue: TickCue? = null,
    val playGong: Boolean = false,
    val handedOverBy: Int? = null,
)

/**
 * One tick of the modes whose players are coupled -- MOVE_TIMER_SHARED, MOVE_TIMER_GLOBAL_SHARED,
 * HOURGLASS, CHRONO_COUNTDOWN, CHRONO_COUNTUP and GONG -- or null for any other mode.
 *
 * These lived inline in [ChessTimerViewModel.tick], with their sounds played from the middle of
 * the arithmetic, so nothing could reach them from a test: the tests ran a hand-written copy of
 * them instead, which is how the hourglass losing time and the shared modes' flag handling went
 * unnoticed. The arithmetic is here now, pure, and the effects come back as data for tick() to
 * perform. PHASES stays in the ViewModel, since its transitions go through methods that read the
 * settings flows.
 */
internal fun tickCoupled(state: ChessClockState, delta: Long, settings: ChessClockSettings, s: PlayerSettings, active: Int): CoupledTick? {
    when (s.mode) {
        TimerMode.MOVE_TIMER_SHARED -> {
            // With no move time at all, every tick would end a move and the turn would spin round
            // the table ten times a second. Nothing runs instead.
            if (s.moveTimeMs <= 0) return CoupledTick(state)
            val p1 = state.players[0]
            val newTime = p1.timeRemainingMs - delta
            if (newTime > 0) {
                return CoupledTick(state.copy(players = state.players.map { it.copy(timeRemainingMs = newTime) }), TickCue(p1, p1.copy(timeRemainingMs = newTime), active))
            }
            // The move time running out is the move: nobody presses in this mode, so it is the end
            // of a turn, not a flag. It used to be handed to the cues as one -- every turn logged as
            // FLAG "Time Up", with the triple beep and "time is up" spoken, and no MOVE ever
            // recorded. What the tick ran past zero is the next player's, as it is in every other
            // mode that carries a remainder on.
            val next = (active % settings.numberOfPlayers) + 1
            val carried = (s.moveTimeMs + newTime).coerceAtLeast(0)
            val newPlayers = state.players.mapIndexed { idx, p ->
                p.copy(timeRemainingMs = carried, moveCount = if (idx == active - 1) p.moveCount + 1 else p.moveCount)
            }
            return CoupledTick(
                state.copy(players = newPlayers, activePlayer = next, cycleCount = if (next == 1) state.cycleCount + 1 else state.cycleCount),
                handedOverBy = active,
            )
        }
        TimerMode.MOVE_TIMER_GLOBAL_SHARED -> {
            val pActive = state.players[active - 1]
            val (newMoveTime, outMove, moveNeg) = globalMoveClockTick(pActive, delta, settings.flagBehavior)
            // Under FLAG a player who is out has stopped playing, and the reserve is everyone's: it
            // went on draining all the same, until it emptied and put the survivors out with it --
            // the winner of a two-player game ended it flagged too.
            val drains = !(pActive.isOutOfTime && settings.flagBehavior == FlagBehavior.FLAG)
            val newGlobalTime = if (drains) (pActive.secondaryTimeMs - delta).coerceAtLeast(0) else pActive.secondaryTimeMs
            val outGlobal = newGlobalTime <= 0
            val fromZero = reverseTotalGone(pActive.secondaryTimeMs, newGlobalTime, settings.flagBehavior)
            val newPlayers = state.players.mapIndexed { idx, p ->
                if (idx + 1 == active) p.copy(timeRemainingMs = if (fromZero && !outMove) 0 else newMoveTime, isNegative = moveNeg, secondaryTimeMs = newGlobalTime, isOutOfTime = outMove || outGlobal)
                // A player keeps the flag their own move clock earned. This used to be the pool's
                // state alone, so whoever ran out of move time was "back" as soon as someone else
                // was on the clock -- under FLAG with three or four players no elimination stuck,
                // and the game could never reach its last player.
                else p.copy(secondaryTimeMs = newGlobalTime, isOutOfTime = p.isOutOfTime || outGlobal, timeRemainingMs = if (fromZero) 0 else p.timeRemainingMs)
            }
            return CoupledTick(
                state.copy(players = newPlayers, isPaused = settings.flagBehavior == FlagBehavior.FREEZE && (outMove || outGlobal)),
                TickCue(pActive.withBindingClock(), newPlayers[active - 1].withBindingClock(), active),
            )
        }
        TimerMode.HOURGLASS -> {
            val activeP = state.players[active - 1]
            val (newTime, isOut, isNeg) = applyFlagBehaviorDelta(activeP.timeRemainingMs, activeP.isOutOfTime, activeP.isNegative, delta, settings.flagBehavior)
            val newActiveP = activeP.copy(timeRemainingMs = newTime, isOutOfTime = isOut, isNegative = isNeg)
            val gains = hourglassGains(state.players, active, activeP, newActiveP, settings.flagBehavior)
            val newPlayers = state.players.mapIndexed { idx, p ->
                if (idx + 1 == active) newActiveP else gains[idx]?.let { p.credited(it, settings.flagBehavior) } ?: p
            }
            return CoupledTick(
                state.copy(players = newPlayers, isPaused = settings.flagBehavior == FlagBehavior.FREEZE && isOut),
                TickCue(activeP, newActiveP, active),
            )
        }
        TimerMode.CHRONO_COUNTDOWN -> {
            val p1 = state.players[0]
            val (newGlobal, isOut, isNeg) = applyFlagBehaviorDelta(state.globalTimeMs, p1.isOutOfTime, p1.isNegative, delta, settings.flagBehavior)
            val oldForAudio = p1.copy(timeRemainingMs = state.globalTimeMs)
            val newForAudio = p1.copy(timeRemainingMs = newGlobal, isOutOfTime = isOut, isNegative = isNeg)
            return CoupledTick(
                state.copy(globalTimeMs = newGlobal, players = state.players.map { it.copy(isOutOfTime = isOut, isNegative = isNeg) }, isPaused = settings.flagBehavior == FlagBehavior.FREEZE && isOut),
                TickCue(oldForAudio, newForAudio, 1),
            )
        }
        TimerMode.CHRONO_COUNTUP -> return CoupledTick(state.copy(globalTimeMs = state.globalTimeMs + delta))
        TimerMode.GONG -> {
            val isSim = s.gongSimultaneous
            val playersToUpdate = if (isSim) (1..settings.numberOfPlayers).toList() else listOf(active)
            var newState = state
            var gong = false
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
                        if (idx == active) gong = true
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
            // The cue for the player whose turn this tick was. GONG used to return none, so it was the
            // one mode with no countdown beeps and no voice at all -- its "Move" and "Reflect" lines
            // in handleVoice were dead code.
            return CoupledTick(newState, TickCue(state.players[active - 1], newState.players[active - 1], active), playGong = gong)
        }
        else -> return null
    }
}

/**
 * Pure post-move state transition (increments/resets/period-advances the mover after
 * [ChessTimerViewModel.startOrSwitch] records a move) for every mode driven by [applyPostMoveLogic].
 * Exposed at the top level for the same testability reason as [tickPlayer].
 */
/** Whether this player's [PlayerState.timeRemainingMs] is an overrun rather than time left. */
private fun PlayerState.isOverrun(flagBehavior: FlagBehavior): Boolean =
    isOutOfTime && (isNegative || flagBehavior == FlagBehavior.REVERSE)

/**
 * The remaining time with its sign. NEGATIVE and REVERSE store an overrun as a positive
 * [PlayerState.timeRemainingMs] -- NEGATIVE with [PlayerState.isNegative] beside it, REVERSE with
 * nothing but the flag -- and arithmetic on the bare field read the overrun as time left: an
 * increment pushed -0:05 to -0:07 instead of -0:03, and Save & Cap banked an overrun as savings.
 * Under REVERSE it still did, the sign being read off isNegative alone: a player who had flagged
 * was handed every increment, and in FIDE the whole of the next period at move 40, while still
 * shown as flagged.
 */
private fun PlayerState.signedTimeMs(flagBehavior: FlagBehavior): Long =
    if (isOverrun(flagBehavior)) -timeRemainingMs else timeRemainingMs

/**
 * Stores a signed remaining time back. Above zero again, an overrun player has time again and is
 * no longer out -- firstToFlag still records who flagged first. Only an overrun player can come
 * back this way; one eliminated under FLAG has no overrun and stays out.
 */
private fun PlayerState.withSignedTime(t: Long, flagBehavior: FlagBehavior): PlayerState = when {
    !isOverrun(flagBehavior) -> copy(timeRemainingMs = t)
    t > 0 -> copy(timeRemainingMs = t, isNegative = false, isOutOfTime = false)
    else -> copy(timeRemainingMs = -t)
}

/**
 * Gives a player [amountMs] (taken away when negative), counted against an overrun first.
 *
 * Both overflowing flag behaviours store the overrun as a positive [PlayerState.timeRemainingMs] --
 * NEGATIVE with [PlayerState.isNegative] beside it, REVERSE with nothing but the flag -- and the
 * hourglass and the arbiter's adjustment added to the bare field: a player at -0:02 handed a second
 * went to -0:03, so every second the opponent spent pushed them further under. Credit now pays the
 * overrun off, and past zero the player has time again and is no longer out, as [withSignedTime]
 * already allows. Anyone else keeps a clock that does not go below zero.
 */
internal fun PlayerState.credited(amountMs: Long, flagBehavior: FlagBehavior): PlayerState {
    if (!isOverrun(flagBehavior)) return copy(timeRemainingMs = (timeRemainingMs + amountMs).coerceAtLeast(0))
    val t = amountMs - timeRemainingMs
    return if (t > 0) copy(timeRemainingMs = t, isNegative = false, isOutOfTime = false) else copy(timeRemainingMs = -t)
}

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
        val fb = settings.flagBehavior
        // The time goes to whoever moves next. It went to the next seat, so with three or four
        // players under FLAG it could go to a player already eliminated, and the one about to move
        // got nothing.
        val recipient = nextPlayerInTurn(playerIndex, state.players, settings.numberOfPlayers, fb) - 1
        // Cumulative: the time spent is added to the opponent's, and the mover keeps what is left of
        // their own -- time moves across the table and nothing is lost. The mover's clock used to be
        // refilled to the move time all the same, so nothing ever accumulated: "cumulative" meant the
        // base time plus the last move. Not cumulative: the opponent gets exactly the time just
        // spent, and the mover starts again from the move time.
        val movedP = if (s.fastMoveTransferCumulative) tempP
            else tempP.copy(timeRemainingMs = s.moveTimeMs).let { if (p.isOverrun(fb)) it.copy(isNegative = false, isOutOfTime = false) else it }
        val newList = state.players.toMutableList().apply { this[playerIndex - 1] = movedP }
        if (recipient != playerIndex - 1) {
            val opponent = state.players[recipient]
            newList[recipient] = if (s.fastMoveTransferCumulative) opponent.credited(timeSpentOnMove, fb)
                else opponent.copy(timeRemainingMs = timeSpentOnMove).let { if (opponent.isOverrun(fb)) it.copy(isNegative = false, isOutOfTime = false) else it }
        }
        return state.copy(players = newList)
    }

    val newP = when (s.mode) {
        TimerMode.FISCHER -> tempP.withSignedTime(p.signedTimeMs(settings.flagBehavior) + s.incrementMs, settings.flagBehavior)
        TimerMode.RANDOM, TimerMode.HIDDEN -> tempP.withSignedTime(p.signedTimeMs(settings.flagBehavior) + p.secondaryTimeMs, settings.flagBehavior)
        TimerMode.BRONSTEIN -> tempP.withSignedTime(p.signedTimeMs(settings.flagBehavior) + timeSpentOnMove.coerceAtMost(s.incrementMs), settings.flagBehavior)
        // Every move starts a fresh allowance, and an overrun of the last one is over with it. The
        // reset restored the time and left isOutOfTime/isNegative set, so under NEGATIVE every
        // later turn counted up from -0:30 and the player could never flag again. Under REVERSE,
        // whose overrun has no isNegative to clear, it still did.
        TimerMode.MOVE_TIMER_STANDARD -> if (p.isOverrun(settings.flagBehavior)) tempP.copy(timeRemainingMs = s.moveTimeMs, isNegative = false, isOutOfTime = false) else tempP.copy(timeRemainingMs = s.moveTimeMs)
        // A fresh move clock; an overrun of the last one ends with it, and the player stays out
        // only if the total (or the shared pool) is gone too. REVERSE marks the overrun with the
        // flag alone, which no reset cleared: the player stayed FLAGGED on every later move. Once
        // the total is gone there is no move time left to give, and the next overrun counts from zero.
        TimerMode.MOVE_TIMER_GLOBAL, TimerMode.MOVE_TIMER_GLOBAL_SHARED -> when {
            p.isNegative -> tempP.copy(timeRemainingMs = s.moveTimeMs, isNegative = false, isOutOfTime = p.secondaryTimeMs <= 0)
            settings.flagBehavior == FlagBehavior.REVERSE && p.isOutOfTime ->
                if (p.secondaryTimeMs > 0) tempP.copy(timeRemainingMs = s.moveTimeMs, isOutOfTime = false) else tempP.copy(timeRemainingMs = 0)
            else -> tempP.copy(timeRemainingMs = s.moveTimeMs)
        }
        // A fresh move clock. Under NEGATIVE and REVERSE play goes on past an empty reserve, so the
        // overrun ends with the move and the next one counts down again -- straight into a new
        // overrun at zero, the reserve being gone. Under FLAG the player stays out.
        TimerMode.MOVE_TIMER_OVERTIME ->
            if (p.isOutOfTime && (settings.flagBehavior == FlagBehavior.NEGATIVE || settings.flagBehavior == FlagBehavior.REVERSE))
                tempP.copy(timeRemainingMs = s.moveTimeMs, isOutOfTime = false, isNegative = false)
            else tempP.copy(timeRemainingMs = s.moveTimeMs)
        TimerMode.MOVE_TIMER_SAVE_CAP -> {
            // What is left of the pot at the moment of the press. SAVE_CAP is not named in
            // tickPlayer, so it burns through the generic branch as a single number: timeRemainingMs
            // is already moveTimeMs plus the bank, minus whatever this turn cost.
            //
            // That is why the bank must not be added again. It used to be -- `secondaryTimeMs +
            // timeRemainingMs` -- which re-credited the whole previous bank every move, so from the
            // second move on the bank grew by itself: with 30s moves and 10s spent, move two banked
            // 60s where 40s were left. The bank drove what the clock showed, so the player
            // watched a number that had stopped meaning anything.
            // Signed: under NEGATIVE an overrun is stored as a positive number, and this banked it
            // -- forty seconds on a thirty-second move started the next turn at 0:40.
            val carried = p.signedTimeMs(settings.flagBehavior).coerceAtLeast(0)
            // The cap is the ceiling on the clock as a whole, not on the bank alone: with 30s moves
            // and a 2:00 cap the clock stops at 2:00, never 2:30. A cap below the move time cannot
            // bind without handing the player less than one move, so it is floored there -- which
            // makes a cap of zero mean "save nothing", i.e. plain MOVE_TIMER_STANDARD.
            val cap = s.timeCapMs.coerceAtLeast(s.moveTimeMs)
            val newTotal = (s.moveTimeMs + carried).coerceAtMost(cap)
            val saved = tempP.copy(timeRemainingMs = newTotal, secondaryTimeMs = (newTotal - s.moveTimeMs).coerceAtLeast(0))
            if (p.isOverrun(settings.flagBehavior)) saved.copy(isNegative = false, isOutOfTime = false) else saved
        }
        // A move refills a period only for a player who still has one: after the last has run out,
        // under NEGATIVE, a move would otherwise hand a fresh period to someone already out.
        TimerMode.BYOYOMI_JAPANESE -> if (p.isInByoyomi && !p.isOutOfTime) tempP.copy(timeRemainingMs = s.byoyomiTimeMs) else tempP
        // A block is at least one move. A quota of 0 renewed the block on every move -- a full block
        // of time per move, for the rest of the game -- while the face read "MOVES: 0".
        TimerMode.BYOYOMI_CANADIAN -> if (p.isInByoyomi && !p.isOutOfTime) { val rem = p.movesRemainingInPeriod - 1; if (rem <= 0) tempP.copy(timeRemainingMs = s.byoyomiTimeMs, movesRemainingInPeriod = s.byoyomiPeriods.coerceAtLeast(1)) else tempP.copy(movesRemainingInPeriod = rem) } else tempP
        // The goal grows by the progression each block, and saturates: added as plain Ints, a large
        // progression wrapped it negative, and every move after renewed the block.
        TimerMode.BYOYOMI_PROGRESSIVE -> if (p.isInByoyomi && !p.isOutOfTime) { val rem = p.movesRemainingInPeriod - 1; if (rem <= 0) { val goal = (p.currentByoyomiMovesGoal.toLong() + s.byoyomiProgression).coerceIn(1, MAX_BYOYOMI_COUNT.toLong()).toInt(); tempP.copy(timeRemainingMs = s.byoyomiTimeMs, movesRemainingInPeriod = goal, currentByoyomiMovesGoal = goal) } else tempP.copy(movesRemainingInPeriod = rem) } else tempP
        TimerMode.FIDE_PERIODS -> {
            val currentPeriod = s.fidePeriods.getOrNull(p.currentPeriodIndex) ?: FidePeriod()
            // Signed, as for the increment modes: under NEGATIVE an overrun is stored positive.
            var nextTime = p.signedTimeMs(settings.flagBehavior)
            var periodIdx = p.currentPeriodIndex
            val flagged = p.hasFlagged
            var activePeriod = currentPeriod
            if (settings.forcedMoveCounter && currentPeriod.movesToNext > 0 && tempP.moveCount >= currentPeriod.movesToNext && p.currentPeriodIndex < s.fidePeriods.size - 1) {
                val nextIdx = p.currentPeriodIndex + 1
                val nextPeriod = s.fidePeriods[nextIdx]
                nextTime += nextPeriod.timeMs
                periodIdx = nextIdx
                // Not a flag: reaching the move count is how a period is meant to end. Setting
                // hasFlagged here put a red PERIOD FLAG under the clock of everyone who reached
                // move 40 with time to spare, for the rest of the game. Without the move counter
                // a period ends by running out, and tickPlayer still marks that one.
                activePeriod = nextPeriod
            }
            // The bonus is for the move to come, as a FIDE clock gives it: the DGT2500 adds it
            // "before each move from the start of the game", so the first is on the clock before
            // move 1 (createInitialState) and each press adds the one for the player's next move,
            // from the period that move is played in. It was added after each move instead, from
            // the period just finished, so the player was always one bonus short -- 90:00 rather
            // than 90:30 to start a 90' + 30s game -- and a bonus that begins with a period, as in
            // the DGT manual's "30 seconds from move 61", began a move late.
            if (activePeriod.isFischer) nextTime += activePeriod.incrementMs
            // Non-Fischer periods can optionally carry a per-move delay (US Chess Delay-style), reusing the
            // same generic delayRemainingMs mechanism US_DELAY uses. hasDelay distinguishes plain Sudden
            // Death (no delay) from US Delay -- both are non-Fischer, only the latter reads incrementMs.
            val nextDelay = if (!activePeriod.isFischer && activePeriod.hasDelay) activePeriod.incrementMs else 0L
            tempP.copy(currentPeriodIndex = periodIdx, hasFlagged = flagged, delayRemainingMs = nextDelay).withSignedTime(nextTime, settings.flagBehavior)
        }
        TimerMode.FAST_MOVE -> {
            // TRANSFER is handled above (needs to update the opponent too); only SHRINK/ACCELERATE reach here.
            // Each move starts a fresh allowance, so an overrun of the last one ends with it.
            val fresh = if (p.isOverrun(settings.flagBehavior)) tempP.copy(isNegative = false, isOutOfTime = false) else tempP
            if (s.fastMoveMode == FastMoveType.SHRINK) {
                val nextTurnStartTime = (p.initialTotalTimeMs - s.fastMoveShrinkDecrementMs).coerceAtLeast(s.fastMoveShrinkFloorMs)
                fresh.copy(timeRemainingMs = nextTurnStartTime, initialTotalTimeMs = nextTurnStartTime)
            } else {
                fresh.copy(timeRemainingMs = s.initialTimeMs, initialTotalTimeMs = s.initialTimeMs)
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
     * When the clock was paused, or null while it runs. resume() moves [moveStartTime] forward by
     * the length of the pause, so a move's duration is the time the clock actually ran for it.
     *
     * Nothing ever adjusted moveStartTime for a pause, so a paused span counted as thinking time:
     * Bronstein gave back up to its whole increment for time never used -- think 1s, pause a
     * minute, press, and gain 2s of a 3s increment -- FAST_MOVE TRANSFER handed the pause to the
     * opponent, and every log, chart and statistic since v0.8.13 counted it as the move.
     */
    private var pausedAt: Long? = null

    /**
     * Notation reported by a linked board that no MOVE event has claimed yet. See
     * [BoardMoveOutcome]; the next clock press consumes it. Only ever holds the most recent one --
     * if the board reports twice before the player presses, the first is stale.
     */
    private var pendingBoardNotation: String? = null
    private val soundManager = SoundManager(application)
    private val voiceManager = VoiceManager(application)
    // Built on first use, not on construction.
    //
    // Every consumer of this ViewModel creates it on first composition -- all four app flavours and
    // paper -- and these three were eager fields whose flows were combined in init, so nothing could
    // avoid them. That meant Mini, Lite, Standard and paper each resolved a BluetoothAdapter,
    // obtained a UsbManager and registered a USB broadcast receiver at every launch, for hardware
    // whose permissions their manifests remove outright and which they ship no screen to reach.
    // BluetoothBoardScreen in app/src/complete is the only file in either module that touches them.
    //
    // The delegates are kept so onCleared can ask whether there is anything to release rather than
    // constructing all three in order to shut them down.
    private val bluetoothManagerDelegate = lazy { BluetoothBoardManager(application) }
    private val usbBoardManagerDelegate = lazy { UsbBoardManager(application) }
    private val bluetoothSerialBoardManagerDelegate = lazy { BluetoothSerialBoardManager(application) }
    val bluetoothManager: BluetoothBoardManager by bluetoothManagerDelegate
    val usbBoardManager: UsbBoardManager by usbBoardManagerDelegate
    val bluetoothSerialBoardManager: BluetoothSerialBoardManager by bluetoothSerialBoardManagerDelegate
    private var lastBeepSecond: Long = -1
    private var lastAutoSaveTime: Long = 0
    

    private val _gameHistory = MutableStateFlow<List<GameLog>>(emptyList())
    val gameHistory: StateFlow<List<GameLog>> = _gameHistory.asStateFlow()
    private var currentLog: GameLog? = null
    private var lastRandomRoll: Pair<Long, Long>? = null

    /**
     * Writes to the game history, applied one at a time in the order they were asked for.
     *
     * The game being played used to exist only in memory until someone pressed Reset, so a game
     * that ended any other way -- a flag and then the app swiped away, the system reclaiming the
     * process overnight, a crash -- was never stored at all. It is now written again after every
     * event, so what is lost is at most the time since the last press.
     *
     * One consumer, because the writes are launched from the main thread and encoded off it: two
     * launches could finish in either order and leave an older copy of the log on top of a newer
     * one, or let the trim that ends a game run before that game's last write.
     */
    private sealed interface HistoryWrite {
        class Store(val log: GameLog) : HistoryWrite
        class Finish(val limit: Int, val durationLimit: LogDurationLimit, val finishedLogId: String, val discard: Boolean = false) : HistoryWrite
        // The saved clock goes through the same queue so that a save and a clear land in the
        // order they were asked for; see savedClockGeneration.
        class SaveClock(val entity: com.masterclock.app.data.SavedClockEntity) : HistoryWrite
        object ClearClock : HistoryWrite
    }

    /**
     * Bumped whenever the saved clock is cleared, so that an autosave taken before the clear is
     * dropped instead of written after it. The autosave encodes off the main thread, and a Reset
     * landing in that window used to clear the clock first and then have the stale snapshot
     * written over the clear -- "Resume" came back for the game just reset. Read and written on
     * the main thread only.
     */
    private var savedClockGeneration = 0
    private val historyWrites = Channel<HistoryWrite>(Channel.UNLIMITED)

    private val _hasSavedClock = MutableStateFlow(false)
    val hasSavedClock: StateFlow<Boolean> = _hasSavedClock.asStateFlow()

    private val _scoreboard = MutableStateFlow(ScoreboardSession())
    val scoreboard: StateFlow<ScoreboardSession> = _scoreboard.asStateFlow()

    private val _customPresets = MutableStateFlow<List<SavedPreset>>(emptyList())
    val customPresets: StateFlow<List<SavedPreset>> = _customPresets.asStateFlow()

    /**
     * The limit to pass to `trimLogs`, or null when the history is unlimited and nothing is trimmed.
     *
     * [loadHistory] clamps the same number for reads and this path did not, which is what let a
     * stored `logHistoryLimit` of 0 delete the whole history at the end of every game while the
     * clamp on the read side hid the symptom: `DELETE ... WHERE id NOT IN (SELECT id ... LIMIT 0)`
     * has a subquery that selects nothing, so every row matches. A trim is destructive and a read is
     * not, so if either of the two was going to be the unguarded one, it should not have been this.
     */
    private fun trimLimitFor(limit: Int): Int? =
        if (limit == -1) null else limit.coerceIn(1, MAX_UNLIMITED_HISTORY)

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
        // Any non-positive value, not just -1: SQLite reads every negative LIMIT as no limit at
        // all, so testing for -1 alone left -2 free to load the entire table. sanitizeImportedSettings
        // now refuses such a value on the way in as well, and this is the backstop.
        val effective = if (limit <= 0) MAX_UNLIMITED_HISTORY else limit.coerceAtMost(MAX_UNLIMITED_HISTORY)
        val entities = gameDao.getRecentLogs(effective)
        return withContext(Dispatchers.Default) { entities.map { converters.toGameLog(it) } }
    }

    /**
     * True once everything a screen can change has been read back: settings, presets, scoreboard,
     * and whether a clock is saved. Both activities show nothing interactive before it.
     *
     * The screens used to be live on default settings for as long as the load took, and every
     * tap in that window acted on the defaults and was then lost or worse when the load landed
     * on top of it: the arbitre panel opened and shut again; a press started a game the load
     * wiped, while its log lived on and claimed the next game; a settings change was written to
     * disk built on the defaults; and saving a preset stored a list of one over every preset the
     * user had, since the list had not been read yet. The game history comes after this flag --
     * it can be large, nothing on the clock screen waits for it, and the screens that show it fill
     * in when it arrives.
     */
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    /** Set once the init block below has finished, history included; see [applyShortcut]. */
    private var isLoaded = false
    private var pendingShortcutId: String? = null

    init {
        // A notation waiting for a press must not outlive the board that supplied it. Held while
        // auto-switch is off, it is claimed by the player's next press -- and if the board goes out
        // of range or is unplugged in between, that press might be minutes later, or in another
        // game entirely. The move recorded would name something nobody played, in an exported PGN,
        // with nothing on screen to suggest anything had gone wrong.
        //
        // Reading the three flows is what used to force all three transports into existence on
        // every launch of every build, so the collector only starts where a board can be connected
        // in the first place. In a build with no board screen there is never a pending notation to
        // drop.
        if (FlavorConfig.hasBoards()) {
            viewModelScope.launch {
                combine(
                    bluetoothManager.connectionState,
                    usbBoardManager.connectionState,
                    bluetoothSerialBoardManager.connectionState,
                ) { states -> states.any { it is ConnectionState.Connected } }
                    .distinctUntilChanged()
                    .collect { anyBoardConnected -> if (!anyBoardConnected) pendingBoardNotation = null }
            }
        }

        viewModelScope.launch {
            for (write in historyWrites) {
                try {
                    applyHistoryWrite(write)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // One failed write must not stop the ones queued behind it.
                    Log.w("ChessTimerViewModel", "Failed to write game history", e)
                }
            }
        }

        viewModelScope.launch {
            // DataStore is where an import writes, so what comes back out of it is exactly as
            // untrusted as what went in -- and until now nothing looked at it again on the way
            // back. Anything a pre-0.8.29 import left behind is repaired here, once, and written
            // back so the repair sticks and so the next export carries the repaired values rather
            // than the poisoned ones. Decoding and checking both happen off the main thread: the
            // decode is settingsFlow's map, which runs in whatever context collects it, so reading
            // it from viewModelScope parsed the whole notebook on the UI thread at every launch.
            val (storedSettings, savedSettings) = withContext(Dispatchers.Default) {
                val stored = settingsRepo.settingsFlow.first()
                stored to sanitizeStoredSettings(getApplication(), stored).wholeTableSettings()
            }
            if (savedSettings != storedSettings) {
                Log.i("ChessTimerViewModel", "Repaired stored settings that failed validation")
                settingsRepo.saveSettings(savedSettings)
            }
            _settings.value = savedSettings
            soundManager.loadSounds(savedSettings)
            _uiState.value = createInitialState(savedSettings)
            // Sanitised like an import: it is the same shape of data, and a backup restore is what
            // usually put it there.
            _scoreboard.value = sanitizeImportedScoreboard(settingsRepo.scoreboardFlow.first())
            _hasSavedClock.value = gameDao.getSavedClock() != null
            _customPresets.value = settingsRepo.customPresetsFlow.first()
            _isReady.value = true

            _gameHistory.value = loadHistory(savedSettings.logHistoryLimit)

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
                    if (s1.roundedTime) drawInRange(s1.randomMinTimeMs, s1.randomMaxTimeMs, 10_000L, 1000L)
                    else drawInRange(s1.randomMinTimeMs, s1.randomMaxTimeMs, 1000L, 1000L)
                } catch (_: Exception) { 600_000L }
                sharedRandomInc = try {
                    if (s1.roundedTime) drawInRange(s1.randomMinIncMs, s1.randomMaxIncMs, 1000L, 100L)
                    else drawInRange(s1.randomMinIncMs, s1.randomMaxIncMs, 100L, 100L)
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
                TimerMode.BYOYOMI_CANADIAN, TimerMode.BYOYOMI_PROGRESSIVE -> pSettings.byoyomiPeriods.coerceAtLeast(1).let { quota -> PlayerState(timeRemainingMs = pSettings.initialTimeMs, movesRemainingInPeriod = quota, currentByoyomiMovesGoal = quota) }
                TimerMode.MOVE_COUNTS_UP -> PlayerState(timeRemainingMs = 0, moveCount = 0)
                TimerMode.MOVE_COUNTS_DOWN -> PlayerState(timeRemainingMs = 0, moveCount = pSettings.maxMoves)
                TimerMode.FIDE_PERIODS -> {
                    val first = pSettings.fidePeriods.firstOrNull() ?: FidePeriod()
                    PlayerState(
                        // The bonus for move 1; see computePostMoveState.
                        timeRemainingMs = first.timeMs + if (first.isFischer) first.incrementMs else 0L,
                        currentPeriodIndex = 0,
                        // Same rule computePostMoveState applies to every later move. Without it the
                        // first move of each player had no delay at all -- delayRemainingMs took
                        // PlayerState's default of 0, and startClock arms a delay only for
                        // US_DELAY -- so a delay period granted its delay from move 2 onwards. On
                        // the built-in "US 80'/40 + 30' + 30s" that is 30 seconds each, taken from
                        // the main clock on the one move where a player is least likely to notice.
                        delayRemainingMs = if (!first.isFischer && first.hasDelay) first.incrementMs else 0L,
                    )
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

        return ChessClockState(players = playerList, globalTimeMs = initialGlobalTimeMs(s1))
    }

    /**
     * Charges the running clock up to this instant, before a press or a pause stops it.
     *
     * The clock is charged a tick at a time, and stopping it used to cancel the ticker and move
     * on: whatever had passed since the last tick -- up to 100ms, 10ms with hundredths showing --
     * was charged to no one. About three seconds a player over a sixty-move game, and in Bronstein
     * the credit, measured by wall time, could exceed what had been charged. A player with 80ms
     * left who pressed before the next tick never flagged; now the flag lands first, and under
     * FREEZE the press is refused, as it would be on a board.
     */
    private fun settleRunningClock() {
        if (timerJob != null && !_uiState.value.isPaused) tick()
    }

    fun startOrSwitch(playerIndex: Int, boardNotation: String? = null) {
        val beforeSettle = _uiState.value
        settleRunningClock()
        val currentState = _uiState.value
        if (currentState.isArbitreMode) return

        val s = getPlayerSettings(playerIndex)

        // With no log, addEvent and finishCurrentLog have nothing to act on, so a build that keeps
        // no history records nothing at all.
        if (currentLog == null && FlavorConfig.keepsGameHistory()) {
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
            val p1 = currentState.players[0]
            // Over -- finished, or flagged by its phase. A tap used to resume a flagged game, and the
            // next one to flag it again.
            if (p1.isOutOfTime) return
            if (currentState.isPaused) { resume(); return }
            // The tick settleRunningClock ran just now ended the pause or the phase this tap was
            // meant for. Acting on it as well skipped the phase that had only just begun.
            val tapped = beforeSettle.players[0]
            if (tapped.currentPhaseIndex != p1.currentPhaseIndex || tapped.isInInterPhasePause != p1.isInInterPhasePause || tapped.isPhaseAwaitingTap != p1.isPhaseAwaitingTap) return
            when {
                // A tap during the pause between phases skips it. It went to startPhaseTransition,
                // which starts the pause -- so a tap with five seconds of it left put it back to
                // its full length, and the pause could never be skipped at all.
                p1.isInInterPhasePause -> _uiState.update { st -> performPhaseAdvance(st, 1).also { onPhaseTransition(st.players[0], it) } }
                // Waiting for a tap at a phase's end is the pause between phases, held until the tap.
                // The tap went through startPhaseTransition, which then started the timed pause as
                // well: the game waited twice over before the next phase.
                p1.isPhaseAwaitingTap -> _uiState.update { st -> performPhaseAdvance(st, 1).also { onPhaseTransition(st.players[0], it) } }
                settings.value.allowPhaseSkip -> _uiState.update { st -> startPhaseTransition(st, skipped = true).also { onPhaseTransition(st.players[0], it) } }
            }
            return
        }

        val playerCount = settings.value.numberOfPlayers.coerceAtLeast(1)
        if (flagsEndGame(currentState.players, settings.value.flagBehavior, playerCount)) return

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
        
        val nextPlayer = nextPlayerInTurn(playerIndex, currentState.players, playerCount, settings.value.flagBehavior)
        
        if (currentState.activePlayer == null) {
            moveStartTime = SystemClock.elapsedRealtime(); pausedAt = null; startClock(nextPlayer)
            if (settings.value.playSwitchSound) soundManager.playSwitch()
            addEvent(GameEvent(eventType = "INITIAL_PRESS", playerIndex = playerIndex, detail = "P$nextPlayer clock started"))
        } else {
            val p = currentState.players.getOrNull(playerIndex - 1) ?: return
            val timeSpentOnMove = SystemClock.elapsedRealtime() - moveStartTime
            // A press with no notation of its own claims whatever the board reported since the last
            // move, which is how the notation reaches the PGN when auto-switch is off.
            val notation = boardNotation ?: pendingBoardNotation
            pendingBoardNotation = null
            // An overrun is stored as a positive time, and was logged as time left: a player two
            // minutes over counted, in the statistics, as one with two minutes to spare.
            addEvent(GameEvent(eventType = "MOVE", playerIndex = playerIndex, timeRemainingMs = p.signedTimeMs(settings.value.flagBehavior).coerceAtLeast(0), moveCount = p.moveCount + 1, moveNotation = notation, timeSpentMs = timeSpentOnMove))
            val flaggedByMove = applyPostMoveLogic(playerIndex, timeSpentOnMove)
            // Under FREEZE a flag stops the game, exactly as tick() stops it for one on the clock.
            if (flaggedByMove && settings.value.flagBehavior == FlagBehavior.FREEZE) {
                timerJob?.cancel()
                timerJob = null
                _uiState.update { it.copy(isPaused = true) }
                return
            }
            moveStartTime = SystemClock.elapsedRealtime(); startClock(nextPlayer)
            if (settings.value.playSwitchSound) soundManager.playSwitch()
        }
    }

    /**
     * The same ceiling an imported log gets, applied to the game being played.
     *
     * A move read from a connected board carries a string the board chose, and the board may be
     * hostile or simply broken; nothing else here bounds how many events one game can produce.
     * Past the cap the game keeps running and the clock keeps working -- only the recording stops
     * growing, which is the right thing to lose.
     *
     * The notation is truncated for the same reason it is on import: the longest real SAN, USI or
     * PDN move is far shorter, and the raw-capture protocol turns whatever arrives into hex.
     */
    private fun addEvent(event: GameEvent) {
        val log = currentLog ?: return
        if (log.events.size >= MAX_IMPORTED_EVENTS_PER_LOG) return
        val updated = log.copy(
            events = log.events + event.copy(
                moveNotation = event.moveNotation?.take(MAX_MOVE_NOTATION_CHARS),
                detail = event.detail?.take(MAX_IMPORTED_EVENT_DETAIL_CHARS),
            )
        )
        currentLog = updated
        historyWrites.trySend(HistoryWrite.Store(updated))
    }

    /**
     * Starts the clock for [playerIndex].
     *
     * [armDelay] is what separates a turn beginning from a clock merely being restarted. US Delay
     * grants its delay once per move, and this function refilled it unconditionally -- while
     * resume() calls it too. So pausing and pressing play handed the mover back a delay they had
     * already spent, as often as they cared to, and with pauseOnBackground on, locking the screen
     * did the same. Only a real turn change arms it now; resume() puts the clock back exactly as it
     * was found.
     */
    private fun startClock(playerIndex: Int, armDelay: Boolean = true) {
        timerJob?.cancel()
        timerJob = null
        lastBeepSecond = -1
        _uiState.update { state ->
            val s = getPlayerSettings(playerIndex)
            val newPlayers = state.players.mapIndexed { idx, p ->
                if (armDelay && idx + 1 == playerIndex && s.mode == TimerMode.US_DELAY) p.copy(delayRemainingMs = s.incrementMs) else p
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
     * Whether hundredths are on screen is [showsHundredthsAt]'s question, and asking it there is
     * what keeps this from being a second copy of the display's condition.
     */
    private fun tickIntervalMs(): Long {
        val settings = _settings.value
        if (!settings.showHundredths) return SLOW_TICK_MS

        val state = _uiState.value
        val active = state.activePlayer ?: return SLOW_TICK_MS
        val player = state.players.getOrNull(active - 1) ?: return SLOW_TICK_MS
        val remaining = displayedTimeMs(state, player, getPlayerSettings(active).mode)
        return if (settings.showsHundredthsAt(kotlin.math.abs(remaining))) FAST_TICK_MS else SLOW_TICK_MS
    }

    private fun tick() {
        val now = SystemClock.elapsedRealtime(); val delta = now - lastTickTime; lastTickTime = now
        
        // Saved so a game survives the app being killed. Every five seconds meant serialising the
        // settings and the whole state, then writing to the database, some 1,400 times over a
        // two-hour game -- for a recovery that almost never happens. Fifteen bounds what a crash
        // costs to fifteen seconds of one player's clock, which is a fair price for a third of the
        // writes.
        // Only the arbitre panel can resume a saved clock, so a build without it has no use for
        // the snapshot -- Mini and the E-Ink build wrote one every fifteen seconds all the same.
        if (FlavorConfig.hasArbitre() && now - lastAutoSaveTime >= AUTO_SAVE_INTERVAL_MS) {
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
                    // Waiting for the tap that ends it (startOrSwitch); nothing runs.
                    if (settings.pauseUntilTap) return@update state
                    val newPause = p1.pauseTimeRemainingMs - delta
                    if (newPause <= 0) return@update performPhaseAdvance(state, 1).carryPhaseOvershoot(-newPause).also { onPhaseTransition(p1, it) }
                    return@update state.copy(players = state.players.toMutableList().apply { this[0] = p1.copy(pauseTimeRemainingMs = newPause) })
                }
                // Waiting for the tap that moves on to the next phase, or over: nothing runs.
                if (p1.isPhaseAwaitingTap || p1.isOutOfTime) return@update state

                val currentPhase = s.phases.getOrNull(p1.currentPhaseIndex) ?: GamePhase()
                val newGlobal = state.globalTimeMs - delta
                if (newGlobal <= 0) {
                    // The last phase of a game that does not loop has no next phase to wait for, so
                    // it ends the game as auto-advance would -- finished, or flagged if the phase
                    // says so. It used to wait, flagged, for a tap that could only finish it, and the
                    // finish then went unannounced. A phase that flags on its end flags as its time
                    // runs out, not on the tap after.
                    val isLastPhase = p1.currentPhaseIndex >= s.phases.size - 1 && !settings.loopPhases
                    if (currentPhase.autoAdvance || isLastPhase || currentPhase.flagOnEnd) {
                        return@update startPhaseTransition(state).carryPhaseOvershoot(-newGlobal).also { onPhaseTransition(p1, it) }
                    }
                    val waiting = state.copy(globalTimeMs = 0, players = state.players.toMutableList().apply { this[0] = p1.copy(isPhaseAwaitingTap = true) })
                    return@update waiting.also { onPhaseTransition(p1, it) }
                }
                handleAudio(p1.copy(timeRemainingMs = state.globalTimeMs), p1.copy(timeRemainingMs = newGlobal), settings, 1)
                handleVoice(p1.copy(timeRemainingMs = state.globalTimeMs), p1.copy(timeRemainingMs = newGlobal), settings, 1)
                return@update state.copy(globalTimeMs = newGlobal)
            }


            tickCoupled(state, delta, settings, s, active)?.let { result ->
                result.cue?.let { cue ->
                    handleAudio(cue.before, cue.after, settings, cue.playerIndex)
                    handleVoice(cue.before, cue.after, settings, cue.playerIndex)
                }
                if (result.playGong) soundManager.playGong()
                result.handedOverBy?.let { mover ->
                    // A plain beep where a press would have made the switch sound, and nothing
                    // spoken: the turn passing is the ordinary course of this mode.
                    if (settings.playSwitchSound) soundManager.playShortBeep()
                    addEvent(GameEvent(eventType = "MOVE", playerIndex = mover, timeRemainingMs = 0, moveCount = result.state.players[mover - 1].moveCount, timeSpentMs = s.moveTimeMs))
                    moveStartTime = now
                }
                return@update result.state.withFirstFlag(state, active)
            }

            val pActive = state.players[active - 1]
            val newStateP = tickPlayer(pActive, delta, s, settings)
            val (cueBefore, cueAfter) = if (s.mode == TimerMode.MOVE_TIMER_GLOBAL) pActive.withBindingClock() to newStateP.withBindingClock() else pActive to newStateP
            handleAudio(cueBefore, cueAfter, settings, active)
            handleVoice(cueBefore, cueAfter, settings, active)
            
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
        // Under NEGATIVE and REVERSE the clock runs on past the flag, counting the overrun, and its
        // first seconds used to beep as if they were the last ones left.
        if (newState.isOutOfTime) return

        // A FIDE period that ran out on its time -- the move count ends one in a press, never here.
        // The screen marked it PERIOD FLAG and nothing else did: no sound, and no trace in the log
        // of a flag an arbiter may need to know fell. A plain beep, not the flag's triple one: the
        // player plays on in the next period.
        if (newState.currentPeriodIndex > oldState.currentPeriodIndex) {
            soundManager.playShortBeep()
            addEvent(GameEvent(eventType = "PERIOD", playerIndex = playerIdx, detail = "Period ${oldState.currentPeriodIndex + 1} ran out"))
            lastBeepSecond = -1
            return
        }
        // A Japanese byoyomi period run out, the clock starting the next: it changed in silence,
        // with no trace in the log, and the player learned of it only by the clock jumping back.
        if (newState.isInByoyomi && oldState.isInByoyomi && newState.byoyomiPeriodsRemaining < oldState.byoyomiPeriodsRemaining) {
            soundManager.playShortBeep()
            addEvent(GameEvent(eventType = "PERIOD", playerIndex = playerIdx, detail = "Byoyomi period lost, ${newState.byoyomiPeriodsRemaining} left"))
            lastBeepSecond = -1
            return
        }

        // Hidden mode hides the clock, and a countdown beep in the last seconds says exactly what
        // the screen withholds. The flag above is shown on screen anyway, so it still sounds.
        if (getPlayerSettings(playerIdx).mode == TimerMode.HIDDEN) return

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

    /**
     * Sound and voice for a PHASES transition, from [before] to [after] (both Player 1's state, which
     * carries the phase).
     *
     * Transitions never went through tick()'s audio: a phase that advanced on its own changed in
     * silence, the end of a non-looping game sounded no flag and logged no FLAG event, and the
     * voice line naming the next phase could never fire -- handleVoice was only ever handed two
     * states of the same phase. Now: the end of the game sounds and is logged like any flag; the
     * start of a pause between phases sounds the gong; and a new phase sounds the gong (unless a
     * pause already did) and has its name spoken.
     *
     * A game that ran out of phases rather than flagged ([ChessClockState.phasesFinished]) sounds
     * and is logged the same, but the voice says it is over instead of that time is up.
     *
     * The cue between phases is a single beep. It was the gong, which belongs to the Gong mode and
     * only there. A phase whose time is up and that waits for a tap sounds the beep as it stops; the
     * tap that moves on then only names the next phase, as the end of a pause does.
     *
     * Every phase change is logged as a PHASE event. A game that never flagged logged nothing but
     * its start, pauses and reset, so finishCurrentLog took it for a game that went nowhere and
     * deleted it: an hour of looping phases left no trace in the history.
     *
     * The countdown beeps start afresh with each phase: the last second beeped in one phase could
     * otherwise be taken for the same second of the next and stay silent.
     */
    private fun onPhaseTransition(before: PlayerState, afterState: ChessClockState) {
        val settings = _settings.value
        val after = afterState.players[0]
        lastBeepSecond = -1
        when {
            after.isOutOfTime -> if (!before.isOutOfTime) {
                handleAudio(before, after, settings, 1)
                if (!afterState.phasesFinished) handleVoice(before, after, settings, 1)
                else if (settings.voiceAnnouncementsEnabled) voiceManager.speak(CoreR.string.voice_finished, settings.voiceVolume)
            }
            after.isPhaseAwaitingTap -> if (!before.isPhaseAwaitingTap) {
                soundManager.playShortBeep()
                addEvent(GameEvent(eventType = "PHASE", playerIndex = 1, detail = "Phase ${after.currentPhaseIndex + 1} over"))
            }
            after.isInInterPhasePause -> if (!before.isInInterPhasePause) {
                if (!before.isPhaseAwaitingTap) soundManager.playShortBeep()
                addEvent(GameEvent(eventType = "PHASE", playerIndex = 1, detail = "Pause"))
            }
            else -> {
                if (!before.isInInterPhasePause && !before.isPhaseAwaitingTap) soundManager.playShortBeep()
                addEvent(GameEvent(eventType = "PHASE", playerIndex = 1, detail = "Phase ${after.currentPhaseIndex + 1}"))
                speakPhase(after.currentPhaseIndex, settings)
            }
        }
    }

    /** The name the user gave the phase, as typed; a numbered one in the voice's language. */
    private fun speakPhase(index: Int, settings: ChessClockSettings) {
        if (!settings.voiceAnnouncementsEnabled) return
        val name = getPlayerSettings(1).phases.getOrNull(index)?.name?.takeIf { it.isNotBlank() }
        if (name != null) voiceManager.speak(name, settings.voiceVolume)
        else voiceManager.speak(CoreR.string.voice_phase, settings.voiceVolume, index + 1)
    }

    private fun handleVoice(oldState: PlayerState, newState: PlayerState, settings: ChessClockSettings, playerIdx: Int) {
        if (!settings.voiceAnnouncementsEnabled) return

        val vol = settings.voiceVolume
        // 1. Time's Up
        if (newState.isOutOfTime && !oldState.isOutOfTime) {
            voiceManager.speak(CoreR.string.voice_time_up, vol, playerIdx)
            return
        }

        // 2. Thresholds (1m, 30s, 10s) -- never in Hidden mode, where "1 minute remaining" is the
        // very thing the clock face replaces with ??:??.
        //
        // Announced every time the clock comes down through one. A threshold can only be crossed on
        // the way down, so each crossing is new; the per-player "last announced" memory that used to
        // sit here only ever suppressed real ones -- in a move timer, after a Fischer increment or in
        // each byoyomi period the clock comes down through the same threshold again, and from the
        // second time on it said nothing. Several crossed in one tick speak the lowest, as the
        // flushing queue keeps only the last.
        val time = newState.timeRemainingMs
        val thresholds = if (getPlayerSettings(playerIdx).mode == TimerMode.HIDDEN) emptyList()
            else listOf(60000L to CoreR.string.voice_one_minute_left, 30000L to CoreR.string.voice_thirty_seconds_left, 10000L to CoreR.string.voice_ten_seconds_left)
        for ((t, words) in thresholds) {
            if (oldState.timeRemainingMs > t && time <= t) voiceManager.speak(words, vol)
        }

        // 3. FIDE Periods
        if (newState.currentPeriodIndex > oldState.currentPeriodIndex) {
            voiceManager.speak(CoreR.string.voice_period, vol, newState.currentPeriodIndex + 1)
        }

        // 4. Byoyomi Entry
        if (newState.isInByoyomi && !oldState.isInByoyomi) {
            voiceManager.speak(CoreR.string.voice_byoyomi, vol)
        } else if (newState.isInByoyomi && newState.byoyomiPeriodsRemaining < oldState.byoyomiPeriodsRemaining && !newState.isOutOfTime) {
            // A period lost; see handleAudio.
            voiceManager.speak(CoreR.string.voice_byoyomi_periods_left, vol, newState.byoyomiPeriodsRemaining)
        }

        // Phase names are announced by onPhaseTransition: this function is only ever handed two
        // states of the same phase, and the check that stood here could never fire.

        // 5. Gong Mode
        if (getPlayerSettings(playerIdx).mode == TimerMode.GONG) {
            if (oldState.isGongReflectionPhase && !newState.isGongReflectionPhase) {
                voiceManager.speak(CoreR.string.voice_move, vol)
            } else if (!oldState.isGongReflectionPhase && newState.isGongReflectionPhase) {
                voiceManager.speak(CoreR.string.voice_reflect, vol)
            }
        }
    }

    override fun onCleared() {
        soundManager.release()
        voiceManager.release()
        // Asked through the delegates, so shutting down does not construct the very transports this
        // build went out of its way never to build.
        if (bluetoothManagerDelegate.isInitialized()) bluetoothManager.disconnect()
        // release(), not disconnect(): the USB manager also holds a registered broadcast receiver,
        // and both it and the serial one own a coroutine scope.
        if (usbBoardManagerDelegate.isInitialized()) usbBoardManager.release()
        if (bluetoothSerialBoardManagerDelegate.isInitialized()) bluetoothSerialBoardManager.release()
    }

    /**
     * Returns whether the move itself put the mover out -- which only MOVE_COUNTS_DOWN does, on
     * its last allowed move. That flag is raised here, outside tick(), so it used to go through
     * none of what tick() does for one: no triple beep, no voice, no FLAG event in the log and no
     * loss colour, while the clock went on to start the opponent's turn as if nothing had happened.
     */
    private fun applyPostMoveLogic(playerIndex: Int, timeSpentOnMove: Long): Boolean {
        val before = _uiState.value.players.getOrNull(playerIndex - 1)
        _uiState.update { state ->
            val s = getPlayerSettings(playerIndex)
            computePostMoveState(state, playerIndex, timeSpentOnMove, _settings.value, s)
        }
        val after = _uiState.value.players.getOrNull(playerIndex - 1)
        if (before == null || after == null) return false
        if (before.isOutOfTime || !after.isOutOfTime) {
            // What the move itself changed is announced too. A FIDE period reached by move count --
            // forcedMoveCounter, the default -- advances here and not in tick(), so "Period 2"
            // was never said for it. Thresholds cannot fire: a move only ever adds time.
            handleVoice(before, after, _settings.value, playerIndex)
            return false
        }
        handleAudio(before, after, _settings.value, playerIndex)
        handleVoice(before, after, _settings.value, playerIndex)
        _uiState.update { if (it.firstToFlag == null) it.copy(firstToFlag = playerIndex) else it }
        return true
    }

    fun pause() {
        // Already paused, there is nothing to stop and nothing to record. Backgrounding the app
        // with pause-on-background calls this on a clock that is often paused already, and every
        // call logged another PAUSE -- which the reconstruction of older logs' move durations
        // reads as the start of a pause span.
        if (_uiState.value.isPaused && timerJob == null) return
        settleRunningClock()
        timerJob?.cancel()
        timerJob = null
        if (pausedAt == null) pausedAt = SystemClock.elapsedRealtime()
        _uiState.update { it.copy(isPaused = true) }
        addEvent(GameEvent(eventType = "PAUSE"))
    }
    fun resume() {
        val starting = _uiState.value
        // A PHASES game that is over -- finished, or flagged by its phase -- has nothing to restart.
        if (getPlayerSettings(1).mode == TimerMode.PHASES && starting.players.firstOrNull()?.isOutOfTime == true) return
        pausedAt?.let { moveStartTime += SystemClock.elapsedRealtime() - it }
        pausedAt = null
        val active = starting.activePlayer ?: 1
        if (currentLog == null && FlavorConfig.keepsGameHistory()) {
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
        if (mode == TimerMode.PHASES) {
            startClock(active, armDelay = false)
            // The first phase is named as it starts, as every later one is; the game used to begin
            // in silence. Only a game that has never run: a resume mid-phase says nothing.
            if (starting.activePlayer == null) {
                val p1 = _uiState.value.players[0]
                if (!p1.isInInterPhasePause && !p1.isPhaseAwaitingTap) speakPhase(p1.currentPhaseIndex, s)
            }
            return
        }
        if (mode == TimerMode.MOVE_TIMER_SHARED || (mode.name.startsWith("CHRONO") && s.isOneForAll)) { startClock(active, armDelay = false); return }
        // The same rule a press goes by. This used to refuse to restart while anyone at all was out
        // of time, so under NEGATIVE -- or FLAG with three or four players, where play goes on
        // after a flag -- a single pause stopped the game for good: Resume did nothing, and the
        // active player's button, which resumes a paused clock, did nothing either.
        if (!flagsEndGame(_uiState.value.players, s.flagBehavior, s.numberOfPlayers.coerceAtLeast(1))) startClock(active, armDelay = false)
    }
    fun reset() {
        timerJob?.cancel()
        timerJob = null
        pausedAt = null
        // Anything the board reported but no press claimed belongs to the game being ended, not to
        // the next one.
        pendingBoardNotation = null
        // The autosave belongs to the game being ended too. Nothing but resumeSavedClock ever
        // cleared it, so the arbitre overlay went on offering "Resume" for a game that had been
        // reset -- indefinitely, and long after its snapshot had stopped resembling anything the
        // user would recognise.
        clearSavedClock()
        finishCurrentLog()
        _uiState.update { createInitialState(_settings.value, reuseRandomRoll = true) }
    }

    /**
     * Stores the game being played, if there is one, and ends it.
     *
     * reset() was the only way a game reached the history, so every other way of ending one lost
     * it or worse. Changing the time control mid-game rebuilt the clock and left [currentLog] in
     * place: the next game's events went on being appended to the old one, under the old one's
     * settings and starting times, and the first game never appeared as a game of its own.
     */
    private fun finishCurrentLog() {
        val log = currentLog ?: return
        val s = _settings.value
        // Nothing happened: Play and then Reset, or a stray press. It has already been written as
        // the game went, so it is taken back out rather than left as a "0 moves" tile in Last
        // Games and one more game played in the statistics. PHASES has no moves, and a phase
        // change is what shows its game went somewhere; see onPhaseTransition.
        if (log.events.none { it.eventType == "MOVE" || it.eventType == "FLAG" || it.eventType == "PHASE" }) {
            currentLog = null
            historyWrites.trySend(HistoryWrite.Finish(s.logHistoryLimit, s.logDurationLimit, log.id, discard = true))
            return
        }
        // Queues the final write. The log used to be captured before this event was added, so no
        // stored game ever carried its RESET.
        addEvent(GameEvent(eventType = "RESET"))
        currentLog = null
        historyWrites.trySend(HistoryWrite.Finish(s.logHistoryLimit, s.logDurationLimit, log.id))
    }

    private suspend fun applyHistoryWrite(write: HistoryWrite) {
        when (write) {
            is HistoryWrite.Store -> {
                val entity = withContext(Dispatchers.Default) { converters.fromGameLog(write.log) }
                gameDao.insertLog(entity)
            }
            is HistoryWrite.SaveClock -> {
                gameDao.saveClock(write.entity)
                _hasSavedClock.value = true
            }
            HistoryWrite.ClearClock -> {
                gameDao.clearSavedClock()
                _hasSavedClock.value = false
            }
            is HistoryWrite.Finish -> {
                if (write.discard) gameDao.deleteLog(write.finishedLogId)

                // 1. Cleanup by count (skip if infinite)
                trimLimitFor(write.limit)?.let { gameDao.trimLogs(it) }

                // 2. Cleanup by duration
                if (write.durationLimit != LogDurationLimit.INFINITE) {
                    val days = when (write.durationLimit) {
                        LogDurationLimit.ONE_DAY -> 1
                        LogDurationLimit.ONE_WEEK -> 7
                        LogDurationLimit.ONE_MONTH -> 30
                        LogDurationLimit.SIX_MONTHS -> 180
                        LogDurationLimit.ONE_YEAR -> 365
                    }
                    if (days > 0) {
                        val threshold = System.currentTimeMillis() - (days * 24 * 3600 * 1000L)
                        // Rows carry only their start time, so the game just finished is spared by
                        // id: with "1 day", one paused overnight and ended 26 hours after it began
                        // was deleted the moment it was saved.
                        gameDao.deleteLogsOlderThan(threshold, keepId = write.finishedLogId)
                    }
                }

                _gameHistory.value = loadHistory(write.limit)
            }
        }
    }

    /**
     * Resets with a brand new random time, for RANDOM/HIDDEN.
     *
     * [reset] deliberately reuses the roll so restarting a game does not change the time under the
     * players; that left no way at all to get a fresh one short of toggling a setting to invalidate
     * the cache. Dropping [lastRandomRoll] first makes createInitialState draw again.
     *
     * Everything else is a reset, and goes through reset(). This used to rebuild the clock itself
     * and simply drop [currentLog], so a game ended by a re-roll was never stored, and the autosave
     * survived it -- the arbitre panel went on offering "Resume" for the game just thrown away.
     */
    fun rerollRandomTime() {
        lastRandomRoll = null
        reset()
    }

    fun updateSettings(
        newSettings: ChessClockSettings,
        initialStates: List<PlayerStateProxy>? = null,
        logsToImport: List<GameLog>? = null,
        scoreboardToImport: ScoreboardSession? = null,
        presetsToImport: List<SavedPreset>? = null,
        isImport: Boolean = false
    ) {
        val oldSettings = _settings.value
        // Shadow the parameter so every use below this line sees the sanitized value; the named
        // argument at call sites still binds by the original parameter name, so this is transparent
        // to callers.
        @Suppress("NAME_SHADOWING")
        val newSettings = (if (isImport) {
            sanitizeImportedSettings(getApplication(), newSettings).let { imported ->
                imported.copy(
                    notebookNotes = mergeImportedNotes(oldSettings.notebookNotes, imported.notebookNotes),
                    // How much history to keep decides what is deleted from this device, so it is
                    // this device's choice. Taken from the file, a friend's configuration with a
                    // limit of 10 trimmed hundreds of the user's own games to ten -- at once when
                    // the file carried logs, otherwise at the end of the next game.
                    logHistoryLimit = oldSettings.logHistoryLimit,
                    logDurationLimit = oldSettings.logDurationLimit,
                )
            }
        } else newSettings).wholeTableSettings()
        _settings.value = newSettings
        
        // isImport guards this for the same reason it guards newSettings above: the scoreboard
        // arrives from the same untrusted QR/JSON/ZIP payload.
        scoreboardToImport?.let { setScoreboard(if (isImport) sanitizeImportedScoreboard(it) else it) }
        presetsToImport?.let { presets ->
            val imported = if (isImport) sanitizeImportedPresets(getApplication(), presets) else presets
            persistCustomPresets(mergeImportedPresets(_customPresets.value, imported))
        }
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
            pausedAt = null
            // The clock below is a new game, so the one being played ends here, the same way Reset
            // ends it. Saved with the settings it was played under: _settings has moved on, but
            // the log carries its own copy from the first press.
            finishCurrentLog()
            if (initialStates != null) {
                // A "Last Games" preset carries its own already-rolled RANDOM/HIDDEN time in
                // initialStates -- without updating the cache reset() reuses, Reset would silently
                // revert to whatever RANDOM/HIDDEN roll was cached from an earlier, unrelated game.
                val s1 = if (newSettings.differentSettingsPerPlayer) newSettings.p1Custom else newSettings.main
                if ((s1.mode == TimerMode.RANDOM || s1.mode == TimerMode.HIDDEN) && initialStates.isNotEmpty()) {
                    val first = initialStates.first()
                    lastRandomRoll = first.initialTotalTimeMs to first.secondaryTimeMs
                }
                // The players' states carry no table clock, and it took the default of zero: a
                // PHASES game ended its first phase on the first tick -- a one-phase game was over
                // before it began -- and a CHRONO countdown flagged at once.
                _uiState.value = ChessClockState(players = initialStates.map { it.toState() }, globalTimeMs = initialGlobalTimeMs(s1))
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
                // Capped, unlike a game the user actually played: that path goes through
                // saveLogWithTrim, which enforces logHistoryLimit, while this one inserts whatever
                // arrives. A backup is allowed to be large, but not unbounded.
                logs.take(MAX_IMPORTED_LOGS).forEach { log ->
                    val sanitizedLog = sanitizeImportedLog(log)
                        .copy(settings = sanitizeImportedSettings(app, log.settings))
                    gameDao.insertLog(converters.fromGameLog(sanitizedLog))
                }
                trimLimitFor(_settings.value.logHistoryLimit)?.let { gameDao.trimLogs(it) }
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
    /**
     * Plays the volume test, and returns whether there was a voice to play it with -- false when
     * the device has no text-to-speech voice in its language or in English, which used to look
     * exactly like a test that worked at zero volume.
     */
    fun previewVoice(): Boolean {
        voiceManager.speak(CoreR.string.voice_test, settings.value.voiceVolume)
        return voiceManager.isAvailable
    }

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
        val flagBehavior = _settings.value.flagBehavior
        // Shared (Auto) keeps one move clock, copied into every player and ticked from player 1's,
        // so an adjustment to anyone else's was overwritten by the next tick. It is the table's.
        val wholeTable = getPlayerSettings(playerIndex).mode == TimerMode.MOVE_TIMER_SHARED
        _uiState.update { state ->
            val newList = state.players.mapIndexed { idx, p ->
                if (wholeTable || idx + 1 == playerIndex) p.credited(amountMs, flagBehavior)
                else p
            }
            state.copy(players = newList)
        }
    }

    /**
     * Ends the current phase: the game flags if the phase says so, otherwise the pause between
     * phases starts, or the next phase, or the game finishes after the last one.
     *
     * [skipped] is a tap that cuts the phase short (allowPhaseSkip). The flag on a phase's end is
     * for its time running out: a skip used to flag the game on the spot, so a phase that flags on
     * its end could not be skipped at all -- the tap ended the game instead.
     *
     * The flag ends the game whatever the flag behaviour. PHASES runs one clock for the table, with
     * no overrun to count and no other player to play on, but under NEGATIVE and REVERSE the game
     * was not over as far as anything else knew: the ticker ran on, the clock was autosaved every
     * fifteen seconds, and TalkBack still said "your turn". It stops here as FREEZE stops a game.
     */
    private fun startPhaseTransition(state: ChessClockState, skipped: Boolean = false): ChessClockState {
        val s = getPlayerSettings(1)
        val p = state.players[0]
        val currentPhase = s.phases.getOrNull(p.currentPhaseIndex) ?: GamePhase()
        if (currentPhase.flagOnEnd && !skipped) {
            val newList = state.players.toMutableList().apply { this[0] = p.copy(isOutOfTime = true, timeRemainingMs = 0, isPhaseAwaitingTap = false) }
            return state.copy(players = newList, globalTimeMs = 0, isPaused = true)
        }
        // A pause is a wait for the next phase. After the last one of a non-looping game there is
        // none, and a tap on the finished game used to start a fresh PAUSE countdown before the
        // clock fell back to finished.
        val isLastPhase = p.currentPhaseIndex >= s.phases.size - 1 && !settings.value.loopPhases
        if ((settings.value.pauseBetweenPhasesMs > 0 || settings.value.pauseUntilTap) && !isLastPhase) {
            // A phase that waited for its tap was still flagged here, so the whole pause read as out of time.
            // A pause until a tap has no countdown; tick() holds it, and the face shows 0:00.
            val pauseMs = if (settings.value.pauseUntilTap) 0L else settings.value.pauseBetweenPhasesMs
            val pausedPlayer = p.copy(isInInterPhasePause = true, pauseTimeRemainingMs = pauseMs, isPhaseAwaitingTap = false)
            val newList = state.players.toMutableList().apply { this[0] = pausedPlayer }
            return state.copy(players = newList)
        }
        return performPhaseAdvance(state, 1)
    }

    private fun performPhaseAdvance(state: ChessClockState, playerIndex: Int): ChessClockState =
        nextPhaseState(state, playerIndex, getPlayerSettings(playerIndex).phases, settings.value.loopPhases)

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
        // Up to the pause, if paused: the Save button is pressed on a paused clock.
        val moveElapsed = if (uiState.value.activePlayer != null) ((pausedAt ?: SystemClock.elapsedRealtime()) - moveStartTime).coerceAtLeast(0) else 0L
        val stateSnapshot = uiState.value.toProxy().copy(gameLogId = currentLog?.id, moveElapsedMs = moveElapsed)

        val generation = savedClockGeneration
        viewModelScope.launch {
            val entity = withContext(Dispatchers.Default) {
                com.masterclock.app.data.SavedClockEntity(
                    settingsJson = json.encodeToString(ChessClockSettings.serializer(), settingsSnapshot),
                    stateJson = json.encodeToString(ChessClockStateProxy.serializer(), stateSnapshot),
                )
            }
            // Back on the main thread: a clear since the snapshot was taken means this game was
            // reset or resumed while it encoded, and the snapshot no longer belongs anywhere.
            if (generation == savedClockGeneration) historyWrites.trySend(HistoryWrite.SaveClock(entity))
        }
    }

    private fun clearSavedClock() {
        savedClockGeneration++
        historyWrites.trySend(HistoryWrite.ClearClock)
    }

    fun resumeSavedClock() {
        viewModelScope.launch {
            val saved = gameDao.getSavedClock() ?: return@launch
            try {
                // The mirror of saveClockForLater, which was moved off the main thread in v0.8.28
                // while this side was left on it. Both blobs carry the notebook.
                val resumed = withContext(Dispatchers.Default) {
                    json.decodeFromString(ChessClockSettings.serializer(), saved.settingsJson) to
                        json.decodeFromString(ChessClockStateProxy.serializer(), saved.stateJson)
                }
                val resumedSettings = resumed.first

                // The game the clock belongs to, read back from the history it has been written to
                // as it was played. Resuming used to leave currentLog null, so the next press opened
                // a new game whose starting times were the mid-game ones: every move from before the
                // interruption was lost, and the Last Games tile offered a 5-minute game as 3:12.
                // A log that cannot be found (trimmed since) or read back resumes as before.
                val savedLogId = resumed.second.gameLogId
                val resumedLog = when {
                    savedLogId == null -> null
                    currentLog?.id == savedLogId -> currentLog
                    else -> gameDao.getLog(savedLogId)
                        ?.let { entity -> withContext(Dispatchers.Default) { converters.toGameLog(entity) } }
                        // toGameLog degrades an unreadable row to an empty log rather than throwing.
                        ?.takeIf { it.events.isNotEmpty() }
                }
                // Whatever was on screen is replaced, so it ends here like any other game -- unless
                // it is the very game being resumed.
                if (resumedLog == null || resumedLog.id != currentLog?.id) finishCurrentLog()
                currentLog = resumedLog

                _settings.value = resumedSettings
                // Persisted, not merely held. Restoring into memory alone left DataStore holding
                // whatever the user had configured since the autosave, so the next settings edit
                // wrote this older snapshot back over all of it; and the sounds still playing were
                // the ones loaded before.
                settingsRepo.saveSettings(resumedSettings)
                soundManager.loadSounds(resumedSettings)

                // The autosave runs only from tick(), so every snapshot it takes is of a *running*
                // clock. Installing one as-is produced a clock that looked like it was counting --
                // active side lit, pause icon showing -- with no timerJob behind it, so it never
                // moved. And because isPaused was false, the first press did not take
                // startOrSwitch's resume() branch either: it went straight to the switch and
                // charged the mover elapsedRealtime() minus a moveStartTime this process had never
                // set, i.e. the device's uptime.
                timerJob?.cancel()
                timerJob = null
                val now = SystemClock.elapsedRealtime()
                lastTickTime = now
                moveStartTime = now - resumed.second.moveElapsedMs.coerceIn(0L, MAX_IMPORTED_DURATION_MS)
                // Installed paused, so the wait until Resume is pressed is a pause like any other.
                pausedAt = now
                _uiState.value = resumed.second.toState().copy(isPaused = true)

                clearSavedClock()
            } catch (e: Exception) {
                Log.w("ChessTimerViewModel", "Failed to resume saved clock, discarding it", e)
                clearSavedClock()
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
        setScoreboard(_scoreboard.value.copy(player1Name = p1, player2Name = p2))
    }

    fun addScoreboardGame(result: String) {
        val current = _scoreboard.value
        setScoreboard(current.copy(games = current.games + ScoreboardGame(result = result)))
    }

    fun resetScoreboard() {
        setScoreboard(ScoreboardSession())
    }

    /**
     * The scoreboard was a plain in-memory flow that nothing ever wrote down, so closing the app
     * threw away an evening's results; only a backup export ever read it. Every change is
     * persisted now, the same way settings edits are.
     */
    private fun setScoreboard(session: ScoreboardSession) {
        _scoreboard.value = session
        viewModelScope.launch { settingsRepo.saveScoreboard(session) }
    }
}
