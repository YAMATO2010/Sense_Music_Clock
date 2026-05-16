package jp.gr.java_conf.SenseMusicClock.ui

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import jp.gr.java_conf.SenseMusicClock.BackgroundResolver
import jp.gr.java_conf.SenseMusicClock.Clock.ClockUiController
import jp.gr.java_conf.SenseMusicClock.IDENTIFIER_INITIAL_INDEX_PROBLEM
import jp.gr.java_conf.SenseMusicClock.LocalMusicRepository
import jp.gr.java_conf.SenseMusicClock.MainViewModel
import jp.gr.java_conf.SenseMusicClock.Music.Data.BlockList
import jp.gr.java_conf.SenseMusicClock.Music.Data.DBManager
import jp.gr.java_conf.SenseMusicClock.Music.JacketAdapter
import jp.gr.java_conf.SenseMusicClock.Music.MusicSearcherByList
import jp.gr.java_conf.SenseMusicClock.Music.StorageAccessHelper
import jp.gr.java_conf.SenseMusicClock.MusicService
import jp.gr.java_conf.SenseMusicClock.PrefsManager
import jp.gr.java_conf.SenseMusicClock.R
import jp.gr.java_conf.SenseMusicClock.app_dir
import jp.gr.java_conf.SenseMusicClock.calculateScrollSpeed
import jp.gr.java_conf.SenseMusicClock.databinding.ActivityMainBinding
import jp.gr.java_conf.SenseMusicClock.dpToPx

import jp.gr.java_conf.SenseMusicClock.load_forRoot
import jp.gr.java_conf.SenseMusicClock.scrollToPositionCentered
import jp.gr.java_conf.SenseMusicClock.showBlockSelectDialog
import jp.gr.java_conf.SenseMusicClock.showPlaylistSelectDialog
import jp.gr.java_conf.SenseMusicClock.smoothScrollToPositionWithSkipAnimationCheck
import jp.gr.java_conf.SenseMusicClock.toBlocklistItem
import jp.gr.java_conf.SenseMusicClock.toFileItem
import jp.gr.java_conf.SenseMusicClock.ui.search.SearchActivity
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding


    private var mediaBrowser: MediaBrowser? = null

    var storageAccessHelper: StorageAccessHelper? = null

    // 追加: アダプタをクラスプロパティ化
    private var jacketAdapter: JacketAdapter? = null

    private lateinit var token: SessionToken

    // ViewModel を使って向きと pendingInstantScroll を保持
    private lateinit var mainViewModel: MainViewModel
    private var musicSearcher: MusicSearcherByList? = null

    private lateinit var tracks: LiveData<MutableList<MediaItem>>

    private var orientation: Int = 1

    // ClockUiController to manage Timer/Alarm/Stopwatch UI
    private var clockUiController: ClockUiController? = null

    private var lastSourceBackGround = ""


    val timeTickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_TIME_TICK) {
                // 1分経つごとに呼ばれる
                // ロゴ背景の更新を試みる。頻度が高いので、前回と同じ背景なら更新しないようにして無駄な処理を避ける
                lifecycleScope.launch {

                    if (::binding.isInitialized && context != null && BackgroundResolver.loadBackgroundSource(
                            context,
                            orientation
                        ).key != lastSourceBackGround
                    ) {
                        lastSourceBackGround =
                            binding.bgImageView.load_forRoot(context, orientation)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("LeakCheck", "MainActivity onCreate $this")


        binding = ActivityMainBinding.inflate(layoutInflater)

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
        setContentView(binding.root)


        ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.Companion.getInstance(application)
        )
        mainViewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        orientation = resources.configuration.orientation
        tracks = mainViewModel.tracks

        binding.textClock.setOnClickListener {
            mainViewModel.reverseIsHHmm()

        }




        createAppFolderIfNeeded()

        storageAccessHelper = StorageAccessHelper(
            this,
            onDirectoryPicked = { _, _ -> },
            onPermissionGranted = { read_music_Granted_foronCreate() },
            onPermissionDenied = {
                allView_onParmissionInvalid()
                //TODO 権限拒否時の処理
                Log.w("MainActivity", "音楽読み取り権限が拒否されました。")
            }

        ).also {
            it.ensureReadAudioPermission()
            lifecycleScope.launch {

                it.isGrantedFlow.collect { value ->
                    if (value) {
                        read_music_Granted_foronCreate()
                    } else {
                        allView_onParmissionInvalid()
                    }
                }
            }
        }
        mainViewModel.isHHmm.observe(this){ value ->
            if (value) {
                binding.textClock.format24Hour = "HH:mm"
                binding.textClock.format12Hour = "hh:mm"
            } else {
                binding.textClock.format24Hour = "HH:mm:ss"
                binding.textClock.format12Hour = "HH:mm:ss"
            }

        }


    }


    override fun onStart() {
        super.onStart()

        if (storageAccessHelper?.isGrantedFlow?.value == true) {
            jacketAdapterInitialize()
            read_music_Granted()
        } else {
            allView_onParmissionInvalid()
        }

        if (mainViewModel.isHHmm.value ?: false){
            binding.textClock.format24Hour = "HH:mm"
            binding.textClock.format12Hour = "hh:mm"
        } else {
            binding.textClock.format24Hour = "HH:mm:ss"
            binding.textClock.format12Hour = "HH:mm:ss"
        }

        lifecycleScope.launch {

            storageAccessHelper?.isGrantedFlow?.collect { value ->
                if (value) {
                    jacketAdapterInitialize()
                    read_music_Granted()
                } else {
                    allView_onParmissionInvalid()
                }

            }

        }


    }

    private fun allView_onParmissionInvalid(isInvalid: Boolean = true) {
        fun listener() {
            if (isInvalid) {
                Toast.makeText(
                    this,
                    "設定ボタンを押して、権限を追加してください",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        fun set(views: List<View>) {

            views.forEach {

                it.setOnClickListener {
                    listener()

                }
                it.setOnLongClickListener {
                    listener()
                    true
                }
            }


        }
        if (isInvalid) {

            binding.run {
                set(
                    listOf(
                        alarmButton,
                        recyclerJackets,
                        textClock,
                        TimerButton,
                        stopWatchBtn,
                        searchKeywordInput,
                        TitleView,
                        MusicEtcView,
                        currentAlbumArt,
                        GoSearchButton,
                        GotoStandardPlayerButton
                    )
                )

                binding.SettingsButton.setOnClickListener {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        // 自分のアプリのパッケージ名を指定して「このアプリの設定」を開く
                        data = Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                }


            }
        }

    }

    fun jacketAdapterInitialize(): Boolean {
        try {

            if (jacketAdapter == null) {
                jacketAdapter =
                    JacketAdapter(
                        emptyList(),
                        R.drawable.default_album_art,
                        this,
                        onItemClick = { clickedTrack -> jacketAdapter_onItemClick(clickedTrack) },
                        onItemLongClick = { clickedTrack, view ->
                            jacketAdapter_onItemLongClick(
                                clickedTrack,
                                view
                            )
                        })
            }
                binding.recyclerJackets.adapter = jacketAdapter
            return true
        } catch (e: Exception) {
            Log.w("MainActivity", "jacketAdapterInitialize failed", e)
            jacketAdapter = null
            return false
        }


    }

    private fun popupMoves(view: View) {
        val popupMenu = PopupMenu(this, view).also {
            it.run {
                menuInflater.inflate(R.menu.first_or_last, menu)
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.first -> {

                            lifecycleScope.launch {
                                binding.recyclerJackets.smoothScrollToPositionWithSkipAnimationCheck(
                                    0,
                                    10F

                                )
                            }

                            true
                        }

                        R.id.last -> {

                            lifecycleScope.launch {
                                val position = (jacketAdapter?.itemCount ?: 1) - 1
                                binding.recyclerJackets.smoothScrollToPositionWithSkipAnimationCheck(
                                    position,
                                    10f
                                )

                            }



                            true
                        }

                        else -> false
                    }

                }
            }
        }
        popupMenu.show()
    }

    private fun jacketAdapter_onItemLongClick(clickedTrack: MediaItem, view: View) {

        val popup = PopupMenu(this, view).also {
            it.run {


                menuInflater.inflate(R.menu.block_or_play_list, menu)
                setOnMenuItemClickListener { item ->
                    val newItem = clickedTrack.toFileItem()
                    when (item.itemId) {
                        R.id.playlist -> {
                            // プレイリストに追加する処理

                            if (newItem?.relativePath?.isBlank()
                                    ?: return@setOnMenuItemClickListener false
                            ) {
                                Log.e(
                                    "jacketAdapter_onItemLongClick",
                                    "getExtra_MediaItem for relative path returned null for track ${clickedTrack.mediaMetadata.title}"
                                )
                                return@setOnMenuItemClickListener false
                            }
                            if (newItem.fileName.isBlank()) {
                                Log.e(
                                    "jacketAdapter_onItemLongClick",
                                    "getExtra_MediaItem for display name returned null for track ${clickedTrack.mediaMetadata.title}"
                                )
                                return@setOnMenuItemClickListener false
                            }






                            lifecycleScope.launch {
                                Log.d(
                                    "LIST_/MainActivity/loadPlaylist",
                                    "loading playlists for jacket long click"
                                )
                                val playlists = DBManager.loadPlaylist(this@MainActivity)
                                showPlaylistSelectDialog(playlists, newItem) { playlistId ->
                                    Log.d("MainActivity", "Selected playlist ID: $playlistId")
                                    lifecycleScope.launch {

                                        DBManager.addPlaylistItem(
                                            this@MainActivity.applicationContext,
                                            playlistId,
                                            newItem
                                        )
                                    }


                                }
                            }
                            Log.d(
                                "MainActivity",
                                "Add to playlist: ${clickedTrack.mediaMetadata.title}"
                            )
                            true
                        }

                        R.id.blocklist -> {


                            if (newItem?.relativePath?.isBlank()
                                    ?: return@setOnMenuItemClickListener false
                            ) {
                                return@setOnMenuItemClickListener false
                            }
                            if (newItem.fileName.isBlank()) {
                                return@setOnMenuItemClickListener false
                            }
                            lifecycleScope.launch {
                                val blockLists: List<BlockList> =
                                    DBManager.loadBlocklist(this@MainActivity)

                                showBlockSelectDialog(blockLists, newItem) { id ->
                                    Log.d("MainActivity", "Selected block list ID: $it")
                                    val newBlockItem = newItem.toBlocklistItem(id)
                                    lifecycleScope.launch {
                                        DBManager.upsertBlocklistItem(
                                            this@MainActivity.applicationContext,
                                            newBlockItem
                                        )
                                    }

                                }
                            }


                            // ブロックリストに追加する処理
                            Log.d(
                                "MainActivity",
                                "Add to blocklist: ${clickedTrack.mediaMetadata.title}"
                            )
                            true
                        }

                        else -> false
                    }

                }
            }
        }
        popup.show()

    }


    private fun jacketAdapter_onItemClick(clickedTrack: MediaItem) {
        val clickedTrackMetadata = clickedTrack.mediaMetadata
        Log.d(
            "MainActivity",
            "track clicked: ${clickedTrackMetadata.title}" + IDENTIFIER_INITIAL_INDEX_PROBLEM
        )
        try {
            // If it's a local track, prefer asking the service to play by index (service has authoritative queue)
            var sent = false


            val index = getIndexById(clickedTrack)
            if (index != null && index >= 0) {

                sent = try {

                    mediaBrowser?.seekTo(index, 0L)
                    mediaBrowser?.play()

                    true
                } catch (e: Exception) {

                    Log.w("MainActivity", "seekTo via MediaBrowser failed", e)
                    false
                }
            }


            // If we couldn't send via MediaController (or it's not a localTrack/service didn't know it), fallback to direct setQueue/play
            if (!sent) {
                val index = jacketAdapter?.getItemPosition(clickedTrack) ?: -1
                if (index >= 0) {

                    mediaBrowser?.seekTo(index, 0L)
                    mediaBrowser?.play()

                }
            }
        } catch (e: Exception) {
            Log.w("MainActivity", "play request failed", e)
        }

        // update texts and restart slide loop (Title -> Etc)
        stopSlideLoop()
        binding.TitleView.text = clickedTrackMetadata.title
        binding.MusicEtcView.text =
            "${clickedTrackMetadata.artist} / ${clickedTrackMetadata.albumTitle}"
        // update marquee state for both views
        updateMarqueeFor(binding.TitleView)
        updateMarqueeFor(binding.MusicEtcView)


        currentSlideTarget = SlideTarget.TITLE
        startSlideLoop()

        // load album art with Coil; use clickedTrack.albumArtUri when present, otherwise fallback to default drawable
        try {
            val uri = clickedTrackMetadata.artworkUri
            if (uri != null) {
                binding.currentAlbumArt.load(uri) {
                    placeholder(R.drawable.default_album_art)
                    error(R.drawable.default_album_art)
                    crossfade(true)
                }
            } else {
                binding.currentAlbumArt.load(R.drawable.default_album_art)
            }
        } catch (e: Exception) {
            Log.w("MainActivity", "failed to load clickedTrack album art via Coil", e)
            try {
                binding.currentAlbumArt.load(R.drawable.default_album_art)
            } catch (ex: Exception) {
                Log.w("MainActivity", "fallback load clickedTrack album art failed", ex)
            }
        }


    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(timeTickReceiver)
    }

    override fun onResume() {
        super.onResume()

        registerReceiver(timeTickReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
        // On resume: if orientation changed since last time, set pending instant scroll and reconfigure layout

        lifecycleScope.launch {

            lastSourceBackGround = binding.bgImageView.load_forRoot(this@MainActivity, orientation)
        }
        mediaBrowser?.let {
            runOnUiThread {
                mainViewModel.clearTracks()
            }
            val newList = mutableListOf<MediaItem>()
            loadAllMedias(newList, it)
        }
    }

    private fun getIndexById(mediaItem: MediaItem?): Int? {
        if (mediaItem == null) return null
        return LocalMusicRepository.getIndexById(mediaItem.mediaId)
    }

    override fun onStop() {
        super.onStop()


        mediaBrowser?.release()
        mediaBrowser = null
        binding.recyclerJackets.adapter = null
        jacketAdapter = null


        // stop sliding animations when activity is not visible
        stopSlideLoop()

    }

    override fun onDestroy() {
        super.onDestroy()

        // destroy clock controller (it unregisters its own receiver)
        try {
            binding.textClock.format24Hour = null
            binding.textClock.format12Hour = null

            clockUiController?.let {
                it.onDestroy()
                it.destroy()
            }
            unregisterReceiver(timeTickReceiver)
            clockUiController = null
            storageAccessHelper = null
            musicSearcher = null

        } catch (e: Exception) {
            Log.w("MainActivity", "clockUiController destroy failed", e)
        }
    }

    private fun read_music_Granted() {

        setLoadingVisible(true)

        token = SessionToken(this, ComponentName(this, MusicService::class.java))
        val browserFuture = MediaBrowser.Builder(this, token).buildAsync()


        browserFuture.addListener({
            mediaBrowser = browserFuture.get()
            // これでサービスと接続完了！再生操作などができるようになる

            mediaBrowser?.let {


                if (mainViewModel.lastOrientation != resources.configuration.orientation) {
                    binding.recyclerJackets.scrollToPositionCentered(it.currentMediaItemIndex)
                }
                mainViewModel.lastOrientation = resources.configuration.orientation

                it.addListener(object : Player.Listener {
                    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                        super.onTimelineChanged(timeline, reason)



                        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                            runOnUiThread {
                                mainViewModel.clearTracks()
                            }
                            val newList = mutableListOf<MediaItem>()
                            loadAllMedias(newList, it)
                        }


                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        super.onMediaItemTransition(mediaItem, reason)
                        Log.d("MainActivity", "onMediaItemTransition: $mediaItem")

                        runOnUiThread {
                            if (mediaItem != null) {
                                applyCurrentTrackToUi(mediaItem)
                            } else {
                                stopSlideLoop()
                                binding.currentAlbumArt.load(R.drawable.default_album_art)
                                binding.TitleView.text = ""
                                binding.MusicEtcView.text = ""
                                updateMarqueeFor(binding.TitleView)
                                updateMarqueeFor(binding.MusicEtcView)
                            }
                        }
                    }


                })
                runOnUiThread {
                    mainViewModel.clearTracks()
                }
                val newList = mutableListOf<MediaItem>()
                loadAllMedias(newList, it)


            }


        }, ContextCompat.getMainExecutor(this))


    }

    private fun read_music_Granted_foronCreate() {


        val profilesDir = File(filesDir, "profiles")
        if (!profilesDir.exists()) {
            profilesDir.mkdir()
        }

        jacketAdapterInitialize()
        binding.recyclerJackets.adapter = jacketAdapter




        // RecyclerView のレイアウトは configureRecyclerForOrientation にまとめる

        configureRecyclerForOrientation(orientation)
        // 初期向きを ViewModel に記録
        if (mainViewModel.lastOrientation == null) mainViewModel.lastOrientation = orientation



        binding.SettingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        binding.recyclerJackets.setHasFixedSize(false)


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
                    override fun getItemOffsets(
                        outRect: Rect,
                        view: View,
                        parent: RecyclerView,
                        state: RecyclerView.State
                    ) {
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
                    override fun getItemOffsets(
                        outRect: Rect,
                        view: View,
                        parent: RecyclerView,
                        state: RecyclerView.State
                    ) {
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

        binding.currentAlbumArt.run {

            setOnClickListener {
                scrollToTrack()
            }

            setOnLongClickListener { view ->
                popupMoves(view)
                true
            }

        }


        binding.GoSearchButton.setOnClickListener {
            val intent = Intent(this, SearchActivity::class.java)
            startActivity(intent)
        }



        binding.GotoStandardPlayerButton.setOnClickListener {
            val intent = Intent(this, StandardPlayerActivity::class.java)
            startActivity(intent)
        }


        // Initialize ClockUiController to handle Timer/Alarm/Stopwatch UI independently
        clockUiController = ClockUiController(this, binding)
        clockUiController?.init()

        // Debug: ensure nowLoading views exist and tint is applied early; use unified setter so parent overlay is shown
        try {
            binding.nowLoadingBar.indeterminateDrawable?.setTint(Color.WHITE)

        } catch (e: Exception) {
            Log.w("MainActivity", "failed to initialize nowLoading views", e)
        }
    }

    private fun loadAllMedias(list: MutableList<MediaItem>, browser: MediaBrowser, page: Int = 0) {
        val pageSize = 300 // 一度に取得するアイテム数を指定
        val future = browser.getChildren("ROOT_ID", page, pageSize, null)
        future.addListener({
            val result = future.get()
            val items = result?.value ?: return@addListener

            if (items.isNotEmpty()) {
                // Activity側のリストに追加
                list.addAll(items)


                // 次のページをリクエスト（再帰的に呼ぶ）
                loadAllMedias(list, browser, page + 1)
            } else {
                // すべてのアイテムを取得し終わった後の処理
                Log.d("MainActivity", "All media items loaded: total=${tracks.value?.size ?: 0}")
                loadAllMediasComplete(list, browser)

            }
        }, ContextCompat.getMainExecutor(this))
    }


    private fun loadAllMediasComplete(newList: MutableList<MediaItem>, browser: MediaBrowser) {

        Log.d("loadAllMediasComplete", " loadAllMediasComplete called")
        mainViewModel.setTracks(newList)
        setLoadingVisible(false)


        val tracks = tracks.value ?: emptyList()
        Log.d("track viewmodel", "tracks Size:${tracks.size}, tracksHash:${tracks.hashCode()}")


        runOnUiThread {

            val current = browser.currentMediaItem
            val currentIndex = getIndexById(current)

            // adapter にリストを渡し、コミット後に現在トラックへスクロールする
            jacketAdapter?.let {


                it.setItems(tracks) {
                    // commit完了後に呼ばれる。adapter に要素が入っていればローディングを消す
                    val hasItems = it.getItems().isNotEmpty()
                    Log.d(
                        "MainActivity",
                        "commitCallback after setItems: hasItems=$hasItems, tracksSize=${tracks.size}"
                    )
                    val jacketAdaptertracks = it.getItems()
                    Log.d(
                        "track viewmodel/adapter",
                        "viewmodel : [${tracks[1].mediaMetadata.title} ,${tracks[2].mediaMetadata.title}, ${tracks[3].mediaMetadata.title}], adapter : [${jacketAdaptertracks[1].mediaMetadata.title} ,${jacketAdaptertracks[2].mediaMetadata.title} ,${jacketAdaptertracks[3].mediaMetadata.title} ]"
                    )

                    // commit 完了後に現在再生トラックがあればリスト上で追従してスクロールする
                    if (hasItems) {
                        if (currentIndex != null) {
                            // If orientation changed since last known by ViewModel, ensure instant scroll
                            val curOrient = resources.configuration.orientation
                            if (mainViewModel.lastOrientation != null && mainViewModel.lastOrientation != curOrient) {
                                Log.d(
                                    "MainActivity",
                                    "commitCallback: orientation change detected (${mainViewModel.lastOrientation} -> $curOrient) -> instant scroll"
                                )
                                scrollToTrack()
                                mainViewModel.pendingInstantScroll = false
                                mainViewModel.lastOrientation = curOrient
                            } else if (mainViewModel.pendingInstantScroll) {
                                Log.d(
                                    "MainActivity",
                                    "commitCallback: performing pending instant scroll"
                                )
                                scrollToTrack()
                                mainViewModel.pendingInstantScroll = false
                            } else {
                                scrollToTrack()
                            }
                        }
                    }
                }
            }
            // If a pending instant-scroll is still set after the initial adapter commit, start retry scheduler
            if (mainViewModel.pendingInstantScroll) {
                Log.d(
                    "MainActivity",
                    "onServiceConnected: pendingInstantScroll detected -> scheduling retries"
                )
                schedulePendingInstantScrollTry()
            }

            // tracksFlow を監視して差分更新（コミット後に現在再生トラックがあれば追従）
            lifecycleScope.launch {


                PrefsManager.getTileTitleDisplayFlow(this@MainActivity).collect {
                    jacketAdapter?.notifyItemChanged(0, jacketAdapter?.itemCount)
                }


            }
            if (current != null) {

                applyCurrentTrackToUi(current)
                stopSlideLoop()
                // use marquee chain
                currentSlideTarget = SlideTarget.TITLE
                startSlideLoop()
            }


        }
        // scrolling was moved into the adapter commit callback; avoid duplicate calls here


        musicSearcher = MusicSearcherByList(
            this@MainActivity,
            binding.SearchResultView,
            binding.searchKeywordInput,
            binding.recyclerJackets
        )
        musicSearcher?.ini()


    }

    private fun createAppFolderIfNeeded() {
        val resolver = contentResolver
        val relPathPattern = "Music/SMC%"
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf(relPathPattern)

        resolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.count > 0) {
                Log.d("MainActivity", "app folder already exists")
                return
            }
        }

        val values = app_dir
        try {
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            Log.d("MainActivity", "フォルダ作成結果: $uri")
        } catch (e: Exception) {
            Log.w("MainActivity", "フォルダ作成に失敗しました", e)
        }
    }


    // Slide target enum preserved so remaining call sites compile and can be wired to a library later
    private enum class SlideTarget { TITLE }

    private var currentSlideTarget = SlideTarget.TITLE
// marqueeManager removed to disable custom animation implementation

    // start/stop are now no-ops: animations removed. They reset view visual state and ensure marquee flags are applied.
    private fun startSlideLoop() {

    }

    private fun stopSlideLoop() {

    }

    // Helper: enable marquee for TextView if text is wider than container; otherwise center it
    private fun updateMarqueeFor(@Suppress("UNUSED_PARAMETER") tv: TextView) {
        // intentionally no-op in current simplified UI; parameter kept to preserve API
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
                val overlay = try {
                    binding.root.findViewById<View>(R.id.loadingOverlay)
                } catch (e: Exception) {
                    Log.w("MainActivity", "findViewById loadingOverlay failed", e); null
                }
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
                // Fallback safety + log
                Log.w(
                    "MainActivity",
                    "setLoadingVisible inner error; falling back to direct child toggle",
                    e
                )
                binding.nowLoadingBar.visibility = vis
                binding.nowLoadingTextView.visibility = vis
            }

            if (visible) {
                // bring to front so overlay receives touches and appears above content
                try {
                    binding.nowLoadingBar.bringToFront()
                    binding.nowLoadingTextView.bringToFront()
                } catch (e: Exception) {
                    Log.w("MainActivity", "bring to front failed", e)
                }
            } else {
                // If hiding, ensure root and main content are visible (protect against earlier bugs setting ancestors GONE)
                try {
                    binding.root.visibility = View.VISIBLE
                } catch (e: Exception) {
                    Log.w("MainActivity", "set root visibility failed", e)
                }
            }
            Log.d(
                "MainActivity",
                "setLoadingVisible($visible) applied overlayVisible=$vis rootVisible=${binding.root.visibility}"
            )
        } catch (e: Exception) {
            Log.w("MainActivity", "setLoadingVisible failed", e)
        }
    }

    private fun scrollToTrack(
        recyclerView: RecyclerView? = binding.recyclerJackets,
        track: MediaItem? = mediaBrowser?.currentMediaItem
    ) {
        val adapter = recyclerView?.adapter as? JacketAdapter


        if (adapter == null) return
        if (track == null) return
        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
        val firstVisible = layoutManager.findFirstVisibleItemPosition()

        val scrollPos = adapter.getItemPosition(track)

        //70ぐらい違うと結構速い
        val speed = calculateScrollSpeed(firstVisible, scrollPos ?: (firstVisible - 70))
        lifecycleScope.launch {
            if (scrollPos != null) recyclerView.smoothScrollToPositionWithSkipAnimationCheck(
                scrollPos,
                speed
            )
        }


    }

    // Configure recycler / padding depending on orientation
    private fun configureRecyclerForOrientation(orientation: Int) {
        try {
            if (orientation == 1) {
                // portrait: 1 列表示

                binding.recyclerJackets.layoutManager =
                    LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
                binding.recyclerJackets.setPadding(this.dpToPx(8), 0, this.dpToPx(8), 0)
                binding.recyclerJackets.clipToPadding = false
            } else {
                // landscape: 横一列でスクロールするレイアウトにする
                val linear = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
                binding.recyclerJackets.layoutManager = linear
                binding.recyclerJackets.setPadding(
                    this.dpToPx(4),
                    this.dpToPx(4),
                    this.dpToPx(4),
                    this.dpToPx(4)
                )
                binding.recyclerJackets.clipToPadding = false
            }
            // 背景は既存ロジックに任せる（別箇所で更新されるためここでは無害）
        } catch (e: Exception) {
            Log.w("MainActivity", "configureRecyclerForOrientation failed", e)
        }
    }

    // Find position of a track in the adapter with fallbacks (id match -> metadata match)
    private fun getPositionForTrack(adapter: JacketAdapter, track: MediaItem?): Int? {
        // Prefer adapter's getItemPosition (handles localTrack/SpotifyTrack ids)
        val byId = adapter.getItemPosition(track)
        if (byId != null) return byId
        if (track == null) return null
        val metadata = track.mediaMetadata

        // Fallback: match by title/artist/album (loose)
        val title = metadata.title
        val artist = metadata.artist
        val album = metadata.albumTitle
        val idx = adapter.getItems().indexOfFirst {
            val itMetadata = it.mediaMetadata

            itMetadata.title == title && itMetadata.artist == artist && itMetadata.albumTitle == album
        }


        Log.d("positionForTrack", "getPositionForTrack: fallback by metadata idx=$idx")
        return if (idx >= 0) idx else null
    }

    // Instant scroll to current track without animation
    private fun scrollToTrackInstant(
        adapter: JacketAdapter? =
            if (jacketAdapter != null) jacketAdapter else (binding.recyclerJackets.adapter as? JacketAdapter),
        track: MediaItem? = mediaBrowser?.currentMediaItem
    ) {

        if (adapter == null) return
        if (track == null) return
        val pos = getPositionForTrack(adapter, track)
        Log.d(
            "MainActivity",
            "scrollToTrackInstant: resolved position=$pos for track='${track.mediaMetadata.title}'"
        )
        if (pos != null) {
            // Attempt to center the item instantly.
            binding.recyclerJackets.post {
                try {
                    val lm = binding.recyclerJackets.layoutManager
                    if (lm is LinearLayoutManager) {
                        // Try to get actual item view to compute exact offset
                        val child =
                            binding.recyclerJackets.findViewHolderForAdapterPosition(pos)?.itemView
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
                                Log.w("MainActivity", "lm.scrollToPositionWithOffset failed", e)
                                binding.recyclerJackets.scrollToPosition(pos)
                            }
                        } else {
                            // Child not yet laid out. Do a best-effort center then retry shortly to refine.
                            val initialOffset =
                                if (lm.canScrollHorizontally()) binding.recyclerJackets.width / 2 else binding.recyclerJackets.height / 2
                            try {
                                lm.scrollToPositionWithOffset(pos, initialOffset)
                            } catch (e: Exception) {
                                Log.w(
                                    "MainActivity",
                                    "lm.scrollToPositionWithOffset initialOffset failed",
                                    e
                                ); binding.recyclerJackets.scrollToPosition(pos)
                            }
                            // Retry once after short delay to compute exact offset when the view holder is created
                            binding.recyclerJackets.postDelayed({
                                try {
                                    val child2 =
                                        binding.recyclerJackets.findViewHolderForAdapterPosition(pos)?.itemView
                                    if (child2 != null) {
                                        val offset2 =
                                            if (lm.canScrollHorizontally()) (binding.recyclerJackets.width / 2) - (child2.width / 2) else (binding.recyclerJackets.height / 2) - (child2.height / 2)
                                        try {
                                            lm.scrollToPositionWithOffset(pos, offset2)
                                        } catch (e: Exception) {
                                            Log.w(
                                                "MainActivity",
                                                "lm.scrollToPositionWithOffset offset2 failed",
                                                e
                                            )
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.w(
                                        "MainActivity",
                                        "scrollToTrackInstant post retry failed",
                                        e
                                    )
                                }
                            }, 50L)
                        }
                    } else {
                        // layout manager not linear (unexpected) -> fallback
                        binding.recyclerJackets.scrollToPosition(pos)
                    }
                } catch (e: Exception) {
                    // final fallback
                    try {
                        binding.recyclerJackets.scrollToPosition(pos)
                    } catch (e: Exception) {
                        Log.w("MainActivity", "final scrollToPosition failed", e)
                    }
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
        if (jacketAdapter == null) {
            Log.d("MainActivity", "attemptConsume: adapter not initialized yet")
        }
        val hasItems = jacketAdapter != null && (jacketAdapter?.itemCount ?: 0) > 0
        val currentTrack = mediaBrowser?.currentMediaItem
        Log.d(
            "MainActivity",
            "attemptConsumePendingInstantScroll: attempt=$pendingScrollAttempts hasItems=$hasItems currentTrackPresent=${currentTrack != null}"
        )
        if (hasItems && currentTrack != null) {
            Log.d(
                "MainActivity",
                "attemptConsumePendingInstantScroll: conditions met -> performing instant scroll"
            )
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
            Log.w(
                "MainActivity",
                "attemptConsumePendingInstantScroll: max attempts reached, giving up"
            )
            mainViewModel.pendingInstantScroll = false
            pendingScrollAttempts = 0
        }
    }

    private fun schedulePendingInstantScrollTry() {
        pendingScrollAttempts = 0
        attemptConsumePendingInstantScroll()
    }

    // Helper: apply current track to the UI (title, etc, album art) and reset marquee/slide state
    private fun applyCurrentTrackToUi(track: MediaItem?) {
        runOnUiThread {
            if (track != null) {

                val metadata = track.mediaMetadata

                stopSlideLoop()
                binding.TitleView.text = metadata.title
                binding.MusicEtcView.text = "${metadata.artist} / ${metadata.albumTitle}"
                try {
                    binding.TitleView.translationX = 0f
                    binding.TitleView.alpha = 1f
                    binding.MusicEtcView.translationX = 0f
                    binding.MusicEtcView.alpha = 1f
                } catch (e: Exception) {
                    Log.w("MainActivity", "set text translation/alpha failed", e)
                }
                currentSlideTarget = SlideTarget.TITLE
                startSlideLoop()


                try {
                    val artUri = metadata.artworkUri
                    if (artUri != null) {
                        binding.currentAlbumArt.load(artUri) {
                            placeholder(R.drawable.default_album_art)
                            error(R.drawable.default_album_art)
                            crossfade(true)
                        }
                    } else {
                        binding.currentAlbumArt.load(R.drawable.default_album_art)
                    }
                } catch (e: Exception) {
                    Log.w("MainActivity", "failed to load currentTrack album art via Coil", e)
                    try {
                        binding.currentAlbumArt.load(R.drawable.default_album_art)
                    } catch (e: Exception) {
                        Log.w("MainActivity", "fallback load currentAlbumArt failed", e)
                    }
                }
            } else {
                stopSlideLoop()
                try {
                    binding.currentAlbumArt.load(R.drawable.default_album_art)
                } catch (e: Exception) {
                    Log.w(
                        "MainActivity",
                        "load default album art failed",
                        e
                    ); binding.currentAlbumArt.setImageBitmap(
                        BitmapFactory.decodeResource(
                            resources,
                            R.drawable.default_album_art
                        )
                    )
                }
                binding.TitleView.text = ""
                binding.MusicEtcView.text = ""
                updateMarqueeFor(binding.TitleView)
                updateMarqueeFor(binding.MusicEtcView)
            }
        }
    }
}