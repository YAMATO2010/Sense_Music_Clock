package jp.gr.java_conf.SenseMusicClock.Clock

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ForegroundInfo
import androidx.core.app.NotificationCompat
import jp.gr.java_conf.SenseMusicClock.R
import jp.gr.java_conf.SenseMusicClock.playDefaultAlarmRingtoneSafe
import jp.gr.java_conf.SenseMusicClock.vibrateOnceSafe

class AlarmWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    companion object {
        private const val CHANNEL_ID = "smc_alarm_channel"
        private const val NOTIF_ID = 2001
    }

    override suspend fun doWork(): Result {
        // ensure channel exists
        try {
            val mgr = applicationContext.getSystemService(android.app.NotificationManager::class.java)
            mgr?.createNotificationChannel(android.app.NotificationChannel(CHANNEL_ID, "SMC Alarm", android.app.NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alarm notifications"
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
            })
        } catch (e: Exception) {
           Log.w("AlarmWorker", "create channel failed", e)
        }

        // build label: try to get scheduled epoch from input, otherwise use current time
        val scheduledEpoch = inputData.getLong("delayMillis", -1L)
        val now = System.currentTimeMillis()
        val hhmm = try {
            val scheduled = if (scheduledEpoch > 0) java.time.Instant.ofEpochMilli(now + scheduledEpoch).atZone(java.time.ZoneId.systemDefault())
            else java.time.ZonedDateTime.now()
            String.format(java.util.Locale.getDefault(), "%02d:%02d", scheduled.hour, scheduled.minute)
        } catch (e: Exception) { Log.w("AlarmWorker", "format scheduled time failed", e); "" }

        // Instead of promoting its own notification, delegate to ClockService by starting it with ACTION_ALARM_TRIGGERED
        try {
            val alarmEpochFromNow = inputData.getLong("delayMillis", -1L)
            val svcIntent = android.content.Intent(applicationContext, ClockService::class.java).apply {

                action = ClockService.ACTION_ALARM_TRIGGERED
                if (alarmEpochFromNow > 0) putExtra(ClockService.EXTRA_ALARM_EPOCH, System.currentTimeMillis() + alarmEpochFromNow)

            }
            applicationContext.startService(svcIntent)
        } catch (e: Exception) { Log.w("AlarmWorker", "delegate to ClockService failed", e) }

        // still play sound & vibrate locally in worker as fallback
        try { applicationContext.playDefaultAlarmRingtoneSafe() } catch (e: Exception) { Log.w("AlarmWorker", "play ringtone fallback failed", e) }
        try { applicationContext.vibrateOnceSafe() } catch (e: Exception) { Log.w("AlarmWorker", "vibrate fallback failed", e) }

        return Result.success()
    }
}
