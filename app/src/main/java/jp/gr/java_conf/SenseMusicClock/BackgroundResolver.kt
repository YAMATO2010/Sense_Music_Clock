package jp.gr.java_conf.SenseMusicClock


import android.content.Context
import android.util.Log
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object BackgroundResolver {


    const val NOW_MORNING = 1
    const val NOW_NOON = 2
    const val NOW_EVENING = 3
    const val NOW_NIGHT = 4


    val IMAGEFILE_KEY_OBLONG_MORNING = PrefsManager.IMAGEFILE_KEY_OBLONG_MORNING_KEY
    val IMAGEFILE_KEY_OBLONG_NOON = PrefsManager.IMAGEFILE_KEY_OBLONG_NOON_KEY
    val IMAGEFILE_KEY_OBLONG_EVENING = PrefsManager.IMAGEFILE_KEY_OBLONG_EVENING_KEY
    val IMAGEFILE_KEY_OBLONG_NIGHT = PrefsManager.IMAGEFILE_KEY_OBLONG_NIGHT_KEY
    val IMAGEFILE_KEY_LAND_MORNING = PrefsManager.IMAGEFILE_KEY_LAND_MORNING_KEY
    val IMAGEFILE_KEY_LAND_NOON = PrefsManager.IMAGEFILE_KEY_LAND_NOON_KEY
    val IMAGEFILE_KEY_LAND_EVENING = PrefsManager.IMAGEFILE_KEY_LAND_EVENING_KEY
    val IMAGEFILE_KEY_LAND_NIGHT = PrefsManager.IMAGEFILE_KEY_LAND_NIGHT_KEY


    const val BACKGROUNDS_PATH = "backgrounds"

    const val ORIENTATION_OBLONG = 1
    const val ORIENTATION_LAND = 2


    sealed class ImageSource {
        abstract val key: String

        data class FilePath(val file: File) : ImageSource() {
            override val key: String
                get() = file.absolutePath
        }

        data class Res(val id: Int) : ImageSource() {
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


    suspend fun getImageFilePath_forBackground(context: Context, key: String): String {

        return PrefsManager.getImageFilePath(context, key)
    }

    fun getDrawableId_byPrefsKey(key: String): ImageSource {
        val DrawableId = when (key) {

            IMAGEFILE_KEY_LAND_MORNING -> R.drawable.land_morning
            IMAGEFILE_KEY_LAND_NOON -> R.drawable.land_noon
            IMAGEFILE_KEY_LAND_EVENING -> R.drawable.land_evening
            IMAGEFILE_KEY_LAND_NIGHT -> R.drawable.land_night

            IMAGEFILE_KEY_OBLONG_MORNING -> R.drawable.oblong_morning
            IMAGEFILE_KEY_OBLONG_NOON -> R.drawable.oblong_noon
            IMAGEFILE_KEY_OBLONG_EVENING -> R.drawable.oblong_evening
            IMAGEFILE_KEY_OBLONG_NIGHT -> R.drawable.oblong_night

            else -> R.drawable.land_noon

        }
        return ImageSource.Res(DrawableId)
    }

    fun randomImageSource(context: Context, orientation: Int): ImageSource {

        val files = context.getAllFile_inInternalStorage(
            BACKGROUNDS_PATH
        ).map { value -> ImageSource.FilePath(value) }
        val defaultBackgrounds = if (orientation == ORIENTATION_OBLONG) {
            listOf(

                ImageSource.Res(R.drawable.oblong_morning),
                ImageSource.Res(R.drawable.oblong_noon),
                ImageSource.Res(R.drawable.oblong_evening),
                ImageSource.Res(R.drawable.oblong_night)
            )
        } else {
            listOf(
                ImageSource.Res(R.drawable.land_morning),
                ImageSource.Res(R.drawable.land_noon),
                ImageSource.Res(R.drawable.land_evening),
                ImageSource.Res(R.drawable.land_night),
            )
        }
        val allSources = files + defaultBackgrounds
        return allSources.random()


    }


    suspend fun loadBackgroundSource(context: Context, orientation: Int): ImageSource {


        val isRandom = PrefsManager.getRandomBackground(context)

        if (isRandom) {
            Log.d("BackgroundResolver", "ランダム背景が有効なのでランダム画像を取得")
            return randomImageSource(context, orientation)
        } else {
            val partOfDay = getPartOfDay(getNowHour_Int())

            Log.d("BackgroundResolver", "現在の時間帯：${partOfDay}、画像の向き：${orientation}")
            val prefKey = PrefsManager.getImageFileKey(orientation, partOfDay)
            Log.d("BackgroundResolver", "取得するPrefKey：${prefKey}")
            val filePath = getImageFilePath_forBackground(context, prefKey)
            Log.d("BackgroundResolver", "取得した画像ファイルパス：${filePath}")

            loadFileIfExists(context, "$BACKGROUNDS_PATH/$filePath")?.let {
                Log.d("BackgroundResolver", "画像ファイルが存在したのでそれを使用：${it}")
                return it
            }

            Log.d("BackgroundResolver", "画像ファイルが存在しなかったのでデフォルト画像を使用")
            return getDrawableId_byPrefsKey(prefKey)
        }

    }


}






