package jp.gr.java_conf.SenseMusicClock.Music

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import jp.gr.java_conf.SenseMusicClock.LocalMusicRepository
import jp.gr.java_conf.SenseMusicClock.Music.Data.DBManager
import jp.gr.java_conf.SenseMusicClock.Music.Data.Playlists.PlayList
import jp.gr.java_conf.SenseMusicClock.PrefsManager
import jp.gr.java_conf.SenseMusicClock.ui.MainActivity
import jp.gr.java_conf.SenseMusicClock.ui.Widget.ControlWidgetUpdater
import jp.gr.java_conf.SenseMusicClock.ui.Widget.WidgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicService : MediaLibraryService() {

    companion object {

        const val CUSTOM_ACTION_SLEEP_TIMER_SET =
            "jp.gr.java_conf.SenseMusicClock.action.SLEEP_TIMER_SET"

        const val CUSTOM_ACTION_SLEEP_TIMER_CANCEL =
            "jp.gr.java_conf.SenseMusicClock.action.SLEEP_TIMER_CANCEL"

        const val CUSTOM_ACTION_SLEEP_TIMER_GET =
            "jp.gr.java_conf.SenseMusicClock.action.SLEEP_TIMER_GET"


        const val CUSTOM_ACTION_LOAD_ALBUM_BY_ID =
            "jp.gr.java_conf.SenseMusicClock.action.LOAD_ALBUM_BY_ID"
        const val CUSTOM_ACTION_LOAD_ARTIST_BY_ID =
            "jp.gr.java_conf.SenseMusicClock.action.LOAD_ARTIST_BY_ID"
        const val CUSTOM_ACTION_LOAD_ONE_MUSIC_BY_ID =
            "jp.gr.java_conf.SenseMusicClock.action.LOAD_ONE_MUSIC_BY_ID"

        const val CUSTOM_ACTION_WIDGET_PLAY =
            "jp.gr.java_conf.SenseMusicClock.action.WIDGET_PLAY"

        const val SLEEP_TIMER_DURATION_MINUTES = "sleep_timer_duration_minutes"

        const val SLEEP_TIMER_END_AT_MILLIS = "sleep_timer_end_at_millis"
        const val ALBUM_ID = "jp.gr.java_conf.SenseMusicClock.action.LOAD_ONE_MUSIC_BY_ID"
        const val MUSIC_ID = "music_id"
        const val ARTIST_ID = "jp.gr.java_conf.SenseMusicClock.action.LOAD_ONE_MUSIC_BY_ID"


        @Volatile
        var isRunning: Boolean = false
            private set


    }


    private lateinit var player: ExoPlayer

    private var session: MediaLibrarySession? = null

    // サービス用コルーチンスコープ
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var sleepTimerJob: Job? = null

    private var sleepTimerEndAtMillis: Long? = null

    private var initialRepositoryJob: Job? = null

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        session


    val callback = @OptIn(UnstableApi::class)
    object : MediaLibrarySession.Callback {

        // ① 本棚の入り口IDを定義

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
                CUSTOM_ACTION_SLEEP_TIMER_SET -> {

                    val minutes = args.getInt(SLEEP_TIMER_DURATION_MINUTES, 0)
                    Log.d(
                        "MusicService",
                        "Received command to set sleep timer for $minutes minutes"
                    )
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
                        Log.d(
                            "MusicService",
                            "Received command to get sleep timer, but no timer is set"
                        )
                        System.currentTimeMillis()

                    } else {

                        sleepTimerEndAtMillis
                    }
                    val Bundle = Bundle().apply {
                        putLong(SLEEP_TIMER_END_AT_MILLIS, timerMillis ?: -1L)
                    }

                    Log.d("MusicService", "Returning sleep timer end time: $timerMillis")
                    return Futures.immediateFuture(
                        SessionResult(
                            SessionResult.RESULT_SUCCESS,
                            Bundle
                        )
                    )


                }

                CUSTOM_ACTION_WIDGET_PLAY -> {
                    return playFirstTrackFromWidget()
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

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            return CallbackToFutureAdapter.getFuture { completer ->
                scope.launch {
                    try {
                        val loadJob = initialRepositoryJob
                            ?: throw IllegalStateException("Initial music loading was not started")
                        loadJob.join()
                        val tracks = LocalMusicRepository.getTracks()
                        if (tracks.isEmpty()) {
                            throw IllegalStateException("No local tracks available for playback resumption")
                        }
                        val items = if (isForPlayback) tracks else tracks.take(1)
                        completer.set(MediaSession.MediaItemsWithStartPosition(items, 0, 0L))
                    } catch (e: Exception) {
                        Log.e("MusicService", "Failed to load tracks for playback resumption", e)
                        completer.setException(e)
                    }
                }
                "onPlaybackResumption"
            }
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
            val allSongs = LocalMusicRepository.getTracks()
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
            availableSessionCommands.add(
                SessionCommand(CUSTOM_ACTION_WIDGET_PLAY, Bundle.EMPTY)
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
            val attributionContext =
                createAttributionContext("sense_music_player")


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
                        (BitmapLoaderForSession(this))
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
                    val artwork: Uri? = mediaItem.mediaMetadata.artworkUri
                    val currentState = WidgetState(
                        title = title,
                        artwork = artwork,
                        playing = player.playWhenReady
                    )


                    scope.launch {
                        try {
                            ControlWidgetUpdater.updateAllWidgets(this@MusicService, currentState)
                        } catch (e: Exception) {
                            Log.e(
                                "MusicService",
                                "Failed to update widget after media transition",
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
                    val artwork: Uri? = player.mediaMetadata.artworkUri
                    val currentState = WidgetState(
                        title = title,
                        artwork = artwork,
                        playing = playWhenReady
                    )


                    scope.launch {
                        try {
                            ControlWidgetUpdater.updateAllWidgets(this@MusicService, currentState)
                        } catch (e: Exception) {
                            Log.e(
                                "MusicService",
                                "Failed to update widget after playback state change",
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
                    PrefsManager.getIsShuffleFlow(this@MusicService).drop(1).collect { value ->
                        LocalMusicRepository.setShuffle(value, this@MusicService::class.simpleName)
                        repoLoadMusicAndCreateMap()
                    }
                }
                launch {
                    PrefsManager.getCurrentBlocklistIdFlow(this@MusicService).distinctUntilChanged()
                        .drop(1).collect { value ->
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

                    PrefsManager.getCurrentPlaylistIdFlow(this@MusicService).drop(1)
                        .distinctUntilChanged().collect { value ->
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
                            "PlayMode loop set to $value -> repeatMode=${player.repeatMode}"
                        )

                    }
                }


                // ディレクトリリストの監視
                launch {

                    PrefsManager.getMusicDirRelativePathFlow(this@MusicService).drop(1)
                        .collect { value ->
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

                    PrefsManager.getReloadTracksFlow(this@MusicService).drop(1)
                        .distinctUntilChanged().collect {
                        Log.d(
                            "MusicService",
                            "reload music directory requested  "
                        )
                        initRepository()

                    }
                }

                launch {
                    LocalMusicRepository.tracksFlow.collect { value ->
                        if (value.isEmpty()) {
                            Log.w(
                                "MusicService",
                                "Tracks list is empty, skipping setSafeMediaItems"
                            )
                            return@collect
                        }
                        player.setSafeMediaItems(
                            value
                        )
                    }
                }
            }

            initialRepositoryJob = scope.launch { initRepository() }

        } catch (e: Exception) {
            Log.e("MusicService", "onCreate initialization failed", e)
        } finally {
            isRunning = true
        }
        Log.d("MusicService", "onCreate end")
    }


    override fun onDestroy() {
        Log.d("MusicService", "onDestroy start")

        try {
            session?.run {
                player.release()
                release()
                session = null
            }

            scope.cancel()
        } catch (e: Exception) {
            Log.e("MusicService", "onDestroy cleanup failed", e)
        } finally {
            isRunning = false
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

    suspend fun Player.setSafeMediaItems(List: List<MediaItem>) {
        val wasPlayWhenReady = withContext(Dispatchers.Main) { playWhenReady }

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


    private fun playFirstTrackFromWidget(): ListenableFuture<SessionResult> {
        return CallbackToFutureAdapter.getFuture { completer ->
            scope.launch {
                try {
                    if (LocalMusicRepository.getTracks().isEmpty()) {
                        initRepository()
                    }

                    withContext(Dispatchers.Main) {
                        if (player.mediaItemCount == 0) {
                            player.setSafeMediaItems(LocalMusicRepository.getTracks())
                        }
                        if (player.mediaItemCount > 0) {
                            player.play()
                        }
                    }

                    completer.set(SessionResult(SessionResult.RESULT_SUCCESS))
                } catch (e: Exception) {
                    Log.e("MusicService", "Failed to play first track from widget", e)
                    completer.set(SessionResult(SessionError.ERROR_UNKNOWN))
                }
            }

            "playFirstTrackFromWidget"
        }
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
