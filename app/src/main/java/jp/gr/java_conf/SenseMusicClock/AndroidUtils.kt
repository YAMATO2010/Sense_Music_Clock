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

