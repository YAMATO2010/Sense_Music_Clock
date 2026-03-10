// kotlin
package jp.gr.java_conf.SenseMusicClock


import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import jp.gr.java_conf.SenseMusicClock.Music.BitmapLoaderForSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import jp.gr.java_conf.SenseMusicClock.Music.TargetDirectoryPrefJSONManager
import kotlinx.coroutines.withContext


class MusicService : MediaLibraryService() {


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
            return super.onConnect(session, controller)
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
                        player.repeatMode = if (value) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
                        Log.d(
                            "MusicService",
                            "Playmode loop set to $value -> repeatMode=${player.repeatMode}"
                        )

                    }
                }


                // ディレクトリリストの監視
                launch {

                    PrefsManager.getMusicDirRelativePathFlow(this@MusicService).collect { value ->
                        val UserRelativePaths =
                            TargetDirectoryPrefJSONManager(this@MusicService).getAll()
                        LocalMusicRepository.loadLocalMusicAndSetTracksAndCreateMap(
                            this@MusicService,
                            UserRelativePaths
                        )
                    }
                }

                launch {

                    PrefsManager.getReloadTracksFlow(this@MusicService).collect {
                        Log.d(
                            "MusicService",
                            "reload music directory requested  "
                        )
                        val UserRelativePaths =
                            TargetDirectoryPrefJSONManager(this@MusicService).getAll()
                        LocalMusicRepository.loadLocalMusicAndSetTracksAndCreateMap(
                            this@MusicService,
                            UserRelativePaths
                        )

                    }
                }

                launch {
                    LocalMusicRepository.tracksFlow.collect { value ->
                        withContext(Dispatchers.Main) {
                            player.setMediaItems(value)
                        }
                    }
                }
                try {
                    if (LocalMusicRepository.getTracks().isEmpty()) {
                        val UserRelativePaths =
                            TargetDirectoryPrefJSONManager(this@MusicService).getAll()
                        LocalMusicRepository.loadLocalMusicAndSetTracksAndCreateMap(
                            this@MusicService,
                            UserRelativePaths
                        )
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


    fun play() {

        if (player.mediaItemCount == 0) {
            Log.d(
                "MusicService",
                "play: setting queue before play./currentIndex=${player.currentMediaItemIndex}" + IDENTIFIER_INITIAL_INDEX_PROBLEM
            )

        }

        Log.d(
            "player_beforePlay",
            " playerCurrentIndex: ${player.currentMediaItemIndex}" + IDENTIFIER_INITIAL_INDEX_PROBLEM
        )

        player.play()
        Log.d(
            "player",
            " playerCurrentIndex: ${player.currentMediaItemIndex}" + IDENTIFIER_INITIAL_INDEX_PROBLEM
        )
    }

    fun pause() {
        player.pause()
    }


    fun skipToNext() {

        if (player.hasNextMediaItem()) {

            player.seekToNext()


        }
    }

    fun skipToPrevious() {

        if (player.hasPreviousMediaItem() && player.currentPosition < 7000) {
            player.seekToPrevious()


        } else {
            player.seekTo(0)
        }
    }


    // 必要ならフォアグラウンド化（起動直後に呼ばれる）


    private fun createNotificationChannel() {


    }


    companion object {

        const val ACTION_PLAY = "jp.gr.java_conf.SenseMusicClock.ACTION_PLAY"
        const val ACTION_PAUSE = "jp.gr.java_conf.SenseMusicClock.ACTION_PAUSE"
        const val ACTION_NEXT = "jp.gr.java_conf.SenseMusicClock.ACTION_NEXT"
        const val ACTION_PREV = "jp.gr.java_conf.SenseMusicClock.ACTION_PREV"
        const val ACTION_PLAY_INDEX = "jp.gr.java_conf.SenseMusicClock.ACTION_PLAY_INDEX"

        const val EXTRA_INDEX = "jp.gr.java_conf.SenseMusicClock.EXTRA_INDEX"


    }


}
