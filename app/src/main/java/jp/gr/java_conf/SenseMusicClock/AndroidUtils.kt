package jp.gr.java_conf.SenseMusicClock


import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.OpenableColumns
import android.util.DisplayMetrics
import android.util.Log
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.withFrameNanos
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

// Small helpers used across services/activities to reduce duplicated boilerplate.

fun Context.getNotificationManagerCompat(): NotificationManager? =
    getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

fun pendingIntentFlags(minSdkForImmutable: Int = Build.VERSION_CODES.S): Int {
    return if (Build.VERSION.SDK_INT >= minSdkForImmutable)
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    else
        PendingIntent.FLAG_UPDATE_CURRENT
}

fun convertMsToTimeString(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val minutesStr = if (minutes < 10) "0$minutes" else "$minutes"
    val secondsStr = if (seconds < 10) "0$seconds" else "$seconds"

    return "$minutesStr:$secondsStr"
}

fun Context.saveToInternalStorage(uri: Uri, childPath: String = ""): File {
    val dir = File(filesDir, childPath)
    if (!dir.exists()) dir.mkdirs()


    var fileName = getFileNameFromUri(uri) ?: "image_${System.currentTimeMillis()}"



    if (File(dir, fileName).exists()) {

        // 拡張子とベース名に分割
        val dotIndex = fileName.lastIndexOf('.')
        val baseName = if (dotIndex != -1) fileName.substring(0, dotIndex) else fileName
        val ext = if (dotIndex != -1) fileName.substring(dotIndex) else ""

        // 連番処理
        var uniqueName = fileName
        var i = 1

        while (File(dir, uniqueName).exists()) {
            uniqueName = "${baseName}_($i)$ext"
            i++
        }
        // 最終的 なファイル名
        fileName = uniqueName

    }
    val outFile = File(dir, fileName)

    contentResolver.openInputStream(uri).use { input ->
        outFile.outputStream().use { output ->
            input?.copyTo(output)
        }
    }

    return outFile
}

fun Context.getFileNameFromUri(uri: Uri): String? {
    val cursor = contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0) {
                return it.getString(nameIndex)
            }
        }
    }
    return null
}

fun Context.getAllFile_inInternalStorage(childPath: String): List<File> {

    val dir = File(filesDir, childPath)
    return dir.walk()
        .filter { it.isFile } // ファイルだけを抽出
        .onEach { Log.d("InternalStorageFile", "File: ${it.absolutePath}") } // ログ出し
        .toList()

}

fun Context.vibrateOnceSafe(durationMs: Long = 1000L) {
    try {

        val vibrator = getSystemService(Vibrator::class.java)
        vibrator?.vibrate(
            VibrationEffect.createOneShot(
                durationMs,
                VibrationEffect.DEFAULT_AMPLITUDE
            )
        )
    } catch (e: Exception) {
        Log.w("AndroidUtils", "vibrateOnceSafe failed", e)
    }
}

fun Context.dpToPx(dp: Int): Int {
    return (dp * resources.displayMetrics.density + 0.5f).toInt()
}

private suspend fun LazyListState.awaitLazyListLayout() {
    if (layoutInfo.totalItemsCount == 0) {
        withFrameNanos { }
    }
}

private fun LazyListState.coerceItemPosition(position: Int): Int? {
    val itemCount = layoutInfo.totalItemsCount
    if (itemCount <= 0) return null
    return position.coerceIn(0, itemCount - 1)
}

private fun LazyListState.visibleItemCenterDelta(position: Int): Float? {
    val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == position } ?: return null
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
    val itemCenter = itemInfo.offset + itemInfo.size / 2f
    return itemCenter - viewportCenter
}

private fun LazyListState.centerScrollOffset(position: Int): Int {
    val visibleItems = layoutInfo.visibleItemsInfo
    val itemSize = visibleItems.firstOrNull { it.index == position }?.size
        ?: visibleItems.map { it.size }.takeIf { it.isNotEmpty() }?.average()?.toInt()
        ?: 0
    val viewportSize = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
    return -((viewportSize - itemSize) / 2)
}

suspend fun LazyListState.animateScrollToItemCentered(position: Int) {
    awaitLazyListLayout()
    val safePosition = coerceItemPosition(position) ?: return

    animateScrollToItem(safePosition, centerScrollOffset(safePosition))
    withFrameNanos { }
    visibleItemCenterDelta(safePosition)?.let { delta ->
        if (abs(delta) > 1f) {
            scrollBy(delta)
        }
    }
}

suspend fun LazyListState.scrollToItemCentered(position: Int) {
    awaitLazyListLayout()
    val safePosition = coerceItemPosition(position) ?: return

    scrollToItem(safePosition, centerScrollOffset(safePosition))
    withFrameNanos { }
    visibleItemCenterDelta(safePosition)?.let { delta ->
        if (abs(delta) > 1f) {
            scrollBy(delta)
        }
    }
}

fun LazyListState.shouldSkipAnimation(
    position: Int,
    maxScrollDistanceForAnimation: Int = MAX_SCROLL_DISTANCE_FOR_ANIMATION
): Boolean {
    val itemCount = layoutInfo.totalItemsCount
    if (position < 0 || position >= itemCount) return true

    val distance = abs(position - firstVisibleItemIndex)
    return distance > maxScrollDistanceForAnimation
}

suspend fun LazyListState.animateScrollToItemWithSkipCheck(
    position: Int,
    maxScrollDistanceForAnimation: Int = MAX_SCROLL_DISTANCE_FOR_ANIMATION
) {
    if (shouldSkipAnimation(position, maxScrollDistanceForAnimation)) {
        stepScrollToItem(position)
    } else {
        animateScrollToItemCentered(position)
    }
}

suspend fun LazyListState.stepScrollToItem(targetPos: Int) {
    awaitLazyListLayout()
    val safeTargetPos = coerceItemPosition(targetPos) ?: return
    val currentPos = firstVisibleItemIndex
    val distance = safeTargetPos - currentPos

    if (distance == 0) {
        animateScrollToItemCentered(safeTargetPos)
        return
    }

    val step = if (distance > 0) 10 else -10
    val landingPos = coerceItemPosition(safeTargetPos - step) ?: return
    scrollToItemCentered(landingPos)

    animateScrollToItemCentered(safeTargetPos)

}
// 滑らかなスクロール（アニメーション）でアイテムを表示する

fun RecyclerView.smoothScrollToPositionCentered(position: Int, speedMsPerInch: Float = 150f) {
    if (position < 0 || adapter == null || position >= adapter!!.itemCount) return

    val lm = layoutManager ?: return
    val safeSpeed = speedMsPerInch.coerceAtLeast(10f)

    val scroller = object : LinearSmoothScroller(context) {
        override fun calculateDtToFit(
            viewStart: Int,
            viewEnd: Int,
            boxStart: Int,
            boxEnd: Int,
            snapPreference: Int
        ): Int {
            // ビューのサイズとRecyclerView自体のサイズから、中央に配置するためのオフセットを計算
            try {
                val viewSize = viewEnd - viewStart
                val boxSize = if (lm.canScrollHorizontally()) width else height
                val boxStartCenter = (boxSize - viewSize) / 2
                // move viewStart to boxStartCenter
                return boxStartCenter - viewStart
            } catch (e: Exception) {
                Log.e("RecyclerView", "smoothScrollToCenter error: ${e.localizedMessage}")

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
            Log.w("RecyclerView", "startSmoothScroll failed", e)
            // 失敗時のフォールバック
            smoothScrollToPosition(position)
        }
    }
}

fun RecyclerView.shouldSkipAnimation(
    position: Int,
    maxScrollDistanceForAnimation: Int = 30
): Boolean {
    try {

        val currentPos =
            (layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition() ?: -1
        if (currentPos == -1) {
            Log.e("RecyclerView", "shouldSkipAnimation: findFirstVisibleItemPosition() is null")
            return true
        }

        val distance = abs(position - currentPos)

        return distance > maxScrollDistanceForAnimation

    } catch (e: java.lang.Exception) {
        Log.w("RecyclerView", "shouldSkipAnimation error", e)
        return true
    }

}


suspend fun RecyclerView.smoothScrollToPositionWithSkipAnimationCheck(
    position: Int,
    speedMsPerInch: Float = 150f,
    maxScrollDistanceForAnimation: Int = MAX_SCROLL_DISTANCE_FOR_ANIMATION
) {
    if (shouldSkipAnimation(position, maxScrollDistanceForAnimation)) {
        // アニメーションをスキップして即座に移動
        stepScrollToItem(position, speedMsPerInch)
    } else {
        // 滑らかなスクロールで移動
        smoothScrollToPositionCentered(position, speedMsPerInch)
    }
}

fun RecyclerView.scrollToPositionCentered(position: Int) {

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

suspend fun RecyclerView.stepScrollToItem(targetPos: Int, speed: Float = 5f) {
    val currentPos =
        (layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition() ?: return
    val distance = targetPos - currentPos

    // 1. ちょっとだけスクロール（助走）
    // 現在地からターゲット方向に2〜3個分だけアニメーション
    val takeOffPos = currentPos + (if (distance > 0) 10 else -10)
    smoothScrollToPositionCentered(takeOffPos, speed) // 速めで！

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

fun calculateScrollSpeed(from: Int, to: Int): Float {

    val diff = abs(from - to)
    val rawSpeed = (150 / (1 + diff)).toFloat()
    val speed = rawSpeed.coerceIn(1f, 300f)
    return speed
}

fun pendingServiceIntent(
    context: Context,
    requestCode: Int,
    intent: Intent,
    minSdkForImmutable: Int = Build.VERSION_CODES.S
): PendingIntent {
    return PendingIntent.getService(
        context,
        requestCode,
        intent,
        pendingIntentFlags(minSdkForImmutable)
    )
}

fun pendingActivityIntent(
    context: Context,
    requestCode: Int,
    intent: Intent,
    minSdkForImmutable: Int = Build.VERSION_CODES.S
): PendingIntent {
    return PendingIntent.getActivity(
        context,
        requestCode,
        intent,
        pendingIntentFlags(minSdkForImmutable)
    )
}

fun formatMillisToTime(millis: Long): String {
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    return if (hours > 0) String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
