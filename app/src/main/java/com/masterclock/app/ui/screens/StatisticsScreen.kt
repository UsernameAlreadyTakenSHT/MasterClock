package com.masterclock.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.masterclock.app.R
import com.masterclock.app.logic.*

/** Fixed player identity colours, shared with the Omni wizard so a player keeps one colour. */
private fun playerColor(playerIndex: Int): Color =
    Color(OMNI_DEFAULT_PLAYER_COLORS[(playerIndex - 1).mod(OMNI_DEFAULT_PLAYER_COLORS.size)])

/**
 * A duration rather than a clock reading: "1h 05m", "2m 30s", "4.2s".
 *
 * Only MINIMAL changes anything. The leading unit is never widened -- "01h 05m" is not a form
 * anyone writes, and the leading zero of [TimePadding.FULL] belongs to clock readings.
 */
private fun formatDuration(ms: Long, padding: TimePadding): String = when {
    ms >= 3_600_000 -> "${ms / 3_600_000}h ${padTimeUnit((ms % 3_600_000) / 60_000, isLeading = false, padding = padding)}m"
    ms >= 60_000 -> "${ms / 60_000}m ${padTimeUnit((ms % 60_000) / 1000, isLeading = false, padding = padding)}s"
    ms >= 1_000 -> "${ms / 1000}.${(ms % 1000) / 100}s"
    else -> "${ms}ms"
}

/**
 * Time spent per move, in play order.
 *
 * Bars are coloured by player rather than by rank, so a player keeps the same colour as the count
 * of players changes. The legend below carries the same identity in text, so colour is never the
 * only cue.
 */
@Composable
fun MoveDurationChart(
    durations: List<MoveDuration>,
    padding: TimePadding,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 140.dp,
) {
    if (durations.isEmpty()) return
    val slowest = durations.maxOf { it.durationMs }.coerceAtLeast(1L)
    val players = durations.map { it.playerIndex }.distinct().sorted()
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    // Hoisted: stringResource is @Composable and cannot be called inside the semantics lambda.
    val chartDescription = pluralStringResource(R.plurals.stats_chart_description, durations.size, formatDuration(slowest, padding), durations.size)

    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatDuration(slowest, padding),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                pluralStringResource(R.plurals.stats_chart_moves, durations.size, durations.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = chartDescription }
        ) {
            val gap = 2.dp.toPx()
            val slotWidth = size.width / durations.size
            val barWidth = (slotWidth - gap).coerceAtLeast(1f)
            val radius = CornerRadius(minOf(4.dp.toPx(), barWidth / 2f), 0f)

            durations.forEachIndexed { index, move ->
                val barHeight = (move.durationMs.toFloat() / slowest) * size.height
                if (barHeight <= 0f) return@forEachIndexed
                drawRoundRect(
                    color = playerColor(move.playerIndex),
                    topLeft = Offset(index * slotWidth, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = radius,
                )
            }

            drawLine(
                color = axisColor,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx(),
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            players.forEach { player ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(playerColor(player))
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.common_player_n, player),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun StatisticsScreen(history: List<GameLog>, timePadding: TimePadding, onBack: () -> Unit) {
    val stats = remember(history) { computeStatistics(history) }
    val recentDurations = remember(history) {
        history.maxByOrNull { it.startTime }?.let { moveDurations(it) }.orEmpty()
    }

    ToolScaffold(title = stringResource(R.string.stats_title), onBack = onBack) { padding ->
        if (stats.isEmpty) {
            Box(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.stats_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@ToolScaffold
        }

        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile(stringResource(R.string.stats_games_played), "${stats.gamesPlayed}", Modifier.weight(1f))
                StatTile(stringResource(R.string.stats_moves), "${stats.totalMoves}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile(stringResource(R.string.stats_average_move), formatDuration(stats.averageMoveMs, timePadding), Modifier.weight(1f))
                StatTile(stringResource(R.string.stats_median_move), formatDuration(stats.medianMoveMs, timePadding), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile(stringResource(R.string.stats_slowest_move), formatDuration(stats.slowestMoveMs, timePadding), Modifier.weight(1f))
                StatTile(stringResource(R.string.stats_time_on_clock), formatDuration(stats.totalThinkTimeMs, timePadding), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            StatTile(
                stringResource(R.string.stats_time_pressure),
                "${(stats.timePressureShare * 100).toInt()}%",
                Modifier.fillMaxWidth(),
            )

            if (recentDurations.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(R.string.stats_recent_game),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                MoveDurationChart(recentDurations, timePadding, Modifier.fillMaxWidth())
            }

            if (stats.perMode.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(R.string.stats_modes_played),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                val mostPlayed = stats.perMode.maxOf { it.games }.coerceAtLeast(1)
                stats.perMode.forEach { tally ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            tally.mode.displayName(),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            Modifier
                                .weight(1.4f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(tally.games.toFloat() / mostPlayed)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${tally.games}",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
