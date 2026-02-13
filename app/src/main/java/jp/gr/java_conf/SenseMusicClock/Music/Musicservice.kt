// kotlin
package jp.gr.java_conf.SenseMusicClock

import IDENTIFIER_INITIAL_INDEX_PROBLEM

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import android.util.Log
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.ui.PlayerNotificationManager
import coil.imageLoader
import coil.request.ImageRequest
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import jp.gr.java_conf.SenseMusicClock.Music.SharedPrefsFlow
import jp.gr.java_conf.SenseMusicClock.Music.TargetDirectoryManager
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext


class Musicservice : MediaLibraryService() {


    private lateinit var player: ExoPlayer


    private var session: MediaLibrarySession? = null


    // サービス用コルーチンスコープ
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + serviceJob)


    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        session


    val callback = object : MediaLibrarySession.Callback {
        // ① 本棚の入り口IDを定義
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ) = Futures.immediateFuture(
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

        // ② 「1MBの壁」を回避するページング実装
        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
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

    }




    override fun onCreate() {
        super.onCreate()


        player = ExoPlayer.Builder(this).build()




        createNotificationChannel()


        session = MediaLibrarySession.Builder(this, player, callback).build()


        // プレイヤー状態変化で通知更新
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {


            }

            override fun onPlaybackStateChanged(playbackState: Int) {

            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {


            }
        })




        scope.launch {

            // Use the app's named SharedPreferences (same as TargetDirectoryManager)
            val prefs = this@Musicservice.getSharedPreferences(
                getString(SHAREDPREFERENCES_NAME),
                MODE_PRIVATE
            )
            Log.d("Musicservice", "prefs obtained (default) hash=${prefs.hashCode()}")
            // デバッグ: 現在の SharedPreferences 全エントリを出力（キーと値の型を確認する）
            Log.d("Musicservice", "prefs.all = ${prefs.all}")


            // 音量設定の監視
            launch {
                Log.d(
                    "Musicservice",
                    "launching observeInt collector for key=${getString(VOLUME_ADJUSTMENT)}"
                )
                SharedPrefsFlow.observeInt(prefs, getString(VOLUME_ADJUSTMENT)).collect { value ->
                    val vol = (value.coerceIn(0, 100)) / 100.0f
                    player.volume = vol
                    Log.i("Musicservice", "Volume adjusted to $value -> $vol")
                }
            }
            launch {
                SharedPrefsFlow.observeBoolean(prefs, getString(PLAYMODE_LOOP_KEY))
                    .collect { value ->
                        if (value == null) return@collect
                        player.repeatMode =
                            if (value) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
                        Log.i(
                            "Musicservice",
                            "Playmode loop set to $value -> repeatMode=${player.repeatMode}"
                        )
                    }
            }


            // ディレクトリリストの監視
            launch {
                Log.d(
                    "Musicservice",
                    "launching observeString collector for key=${
                        getString(MUSIC_DIR_RELATIVE_PATHS_KEY)
                    }"
                )
                SharedPrefsFlow.observeString(prefs, getString(MUSIC_DIR_RELATIVE_PATHS_KEY))
                    .collect { value ->
                        Log.d("Musicservice", "observeString.collect emitted value=$value")
                        val UserRelativePaths = TargetDirectoryManager(this@Musicservice).getAll()
                        LocalMusicRepository.loadLocalMusicAndSetTracksAndCreateMap(this@Musicservice, UserRelativePaths)

                    }
            }

            launch {
                SharedPrefsFlow.observeBoolean(prefs, getString(ReLoad_Tracks_KEY), false)
                    .onStart {
                        Log.d(
                            "Musicservice",
                            "launching observeBoolean collector for key=${
                                getString(
                                    ReLoad_Tracks_KEY
                                )
                            }"
                        )
                    }
                    .collect { _ ->
                        Log.i(
                            "Musicservice",
                            "reload music directory requested (prefs key=${
                                getString(ReLoad_Tracks_KEY)
                            }) -> reloading"
                        )
                        val UserRelativePaths = TargetDirectoryManager(this@Musicservice).getAll()
                        LocalMusicRepository.loadLocalMusicAndSetTracksAndCreateMap(this@Musicservice, UserRelativePaths)

                    }
            }

            launch {
                LocalMusicRepository.tracksFlow.collect { value ->
                    withContext(Dispatchers.Main){
                        player.setMediaItems(value)
                    }
                }
            }
            try {
                val UserRelativePaths = TargetDirectoryManager(this@Musicservice).getAll()
                LocalMusicRepository.loadLocalMusicAndSetTracksAndCreateMap(this@Musicservice, UserRelativePaths)
            } catch (e: Exception) {
                Log.w("Musicservice", "failed to load local tracks", e)
            }


        }


    }


    override fun onDestroy() {
        session?.run {
            player.release()
            release()
            session = null
        }
        scope.cancel()


        super.onDestroy()
    }


    fun play() {

        if (player.mediaItemCount == 0) {
            Log.i(
                "Musicservice",
                "play: setting queue before play./currentIndex=${player.currentMediaItemIndex}" + IDENTIFIER_INITIAL_INDEX_PROBLEM
            )

        }

        Log.i(
            "player_beforePlay",
            " playerCurrentIndex: ${player.currentMediaItemIndex}" + IDENTIFIER_INITIAL_INDEX_PROBLEM
        )

        player.play()
        Log.i(
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


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        intent?.action?.let { act ->
            when (act) {
                ACTION_PLAY -> play()
                ACTION_PAUSE -> pause()
                ACTION_NEXT -> skipToNext()
                ACTION_PREV -> skipToPrevious()
            }
        }
        return START_STICKY
    }

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
