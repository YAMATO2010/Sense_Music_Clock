package jp.gr.java_conf.SenseMusicClock.Clock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent

import android.os.Binder
import android.os.IBinder
import com.google.common.base.Stopwatch

import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import jp.gr.java_conf.SenseMusicClock.R
import javax.annotation.meta.When

class StopWatchService : Service() {
    private var isSWFirstRunning = false
    private var isSWRunning = false

    companion object {
        const val ACTION_RESET_STOPWATCH =
            "jp.gr.java_conf.SenseMusicClock.Clock.StopWatchService.ACTION_RESET_TIMER"
        const val ACTION_STOP_STOPWATCH =
            "jp.gr.java_conf.SenseMusicClock.Clock.TimerService.ACTION_STOP_TIMER"
        const val ACTION_START_STOPWATCH =
            "jp.gr.java_conf.SenseMusicClock.Clock.TimerService.ACTION_START_TIMER"
        const val ACTION_AUTO_STOPWATCH =
            "jp.gr.java_conf.SenseMusicClock.Clock.TimerService.ACTION_AUTO_TIMER"

        const val CHANNEL_ID = "SW_SMC"

        const val NOTIFICATION_ID = 3274
    }

    private val binder = LocalBinder()

    // Activityが接続（Bind）してきたときに呼ばれる
    override fun onBind(intent: Intent): IBinder {
        Log.d("StopWatchService", "onBind called intent=$intent")
        return binder
    }


    // 2. 自分自身を返すための中継クラス
    inner class LocalBinder : Binder() {
        fun getService(): StopWatchService = this@StopWatchService
    }


    private var stoppedTime: Long = 0L
    private var firstStartedTime : Long = 0L

    private var baseTime: Long = 0L


    override fun onCreate() {
        super.onCreate()
        Log.d("StopWatchService", "onCreate called")
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "SMC Stopwatch Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "StopWatch notifications"
                // leave default sound/vibration so alarm notification can be prominent
            }
        )

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {

            ACTION_START_STOPWATCH -> {
                Log.d("StopWatchService", "onStartCommand received: ACTION_START_STOPWATCH")
                startStopWatch()
            }

            ACTION_STOP_STOPWATCH -> {
                Log.d("StopWatchService", "onStartCommand received: ACTION_STOP_STOPWATCH")
                stopStopWatch()
            }

            ACTION_RESET_STOPWATCH -> {
                Log.d("StopWatchService", "onStartCommand received: ACTION_RESET_STOPWATCH")
                resetStopWatch()
            }

            ACTION_AUTO_STOPWATCH -> {
                Log.d("StopWatchService", "onStartCommand received: ACTION_AUTO_STOPWATCH")
                if (isSWRunning) {
                    stopStopWatch()
                } else {
                    startStopWatch()
                }
            }

            else -> {
                Log.d(
                    "StopWatchService",
                    "onStartCommand: unknown or null action: ${intent?.action}"
                )
            }
        }
        return START_NOT_STICKY
    }

    private fun showNotification_Start() {
        Log.d(
            "StopWatchService",
            "showNotification_Start called baseTime=$baseTime isSWFirstRunning=$isSWFirstRunning"
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // アイコンは必須
            .setContentTitle("ストップウォッチ起動中")
            .setOngoing(true)                // ユーザーが消せないようにする
            .setOnlyAlertOnce(true)          // 更新時に音を鳴らさない
            .setUsesChronometer(true)        // ★OSが勝手に時間を進めてくれる
            .setWhen(baseTime)                // ★基準時間をセット
            // カウントダウンなら true、ストップウォッチなら false
            .setChronometerCountDown(false)

        // サービスをフォアグラウンドに昇格（これでアプリを閉じても死なない）
        startForeground(NOTIFICATION_ID, builder.build())
    }

    private fun showNotification_Stop() {
        Log.d("StopWatchService", "showNotification_Stop called")
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // アイコンは必須
            .setContentTitle("ストップウォッチ停止中")
            .setOngoing(false)                // ユーザーが消せるようにする
            .setOnlyAlertOnce(true)          // 更新時に音を鳴らさない

        startForeground(NOTIFICATION_ID, builder.build())
    }

    private var stopWatch: Stopwatch = Stopwatch.createUnstarted()

    fun getElapsedTime_Millis(): Long {

        val now = System.currentTimeMillis() // ミリ秒

        return if(!isSWRunning){
            stoppedTime  - baseTime

        }else if (baseTime > 0L) {
            now - baseTime
        } else {
            0L
        }


    }

    fun startStopWatch() {
        Log.d("StopWatchService", "startStopWatch called isSWFirstRunning=$isSWFirstRunning")
        if (!isSWRunning) {
            stopWatch.start()
            isSWRunning = true
            if (!isSWFirstRunning) {
                baseTime = System.currentTimeMillis()
                firstStartedTime = System.currentTimeMillis()

            } else {
                baseTime = System.currentTimeMillis() -( stoppedTime - baseTime)
            }
            showNotification_Start()
            isSWFirstRunning = true
        }
    }

    fun stopStopWatch() {
        Log.d("StopWatchService", "stopStopWatch called")
        if (isSWRunning) {

            stopWatch.stop()
            isSWRunning = false
            stoppedTime = System.currentTimeMillis()
            showNotification_Stop()
        }

    }

    fun resetStopWatch() {
        Log.d("StopWatchService", "resetStopWatch called")
        if (isSWRunning) {

            stopWatch.stop()
            isSWRunning = false
        }
        stopWatch.reset()
        isSWFirstRunning = false
        isSWRunning = false
        baseTime = 0
        firstStartedTime = 0L
        stoppedTime = 0

        stopForeground(STOP_FOREGROUND_REMOVE)


    }


}