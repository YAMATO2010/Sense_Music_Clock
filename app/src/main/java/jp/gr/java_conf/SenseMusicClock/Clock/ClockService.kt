
package jp.gr.java_conf.SenseMusicClock.Clock

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import jp.gr.java_conf.SenseMusicClock.R
import java.util.concurrent.TimeUnit

class ClockService : Service() {

    companion object {
        const val ACTION_START_TIMER = "jp.gr.java_conf.SenseMusicClock.ACTION_START_TIMER"
        const val ACTION_STOP_TIMER = "jp.gr.java_conf.SenseMusicClock.ACTION_STOP_TIMER"
        const val EXTRA_DURATION = "duration_millis"

        const val BROADCAST_TICK = "jp.gr.java_conf.SenseMusicClock.TIMER_TICK"
        const val BROADCAST_FINISHED = "jp.gr.java_conf.SenseMusicClock.TIMER_FINISHED"
        const val EXTRA_REMAINING = "remaining_millis"

        private const val CHANNEL_ID = "smc_timer_channel"
        private const val NOTIF_ID = 1001
    }

    private var timer: CountDownTimer? = null
    private var currentRingtone: Ringtone? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
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
        try {
            val alarmUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            currentRingtone = RingtoneManager.getRingtone(this, alarmUri)
            currentRingtone?.play()
        } catch (e: Exception) {
            // ignore
        }

        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun stopRingtone() {
        try {
            currentRingtone?.stop()
        } catch (e: Exception) {
        } finally {
            currentRingtone = null
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val stopIntent = Intent(this, ClockService::class.java).apply { action = ACTION_STOP_TIMER }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification) // 適切なアイコンに置き換える
            .setOngoing(true)
            .addAction(NotificationCompat.Action(0, "停止", stopPending))
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notif = buildNotification(contentText)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, notif)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "SMC Timer", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Timer notifications"
                }
            )
        }
    }

    private fun formatRemaining(millis: Long): String {
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        return if (hours > 0) String.format("%02d:%02d:%02d", hours, minutes, seconds)
        else String.format("%02d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTimer()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}