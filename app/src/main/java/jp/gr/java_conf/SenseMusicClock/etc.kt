import android.content.ContentValues
import android.provider.MediaStore



val app_dir  = ContentValues().apply{
    put(MediaStore.Audio.Media.DISPLAY_NAME, ".nomedia") // フォルダ名
    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/SMC")// 保存先の相対パス
    put(MediaStore.Audio.Media.IS_PENDING, 0)
    put(MediaStore.Audio.Media.MIME_TYPE, "Audio/mpeg") // ファイルタイプ
}

const val dummyListId = -1236457810114514L





const val IDENTIFIER_INITIAL_INDEX_PROBLEM = "  ///IDENTIFIER_INITIAL_INDEX_PROBLEM"
const val MAX_SCROLL_DISTANCE_FOR_ANIMATION  = 30




