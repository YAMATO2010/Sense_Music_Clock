package jp.gr.java_conf.SenseMusicClock


import android.content.ContentResolver
import android.content.Context
import android.provider.MediaStore
import android.util.Log

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.media3.common.MediaItem

import jp.gr.java_conf.SenseMusicClock.Music.Data.BlocklistItem
import jp.gr.java_conf.SenseMusicClock.Music.Data.PlaylistItem
import jp.gr.java_conf.SenseMusicClock.Music.LocalMusicFetcher
import jp.gr.java_conf.SenseMusicClock.Music.LocalMusicFetcher.playlist_selection
import jp.gr.java_conf.SenseMusicClock.Music.LocalMusicFetcher.selection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object LocalMusicRepository {


    private var _tracksFlow = MutableStateFlow(listOf<MediaItem>())

    private var blockItems = listOf<BlocklistItem>()

    val tracksFlow: StateFlow<List<MediaItem>> = _tracksFlow.asStateFlow()

    private var isShuffle = true
    private val idToIndexMap = mutableMapOf<String, Int>()

    val EXTRA_ALBUM_ID = LocalMusicFetcher.EXTRA_ALBUM_ID
    val EXTRA_ARTIST_ID = LocalMusicFetcher.EXTRA_ARTIST_ID
    val EXTRA_RELATIVE_PATH = LocalMusicFetcher.EXTRA_RELATIVE_PATH

    val EXTRA_DISPLAY_NAME = LocalMusicFetcher.EXTRA_DISPLAY_NAME

    val EXTRA_DATA_PATH = LocalMusicFetcher.EXTRA_DATA_PATH


    /*TODO プレイリストの選択肢を増やすときはここに追加する
    fun playlist_selection(): Pair<String, Array<String>> {


    }

     */

    //TODO プレイリストの時に、同じ曲（同じクエリ）が重複していた場合、消される可能性がある。重複していた場合、それに該当するパスを持つもののMediaItemのIDを操作し、重複させよ。


    fun setTracks(newTracks: List<MediaItem>): List<MediaItem> {
        val filteredTracks = newTracks.filterByBlocklist(blockItems)
        _tracksFlow.value = filteredTracks
        return filteredTracks
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

    suspend fun loadMusicAndSetTracksAndCreateMap_addedAtDesc(
        context: Context,
        UserRelativePaths: List<String> = emptyList(),
        isFilterByDir: Boolean,
        isBlock: Boolean,
        limit: Int,
        useCurrentShuffleMode: Boolean

    ) {
        val isShuffle = if (useCurrentShuffleMode) this.isShuffle else false

        val (selection, selectionArgs) = if (isFilterByDir) {
            LocalMusicFetcher.selection(UserRelativePaths)
        } else {
            Pair(null, null)
        }

        val queryArgs = LocalMusicFetcher.createQueryArgs(
            selection = selection,
            selectionArgs = selectionArgs,
            limit = limit,
            offset = 0,
            sortColumn = MediaStore.Audio.Media.DATE_ADDED,
            sortDirection = ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
        )

        return withContext(Dispatchers.IO) {
            val tracks = LocalMusicFetcher
                .loadMediaItemFromMediaStore(context.contentResolver, queryArgs)
                .let { tracks ->
                    if (isBlock) tracks.filterByBlocklist(blockItems) else tracks
                }
                .let { tracks ->
                    if (isShuffle) tracks.shuffled() else tracks
                }
            Log.d("LocalMusicRepository", "Loaded ${tracks.size} . first track: ${tracks.firstOrNull()?.mediaMetadata?.getDisplayName()}")
            setTracksAndCreateMap(tracks)
        }


    }

    suspend fun loadMusicAndSetTracksAndCreateMap_playlist(
        context: Context,
        playlistItems: List<PlaylistItem>
    ) {
        setTracksAndCreateMap(loadPlaylistMusic(context, playlistItems))
    }

    suspend fun loadMusicAndSetTracksAndCreateMap_albumId(
        context: Context,
        albumId: Long
    ) {
        setTracksAndCreateMap(loadAlbumMusic(context, albumId))
    }

    suspend fun loadMusicAndSetTracksAndCreateMap_artistId(
        context: Context,
        artistId: Long
    ) {
        setTracksAndCreateMap(loadArtistMusic(context, artistId))
    }

    suspend fun loadMusicAndSetTracksAndCreateMap_Id(
        context: Context,
        Id: Long
    ) {
        setTracksAndCreateMap(loadIdMusic(context, Id))
    }

    suspend fun loadAlbumMusic(
        context: Context,
        albumId: Long
    ): List<MediaItem> {
        val (selection, selectionArgs) = LocalMusicFetcher.albumID_selection(albumId)
        val queryArgs = LocalMusicFetcher.createQueryArgs(selection, selectionArgs)
        return withContext(Dispatchers.IO) {
            if (isShuffle) {
                LocalMusicFetcher.safeLoadMediaItemFromMediaStore(
                    context.contentResolver,
                    queryArgs
                ).shuffled()

            } else {
                LocalMusicFetcher.safeLoadMediaItemFromMediaStore(
                    context.contentResolver,
                    queryArgs
                )
            }
        }
    }

    suspend fun loadArtistMusic(
        context: Context,
        artistId: Long
    ): List<MediaItem> {
        val (selection, selectionArgs) = LocalMusicFetcher.artistID_selection(artistId)
        val queryArgs = LocalMusicFetcher.createQueryArgs(selection, selectionArgs)
        return withContext(Dispatchers.IO) {

            if (isShuffle) {
                LocalMusicFetcher.safeLoadMediaItemFromMediaStore(
                    context.contentResolver,
                    queryArgs
                ).shuffled()
            } else {
                LocalMusicFetcher.safeLoadMediaItemFromMediaStore(
                    context.contentResolver,
                    queryArgs
                )

            }
        }
    }

    suspend fun loadIdMusic(
        context: Context,
        Id: Long
    ): List<MediaItem> {
        val (selection, selectionArgs) = LocalMusicFetcher.id_selection(Id)
        val queryArgs = LocalMusicFetcher.createQueryArgs(selection, selectionArgs)
        return withContext(Dispatchers.IO) {
            LocalMusicFetcher.safeLoadMediaItemFromMediaStore(context.contentResolver, queryArgs)

        }
    }

    suspend fun loadPlaylistMusic(
        context: Context,
        playlistItems: List<PlaylistItem>
    ): List<MediaItem> {

        if (playlistItems.isEmpty()) {
            return emptyList()
        }

        val duplicateList: List<Pair<Int, PlaylistItem>> = playlistItems.groupBy { it.fileItem }
            .filter { it.value.size > 1 }
            .mapNotNull { (_, items) ->
                val content: PlaylistItem? = items.firstOrNull()
                val duplicateSize = items.size
                if (content != null) {
                    Pair<Int, PlaylistItem>(duplicateSize, content)
                } else null
            }


        val (selection, selectionArgs) = playlist_selection(playlistItems)
        val queryArgs = LocalMusicFetcher.createQueryArgs(selection, selectionArgs)

        val PlaylistTracks: MutableList<MediaItem> =
            LocalMusicFetcher.safeLoadMediaItemFromMediaStore(context.contentResolver, queryArgs)
                .toMutableList()
        return withContext(Dispatchers.Default) {

            duplicateList.forEach { item ->
                for (i in 0 until item.first) {

                    val duplicateTrack = PlaylistTracks.find { it.isSamePath(item.second.fileItem) }

                    if (duplicateTrack != null) {
                        val newMediaItem = duplicateTrack.buildUpon()
                            .setMediaId(duplicateTrack.mediaId + "_duplicate_${i}")
                            .build()
                        PlaylistTracks.add(newMediaItem)

                    }
                }
            }

            if (isShuffle) {
                return@withContext PlaylistTracks.shuffled().filterByBlocklist(blockItems)
            } else {
                PlaylistTracks.sortedByPlaylistItems(playlistItems).filterByBlocklist(blockItems)

            }

        }


    }


    suspend fun loadMusicAndSetTracksAndCreateMap(
        context: Context,
        UserRelativePaths: List<String> = emptyList()
    ) {
        val (selection, selectionArgs) = selection(UserRelativePaths)

        val queryArgs = LocalMusicFetcher.createQueryArgs(selection, selectionArgs)

        val localTracks =
            LocalMusicFetcher.loadMediaItemFromMediaStore(context.contentResolver, queryArgs)
                .filterByBlocklist(blockItems)
        withContext(Dispatchers.Default) {
            if (isShuffle) {
                setTracksAndCreateMap(localTracks.shuffled())
            } else {
                setTracksAndCreateMap(localTracks)
            }
        }
    }


    fun setTracksAndCreateMap(newTracks: List<MediaItem>) {

        setTracks(newTracks)
        val filteredTracks = setTracks(newTracks)
        createMap_idToIndex(filteredTracks)

    }


    fun getIndexById(mediaId: String): Int? {
        if (idToIndexMap.isEmpty()) {
            createMap_idToIndex()
        }
        return idToIndexMap[mediaId]
    }

    fun getTracks(): List<MediaItem> = tracksFlow.value

    fun setShuffle(shuffle: Boolean, className: String?) {
        if (className == MusicService::class.simpleName) {
            isShuffle = shuffle
        }
    }

    fun setBlockItems(newBlockItems: List<BlocklistItem>) {
        blockItems = newBlockItems
    }

    fun getBlockItems(): List<BlocklistItem> = blockItems

    fun findIdxByPathAndName(relativePath: String, displayName: String): Int {
        return tracksFlow.value.indexOfFirst {
            it.mediaMetadata.getRelativePath() == relativePath
                    && it.mediaMetadata.getDisplayName() == displayName
        }
    }


}
