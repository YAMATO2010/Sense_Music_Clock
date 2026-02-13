package jp.gr.java_conf.SenseMusicClock

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.MediaMetadataCompat
import androidx.appcompat.app.AppCompatActivity
import jp.gr.java_conf.SenseMusicClock.databinding.ActivityStandardPlayerBinding
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmapOrNull
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.imageLoader
import coil.load
import coil.request.ImageRequest
import jp.gr.java_conf.SenseMusicClock.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StandardPlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStandardPlayerBinding


    // service binding


    private lateinit var token: SessionToken

    private var mediaController: MediaController? = null


    private val uiHandler = Handler(Looper.getMainLooper())
    private var progressUpdaterScheduled = false


    private val progressUpdateRunnable = object : Runnable {
        override fun run() {
            try {
                val durationMs = mediaController?.duration ?: 0L
                val posMs = mediaController?.currentPosition ?: 0L

                val durationSec = (durationMs / 1000L).coerceAtLeast(0L).toInt()
                val posSec = (posMs / 1000L).toInt()

                val sb = binding.seekBar
                // binding.seekBar is non-null via viewBinding
                if (durationSec > 0 && sb.max != durationSec) sb.max = durationSec
                sb.progress = posSec.coerceIn(0, (sb.max))

            } catch (e: Exception) {
                android.util.Log.w("StandardPlayerActivity", "progress update failed", e)
            }
            if (progressUpdaterScheduled) uiHandler.postDelayed(this, 500)
        }
    }


    /* TODO : ServiceConnectionのところ

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? Musicservice.LocalBinder ?: return
            musicService = binder.getService()
            musicBound = true
            try {
                val token = binder.getSessionToken()
                val mc = MediaControllerCompat(this@StandardPlayerActivity, token)
                MediaControllerCompat.setMediaController(this@StandardPlayerActivity, mc)
                mediaController = mc
                mc.registerCallback(controllerCallback)
                // initial sync
                controllerCallback.onMetadataChanged(mc.metadata)
                controllerCallback.onPlaybackStateChanged(mc.playbackState)
            } catch (e: Exception) {
                android.util.Log.w("StandardPlayerActivity", "create media controller failed", e)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicBound = false
            musicService = null
            try { mediaController?.unregisterCallback(controllerCallback) } catch (e: Exception) { android.util.Log.w("StandardPlayerActivity", "unregister callback failed", e) }
            mediaController = null
        }
    }

     */
    /*

    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            runOnUiThread {
                val playing = state?.state == PlaybackStateCompat.STATE_PLAYING
                animatePlayButton(playing)
                if (playing) startProgressUpdates() else stopProgressUpdates()
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            runOnUiThread {
                try {
                    val title = metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: ""
                    val artist = metadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: ""
                    val album = metadata?.getString(MediaMetadataCompat.METADATA_KEY_ALBUM) ?: ""
                    binding.tvTitle.text = title
                    binding.tvArtist.text = artist
                    binding.tvAlbumName.text = album

                    val art = metadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
                        ?: android.graphics.BitmapFactory.decodeResource(resources, R.drawable.default_album_art)

                    binding.ivAlbumArt.setImageBitmap(art)

                    binding.root.background =getDrawble_forRootBackgroundByTimeAndOrientation(resources.configuration.orientation,this@StandardPlayerActivity)

                } catch (e: Exception) {
                    android.util.Log.w("StandardPlayerActivity", "metadata update failed", e)
                }
            }
        }
    }

     */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStandardPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        token = SessionToken(this, ComponentName(this, Musicservice::class.java))
        binding.bgImageView.load_forRoot(this@StandardPlayerActivity,resources.configuration.orientation)


        val controllerFuture = MediaController.Builder(this, token).buildAsync()


        controllerFuture.addListener({
            mediaController = controllerFuture.get()
            mediaController?.let {


                runOnUiThread {
                    try {
                        animatePlayButton(it.playWhenReady)
                        val title = it.mediaMetadata.title ?: ""
                        val artist = it.mediaMetadata.artist ?: ""
                        val album = it.mediaMetadata.albumTitle ?: ""
                        binding.tvTitle.text = title
                        binding.tvArtist.text = artist
                        binding.tvAlbumName.text = album


                        binding.ivAlbumArt.load(it.mediaMetadata.artworkUri) {
                            placeholder(R.drawable.default_album_art)
                            error(R.drawable.default_album_art)
                            crossfade(true)
                        }

                        startProgressUpdates()


                    } catch (e: Exception) {
                        android.util.Log.w("StandardPlayerActivity", "metadata update failed", e)
                    }
                }





                it.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {

                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {

                    }


                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        runOnUiThread {
                            try {
                                val title = mediaItem?.mediaMetadata?.title ?: ""
                                val artist = mediaItem?.mediaMetadata?.artist ?: ""
                                val album = mediaItem?.mediaMetadata?.albumTitle ?: ""
                                binding.tvTitle.text = title
                                binding.tvArtist.text = artist
                                binding.tvAlbumName.text = album


                                binding.ivAlbumArt.load(mediaItem?.mediaMetadata?.artworkUri) {
                                    placeholder(R.drawable.default_album_art)
                                    error(R.drawable.default_album_art)
                                    crossfade(true)
                                }

                                binding.bgImageView.load_forRoot(this@StandardPlayerActivity,resources.configuration.orientation)

                                val durationMs = mediaItem?.mediaMetadata?.durationMs ?: 0L
                                if (durationMs > 0) binding.seekBar.max =
                                    (durationMs / 1000L).toInt()
                            } catch (e: Exception) {
                                android.util.Log.w(
                                    "StandardPlayerActivity",
                                    "metadata update failed",
                                    e
                                )
                            }
                        }

                    }
                })
            }
        }, ContextCompat.getMainExecutor(this))


        // back button (edge) to return to MainActivity
        binding.btnBackEdge.setOnClickListener { finish() }





        binding.btnPrev.setOnClickListener {
            try {
                mediaController?.seekToPrevious()
            } catch (e: Exception) {
                android.util.Log.w("StandardPlayerActivity", "skipToPrevious failed", e)
            }
        }

        binding.btnNext.setOnClickListener {
            try {
                mediaController?.seekToNext()
            } catch (e: Exception) {
                android.util.Log.w("StandardPlayerActivity", "skipToNext failed", e)
            }
        }

        binding.btnPlayPause.setOnClickListener {
            val isPlaying = mediaController?.playWhenReady == true
            animatePlayButton(!isPlaying)
            if (isPlaying) {
                try {
                    mediaController?.pause()
                } catch (e: Exception) {
                    android.util.Log.w("StandardPlayerActivity", "pause failed", e)
                }
            } else {
                try {
                    mediaController?.play()
                } catch (e: Exception) {
                    android.util.Log.w("StandardPlayerActivity", "play failed", e)
                }
            }
        }


        val orientation = resources.configuration.orientation

        binding.scrimOverlay.background = ColorDrawable(getColor(R.color.black_overlay))
        binding.bgImageView.load_forRoot(this@StandardPlayerActivity,orientation)





        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    try {
                        // convert progress to percent of duration and ask service to seek
                        val max = seekBar?.max ?: 0
                        if (max > 0) {
                            val percent = (progress.toFloat() / max.toFloat())
                            val positionMs: Long =
                                ((mediaController?.duration ?: 0L) * percent).toLong()
                            mediaController?.seekTo(positionMs)
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("StandardPlayerActivity", "seek handling failed", e)
                    }
                }
            }


            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                stopProgressUpdates()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (mediaController?.isPlaying ?: false) startProgressUpdates()
            }
        })


    }

    private fun animatePlayButton(playing: Boolean) {
        val resId =
            if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        binding.btnPlayPause.setImageResource(resId)
    }

    private fun startProgressUpdates() {
        if (!progressUpdaterScheduled) {
            progressUpdaterScheduled = true
            uiHandler.post(progressUpdateRunnable)
        }
    }

    private fun stopProgressUpdates() {
        if (progressUpdaterScheduled) {
            progressUpdaterScheduled = false
            uiHandler.removeCallbacks(progressUpdateRunnable)
        }
    }

    override fun onResume() {
        super.onResume()
        binding.bgImageView.load_forRoot(this, resources.configuration.orientation)
    }


    override fun onDestroy() {
        super.onDestroy()
        // cleanup
        //stopProgressUpdates()

        mediaController?.release()

        mediaController = null
    }
}
