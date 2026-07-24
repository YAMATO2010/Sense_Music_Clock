package jp.gr.java_conf.SenseMusicClock.ui


import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.Color
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
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import coil.load
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.memory.MemoryCache
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.maxBitmapSize
import coil3.size.Precision
import jp.gr.java_conf.SenseMusicClock.BackgroundResolver
import jp.gr.java_conf.SenseMusicClock.Clock.ClockUiController
import jp.gr.java_conf.SenseMusicClock.IDENTIFIER_INITIAL_INDEX_PROBLEM
import jp.gr.java_conf.SenseMusicClock.LocalMusicRepository
import jp.gr.java_conf.SenseMusicClock.MainViewModel
import jp.gr.java_conf.SenseMusicClock.Music.Data.Blacklists.BlockList
import jp.gr.java_conf.SenseMusicClock.Music.Data.DBManager
import jp.gr.java_conf.SenseMusicClock.Music.MusicSearcherByList
import jp.gr.java_conf.SenseMusicClock.Music.StorageAccessHelper
import jp.gr.java_conf.SenseMusicClock.Music.MusicService
import jp.gr.java_conf.SenseMusicClock.PrefsManager
import jp.gr.java_conf.SenseMusicClock.R
import jp.gr.java_conf.SenseMusicClock.animateScrollToItemWithSkipCheck
import jp.gr.java_conf.SenseMusicClock.app_dir
import jp.gr.java_conf.SenseMusicClock.databinding.ActivityMainBinding
import jp.gr.java_conf.SenseMusicClock.load_forRoot
import jp.gr.java_conf.SenseMusicClock.showBlockSelectDialog
import jp.gr.java_conf.SenseMusicClock.showPlaylistSelectDialog
import jp.gr.java_conf.SenseMusicClock.toBlocklistItem
import jp.gr.java_conf.SenseMusicClock.toFileItem
import jp.gr.java_conf.SenseMusicClock.ui.search.SearchActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding


    private var mediaBrowser: MediaBrowser? = null

    var storageAccessHelper: StorageAccessHelper? = null

    // 追加: アダプタをクラスプロパティ化

    private lateinit var token: SessionToken

    // ViewModel を使って向きと pendingInstantScroll を保持
    private lateinit var mainViewModel: MainViewModel
    private var musicSearcher: MusicSearcherByList? = null

    private var sleepTimerUIJob: Job? = null


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
                    if (::binding.isInitialized) {

                        if (context != null && BackgroundResolver.loadBackgroundSource(
                                context,
                                orientation
                            ).key != lastSourceBackGround
                        ) {
                            lastSourceBackGround =
                                binding.bgImageView.load_forRoot(context, orientation)
                        }

                    }



                    loadSTEndATimeByService()

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
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )
        mainViewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        orientation = resources.configuration.orientation


        binding.textClock.setOnClickListener {
            mainViewModel.reverseIsHHmm()

        }


        binding.jackets.setContent {
            if (orientation == BackgroundResolver.ORIENTATION_OBLONG) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item { }

                }
            } else {
                LazyRow(modifier = Modifier.fillMaxSize()) {

                    item { }
                }

            }
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
        mainViewModel.isHHmm.observe(this) { value ->
            if (value) {
                binding.textClock.format24Hour = "HH:mm"
                binding.textClock.format12Hour = "hh:mm"
            } else {
                binding.textClock.format24Hour = "HH:mm:ss"
                binding.textClock.format12Hour = "HH:mm:ss"
            }

        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.sleepTimerEndAtTimeFlow.collectLatest { value ->
                    stopRemainingTimer()
                    if (mainViewModel.isValidSleepTimerEndAtTime(value) && value != null) {
                        startRemainingTimer(value)


                    }

                }


            }
        }


    }


    override fun onStart() {
        super.onStart()

        if (storageAccessHelper?.isGrantedFlow?.value == true) {

            read_music_Granted()
        } else {
            allView_onParmissionInvalid()
        }

        if (mainViewModel.isHHmm.value ?: false) {
            binding.textClock.format24Hour = "HH:mm"
            binding.textClock.format12Hour = "hh:mm"
        } else {
            binding.textClock.format24Hour = "HH:mm:ss"
            binding.textClock.format12Hour = "HH:mm:ss"
        }


        lifecycleScope.run {
            launch {

                storageAccessHelper?.isGrantedFlow?.collect { value ->
                    if (value) {
                        read_music_Granted()

                    } else {
                        allView_onParmissionInvalid()
                    }

                }


            }


        }

    }

    @Composable
    fun JacketsComposable(viewModel: MainViewModel) {


        val listState = rememberLazyListState()
        val imageLoader: ImageLoader = remember {
            ImageLoader.Builder(this)
                .crossfade(true)
                .allowHardware(true)
                .maxBitmapSize(coil3.size.Size(500, 500))
                .precision(Precision.INEXACT)
                .memoryCache(
                    MemoryCache.Builder()
                        .maxSizePercent(this, 0.05)
                        .build()
                )
                .build()
        }


        val item by viewModel.tracks.collectAsState(initial = emptyList())
        val isTextPlus by PrefsManager.getTileTitleDisplayFlow(this).collectAsState(initial = false)


        LaunchedEffect(Unit) {


            viewModel.currentIndex.collectLatest { value ->


                listState.animateScrollToItemWithSkipCheck(value)

            }

        }

        if (orientation == BackgroundResolver.ORIENTATION_OBLONG) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 5.dp),
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                itemsIndexed(items = item) { index, mediaItem ->
                    var menuExpanded by remember {
                        mutableStateOf(false)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)


                            .padding(vertical = 8.dp, horizontal = 5.dp)
                            .combinedClickable(
                                onClick = {
                                    // タップ
                                    jacketAdapter_onItemClick(mediaItem)
                                },
                                onLongClick = {
                                    // 長押し
                                    menuExpanded = true
                                }
                            )

                    ) {
                        if (isTextPlus) {


                            TextPlusComposable(mediaItem, imageLoader)
                        } else {
                            JacketItemComposable(mediaItem, imageLoader)
                        }


                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = {
                                menuExpanded = false
                            }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text("プレイリストに追加")
                                },
                                onClick = {
                                    addPlaylistByPopup(mediaItem) {
                                        menuExpanded = false
                                    }
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Text("ブロックリストに追加")
                                },
                                onClick = {
                                    addBlickListByPopup(mediaItem) {
                                        menuExpanded = false
                                    }

                                }
                            )
                        }

                    }
                }
            }
        } else {
            LazyRow(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(items = item, key = { _, item -> item.mediaId }) { index, mediaItem ->
                    var menuExpanded by remember {
                        mutableStateOf(false)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f)


                            .padding(10.dp)
                            .combinedClickable(
                                onClick = {
                                    // タップ
                                    jacketAdapter_onItemClick(mediaItem)
                                },
                                onLongClick = {
                                    // 長押し
                                    menuExpanded = true
                                }
                            )

                    ) {

                        if (isTextPlus) {


                            TextPlusComposable(mediaItem, imageLoader)
                        } else {
                            JacketItemComposable(mediaItem, imageLoader)
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = {
                                menuExpanded = false
                            }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text("プレイリストに追加")
                                },
                                onClick = {
                                    addPlaylistByPopup(mediaItem) {
                                        menuExpanded = false
                                    }
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Text("ブロックリストに追加")
                                },
                                onClick = {
                                    addBlickListByPopup(mediaItem) {
                                        menuExpanded = false
                                    }

                                }
                            )
                        }


                    }
                }
            }
        }


    }

    fun addPlaylistByPopup(mediaItem: MediaItem, menuDismiss: () -> Unit) {

        menuDismiss()
        val newItem = mediaItem.toFileItem()
        // プレイリストに追加する処理

        if (newItem?.relativePath?.isBlank()
                ?: return
        ) {
            Log.e(
                "jacketAdapter_onItemLongClick",
                "getExtra_MediaItem for relative path returned null for track ${newItem.relativePath} / ${newItem.fileName}"
            )
            return
        }
        if (newItem.fileName.isBlank()) {
            Log.e(
                "jacketAdapter_onItemLongClick",
                "getExtra_MediaItem for display name returned null for track ${newItem.relativePath} / ${newItem.fileName}"
            )
            return
        }






        lifecycleScope.launch {
            Log.d(
                "LIST_/MainActivity/loadPlaylist",
                "loading playlists for jacket long click"
            )
            val playlists = DBManager.loadPlaylist(this@MainActivity)
            showPlaylistSelectDialog(playlists, newItem) { playlistId ->
                Log.d(
                    "MainActivity",
                    "Selected playlist ID: $playlistId"
                )
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
            "Add to playlist: ${newItem.relativePath} / ${newItem.fileName}"
        )
    }

    fun addBlickListByPopup(mediaItem: MediaItem, menuDismiss: () -> Unit) {

        val newItem = mediaItem.toFileItem()

        if (newItem?.relativePath?.isBlank()
                ?: return
        ) {
            return
        }
        if (newItem.fileName.isBlank()) {
            return
        }
        lifecycleScope.launch {
            val blockLists: List<BlockList> =
                DBManager.loadBlocklist(this@MainActivity)

            showBlockSelectDialog(blockLists, newItem) { id ->
                Log.d("MainActivity", "Selected block list ID: $id")
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
            "Add to blocklist: ${newItem.relativePath} / ${newItem.fileName}"
        )


    }

    @Composable
    fun JacketItemComposable(mediaItem: MediaItem, imageLoader: ImageLoader) {

        val artworkUri = mediaItem.mediaMetadata.artworkUri


        AsyncImage(
            model = artworkUri,
            contentDescription = title.toString(),
            imageLoader = imageLoader,
            placeholder = painterResource(R.drawable.default_album_art),
            error = painterResource(R.drawable.default_album_art),
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(1f), // 表示領域を正方形に
            contentScale = ContentScale.Fit,


            )

    }


    @Composable
    fun TextPlusComposable(mediaItem: MediaItem, imageLoader: ImageLoader) {
        val title = mediaItem.mediaMetadata.title ?: "Unknown Title"
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colorResource(R.color.white_glass_item_square)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            AsyncImage(
                model = mediaItem.mediaMetadata.artworkUri,
                contentDescription = title.toString(),
                imageLoader = imageLoader,
                placeholder = painterResource(R.drawable.default_album_art),
                error = painterResource(R.drawable.default_album_art),
                modifier = Modifier
                    .weight(5f)
                    .aspectRatio(1f), // 表示領域を正方形に
                contentScale = ContentScale.Fit,
            )
            Text(
                text = title.toString(),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 2.dp, horizontal = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(
                    maxFontSize = 15.sp
                ),
                textAlign = TextAlign.Center,
                color = androidx.compose.ui.graphics.Color.Black

            )


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
                        jackets,
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


    private fun popupMoves(view: View) {
        val popupMenu = PopupMenu(this, view).also {
            it.run {
                menuInflater.inflate(R.menu.first_or_last, menu)
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.first -> {

                            lifecycleScope.launch {

                                mainViewModel.setCurrentIndex(0)

                            }

                            true
                        }

                        R.id.last -> {

                            lifecycleScope.launch {
                                mainViewModel.setCurrentIndex(mainViewModel.tracks.value.size - 1)


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
                val index = mainViewModel.lastIndex ?: 0
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


        // stop sliding animations when activity is not visible
        stopSlideLoop()

    }

    override fun onDestroy() {
        super.onDestroy()

        stopRemainingTimer()

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
                loadSTEndATimeByService()
                runOnUiThread {
                    mainViewModel.clearTracks()
                }
                val newList = mutableListOf<MediaItem>()
                loadAllMedias(newList, it)


                if (mainViewModel.lastOrientation != resources.configuration.orientation) {

                    mainViewModel.setCurrentIndex(it.currentMediaItemIndex)
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
                                binding.currentAlbumArt.load(R.drawable.default_album_art) {
                                    size(512, 512)
                                    precision(coil.size.Precision.INEXACT)

                                    crossfade(false)
                                }
                                binding.TitleView.text = ""
                                binding.MusicEtcView.text = ""
                                updateMarqueeFor(binding.TitleView)
                                updateMarqueeFor(binding.MusicEtcView)
                            }
                        }

                    }


                })


            }


        }, ContextCompat.getMainExecutor(this))


    }

    private fun read_music_Granted_foronCreate() {


        val profilesDir = File(filesDir, "profiles")
        if (!profilesDir.exists()) {
            profilesDir.mkdir()
        }


        // 初期向きを ViewModel に記録
        if (mainViewModel.lastOrientation == null) mainViewModel.lastOrientation = orientation



        binding.SettingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
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
        binding.jackets.setContent {
            JacketsComposable(mainViewModel)
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
                Log.d(
                    "MainActivity",
                    "All media items loaded: total=${mainViewModel.tracks.value.size}"
                )
                loadAllMediasComplete(list, browser)

            }
        }, ContextCompat.getMainExecutor(this))
    }


    private fun loadAllMediasComplete(newList: MutableList<MediaItem>, browser: MediaBrowser) {

        Log.d("loadAllMediasComplete", " loadAllMediasComplete called")
        mainViewModel.setTracks(newList)
        setLoadingVisible(false)


        val tracks = mainViewModel.tracks.value
        Log.d("track viewmodel", "tracks Size:${tracks.size}, tracksHash:${tracks.hashCode()}")


        runOnUiThread {

            val current = browser.currentMediaItem
            getIndexById(current)

            scrollToTrack()

            // tracksFlow を監視して差分更新（コミット後に現在再生トラックがあれば追従）
            lifecycleScope.launch {


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
            onSearch = { index ->
                mainViewModel.setCurrentIndex(index)
            }
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
        track: MediaItem? = mediaBrowser?.currentMediaItem
    ) {

        LocalMusicRepository.getIndexById(track?.mediaId ?: "")?.let { index ->
            if (index >= 0) {
                mainViewModel.setCurrentIndex(index)
            } else {
                mediaBrowser?.currentMediaItemIndex?.let {

                    mainViewModel.setCurrentIndex(it)
                }
            }
        }


    }


    // retry state for pending instant scroll


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
                    binding.currentAlbumArt.load(R.drawable.default_album_art) {
                        size(512, 512)
                        precision(coil.size.Precision.INEXACT)

                        crossfade(false)
                    }
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

    private fun startRemainingTimer(endAtMillis: Long) {
        sleepTimerUIJob?.cancel()

        sleepTimerUIJob = lifecycleScope.launch {
            while (isActive) {
                val remaining =
                    (endAtMillis - System.currentTimeMillis())
                        .coerceAtLeast(0L)

                binding.sleepTimerTextView.run {

                    text = "${remaining / 60_000}分後に停止"
                    visibility = if (remaining > 0) View.VISIBLE else View.GONE
                }

                if (remaining == 0L) break

                delay(1000)
            }
        }
    }

    private fun stopRemainingTimer() {
        sleepTimerUIJob?.cancel()
        sleepTimerUIJob = null
        binding.sleepTimerTextView.run {

            text = ""
            visibility = View.GONE
        }
    }


    fun loadSTEndATimeByService() {
        mediaBrowser?.let {

            val future = it.sendCustomCommand(
                SessionCommand(
                    MusicService.CUSTOM_ACTION_SLEEP_TIMER_GET,
                    Bundle.EMPTY
                ),
                Bundle.EMPTY,
            )

            future.addListener(
                {
                    val result = future.get()

                    if (result.resultCode != SessionResult.RESULT_SUCCESS) {
                        Log.w(
                            "MainActivity",
                            "Failed to get sleep timer end time: ${result.resultCode}"
                        )
                        return@addListener
                    }

                    val endAtMillis = result.extras.getLong(
                        MusicService.SLEEP_TIMER_END_AT_MILLIS,
                        -1L
                    )
                    if (endAtMillis == -1L) {

                        mainViewModel.clearSleepTimerEndAtTime()

                        return@addListener
                    } else if (endAtMillis < 0) {
                        Log.w(
                            "MainActivity",
                            "Sleep timer end time is negative: $endAtMillis"
                        )
                        mainViewModel.clearSleepTimerEndAtTime()
                        return@addListener

                    } else if (endAtMillis < System.currentTimeMillis()) {
                        Log.w(
                            "MainActivity",
                            "Sleep timer end time is in the past: $endAtMillis"
                        )
                        mainViewModel.clearSleepTimerEndAtTime()
                        return@addListener
                    }

                    mainViewModel.setSleepTimerIfNeeded(endAtMillis)
                },
                ContextCompat.getMainExecutor(this@MainActivity)
            )
        }


    }
}