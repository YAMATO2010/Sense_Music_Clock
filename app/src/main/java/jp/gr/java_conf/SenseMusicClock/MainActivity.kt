// kotlin
package jp.gr.java_conf.SenseMusicClock

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import android.support.v4.media.session.MediaControllerCompat
import app_dir
import jp.gr.java_conf.SenseMusicClock.Music.StorageAccessHelper
import jp.gr.java_conf.SenseMusicClock.databinding.ActivityMainBinding
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var musicService: Musicservice? = null
    private var musicBound = false
    private val rebindHandler = Handler(Looper.getMainLooper())
    private var mediaController: MediaControllerCompat? = null

    lateinit var storageAccessHelper : StorageAccessHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)

        ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(application))

        createAppFolderIfNeeded()

        val nowTime = LocalDateTime.now()
        val dtformat1 = DateTimeFormatter.ofPattern("HH")
        val fdate1 = dtformat1.format(nowTime)
        Log.i("nowHour", fdate1)

        binding.recyclerJackets.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        binding.recyclerJackets.setHasFixedSize(false)
        binding.recyclerJackets.adapter = JacketAdapter(emptyList(), R.drawable.default_album_art)

        try {
            val orientation = resources.configuration.orientation
            val Rdrawable = if (orientation == 1){
                when (fdate1.toInt()) {
                    in 5..9 -> R.drawable.oblong_morning
                    in 10..15 -> R.drawable.oblong_noon
                    in 16..19 -> R.drawable.oblong_evening
                    else -> R.drawable.oblong_night
                }

            } else{
                when (fdate1.toInt()) {
                    in 5..9 -> R.drawable.land_morning
                    in 10..15 -> R.drawable.land_noon
                    in 16..19 -> R.drawable.land_evening
                    else -> R.drawable.land_night
                }
            }

            val drawable = ContextCompat.getDrawable(this, Rdrawable)
            binding.main.background = drawable
        } catch (e: Exception) {
            Log.w("MainActivity", "background set failed", e)
        }

        setContentView(binding.root)
        storageAccessHelper = StorageAccessHelper(this,
            onDirectoryPicked = { _, _ -> },
            onPermissionGranted = { read_music_Granted() },
            onPermissionDenied = {}

            )
    }



    private val musicConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.i("MainActivity", "onServiceConnected: component=$name binder=${service != null}")
            val binder = service as? Musicservice.LocalBinder
            if (binder == null) {
                Log.w("MainActivity", "binder null - retrying bind shortly")
                rebindHandler.postDelayed({
                    try {
                        val intent = Intent(this@MainActivity, Musicservice::class.java)
                        this@MainActivity.bindService(intent, this, Context.BIND_AUTO_CREATE)
                    } catch (e: Exception) {
                        Log.w("MainActivity", "rebind failed", e)
                    }
                }, 500L)
                return
            }

            musicService = binder.getService()
            musicBound = true

            try {
                val token = binder.getSessionToken()
                val mc = MediaControllerCompat(this@MainActivity, token)
                MediaControllerCompat.setMediaController(this@MainActivity, mc)
                mediaController = mc
            } catch (e: Exception) {
                Log.w("MainActivity", "create MediaController failed", e)
            }

            val tracks = musicService?.Tracks ?: emptyList()
            Log.i("musicConnection", "loaded tracks: ${tracks.size}")

            runOnUiThread {


// kotlin
                val jacketAdapter = JacketAdapter(tracks, R.drawable.default_album_art) { clickedTrack ->
                    val index = tracks.indexOfFirst { it.id == clickedTrack.id }.coerceAtLeast(0)
                    Log.i("MainActivity", "track clicked: index=$index title=${clickedTrack.title}")
                    try {
                        val extras = Bundle().apply { putInt(Musicservice.EXTRA_INDEX, index) }
                        // 可能なら MediaController 経由で送る
                        val sent = try {
                            mediaController?.transportControls?.sendCustomAction(Musicservice.ACTION_PLAY_INDEX, extras)
                            true
                        } catch (e: Exception) {
                            Log.w("MainActivity", "sendCustomAction failed", e)
                            false
                        }
                        // MediaController が無い／失敗なら直接サービスにフォールバック
                        if (mediaController == null || !sent) {
                            try {
                                musicService?.setQueue(tracks, index)
                                musicService?.play()
                            } catch (e: Exception) {
                                Log.w("MainActivity", "direct play fallback failed", e)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("MainActivity", "play request failed", e)
                    }

                    binding.TitleView.text = clickedTrack.title
                    binding.MusicEtcView.text = "${clickedTrack.artist} / ${clickedTrack.album}"
                    binding.recyclerJackets.scrollToPosition(index)
                }

                binding.recyclerJackets.adapter = jacketAdapter

                try {
                    musicService?.setQueue(tracks, 0)
                } catch (e: Exception) {
                    Log.w("MainActivity", "setQueue failed", e)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.i("MainActivity", "onServiceDisconnected: $name")
            musicService = null
            mediaController = null
            musicBound = false
        }
    }

    override fun onStart() {
        super.onStart()
        if (storageAccessHelper.hasReadAudioPermission()) {
            read_music_Granted()
        }


    }


    override fun onStop() {
        super.onStop()
        if (musicBound) {
            try {
                unbindService(musicConnection)
            } catch (e: Exception) {
                Log.w("MainActivity", "unbind failed", e)
            }
            musicBound = false
            musicService = null
            mediaController = null
        }
    }

    private fun read_music_Granted() {
        try {
            val startIntent = Intent(this, Musicservice::class.java)
            ContextCompat.startForegroundService(this, startIntent)
        } catch (e: Exception) {
            Log.w("MainActivity", "startForegroundService failed (continuing to bind)", e)
        }
        val intent = Intent(this, Musicservice::class.java)
        bindService(intent, musicConnection, Context.BIND_AUTO_CREATE)

    }

    private fun createAppFolderIfNeeded() {
        val resolver = contentResolver
        val relPathPattern = "Music/SMC%"
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf(relPathPattern)

        resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.count > 0) {
                Log.i("MainActivity", "app folder already exists")
                return
            }
        }

        val values = app_dir
        try {
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            Log.i("MainActivity", "フォルダ作成結果: $uri")
        } catch (e: Exception) {
            Log.w("MainActivity", "フォルダ作成に失敗しました", e)
        }
    }
}
