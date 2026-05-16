// kotlin
package jp.gr.java_conf.SenseMusicClock


import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.work.impl.utils.PREFERENCE_FILE_KEY
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import jp.gr.java_conf.SenseMusicClock.Music.BitmapLoaderForSession
import jp.gr.java_conf.SenseMusicClock.Music.Data.DBManager
import jp.gr.java_conf.SenseMusicClock.Music.LocalMusicFetcher
import jp.gr.java_conf.SenseMusicClock.Music.LocalMusicFetcher.toMediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import jp.gr.java_conf.SenseMusicClock.Music.TargetDirectoryPrefJSONManager
import jp.gr.java_conf.SenseMusicClock.ui.MainActivity
import jp.gr.java_conf.SenseMusicClock.ui.list.ListsActivity
import kotlinx.coroutines.withContext


class MusicService : MediaLibraryService() {

    companion object {


        const val CUSTOM_ACTION_LOAD_ALBUM_BY_ID =
            "jp.gr.java_conf.SenseMusicClock.action.LOAD_ALBUM_BY_ID"
        const val CUSTOM_ACTION_LOAD_ARTIST_BY_ID =
            "jp.gr.java_conf.SenseMusicClock.action.LOAD_ARTIST_BY_ID"
        const val CUSTOM_ACTION_LOAD_ONE_MUSIC_BY_ID =
            "jp.gr.java_conf.SenseMusicClock.action.LOAD_ONE_MUSIC_BY_ID"
        const val ALBUM_ID = "jp.gr.java_conf.SenseMusicClock.action.LOAD_ONE_MUSIC_BY_ID"
        const val MUSIC_ID = "music_id"
        const val ARTIST_ID = "jp.gr.java_conf.SenseMusicClock.action.LOAD_ONE_MUSIC_BY_ID"

    }


    private lateinit var player: ExoPlayer

    private var session: MediaLibrarySession? = null


    // サービス用コルーチンスコープ
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + serviceJob)


    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        session


    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent?.action) {
                player.pause()
            }
        }
    }


    val callback = object : MediaLibrarySession.Callback {
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
                CUSTOM_ACTION_LOAD_ALBUM_BY_ID -> {
                    val albumId = args.getLong(ALBUM_ID, -1L)
                    if (albumId == -1L) {
                        Log.w("MusicService", "Invalid album ID received: $albumId")
                    } else {
                        Log.d("MusicService", "Received command to load album with ID: $albumId")
                        scope.launch {

                            LocalMusicRepository.loadLocalMusicAndSetTracksAndCreateMap_albumId(
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

                            LocalMusicRepository.loadLocalMusicAndSetTracksAndCreateMap_artistId(
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

                            LocalMusicRepository.loadLocalMusicAndSetTracksAndCreateMap_Id(
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


            player = ExoPlayer.Builder(attributionContext).build()
            val intent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            // 追加: 再生中にCPUがスリープするのを防ぐ
            player.setWakeMode(C.WAKE_MODE_LOCAL)


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

            Log.d("MusicService", "MediaLibrarySession created: session=$session")
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            registerReceiver(becomingNoisyReceiver, filter)
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
                        repoLoadMusicAndCreateMap()

                    }
                }

                launch {
                    LocalMusicRepository.tracksFlow.collect { value ->
                        player.setSafeMediaItems(value)
                    }
                }
                try {
                    if (LocalMusicRepository.getTracks().isEmpty()) {
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
                    }
                } catch (e: Exception) {
                    Log.w("MusicService", "failed to load local tracks", e)
                }


            }

        } catch (e: Exception) {
            Log.e("MusicService", "onCreate initialization failed", e)
        }
        Log.d("MusicService", "onCreate end")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(
            "MusicService",
            "onStartCommand action=${intent?.action} flags=$flags startId=$startId"
        )
        return try {
            super.onStartCommand(intent, flags, startId)
            Log.d("MusicService", "onStartCommand processed")
            START_STICKY
        } catch (e: Exception) {
            Log.e("MusicService", "onStartCommand exception", e)
            START_STICKY
        }

    }


    override fun onDestroy() {
        Log.d("MusicService", "onDestroy start")
        try {
            session?.run {
                player.release()
                release()
                session = null
            }
            unregisterReceiver(becomingNoisyReceiver)
            scope.cancel()
        } catch (e: Exception) {
            Log.e("MusicService", "onDestroy cleanup failed", e)
        }


        super.onDestroy()
        Log.d("MusicService", "onDestroy end")
    }

    suspend fun repoLoadMusicAndCreateMap(playID: Long? = null) {
        val id = playID ?: PrefsManager.getCurrentPlaylistId(this)
        if (DUMMY_PLAYLIST_REMOVAL_ID == id || id < 0) {
            val UserRelativePaths =
                TargetDirectoryPrefJSONManager(this@MusicService).getAll()
            LocalMusicRepository.loadLocalMusicAndSetTracksAndCreateMap(this, UserRelativePaths)

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
            LocalMusicRepository.loadLocalMusicAndSetTracksAndCreateMap_playlist(
                this,
                playlistItem
            )
        }

    }

    suspend fun Player.setSafeMediaItems(List: List<MediaItem>) {

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
            this@setSafeMediaItems.prepare()
            this@setSafeMediaItems.play()
            this@setSafeMediaItems.pause()
        }


    }

}




