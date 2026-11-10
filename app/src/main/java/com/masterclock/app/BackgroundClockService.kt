package com.masterclock.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.masterclock.app.logic.BackgroundClock
import com.masterclock.app.logic.NotifiedCountdown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Between the activity, which holds the clock's ViewModel, and the service, which outlives the
 * activity's visibility but not its process. The activity publishes what is running as it changes and
 * the Pause the notification offers; the service reads both. Nulling [clock] ends the service.
 */
object BackgroundClockLink {
    val clock = MutableStateFlow<BackgroundClock?>(null)
    @Volatile var pause: (() -> Unit)? = null
    /** The activity that started the service, by identity: its ViewModel is the game being kept. */
    @Volatile var owner: Int? = null
}

/**
 * Keeps a game left running alive in the background; see runsInBackground.
 *
 * Android may close an app it no longer shows during a long absence, and a game then came back only
 * from its last autosave, or not at all. A foreground service and its notification keep the process,
 * and with it the ViewModel and its ticker. The time itself never depended on this -- the clock goes
 * by elapsed real time, which a phone asleep still counts -- but a flag that falls is only sounded by
 * a process still there, and late if the phone was asleep: there is no exact alarm behind it.
 */
class BackgroundClockService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel_clock), NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                }
            )
        }
        // Within five seconds of startForegroundService, whatever the clock says, or Android ends the app.
        startInForeground(notification(BackgroundClockLink.clock.value))
        scope.launch {
            // A tick changes the time, not what the notification says: it counts down on its own, and
            // is redrawn only when the end it counts to has moved; see NotifiedCountdown.
            BackgroundClockLink.clock.collect { clock ->
                if (clock == null) { stopSelf(); return@collect }
                val now = NotifiedCountdown.of(clock, SystemClock.elapsedRealtime())
                if (shown?.outOfStepWith(now) != false) manager.notify(NOTIFICATION_ID, notification(clock))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PAUSE) BackgroundClockLink.pause?.invoke()
        // Not restarted by Android: a process it killed lost the ViewModel this was keeping.
        return START_NOT_STICKY
    }

    // Swiped away from Recents: the activity and its ViewModel go, and there is nothing left to keep.
    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    /** What the notification shows now, to tell when it has fallen out of step with the clock. */
    private var shown: NotifiedCountdown? = null

    private fun notification(clock: BackgroundClock?): Notification {
        val drawnAt = SystemClock.elapsedRealtime()
        shown = clock?.let { NotifiedCountdown.of(it, drawnAt) }
        // Back to the activity that holds the game. The launcher's intent brought a task begun from a
        // preset shortcut forward with a second MainActivity on top -- a second ViewModel, the game in
        // the first still ticking underneath. SINGLE_TOP finds the one there.
        val open = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val tap = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val pause = PendingIntent.getService(
            this, 1, Intent(this, BackgroundClockService::class.java).setAction(ACTION_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = clock?.playerIndex?.let { getString(R.string.a11y_announce_to_move, it) } ?: getString(R.string.a11y_announce_running)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_clock)
            .setContentTitle(title)
            .setContentIntent(tap)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(R.string.timer_pause), pause)
        val left = clock?.countdownMs
        if (left != null) {
            // The time left, counted down by the notification itself, with no update from here.
            builder.setUsesChronometer(true).setChronometerCountDown(true).setWhen(System.currentTimeMillis() + left).setShowWhen(true)
        } else {
            builder.setShowWhen(false)
        }
        return builder.build()
    }

    companion object {
        private const val CHANNEL_ID = "running_game"
        const val NOTIFICATION_ID = 7031
        private const val ACTION_PAUSE = "com.masterclock.app.action.PAUSE_BACKGROUND_CLOCK"

        /** Starts the service for [clock], as the app leaves the screen with it running. */
        fun start(context: Context, clock: BackgroundClock, pause: () -> Unit) {
            BackgroundClockLink.owner = System.identityHashCode(context)
            BackgroundClockLink.pause = pause
            BackgroundClockLink.clock.value = clock
            // Allowed from ON_PAUSE, while the activity is still the one in front. Should Android refuse
            // it anyway, the game goes on as it did without this setting.
            runCatching { ContextCompat.startForegroundService(context, Intent(context, BackgroundClockService::class.java)) }
                .onFailure { Log.w("BackgroundClockService", "Could not keep the clock running in the background", it) }
        }

        /** Back in front, or the game no longer running: the notification goes. */
        fun stop(context: Context) {
            BackgroundClockLink.clock.value = null
            BackgroundClockLink.pause = null
            BackgroundClockLink.owner = null
            context.stopService(Intent(context, BackgroundClockService::class.java))
        }

        /**
         * The activity that started it destroyed for good: its ViewModel is cleared and the game with it,
         * and the notification went on counting past zero with a Pause that reached nothing. Only that
         * activity's, so that another one -- opened from a shortcut -- does not end a game it does not hold.
         */
        fun stopIfStartedBy(context: Context) {
            if (BackgroundClockLink.owner == System.identityHashCode(context)) stop(context)
        }
    }
}
