package jp.gr.java_conf.SenseMusicClock

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import java.util.Locale

// Small helpers used across services/activities to reduce duplicated boilerplate.

fun Context.getNotificationManagerCompat(): NotificationManager? =
    getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

fun pendingIntentFlags(minSdkForImmutable: Int = Build.VERSION_CODES.S): Int {
    return if (Build.VERSION.SDK_INT >= minSdkForImmutable)
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    else
        PendingIntent.FLAG_UPDATE_CURRENT
}

fun Context.playDefaultAlarmRingtoneSafe(): Ringtone? {
    return try {
        val alarmUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val r = RingtoneManager.getRingtone(this, alarmUri)
        r.play()
        r
    } catch (e: Exception) {
        null
    }
}

fun Context.vibrateOnceSafe(durationMs: Long = 1000L) {
    try {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
    } catch (_: Exception) {
        // ignore
    }
}

fun Context.dpToPx(dp: Int): Int {
    return (dp * resources.displayMetrics.density + 0.5f).toInt()
}

fun pendingServiceIntent(context: Context, requestCode: Int, intent: Intent, minSdkForImmutable: Int = Build.VERSION_CODES.S): PendingIntent {
    return PendingIntent.getService(context, requestCode, intent, pendingIntentFlags(minSdkForImmutable))
}

fun pendingActivityIntent(context: Context, requestCode: Int, intent: Intent, minSdkForImmutable: Int = Build.VERSION_CODES.S): PendingIntent {
    return PendingIntent.getActivity(context, requestCode, intent, pendingIntentFlags(minSdkForImmutable))
}

fun formatMillisToTime(millis: Long): String {
    val seconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(millis)
    return if (hours > 0) String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
