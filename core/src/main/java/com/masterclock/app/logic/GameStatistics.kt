package com.masterclock.app.logic

/**
 * Time-usage analysis over recorded games.
 *
 * Everything here is pure and Android-free so it can be unit tested directly. Logs are stored as
 * opaque JSON blobs rather than queryable columns (see GameDatabase), so aggregation has to happen
 * in Kotlin over the deserialized [GameLog]s -- there is no SQL path to take instead.
 */

/** One completed move, with how long the player actually spent on it. */
data class MoveDuration(
    val playerIndex: Int,
    /** 1-based, counted per player. */
    val moveNumber: Int,
    val durationMs: Long,
    /** Clock left when the move was made, *before* any increment was applied. */
    val timeRemainingMs: Long,
)

data class ModeTally(val mode: TimerMode, val games: Int)

data class GameStatistics(
    val gamesPlayed: Int,
    val totalMoves: Int,
    val totalThinkTimeMs: Long,
    val averageMoveMs: Long,
    val medianMoveMs: Long,
    val slowestMoveMs: Long,
    val slowestMoveGameStart: Long?,
    /** Share of moves made with under 10% of the starting clock left, 0f..1f. */
    val timePressureShare: Float,
    val perMode: List<ModeTally>,
) {
    val isEmpty: Boolean get() = totalMoves == 0
}

/**
 * Per-move durations for one game.
 *
 * Prefers [GameEvent.timeSpentMs], which games recorded from v0.8.13 onwards carry. Older logs
 * predate that field, so their durations are reconstructed from the gap between consecutive move
 * events with any PAUSE..RESUME span removed -- otherwise a game left paused overnight would report
 * a single eight-hour move.
 *
 * Note the first press logs INITIAL_PRESS rather than MOVE, so it opens the first turn without
 * being counted as one; per-player move numbering starts at the first real move.
 */
fun moveDurations(log: GameLog): List<MoveDuration> {
    val result = mutableListOf<MoveDuration>()
    val moveNumbers = mutableMapOf<Int, Int>()

    var turnStart: Long? = null
    var pausedAt: Long? = null
    var pausedMs = 0L

    for (event in log.events.sortedBy { it.timestamp }) {
        when (event.eventType) {
            "START", "INITIAL_PRESS", "RESET" -> {
                turnStart = event.timestamp
                pausedMs = 0L
                pausedAt = null
            }
            "PAUSE" -> pausedAt = event.timestamp
            "RESUME" -> {
                pausedAt?.let { pausedMs += (event.timestamp - it).coerceAtLeast(0L) }
                pausedAt = null
            }
            "MOVE" -> {
                val player = event.playerIndex ?: continue
                val number = (moveNumbers[player] ?: 0) + 1
                moveNumbers[player] = number

                val reconstructed = turnStart
                    ?.let { (event.timestamp - it - pausedMs).coerceAtLeast(0L) }
                    ?: 0L

                result += MoveDuration(
                    playerIndex = player,
                    moveNumber = number,
                    durationMs = event.timeSpentMs ?: reconstructed,
                    timeRemainingMs = event.timeRemainingMs ?: 0L,
                )

                turnStart = event.timestamp
                pausedMs = 0L
                pausedAt = null
            }
        }
    }
    return result
}

/** Starting clock for [playerIndex] (1-based), used to judge time pressure. */
private fun startingTimeMs(log: GameLog, playerIndex: Int): Long =
    log.initialPlayerStates.getOrNull(playerIndex - 1)?.timeRemainingMs ?: 0L

fun computeStatistics(history: List<GameLog>): GameStatistics {
    val durations = history.flatMap { log -> moveDurations(log).map { log to it } }
    val allMs = durations.map { it.second.durationMs }.sorted()

    if (allMs.isEmpty()) {
        return GameStatistics(
            gamesPlayed = history.size,
            totalMoves = 0,
            totalThinkTimeMs = 0,
            averageMoveMs = 0,
            medianMoveMs = 0,
            slowestMoveMs = 0,
            slowestMoveGameStart = null,
            timePressureShare = 0f,
            perMode = modeTallies(history),
        )
    }

    val slowest = durations.maxByOrNull { it.second.durationMs }

    // A move counts as played under pressure when less than a tenth of the starting clock is left.
    // Games with no recorded starting time (imported logs, move-timer modes) are excluded from the
    // denominator rather than silently counted as unpressured.
    val judgeable = durations.filter { (log, move) -> startingTimeMs(log, move.playerIndex) > 0 }
    val pressured = judgeable.count { (log, move) ->
        move.timeRemainingMs < startingTimeMs(log, move.playerIndex) / 10
    }

    return GameStatistics(
        gamesPlayed = history.size,
        totalMoves = allMs.size,
        totalThinkTimeMs = allMs.sum(),
        averageMoveMs = allMs.sum() / allMs.size,
        medianMoveMs = median(allMs),
        slowestMoveMs = slowest?.second?.durationMs ?: 0L,
        slowestMoveGameStart = slowest?.first?.startTime,
        timePressureShare = if (judgeable.isEmpty()) 0f else pressured.toFloat() / judgeable.size,
        perMode = modeTallies(history),
    )
}

private fun median(sorted: List<Long>): Long {
    if (sorted.isEmpty()) return 0
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
}

private fun modeTallies(history: List<GameLog>): List<ModeTally> = history
    .groupingBy { log ->
        if (log.settings.differentSettingsPerPlayer) log.settings.p1Custom.mode else log.settings.main.mode
    }
    .eachCount()
    .map { ModeTally(it.key, it.value) }
    .sortedByDescending { it.games }
