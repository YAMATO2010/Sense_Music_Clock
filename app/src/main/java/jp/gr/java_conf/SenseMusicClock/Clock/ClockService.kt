package jp.gr.java_conf.SenseMusicClock.Clock

import android.app.*
import android.content.Intent
import android.media.Ringtone

import android.os.CountDownTimer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import jp.gr.java_conf.SenseMusicClock.R
import jp.gr.java_conf.SenseMusicClock.getNotificationManagerCompat
import jp.gr.java_conf.SenseMusicClock.playDefaultAlarmRingtoneSafe
import jp.gr.java_conf.SenseMusicClock.vibrateOnceSafe


class ClockService : Service() {

    companion object {
        const val ACTION_START_TIMER = "jp.gr.java_conf.SenseMusicClock.ACTION_START_TIMER"
        const val ACTION_STOP_TIMER = "jp.gr.java_conf.SenseMusicClock.ACTION_STOP_TIMER"
        const val EXTRA_DURATION = "duration_millis"

        // Alarm trigger action sent from AlarmWorker
        const val ACTION_ALARM_TRIGGERED = "jp.gr.java_conf.SenseMusicClock.ACTION_ALARM_TRIGGERED"
        const val EXTRA_ALARM_EPOCH = "alarm_epoch_millis"

        const val BROADCAST_TICK = "jp.gr.java_conf.SenseMusicClock.TIMER_TICK"


        const val ACTION_STOP_ALARM = "jp.gr.java_conf.SenseMusicClock.ACTION_STOP_ALARM"
        const val BROADCAST_FINISHED = "jp.gr.java_conf.SenseMusicClock.TIMER_FINISHED"
        const val EXTRA_REMAINING = "remaining_millis"

        private const val CHANNEL_ID = "smc_timer_channel"
        private const val ALARM_CHANNEL_ID = "smc_alarm_channel"
        private const val NOTIF_ID = 2347

        // stopwatch actions/broadcasts
        const val ACTION_START_STOPWATCH = "jp.gr.java_conf.SenseMusicClock.ACTION_START_STOPWATCH"
        const val ACTION_PAUSE_STOPWATCH = "jp.gr.java_conf.SenseMusicClock.ACTION_PAUSE_STOPWATCH"
        const val ACTION_RESET_STOPWATCH = "jp.gr.java_conf.SenseMusicClock.ACTION_RESET_STOPWATCH"

        const val BROADCAST_STOPWATCH_TICK = "jp.gr.java_conf.SenseMusicClock.STOPWATCH_TICK"
        const val EXTRA_ELAPSED = "elapsed_millis"
    }

    private var timer: CountDownTimer? = null
    private var currentRingtone: Ringtone? = null
    private var alarmActive: Boolean = false
    private var alarmEpoch: Long = -1L

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
            ACTION_ALARM_TRIGGERED -> {
                // Mark alarm active and remember epoch if provided
                val epoch = intent.getLongExtra(EXTRA_ALARM_EPOCH, -1L)
                alarmActive = true
                if (epoch > 0) alarmEpoch = epoch
                // ensure foreground and update notification immediately
                try {
                    if (!foregroundStarted) {
                        startForeground(NOTIF_ID, buildNotification(""))
                        foregroundStarted = true
                    } else {
                        updateNotification("")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("ClockService", "failed to ensure foreground for alarm", e)
                }
                // play ringtone & vibrate
                currentRingtone = playDefaultAlarmRingtoneSafe()
                try {
                    vibrateOnceSafe()
                } catch (e: Exception) {
                    android.util.Log.w("ClockService", "vibrate failed", e)
                }
            }

            ACTION_START_STOPWATCH -> {
                startStopwatch()
            }

            ACTION_PAUSE_STOPWATCH -> {
                pauseStopwatch()
            }

            ACTION_RESET_STOPWATCH -> {
                resetStopwatch()
            }

            ACTION_STOP_ALARM -> {
                // stop ringtone and clear alarm state
                stopRingtone()
                alarmActive = false
                alarmEpoch = -1L
                // update notification to reflect cleared alarm
                try {
                    updateNotification("")
                } catch (e: Exception) {
                    android.util.Log.w("ClockService", "updateNotification failed on stop alarm", e)
                }
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
                notifyFinished()
                stopSelf()
            }
        }.start()
    }

    private fun stopTimer() {
        timer?.cancel()
        timer = null
        stopRingtone()
    }

    private fun sendTickBroadcast(millis: Long) {
        val b = Intent(BROADCAST_TICK)
        b.putExtra(EXTRA_REMAINING, millis)
        sendBroadcast(b)
    }

    private fun sendFinishedBroadcast() {
        sendBroadcast(Intent(BROADCAST_FINISHED))
    }

    private fun notifyFinished() {
        // play ringtone and vibrate using shared helpers
        currentRingtone = playDefaultAlarmRingtoneSafe()
        vibrateOnceSafe()
    }

    private fun stopRingtone() {
        try {
            currentRingtone?.stop()
        } catch (e: Exception) {
            android.util.Log.w("ClockService", "stopRingtone failed", e)
        } finally {
            currentRingtone = null
        }
    }

    // reuse shared helpers in AndroidUtils.kt
    // build the PendingIntent used for the "停止" action in the notification
    // 引数で停止対象を切り替え可能にする（デフォルトはタイマー停止）
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
        // choose appropriate stop action: prefer alarm stop when alarmActive
        val stopAction = when {
            alarmActive -> ACTION_STOP_ALARM
            timer != null -> ACTION_STOP_TIMER
            else -> ACTION_STOP_TIMER
        }
        val stopPending = buildStopPendingIntent(stopAction)
        val stopLabel = if (stopAction == ACTION_STOP_ALARM) "アラーム停止" else "停止"

        // Build combined content listing active features
        val parts = mutableListOf<String>()
        try {
            if (alarmActive) {
                val label = formatAlarmLabelFromEpoch(alarmEpoch)
                parts.add("アラーム ${label}")
            }
        } catch (e: Exception) {
            android.util.Log.w("ClockService", "formatAlarmLabelFromEpoch failed", e)
        }
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

        val finalText =
            if (parts.isNotEmpty()) "実行中: ${parts.joinToString(", ")}" else contentText

        // choose channel: alarm uses alarm channel (so system may play sound even on low importance channels separately)
        val channelToUse = if (alarmActive) ALARM_CHANNEL_ID else CHANNEL_ID

        return NotificationCompat.Builder(this, channelToUse)
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
        nm?.createNotificationChannel(
            NotificationChannel(
                ALARM_CHANNEL_ID,
                "SMC Alarm",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarm notifications"
                // leave default sound/vibration so alarm notification can be prominent
            }
        )

    }

    private fun formatAlarmLabelFromEpoch(epochMillis: Long): String {
        if (epochMillis <= 0L) return ""
        val z = java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault())
        return String.format(java.util.Locale.getDefault(), "%02d:%02d", z.hour, z.minute)
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
