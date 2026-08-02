package com.masterclock.paper.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.masterclock.paper.R
import com.masterclock.app.logic.ClockAnnouncement
import com.masterclock.app.logic.spokenDuration

/**
 * Turns the screen-reader data that core reports into a sentence.
 *
 * core deliberately has no resources of its own, so the arithmetic lives there and the wording
 * lives here. That split is what lets the announcement logic stay covered by plain unit tests, and
 * what makes these strings translatable at all.
 */

/** "5 minutes 3 seconds", dropping the components that are zero. */
@Composable
fun spokenDurationText(ms: Long): String {
    val duration = spokenDuration(ms)
    if (duration.isExpired) return stringResource(R.string.a11y_no_time_left)

    val parts = mutableListOf<String>()
    if (duration.hours > 0) {
        parts += pluralStringResource(R.plurals.a11y_hours, duration.hours.toInt(), duration.hours)
    }
    if (duration.minutes > 0) {
        parts += pluralStringResource(R.plurals.a11y_minutes, duration.minutes.toInt(), duration.minutes)
    }
    // Always say something, so a clock under a minute does not read as silence.
    if (duration.seconds > 0 || parts.isEmpty()) {
        parts += pluralStringResource(R.plurals.a11y_seconds, duration.seconds.toInt(), duration.seconds)
    }
    return parts.joinToString(" ")
}

@Composable
fun clockAnnouncementText(announcement: ClockAnnouncement): String = when (announcement) {
    is ClockAnnouncement.OutOfTime ->
        stringResource(R.string.a11y_announce_out_of_time, announcement.playerIndex)

    ClockAnnouncement.Paused -> stringResource(R.string.a11y_announce_paused)

    is ClockAnnouncement.ToMove -> {
        val base = stringResource(R.string.a11y_announce_to_move, announcement.playerIndex)
        val periods = announcement.byoyomiPeriodsRemaining
        when {
            periods == null -> base
            periods > 0 -> stringResource(
                R.string.a11y_announce_byoyomi_periods,
                base,
                pluralStringResource(R.plurals.a11y_periods_left, periods, periods),
            )
            else -> stringResource(R.string.a11y_announce_byoyomi, base)
        }
    }
}
