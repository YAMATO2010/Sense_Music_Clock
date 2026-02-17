package jp.gr.java_conf.SenseMusicClock.Clock

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.widget.EditText
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

import jp.gr.java_conf.SenseMusicClock.MainActivity
import jp.gr.java_conf.SenseMusicClock.R
import jp.gr.java_conf.SenseMusicClock.databinding.ActivityMainBinding
import java.time.Duration
import java.time.LocalDateTime

/**
 * ClockUiController: タイマー／アラーム／ストップウォッチに関する UI ロジックを MainActivity から分離するコントローラ。
 * - Activity と viewBinding を受け取り、ボタンのイベント・SharedPreferences・WorkManager・BroadcastReceiver を管理する。
 * - 音楽再生や RecyclerView などのロジックには触れない（独立）。
 */
class ClockUiController(
    private val activity: MainActivity,
    private val binding: ActivityMainBinding
) {

    private val prefs: SharedPreferences by lazy {
        activity.getSharedPreferences(
            activity.getString(R.string.SHAREDPREFERENCES_NAME),
            Context.MODE_PRIVATE
        )
    }

    private var receiver: android.content.BroadcastReceiver? = null

    fun init() {
        // restore labels
        val timerMillis = prefs.getLong("pref_timer_millis", 0L)
        val alarmHour = prefs.getInt("pref_alarm_hour", -1)
        val alarmMinute = prefs.getInt("pref_alarm_minute", -1)
        // restore scheduled epoch if present (used to show next-day marker)
        val alarmScheduledAt = prefs.getLong("pref_alarm_scheduled_at", -1L)
        // If pref doesn't contain elapsed value, display zero to avoid spurious numbers on first run
        val swElapsed =
            if (prefs.contains("pref_sw_elapsed")) prefs.getLong("pref_sw_elapsed", 0L) else 0L

        binding.TimerButton.text =
            if (timerMillis > 0L) formatMillisToTime(timerMillis) else activity.getString(R.string.timer_button_label)
        // show alarm button using scheduled epoch if available, otherwise fallback to stored hour/minute
        binding.alarmButton.text = when {
            alarmScheduledAt > 0L -> formatAlarmLabelFromEpoch(alarmScheduledAt)
            alarmHour >= 0 -> String.format("%02d:%02d", alarmHour, alarmMinute)
            else -> activity.getString(R.string.alarm_button_label)
        }
        // Activity shows centiseconds; use centisecond formatter for initial label
        binding.stopWatchBtn.text = formatStopwatchDisplayWithCentis(swElapsed)

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

            android.app.AlertDialog.Builder(activity)
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

        // Alarm
        binding.alarmButton.setOnClickListener {
            val now = LocalDateTime.now()
            val tpd = android.app.TimePickerDialog(activity, { _, hourOfDay, minute ->
                // compute scheduled epoch (today or next day)
                val nowDt = LocalDateTime.now()
                var target = nowDt.withHour(hourOfDay).withMinute(minute).withSecond(0).withNano(0)
                var delayMillis = java.time.Duration.between(nowDt, target).toMillis()
                if (delayMillis <= 0) {
                    delayMillis += java.time.Duration.ofDays(1).toMillis()
                    target = target.plusDays(1)
                }
                // persist hour/minute and scheduled epoch
                prefs.edit()
                    .putInt("pref_alarm_hour", hourOfDay)
                    .putInt("pref_alarm_minute", minute)
                    .putLong(
                        "pref_alarm_scheduled_at",
                        target.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    )
                    .apply()
                // update button label with possible next-day marker
                binding.alarmButton.text =
                    formatAlarmLabelFromEpoch(prefs.getLong("pref_alarm_scheduled_at", -1L))
                // schedule with WorkManager
                scheduleAlarmWithWorkManager(hourOfDay, minute)
            }, now.hour, now.minute, true)
            tpd.show()
        }
        binding.alarmButton.setOnLongClickListener {
            prefs.edit().remove("pref_alarm_hour").remove("pref_alarm_minute")
                .remove("pref_alarm_scheduled_at").apply()
            binding.alarmButton.text = activity.getString(R.string.alarm_button_label)
            WorkManager.getInstance(activity).cancelAllWorkByTag("smc_alarm")

            // ここで ClockService に ACTION_STOP_ALARM を投げる
            val stopIntent = Intent(activity, ClockService::class.java).apply {
                action = "jp.gr.java_conf.SenseMusicClock.ACTION_STOP_ALARM"
            }
            activity.startService(stopIntent)

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
