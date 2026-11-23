package com.masterclock.app

import com.masterclock.app.logic.WAKE_MARGIN_MS
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
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.masterclock.app.logic.BackgroundClock
import com.masterclock.app.logic.NotifiedCountdown
import com.masterclock.app.logic.wakeForMs
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
    /**
     * The game being kept, by its ViewModel's identity. The activity's was used, and an activity is
     * recreated by a theme change while the game goes on in the same ViewModel; and a second activity,
     * opened from a shortcut, stopped on its resume the service another's game was kept by.
     */
    @Volatile var owner: Int? = null
}

/**
 * Keeps a game left running alive in the background; see runsInBackground.
 *
 * Android may close an app it no longer shows during a long absence, and a game then came back only
 * from its last autosave, or not at all. A foreground service and its notification keep the process,
 * and with it the ViewModel and its ticker. The time itself never depended on this -- the clock goes
 * by elapsed real time, which a phone asleep still counts -- but a flag that falls is only sounded by
 * a process still there, and by a processor awake to run its tick: with the screen off the phone slept
 * and the flag sounded when something else woke it. The service holds the processor until the end
 * the clock counts to; see [keepAwake].
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
        keepAwake(BackgroundClockLink.clock.value)
        scope.launch {
            // A tick changes the time, not what the notification says: it counts down on its own, and
            // is redrawn only when the end it counts to has moved; see NotifiedCountdown.
            BackgroundClockLink.clock.collect { clock ->
                if (clock == null) { stopSelf(); return@collect }
                keepAwake(clock)
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
        // Let go, unless it is the flag that stopped the clock: the service stops with it under
        // FREEZE, and released then, the lock went the instant the flag fell -- and the sound of the
        // flag, a spoken "time is up" above all, which has to be put together first, waited for the
        // phone's next wake. Kept, it lapses within its own limit, the margin left of it. A pause
        // stops the service too, long before the end it waited for, and lets the phone sleep.
        if (!flagJustFell()) flagLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    /** Whether the end the processor was kept awake for has come: the flag has just fallen. */
    private fun flagJustFell(): Boolean =
        awakeFor?.endsAtMs?.let { SystemClock.elapsedRealtime() >= it - FLAG_FELL_SLACK_MS } == true

    /** The end the processor is being kept awake for, to tell when it has moved. */
    private var awakeFor: NotifiedCountdown? = null

    /**
     * Holds the processor until [clock] has sounded what it counts to; see wakeForMs. Taken again only
     * when that end has moved -- a move played, a period begun -- not at every tick, and always with
     * its own time limit, so a game abandoned lets the phone sleep without anything having to end it.
     */
    private fun keepAwake(clock: BackgroundClock?) {
        val wanted = NotifiedCountdown(null, clock?.soundsInMs?.let { SystemClock.elapsedRealtime() + it })
        if (awakeFor?.outOfStepWith(wanted) == false) return
        val flagFell = flagJustFell()
        awakeFor = wanted
        val lock = flagLock ?: applicationContext.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MasterClock:flag")
            .apply { setReferenceCounted(false) }
            .also { flagLock = it }
        // Released first: a lock taken again keeps the earlier limit's release, which would let go of
        // it early.
        if (lock.isHeld) lock.release()
        // With nothing left to sound because the flag has just fallen -- the game going on, under
        // FLAG, NEGATIVE or REVERSE -- held for the margin still, rather than let go: the margin is
        // there for the sound the flag plays, and it was released as that sound began.
        (wakeForMs(clock) ?: WAKE_MARGIN_MS.takeIf { flagFell })?.let { lock.acquire(it) }
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
        /**
         * The lock that keeps the processor up to the flag. The process's, not the service's: it
         * outlives a service that stops as the flag falls, for the length of its own limit.
         */
        private var flagLock: PowerManager.WakeLock? = null

        /** Early by this, an end still counts as reached: the clock is published a tick at a time. */
        private const val FLAG_FELL_SLACK_MS = 1_000L
        private const val CHANNEL_ID = "running_game"
        const val NOTIFICATION_ID = 7031
        private const val ACTION_PAUSE = "com.masterclock.app.action.PAUSE_BACKGROUND_CLOCK"

        /** Starts the service for [clock], as the app leaves the screen with it running. */
        fun start(context: Context, game: Any, clock: BackgroundClock, pause: () -> Unit) {
            BackgroundClockLink.owner = System.identityHashCode(game)
            BackgroundClockLink.pause = pause
            BackgroundClockLink.clock.value = clock
            // Allowed from ON_PAUSE, while the activity is still the one in front. Should Android refuse
            // it anyway, the game goes on as it did without this setting.
            runCatching { ContextCompat.startForegroundService(context, Intent(context, BackgroundClockService::class.java)) }
                .onFailure { Log.w("BackgroundClockService", "Could not keep the clock running in the background", it) }
        }

        /** Back in front, or the game no longer running: the notification goes. */
        fun stop(context: Context) {
            // By emptying what the service watches, and by that alone: it stops itself on seeing it
            // (see onCreate), which it can only do once it has gone to the foreground. stopService
            // from here could reach Android before that, when the app was left and come back to
            // within a few tens of milliseconds, and a service started for the foreground and
            // stopped before saying so closes the app.
            BackgroundClockLink.clock.value = null
            BackgroundClockLink.pause = null
            BackgroundClockLink.owner = null
        }

        /**
         * Ends it if it keeps [game]: back in front with that game, or its activity destroyed for good,
         * the ViewModel and the game cleared with it -- the notification went on counting past zero with a
         * Pause that reached nothing. Only that game's, so that another activity, opened from a shortcut,
         * does not end a game it does not hold.
         */
        fun stopIfKeeping(context: Context, game: Any) {
            if (BackgroundClockLink.owner == System.identityHashCode(game)) stop(context)
        }
    }
}
