package jp.gr.java_conf.SenseMusicClock.Clock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.CountDownTimer
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import jp.gr.java_conf.SenseMusicClock.R
import jp.gr.java_conf.SenseMusicClock.vibrateOnceSafe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class TimerService : Service() {

    companion object {
        const val ACTION_START_TIMER = "jp.gr.java_conf.SenseMusicClock.Clock.TimerService.ACTION_START_TIMER"
        const val ACTION_STOP_TIMER = "jp.gr.java_conf.SenseMusicClock.Clock.TimerService.ACTION_STOP_TIMER"
        const val EXTRA_TIMER_DURATION = "timer_duration_millis"

        const val CHANNEL_ID = "timer_00110010010100101001"

        const val NOTIFICATION_ID = 6924
    }

    // 1. Binderのインスタンスを作成
    private val binder = LocalBinder()

    // Activityが接続（Bind）してきたときに呼ばれる
    override fun onBind(intent: Intent): IBinder = binder


    // 2. 自分自身を返すための中継クラス
    inner class LocalBinder : Binder() {
        fun getService(): TimerService = this@TimerService
    }
    private var timer: CountDownTimer? = null




    // 1. 内部更新用のMutableStateFlow
    private val _remainingTime = MutableStateFlow(0L)
    // 2. 外部（Activity）公開用の読み取り専用Flow
    val remainingTime = _remainingTime.asStateFlow()

    var isTimerFinished: Boolean = false

    private var foregroundStarted = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("TimerService", "onStartCommand received: intent=$intent flags=$flags startId=$startId")

        when(intent?.action) {
            ACTION_START_TIMER -> {
                val duration = intent.getLongExtra(EXTRA_TIMER_DURATION, 0L)
                Log.d("TimerService", "ACTION_START_TIMER with duration=$duration")
                startTimer(duration)
            }
            ACTION_STOP_TIMER -> {
                Log.d("TimerService", "ACTION_STOP_TIMER received")
                stopTimer()
            }
            else -> {
                Log.d("TimerService", "onStartCommand: unknown or null action: ${intent?.action}")
            }
        }

        // If the service is killed by the system, do not recreate (preserve previous behavior)
        return START_NOT_STICKY
    }


    override fun onCreate() {
        super.onCreate()
        Log.d("TimerService", "onCreate called")
        createNotificationChannel()


    }


    private fun createNotificationChannel (){
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "SMC Timer",
                NotificationManager.IMPORTANCE_LOW
            )
        )

    }
    private fun showNotification(endTime: Long) {
        Log.d("TimerService", "showNotification called endTime=$endTime foregroundStarted=$foregroundStarted")
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // アイコンは必須
            .setContentTitle("タイマー起動中")
            .setOngoing(true)                // ユーザーが消せないようにする
            .setOnlyAlertOnce(true)          // 更新時に音を鳴らさない
            .setUsesChronometer(true)        // ★OSが勝手に時間を進めてくれる
            .setWhen(endTime)                // ★基準時間をセット
            // カウントダウンなら true、ストップウォッチなら false
            .setChronometerCountDown(true)

        // サービスをフォアグラウンドに昇格（これでアプリを閉じても死なない）
        startForeground(NOTIFICATION_ID, builder.build())
        foregroundStarted = true
    }

    fun isTimerRunning(): Boolean {
        return timer != null
    }



    private fun startTimer(duration: Long) {
        Log.d("TimerService", "startTimer called with duration=$duration")
        stopTimer()
        val random = (1..4).random().toLong()
        timer = object : CountDownTimer(duration, random * 100) { // 10msごとに更新
            override fun onTick(millisUntilFinished: Long) {
                _remainingTime.value = millisUntilFinished // Flowの値を更新
            }

            override fun onFinish() {
                Log.d("TimerService", "CountDownTimer finished")
                _remainingTime.value = 0L
                vibrateOnceSafe()
                stopForeground(STOP_FOREGROUND_REMOVE)
                foregroundStarted = false
                isTimerFinished = true
            }
        }.start()
        val endTime = System.currentTimeMillis() +
                duration
        showNotification(endTime)
    }

    private fun stopTimer() {
        Log.d("TimerService", "stopTimer called")
        timer?.cancel()
        timer = null
        isTimerFinished = false
        _remainingTime.value = 0L
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.w("TimerService", "stopForeground failed or not running", e)
        }
        foregroundStarted = false
    }


}