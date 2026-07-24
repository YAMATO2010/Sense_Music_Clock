package jp.gr.java_conf.SenseMusicClock.ui.Widget

import android.content.ComponentName
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import jp.gr.java_conf.SenseMusicClock.MusicService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext

class ActionPlayPause: ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val appContext = context.applicationContext
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

                if (controller.playWhenReady) {
                    controller.pause()
                } else {
                    controller.play()
                }
            } finally {
                MediaController.releaseFuture(controllerFuture)
            }
        }
    }

}