package jp.gr.java_conf.SenseMusicClock.Clock

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.EditText
import android.widget.LinearLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import jp.gr.java_conf.SenseMusicClock.ui.MainActivity
import jp.gr.java_conf.SenseMusicClock.PrefsManager
import jp.gr.java_conf.SenseMusicClock.R
import jp.gr.java_conf.SenseMusicClock.databinding.ActivityMainBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.Locale.getDefault

/**
 * ClockUiController: タイマー／アラーム／ストップウォッチに関する UI ロジックを MainActivity から分離するコントローラ。
 * - Activity と viewBinding を受け取り、ボタンのイベント・SharedPreferences・時計関連の各サービスとの接続 を管理する。
 * - 音楽再生や RecyclerView などのロジックには触れない（独立）。
 */
class ClockUiController(
    private val activity: MainActivity,
    private val binding: ActivityMainBinding
) {


    private var stopWatchService: StopWatchService? = null
    private var timerService: TimerService? = null


    fun requestSetAlarm(hour: Int, minute: Int) {
        Log.d("ClockUiController", "Requesting set alarm for $hour:$minute")
        val intent: Intent = Intent(activity, AlarmService::class.java).apply {
            action = AlarmService.ACTION_SET_ALARM
            putExtra(AlarmService.ALARM_HOUR_KEY, hour)
            putExtra(AlarmService.ALARM_MINUTE_KEY, minute)
        }

        activity.startForegroundService(intent)
        Log.d(
            "ClockUiController",
            "startForegroundService sent for AlarmService with $hour:$minute"
        )


    }

    suspend fun requestStopAlarm() {
        Log.d("ClockUiController", "requestStopAlarm called")
        val intent = Intent(activity, AlarmService::class.java).apply {
            action = AlarmService.ACTION_STOP_ALARM
        }
        activity.startService(intent)
        PrefsManager.clearSetAlarmTime(activity)
        Log.d("ClockUiController", "Requested stop alarm (startService) and cleared prefs")
    }

    fun requestResetStopWatch() {
        Log.d("ClockUiController", "requestresetStopWatch called")
        val intent = Intent(activity, StopWatchService::class.java).apply {
            action = StopWatchService.ACTION_RESET_STOPWATCH
        }
        activity.startService(intent)
        Log.d("ClockUiController", "startService sent to StopWatchService to reset")
        unbindService_StopWatch()
    }

    private suspend fun requestCancelAlarm() {
        Log.d("ClockUiController", "requestCancelAlarm called")

        val intent = Intent(activity, AlarmService::class.java).apply {
            action = AlarmService.ACTION_CANCEL_ALARM
        }
        activity.startService(intent)
        PrefsManager.clearSetAlarmTime(activity)
        Log.d("ClockUiController", "Requested cancel alarm (startService) and cleared prefs")
    }


    private fun requestStartTimer(duration: Long) {
        Log.d("ClockUiController", "requestStartTimer called with duration=$duration")


        val intent = Intent(activity, TimerService::class.java).apply {
            action = TimerService.ACTION_START_TIMER
            putExtra(TimerService.EXTRA_TIMER_DURATION, duration)
        }
        activity.startForegroundService(intent)
        Log.d(
            "ClockUiController",
            "startForegroundService sent for TimerService duration=$duration"
        )
        BindService_Timer()

    }

    private fun requestStopTimer() {
        Log.d("ClockUiController", "requestStopTimer called")
        val intent = Intent(activity, TimerService::class.java).apply {
            action = TimerService.ACTION_STOP_TIMER
        }
        activity.startService(intent)
        Log.d("ClockUiController", "startService sent to TimerService to stop")
    }


    private val timerConnection = object : android.content.ServiceConnection {
        private var timerJob : Job? = null
        override fun onServiceConnected(
            name: android.content.ComponentName?,
            service: android.os.IBinder?
        ) {
            Log.d(
                "ClockUiController",
                "timerConnection.onServiceConnected: name=$name service=$service"
            )
            // Not used since we're using BroadcastReceiver for updates
            val binder = service as? TimerService.LocalBinder
            timerService = binder?.getService()
            timerJob = timerService?.let {


                activity.lifecycleScope.launch {
                    activity.repeatOnLifecycle(Lifecycle.State.STARTED) {

                        Log.d("ClockUiController", "Collecting remainingTime from TimerService")

                        it.remainingTime.collect { value ->


                            if (binding.TimerButton.text != "Timer" && value <= 0L && it.isTimerFinished ) {
                                binding.TimerButton.text = "完了！"
                            } else {
                                binding.TimerButton.text = formatMillisToTime(value)
                            }
                        }
                    }
                }

            }


        }


        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            Log.d("ClockUiController", "timerConnection.onServiceDisconnected: name=$name")

            timerJob?.cancel()
            timerJob = null
            binding.TimerButton.text = "Timer"
            timerService = null
            // Not used
        }
    }

    private val stopWatchConnection = object : android.content.ServiceConnection {
        override fun onServiceConnected(
            name: android.content.ComponentName?,
            service: android.os.IBinder?
        ) {
            Log.d(
                "ClockUiController",
                "stopWatchConnection.onServiceConnected: name=$name service=$service"
            )

            val binder = service as? StopWatchService.LocalBinder
            stopWatchService = binder?.getService()
            handler.postDelayed(timer_textOutput, 0L)
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            Log.d("ClockUiController", "stopWatchConnection.onServiceDisconnected: name=$name")

            binding.stopWatchBtn.text = "StopWatch"
            // Not used
            handler.removeCallbacks(timer_textOutput)
        }
    }

    fun onDestroy() {
        unbindService_Timer()
        unbindService_StopWatch()
        handler.removeCallbacks(timer_textOutput)
    }

    val handler = Handler(Looper.getMainLooper())
    val timer_textOutput = object : Runnable {
        override fun run() {
            // timeに0.1秒を追加

            stopWatchService?.let {
                val millis = it.getElapsedTime_Millis()
                val text = formatStopwatchDisplayWithCentis(millis)
                binding.stopWatchBtn.text = text


            }

            //　なんか、0.01秒ごとに更新したいけど、それだと負荷が高すぎるので、0.01秒から0.07秒の間でランダムに遅延させることで少数第二位の表示を擬似的に実現する
            val delayMillis = (10..70).random().toLong()

            handler.postDelayed(this, delayMillis)
        }
    }


    fun startService_StopWatch() {
        Log.d("ClockUiController", "startService_StopWatch called")
        val intent = Intent(activity, StopWatchService::class.java).apply {
            action = StopWatchService.ACTION_AUTO_STOPWATCH
        }
        activity.startService(intent)
        Log.d("ClockUiController", "startService sent for StopWatchService")
    }


    fun BindService_StopWatch() {
        Log.d("ClockUiController", "BindService_StopWatch called")
        val intent = Intent(activity, StopWatchService::class.java)
        activity.bindService(intent, stopWatchConnection, 0)
        Log.d("ClockUiController", "bindService requested for StopWatchService")
    }

    fun unbindService_StopWatch() {
        Log.d("ClockUiController", "unbindService_StopWatch called")
        try {
            activity.unbindService(stopWatchConnection)
            Log.d("ClockUiController", "unbindService StopWatch succeeded")
        } catch (e: Exception) {
            Log.w("ClockUiController", "unbindService StopWatch failed", e)
        }
    }

    fun unbindService_Timer() {
        Log.d("ClockUiController", "unbindService_Timer called")
        try {
            activity.unbindService(timerConnection)
            Log.d("ClockUiController", "unbindService Timer succeeded")
        } catch (e: Exception) {
            Log.w("ClockUiController", "unbindService Timer failed", e)
        }
    }


    fun BindService_Timer() {
        Log.d("ClockUiController", "BindService_Timer called")
        val intent = Intent(activity, TimerService::class.java)
        activity.bindService(intent, timerConnection, 0)
        Log.d("ClockUiController", "bindService requested for TimerService")
    }

    fun startAndBindService_StopWatch() {
        startService_StopWatch()
        BindService_StopWatch()
    }


    fun init() {
        // restore labels


        activity.bindService(
            Intent(activity, TimerService::class.java),
            timerConnection,
            Context.BIND_AUTO_CREATE
        )
        activity.lifecycleScope.launch {
            val alarmTime = PrefsManager.getSetAlarmTime(activity)
            binding.alarmButton.text = when {
                alarmTime.isBlank()|| alarmTime.isBlank() -> activity.getString(R.string.alarm_button_label)
                else -> alarmTime
            }
        }
        // If pref doesn't contain elapsed value, display zero to avoid spurious numbers on first run

        binding.TimerButton.text = activity.getString(R.string.timer_button_label)
        // show alarm button using scheduled epoch if available, otherwise fallback to stored hour/minute

        // Activity shows centiseconds; use centisecond formatter for initial label
        // remove alarm button setup
        binding.stopWatchBtn.text = "ストップウォッチ" // initial label for stop watch

        14
        // Timer
        binding.TimerButton.setOnClickListener {
            // Build a horizontal LinearLayout containing three numeric EditTexts for H/M/S
            val container = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(32, 16, 32, 16)
            }
            val lp = LinearLayout.LayoutParams(
                0,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
            val etH = EditText(activity).apply {
                hint = "時"
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                layoutParams = lp
                setText("")
            }
            val etM = EditText(activity).apply {
                hint = "分"
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                layoutParams = lp
                setText("")
            }
            val etS = EditText(activity).apply {
                hint = "秒"
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                layoutParams = lp
                setText("")
            }
            container.addView(etH)
            container.addView(etM)
            container.addView(etS)

            AlertDialog.Builder(activity)
                .setTitle("タイマーを設定（時 / 分 / 秒）")
                .setView(container)
                .setPositiveButton("開始") { _, _ ->
                    val h = etH.text.toString().toLongOrNull() ?: 0L
                    val m = etM.text.toString().toLongOrNull() ?: 0L
                    val s = etS.text.toString().toLongOrNull() ?: 0L
                    val totalSeconds = h * 3600L + m * 60L + s
                    if (totalSeconds > 0L) {
                        val millis = totalSeconds * 1000L

                        requestStartTimer(millis)
                        binding.TimerButton.text = formatMillisToTime(millis)
                    }
                }
                .setNegativeButton("キャンセル", null)
                .show()
        }
        binding.TimerButton.setOnLongClickListener {

            binding.TimerButton.text = activity.getString(R.string.timer_button_label)
            requestStopTimer()
            unbindService_Timer()
            true
        }

        binding.alarmButton.setOnClickListener {
            val now = LocalDateTime.now()
            val tpd = android.app.TimePickerDialog(activity, { _, hourOfDay, minute ->


                Log.d("ClockUiController", "Selected time: $hourOfDay:$minute")
                val minuteText = if (minute < 10) "0$minute" else "$minute"
                val hourText = if (hourOfDay < 10) "0$hourOfDay" else "$hourOfDay"
                val text = "$hourText:$minuteText"
                binding.alarmButton.text = text
                activity.lifecycleScope.launch {

                    PrefsManager.setSetAlarmTime(activity, hourOfDay, minute)

                    requestSetAlarm(hourOfDay, minute)
                }
            }, now.hour, now.minute, true)
            tpd.show()
        }
        binding.alarmButton.setOnLongClickListener {

            binding.alarmButton.text = activity.getString(R.string.alarm_button_label)
            activity.lifecycleScope.launch {
                requestStopAlarm()

                requestCancelAlarm()
            }

            true
        }


        // Stopwatch
        binding.stopWatchBtn.setOnClickListener {
            startAndBindService_StopWatch()

        }
        binding.stopWatchBtn.setOnLongClickListener {

            requestResetStopWatch()
            true
        }

    }

    fun destroy() {
        try {

        } catch (e: Exception) {
            Log.w("ClockUiController", "unregisterReceiver failed", e)
        }
    }


    private fun formatMillisToTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val seconds = (totalSeconds % 60).toInt()
        val minutes = ((totalSeconds / 60) % 60).toInt()
        val hours = (totalSeconds / 3600).toInt()
        return if (hours > 0) String.format(
            getDefault(),
            "%02d:%02d:%02d",
            hours,
            minutes,
            seconds
        )
        else String.format(getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun formatStopwatchDisplayWithCentis(millis: Long): String {
        val cs = (millis % 1000L) / 10L
        val seconds = (millis / 1000L) % 60L
        val minutes = (millis / 60000L) % 60L
        val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(millis)
        return if (hours > 0) String.format(
            getDefault(),
            "%02d:%02d:%02d.%02d",
            hours,
            minutes,
            seconds,
            cs
        )
        else String.format(getDefault(), "%02d:%02d.%02d", minutes, seconds, cs)
    }

    private fun formatStopwatchDisplay(millis: Long): String {
        val seconds = (millis / 1000L) % 60L
        val minutes = (millis / 60000L) % 60L
        val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(millis)
        return if (hours > 0) String.format(
            getDefault(),
            "%02d:%02d:%02d",
            hours,
            minutes,
            seconds
        )
        else String.format(getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun formatAlarmLabelFromEpoch(epochMillis: Long): String {
        if (epochMillis <= 0L) return activity.getString(R.string.alarm_button_label)
        val z = java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault())
        val hhmm = String.format(getDefault(), "%02d:%02d", z.hour, z.minute)
        // show marker if scheduled day is after today
        val today = java.time.LocalDate.now(java.time.ZoneId.systemDefault())
        val scheduledDate = z.toLocalDate()
        return if (scheduledDate.isAfter(today)) "$hhmm (翌日)" else hhmm
    }
}
