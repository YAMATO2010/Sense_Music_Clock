package jp.gr.java_conf.SenseMusicClock

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
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
import jp.gr.java_conf.SenseMusicClock.R
class StandardPlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStandardPlayerBinding


    // service binding
    private var musicService: Musicservice? = null
    private var musicBound = false
    private var mediaController: MediaControllerCompat? = null

    private val uiHandler = Handler(Looper.getMainLooper())
    private var progressUpdaterScheduled = false

    private val progressUpdateRunnable = object : Runnable {
        override fun run() {
            try {
                val durationMs = musicService?.getDurationMs() ?: mediaController?.metadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: 0L
                val posMs = musicService?.getCurrentPositionMs() ?: mediaController?.playbackState?.position ?: 0L

                val durationSec = (durationMs / 1000L).coerceAtLeast(0L).toInt()
                val posSec = (posMs / 1000L).toInt()

                val sb = binding.seekBar
                // binding.seekBar is non-null via viewBinding
                if (durationSec > 0 && sb.max != durationSec) sb.max = durationSec
                sb.progress = posSec.coerceIn(0, (sb.max))

            } catch (_: Exception) {}
            if (progressUpdaterScheduled) uiHandler.postDelayed(this, 500)
        }
    }

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
            } catch (_: Exception) {
                // ignore
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicBound = false
            musicService = null
            try { mediaController?.unregisterCallback(controllerCallback) } catch (_: Exception) {}
            mediaController = null
        }
    }

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





                    // duration if available (convert to seconds for SeekBar)
                    val durationMs = metadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: 0L
                    if (durationMs > 0) binding.seekBar.max = (durationMs / 1000L).toInt()
                } catch (_: Exception) {
                    // ignore
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStandardPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)


        // back button (edge) to return to MainActivity
        binding.btnBackEdge.setOnClickListener { finish() }

        binding.btnPrev.setOnClickListener {
            try { mediaController?.transportControls?.skipToPrevious() } catch (_: Exception) {}
        }

        binding.btnNext.setOnClickListener {
            try { mediaController?.transportControls?.skipToNext() } catch (_: Exception) {}
        }

        binding.btnPlayPause.setOnClickListener {
            val isPlaying = mediaController?.playbackState?.state == PlaybackStateCompat.STATE_PLAYING
            if (isPlaying) {
                try { mediaController?.transportControls?.pause() } catch (_: Exception) {}
            } else {
                try { mediaController?.transportControls?.play() } catch (_: Exception) {}
            }
        }

        val orientation = resources.configuration.orientation

        binding.scrimOverlay.background = ColorDrawable(getColor(R.color.black_overlay))
        binding.root.background = getDrawble_forRootBackgroundByTimeAndOrientation(orientation,this)


        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    try {
                        // convert progress to percent of duration and ask service to seek
                        val max = seekBar?.max ?: 0
                        if (max > 0) {
                            val percent = (progress.toFloat() / max.toFloat()) * 100f
                            musicService?.seekToPercent(percent)
                        }
                    } catch (_: Exception) {}
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) { stopProgressUpdates() }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { if (mediaController?.playbackState?.state == PlaybackStateCompat.STATE_PLAYING) startProgressUpdates() }
        })

        // connect to service
        val intent = Intent(this, Musicservice::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
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



    private fun animatePlayButton(playing: Boolean) {
        val resId = if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        binding.btnPlayPause.setImageResource(resId)
    }

    override fun onDestroy() {
        super.onDestroy()
        // cleanup
        try { unbindService(connection) } catch (_: Exception) {}
        stopProgressUpdates()
        musicBound = false
        musicService = null
        mediaController = null
    }
}
