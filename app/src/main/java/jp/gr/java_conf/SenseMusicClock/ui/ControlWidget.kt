/*

package jp.gr.java_conf.SenseMusicClock.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import jp.gr.java_conf.SenseMusicClock.MusicService
import jp.gr.java_conf.SenseMusicClock.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Implementation of App Widget functionality.
 */
class ControlWidget : AppWidgetProvider() {
    companion object {
        const val clixkedContainer =
            "jp.gr.java_conf.SenseMusicClock.ui.ControlWidget.clickedContainer"
        const val clickedPlay = "jp.gr.java_conf.SenseMusicClock.ui.ControlWidget.clickedPlay"
        const val clickedNext = "jp.gr.java_conf.SenseMusicClock.ui.ControlWidget.clickedNext"
        const val clickedPrev = "jp.gr.java_conf.SenseMusicClock.ui.ControlWidget.clickedPrev"

        const val STARTSERVICE = "jp.gr.java_conf.SenseMusicClock.ui.ControlWidget.startService"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {

            try {
                for (appWidgetId in appWidgetIds) {
                    ControlWidgetUpdater.update(
                        context,
                        appWidgetManager = appWidgetManager,
                        changedAppWidgetId = appWidgetId
                    )
                }
            } finally {
                result.finish()
            }
        }
        // There may be multiple widgets active, so update all of them

    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        super.onReceive(context, intent)

        if (intent == null || context == null) return

        Log.d("ControlWidget", "Received intent with action: ${intent.action}")
        when (intent.action) {


            clixkedContainer -> {
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(launchIntent)
                } catch (e: ActivityNotFoundException) {
                    Log.e("ControlWidget", "Failed to launch MainActivity", e)
                }
            }

            clickedNext -> {
                context.startForegroundService(

                    Intent(context, MusicService::class.java).apply {
                        action = STARTSERVICE
                    }
                )
                context.sendBroadcast(Intent().apply {
                    action = MusicService.clickedNext
                })
            }

            clickedPlay -> {
                context.startForegroundService(

                    Intent(context, MusicService::class.java).apply {
                        action = STARTSERVICE
                    }
                )
                context.sendBroadcast(Intent().apply {
                    action = MusicService.clickedPlay
                })
            }

            clickedPrev -> {
                context.startForegroundService(

                    Intent(context, MusicService::class.java).apply {
                        action = STARTSERVICE
                    }
                )
                context.sendBroadcast(Intent().apply {
                    action = MusicService.clickedPrev
                })
            }
        }


    }

    override fun onAppWidgetOptionsChanged(
        context: Context?,
        appWidgetManager: AppWidgetManager?,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        if (context == null || appWidgetManager == null) return


        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {

            try {
                ControlWidgetUpdater.update(
                    context,
                    appWidgetManager = appWidgetManager,
                    changedAppWidgetId = appWidgetId
                )

            } finally {
                result.finish()
            }
        }
        // There m
    }
}
*/