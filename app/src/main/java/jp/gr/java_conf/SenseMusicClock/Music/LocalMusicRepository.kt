package jp.gr.java_conf.SenseMusicClock


import android.content.ContentUris
import android.content.Context

import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Display
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import jp.gr.java_conf.SenseMusicClock.Music.Data.PlaylistItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

object LocalMusicRepository {


    private var _tracksFlow = MutableStateFlow(listOf<MediaItem>())

    val tracksFlow: StateFlow<List<MediaItem>> = _tracksFlow.asStateFlow()
    private val idToIndexMap = mutableMapOf<String, Int>()

    const val EXTRA_ALBUM_ID = "ALBUM_ID"
    const val EXTRA_ARTIST_ID = "ARTIST_ID"
    const val EXTRA_RELATIVE_PATH = "RELATIVE_PATH"

    const val EXTRA_DISPLAY_NAME = "DISPLAY_NAME"

    const val EXTRA_DATA_PATH = "DATA_PATH"


    /*TODO プレイリストの選択肢を増やすときはここに追加する
    fun playlist_selection(): Pair<String, Array<String>> {


    }

     */

    fun selection(UserRelativePaths: List<String> = emptyList()): Pair<String, Array<String>> {
        val relativePaths = listOf("Music/SMC") + UserRelativePaths

        val parts = mutableListOf<String>()
        val args = mutableListOf<String>()
        for (p in relativePaths) {
            parts.add("(${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?)")
            parts.add("(${MediaStore.Audio.Media.DATA} LIKE ?)")
            args.add("${p.removeSuffix("/")}/%")
            args.add("%/${p.removePrefix("/")}%")
        }
        val selection = parts.joinToString(" OR ")

        return Pair(selection, args.toTypedArray())
    }

    fun playlist_selection(playlistItems: List<PlaylistItem>): Pair<String, Array<String>> {

        val parts = mutableListOf<String>()
        val args = mutableListOf<String>()

        for (item in playlistItems.map { it.fileItem }) {
            parts.add("(${MediaStore.Audio.Media.RELATIVE_PATH} = ? AND ${MediaStore.Audio.Media.DISPLAY_NAME} = ?)")
            args.add(item.relativePath)
            args.add(item.fileName)
            Log.d("LocalMusicRepository", "playlist_selection: added selection for ${item.relativePath}/${item.fileName}")
        }
        val selection = parts.joinToString(" OR ")
        return (selection to args.toTypedArray())
    }

    fun sortOrder(): String {

        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
        return sortOrder
    }


    suspend fun loadLocalMusicFromAppDir(
        context: Context,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
        projection: Array<String>? = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DISPLAY_NAME,

            ),
        isShuffle: Boolean = true

    ): List<MediaItem> {

        val list = mutableListOf<MediaItem>()

        // クエリとカーソル走査を IO コンテキストで行う（カーソルが開いている間は同じスレッドで処理）
        withContext(Dispatchers.IO) {
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
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
                    val uri =
                        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                    val albumArtUri: Uri? = try {
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
                        putString(EXTRA_RELATIVE_PATH, relativePath)
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

        if (isShuffle) {
            // 取得したリストをシャッフルして順序をランダム化する
            list.shuffle()
        }

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

    suspend fun loadLocalMusicAndSetTracksAndCreateMap_playlist(context: Context, playlistItems: List<PlaylistItem>) {
        setTracksAndCreateMap(loadPlaylistMusic(context,playlistItems))
    }
    suspend fun loadPlaylistMusic(context: Context, playlistItems: List<PlaylistItem>): List<MediaItem> {
        val (selection, selectionArgs) = playlist_selection(playlistItems)

        val PlaylistTracks = loadLocalMusicFromAppDir(context, selection, selectionArgs, null)
        return PlaylistTracks
    }




    suspend fun loadLocalMusicAndSetTracksAndCreateMap(
        context: Context,
        UserRelativePaths: List<String> = emptyList()
    ) {
        val (selection, selectionArgs) = selection(UserRelativePaths)
        val sortOrder = sortOrder()

        val localTracks = loadLocalMusicFromAppDir(context, selection, selectionArgs, sortOrder)
        setTracksAndCreateMap(localTracks)
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
