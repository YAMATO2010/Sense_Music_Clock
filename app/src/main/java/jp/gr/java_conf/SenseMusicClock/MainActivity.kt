// kotlin
package jp.gr.java_conf.SenseMusicClock


import IDENTIFIER_INITIAL_INDEX_PROBLEM
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.graphics.Rect
import android.view.View
import android.support.v4.media.session.MediaControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app_dir
import jp.gr.java_conf.SenseMusicClock.Music.StorageAccessHelper
import jp.gr.java_conf.SenseMusicClock.databinding.ActivityMainBinding

import kotlinx.coroutines.launch
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

        binding.Settingsbutton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        // 初期 adapter は置いておくが、レイアウトマネージャはあとで orientation に応じて設定する
        binding.recyclerJackets.setHasFixedSize(false)
        binding.recyclerJackets.adapter = JacketAdapter(emptyList(), R.drawable.default_album_art)

        try {
            val orientation = resources.configuration.orientation
            val Rdrawable = if (orientation == 1){
                // portrait: 1 列表示
                val span = 1
                binding.recyclerJackets.layoutManager = GridLayoutManager(this, span)
                // 少し左右の余白を詰める
                binding.recyclerJackets.setPadding(dpToPx(8), 0, dpToPx(8), 0)

                when (fdate1.toInt()) {
                    in 5..9 -> R.drawable.oblong_morning
                    in 10..15 -> R.drawable.oblong_noon
                    in 16..19 -> R.drawable.oblong_evening
                    else -> R.drawable.oblong_night
                }

            } else{
                // landscape: 横一列でスクロールするレイアウトにする
                // 横一列（Horizontal LinearLayout）にして横スクロールで複数アイテムを見せる
                val linear = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
                binding.recyclerJackets.layoutManager = linear
                // 横方向の余白は小さめに、上下に少しパディングを入れる
                binding.recyclerJackets.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
                binding.recyclerJackets.clipToPadding = false

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

        // RecyclerView のアイテム間隔をレイアウトに応じて設定する ItemDecoration を追加
        try {
            val spacing = dpToPx(4)
            // 既存のデコレーションはクリア
            while (binding.recyclerJackets.itemDecorationCount > 0) {
                binding.recyclerJackets.removeItemDecorationAt(0)
            }

            val lm = binding.recyclerJackets.layoutManager
            if (lm is GridLayoutManager) {
                val spanCount = lm.spanCount
                binding.recyclerJackets.addItemDecoration(object : RecyclerView.ItemDecoration() {
                    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                        val position = parent.getChildAdapterPosition(view)
                        if (position == RecyclerView.NO_POSITION) return
                        val column = position % spanCount
                        outRect.left = spacing - column * spacing / spanCount
                        outRect.right = (column + 1) * spacing / spanCount
                        outRect.top = spacing
                        outRect.bottom = spacing
                    }
                })
            } else if (lm is LinearLayoutManager && lm.orientation == LinearLayoutManager.HORIZONTAL) {
                // 横スクロール用の間隔（左右に small gap、上下は少し）
                binding.recyclerJackets.addItemDecoration(object : RecyclerView.ItemDecoration() {
                    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                        val position = parent.getChildAdapterPosition(view)
                        if (position == RecyclerView.NO_POSITION) return
                        // 左右とも spacing / 2 を入れて均等に見せる。先頭と末尾に余白を多めにする。
                        outRect.top = spacing / 2
                        outRect.bottom = spacing / 2
                        outRect.left = if (position == 0) spacing else spacing / 2
                        outRect.right = spacing / 2
                        // 最後尾には右マージンを与える
                        if (position == parent.adapter?.itemCount?.minus(1)) {
                            outRect.right = spacing
                        }
                    }
                })
            }
        } catch (e: Exception) {
            Log.w("MainActivity", "addItemDecoration failed", e)
        }

        setContentView(binding.root)
        storageAccessHelper = StorageAccessHelper(this,
            onDirectoryPicked = { _, _ -> },
            onPermissionGranted = { read_music_Granted() },
            onPermissionDenied = {
                //TODO 権限拒否時の処理
                Log.w("MainActivity", "音楽読み取り権限が拒否されました。")
            }

            )
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density + 0.5f).toInt()
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




                val jacketAdapter = JacketAdapter(tracks, R.drawable.default_album_art) { clickedTrack ->
                    val Trackuuid = clickedTrack.uuid
                    val index = tracks.indexOfFirst { it.uuid == Trackuuid }
                    Log.i("MainActivity", "track clicked: uuid :$Trackuuid title=${clickedTrack.title}" + IDENTIFIER_INITIAL_INDEX_PROBLEM)
                    try {
                        val extras = Bundle().apply { putString(Musicservice.EXTRA_UUID, Trackuuid.toString()) }
                        // 可能なら MediaController 経由で送る
                        val sent = try {
                            mediaController?.transportControls?.sendCustomAction(Musicservice.ACTION_PLAY_UUID, extras)
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

                lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        musicService?.tracksFlow?.collect { tracks ->
                            // UI はメインスレッドなのでそのまま更新
                            jacketAdapter.setItems(tracks)
                        }
                    }
                }

                binding.recyclerJackets.adapter = jacketAdapter


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
