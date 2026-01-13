// kotlin
package jp.gr.java_conf.SenseMusicClock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import android.util.Log

class Musicservice : Service() {
    inner class LocalBinder : Binder() {
        fun getService(): Musicservice = this@Musicservice
        // 追加: Activity 側で MediaSession トークンを取得できるようにする
        fun getSessionToken(): MediaSessionCompat.Token = mediaSession.sessionToken
    }

    private val binder = LocalBinder()
    private lateinit var player: ExoPlayer

    lateinit var Tracks: List<localTrack>
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: NotificationManager

    private var queue: List<localTrack> = emptyList()
    private var currentIndex: Int = 0

    // フォアグラウンド開始済みフラグ
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        Tracks = LocalMusicRepository.loadLocalMusicFromAppDir(this)

        player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSessionCompat(this, "MusicService").apply {
            setCallback(mediaSessionCallback)
            isActive = true
        }
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        // プレイヤー状態変化で通知更新
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlaybackState()
                startForegroundIfNeeded()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                updatePlaybackState()
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentIndex = player.currentMediaItemIndex
                updateMetadataForCurrent()
            }
        })
        setQueue(Tracks)
     }



    override fun onBind(intent: Intent?): IBinder? = binder

    override fun onDestroy() {
        player.release()
        mediaSession.release()
        stopForeground(true)
        super.onDestroy()
    }

    fun setQueue(tracks: List<localTrack>, startIndex: Int = 0) {
        queue = tracks
        currentIndex = when {
            tracks.isEmpty() -> 0
            else -> startIndex.coerceIn(0, tracks.size - 1)
        }
        Log.i("Musicservice", "setQueue: size=${tracks.size} startIndex=$currentIndex")
        player.stop()
        player.clearMediaItems()
        for (t in tracks) {
            t.uri?.let { uriStr ->
                val mediaItem = MediaItem.fromUri(uriStr)
                player.addMediaItem(mediaItem)
            }
        }
        player.prepare()
        if (player.mediaItemCount > 0) {
            player.seekTo(currentIndex, 0)
        }
        updateMetadataForCurrent()
    }

    fun play() {
        if (player.mediaItemCount == 0 && queue.isNotEmpty()) {
            setQueue(queue, currentIndex)
        }
        player.play()
    }

    fun pause() { player.pause() }
    fun stopPlayback() {
        player.stop()
        stopForeground(true)
    }

    fun skipToNext() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            currentIndex = player.currentMediaItemIndex
            updateMetadataForCurrent()
            play()
        }
    }

    fun skipToPrevious() {
        if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
            currentIndex = player.currentMediaItemIndex
            updateMetadataForCurrent()
            play()
        } else {
            player.seekTo(0)
        }
    }

    fun getCurrentTrack(): Track? = queue.getOrNull(currentIndex)

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() { play() }
        override fun onPause() { pause() }
        override fun onStop() { stopPlayback() }
        override fun onSkipToNext() { skipToNext() }
        override fun onSkipToPrevious() { skipToPrevious() }
        override fun onSeekTo(pos: Long) { player.seekTo(pos) }

        // 追加: カスタムアクションでインデックス指定再生を受ける
        override fun onCustomAction(action: String?, extras: Bundle?) {
            super.onCustomAction(action, extras)
            if (action == ACTION_PLAY_INDEX) {
                val index = extras?.getInt(EXTRA_INDEX, 0) ?: 0
                Log.i("Musicservice", "onCustomAction: play index $index")
                try {
                    // queue はサービス内部の現在の曲リスト（Tracks と同期済み）
                    setQueue(queue, index)
                    play()
                } catch (e: Exception) {
                    Log.w("Musicservice", "play by index failed", e)
                }
            }
        }
    }

    private fun updateMetadataForCurrent() {
        val track = getCurrentTrack()
        val metaBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track?.title ?: "")
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track?.artist ?: "")
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track?.album ?: "")
        mediaSession.setMetadata(metaBuilder.build())
        updatePlaybackState()
        startForegroundIfNeeded()
    }

    private fun updatePlaybackState() {
        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO
            )
        val state = if (player.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        stateBuilder.setState(state, player.currentPosition, 1.0f)
        mediaSession.setPlaybackState(stateBuilder.build())
        notificationManager.notify(NOTIFICATION_ID, buildNotification(player.isPlaying))
    }

    // 必要ならフォアグラウンド化（起動直後に呼ばれる）
    private fun startForegroundIfNeeded() {
        if (foregroundStarted) {
            // 既にフォアグラウンド化済みなら通知更新のみ
            notificationManager.notify(NOTIFICATION_ID, buildNotification(player.isPlaying))
            return
        }
        try {
            val notif = buildNotification(player.isPlaying)
            startForeground(NOTIFICATION_ID, notif)
            foregroundStarted = true
            Log.i("Musicservice", "startForeground called to avoid ANR")
        } catch (e: Exception) {
            Log.w("Musicservice", "failed to startForeground", e)
        }
    }

    private fun buildNotification(isPlaying: Boolean): android.app.Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_IMMUTABLE else 0
        )

        val playPauseAction = if (isPlaying)
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause, "Pause",
                buildActionPendingIntent(ACTION_PAUSE)
            )
        else
            NotificationCompat.Action(
                android.R.drawable.ic_media_play, "Play",
                buildActionPendingIntent(ACTION_PLAY)
            )

        val prevAction = NotificationCompat.Action(
            android.R.drawable.ic_media_previous, "Previous",
            buildActionPendingIntent(ACTION_PREV)
        )
        val nextAction = NotificationCompat.Action(
            android.R.drawable.ic_media_next, "Next",
            buildActionPendingIntent(ACTION_NEXT)
        )

        val metadata = mediaSession.controller.metadata
        val title = metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: "Unknown"
        val artist = metadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: ""

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingOpen)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .setStyle(MediaStyle().setMediaSession(mediaSession.sessionToken).setShowActionsInCompactView(1))

        return builder.build()
    }

    private fun buildActionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, javaClass).apply { this.action = action }
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // サービス起動時に即座にフォアグラウンド化して ANR を回避
        if (!foregroundStarted) {
            startForegroundIfNeeded()
        }

        intent?.action?.let { act ->
            when (act) {
                ACTION_PLAY -> mediaSession.controller.transportControls.play()
                ACTION_PAUSE -> mediaSession.controller.transportControls.pause()
                ACTION_NEXT -> mediaSession.controller.transportControls.skipToNext()
                ACTION_PREV -> mediaSession.controller.transportControls.skipToPrevious()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(chan)
        }
    }

    companion object {
        private const val CHANNEL_ID = "music_playback_channel"
        private const val NOTIFICATION_ID = 1

        const val ACTION_PLAY = "jp.gr.java_conf.SenseMusicClock.ACTION_PLAY"
        const val ACTION_PAUSE = "jp.gr.java_conf.SenseMusicClock.ACTION_PAUSE"
        const val ACTION_NEXT = "jp.gr.java_conf.SenseMusicClock.ACTION_NEXT"
        const val ACTION_PREV = "jp.gr.java_conf.SenseMusicClock.ACTION_PREV"
        const val ACTION_PLAY_INDEX = "jp.gr.java_conf.SenseMusicClock.ACTION_PLAY_INDEX"
        const val EXTRA_INDEX = "jp.gr.java_conf.SenseMusicClock.EXTRA_INDEX"

    }
}