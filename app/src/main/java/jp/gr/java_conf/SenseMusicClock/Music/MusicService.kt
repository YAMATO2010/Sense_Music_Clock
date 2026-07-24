// kotlin
package jp.gr.java_conf.SenseMusicClock


import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import android.util.Log
import androidx.annotation.OptIn
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import jp.gr.java_conf.SenseMusicClock.Music.BitmapLoaderForSession
import jp.gr.java_conf.SenseMusicClock.Music.Data.DBManager
import jp.gr.java_conf.SenseMusicClock.Music.Data.PlayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import jp.gr.java_conf.SenseMusicClock.Music.TargetDirectoryPrefJSONManager
import jp.gr.java_conf.SenseMusicClock.ui.Widget.ControlWidgetUpdater

import jp.gr.java_conf.SenseMusicClock.ui.MainActivity
import jp.gr.java_conf.SenseMusicClock.ui.Widget.WidgetState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay


import kotlinx.coroutines.withContext


class MusicService : MediaLibraryService() {

    companion object {

        const val clickedPlay = "jp.gr.java_conf.SenseMusicClock.ui.MusicService.clickedPlay"
        const val clickedNext = "jp.gr.java_conf.SenseMusicClock.ui.MusicService.clickedNext"
        const val clickedPrev = "jp.gr.java_conf.SenseMusicClock.ui.MusicService.clickedPrev"

        const val CUSTOM_ACTION_SLEEP_TIMER_SET = "jp.gr.java_conf.SenseMusicClock.action.SLEEP_TIMER_SET"

        const val CUSTOM_ACTION_SLEEP_TIMER_CANCEL = "jp.gr.java_conf.SenseMusicClock.action.SLEEP_TIMER_CANCEL"

        const val CUSTOM_ACTION_SLEEP_TIMER_GET = "jp.gr.java_conf.SenseMusicClock.action.SLEEP_TIMER_GET"


        const val CUSTOM_ACTION_LOAD_ALBUM_BY_ID =
            "jp.gr.java_conf.SenseMusicClock.action.LOAD_ALBUM_BY_ID"
        const val CUSTOM_ACTION_LOAD_ARTIST_BY_ID =
            "jp.gr.java_conf.SenseMusicClock.action.LOAD_ARTIST_BY_ID"
        const val CUSTOM_ACTION_LOAD_ONE_MUSIC_BY_ID =
            "jp.gr.java_conf.SenseMusicClock.action.LOAD_ONE_MUSIC_BY_ID"

        const val SLEEP_TIMER_DURATION_MINUTES = "sleep_timer_duration_minutes"

        const val SLEEP_TIMER_END_AT_MILLIS = "sleep_timer_end_at_millis"
        const val ALBUM_ID = "jp.gr.java_conf.SenseMusicClock.action.LOAD_ONE_MUSIC_BY_ID"
        const val MUSIC_ID = "music_id"
        const val ARTIST_ID = "jp.gr.java_conf.SenseMusicClock.action.LOAD_ONE_MUSIC_BY_ID"

    }


    private lateinit var player: ExoPlayer

    private var session: MediaLibrarySession? = null


    // サービス用コルーチンスコープ
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var isPlayerFirstItemSeted = false

    private var sleepTimerJob : Job? = null

    private var sleepTimerEndAtMillis: Long? = null

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        session


    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return

            when (intent.action) {



                clickedPlay -> {
                    player.playWhenReady = !player.isPlaying
                }

                clickedNext -> {
                    player.seekToNext()
                }

                clickedPrev -> {
                    player.seekToPrevious()
                }

            }
        }
    }


    val callback = object : MediaLibrarySession.Callback {

        @OptIn(UnstableApi::class)
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            Log.d(
                "MusicService onPlaybackResumption",
                "onPlaybackResumption called for controller=${controller.packageName}, isForPlayback=$isForPlayback"
            )

            return CallbackToFutureAdapter.getFuture { completer ->
                scope.launch {


                    val lastTrack = findLastIndexByRepo()
                    val lastPosition = PrefsManager.getLastTrackPosition(this@MusicService)
                    try {

                        val result = if (lastTrack >= 0) {
                            Log.d(
                                "MusicService onPlaybackResumption",
                                "Resuming playback from last track index=$lastTrack"
                            )
                            MediaSession.MediaItemsWithStartPosition(
                                LocalMusicRepository.getTracks(),
                                lastTrack,
                                lastPosition
                            )

                        } else {
                            Log.d(
                                "MusicService onPlaybackResumption",
                                "No last track found, resuming from current media item"
                            )
                            val currentItem = player.currentMediaItem

                            if (currentItem != null) {
                                MediaSession.MediaItemsWithStartPosition(
                                    listOf(currentItem),
                                    0,
                                    0L
                                )
                            } else {
                                MediaSession.MediaItemsWithStartPosition(
                                    emptyList(),
                                    0,
                                    0L
                                )
                            }
                        }

                        completer.set(result)
                    } catch (e: Exception) {
                        Log.e("MusicService onPlaybackResumption", "Failed to resume playback", e)

                        completer.set(
                            MediaSession.MediaItemsWithStartPosition(
                                emptyList(),
                                0,
                                0L
                            )
                        )
                    }

                }
            }

        }

        // ① 本棚の入り口IDを定義
        @OptIn(UnstableApi::class)
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {

            Log.d(
                "MusicService",
                "onCustomCommand received: action=${customCommand.customAction} from controller=${controller.packageName}"
            )
            when (customCommand.customAction) {
                CUSTOM_ACTION_SLEEP_TIMER_SET ->{

                    val  minutes = args.getInt(SLEEP_TIMER_DURATION_MINUTES, 0)
                    Log.d("MusicService", "Received command to set sleep timer for $minutes minutes")
                    setSleepTimerByMinutes(minutes)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))


                }

                CUSTOM_ACTION_SLEEP_TIMER_CANCEL -> {
                    Log.d("MusicService", "Received command to cancel sleep timer")
                    cancelSleepTimer()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                CUSTOM_ACTION_SLEEP_TIMER_GET -> {
                    val timerMillis = if (sleepTimerEndAtMillis == null) {
                        Log.d("MusicService", "Received command to get sleep timer, but no timer is set")
                        System.currentTimeMillis()

                    } else {

                        sleepTimerEndAtMillis
                    }
                    val Bundle = Bundle().apply {
                        putLong(SLEEP_TIMER_END_AT_MILLIS, timerMillis ?: -1L)
                    }

                    Log.d("MusicService", "Returning sleep timer end time: $timerMillis")
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, Bundle))



                }
                CUSTOM_ACTION_LOAD_ALBUM_BY_ID -> {
                    val albumId = args.getLong(ALBUM_ID, -1L)
                    if (albumId == -1L) {
                        Log.w("MusicService", "Invalid album ID received: $albumId")
                    } else {
                        Log.d("MusicService", "Received command to load album with ID: $albumId")
                        scope.launch {

                            LocalMusicRepository.loadMusicAndSetTracksAndCreateMap_albumId(
                                this@MusicService,
                                albumId
                            )

                        }
                    }

                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                CUSTOM_ACTION_LOAD_ARTIST_BY_ID -> {
                    val artistId = args.getLong(ARTIST_ID, -1L)
                    Log.d("MusicService", "Received command to load artist with ID: $artistId")
                    if (artistId == -1L) {
                        Log.w("MusicService", "Invalid artist ID received: $artistId")
                    } else {
                        scope.launch {

                            LocalMusicRepository.loadMusicAndSetTracksAndCreateMap_artistId(
                                this@MusicService,
                                artistId
                            )
                        }
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                CUSTOM_ACTION_LOAD_ONE_MUSIC_BY_ID -> {
                    val musicId = args.getLong(MUSIC_ID, -1L)
                    Log.d("MusicService", "Received command to load music with ID: $musicId")
                    if (musicId == -1L) {
                        Log.w("MusicService", "Invalid music ID received: $musicId")
                    } else {
                        scope.launch {

                            LocalMusicRepository.loadMusicAndSetTracksAndCreateMap_Id(
                                this@MusicService,
                                musicId
                            )
                        }
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }

            return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))

        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            Log.d("MusicService", "onGetLibraryRoot called by controller=${browser.packageName}")
            return Futures.immediateFuture(
                LibraryResult.ofItem(
                    MediaItem.Builder()
                        .setMediaId("ROOT_ID")
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setIsBrowsable(true)
                                .setIsPlayable(false)
                                .build()
                        )
                        .build(), params
                )
            )
        }

        // ② 「1MBの壁」を回避するページング実装
        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            Log.d(
                "MusicService",
                "onGetChildren called parentId=$parentId page=$page pageSize=$pageSize controller=${browser.packageName}"
            )
            val allSongs = LocalMusicRepository.getTracks() // リポジトリの全曲リスト
            val fromIndex = page * pageSize
            val toIndex = minOf(fromIndex + pageSize, allSongs.size)

            return if (fromIndex < allSongs.size) {
                val pagedList = allSongs.subList(fromIndex, toIndex).map { it } // MediaItemに変換
                Futures.immediateFuture(LibraryResult.ofItemList(pagedList, params))
            } else {
                Futures.immediateFuture(LibraryResult.ofItemList(listOf(), params))
            }
        }

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            Log.d("MusicService", "onConnect called controller=${controller.packageName}")

            val connectionResult = super.onConnect(session, controller)
            val availableSessionCommands = connectionResult.availableSessionCommands.buildUpon()

            // ここでカスタムコマンドを明示的に追加する
            // 第2引数のBundleは空でOK
            availableSessionCommands.add(
                SessionCommand(CUSTOM_ACTION_LOAD_ARTIST_BY_ID, Bundle.EMPTY)

            )
            availableSessionCommands.add(
                SessionCommand(CUSTOM_ACTION_LOAD_ALBUM_BY_ID, Bundle.EMPTY)

            )
            availableSessionCommands.add(
                SessionCommand(CUSTOM_ACTION_LOAD_ONE_MUSIC_BY_ID, Bundle.EMPTY)

            )

            availableSessionCommands.add(
                SessionCommand(CUSTOM_ACTION_SLEEP_TIMER_SET, Bundle.EMPTY)
            )
            availableSessionCommands.add(
                SessionCommand(CUSTOM_ACTION_SLEEP_TIMER_CANCEL, Bundle.EMPTY)
            )
            availableSessionCommands.add(
                SessionCommand(CUSTOM_ACTION_SLEEP_TIMER_GET, Bundle.EMPTY)
            )

            return MediaSession.ConnectionResult.accept(
                availableSessionCommands.build(),
                connectionResult.availablePlayerCommands
            )

        }

    }


    override fun onCreate() {
        super.onCreate()
        Log.d("MusicService", "onCreate start")

        try {
            val attributionContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                createAttributionContext("sense_music_player")
            } else {
                this // Android 10以前はそのまま
            }


            player = ExoPlayer.Builder(attributionContext)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build()

            val intent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )





            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)

                .build()

            player.setAudioAttributes(audioAttributes, true)

            @UnstableApi
            session =
                MediaLibrarySession.Builder(this, player, callback)
                    .setSessionActivity(pendingIntent)
                    .setBitmapLoader(
                        @UnstableApi
                        BitmapLoaderForSession(this)
                    )
                    .build()

            player.addListener(object : Player.Listener {

                override fun onPlayerError(error: PlaybackException) {
                    super.onPlayerError(error)
                    Log.e(
                        "PlayerError",
                        "errorCode=${error.errorCodeName}",
                        error
                    )
                }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    super.onMediaItemTransition(mediaItem, reason)

                    Log.d(
                        "MusicService",
                        "Media item transition: newItem=${mediaItem?.mediaId} reason=$reason"
                    )

                    if (mediaItem == null) return

                    val title = mediaItem.mediaMetadata.title?.toString() ?: "Unknown Title"
                    val artwork : Uri? = mediaItem.mediaMetadata.artworkUri
                    val currentState = WidgetState(
                        title = title,
                        artwork = artwork,
                        playing = player.playWhenReady
                    )


                    scope.launch {
                        try {
                            Log.d(
                                "MusicService",
                                "[WidgetTrace] media transition widget coroutine start state=$currentState"
                            )
                            mediaItem.setLastInfos(0L)
                            Log.d(
                                "MusicService",
                                "[WidgetTrace] media transition setLastInfos done"
                            )
                            ControlWidgetUpdater.updateAllWidgets(this@MusicService, currentState)
                            Log.d(
                                "MusicService",
                                "[WidgetTrace] media transition updateAllWidgets done"
                            )
                        } catch (e: Exception) {
                            Log.e(
                                "MusicService",
                                "[WidgetTrace] media transition widget update failed",
                                e
                            )
                            throw e
                        }
                    }


                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    super.onPlayWhenReadyChanged(playWhenReady, reason)

                    Log.d(
                        "MusicService",
                        "PlayWhenReady changed: playWhenReady=$playWhenReady reason=$reason"
                    )

                    val title = player.mediaMetadata.title?.toString() ?: "Unknown Title"
                    val artwork : Uri? = player.mediaMetadata.artworkUri
                    val currentState = WidgetState(
                        title = title,
                        artwork = artwork,
                        playing = playWhenReady
                    )


                    scope.launch {
                        try {
                            Log.d(
                                "MusicService",
                                "[WidgetTrace] playWhenReady widget coroutine start state=$currentState"
                            )
                            ControlWidgetUpdater.updateAllWidgets(this@MusicService, currentState)
                            Log.d(
                                "MusicService",
                                "[WidgetTrace] playWhenReady updateAllWidgets done"
                            )
                        } catch (e: Exception) {
                            Log.e(
                                "MusicService",
                                "[WidgetTrace] playWhenReady widget update failed",
                                e
                            )
                            throw e
                        }
                    }
                }




            })


            Log.d("MusicService", "MediaLibrarySession created: session=$session")


            scope.launch {

                launch {
                    PrefsManager.getIsShuffleFlow(this@MusicService).collect { value ->
                        LocalMusicRepository.setShuffle(value, this@MusicService::class.simpleName)
                        repoLoadMusicAndCreateMap()
                    }
                }
                launch {
                    PrefsManager.getCurrentBlocklistIdFlow(this@MusicService).collect { value ->
                        Log.d("LIST_/MusicService/loadBlocklistItem", "collect blocklistId=$value")
                        val blocklistItems = DBManager.loadBlocklistItem(this@MusicService, value)
                        Log.d(
                            "LIST_/MusicService/loadBlocklistItem",
                            "loaded blocklist items=${blocklistItems.size} for id=$value"
                        )
                        LocalMusicRepository.setBlockItems(blocklistItems)
                        repoLoadMusicAndCreateMap()
                    }
                }


                launch {

                    PrefsManager.getCurrentPlaylistIdFlow(this@MusicService).collect { value ->
                        repoLoadMusicAndCreateMap(value)
                    }

                }

                // 音量設定の監視
                launch {

                    PrefsManager.getVolumeAdjustmentFlow(this@MusicService).collect { value ->
                        val vol = (value.coerceIn(0, 100)) / 100.0f
                        player.volume = vol
                        Log.d("MusicService", "Volume adjusted to $value -> $vol")
                    }

                }
                launch {

                    PrefsManager.getPlayModeLoopFlow(this@MusicService).collect { value ->
                        player.repeatMode =
                            if (value) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
                        Log.d(
                            "MusicService",
                            "Playmode loop set to $value -> repeatMode=${player.repeatMode}"
                        )

                    }
                }


                // ディレクトリリストの監視
                launch {

                    PrefsManager.getMusicDirRelativePathFlow(this@MusicService).collect { value ->
                        if (PrefsManager.getCurrentPlaylistId(this@MusicService) < 0) {
                            Log.d(
                                "MusicService",
                                "Music directory changed, reloading music. new dirs: $value"
                            )
                            repoLoadMusicAndCreateMap()
                        }
                    }
                }

                launch {

                    PrefsManager.getReloadTracksFlow(this@MusicService).collect {
                        Log.d(
                            "MusicService",
                            "reload music directory requested  "
                        )
                        initRepository()

                    }
                }

                launch {
                    LocalMusicRepository.tracksFlow.collect { value ->
                        player.setSafeMediaItems(value, isFirst = !isPlayerFirstItemSeted)
                        isPlayerFirstItemSeted = true
                    }
                }
                initRepository()


            }

        } catch (e: Exception) {
            Log.e("MusicService", "onCreate initialization failed", e)
        }
        Log.d("MusicService", "onCreate end")
    }



    override fun onDestroy() {
        Log.d("MusicService", "onDestroy start")
        try {
            unregisterReceiver(becomingNoisyReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w("MusicService", "Receiver already unregistered", e)
        }
        scope.launch {

            player.currentMediaItem?.setLastInfos(player.currentPosition)

            withContext(Dispatchers.Main) {
                try {
                    session?.run {
                        player.release()
                        release()
                        session = null
                    }

                    scope.cancel()
                } catch (e: Exception) {
                    Log.e("MusicService", "onDestroy cleanup failed", e)
                }
            }

        }

        super.onDestroy()
        Log.d("MusicService", "onDestroy end")
    }

    suspend fun repoLoadMusicAndCreateMap(playID: Long? = null) {
        val id = playID ?: PrefsManager.getCurrentPlaylistId(this)
        if (id == PlayList.ADDED_AT_DESC_ID) {
            Log.d(
                "LIST_/MusicService/loadPlaylistItem",
                "loading playlist items for ADDED_AT_DESC_ID"
            )


            LocalMusicRepository.loadMusicAndSetTracksAndCreateMap_addedAtDesc(
                context = this@MusicService,
                UserRelativePaths = TargetDirectoryPrefJSONManager.getAll(this@MusicService),
                isFilterByDir = PrefsManager.getIsFilterByDirAddedAtDesc(this@MusicService),
                isBlock = PrefsManager.getIsBlockAddedAtDesc(this@MusicService),
                limit = PrefsManager.getMaxLoadTracksAddedAtDesk(this@MusicService),
                useCurrentShuffleMode = PrefsManager.getUseCurrentShuffleModeAddedAtDesc(this@MusicService)
            )


        } else if (id == PlayList.CURRENT_REMOVAL_ID || id < 0) {
            val UserRelativePaths =
                TargetDirectoryPrefJSONManager.getAll(this)
            LocalMusicRepository.loadMusicAndSetTracksAndCreateMap(this, UserRelativePaths)

        } else {
            Log.d(
                "LIST_/MusicService/loadPlaylistItem",
                "loading playlist items for playlistId=$id"
            )
            val playlistItem = DBManager.loadPlaylistItem(this, id)
            Log.d(
                "LIST_/MusicService/loadPlaylistItem",
                "loaded playlist items=${playlistItem.size} for playlistId=$id"
            )
            LocalMusicRepository.loadMusicAndSetTracksAndCreateMap_playlist(
                this,
                playlistItem
            )
        }

    }

    suspend fun Player.setSafeMediaItems(List: List<MediaItem>, isFirst: Boolean = false) {
        val wasPlayWhenReady = withContext(Dispatchers.Main) { playWhenReady }


        val lastIndex = findLastIndexByRepo()
        val lastPosition = PrefsManager.getLastTrackPosition(this@MusicService)
        withContext(Dispatchers.Main) {
            this@setSafeMediaItems.clearMediaItems()
        }
        withContext(Dispatchers.Default) {
            val listSize = List.size
            Log.d("MusicService", "Setting media items to player, count=${listSize}")
            val pageSize = 900

            val page = (listSize + pageSize - 1) / pageSize // 切り上げでページ数を計算
            Log.d("MusicService", "Calculated pages: $page for pageSize=$pageSize")
            for (i in 0 until page) {
                val fromIndex = i * pageSize
                val toIndex = minOf(fromIndex + pageSize, listSize)
                val sublist = List.subList(fromIndex, toIndex)
                Log.d(
                    "MusicService",
                    "Adding media items to player: page=${i + 1}/$page, items=${sublist.size}"
                )
                withContext(Dispatchers.Main) {
                    addMediaItems(sublist)

                }
            }
        }
        withContext(Dispatchers.Main) {
            prepare()
            if (isFirst) {
                seekTo(lastIndex, lastPosition)
            }
            playWhenReady = wasPlayWhenReady
        }

    }

    suspend fun initRepository() {
        try {

            val blockListID = PrefsManager.getCurrentBlocklistId(this@MusicService)
            if (blockListID >= 0) {
                Log.d(
                    "LIST_/MusicService/loadBlocklistItem_sync",
                    "loading blocklist synchronously id=$blockListID"
                )
                val items = DBManager.loadBlocklistItem(this@MusicService, blockListID)
                Log.d(
                    "LIST_/MusicService/loadBlocklistItem_sync",
                    "loaded blocklist items=${items.size} for id=$blockListID"
                )
                LocalMusicRepository.setBlockItems(items)
            }
            LocalMusicRepository.setShuffle(
                PrefsManager.getIsShuffle(this@MusicService),
                this@MusicService::class.simpleName
            )
            repoLoadMusicAndCreateMap()

        } catch (e: Exception) {
            Log.w("MusicService", "failed to load local tracks", e)
        }
    }



    suspend fun findLastIndexByRepo(): Int {
        val lastDisPlayName =
            PrefsManager.getLastTrackDisplayName(this)
        val lastRelativePath =
            PrefsManager.getLastTrackRelativePath(this)
        val index = LocalMusicRepository.findIdxByPathAndName(
            lastRelativePath,
            lastDisPlayName
        )
        Log.d(
            "MusicService",
            "findIndexByRepo: lastDisplayName=$lastDisPlayName, lastRelativePath=$lastRelativePath, foundIndex=$index"
        )
        if (index >= 0) {
            return index
        } else {
            return 0
        }

    }

    suspend fun MediaItem.setLastInfos(pos: Long) {
        PrefsManager.setLastTrackDisplayName(
            this@MusicService,
            this.getDisplayName() ?: ""
        )
        PrefsManager.setLastTrackRelativePath(
            this@MusicService,
            this.getRelativePath() ?: ""
        )
        PrefsManager.setLastTrackPosition(this@MusicService, pos)
        Log.d(
            "MusicService",
            "setLastInfos: displayName=${this.getDisplayName()}, relativePath=${this.getRelativePath()}, position=$pos"
        )

    }
    fun setSleepTimerByMinutes(minutes: Int) {
        if (minutes <= 0) {
            cancelSleepTimer()
        } else {
            val durationMillis = minutes * 60 * 1000L
            setSleepTimer(durationMillis)

        }
    }
    fun setSleepTimer(durationMillis: Long) {
        cancelSleepTimer() // 既存のタイマーがあればキャンセル
        sleepTimerEndAtMillis = System.currentTimeMillis() + durationMillis
        sleepTimerJob = scope.launch {
            Log.d("MusicService", "Sleep timer started for $durationMillis ms")
            delay(durationMillis)
            Log.d("MusicService", "Sleep timer elapsed, stopping playback")
            player.pause()
            cancelSleepTimer() // タイマー終了後にジョブをクリア
        }
    }
    fun cancelSleepTimer() {
        sleepTimerEndAtMillis = null
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        Log.d("MusicService", "Sleep timer canceled")
    }

}


