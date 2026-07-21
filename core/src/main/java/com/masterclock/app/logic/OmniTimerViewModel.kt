package com.masterclock.app.logic

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.masterclock.app.data.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.milliseconds

/** GLOBAL_RESERVE shares one pool across every player instead of one bank per player. */
internal fun bankKeyFor(settings: OmniSettings, playerIndex: Int): Int =
    if (settings.timeBankMode == TimeBankMode.GLOBAL_RESERVE) OMNI_GLOBAL_TIME_BANK_KEY else playerIndex

internal fun applyOmniTimeBanking(state: OmniState, settings: OmniSettings, playerIndex: Int, timeRemainingInTurn: Long): Map<Int, Long> {
    if (settings.timeBankMode == TimeBankMode.NONE || timeRemainingInTurn <= 0) return state.playerTimeBanks
    val key = bankKeyFor(settings, playerIndex)
    val currentBanks = state.playerTimeBanks.toMutableMap()
    currentBanks[key] = (currentBanks[key] ?: 0L) + timeRemainingInTurn
    return currentBanks
}

/**
 * Draws whatever is banked for the player about to start their turn into that turn's time, and
 * clears just that entry so it is not re-applied on every subsequent turn. Previously the whole
 * time-banking system computed playerTimeBanks but nothing ever read it back into a clock or
 * displayed it -- purely cosmetic. See AUDIT.md section 7.1.
 */
internal fun drawOmniBank(banks: Map<Int, Long>, settings: OmniSettings, nextPlayerIndex: Int): Pair<Map<Int, Long>, Long> {
    if (settings.timeBankMode == TimeBankMode.NONE) return banks to 0L
    val key = bankKeyFor(settings, nextPlayerIndex)
    val banked = banks[key] ?: 0L
    if (banked <= 0L) return banks to 0L
    return (banks + (key to 0L)) to banked
}

internal fun handleTimeBankScope(currentBanks: Map<Int, Long>, scope: TimeBankScope, event: String): Map<Int, Long> {
    return when (scope) {
        TimeBankScope.TURN_TO_TURN -> emptyMap()
        TimeBankScope.ROUND_TO_ROUND -> if (event == "ROUND" || event == "GAME" || event == "SESSION") emptyMap() else currentBanks
        TimeBankScope.GAME_TO_GAME -> if (event == "GAME" || event == "SESSION") emptyMap() else currentBanks
        TimeBankScope.SESSION_WIDE -> currentBanks
    }
}

internal fun getOmniDuration(settings: OmniSettings, level: String, gameIdx: Int = 0, roundIdx: Int = 0, playerIdx: Int = 0, phaseIdx: Int = 0): Long {
    // Was always settings.games.firstOrNull(), so any per-game duration/structure was silently
    // ignored from Game 2 onward -- see AUDIT.md section 7.1.
    val game = settings.games.getOrNull(gameIdx) ?: settings.games.lastOrNull() ?: OmniGameSettings()
    val round = game.rounds.getOrNull(roundIdx) ?: game.rounds.lastOrNull() ?: OmniRoundSettings()
    return when (level) {
        "ROUND" -> round.durationMs
        "TURN" -> if (round.turnLogic == RoundTurnLogic.SEQUENCE) { val turn = round.customTurns.getOrNull(playerIdx); turn?.durationMs ?: round.turnDurationMs } else round.turnDurationMs
        "PHASE" -> {
            val turnsList = if (round.turnLogic == RoundTurnLogic.SEQUENCE) round.customTurns else List(settings.numberOfPlayers) { OmniTurnSettings(durationMs = round.turnDurationMs) }
            val turn = turnsList.getOrNull(playerIdx); val phaseList = turn?.phases ?: listOf(OmniPhaseSettings())
            phaseList.getOrNull(phaseIdx)?.durationMs ?: 60_000L
        }
        else -> 0L
    }
}

internal fun calculateNextPlayerIndex(turnIndex: Int, roundIndex: Int, settings: OmniSettings): Int {
    val numPlayers = settings.numberOfPlayers
    return when (settings.playerOrderType) {
        PlayerOrderType.LINEAR -> turnIndex % numPlayers
        PlayerOrderType.SNAKE -> if (roundIndex % 2 == 0) turnIndex % numPlayers else (numPlayers - 1) - (turnIndex % numPlayers)
        PlayerOrderType.ROTATE -> (turnIndex + roundIndex) % numPlayers
        PlayerOrderType.RANDOM -> (0 until numPlayers).random()
    }
}

/**
 * Advances the Omni session by exactly one step (phase, or turn/round/game/session if the current
 * one is exhausted), normally called from the manual Next button.
 *
 * forceLevel lets a level's own clock reaching zero (see OmniSettings *ForcesCutoff fields) cut
 * short whatever is happening inside that level instead of waiting for it to finish naturally:
 * TURN/ROUND/GAME treat the phase/turn/round respectively as exhausted regardless of how much of
 * it is actually left, cascading exactly like the natural-exhaustion path already does. SESSION
 * ends the whole session immediately. Phase-level cutoff needs no special case: advancing by one
 * phase (the default, forceLevel = null) already is "cut this phase short".
 *
 * Callers must ensure `!state.isInTransition` — the only caller (OmniTimerScreen's manual Next
 * button) is wired to render `TransitionOverlay`/`confirmOmniReady()` instead while a transition
 * is showing, so this is never invoked mid-transition.
 */
internal fun computeOmniAdvance(state: OmniState, settings: OmniSettings, forceLevel: String? = null): OmniState {
    val updatedBanks = applyOmniTimeBanking(state, settings, state.currentPlayerIndex, state.currentTurnTimeMs)

    if (forceLevel == "SESSION") {
        return state.copy(isRunning = false, isInTransition = true, transitionLabel = "SESSION", playerTimeBanks = handleTimeBankScope(updatedBanks, settings.timeBankScope, "SESSION"))
    }

    var nextPhaseIdx = state.currentPhaseIndex + 1
    // Raw, ever-incrementing turn count within the round -- NOT currentPlayerIndex, which is
    // already reduced to [0, numberOfPlayers) by calculateNextPlayerIndex() below and would
    // double-apply the player-order transform for ROTATE/SNAKE (see OmniState.turnCounterInRound
    // and AUDIT.md section 7.1).
    var nextTurnIdx = state.turnCounterInRound

    val currentGameIdx = state.currentGameIndex
    val currentGame = settings.games.getOrNull(currentGameIdx) ?: settings.games.firstOrNull() ?: OmniGameSettings()
    val currentRound = currentGame.rounds.getOrNull(state.currentRoundIndex) ?: currentGame.rounds.firstOrNull() ?: OmniRoundSettings()
    val turnsList = if (currentRound.turnLogic == RoundTurnLogic.SEQUENCE) currentRound.customTurns else List(currentRound.customTurns.size.coerceAtLeast(settings.numberOfPlayers)) { OmniTurnSettings(durationMs = currentRound.turnDurationMs) }
    val currentTurn = turnsList.getOrNull(nextTurnIdx) ?: OmniTurnSettings(durationMs = currentRound.turnDurationMs)

    var nextRoundIdx = state.currentRoundIndex

    val phaseDone = !settings.usePhaseClock || nextPhaseIdx >= currentTurn.phases.size ||
        forceLevel == "TURN" || forceLevel == "ROUND" || forceLevel == "GAME"
    if (phaseDone) {
        nextPhaseIdx = 0
        nextTurnIdx++

        val turnDone = nextTurnIdx >= turnsList.size || forceLevel == "ROUND" || forceLevel == "GAME"
        if (turnDone) {
            if (currentRound.roundEndBehavior == RoundEndBehavior.LOOP && forceLevel != "GAME" && forceLevel != "ROUND") {
                nextTurnIdx = 0
            } else {
                nextTurnIdx = 0
                nextRoundIdx++
                val roundDone = nextRoundIdx >= currentGame.rounds.size || forceLevel == "GAME"
                if (roundDone) {
                    val nextFinalGameIdx = state.currentGameIndex + 1
                    if (nextFinalGameIdx >= settings.games.size) {
                        return state.copy(isRunning = false, isInTransition = true, transitionLabel = "SESSION", playerTimeBanks = handleTimeBankScope(updatedBanks, settings.timeBankScope, "SESSION"))
                    }
                    val gameBanks = handleTimeBankScope(updatedBanks, settings.timeBankScope, "GAME")
                    val nextGamePlayerIdx = calculateNextPlayerIndex(0, 0, settings)
                    val (drawnBanks, bankedMs) = drawOmniBank(gameBanks, settings, nextGamePlayerIdx)
                    return state.copy(
                        currentGameIndex = nextFinalGameIdx, currentRoundIndex = 0, currentPlayerIndex = nextGamePlayerIdx,
                        currentPhaseIndex = 0, turnCounterInRound = 0, isInTransition = true, transitionTimeRemainingMs = settings.interGamePauseMs, transitionLabel = "GAME",
                        currentRoundTimeMs = getOmniDuration(settings, "ROUND", gameIdx = nextFinalGameIdx, roundIdx = 0),
                        currentTurnTimeMs = getOmniDuration(settings, "TURN", gameIdx = nextFinalGameIdx, roundIdx = 0, playerIdx = 0) + bankedMs,
                        currentGameTimeMs = settings.games.getOrNull(nextFinalGameIdx)?.durationMs ?: settings.gameDurationMs,
                        playerTimeBanks = drawnBanks
                    )
                }
                val roundBanks = handleTimeBankScope(updatedBanks, settings.timeBankScope, "ROUND")
                val nextPlayerIdx = calculateNextPlayerIndex(nextTurnIdx, nextRoundIdx, settings)
                val (drawnBanks, bankedMs) = drawOmniBank(roundBanks, settings, nextPlayerIdx)
                return state.copy(
                    currentRoundIndex = nextRoundIdx, currentPlayerIndex = nextPlayerIdx, currentPhaseIndex = nextPhaseIdx,
                    turnCounterInRound = nextTurnIdx,
                    isInTransition = true, transitionTimeRemainingMs = settings.interRoundPauseMs, transitionLabel = "ROUND",
                    currentRoundTimeMs = getOmniDuration(settings, "ROUND", gameIdx = currentGameIdx, roundIdx = nextRoundIdx),
                    currentTurnTimeMs = getOmniDuration(settings, "TURN", gameIdx = currentGameIdx, roundIdx = nextRoundIdx, playerIdx = nextTurnIdx) + bankedMs,
                    playerTimeBanks = drawnBanks
                )
            }
        }
        val turnBanks = handleTimeBankScope(updatedBanks, settings.timeBankScope, "TURN")
        val nextPlayerIdx = calculateNextPlayerIndex(nextTurnIdx, nextRoundIdx, settings)
        val (drawnBanks, bankedMs) = drawOmniBank(turnBanks, settings, nextPlayerIdx)
        return state.copy(
            currentPlayerIndex = nextPlayerIdx, currentPhaseIndex = nextPhaseIdx, turnCounterInRound = nextTurnIdx, isInTransition = true,
            transitionTimeRemainingMs = settings.interTurnPauseMs, transitionLabel = "TURN",
            currentTurnTimeMs = getOmniDuration(settings, "TURN", gameIdx = currentGameIdx, roundIdx = nextRoundIdx, playerIdx = nextTurnIdx) + bankedMs,
            playerTimeBanks = drawnBanks
        )
    }
    return state.copy(currentPhaseIndex = nextPhaseIdx, currentPhaseTimeMs = getOmniDuration(settings, "PHASE", gameIdx = currentGameIdx, roundIdx = nextRoundIdx, playerIdx = nextTurnIdx, phaseIdx = nextPhaseIdx), playerTimeBanks = updatedBanks)
}

class OmniTimerViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepo = SettingsRepository(application)
    private val soundManager = SoundManager(application)

    private val _omniSettings = MutableStateFlow(OmniSettings())
    val omniSettings: StateFlow<OmniSettings> = _omniSettings.asStateFlow()

    private val _omniState = MutableStateFlow(createInitialOmniState(OmniSettings()))
    val omniState: StateFlow<OmniState> = _omniState.asStateFlow()

    private var timerJob: Job? = null
    private var lastTickTime: Long = 0

    init {
        viewModelScope.launch {
            val savedOmni = settingsRepo.omniSettingsFlow.first()
            _omniSettings.value = savedOmni
            _omniState.value = createInitialOmniState(savedOmni)

            // soundManager.loadSounds() was never called here, so the SoundPool stayed null forever
            // and playTripleBeep()/playGong()/playShortBeep() were silent no-ops -- see AUDIT.md section 7.1.
            // Reuses the classic clock's own audio prefs (custom sounds/volume) rather than adding a
            // separate Omni-specific audio settings page.
            soundManager.loadSounds(settingsRepo.settingsFlow.first())
        }
    }

    fun updateOmniSettings(newSettings: OmniSettings) {
        _omniSettings.value = newSettings
        if (!_omniState.value.isRunning) {
            _omniState.value = createInitialOmniState(newSettings)
        }
        viewModelScope.launch {
            settingsRepo.saveOmniSettings(newSettings)
        }
    }

    fun startOmni() {
        val settings = _omniSettings.value
        _omniSettings.update { it.copy(isEnabled = true) }

        if (!_omniState.value.hasStarted) {
            val initial = createInitialOmniState(settings)
            _omniState.value = initial.copy(
                isRunning = true,
                hasStarted = true,
                isLaunching = settings.launchCountdownMs > 0,
                launchTimeRemainingMs = settings.launchCountdownMs
            )
        } else {
            _omniState.update { it.copy(isRunning = true) }
        }

        if (timerJob == null) {
            lastTickTime = System.currentTimeMillis()
            timerJob = viewModelScope.launch {
                while (isActive) {
                    val state = _omniState.value
                    if (!state.isRunning) break

                    val needsFastTick = state.currentTurnTimeMs < 10_000 || state.currentPhaseTimeMs < 10_000
                    val delayMs = if (needsFastTick) 10L else 100L

                    delay(delayMs.milliseconds)
                    val now = System.currentTimeMillis()
                    tickOmni(now - lastTickTime)
                    lastTickTime = now
                }
                timerJob = null
            }
        }
    }

    fun pauseOmni() {
        _omniState.update { it.copy(isRunning = false) }
    }

    fun stopOmni() {
        timerJob?.cancel()
        timerJob = null
        _omniState.update { it.copy(isRunning = false, hasStarted = false) }
    }

    fun resetOmni() {
        timerJob?.cancel()
        timerJob = null
        _omniState.value = createInitialOmniState(_omniSettings.value)
    }

    fun advanceOmni() {
        _omniState.update { state -> computeOmniAdvance(state, _omniSettings.value) }
    }

    fun confirmOmniReady() {
        _omniState.update { it.copy(isInTransition = false, transitionTimeRemainingMs = 0) }
    }

    private fun createInitialOmniState(settings: OmniSettings): OmniState {
        return OmniState(
            currentGlobalTimeMs = settings.globalDurationMs,
            currentGameTimeMs = settings.games.firstOrNull()?.durationMs ?: settings.gameDurationMs,
            currentRoundTimeMs = getOmniDuration(settings, "ROUND", gameIdx = 0, roundIdx = 0),
            currentTurnTimeMs = getOmniDuration(settings, "TURN", gameIdx = 0, roundIdx = 0, playerIdx = 0),
            currentPhaseTimeMs = getOmniDuration(settings, "PHASE", gameIdx = 0, roundIdx = 0, playerIdx = 0, phaseIdx = 0),
            currentPlayerIndex = calculateNextPlayerIndex(0, 0, settings),
            turnCounterInRound = 0,
            playerTimeBanks = emptyMap()
        )
    }

    private fun tickOmni(delta: Long) {
        _omniState.update { state ->
            if (!state.isRunning) return@update state
            val settings = _omniSettings.value
            var newState = state

            if (state.isLaunching) {
                val newLaunchRem = (state.launchTimeRemainingMs - delta).coerceAtLeast(0)
                newState = newState.copy(launchTimeRemainingMs = newLaunchRem, isLaunching = newLaunchRem > 0)
                if (newLaunchRem > 0) return@update newState
            }

            if (state.isInTransition) {
                val newTransition = (state.transitionTimeRemainingMs - delta).coerceAtLeast(0)
                newState = newState.copy(
                    transitionTimeRemainingMs = newTransition,
                    currentGlobalTimeMs = if (settings.pauseDeductsFromGlobal) (state.currentGlobalTimeMs - delta).coerceAtLeast(0) else state.currentGlobalTimeMs,
                    currentGameTimeMs = if (settings.pauseDeductsFromGame) (state.currentGameTimeMs - delta).coerceAtLeast(0) else state.currentGameTimeMs,
                    currentRoundTimeMs = if (settings.pauseDeductsFromRound) (state.currentRoundTimeMs - delta).coerceAtLeast(0) else state.currentRoundTimeMs
                )
                if (newTransition <= 0 && settings.transitionType == TransitionType.AUTOMATIC) { newState = newState.copy(isInTransition = false) }
                return@update newState
            }

            val oldGlobal = state.currentGlobalTimeMs; val oldGame = state.currentGameTimeMs; val oldRound = state.currentRoundTimeMs; val oldTurn = state.currentTurnTimeMs; val oldPhase = state.currentPhaseTimeMs
            if (settings.useGlobalClock) newState = newState.copy(currentGlobalTimeMs = (newState.currentGlobalTimeMs - delta).coerceAtLeast(0))
            if (settings.useGameClock) newState = newState.copy(currentGameTimeMs = (newState.currentGameTimeMs - delta).coerceAtLeast(0))
            if (settings.useRoundClock) newState = newState.copy(currentRoundTimeMs = (newState.currentRoundTimeMs - delta).coerceAtLeast(0))
            if (settings.useTurnClock) newState = newState.copy(currentTurnTimeMs = (newState.currentTurnTimeMs - delta).coerceAtLeast(0))
            if (settings.usePhaseClock) newState = newState.copy(currentPhaseTimeMs = (newState.currentPhaseTimeMs - delta).coerceAtLeast(0))

            if (oldGlobal > 0 && newState.currentGlobalTimeMs <= 0 && settings.soundGameEnd) soundManager.playTripleBeep()
            if (oldGame > 0 && newState.currentGameTimeMs <= 0 && settings.soundGameEnd) soundManager.playTripleBeep()
            if (oldRound > 0 && newState.currentRoundTimeMs <= 0 && settings.soundRoundEnd) soundManager.playGong()
            if (oldTurn > 0 && newState.currentTurnTimeMs <= 0 && settings.soundTurnEnd) soundManager.playShortBeep()
            if (oldPhase > 0 && newState.currentPhaseTimeMs <= 0 && settings.soundTurnEnd) soundManager.playShortBeep()

            // Whichever level with *ForcesCutoff enabled crossed to zero this tick forces an advance,
            // outermost first (a session/game/round cutoff already implies whatever is nested inside
            // it is being cut short too, so there is never a need to apply more than one per tick).
            val forceLevel = when {
                settings.globalForcesCutoff && oldGlobal > 0 && newState.currentGlobalTimeMs <= 0 -> "SESSION"
                settings.gameForcesCutoff && oldGame > 0 && newState.currentGameTimeMs <= 0 -> "GAME"
                settings.roundForcesCutoff && oldRound > 0 && newState.currentRoundTimeMs <= 0 -> "ROUND"
                settings.turnForcesCutoff && oldTurn > 0 && newState.currentTurnTimeMs <= 0 -> "TURN"
                settings.phaseForcesCutoff && oldPhase > 0 && newState.currentPhaseTimeMs <= 0 -> "PHASE"
                else -> null
            }
            if (forceLevel != null) {
                newState = computeOmniAdvance(newState, settings, if (forceLevel == "PHASE") null else forceLevel)
            }

            newState
        }
    }

    override fun onCleared() {
        soundManager.release()
        timerJob?.cancel()
    }
}
