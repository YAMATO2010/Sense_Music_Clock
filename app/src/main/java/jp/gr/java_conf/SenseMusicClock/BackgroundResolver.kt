package jp.gr.java_conf.SenseMusicClock


import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.ImageView
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
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
        abstract val key: String

        data class FilePath(val file: File) : ImageSource(){
            override val key: String
                get() = file.absolutePath
        }
        data class Res(val id: Int) : ImageSource(){
            override val key: String
                get() = id.toString()
        }
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

    fun getPrefsKey(orientation: Int, partOfDay: Int, ): String {
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

    fun getDrawableId_byPrefsKey(key: String): ImageSource {
        val DrawableId = when (key) {

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

        val prefs = PrefsManager.getSharedPreferences(context)


        val partOfDay = getPartOfDay(getNowHour_Int())

        Log.d("BackgroundResolver", "現在の時間帯：${partOfDay}、画像の向き：${orientation}")
        val prefKey = getPrefsKey(orientation, partOfDay)
        Log.d("BackgroundResolver", "取得するPrefKey：${prefKey}")
        val filePath = getImageFilePath_forBackground(prefs, prefKey)
        Log.d("BackgroundResolver", "取得した画像ファイルパス：${filePath}")

        loadFileIfExists(context, "$BACKGROUNDS_PATH/$filePath")?.let {
            Log.d("BackgroundResolver", "画像ファイルが存在したのでそれを使用：${it}")
            return it
        }

        Log.d("BackgroundResolver", "画像ファイルが存在しなかったのでデフォルト画像を使用")
        return getDrawableId_byPrefsKey(prefKey)


    }



}



fun ImageView.load_forRoot(context: Context, orientation: Int): String {




    val source = BackgroundResolver.loadBackgroundSource(context, orientation)

    when (source) {
        is BackgroundResolver.ImageSource.FilePath -> {
            val imageLoader = ImageLoader.Builder(context)
                .components {
                    add(ImageDecoderDecoder.Factory()) // Android 9以降のWebP/GIF用
                    add(GifDecoder.Factory())          // Android 8以前のGIF用
                }
                .build()

            this.load(source.file, imageLoader)

        }

        is BackgroundResolver.ImageSource.Res -> {
            this.load(source.id)

        }
    }
    return  source.key


}



