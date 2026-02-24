package jp.gr.java_conf.SenseMusicClock.Clock

import android.content.BroadcastReceiver
import android.content.Intent
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: android.content.Context?, intent: Intent?) {
        // ここでアラームが発火したときの処理を行う
        // 例えば、通知を表示するなどの処理をここに書く
        when (intent?.action) {
            AlarmService.ACTION_ALARM_START -> {
                // アラームが開始されたときの処理
                // 例: 通知を表示するなど
                val intent = Intent(context, AlarmService::class.java).apply {
                    action = AlarmService.ACTION_ALARM_START
                }
                context?.let {
                    it.startForegroundService(intent)
                    Log.d("AlarmReceiver", "Sent intent to start alarm service")

                }
                if (context == null) {
                    Log.e("AlarmReceiver", "Context is null in onReceive")
                }
            }

            AlarmService.ACTION_STOP_ALARM -> {

                val intent = Intent(context, AlarmService::class.java).apply {
                    action = AlarmService.ACTION_STOP_ALARM
                }
                context?.let {
                    it.startForegroundService(intent)

                    Log.d("AlarmReceiver", "Sent intent to stop alarm service")
                }
                if (context == null) {
                    Log.e("AlarmReceiver", "Context is null in onReceive")
                }
            }

        }
    }

}