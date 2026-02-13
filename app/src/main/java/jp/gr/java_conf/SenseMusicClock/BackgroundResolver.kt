package jp.gr.java_conf.SenseMusicClock


import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.core.graphics.drawable.toDrawable
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.load
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object BackgroundResolver {


    const val NOW_MORNING = 1
    const val NOW_NOON = 2
    const val NOW_EVENING = 3
    const val NOW_NIGHT = 4


    const val IMAGEFILE_KEY_OBLONG_MORNING = "imageFile_key_oblong_morning"
    const val IMAGEFILE_KEY_OBLONG_NOON = "imageFile_key_oblong_noon"
    const val IMAGEFILE_KEY_OBLONG_EVENING = "imageFile_key_oblong_evening"
    const val IMAGEFILE_KEY_OBLONG_NIGHT = "imageFile_key_oblong_night"
    const val IMAGEFILE_KEY_LAND_MORNING = "imageFile_key_land_morning"
    const val IMAGEFILE_KEY_LAND_NOON = "imageFile_key_land_noon"
    const val IMAGEFILE_KEY_LAND_EVENING = "imageFile_key_land_evening"
    const val IMAGEFILE_KEY_LAND_NIGHT = "imageFile_key_land_night"


    const val BACKGROUNDS_PATH = "backgrounds"

    const val ORIENTATION_OBLONG = 1
    const val ORIENTATION_LAND = 2


    sealed class ImageSource {

        data class FilePath(val file: File) : ImageSource()
        data class Res(val id: Int) : ImageSource()
    }

    fun loadFileIfExists(context: Context, path: String?): ImageSource? {
        if (path == null) return null
        val file = File(context.filesDir, path)
        return if (file.exists() && file.isFile) ImageSource.FilePath(file) else null
    }

    fun getNowHour_Int(): Int {
        val nowTime = LocalDateTime.now()
        val dtFormat1 = DateTimeFormatter.ofPattern("HH")
        val fDate1 = dtFormat1.format(nowTime)
        return fDate1.toInt()
    }

    fun getPartOfDay(hour: Int): Int {

        val nowPartOfDay = when (hour) {
            in 5..9 -> NOW_MORNING
            in 10..15 -> NOW_NOON
            in 16..19 -> NOW_EVENING
            else -> NOW_NIGHT
        }
        return nowPartOfDay

    }

    fun getPrefsKey(orientation: Int, partOfDay: Int): String {
        val key = when (orientation) {
            ORIENTATION_OBLONG -> { // oblong
                when (partOfDay) {
                    NOW_MORNING -> IMAGEFILE_KEY_OBLONG_MORNING
                    NOW_NOON -> IMAGEFILE_KEY_OBLONG_NOON
                    NOW_EVENING -> IMAGEFILE_KEY_OBLONG_EVENING
                    NOW_NIGHT -> IMAGEFILE_KEY_OBLONG_NIGHT
                    else -> IMAGEFILE_KEY_OBLONG_NIGHT // default
                }
            }

            else -> { // land
                when (partOfDay) {
                    NOW_MORNING -> IMAGEFILE_KEY_LAND_MORNING
                    NOW_NOON -> IMAGEFILE_KEY_LAND_NOON
                    NOW_EVENING -> IMAGEFILE_KEY_LAND_EVENING
                    NOW_NIGHT -> IMAGEFILE_KEY_LAND_NIGHT
                    else -> IMAGEFILE_KEY_LAND_NIGHT // default
                }
            }
        }
        return key
    }

    fun getImageFilePath_forBackground(preferences: SharedPreferences, key: String): String? {

        return preferences.getString(key, null)
    }

    fun getDrawableId_byPrefsKey( key: String): ImageSource {
        val DrawableId = when(key){

            IMAGEFILE_KEY_LAND_MORNING -> R.drawable.land_morning;
            IMAGEFILE_KEY_LAND_NOON -> R.drawable.land_noon;
            IMAGEFILE_KEY_LAND_EVENING -> R.drawable.land_evening;
            IMAGEFILE_KEY_LAND_NIGHT -> R.drawable.land_night;

            IMAGEFILE_KEY_OBLONG_MORNING -> R.drawable.oblong_morning;
            IMAGEFILE_KEY_OBLONG_NOON -> R.drawable.oblong_noon;
            IMAGEFILE_KEY_OBLONG_EVENING -> R.drawable.oblong_evening;
            IMAGEFILE_KEY_OBLONG_NIGHT -> R.drawable.oblong_night;

            else -> R.drawable.land_noon

        }
        return ImageSource.Res(DrawableId)
    }


    fun loadBackgroundSource(context: Context, orientation: Int): ImageSource {

        val prefs = context.getSharedPreferences(context.getString(R.string.SHAREDPREFERENCES_NAME), Context.MODE_PRIVATE)


        val partOfDay = getPartOfDay(getNowHour_Int())

        Log.i("BackgroundResolver","現在の時間帯：${partOfDay}、画像の向き：${orientation}")
        val prefKey = getPrefsKey(orientation, partOfDay)
        Log.i("BackgroundResolver","取得するPrefKey：${prefKey}")
        val filePath = getImageFilePath_forBackground(prefs, prefKey)
        Log.i("BackgroundResolver","取得した画像ファイルパス：${filePath}")

        loadFileIfExists(context, "$BACKGROUNDS_PATH/$filePath")?.let {
            Log.i("BackgroundResolver","画像ファイルが存在したのでそれを使用：${it}")
            return it
        }

        Log.i("BackgroundResolver","画像ファイルが存在しなかったのでデフォルト画像を使用")
        return getDrawableId_byPrefsKey(prefKey)


    }





}

fun ImageView.load_forRoot(context: Context,orientation: Int){


    val source = BackgroundResolver.loadBackgroundSource(context,orientation)


    when (source) {
        is BackgroundResolver.ImageSource.FilePath -> {
            val imageLoader = ImageLoader.Builder(context)
                .components { add(GifDecoder.Factory()) }
                .build()
            this.load(source.file,imageLoader)
        }
        is BackgroundResolver.ImageSource.Res -> this.load(source.id)
    }

}

