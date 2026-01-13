import android.content.ContentValues
import android.provider.MediaStore



val app_dir  = ContentValues().apply{
    put(MediaStore.Audio.Media.DISPLAY_NAME, ".nomedia") // フォルダ名
    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/SMC")// 保存先の相対パス
    put(MediaStore.Audio.Media.IS_PENDING, 0)
    put(MediaStore.Audio.Media.MIME_TYPE, "Audio/mpeg") // ファイルタイプ
}



val SHAREDPREFERENCES_NAME = "smc_prefs"
val MUSIC_DIR_RELATIVE_PATHS_KEY = "music_dir_relative_paths"
