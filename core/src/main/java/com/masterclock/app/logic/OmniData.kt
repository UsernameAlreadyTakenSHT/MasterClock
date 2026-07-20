package com.masterclock.app.logic

import kotlinx.serialization.Serializable

/** Key used in [OmniState.playerTimeBanks] for the shared TimeBankMode.GLOBAL_RESERVE pool (real player indices are always >= 0). */
const val OMNI_GLOBAL_TIME_BANK_KEY = -1

@Serializable
data class OmniPhaseSettings(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "Phase",
    val durationMs: Long = 60_000L,
    val autoAdvance: Boolean = true
)

@Serializable
data class OmniTurnSettings(
    val id: String = java.util.UUID.randomUUID().toString(),
    val durationMs: Long = 600_000L,
    val phases: List<OmniPhaseSettings> = emptyList()
)

@Serializable
enum class RoundTurnLogic { FIXED, SEQUENCE }

@Serializable
enum class RoundEndBehavior { ADVANCE, LOOP }

@Serializable
data class OmniRoundSettings(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "Round",
    val durationMs: Long = 600_000L, // 10 min default
    val turnDurationMs: Long = 600_000L,
    val turnLogic: RoundTurnLogic = RoundTurnLogic.FIXED,
    val roundEndBehavior: RoundEndBehavior = RoundEndBehavior.ADVANCE,
    val customTurns: List<OmniTurnSettings> = emptyList()
)

@Serializable
enum class TimeBankMode { NONE, ACCUMULATIVE, GLOBAL_RESERVE }

@Serializable
enum class TransitionType { AUTOMATIC, MANUAL_READY }

@Serializable
enum class TimeBankScope { TURN_TO_TURN, ROUND_TO_ROUND, GAME_TO_GAME, SESSION_WIDE }

@Serializable
enum class PlayerOrderType { LINEAR, SNAKE, ROTATE, RANDOM }

@Serializable
data class OmniGameSettings(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "Game",
    val durationMs: Long = 2_700_000L,
    val rounds: List<OmniRoundSettings> = listOf(OmniRoundSettings())
)

/**
 * Main settings object for the Omni-Timer mode.
 * Isolated from standard ChessClockSettings.
 */
@Serializable
data class OmniSettings(
    val isEnabled: Boolean = false,
    
    // Hierarchy activation
    val useGlobalClock: Boolean = false,
    val useGameClock: Boolean = true,
    val useRoundClock: Boolean = true,
    val useTurnClock: Boolean = true,
    val usePhaseClock: Boolean = false,
    val numberOfPlayers: Int = 2,
    val playerOrderType: PlayerOrderType = PlayerOrderType.LINEAR,

    // Base Durations (Defaults)
    val globalDurationMs: Long = 18_000_000L, // 5h
    val gameDurationMs: Long = 2_700_000L,   // 45 min

    // Structure
    val games: List<OmniGameSettings> = listOf(OmniGameSettings()),
    
    // Transitions & Pauses
    val interTurnPauseMs: Long = 0,
    val interRoundPauseMs: Long = 0,
    val interGamePauseMs: Long = 0,
    val launchCountdownMs: Long = 10_000L, // 10s default
    val transitionType: TransitionType = TransitionType.AUTOMATIC,

    // More Rules
    val pauseDeductsFromGlobal: Boolean = false,
    val pauseDeductsFromGame: Boolean = false,
    val pauseDeductsFromRound: Boolean = false,

    val timeBankMode: TimeBankMode = TimeBankMode.NONE,
    val timeBankScope: TimeBankScope = TimeBankScope.ROUND_TO_ROUND,

    val soundTurnEnd: Boolean = true,
    val soundRoundEnd: Boolean = true,
    val soundGameEnd: Boolean = true,

    // When true, this level's own clock reaching zero forces an immediate advance to the next
    // unit at that level (cutting short whatever turn/phase is in progress inside it) instead of the
    // default -- freeze at zero, beep, and wait for the manual Next tap. See AUDIT.md 7.1.
    val phaseForcesCutoff: Boolean = false,
    val turnForcesCutoff: Boolean = false,
    val roundForcesCutoff: Boolean = false,
    val gameForcesCutoff: Boolean = false,
    val globalForcesCutoff: Boolean = false
)

/**
 * Live state for an Omni-Timer session.
 */
@Serializable
data class OmniState(
    val isRunning: Boolean = false,
    // Distinguishes "temporarily paused, Play resumes where we left off" from "stopped/never
    // started, Play begins a brand new session". Previously both pauseOmni() and stopOmni() only
    // ever cleared isRunning, so startOmni() (which decided fresh-vs-resume off !isRunning alone)
    // reset the whole session -- games/rounds/turns/banks -- after a single Pause. See AUDIT.md §7.1.
    val hasStarted: Boolean = false,

    // Live Clocks
    val currentGlobalTimeMs: Long = 0,
    val currentGameTimeMs: Long = 0,
    val currentRoundTimeMs: Long = 0,
    val currentTurnTimeMs: Long = 0,
    val currentPhaseTimeMs: Long = 0,

    // Navigation Indices
    val currentGameIndex: Int = 0,
    val currentRoundIndex: Int = 0,
    val currentPlayerIndex: Int = 0,
    val currentPhaseIndex: Int = 0,
    // Raw, ever-incrementing count of turns taken within the current round (reset to 0 on every
    // round/game change) -- distinct from currentPlayerIndex, which is already reduced to [0,
    // numberOfPlayers) by calculateNextPlayerIndex(). advanceOmni() used to reuse currentPlayerIndex
    // as if it were this raw counter, double-applying the player-order transform for
    // ROTATE/SNAKE (a player could get skipped or replayed). See AUDIT.md §7.1.
    val turnCounterInRound: Int = 0,

    // Buffer States
    val isInTransition: Boolean = false,
    val transitionTimeRemainingMs: Long = 0,
    val transitionLabel: String = "", // "TURN", "ROUND", "GAME"
    val isLaunching: Boolean = false,
    val launchTimeRemainingMs: Long = 0,

    // Time Banks (Player Index -> Ms)
    val playerTimeBanks: Map<Int, Long> = emptyMap()
)
