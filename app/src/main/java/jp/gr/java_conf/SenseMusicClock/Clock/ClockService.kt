/*package jp.gr.java_conf.SenseMusicClock.Clock

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build

import android.os.CountDownTimer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import jp.gr.java_conf.SenseMusicClock.R
import jp.gr.java_conf.SenseMusicClock.getNotificationManagerCompat
import jp.gr.java_conf.SenseMusicClock.vibrateOnceSafe


//class ClockService : Service() {

    companion object {
        const val ACTION_START_TIMER = "jp.gr.java_conf.SenseMusicClock.ACTION_START_TIMER"
        const val ACTION_STOP_TIMER = "jp.gr.java_conf.SenseMusicClock.ACTION_STOP_TIMER"
        const val EXTRA_DURATION = "duration_millis"



        const val BROADCAST_TICK = "jp.gr.java_conf.SenseMusicClock.TIMER_TICK"



        const val BROADCAST_FINISHED = "jp.gr.java_conf.SenseMusicClock.TIMER_FINISHED"
        const val EXTRA_REMAINING = "remaining_millis"

        private const val CHANNEL_ID = "smc_timer_channel"

        private const val NOTIF_ID = 2347

        // stopwatch actions/broadcasts
        const val ACTION_START_STOPWATCH = "jp.gr.java_conf.SenseMusicClock.ACTION_START_STOPWATCH"
        const val ACTION_PAUSE_STOPWATCH = "jp.gr.java_conf.SenseMusicClock.ACTION_PAUSE_STOPWATCH"
        const val ACTION_RESET_STOPWATCH = "jp.gr.java_conf.SenseMusicClock.ACTION_RESET_STOPWATCH"

        const val BROADCAST_STOPWATCH_TICK = "jp.gr.java_conf.SenseMusicClock.STOPWATCH_TICK"
        const val EXTRA_ELAPSED = "elapsed_millis"
    }

    private var timer: CountDownTimer? = null

    // whether service has been promoted to foreground for API timing requirement
    private var foregroundStarted = false

    // stopwatch state
    private var stopwatchRunning = false
    private var stopwatchStartTime = 0L
    private var stopwatchElapsedWhenPaused = 0L
    private val stopwatchHandler = Handler(Looper.getMainLooper())
    private var lastNotificationUpdate = 0L
    private val notificationUpdateIntervalMs =
        1000L // only update notification once per second to reduce churn
    private val STOPWATCH_CHANNEL_ID = "smc_stopwatch_channel"

    // modify stopwatch runnable to update frequently for centiseconds in UI, but throttle notification updates
    private val stopwatchRunnable = object : Runnable {
        override fun run() {
            val elapsed = getStopwatchElapsed()
            sendStopwatchTick(elapsed)
            val now = System.currentTimeMillis()
            if (now - lastNotificationUpdate >= notificationUpdateIntervalMs) {
                // notification text remains without centiseconds (formatStopwatch)
                updateNotification(formatStopwatch(elapsed))
                lastNotificationUpdate = now
            }
            // update UI frequently to allow centisecond display
            stopwatchHandler.postDelayed(this, 50L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }




    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // If service was started (intent != null) and not yet promoted to foreground, promote quickly
        try {
            if (intent != null && !foregroundStarted) {
                try {
                    // use timer channel by default for quick promotion
                    startForeground(NOTIF_ID, buildNotification("SMC"))
                    foregroundStarted = true
                } catch (e: Exception) {
                    android.util.Log.w(
                        "ClockService",
                        "startForeground initial promotion failed",
                        e
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ClockService", "onStartCommand preflight failed", e)
        }

        when (intent?.action) {
            ACTION_START_STOPWATCH -> {
                startStopwatch()
            }

            ACTION_PAUSE_STOPWATCH -> {
                pauseStopwatch()
            }

            ACTION_RESET_STOPWATCH -> {
                resetStopwatch()
            }

            ACTION_START_TIMER -> {
                val duration = intent.getLongExtra(EXTRA_DURATION, 0L)
                if (duration > 0L) {
                    startForeground(NOTIF_ID, buildNotification(formatRemaining(duration)))
                    startCountDown(duration)
                }
            }

            ACTION_STOP_TIMER -> {
                stopTimer()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startCountDown(durationMillis: Long) {
        timer?.cancel()
        timer = object : CountDownTimer(durationMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                updateNotification(formatRemaining(millisUntilFinished))
                sendTickBroadcast(millisUntilFinished)
            }

            override fun onFinish() {
                updateNotification("完了")
                sendFinishedBroadcast()
                stopSelf()
                vibrateOnceSafe()
            }
        }.start()
    }

    private fun stopTimer() {
        timer?.cancel()
        timer = null
    }

    private fun sendTickBroadcast(millis: Long) {
        val b = Intent(BROADCAST_TICK)
        b.putExtra(EXTRA_REMAINING, millis)
        sendBroadcast(b)
    }

    private fun sendFinishedBroadcast() {
        sendBroadcast(Intent(BROADCAST_FINISHED))
    }

    // build the PendingIntent used for the "停止" action in the notification (timer/stopwatch stop)
    private fun buildStopPendingIntent(action: String = ACTION_STOP_TIMER): PendingIntent {
        val stopIntent = Intent(this, ClockService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this,
            0,
            stopIntent,
            jp.gr.java_conf.SenseMusicClock.pendingIntentFlags()
        )
    }

    private fun buildNotification(contentText: String): Notification {
        // choose appropriate stop action: timers use ACTION_STOP_TIMER, stopwatch has its own controls
        val stopAction = when {
            timer != null -> ACTION_STOP_TIMER
            else -> ACTION_STOP_TIMER
        }
        val stopPending = buildStopPendingIntent(stopAction)
        val stopLabel = "停止"

        // Build combined content listing active features
        val parts = mutableListOf<String>()
        try {
            if (timer != null) parts.add("タイマー")
        } catch (e: Exception) {
            android.util.Log.w("ClockService", "checking timer failed", e)
        }
        try {
            if (stopwatchRunning) parts.add("ストップウォッチ")
        } catch (e: Exception) {
            android.util.Log.w("ClockService", "checking stopwatch state failed", e)
        }

        val finalText = if (parts.isNotEmpty()) "実行中: ${parts.joinToString(", ")}" else contentText

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(finalText)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .addAction(NotificationCompat.Action(0, stopLabel, stopPending))
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notif = buildNotification(contentText)
        val nm = getNotificationManagerCompat()
        nm?.notify(NOTIF_ID, notif)
    }

    private fun createChannel() {

        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "SMC Timer", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Timer notifications"
            }
        )
        // stopwatch channel (silent)
        nm?.createNotificationChannel(
            NotificationChannel(
                STOPWATCH_CHANNEL_ID,
                "SMC Stopwatch",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Stopwatch notifications"
                setSound(null, null)
                enableVibration(false)
            }
        )
        // alarm channel: 高優先でサウンド・バイブ有効（システム設定に従う）


    }

    // reuse shared time formatter
    private fun formatRemaining(millis: Long): String =
        jp.gr.java_conf.SenseMusicClock.formatMillisToTime(millis)

    private fun sendStopwatchTick(elapsed: Long) {
        val b = Intent(BROADCAST_STOPWATCH_TICK)
        b.putExtra(EXTRA_ELAPSED, elapsed)
        sendBroadcast(b)
    }

    private fun getStopwatchElapsed(): Long {
        return if (stopwatchRunning) stopwatchElapsedWhenPaused + (System.currentTimeMillis() - stopwatchStartTime)
        else stopwatchElapsedWhenPaused
    }

    private fun formatStopwatch(millis: Long): String {
        val seconds = (millis / 1000L) % 60L
        val minutes = (millis / 60000L) % 60L
        val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(millis)
        return if (hours > 0) String.format(
            java.util.Locale.getDefault(),
            "%02d:%02d:%02d",
            hours,
            minutes,
            seconds
        )
        else String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun startStopwatch() {
        if (stopwatchRunning) return
        stopwatchStartTime = System.currentTimeMillis()
        stopwatchRunning = true
        try {
            if (!foregroundStarted) {
                startForeground(NOTIF_ID, buildNotification(formatStopwatch(getStopwatchElapsed())))
                foregroundStarted = true
            }
        } catch (e: Exception) {
            android.util.Log.w("ClockService", "promote foreground for stopwatch failed", e)
        }
        try {
            updateNotification(formatStopwatch(getStopwatchElapsed()))
            lastNotificationUpdate = System.currentTimeMillis()
        } catch (e: Exception) {
            android.util.Log.w("ClockService", "updateNotification for stopwatch failed", e)
        }
        stopwatchHandler.post(stopwatchRunnable)
    }

    private fun pauseStopwatch() {
        if (!stopwatchRunning) return
        stopwatchElapsedWhenPaused += System.currentTimeMillis() - stopwatchStartTime
        stopwatchRunning = false
        stopwatchHandler.removeCallbacks(stopwatchRunnable)
        updateNotification(formatStopwatch(getStopwatchElapsed()))
    }

    private fun resetStopwatch() {
        stopwatchHandler.removeCallbacks(stopwatchRunnable)
        stopwatchRunning = false
        stopwatchStartTime = 0L
        stopwatchElapsedWhenPaused = 0L
        try {
            updateNotification("00:00")
        } catch (e: Exception) {
            android.util.Log.w("ClockService", "updateNotification failed on reset", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTimer()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

 */
