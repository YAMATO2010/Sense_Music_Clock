package jp.gr.java_conf.SenseMusicClock.ui

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.load
import coil.request.CachePolicy
import coil.size.Precision
import jp.gr.java_conf.SenseMusicClock.BackgroundResolver
import jp.gr.java_conf.SenseMusicClock.MusicService
import jp.gr.java_conf.SenseMusicClock.PrefsManager
import jp.gr.java_conf.SenseMusicClock.R
import jp.gr.java_conf.SenseMusicClock.convertMsToTimeString
import jp.gr.java_conf.SenseMusicClock.databinding.ActivityStandardPlayerBinding
import jp.gr.java_conf.SenseMusicClock.load_forRoot
import kotlinx.coroutines.launch
import org.w3c.dom.Text
import kotlin.time.Duration

class StandardPlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStandardPlayerBinding


    // service binding


    private lateinit var token: SessionToken

    private var mediaController: MediaController? = null


    private val uiHandler = Handler(Looper.getMainLooper())


    private var progressUpdaterScheduled = false

    val timeTickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_TIME_TICK) {
                // 1分経つごとに呼ばれる
                lifecycleScope.launch {

                    if (::binding.isInitialized && context != null && BackgroundResolver.loadBackgroundSource(
                            context,
                            resources.configuration.orientation
                        ).key != lastSourceBackGround
                    ) {
                        lastSourceBackGround = binding.bgImageView.load_forRoot(
                            context,
                            resources.configuration.orientation
                        )
                    }
                }
            }
        }
    }

    private var lastSourceBackGround = ""

    private val progressUpdateRunnable = object : Runnable {
        override fun run() {

            try {

                val durationMs = mediaController?.duration ?: 0L
                val posMs = mediaController?.currentPosition ?: 0L

                binding.CurrentTimeTextView.text = convertMsToTimeString(posMs)
                setDurationText(durationMs)

                val durationSec = (durationMs / 1000L).coerceAtLeast(0L).toInt()
                val posSec = (posMs / 1000L).toInt()

                val sb = binding.seekBar
                if (durationSec > 0 && sb.max != durationSec) sb.max = durationSec
                sb.progress = posSec.coerceIn(0, (sb.max))
                mediaController?.playWhenReady.let {
                    animatePlayButton(it == true)
                }

            } catch (e: Exception) {
                Log.w("StandardPlayerActivity", "progress update failed", e)
            }
            if (progressUpdaterScheduled) uiHandler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityStandardPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            // システムバーのインセット（余白）を取得
            val navigationBarsInsets =
                insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            // 取得したボトムインセット（ナビゲーションバーの高さ）をパディングに設定
            view.setPadding(
                navigationBarsInsets.left,
                navigationBarsInsets.top,
                navigationBarsInsets.right,
                navigationBarsInsets.bottom
            )

            // インセットを消費したことを伝える
            // これにより、他のビューに同じインセットが適用されるのを防ぐ
            insets
        }
        token = SessionToken(this, ComponentName(this, MusicService::class.java))
        lifecycleScope.launch {
            lastSourceBackGround = binding.bgImageView.load_forRoot(
                this@StandardPlayerActivity,
                resources.configuration.orientation
            )
        }

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
                        val durationMs = it.mediaMetadata.durationMs ?: 0L

                        setDurationText(durationMs)
                        binding.tvTitle.text = title
                        binding.tvArtist.text = artist
                        binding.tvAlbumName.text = album


                        binding.ivAlbumArt.load_albumArt(it.mediaMetadata.artworkUri)

                        startProgressUpdates()


                    } catch (e: Exception) {
                        Log.w("StandardPlayerActivity", "metadata update failed", e)
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


                                binding.ivAlbumArt.load_albumArt(mediaItem?.mediaMetadata?.artworkUri)


                                val durationMs = mediaItem?.mediaMetadata?.durationMs ?: 0L
                                setDurationText(durationMs)
                                if (durationMs > 0) binding.seekBar.max =
                                    (durationMs / 1000L).toInt()
                                startProgressUpdates()
                            } catch (e: Exception) {
                                Log.w(
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
                Log.w("StandardPlayerActivity", "skipToPrevious failed", e)
            }
        }

        binding.btnNext.setOnClickListener {
            try {
                mediaController?.seekToNext()
            } catch (e: Exception) {
                Log.w("StandardPlayerActivity", "skipToNext failed", e)
            }
        }

        binding.btnPlayPause.setOnClickListener {
            val isPlaying = mediaController?.playWhenReady == true
            animatePlayButton(!isPlaying)
            if (isPlaying) {
                try {
                    mediaController?.pause()
                } catch (e: Exception) {
                    Log.w("StandardPlayerActivity", "pause failed", e)
                }
            } else {
                try {
                    mediaController?.play()
                } catch (e: Exception) {
                    Log.w("StandardPlayerActivity", "play failed", e)
                }
            }
        }

        lifecycleScope.launch {

            val nowRepeatMode = PrefsManager.getPlayModeLoop(this@StandardPlayerActivity)

            if (nowRepeatMode) {

                binding.btnRepeat.load(androidx.media3.ui.R.drawable.exo_icon_repeat_one)
            } else {
                binding.btnRepeat.load(androidx.media3.ui.R.drawable.exo_icon_repeat_all)
            }
        }
        binding.btnRepeat.setOnClickListener {
            lifecycleScope.launch {
                val iscurrentRepeatMode_oneLoop =
                    PrefsManager.getPlayModeLoop(this@StandardPlayerActivity)

                val isNewRepeatMode_oneLoop = !iscurrentRepeatMode_oneLoop
                PrefsManager.setPlayModeLoop(this@StandardPlayerActivity, isNewRepeatMode_oneLoop)

                if (isNewRepeatMode_oneLoop) {
                    val view = it as ImageButton
                    view.load(androidx.media3.ui.R.drawable.exo_icon_repeat_one)
                } else {
                    val view = it as ImageButton
                    view.load(androidx.media3.ui.R.drawable.exo_icon_repeat_all)
                }
            }
        }


        val orientation = resources.configuration.orientation

        binding.scrimOverlay.background = ColorDrawable(getColor(R.color.black_overlay))
        lifecycleScope.launch {

            lastSourceBackGround =
                binding.bgImageView.load_forRoot(this@StandardPlayerActivity, orientation)
        }





        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
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
                        Log.w("StandardPlayerActivity", "seek handling failed", e)
                    }
                }
            }


            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                stopProgressUpdates()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                startProgressUpdates()
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


    private fun setDurationText(durationMs : Long) {
        binding.durationTextView.text = "/" + convertMsToTimeString(durationMs)

    }
    override fun onPause() {
        super.onPause()
        unregisterReceiver(timeTickReceiver)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {

            lastSourceBackGround =
                binding.bgImageView.load_forRoot(
                    this@StandardPlayerActivity,
                    resources.configuration.orientation
                )
        }
        registerReceiver(timeTickReceiver, IntentFilter(Intent.ACTION_TIME_TICK))

    }


    override fun onDestroy() {
        super.onDestroy()
        // cleanup
        //stopProgressUpdates()

        try {

            unregisterReceiver(timeTickReceiver)
        } catch (e: Exception) {
            Log.w("StandardPlayerActivity", "unregisterReceiver failed", e)
        }
        mediaController?.release()

        mediaController = null
    }

    fun ImageView.load_albumArt(uri: Uri?) {
        this.load(uri) {
            bitmapConfig(Bitmap.Config.ARGB_8888)
            placeholder(R.drawable.default_album_art)
            error(R.drawable.default_album_art)
            precision(Precision.EXACT)
            allowHardware(true)
            memoryCachePolicy(CachePolicy.DISABLED)
            crossfade(true)
                .memoryCachePolicy(CachePolicy.DISABLED)
        }
    }
}