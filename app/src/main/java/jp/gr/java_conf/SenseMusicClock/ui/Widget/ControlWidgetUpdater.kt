package jp.gr.java_conf.SenseMusicClock.ui.Widget

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import coil.ImageLoader
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.request.SuccessResult
import jp.gr.java_conf.SenseMusicClock.BackgroundResolver
import jp.gr.java_conf.SenseMusicClock.PrefsManager
import jp.gr.java_conf.SenseMusicClock.R

object ControlWidgetUpdater {

    fun isTall(height: Float, width: Float): Boolean {
        return height > width * 0.8
    }


    suspend fun resolveArtworkBitmap(context: Context, artworkUri: Uri?): Bitmap? {
        if (artworkUri == null || artworkUri == "".toUri()) return null

        return runCatching {
            val request = ImageRequest.Builder(context)
                .data(artworkUri)
                .allowHardware(false)
                .size(512, 512)
                .build()

            val result = context.imageLoader.execute(request)

            val drawable = (result as? SuccessResult)?.drawable ?: return null

            drawable.toBitmap()
        }.onFailure {
            Log.w("ControlWidget", "Failed to decode widget artwork: $artworkUri", it)
        }.getOrNull()

    }


    suspend fun resolveBackground(context: Context, isTall: Boolean): ImageResult? {
        val orientation =
            if (isTall) BackgroundResolver.ORIENTATION_OBLONG else BackgroundResolver.ORIENTATION_LAND
        val isBackgroundEnabled = PrefsManager.getWidgetBackground(context)
        val result = if (isBackgroundEnabled) {
            val loader = ImageLoader(context)
            val source = BackgroundResolver.loadBackgroundSource(
                context,
                orientation
            )
            val useSource = when (source) {
                is BackgroundResolver.ImageSource.FilePath -> source.file
                is BackgroundResolver.ImageSource.Res -> source.id
            }

            val request = ImageRequest.Builder(context)
                .allowHardware(false)
                .crossfade(false)
                .data(useSource)
                .size(800, 300)
                .placeholder(R.drawable.gradient2)
                .error(R.drawable.gradient2)
                .fallback(R.drawable.gradient2)
                .build()
            loader.execute(request)
        } else {
            null
        }
        return result

    }

    suspend fun updateAllWidgets(context: Context, newState: WidgetState) {
        WidgetStateManager.updateWidgetState(context, newState)
        ControlWidget().updateAll(context)
    }

}
