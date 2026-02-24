package jp.gr.java_conf.SenseMusicClock



import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Display
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

object LocalMusicRepository {



    private var _tracksFlow = MutableStateFlow( listOf<MediaItem>())

    val tracksFlow : StateFlow<List<MediaItem>> = _tracksFlow.asStateFlow()
    private val idToIndexMap = mutableMapOf<String, Int>()

    val EXTRA_ALBUM_ID = "ALBUM_ID"
    val EXTRA_ARTIST_ID = "ARTIST_ID"
    val EXTRA_RELATIVE_PATH = "RELATIVE_PATH"

    val EXTRA_DISPLAY_NAME = "DISPLAY_NAME"

    val EXTRA_DATA_PATH = "DATA_PATH"


    // 追加: 指定された相対パス群（または URI ベースのパス）とファイル名で絞り込む selection/args を生成する
    // - paths: RELATIVE_PATH に含めたいフォルダパスのリスト（例: "Music/SMC"）
    // - fileName: ファイル名での絞り込み（部分一致）。null または空文字ならファイル名条件は追加されない
    // 戻り値: Pair(selectionString, selectionArgsArray)
    fun buildPathAndFilenameSelection(paths: List<String>, fileName: String?): Pair<String, Array<String>> {
        val parts = mutableListOf<String>()
        val args = mutableListOf<String>()

        for (p in paths) {
            // RELATIVE_PATH はディレクトリ部分を持つので先頭/末尾を整形して部分一致で検索
            parts.add("(${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?)")
            args.add("${p.removeSuffix("/")}%")

            // DATA（フルパス）側でもフォルダを含むか確認する（互換性のため）
            parts.add("(${MediaStore.Audio.Media.DATA} LIKE ?)")
            args.add("%/${p.removePrefix("/")}%")
        }

        // fileName が指定されていれば DISPLAY_NAME と DATA の両方で部分一致を追加
        val name = fileName?.takeIf { it.isNotBlank() }
        if (name != null) {
            parts.add("(${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?)")
            args.add("%${name}%")

            parts.add("(${MediaStore.Audio.Media.DATA} LIKE ?)")
            args.add("%${name}%")
        }

        val selection = if (parts.isEmpty()) "1=1" else parts.joinToString(" OR ")
        return selection to args.toTypedArray()
    }


    suspend fun loadLocalMusicFromAppDir(
        context: Context,
        UserRelativePaths: List<String> = emptyList()
    ): List<MediaItem> {
        val relativePaths = listOf("Music/SMC") + UserRelativePaths

        if (relativePaths.isEmpty()) return emptyList()
        val list = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DISPLAY_NAME
        )

        val parts = mutableListOf<String>()
        val args = mutableListOf<String>()
        for (p in relativePaths) {
            parts.add("(${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?)")
            parts.add("(${MediaStore.Audio.Media.DATA} LIKE ?)")
            args.add("${p.removeSuffix("/")}%")
            args.add("%/${p.removePrefix("/")}%")
        }
        val selection = parts.joinToString(" OR ")
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        // クエリとカーソル走査を IO コンテキストで行う（カーソルが開いている間は同じスレッドで処理）
        withContext(Dispatchers.IO) {
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                args.toTypedArray(),
                sortOrder
            )

            cursor?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val albumIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val artistIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumIdIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val artistIdIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
                val trackIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val dataIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val relativePathIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
                val displayNameIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)


                while (c.moveToNext()) {
                    val id = c.getLong(idIdx)
                    val title = c.getString(titleIdx) ?: ""
                    if (title.contains(".nomedia")) continue
                    val album = c.getString(albumIdx) ?: ""
                    val artist = c.getString(artistIdx) ?: ""
                    val albumId = c.getLong(albumIdIdx)
                    val artistId = c.getLong(artistIdIdx)
                    val trackNo = c.getInt(trackIdx)
                    val path = c.getString(dataIdx) ?: ""
                    if (path.contains(".nomedia")) continue
                    val relativePath = c.getString(relativePathIdx) ?: ""
                    if (relativePath.contains(".nomedia")) continue
                    val displayName = c.getString(displayNameIdx) ?: ""
                    if (displayName.contains(".nomedia")) continue
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                    val albumArtUri : Uri? = try {
                        "content://media/external/audio/albumart".toUri()
                            .buildUpon()
                            .appendPath(albumId.toString())
                            .build()
                    } catch (e: Exception) {
                        android.util.Log.w("LocalMusicRepository", "build albumArtUri failed", e)
                        null
                    }

                    val extras = Bundle().apply {
                        putLong(EXTRA_ALBUM_ID, albumId)
                        putLong(EXTRA_ARTIST_ID, artistId)
                        putString(EXTRA_RELATIVE_PATH,relativePath )
                        putString(EXTRA_DISPLAY_NAME, displayName)
                        putString(EXTRA_DATA_PATH, path)
                    }
                    val metadata = MediaMetadata.Builder()
                        .setTitle(title)
                        .setAlbumTitle(album)
                        .setArtist(artist)
                        .setArtworkUri(
                            albumArtUri ?: "app:///default_album_art.webp".toUri()
                        )
                        .setTrackNumber(trackNo)
                        .setExtras(extras)
                        .setIsPlayable(true)
                        .setIsBrowsable(false)
                        .build()

                    val mediaItem = MediaItem.Builder()
                        .setMediaId(id.toString() + "_" + fastRandomUUID().toString()) // ID とタイトルを組み合わせてユニークな mediaId を生成
                        .setUri(uri)
                        .setMediaMetadata(metadata)
                        .build()



                    list.add(mediaItem)
                }
            }
        }

        // 取得したリストをシャッフルして順序をランダム化する
        list.shuffle()

        return list
    }






    fun setTracks(newTracks: List<MediaItem>) {
        _tracksFlow.value = newTracks
    }


    fun createMap_idToIndex(Tracks: List<MediaItem> = tracksFlow.value) {
        idToIndexMap.clear()
        Tracks.forEachIndexed { index, mediaItem ->
            idToIndexMap[mediaItem.mediaId] = index
        }
    }

    fun clearMap_idToIndex() {
        idToIndexMap.clear()
    }


    suspend fun loadLocalMusicAndSetTracksAndCreateMap(
        context: Context,
        UserRelativePaths: List<String> = emptyList()
    ) {
        val localTracks =  loadLocalMusicFromAppDir(context, UserRelativePaths)
        setTracksAndCreateMap( localTracks )
    }


    fun setTracksAndCreateMap(newTracks: List<MediaItem>) {
        setTracks(newTracks)
        createMap_idToIndex(newTracks)
    }

    fun getIndexById(mediaId: String): Int? {
        if (idToIndexMap.isEmpty()) {
            createMap_idToIndex()
        }
        return idToIndexMap[mediaId]
    }

    fun getTracks(): List<MediaItem> = tracksFlow.value



}
