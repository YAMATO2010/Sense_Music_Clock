package jp.gr.java_conf.SenseMusicClock.Clock

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import android.widget.EditText
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

import jp.gr.java_conf.SenseMusicClock.MainActivity
import jp.gr.java_conf.SenseMusicClock.PrefsManager
import jp.gr.java_conf.SenseMusicClock.R
import jp.gr.java_conf.SenseMusicClock.databinding.ActivityMainBinding
import java.time.Duration
import java.time.LocalDateTime

/**
 * ClockUiController: タイマー／アラーム／ストップウォッチに関する UI ロジックを MainActivity から分離するコントローラ。
 * - Activity と viewBinding を受け取り、ボタンのイベント・SharedPreferences・WorkManager・BroadcastReceiver を管理する。
 * - 音楽再生や RecyclerView などのロジックには触れない（独立）。
 * ClockUiController: タイマー／ストップウォッチに関する UI ロジックを MainActivity から分離するコントローラ。
 * - Activity と viewBinding を受け取り、ボタンのイベント・SharedPreferences・BroadcastReceiver を管理する。
 * - アラームは外部で扱うため、ここからは削除。
 */
class ClockUiController(
    private val activity: MainActivity,
    private val binding: ActivityMainBinding
) {

    private val prefs: SharedPreferences by lazy {
        PrefsManager.getSharedPreferences(activity)
    }

    fun requestSetAlarm(hour: Int, minute: Int) {
        Log.d("ClockUiController", "Requesting set alarm for $hour:$minute")
        val intent : Intent = Intent(activity, AlarmService::class.java).apply {
            action = AlarmService.ACTION_SET_ALARM
            putExtra(AlarmService.ALARM_HOUR_KEY, hour)
            putExtra(AlarmService.ALARM_MINUTE_KEY, minute)
        }

        activity.startForegroundService(intent)


    }

    fun requestStopAlarm() {
        val intent = Intent(activity, AlarmService::class.java).apply {
            action = AlarmService.ACTION_STOP_ALARM
        }
        activity.startService(intent)
        PrefsManager.clearSetAlarmTime(activity)
        Log.d("ClockUiController", "Requested stop alarm")
    }
    private fun requestCancelAlarm(){

        val intent = Intent(activity, AlarmService::class.java).apply {
            action = AlarmService.ACTION_CANCEL_ALARM
        }
        activity.startService(intent)
        PrefsManager.clearSetAlarmTime(activity)
        Log.d("ClockUiController", "Requested cancel alarm")
    }

    private var receiver: android.content.BroadcastReceiver? = null

    fun init() {
        // restore labels
        val timerMillis = prefs.getLong("pref_timer_millis", 0L)

        val alarmTime = PrefsManager.getSetAlarmTime(activity)
        // If pref doesn't contain elapsed value, display zero to avoid spurious numbers on first run
        val swElapsed = if (prefs.contains("pref_sw_elapsed")) prefs.getLong("pref_sw_elapsed", 0L) else 0L

        binding.TimerButton.text =
            if (timerMillis > 0L) formatMillisToTime(timerMillis) else activity.getString(R.string.timer_button_label)
        // show alarm button using scheduled epoch if available, otherwise fallback to stored hour/minute
        binding.alarmButton.text = when {
            alarmTime.isNullOrEmpty() && alarmTime.isNullOrBlank() -> activity.getString(R.string.alarm_button_label)
            else -> alarmTime
        }
        // Activity shows centiseconds; use centisecond formatter for initial label
        // remove alarm button setup
        binding.stopWatchBtn.text = formatStopwatchDisplayWithCentis(swElapsed)

        14
        // Timer
        binding.TimerButton.setOnClickListener {
            // Build a horizontal LinearLayout containing three numeric EditTexts for H/M/S
            val container = android.widget.LinearLayout(activity).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                setPadding(32, 16, 32, 16)
            }
            val lp = android.widget.LinearLayout.LayoutParams(
                0,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
            val etH = android.widget.EditText(activity).apply {
                hint = "時"
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                layoutParams = lp
                setText("")
            }
            val etM = android.widget.EditText(activity).apply {
                hint = "分"
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                layoutParams = lp
                setText("")
            }
            val etS = android.widget.EditText(activity).apply {
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
                        prefs.edit().putLong("pref_timer_millis", millis).apply()
                        binding.TimerButton.text = formatMillisToTime(millis)
                        val intent = Intent(activity, ClockService::class.java).apply {
                            action = ClockService.ACTION_START_TIMER
                            putExtra(ClockService.EXTRA_DURATION, millis)
                        }
                        ContextCompat.startForegroundService(activity, intent)
                    }
                }
                .setNegativeButton("キャンセル", null)
                .show()
        }
        binding.TimerButton.setOnLongClickListener {
            prefs.edit().remove("pref_timer_millis").apply()
            binding.TimerButton.text = activity.getString(R.string.timer_button_label)
            val intent = Intent(activity, ClockService::class.java).apply {
                action = ClockService.ACTION_STOP_TIMER
            }
            activity.startService(intent)
            true
        }

        // Alarm TODO
        binding.alarmButton.setOnClickListener {
            val now = LocalDateTime.now()
            val tpd = android.app.TimePickerDialog(activity, { _, hourOfDay, minute ->
                // compute scheduled epoch (today or next day)


                Log.d("ClockUiController", "Selected time: $hourOfDay:$minute")
                // persist hour/minute and scheduled epoch
                // update button label with possible next-day marker
                val text = "$hourOfDay:$minute"
                binding.alarmButton.text = text
                PrefsManager.setSetAlarmTime(activity,hourOfDay, minute)
                // schedule with WorkManager
                requestSetAlarm(hourOfDay, minute)
            }, now.hour, now.minute, true)
            tpd.show()
        }
        binding.alarmButton.setOnLongClickListener {

            binding.alarmButton.text = activity.getString(R.string.alarm_button_label)
            requestStopAlarm()
            requestCancelAlarm()

            true
        }



        // Stopwatch
        binding.stopWatchBtn.setOnClickListener {
            val running = prefs.getBoolean("pref_sw_running", false)
            val intent = Intent(activity, ClockService::class.java)
            if (running) {
                intent.action = ClockService.ACTION_PAUSE_STOPWATCH
                prefs.edit().putBoolean("pref_sw_running", false).apply()
                activity.startService(intent)
            } else {
                intent.action = ClockService.ACTION_START_STOPWATCH
                prefs.edit().putBoolean("pref_sw_running", true).apply()
                ContextCompat.startForegroundService(activity, intent)
            }
        }
        binding.stopWatchBtn.setOnLongClickListener {
            val intent = Intent(activity, ClockService::class.java).apply {
                action = ClockService.ACTION_RESET_STOPWATCH
            }
            activity.startService(intent)
            binding.stopWatchBtn.text = formatStopwatchDisplay(0L)
            prefs.edit().putLong("pref_sw_elapsed", 0L).putBoolean("pref_sw_running", false).apply()
            true
        }

        // BroadcastReceiver
        val filter = IntentFilter().apply {
            addAction(ClockService.BROADCAST_TICK)
            addAction(ClockService.BROADCAST_FINISHED)
            addAction(ClockService.BROADCAST_STOPWATCH_TICK)
        }
        receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    ClockService.BROADCAST_TICK -> {
                        val remaining = intent.getLongExtra(ClockService.EXTRA_REMAINING, 0L)
                        binding.TimerButton.text = formatMillisToTime(remaining)
                        prefs.edit().putLong("pref_timer_millis", remaining).apply()
                    }

                    ClockService.BROADCAST_FINISHED -> {
                        binding.TimerButton.text = activity.getString(R.string.timer_done_label)
                        prefs.edit().remove("pref_timer_millis").apply()
                    }

                    ClockService.BROADCAST_STOPWATCH_TICK -> {
                        val elapsed = intent.getLongExtra(ClockService.EXTRA_ELAPSED, 0L)
                        // Activity shows centiseconds, service sends millis; format accordingly
                        binding.stopWatchBtn.text = formatStopwatchDisplayWithCentis(elapsed)
                        prefs.edit().putLong("pref_sw_elapsed", elapsed).apply()
                    }
                }
            }
        }

        activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)

    }

    fun destroy() {
        try {
            receiver?.let { activity.unregisterReceiver(it) }
        } catch (e: Exception) {
            android.util.Log.w("ClockUiController", "unregisterReceiver failed", e)
        }
    }

    private fun scheduleAlarmWithWorkManager(hour: Int, minute: Int) {
        val now = LocalDateTime.now()
        val target = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        var delayMillis = Duration.between(now, target).toMillis()
        if (delayMillis <= 0) delayMillis += Duration.ofDays(1).toMillis()

        val data = Data.Builder().putLong("delayMillis", delayMillis).build()
        val request = OneTimeWorkRequestBuilder<AlarmWorker>()
            .setInitialDelay(delayMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
            .addTag("smc_alarm")
            .setInputData(data)
            .build()
        WorkManager.getInstance(activity).enqueue(request)
    }

    private fun formatMillisToTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val seconds = (totalSeconds % 60).toInt()
        val minutes = ((totalSeconds / 60) % 60).toInt()
        val hours = (totalSeconds / 3600).toInt()
        return if (hours > 0) String.format(
            java.util.Locale.getDefault(),
            "%02d:%02d:%02d",
            hours,
            minutes,
            seconds
        )
        else String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun formatStopwatchDisplayWithCentis(millis: Long): String {
        val cs = (millis % 1000L) / 10L
        val seconds = (millis / 1000L) % 60L
        val minutes = (millis / 60000L) % 60L
        val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(millis)
        return if (hours > 0) String.format(
            java.util.Locale.getDefault(),
            "%02d:%02d:%02d.%02d",
            hours,
            minutes,
            seconds,
            cs
        )
        else String.format(java.util.Locale.getDefault(), "%02d:%02d.%02d", minutes, seconds, cs)
    }

    private fun formatStopwatchDisplay(millis: Long): String {
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

    private fun formatAlarmLabelFromEpoch(epochMillis: Long): String {
        if (epochMillis <= 0L) return activity.getString(R.string.alarm_button_label)
        val z = java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault())
        val hhmm = String.format(java.util.Locale.getDefault(), "%02d:%02d", z.hour, z.minute)
        // show marker if scheduled day is after today
        val today = java.time.LocalDate.now(java.time.ZoneId.systemDefault())
        val scheduledDate = z.toLocalDate()
        return if (scheduledDate.isAfter(today)) "$hhmm (翌日)" else hhmm
    }
}
