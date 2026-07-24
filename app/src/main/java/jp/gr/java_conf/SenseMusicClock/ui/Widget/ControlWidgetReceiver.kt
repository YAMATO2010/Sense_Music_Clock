package jp.gr.java_conf.SenseMusicClock.ui.Widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class ControlWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ControlWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(
            "ControlWidget",
            "[WidgetTrace] receiver onUpdate ids=${appWidgetIds.contentToString()}"
        )
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(
            "ControlWidget",
            "[WidgetTrace] receiver onReceive action=${intent.action} extras=${intent.extras}"
        )
        super.onReceive(context, intent)
    }

}
