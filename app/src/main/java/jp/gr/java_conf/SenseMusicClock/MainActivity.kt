// kotlin
package jp.gr.java_conf.SenseMusicClock


import IDENTIFIER_INITIAL_INDEX_PROBLEM
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import jp.gr.java_conf.SenseMusicClock.MainViewModel
import jp.gr.java_conf.SenseMusicClock.Music.StorageAccessHelper
import jp.gr.java_conf.SenseMusicClock.databinding.ActivityMainBinding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.support.v4.media.session.MediaControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import android.graphics.Color
import android.graphics.PorterDuff
import android.content.pm.ApplicationInfo
import app_dir
import jp.gr.java_conf.SenseMusicClock.Music.JacketAdapter


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var musicService: Musicservice? = null
    private var musicBound = false
    private val rebindHandler = Handler(Looper.getMainLooper())
    private var mediaController: MediaControllerCompat? = null

    lateinit var storageAccessHelper : StorageAccessHelper

    // 追加: アダプタをクラスプロパティ化
    private lateinit var jacketAdapter: JacketAdapter

    // ViewModel を使って向きと pendingInstantScroll を保持
    private lateinit var mainViewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)

        ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(application))

        createAppFolderIfNeeded()
        storageAccessHelper = StorageAccessHelper(this,
            onDirectoryPicked = { _, _ -> },
            onPermissionGranted = { read_music_Granted() },
            onPermissionDenied = {
                //TODO 権限拒否時の処理
                Log.w("MainActivity", "音楽読み取り権限が拒否されました。")
            }

        )

        // ViewModel を初期化
        mainViewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        // RecyclerView のレイアウトは configureRecyclerForOrientation にまとめる
        val orientation = resources.configuration.orientation
        configureRecyclerForOrientation(orientation)
        // 初期向きを ViewModel に記録
        if (mainViewModel.lastOrientation == null) mainViewModel.lastOrientation = orientation

        val nowTime = LocalDateTime.now()
        val dtformat1 = DateTimeFormatter.ofPattern("HH")
        val fdate1 = dtformat1.format(nowTime)
        Log.i("nowHour", fdate1)

        binding.Settingsbutton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        // 初期 adapter はクラスプロパティとして作成し、クリック処理もここで定義する
        binding.recyclerJackets.setHasFixedSize(false)
        jacketAdapter = JacketAdapter(emptyList(), R.drawable.default_album_art) { clickedTrack ->
            Log.i(
                "MainActivity",
                "track clicked: ${clickedTrack.title}" + IDENTIFIER_INITIAL_INDEX_PROBLEM
            )
            try {
                // If it's a local track, prefer asking the service to play by index (service has authoritative queue)
                var sent = false
                if (clickedTrack is localTrack) {
                    val serviceTracks = musicService?.Tracks
                    val serviceIndex =
                        serviceTracks?.indexOfFirst { it.id == clickedTrack.id } ?: -1
                    if (serviceIndex >= 0) {
                        val extras =
                            Bundle().apply { putInt(Musicservice.EXTRA_INDEX, serviceIndex) }
                        sent = try {
                            Log.d(
                                "MainActivity",
                                "sending custom action: ${Musicservice.ACTION_PLAY_INDEX} extras=${extras.toString()}"
                            )
                            mediaController?.transportControls?.sendCustomAction(
                                Musicservice.ACTION_PLAY_INDEX,
                                extras
                            )
                            true
                        } catch (e: Exception) {
                            Log.w("MainActivity", "sendCustomAction ACTION_PLAY_INDEX failed", e)
                            false
                        }
                    }
                }

                // If we couldn't send via MediaController (or it's not a localTrack/service didn't know it), fallback to direct setQueue/play
                if (!sent) {
                    if (clickedTrack is localTrack) {
                        val localList = jacketAdapter.getItems().filterIsInstance<localTrack>()
                        val idxLocal = localList.indexOfFirst { it.id == clickedTrack.id }
                        if (idxLocal >= 0) {
                            musicService?.setQueue(localList, idxLocal)
                            musicService?.play()
                        } else {
                            Log.w(
                                "MainActivity",
                                "clickedTrack not found in localList - skipping fallback setQueue/play"
                            )
                        }
                    } else {
                        Log.w(
                            "MainActivity",
                            "No play action for non-local track (or MediaController unavailable)"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "play request failed", e)
            }

            // update texts and restart slide loop (Title -> Etc)
            stopSlideLoop()
            binding.TitleView.text = clickedTrack.title
            binding.MusicEtcView.text = "${clickedTrack.artist} / ${clickedTrack.album}"
            // update marquee state for both views
            updateMarqueeFor(binding.TitleView)
            updateMarqueeFor(binding.MusicEtcView)

            // reset visual state
            try {
                binding.TitleView.translationX = 0f
                binding.TitleView.alpha = 1f
                binding.MusicEtcView.translationX = 0f
                binding.MusicEtcView.alpha = 1f
            } catch (_: Exception) {
            }
            currentSlideTarget = SlideTarget.TITLE
            startSlideLoop()

            binding.currentAlbumArt.setImageBitmap(
                clickedTrack.albumArt ?: BitmapFactory.decodeResource(
                    resources,
                    R.drawable.default_album_art
                )
            )
        }



        binding.recyclerJackets.adapter = jacketAdapter

        // RecyclerView のアイテム間隔をレイアウトに応じて設定する ItemDecoration を追加
        try {
            val spacing = this.dpToPx(4)
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

        binding.currentAlbumArt.setOnClickListener {
            scrollToTrack()
        }


        binding.GotoStandardPlayerButton.setOnClickListener {
            val intent = Intent(this, StandardPlayerActivity::class.java)
            startActivity(intent)
        }
        setContentView(binding.root)



        // Debug: ensure nowLoading views exist and tint is applied early; use unified setter so parent overlay is shown
        try {
            binding.nowLoadingBar.indeterminateDrawable?.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            setLoadingVisible(true)
            Log.d("MainActivity", "nowLoading views initialized and shown via setLoadingVisible(true)")
        } catch (e: Exception) {
            Log.w("MainActivity", "failed to initialize nowLoading views", e)
        }
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
                 // adapter にリストを渡し、コミット後に現在トラックへスクロールする
                 jacketAdapter.setItems(tracks) {
                     // commit完了後に呼ばれる。adapter に要素が入っていればローディングを消す
                     val hasItems = jacketAdapter.getItems().isNotEmpty()
                     Log.d("MainActivity", "commitCallback after setItems: hasItems=$hasItems, tracksSize=${tracks.size}")
                     setLoadingVisible(!hasItems)
                    // commit 完了後に現在再生トラックがあればリスト上で追従してスクロールする
                    if (hasItems) {
                        val current = musicService?.getCurrentTrack()
                        if (current != null) {
                            // If orientation changed since last known by ViewModel, ensure instant scroll
                            val curOrient = resources.configuration.orientation
                            if (mainViewModel.lastOrientation != null && mainViewModel.lastOrientation != curOrient) {
                                Log.d("MainActivity", "commitCallback: orientation change detected (${mainViewModel.lastOrientation} -> $curOrient) -> instant scroll")
                                scrollToTrackInstant()
                                mainViewModel.pendingInstantScroll = false
                                mainViewModel.lastOrientation = curOrient
                            } else if (mainViewModel.pendingInstantScroll) {
                                Log.d("MainActivity", "commitCallback: performing pending instant scroll")
                                scrollToTrackInstant()
                                mainViewModel.pendingInstantScroll = false
                            } else {
                                scrollToTrack()
                            }
                        }
                    }
                 }

                // If a pending instant-scroll is still set after the initial adapter commit, start retry scheduler
                if (mainViewModel.pendingInstantScroll) {
                    Log.d("MainActivity", "onServiceConnected: pendingInstantScroll detected -> scheduling retries")
                    schedulePendingInstantScrollTry()
                }

                // tracksFlow を監視して差分更新（コミット後に現在再生トラックがあれば追従）
                lifecycleScope.launch {

                    launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            musicService?.currentTracksFlow?.collect { currentTrack ->
                                runOnUiThread {
                                    if (currentTrack != null) {
                                        applyCurrentTrackToUi(currentTrack)
                                    } else {
                                        stopSlideLoop()
                                        binding.currentAlbumArt.setImageBitmap(BitmapFactory.decodeResource(resources,R.drawable.default_album_art) )
                                        binding.TitleView.text = ""
                                        binding.MusicEtcView.text = ""
                                        updateMarqueeFor(binding.TitleView)
                                        updateMarqueeFor(binding.MusicEtcView)
                                    }
                                    binding.main.background = getDrawble_forRootBackgroundByTimeAndOrientation(resources.configuration.orientation,this@MainActivity)
                                }
                            }
                        }
                    }

                    musicService?.tracksFlow?.collect { updated ->
                        // 新しいリストを反映。反映後に要素があればローディングを消す
                        jacketAdapter.setItems(updated) {
                            val hasItems = jacketAdapter.getItems().isNotEmpty()
                            Log.d("MainActivity", "tracksFlow commitCallback: hasItems=$hasItems, updatedSize=${updated.size}")
                            if (hasItems) {
                                Log.d("MainActivity", "hiding loading indicator")
                                setLoadingVisible(false)
                            } else {
                                Log.d("MainActivity", "showing loading indicator")
                                setLoadingVisible(true)
                            }

                            // If rotation requested an instant scroll and we now have items, consume it here as well
                            if (hasItems && mainViewModel.pendingInstantScroll) {
                                val curr = musicService?.getCurrentTrack()
                                if (curr != null) {
                                    Log.d("MainActivity", "tracksFlow commitCallback: performing pending instant scroll")
                                    scrollToTrackInstant()
                                } else {
                                    Log.d("MainActivity", "tracksFlow commitCallback: pendingInstantScroll set but currentTrack is null")
                                }
                                mainViewModel.pendingInstantScroll = false
                            }


                        }
                    }
                 }
                val currentTrack = musicService?.getCurrentTrack()
                if (currentTrack != null) {
                    // use marquee chain

                    stopSlideLoop()
                    binding.TitleView.text = currentTrack.title
                    binding.MusicEtcView.text = "${currentTrack.artist} / ${currentTrack.album}"
                    try {
                        binding.TitleView.translationX = 0f
                        binding.TitleView.alpha = 1f
                        binding.MusicEtcView.translationX = 0f
                        binding.MusicEtcView.alpha = 1f
                    } catch (_: Exception) {}
                    currentSlideTarget = SlideTarget.TITLE
                    startSlideLoop()


                    binding.currentAlbumArt.setImageBitmap(currentTrack.albumArt ?: BitmapFactory.decodeResource(resources,R.drawable.default_album_art) )


                }
                // scrolling was moved into the adapter commit callback; avoid duplicate calls here

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
            // show loading indicator when starting to read music (use unified helper so overlay parent becomes visible)
            setLoadingVisible(true)
            read_music_Granted()





        }



    }

    override fun onResume() {
        super.onResume()

        // On resume: if orientation changed since last time, set pending instant scroll and reconfigure layout
        val currentOrientation = resources.configuration.orientation
        val prevOrientation = mainViewModel.lastOrientation
        if (prevOrientation == null) {
            mainViewModel.lastOrientation = currentOrientation
        } else if (currentOrientation != prevOrientation) {
            Log.d("MainActivity", "orientation changed from ${prevOrientation} to ${currentOrientation} -> reconfiguring recycler and scheduling instant-scroll")
            configureRecyclerForOrientation(currentOrientation)
            // If adapter already has items, perform instant scroll now. Otherwise set pending flag to be consumed on commit callback.
            val adapterHasItems = ::jacketAdapter.isInitialized && jacketAdapter.itemCount > 0
            if (adapterHasItems && musicService?.getCurrentTrack() != null) {
                Log.d("MainActivity", "onResume: adapter has items and current track present -> instant scroll now")
                scrollToTrackInstant()
            } else {
                Log.d("MainActivity", "onResume: scheduling pendingInstantScroll (adapterHasItems=$adapterHasItems currentTrackPresent=${musicService?.getCurrentTrack() != null})")
                mainViewModel.pendingInstantScroll = true
                schedulePendingInstantScrollTry()
            }
            mainViewModel.lastOrientation = currentOrientation
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

            mediaController = null
        }

        // stop sliding animations when activity is not visible
        stopSlideLoop()

    }

    override fun onDestroy() {
        super.onDestroy()
        if (musicBound) {
           musicService = null
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
        // do not attempt to scroll here; wait for adapter commit in onServiceConnected


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



    // 追加: 滑らかなスクロール（アニメーション）でアイテムを表示する
    private fun smoothScrollToPosition(position: Int) {
        if (position < 0) return
        val recycler = binding.recyclerJackets
        recycler.post {
            val lm = recycler.layoutManager
            if (lm == null) return@post

            val smoothScroller = object : androidx.recyclerview.widget.LinearSmoothScroller(this@MainActivity) {
                // Center the target view if possible
                override fun calculateDtToFit(viewStart: Int, viewEnd: Int, boxStart: Int, boxEnd: Int, snapPreference: Int): Int {
                    try {
                        val viewSize = viewEnd - viewStart
                        val boxSize = if (lm.canScrollHorizontally()) recycler.width else recycler.height
                        val boxStartCenter = (boxSize - viewSize) / 2
                        // move viewStart to boxStartCenter
                        return boxStartCenter - viewStart
                    } catch (e: Exception) {
                        return super.calculateDtToFit(viewStart, viewEnd, boxStart, boxEnd, snapPreference)
                    }
                }

                // Optional: tune scrolling speed
                override fun calculateSpeedPerPixel(displayMetrics: android.util.DisplayMetrics): Float {
                    // milliseconds per pixel
                    return 10f / displayMetrics.densityDpi // slower than default for more visible animation
                }
            }

            try {
                smoothScroller.targetPosition = position
                lm.startSmoothScroll(smoothScroller)
            } catch (e: Exception) {
                // fallback
                recycler.smoothScrollToPosition(position)
            }
        }
    }

    // Slide target enum preserved so remaining call sites compile and can be wired to a library later
    private enum class SlideTarget { TITLE, ETC }

    private var currentSlideTarget = SlideTarget.TITLE
    // marqueeManager removed to disable custom animation implementation

    // start/stop are now no-ops: animations removed. They reset view visual state and ensure marquee flags are applied.
    private fun startSlideLoop() {

    }

    private fun stopSlideLoop() {

    }

    // Helper: enable marquee for TextView if text is wider than container; otherwise center it
    private fun updateMarqueeFor(tv: TextView) {

    }

    // Utility to show/hide loading indicator safely (handles overlays or direct children)
    private fun setLoadingVisible(visible: Boolean) {
        try {
            val vis = if (visible) View.VISIBLE else View.GONE
            // Ensure overlay visibility is controlled safely.
            // When showing: make the overlay visible and bring it to front.
            // When hiding: only hide the overlay, do NOT set ancestor/root visibility to GONE (that hides the whole UI).
            // Prefer controlling the FrameLayout overlay directly (it contains the nowLoading views).
            try {
                // If the binding has loadingOverlay (we added FrameLayout), toggle it.
                val overlay = try { binding.root.findViewById<View>(R.id.loadingOverlay) } catch (_: Exception) { null }
                if (overlay != null) {
                    overlay.visibility = vis
                    // ensure overlay interaction state
                    overlay.isClickable = visible
                    overlay.isFocusable = visible
                } else {
                    // Fallback: toggle the nowLoading child views directly
                    binding.nowLoadingBar.visibility = vis
                    binding.nowLoadingTextView.visibility = vis
                }
            } catch (e: Exception) {
                // Fallback safety
                binding.nowLoadingBar.visibility = vis
                binding.nowLoadingTextView.visibility = vis
            }

            if (visible) {
                // bring to front so overlay receives touches and appears above content
                try {
                    binding.nowLoadingBar.bringToFront()
                    binding.nowLoadingTextView.bringToFront()
                } catch (_: Exception) {}
            } else {
                // If hiding, ensure root and main content are visible (protect against earlier bugs setting ancestors GONE)
                try {
                    binding.root.visibility = View.VISIBLE
                } catch (_: Exception) {}
            }
            Log.d("MainActivity", "setLoadingVisible($visible) applied overlayVisible=$vis rootVisible=${binding.root.visibility}")
         } catch (e: Exception) {
             Log.w("MainActivity", "setLoadingVisible failed", e)
         }
     }

    fun scrollToTrack(adapter: JacketAdapter? =
                          if (::jacketAdapter.isInitialized) {
                              jacketAdapter
                          }
                          else if (::binding.isInitialized){
                              binding.recyclerJackets.adapter as? JacketAdapter
                          } else {
                              null
                                 },track: Track? = musicService?.getCurrentTrack())
    {

        if (!musicBound) return
        if (adapter == null)      return
        if (track == null)        return

        val scrollPos = adapter.getItemPosition(track)
        if (scrollPos != null) binding.recyclerJackets.post { smoothScrollToPosition(scrollPos) }




    }

    // Configure recycler / padding depending on orientation
    private fun configureRecyclerForOrientation(orientation: Int) {
        try {
            if (orientation == 1) {
                // portrait: 1 列表示
                val span = 1
                binding.recyclerJackets.layoutManager = GridLayoutManager(this, span)
                binding.recyclerJackets.setPadding(this.dpToPx(8), 0, this.dpToPx(8), 0)
                binding.recyclerJackets.clipToPadding = false
            } else {
                // landscape: 横一列でスクロールするレイアウトにする
                val linear = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
                binding.recyclerJackets.layoutManager = linear
                binding.recyclerJackets.setPadding(this.dpToPx(4), this.dpToPx(4), this.dpToPx(4), this.dpToPx(4))
                binding.recyclerJackets.clipToPadding = false
            }
            // 背景は既存ロジックに任せる（別箇所で更新されるためここでは無害）
        } catch (e: Exception) {
            Log.w("MainActivity", "configureRecyclerForOrientation failed", e)
        }
    }

    // Find position of a track in the adapter with fallbacks (id match -> metadata match)
    private fun getPositionForTrack(adapter: JacketAdapter, track: Track): Int? {
        // Prefer adapter's getItemPosition (handles localTrack/SpotifyTrack ids)
        val byId = adapter.getItemPosition(track)
        if (byId != null) return byId

        // Fallback: match by title/artist/album (loose)
        val title = track.title
        val artist = track.artist
        val album = track.album
        val idx = adapter.getItems().indexOfFirst { it.title == title && it.artist == artist && it.album == album }
        return if (idx >= 0) idx else null
    }

    // Instant scroll to current track without animation
    private fun scrollToTrackInstant(adapter: JacketAdapter? =
                                        if (::jacketAdapter.isInitialized) jacketAdapter else (binding.recyclerJackets.adapter as? JacketAdapter),
                                     track: Track? = musicService?.getCurrentTrack()) {
        if (!musicBound) return
        if (adapter == null) return
        if (track == null) return
        val pos = getPositionForTrack(adapter, track)
        Log.d("MainActivity", "scrollToTrackInstant: resolved position=$pos for track='${track.title}'")
        if (pos != null) {
            // Attempt to center the item instantly.
            binding.recyclerJackets.post {
                try {
                    val lm = binding.recyclerJackets.layoutManager
                    if (lm is LinearLayoutManager) {
                        // Try to get actual item view to compute exact offset
                        val child = binding.recyclerJackets.findViewHolderForAdapterPosition(pos)?.itemView
                        if (child != null) {
                          val offset = if (lm.canScrollHorizontally()) {
                                // center horizontally
                                (binding.recyclerJackets.width / 2) - (child.width / 2)
                            } else {
                                // center vertically
                                (binding.recyclerJackets.height / 2) - (child.height / 2)
                            }
                            // scrollToPositionWithOffset expects offset from start edge to child start
                            try {
                                lm.scrollToPositionWithOffset(pos, offset)
                            } catch (e: Exception) {
                                binding.recyclerJackets.scrollToPosition(pos)
                            }
                        } else {
                            // Child not yet laid out. Do a best-effort center then retry shortly to refine.
                            val initialOffset = if (lm.canScrollHorizontally()) binding.recyclerJackets.width / 2 else binding.recyclerJackets.height / 2
                            try { lm.scrollToPositionWithOffset(pos, initialOffset) } catch (_: Exception) { binding.recyclerJackets.scrollToPosition(pos) }
                            // Retry once after short delay to compute exact offset when the view holder is created
                            binding.recyclerJackets.postDelayed({
                                try {
                                    val child2 = binding.recyclerJackets.findViewHolderForAdapterPosition(pos)?.itemView
                                    if (child2 != null) {
                                        val offset2 = if (lm.canScrollHorizontally()) (binding.recyclerJackets.width / 2) - (child2.width / 2) else (binding.recyclerJackets.height / 2) - (child2.height / 2)
                                        try { lm.scrollToPositionWithOffset(pos, offset2) } catch (_: Exception) { }
                                    }
                                } catch (_: Exception) {}
                            }, 50L)
                        }
                    } else {
                        // layout manager not linear (unexpected) -> fallback
                        binding.recyclerJackets.scrollToPosition(pos)
                    }
                } catch (e: Exception) {
                    // final fallback
                    try { binding.recyclerJackets.scrollToPosition(pos) } catch (_: Exception) {}
                }
            }
        }
    }

    // retry state for pending instant scroll
    private var pendingScrollAttempts = 0
    private val PENDING_SCROLL_MAX_ATTEMPTS = 6
    private val PENDING_SCROLL_DELAY_MS = 200L

    private fun attemptConsumePendingInstantScroll() {
        if (!mainViewModel.pendingInstantScroll) return
        if (!::jacketAdapter.isInitialized) {
            Log.d("MainActivity", "attemptConsume: adapter not initialized yet")
        }
        val hasItems = ::jacketAdapter.isInitialized && jacketAdapter.itemCount > 0
        val currentTrack = musicService?.getCurrentTrack()
        Log.d("MainActivity", "attemptConsumePendingInstantScroll: attempt=$pendingScrollAttempts hasItems=$hasItems currentTrackPresent=${currentTrack != null}")
        if (hasItems && currentTrack != null) {
            Log.d("MainActivity", "attemptConsumePendingInstantScroll: conditions met -> performing instant scroll")
            scrollToTrackInstant()
            mainViewModel.pendingInstantScroll = false
            pendingScrollAttempts = 0
            return
        }

        // otherwise schedule retry if attempts remain
        pendingScrollAttempts++
        if (pendingScrollAttempts < PENDING_SCROLL_MAX_ATTEMPTS) {
            binding.recyclerJackets.postDelayed({
                attemptConsumePendingInstantScroll()
            }, PENDING_SCROLL_DELAY_MS)
        } else {
            Log.w("MainActivity", "attemptConsumePendingInstantScroll: max attempts reached, giving up")
            mainViewModel.pendingInstantScroll = false
            pendingScrollAttempts = 0
        }
    }

    private fun schedulePendingInstantScrollTry() {
        pendingScrollAttempts = 0
        attemptConsumePendingInstantScroll()
    }

    // Helper: apply current track to the UI (title, etc, album art) and reset marquee/slide state
    private fun applyCurrentTrackToUi(track: Track?) {
        runOnUiThread {
            if (track != null) {
                binding.root.background = getDrawble_forRootBackgroundByTimeAndOrientation(resources.configuration.orientation,this@MainActivity)

                stopSlideLoop()
                binding.TitleView.text = track.title
                binding.MusicEtcView.text = "${track.artist} / ${track.album}"
                try {
                    binding.TitleView.translationX = 0f
                    binding.TitleView.alpha = 1f
                    binding.MusicEtcView.translationX = 0f
                    binding.MusicEtcView.alpha = 1f
                } catch (_: Exception) {}
                currentSlideTarget = SlideTarget.TITLE
                startSlideLoop()


                binding.currentAlbumArt.setImageBitmap(track.albumArt ?: BitmapFactory.decodeResource(resources,R.drawable.default_album_art) )
            } else {
                stopSlideLoop()
                binding.currentAlbumArt.setImageBitmap(BitmapFactory.decodeResource(resources,R.drawable.default_album_art) )
                binding.TitleView.text = ""
                binding.MusicEtcView.text = ""
                updateMarqueeFor(binding.TitleView)
                updateMarqueeFor(binding.MusicEtcView)
            }
        }
    }
}
