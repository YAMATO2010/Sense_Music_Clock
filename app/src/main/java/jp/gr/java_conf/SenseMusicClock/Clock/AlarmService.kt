package jp.gr.java_conf.SenseMusicClock.Clock

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import jp.gr.java_conf.SenseMusicClock.PrefsManager
import jp.gr.java_conf.SenseMusicClock.R
import jp.gr.java_conf.SenseMusicClock.vibrateOnceSafe
import kotlinx.coroutines.launch
import java.util.Calendar


class AlarmService : LifecycleService() {
    override fun onBind(intent: Intent): IBinder? {

        super.onBind(intent)


        return null
    }


    private var ringtone: Ringtone? = null
    private var isForeground = false

    companion object {
        // Alarm trigger action sent from AlarmWorker
        const val ACTION_ALARM_TRIGGERED = "jp.gr.java_conf.SenseMusicClock.ACTION_ALARM_TRIGGERED"
        const val EXTRA_ALARM_EPOCH = "alarm_epoch_millis"

        const val ACTION_ALARM_START = "jp.gr.java_conf.SenseMusicClock.ACTION_ALARM_START"

        const val ACTION_STOP_ALARM = "jp.gr.java_conf.SenseMusicClock.ACTION_STOP_ALARM"

        const val ACTION_CANCEL_ALARM = "jp.gr.java_conf.SenseMusicClock.ACTION_CANCEL_ALARM"

        const val ACTION_SET_ALARM = "jp.gr.java_conf.SenseMusicClock.ACTION_SET_ALARM"


        private const val PLAY_ALARM_CHANNEL_ID = "smc_play_alarm_channel"

        private const val INFO_ALARM_CHANNEL_ID = "smc_info_alarm_channel"
        const val ALARM_MINUTE_KEY = "alarm_minute"
        const val ALARM_HOUR_KEY = "alarm_hour"
        const val requestCode = 4878

        const val stopRequestCode = 6794

        const val NOTIF_ID_ALARM = 3199


    }


    @RequiresPermission(Manifest.permission.SCHEDULE_EXACT_ALARM)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d("AlarmService", "Received intent with action: ${intent?.action}")
        when (intent?.action) {
            ACTION_ALARM_START -> startAlarm()
            ACTION_STOP_ALARM -> {
                lifecycleScope.launch {

                    stopAlarm()
                }
            }

            ACTION_CANCEL_ALARM -> {
                lifecycleScope.launch {
                    cancelAlarm(this@AlarmService)
                }
            }

            ACTION_SET_ALARM -> {
                lifecycleScope.launch {

                    setAlarm(
                        this@AlarmService,
                        intent.getIntExtra(ALARM_HOUR_KEY, 0),
                        intent.getIntExtra(ALARM_MINUTE_KEY, 0)
                    )
                }
            }
        }
        return START_NOT_STICKY
    }

    // アラームを鳴らすメソッド
    fun startAlarm() {

        Log.d("AlarmService", "Starting alarm")
        createChannel()
        createAlarmNotification(this)
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ringtone = RingtoneManager.getRingtone(this, alarmUri).apply {
            isLooping = true  // ループ設定
            play()
        }
        vibrateOnceSafe()


    }

    fun getNextAlarmTime(hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1) // 今日の時間が過ぎている場合は翌日に設定
        }
        return calendar.timeInMillis
    }


    @RequiresPermission(Manifest.permission.SCHEDULE_EXACT_ALARM)
    suspend fun setAlarm(context: Context, hour: Int, minute: Int) {
        // 1. AlarmReceiver を呼び出すための Intent を作成
        val pendingIntent = createPendingIntent(context)
        createChannel()
        // 3. AlarmManager を取得

        val triggerTimeMillis = getNextAlarmTime(hour, minute)
        val alarmManager = context.getSystemService(ALARM_SERVICE) as AlarmManager


        Log.d("AlarmService", "Setting alarm for $hour:$minute (epoch: $triggerTimeMillis)")
        try {

            PrefsManager.setSetAlarmTime(this, hour, minute)
            createAlarmInfoNotification(context, "アラームセット / $hour:${minute}")
            // 4. 正確な時間にアラームをセット
            // RTC_WAKEUP はスリープ中でも端末を起こして実行する設定
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTimeMillis,
                pendingIntent
            )
            Log.d(
                "AlarmService",
                "AlarmManager.setExactAndAllowWhileIdle succeeded; pendingIntent=$pendingIntent"
            )
        } catch (e: SecurityException) {
            Log.e("AlarmService", "SecurityException while setting alarm", e)
            // fallthrough - persist failed state
            PrefsManager.setSetAlarmTime(this)
        } catch (e: Exception) {
            Log.e("AlarmService", "Exception while setting alarm", e)
            PrefsManager.setSetAlarmTime(this)
        }


    }

    fun createPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_START
        }


        // 2. PendingIntent（あとで実行される予約券）を作成
        // FLAG_IMMUTABLE は Android 12 以降で必須
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return pendingIntent
    }

    fun createStopAlarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmService::class.java).apply {
            action = ACTION_STOP_ALARM
        }
        return PendingIntent.getService(
            context,
            stopRequestCode, // 別のリクエストコードを使用して区別
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    suspend fun cancelAlarm(context: Context) {
        val pendingIntent = createPendingIntent(context)
        val alarmManager = context.getSystemService(ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
        PrefsManager.setSetAlarmTime(this)
        stopSelf()
    }

    private fun createChannel() {

        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel(
                PLAY_ALARM_CHANNEL_ID,
                "SMC Alarm",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarm notifications"
                // leave default sound/vibration so alarm notification can be prominent
            }
        )
        nm?.createNotificationChannel(
            NotificationChannel(
                INFO_ALARM_CHANNEL_ID,
                "SMC Alarm Info",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Alarm info notifications"
            }
        )

    }

    fun createAlarmNotification(context: Context) {


        val notification = NotificationCompat.Builder(context, PLAY_ALARM_CHANNEL_ID)
            .setContentTitle("SMC  アラーム")
            .setContentText("")
            .setSmallIcon(R.drawable.ic_notification) // 適切なアイコンを設定
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .addAction(R.drawable.ic_notification, "停止", createStopAlarmPendingIntent(context))
            .build()
        startForeground(NOTIF_ID_ALARM, notification)
    }

    fun createAlarmInfoNotification(context: Context, infoText: String) {

        val notification = NotificationCompat.Builder(context, INFO_ALARM_CHANNEL_ID)
            .setContentTitle("SMC アラームセット中")
            .setContentText(infoText)
            .setSmallIcon(R.drawable.ic_notification) // 適切なアイコンを設定
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
        startForeground(NOTIF_ID_ALARM, notification)
    }

    // アラームを止めるメソッド
    suspend fun stopAlarm() {
        ringtone?.let {

            it.stop()
            Log.d("AlarmService", "Alarm stopped")


        }
        ringtone = null
        PrefsManager.setSetAlarmTime(this)


        stopSelf()
    }
}
