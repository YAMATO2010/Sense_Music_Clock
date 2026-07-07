package jp.gr.java_conf.SenseMusicClock.Music

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.provider.MediaStore
import android.util.Log
import androidx.constraintlayout.widget.Placeholder
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.paging.LOG_TAG
import androidx.room.Query
import jp.gr.java_conf.SenseMusicClock.Music.Data.FileItem

import jp.gr.java_conf.SenseMusicClock.Music.Data.PlaylistItem
import jp.gr.java_conf.SenseMusicClock.fastRandomUUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LocalMusicFetcher {
    const val EXTRA_ALBUM_ID = "ALBUM_ID"
    const val EXTRA_ARTIST_ID = "ARTIST_ID"
    const val EXTRA_RELATIVE_PATH = "RELATIVE_PATH"

    const val EXTRA_DISPLAY_NAME = "DISPLAY_NAME"

    const val EXTRA_DATA_PATH = "DATA_PATH"

    const val OR = " OR "


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
        val selection = parts.joinToString(OR)

        return Pair(selection, args.toTypedArray())
    }

    fun path_selection_RPath_LIKE(fileItems: List<FileItem>): Pair<String, Array<String>> {
        val selection =
            "(${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Audio.Media.DISPLAY_NAME} = ?)"
        val args = mutableListOf<String>()
        val selectionParts = mutableListOf<String>()

        for (item in fileItems) {

            selectionParts.add(selection)
            args.add("%${item.relativePath}%")
            args.add(item.fileName)
            Log.d(
                "LocalMusicRepository",
                "path_selection_RPath_LIKE: added selection for ${item.relativePath}/${item.fileName}"
            )
        }
        val finalSelection = selectionParts.joinToString(OR)


        return (finalSelection to args.toTypedArray())
    }

    fun playlist_selection(playlistItems: List<PlaylistItem>): Pair<String, Array<String>> {

        val parts = mutableListOf<String>()
        val args = mutableListOf<String>()

        for (item in playlistItems.map { it.fileItem }) {
            parts.add("(${MediaStore.Audio.Media.RELATIVE_PATH} = ? AND ${MediaStore.Audio.Media.DISPLAY_NAME} = ?)")
            args.add(item.relativePath)
            args.add(item.fileName)
            Log.d(
                "LocalMusicRepository",
                "playlist_selection: added selection for ${item.relativePath}/${item.fileName}"
            )
        }
        val selection = parts.joinToString(OR)
        return (selection to args.toTypedArray())
    }

    fun id_selection(id: Long): Pair<String, Array<String>> {
        val selection = "${MediaStore.Audio.Media._ID} = ?"
        val args = arrayOf(id.toString())
        Log.d(
            "LocalMusicRepository",
            "id_selection: added selection for id ${id}"
        )
        return (selection to args)
    }

    fun title_selection(title: String): Pair<String, Array<String>> {

        val parts = mutableListOf<String>()



        parts.add("${MediaStore.Audio.Media.TITLE} LIKE ?")
        val args = arrayOf("%$title%")
        Log.d(
            "LocalMusicRepository",
            "title_selection: added selection for ${title}"
        )

        val selection = parts.joinToString(OR)
        return (selection to args)
    }

    fun albumID_selection(albumID: Long): Pair<String, Array<String>> {
        val parts = mutableListOf<String>()



        parts.add("${MediaStore.Audio.Media.ALBUM_ID} = ?")
        val args = arrayOf(albumID.toString())
        Log.d(
            "LocalMusicRepository",
            "album_selection: added selection for albumID ${albumID}"
        )

        val selection = parts.joinToString(OR)
        return (selection to args)


    }


    fun artistID_selection(artistID: Long): Pair<String, Array<String>> {
        val parts = mutableListOf<String>()



        parts.add("${MediaStore.Audio.Media.ARTIST_ID} = ?")
        val args = arrayOf(artistID.toString())
        Log.d(
            "LocalMusicRepository",
            "album_selection: added selection for artistID ${artistID}"
        )

        val selection = parts.joinToString(OR)
        return (selection to args)


    }

    fun mediaId_selection(ids: List<Long>): Pair<String, Array<String>> {

        val parts = mutableListOf<String>()
        val args = mutableListOf<String>()

        for (item in ids) {
            parts.add("${MediaStore.Audio.Media._ID} = ?")
            args.add(item.toString())
            Log.d(
                "LocalMusicRepository",
                "mediaID_selection: added selection for ${item}"
            )
        }
        val selection = parts.joinToString(OR)
        return (selection to args.toTypedArray())
    }


    fun sortOrder(): String {

        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
        return sortOrder
    }


    fun createQueryArgs(
        selection: String?,
        selectionArgs: Array<String>?,
        limit: Int,
        offset: Int
    ): Bundle {
        val args = Bundle()

        args.run {

            if (selection != null && !selectionArgs.isNullOrEmpty()) {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)

            }
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)

            putInt(
                ContentResolver.QUERY_ARG_SORT_DIRECTION,
                ContentResolver.QUERY_SORT_DIRECTION_ASCENDING
            )
            putString(ContentResolver.QUERY_ARG_SORT_COLUMNS, MediaStore.Audio.Media.TITLE)
        }


        return args
    }

    fun createQueryArgs(
        selection: String?,
        selectionArgs: Array<String>?,
    ): Bundle {
        val args = Bundle()

        args.run {

            if (selection != null && !selectionArgs.isNullOrEmpty()) {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)

            }

            putInt(
                ContentResolver.QUERY_ARG_SORT_DIRECTION,
                ContentResolver.QUERY_SORT_DIRECTION_ASCENDING
            )
            putString(ContentResolver.QUERY_ARG_SORT_COLUMNS, MediaStore.Audio.Media.TITLE)
        }
        return args
    }

    fun createQueryArgs(
        selection: String?,
        selectionArgs: Array<String>?,
        limit: Int,
        offset: Int,
        sortColumn: String,
        sortDirection: Int ,
    ): Bundle {
        val args = Bundle()

        args.apply {

            if (selection != null && !selectionArgs.isNullOrEmpty()) {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)

            }
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)

            putStringArray(
                ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(sortColumn)
            )
            putInt(
                ContentResolver.QUERY_ARG_SORT_DIRECTION,
                sortDirection
            )
        }

        return args
    }

    //todo プレースホルダ対応クエリの実装。MediaStore のクエリは一度に大量の条件を指定できないため、分割して複数回クエリを実行する

    suspend fun SafeLocalMusicFromAppDir(
        resolver: ContentResolver,
        queryArgs: Bundle,
        cancellationSignal: CancellationSignal? = null,
        // 一度に投げる「曲（条件）」の数。安全のため 100 くらいがベスト
        chunkSize: Int = 100,
        argsPerItem: Int = 2,
        singleCondition: String = "(${MediaStore.Audio.Media.RELATIVE_PATH} = ? AND ${MediaStore.Audio.Media.DISPLAY_NAME} = ?)"
    ): List<MediaStoreAudioSummary> {

        val fullSelection = queryArgs.getString(ContentResolver.QUERY_ARG_SQL_SELECTION)
        val fullSelectionArgs =
            queryArgs.getStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS)

        // プレースホルダがない、または引数が少なければそのまま実行
        if (fullSelection == null || fullSelectionArgs == null || fullSelectionArgs.size <= chunkSize) {
            return loadLocalMusicFromAppDir(resolver, queryArgs, cancellationSignal)
        }

        val list = mutableListOf<MediaStoreAudioSummary>()

        // selectionArgs を chunkSize (例: 100個) ずつに分割してループ
        // 1つの条件に付き引数が2つ（path, name）なら、引数は chunkSize * 2 ずつ取り出す

        val argsChunks = fullSelectionArgs.toList().chunked(chunkSize * argsPerItem)

        // Selection文の "(path=? AND name=?)" のパーツを特定する
        // 元の Selection が "((固定条件) AND ((条件1) OR (条件2) ...))" の形式と想定
        // ここでは一番安全な「引数の数に合わせて Selection を動的に作る」方法を推奨します

        argsChunks.forEach { chunkArgs ->
            // このチャンク専用の Selection 文を組み立てる
            // 例: (rel_path=? AND disp_name=?) を引数の数だけ OR でつなぐ

            val pagedSelection = List(chunkArgs.size / argsPerItem) { singleCondition }
                .joinToString(" OR ")


            val pagedQueryArgs = createQueryArgs(
                selection = pagedSelection,
                selectionArgs = chunkArgs.toTypedArray(),
                limit = chunkSize,
                offset = 0
            )

            val pageResults = loadLocalMusicFromAppDir(resolver, pagedQueryArgs, cancellationSignal)
            list.addAll(pageResults)
        }

        return list.sortedBy { it.title }
    }

    suspend fun loadMediaItemFromMediaStore(
        resolver: ContentResolver, queryArgs: Bundle,

        cancellationSignal: CancellationSignal? = null,
    ): List<MediaItem> {

        return withContext(Dispatchers.IO) {
            loadLocalMusicFromAppDir(
                resolver,
                queryArgs,
                cancellationSignal
            ).map { it.toMediaItem() }.withUniqueMediaIds()
        }


    }

    suspend fun safeLoadMediaItemFromMediaStore(
        resolver: ContentResolver, queryArgs: Bundle,

        chunkSize: Int = 100,
        argsPerItem: Int = 2,
        singleCondition: String = "(${MediaStore.Audio.Media.RELATIVE_PATH} = ? AND ${MediaStore.Audio.Media.DISPLAY_NAME} = ?)",
        cancellationSignal: CancellationSignal? = null,

        ): List<MediaItem> {

        return withContext(Dispatchers.IO) {
            SafeLocalMusicFromAppDir(
                resolver,
                queryArgs,
                cancellationSignal,
                chunkSize,
                argsPerItem,
                singleCondition
            ).map { it.toMediaItem() }.withUniqueMediaIds()
        }


    }

    suspend fun FetchAllMusicPage(
        resolver: ContentResolver,
        cancellationSignal: CancellationSignal? = null,
        page: Int,
        limit: Int
    ): List<MediaStoreAudioSummary> {
        return withContext(Dispatchers.IO) {

            val offset = (limit * page) - limit
            val useOffset = offset.coerceAtLeast(0)
            Log.d(
                "LIST_/LocalMusicFetcher",
                "FetchAllMusicPage: page=$page, limit=$limit, offset=$useOffset, limit = $limit"
            )


            val queryArgs = createQueryArgs(null, null, limit, useOffset)
            val Musics = loadLocalMusicFromAppDir(resolver, queryArgs, cancellationSignal)
            Log.d(
                "LIST_/LocalMusicFetcher",
                "FetchAllMusicPage: loaded ${Musics.size} items for page $page"
            )
            return@withContext Musics
        }

    }


    suspend fun loadLocalMusicFromAppDir(
        resolver: ContentResolver,
        queryArgs: Bundle,
        cancellationSignal: CancellationSignal? = null,


        ): List<MediaStoreAudioSummary> {

        val list = mutableListOf<MediaStoreAudioSummary>()
        val selection = queryArgs.getString(ContentResolver.QUERY_ARG_SQL_SELECTION)
        val selectionArgs = queryArgs.getStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS)
        val noMediaSelection = "${MediaStore.Audio.Media.DISPLAY_NAME} NOT LIKE ?"
        val sortColumns = queryArgs.getStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS)
        val sortDirection = queryArgs.getInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_ASCENDING)

        val newSelection = if (selection == null) {
            noMediaSelection
        } else {
            "($selection) AND ($noMediaSelection)"
        }
        val newSelectionArgs =
            ((selectionArgs?.toList() ?: listOf()) + listOf("%.nomedia%")).toTypedArray()

        val newQueryArgs = createQueryArgs(
            selection = newSelection,
            selectionArgs = newSelectionArgs,
            limit = queryArgs.getInt(ContentResolver.QUERY_ARG_LIMIT, -1),
            offset = queryArgs.getInt(ContentResolver.QUERY_ARG_OFFSET, 0),
            sortColumn = sortColumns?.firstOrNull() ?: MediaStore.Audio.Media.DATE_ADDED,
            sortDirection = sortDirection
        )
        val projection: Array<String> = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TRACK
        )

        Log.d(
            "LocalMusicFetcher",
            "loadLocalMusicFromAppDir: executing query with selection=$newSelection, ,argsFirst=${newSelectionArgs.first()} argsCount=${newSelectionArgs.size}, limit=${
                queryArgs.getInt(
                    ContentResolver.QUERY_ARG_LIMIT,
                    -1
                )
            }, offset=${queryArgs.getInt(ContentResolver.QUERY_ARG_OFFSET, 0)}"
        )
        try {


            // クエリとカーソル走査を IO コンテキストで行う（カーソルが開いている間は同じスレッドで処理）
            withContext(Dispatchers.IO) {
                val cursor = resolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    newQueryArgs,
                    cancellationSignal
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
                    val relativePathIdx =
                        c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
                    val displayNameIdx =
                        c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)



                    Log.d(
                        "LocalMusicFetcher",
                        "loadLocalMusicFromAppDir: query executed, starting to read cursor with ${c.count} items"
                    )
                    while (c.moveToNext()) {
                        val id = c.getLong(idIdx)
                        val title = c.getString(titleIdx) ?: ""
                        if (title.contains(".nomedia", ignoreCase = true)) {
                            Log.e(
                                "LocalMusicFetcher",
                                "Skipping item with ID $id because title contains .nomedia: $title"
                            )
                            continue
                        }

                        val album = c.getString(albumIdx) ?: ""
                        val artist = c.getString(artistIdx) ?: ""
                        val albumId = c.getLong(albumIdIdx)
                        val artistId = c.getLong(artistIdIdx)
                        val trackNo = c.getInt(trackIdx)
                        val path = c.getString(dataIdx) ?: ""
                        if (path.contains(".nomedia", ignoreCase = true)) {
                            Log.e(
                                "LocalMusicFetcher",
                                "Skipping item with ID $id because data path contains .nomedia: $path"
                            )

                            continue
                        }

                        val relativePath = c.getString(relativePathIdx) ?: ""
                        if (relativePath.contains(".nomedia", ignoreCase = true)) {
                            Log.e(
                                "LocalMusicFetcher",
                                "Skipping item with ID $id because relative path contains .nomedia: $relativePath"
                            )

                            continue
                        }

                        val displayName = c.getString(displayNameIdx) ?: ""
                        if (displayName.contains(".nomedia", ignoreCase = true)) {
                            Log.e(
                                "LocalMusicFetcher",
                                "Skipping item with ID $id because display name contains .nomedia: $displayName"
                            )
                            continue

                        }

                        val uri =
                            ContentUris.withAppendedId(
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                id
                            )

                        val albumArtUri: Uri = ContentUris.withAppendedId(
                            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                            albumId
                        )
                        val mediaItem = MediaStoreAudioSummary(
                            id = id,
                            title = title,
                            album = album,
                            artist = artist,
                            albumId = albumId,
                            artistId = artistId,
                            trackNo = trackNo,
                            data = path,
                            relativePath = relativePath,
                            displayName = displayName,
                            uri = uri,
                            albumArtUri = albumArtUri
                        )



                        list.add(mediaItem)
                    }
                }
                Log.d(
                    "LocalMusicFetcher",
                    "loadLocalMusicFromAppDir: finished reading cursor, total items loaded=${list.size}"
                )

            }
        } catch (e: SecurityException) {
            Log.e("LocalMusicFetcher", "SecurityException while querying MediaStore", e)
        } catch (e: OperationCanceledException) {
            Log.e("LocalMusicFetcher", "OperationCanceledException while querying MediaStore", e)
        } catch (e: Exception) {
            Log.e("LocalMusicFetcher", "Unexpected exception while querying MediaStore", e)
        }

        return list
    }


    data class MediaStoreAudioSummary(
        val id: Long,
        val title: String?,
        val album: String?,
        val artist: String?,
        val albumId: Long?,
        val artistId: Long?,
        val trackNo: Int?,
        val data: String?,
        val relativePath: String?,
        val displayName: String?,
        val uri: Uri?,
        val albumArtUri: Uri?

    )


    fun MediaStoreAudioSummary.toMediaItem(): MediaItem {

        val extras = Bundle().apply {

            putLong(EXTRA_ALBUM_ID, albumId ?: -1L)
            putLong(EXTRA_ARTIST_ID, artistId ?: -1L)
            putString(EXTRA_RELATIVE_PATH, relativePath ?: "")
            putString(EXTRA_DISPLAY_NAME, displayName ?: "")
            putString(EXTRA_DATA_PATH, data ?: "")
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(title ?: "<Unknown Title>")
            .setAlbumTitle(album ?: "<Unknown Album>")
            .setArtist(artist ?: "<Unknown Artist>")
            .setArtworkUri(
                albumArtUri ?: "app:///default_album_art.webp".toUri()
            )
            .setTrackNumber(trackNo ?: 0)
            .setExtras(extras)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .build()

        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }

    fun List<MediaItem>.withUniqueMediaIds(): List<MediaItem> {
        val seen = mutableSetOf<String>()
        return this.map { item ->
            val originalId = item.mediaId ?: ""
            // mediaId が空ならランダムな基本 ID を使う（先頭に "_" を付けて区別）
            var candidateId = if (originalId.isNotEmpty()) originalId else "_${fastRandomUUID()}"

            // すでに存在する ID ならランダム文字列を付与してユニーク化
            while (seen.contains(candidateId)) {
                candidateId = if (originalId.isNotEmpty()) {
                    "${originalId}_${fastRandomUUID()}"
                } else {
                    // 元が空の場合は別のランダム ID を作る
                    "_${fastRandomUUID()}"
                }
            }

            seen.add(candidateId)

            // mediaId を変更する必要がある場合は buildUpon() で新しい MediaItem を作る
            if (candidateId == item.mediaId) {
                item
            } else {
                item.buildUpon()
                    .setMediaId(candidateId)
                    .build()
            }
        }
    }


}