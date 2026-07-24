package jp.gr.java_conf.SenseMusicClock.ui.Widget

import android.net.Uri

data class WidgetState(
    val title: String,
    val artwork: Uri?,
    val playing: Boolean
)
