package jp.gr.java_conf.SenseMusicClock

import android.content.Context
import android.graphics.drawable.Drawable
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun getDrawble_forRootBackgroundByTimeAndOrientation(orientation: Int ,context: Context ) : Drawable?{

    val nowTime = LocalDateTime.now()
    val dtformat1 = DateTimeFormatter.ofPattern("HH")
    val fdate1 = dtformat1.format(nowTime)

    val Rdrawable = if (orientation == 1){


        when (fdate1.toInt()) {
            in 5..9 -> R.drawable.oblong_morning
            in 10..15 -> R.drawable.oblong_noon
            in 16..19 -> R.drawable.oblong_evening
            else -> R.drawable.oblong_night
        }

    } else{

        when (fdate1.toInt()) {
            in 5..9 -> R.drawable.land_morning
            in 10..15 -> R.drawable.land_noon
            in 16..19 -> R.drawable.land_evening
            else -> R.drawable.land_night
        }
    }

    val drawable = context.getDrawable( Rdrawable)
    return  drawable
}
