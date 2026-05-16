package jp.gr.java_conf.SenseMusicClock.Music

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout

import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.work.Constraints
import coil.load
import coil.request.CachePolicy
import coil.size.Precision
import jp.gr.java_conf.SenseMusicClock.MusicService
import jp.gr.java_conf.SenseMusicClock.R
import jp.gr.java_conf.SenseMusicClock.ui.StandardPlayerActivity
import java.lang.ref.WeakReference

class BottomController @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
): FrameLayout(context, attrs, defStyleAttr) {

    private val titleView: TextView by lazy { findViewById(R.id.nowItemTitleView) }
    private val artistView: TextView by lazy { findViewById(R.id.nowItemArtistView) }
    private val artworkView: ImageView by lazy { findViewById(R.id.nowItemartwork) }
    private val playBtn: ImageButton by lazy { findViewById(R.id.bcBtnPlayPause) }
    private val backBtn: ImageButton by lazy { findViewById(R.id.bcBtnPrev)}

    private val nextBtn : ImageButton by lazy { findViewById(R.id.bcBtnNext) }
    private val container : ConstraintLayout by  lazy { findViewById(R.id.bottomControllerContainer) }
    private val progressBar: ProgressBar by lazy { findViewById(R.id.currentPositionBer) }




    private var mediaController: MediaController? = null

    private lateinit var token: SessionToken
    private val barHandler = Handler(Looper.getMainLooper())
    private val progressUpdateRunnable = object : Runnable {
        override fun run() {

            try {

                val durationMs = mediaController?.duration ?: 0L
                val posMs = mediaController?.currentPosition ?: 0L

                val durationSec = (durationMs / 1000L).coerceAtLeast(0L).toInt()
                val posSec = (posMs / 1000L).toInt()


                // binding.seekBar is non-null via viewBinding
                if (durationSec > 0 && progressBar.max != durationSec) progressBar.max = durationSec
                progressBar.progress = posSec.coerceIn(0, (progressBar.max))
                mediaController?.playWhenReady.let {
                    animatePlayButton(it == true)


                }

            } catch (e: Exception) {
                Log.w("StandardPlayerActivity", "progress update failed", e)
            }
            barHandler.postDelayed(this, 500)
        }
    }
    init {
        LayoutInflater.from(context).inflate(R.layout.bottom_controller, this, true)


    }


    fun initialize(activity: AppCompatActivity) {

        val activityRef = WeakReference(activity)

        val activityWeak = activityRef.get()

        token = SessionToken(activityWeak ?: return, ComponentName(activity, MusicService::class.java))

        val controllerFuture = MediaController.Builder(activityWeak , token).buildAsync()



        controllerFuture.addListener({
            mediaController = controllerFuture.get()
            mediaController?.let {


                activityWeak.runOnUiThread {
                    try {

                        animatePlayButton(it.playWhenReady)
                        val title = it.mediaMetadata.title ?: ""
                        val artist = it.mediaMetadata.artist ?: ""

                        titleView.text = title
                        artistView.text = artist



                        artworkView.load_albumArt(it.mediaMetadata.artworkUri)

                        barHandler.post(progressUpdateRunnable)


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
                        activityRef.get()?.runOnUiThread {
                            try {
                                val title = mediaItem?.mediaMetadata?.title ?: ""
                                val artist = mediaItem?.mediaMetadata?.artist ?: ""

                                titleView.text = title
                                artistView.text = artist



                                artworkView.load_albumArt(mediaItem?.mediaMetadata?.artworkUri)
                                val durationMs = mediaItem?.mediaMetadata?.durationMs ?: 0L
                                if (durationMs > 0) progressBar.max =
                                    (durationMs / 1000L).toInt()

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
        }, ContextCompat.getMainExecutor(activityWeak))


        playBtn.setOnClickListener {
            mediaController?.let {
                it.playWhenReady = !(it.playWhenReady)
            }
        }

        backBtn.setOnClickListener {
            mediaController?.seekToPrevious()
        }

        nextBtn.setOnClickListener {
            mediaController?.seekToNext()
        }

        container.setOnClickListener {
            val intent = Intent( context, StandardPlayerActivity::class.java)
            context.startActivity(intent)
        }



    }

    fun release() {
        barHandler.removeCallbacks(progressUpdateRunnable)
        mediaController?.release()
        mediaController = null
    }


    private fun animatePlayButton(playing: Boolean) {
        val resId =
            if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        playBtn.load(resId)
    }

    fun ImageView.load_albumArt(uri: Uri?) {
        this.load(uri) {

            placeholder(R.drawable.default_album_art)
            error(R.drawable.default_album_art)

            allowHardware(true)
            memoryCachePolicy(CachePolicy.DISABLED)
            crossfade(true)
                .memoryCachePolicy(CachePolicy.DISABLED)
        }
    }


}