package jp.gr.java_conf.SenseMusicClock

import MAX_SCROLL_DISTANCE_FOR_ANIMATION
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
import android.util.DisplayMetrics
import android.util.Log
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.delay
import org.checkerframework.checker.units.qual.Speed
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

// 滑らかなスクロール（アニメーション）でアイテムを表示する

fun RecyclerView.smoothScrollToPositionCentered(position: Int, speedMsPerInch: Float = 150f) {
    if (position < 0 || adapter == null || position >= adapter!!.itemCount) return

    val lm = layoutManager ?: return
    val safeSpeed = speedMsPerInch.coerceAtLeast(10f)

    val scroller = object : LinearSmoothScroller(context) {
        override fun calculateDtToFit(viewStart: Int, viewEnd: Int, boxStart: Int, boxEnd: Int, snapPreference: Int): Int {
            // ビューのサイズとRecyclerView自体のサイズから、中央に配置するためのオフセットを計算
            try {
                val viewSize = viewEnd - viewStart
                val boxSize = if (lm.canScrollHorizontally()) width else height
                val boxStartCenter = (boxSize - viewSize) / 2
                // move viewStart to boxStartCenter
                return boxStartCenter - viewStart
            } catch (e: Exception) {
                Log.e("RecyclerView","smoothScrollToCenter error: ${e.localizedMessage}")

                return super.calculateDtToFit(viewStart, viewEnd, boxStart, boxEnd, snapPreference)
            }
        }

        override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
            // 指定したスピード（インチあたりのミリ秒）をピクセルあたりに変換
            return safeSpeed / displayMetrics.densityDpi
        }
    }


    scroller.targetPosition = position
    post {
        try {
            lm.startSmoothScroll(scroller)
        } catch (e: Exception) {
            // 失敗時のフォールバック
            smoothScrollToPosition(position)
        }
    }
}
fun RecyclerView.shouldSkipAnimation(position: Int,maxScrollDistanceForAnimation : Int = 30): Boolean{
    try {

        val currentPos = (layoutManager as? androidx.recyclerview.widget.LinearLayoutManager)?.findFirstVisibleItemPosition() ?: -1
        if (currentPos == -1){
            Log.e("RecyclerView","shouldSkipAnimation: findFirstVisibleItemPosition() is null")
            return true
        }

        val distance = kotlin.math.abs(position - currentPos)

        return distance > maxScrollDistanceForAnimation

    }catch (e: java.lang.Exception){
        Log.e("RecyclerView","shouldSkipAnimation error: ${e.localizedMessage}")
        return true
    }

}

suspend fun RecyclerView.smoothScrollToPositionWithSkipAnimationCheck(position: Int,speedMsPerInch: Float = 150f,maxScrollDistanceForAnimation : Int = MAX_SCROLL_DISTANCE_FOR_ANIMATION) {
    if (shouldSkipAnimation(position,maxScrollDistanceForAnimation)){
        // アニメーションをスキップして即座に移動
        stepScrollToItem(position,speedMsPerInch)
    }else {
        // 滑らかなスクロールで移動
        smoothScrollToPositionCentered(position, speedMsPerInch)
    }
}

fun RecyclerView.scrollToPositionCentered(position: Int){

    // 1. 基本的なバリデーション（範囲外なら何もしない）
    val adapterItemCount = adapter?.itemCount ?: 0
    if (position < 0 || position >= adapterItemCount) return

    // 2. LinearLayoutManagerにキャスト（as? で安全に）
    val lm = layoutManager as? LinearLayoutManager ?: return

    // 3. 中央に配置するためのオフセット計算
    // RecyclerViewの高さ（または幅）の半分から、アイテムの想定サイズの半分を引く
    // アイテムの正確なサイズが不明な場合は、単に「高さの半分」を指定すればだいたい中央に来る
    val offset = if (lm.canScrollHorizontally()) {
        width / 2
    } else {
        height / 2
    }

    // 4. 実行（これだけで一瞬で飛ぶ）
    lm.scrollToPositionWithOffset(position, offset)

}
suspend fun RecyclerView.stepScrollToItem(targetPos: Int,speed: Float = 5f ) {
    val currentPos = (layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition() ?: return
    val distance = targetPos - currentPos

    // 1. ちょっとだけスクロール（助走）
    // 現在地からターゲット方向に2〜3個分だけアニメーション
    val takeOffPos = currentPos + (if (distance > 0) 10 else -10)
    smoothScrollToPositionCentered(takeOffPos,speed) // 速めで！

    // スクロールが落ち着くまで少し待機（ディレイ）
    delay(200)

    // 2. 近くまでワープ
    // ターゲットの2個手前まで一気に飛ぶ
    val landingPos = targetPos - (if (distance > 0) 10 else -10)
    scrollToPositionCentered(landingPos)

    // 3. 最後にちょっとスクロールして着地
    // 最後の仕上げにヌルッと中央へ

    smoothScrollToPositionCentered(targetPos, speed) // 最後は丁寧に
}
fun calculateScrollSpeed(from : Int, to :Int): Float {

    val diff = kotlin.math.abs(from - to)
    val rawSpeed = (150 / (1 + diff)).toFloat()
    val speed = rawSpeed.coerceIn(1f, 300f)
    return speed
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
