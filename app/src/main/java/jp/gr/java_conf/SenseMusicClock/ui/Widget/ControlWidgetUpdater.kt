package jp.gr.java_conf.SenseMusicClock.ui.Widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.glance.GlanceId
import androidx.glance.appwidget.AppWidgetId
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
import kotlinx.coroutines.flow.Flow

object ControlWidgetUpdater {

    fun isTall(height : Float, width: Float): Boolean {
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
        Log.d("ControlWidget", "[WidgetTrace] resolveBackground isTall=$isTall orientation=$orientation isBackgroundEnabled=$isBackgroundEnabled")
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
        Log.d("ControlWidget", "[WidgetTrace] updateAllWidgets start state=$newState")
        WidgetStateManager.updateWidgetState(context, newState)
        Log.d("ControlWidget", "[WidgetTrace] updateAllWidgets state saved")
        val glanceIds = GlanceAppWidgetManager(context).getGlanceIds(ControlWidget::class.java)
        Log.d(
            "ControlWidget",
            "[WidgetTrace] updateAllWidgets glanceIds count=${glanceIds.size} ids=$glanceIds"
        )
        ControlWidget().updateAll(context)
        Log.d("ControlWidget", "[WidgetTrace] updateAllWidgets updateAll requested")
    }

/*

    internal suspend fun update(
        context: Context,
        appWidgetManager: AppWidgetManager? = null,
        changedAppWidgetId: Int? = null

    ) {

        val WidgetState = WidgetStateManager.getWidgetState(context)
        val title = WidgetState.title
        val artworkUri = WidgetState.artwork
        val playing = WidgetState.playing





        val appWidgetManager = appWidgetManager ?: AppWidgetManager.getInstance(context)
        val appWidgetId = changedAppWidgetId ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID ) {
            Log.d(
                "ControlWidgetUpdater",
                "No specific widget ID provided, checking all widgets for orientation."
            )

            val ids = appWidgetManager.getAppWidgetIds(
                ComponentName(context, ControlWidget::class.java)
            )


            ids.forEach { id ->
                val options = appWidgetManager.getAppWidgetOptions(id)
                val Width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                val Height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)

                val isTall = Height > Width * 0.8
                val result = resolveBackground(context, isTall)
                updateControlWidget(
                    context = context,
                    appWidgetManager = appWidgetManager,
                    appWidgetId = id,
                    title = title,
                    artworkUri = artworkUri,
                    playing = playing,
                    BackgroundBitmap = result?.drawable?.toBitmap(
                        width = 512,
                        height = 512,
                        config = Bitmap.Config.ARGB_8888
                    ),
                    isTall = isTall
                )


                Log.d("ControlWidgetUpdater", "Widget orientation - isTall: $isTall")
            }

        } else {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val Width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val Height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)

            Log.d("ControlWidgetUpdater", "Widget dimensions - Width: $Width, Height: $Height")

            val isTall = Height > Width * 0.8
            val result = resolveBackground(context, isTall)
            updateControlWidget(
                context = context,
                appWidgetManager = appWidgetManager,
                appWidgetId = appWidgetId,
                title = title,
                artworkUri = artworkUri,
                playing = playing,
                BackgroundBitmap = result?.drawable?.toBitmap(
                    width = 512,
                    height = 512,
                    config = Bitmap.Config.ARGB_8888
                ),
                isTall = isTall
            )



        }


    }


 */
}
