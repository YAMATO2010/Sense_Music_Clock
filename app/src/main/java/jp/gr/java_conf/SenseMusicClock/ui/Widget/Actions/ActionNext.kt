package jp.gr.java_conf.SenseMusicClock.ui.Widget.Actions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import jp.gr.java_conf.SenseMusicClock.Music.MusicService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext

class ActionNext : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val appContext = context.applicationContext
        val intent = Intent(appContext, MusicService::class.java)
        appContext.startForegroundService(intent)
        withContext(Dispatchers.Main.immediate) {
            val sessionToken = SessionToken(
                appContext,
                ComponentName(appContext, MusicService::class.java)
            )

            val controllerFuture =
                MediaController.Builder(appContext, sessionToken)
                    .buildAsync()

            try {
                val controller = controllerFuture.await()
                controller.seekToNext()
            } finally {
                MediaController.releaseFuture(controllerFuture)
            }
        }
    }


}
