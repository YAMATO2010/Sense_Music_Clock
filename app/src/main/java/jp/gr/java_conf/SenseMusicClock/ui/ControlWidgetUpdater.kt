package jp.gr.java_conf.SenseMusicClock.ui


import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import coil.ImageLoader
import coil.executeBlocking
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.request.SuccessResult
import jp.gr.java_conf.SenseMusicClock.BackgroundResolver
import jp.gr.java_conf.SenseMusicClock.PrefsManager
import jp.gr.java_conf.SenseMusicClock.R

object ControlWidgetUpdater {

    private var title: String = "Title"
    private var artworkUri: Uri? = null
    private var playing: Boolean = false

    private fun <T> CreateAction(context: Context, action: String, cls: Class<T>): PendingIntent {
        val intent = Intent(context, cls).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private suspend fun resolveArtworkBitmap(context: Context, artworkUri: Uri?): Bitmap? {
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

    private suspend fun updateControlWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        title: String = "Title",
        artworkUri: Uri? = null,
        playing: Boolean = false,
        BackgroundBitmap: Bitmap? = null,
        isTall: Boolean = false
    ) {



        val layoutId = if (isTall) R.layout.control_widget_tall else R.layout.control_widget

        Log.d(
            "ControlWidgetUpdater",
            "Updating widget (ID: $appWidgetId) with title: $title, artworkUri: $artworkUri, playing: $playing, isTall: $isTall"
        )
        // Construct the RemoteViews object
        val views = RemoteViews(context.packageName, layoutId)
        views.setTextViewText(R.id.widget_title, title)

        val artworkBitmap = resolveArtworkBitmap(context, artworkUri)
        if (artworkBitmap == null) {
            views.setImageViewResource(R.id.widget_albumArt, R.drawable.default_album_art)
        } else {
            views.setImageViewBitmap(R.id.widget_albumArt, artworkBitmap)
        }
        if (playing) {
            views.setImageViewResource(R.id.widget_btnPlayPause, android.R.drawable.ic_media_pause)
        } else {
            views.setImageViewResource(R.id.widget_btnPlayPause, android.R.drawable.ic_media_play)
        }
        if (BackgroundBitmap != null) {
            views.setImageViewBitmap(R.id.widget_background, BackgroundBitmap)
        } else {
            views.setImageViewResource(R.id.widget_background, R.drawable.gradient2)
        }

        val containerIntent =
            CreateAction(context, ControlWidget.clixkedContainer, ControlWidget::class.java)
        val prevIntent = CreateAction(context, ControlWidget.clickedPrev, ControlWidget::class.java)
        val playIntent = CreateAction(context, ControlWidget.clickedPlay, ControlWidget::class.java)
        val nextIntent = CreateAction(context, ControlWidget.clickedNext, ControlWidget::class.java)

        views.setOnClickPendingIntent(android.R.id.background, containerIntent)
        views.setOnClickPendingIntent(R.id.widget_btnPrev, prevIntent)
        views.setOnClickPendingIntent(R.id.widget_btnPlayPause, playIntent)
        views.setOnClickPendingIntent(R.id.widget_btnNext, nextIntent)


        // Instruct the widget manager to update the widget
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            val widgetIds =
                appWidgetManager.getAppWidgetIds(ComponentName(context, ControlWidget::class.java))
            if (widgetIds.isNotEmpty()) {
                appWidgetManager.updateAppWidget(widgetIds, views)
            }
        } else {
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private suspend fun resolveBackground(context: Context, isTall: Boolean): ImageResult? {
        val orientation =
            if (isTall) BackgroundResolver.ORIENTATION_OBLONG else BackgroundResolver.ORIENTATION_LAND
        val result = if (PrefsManager.getWidgetBackground(context)) {
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


    internal suspend fun update(
        context: Context,
        newTitle: String? = null,
        newArtworkUri: Uri? = null,
        newIsPlaying: Boolean? = null,
        appWidgetManager: AppWidgetManager? = null,
        changedAppWidgetId: Int? = null

    ) {

        if (newTitle != null) title = newTitle
        if (newArtworkUri != null) artworkUri = newArtworkUri
        if (newIsPlaying != null) playing = newIsPlaying

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

}