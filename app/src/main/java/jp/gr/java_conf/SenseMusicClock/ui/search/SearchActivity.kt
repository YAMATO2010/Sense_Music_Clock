package jp.gr.java_conf.SenseMusicClock.ui.search

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import coil.request.CachePolicy
import com.google.android.material.tabs.TabLayout
import jp.gr.java_conf.SenseMusicClock.Music.Data.BlocklistItem
import jp.gr.java_conf.SenseMusicClock.Music.Data.DBManager
import jp.gr.java_conf.SenseMusicClock.Music.Data.FileItem
import jp.gr.java_conf.SenseMusicClock.Music.Data.HistoryDBManager
import jp.gr.java_conf.SenseMusicClock.Music.Data.SearchHistory
import jp.gr.java_conf.SenseMusicClock.Music.LocalAlbumFetcher
import jp.gr.java_conf.SenseMusicClock.Music.LocalArtistFetcher
import jp.gr.java_conf.SenseMusicClock.Music.LocalMusicFetcher
import jp.gr.java_conf.SenseMusicClock.Music.LocalMusicFetcher.toMediaItem
import jp.gr.java_conf.SenseMusicClock.MusicService
import jp.gr.java_conf.SenseMusicClock.R
import jp.gr.java_conf.SenseMusicClock.databinding.ActivitySearchBinding
import jp.gr.java_conf.SenseMusicClock.showBlockSelectDialog
import jp.gr.java_conf.SenseMusicClock.showPlaylistSelectDialog
import jp.gr.java_conf.SenseMusicClock.toFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchActivity : AppCompatActivity() {

    enum class SearchType {
        ARTIST,
        ALBUM,
        TITLE
    }

    companion object {
        val ARTIST = "アーティスト"
        val TITLE = "タイトル"
        val ALBUM = "アルバム"

        val HISTORY_ITEM_MAX_COUNT = 50
    }

    private val searchType: Map<String, SearchType> = mapOf<String, SearchType>(
        ARTIST to SearchType.ARTIST,
        ALBUM to SearchType.ALBUM,
        TITLE to SearchType.TITLE
    )
    private val viewModel: SearchViewModel by viewModels()

    private lateinit var searchHistoryAdapter: SearchHistoryAdapter

    private lateinit var searchResultAdapter: SearchAdapter

    private lateinit var binding: ActivitySearchBinding

    private var cancelSignal: CancellationSignal? = null

    private lateinit var token: SessionToken

    private var mediaController: MediaController? = null


    override fun onStart() {
        super.onStart()

        binding.bottomController.initialize(this)
    }

    override fun onStop() {
        super.onStop()
        binding.bottomController.release()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)

        setContentView(binding.root)
        token = SessionToken(this, ComponentName(this, MusicService::class.java))
        MediaController.Builder(this, token).buildAsync().also {
            it.addListener({
                mediaController = it.get()
            }, ContextCompat.getMainExecutor(this))
        }

        val types = arrayOf(ALBUM, TITLE, ARTIST)
        viewModel.setSearchType(
            searchType[types[binding.typeTabLayout.selectedTabPosition]] ?: SearchType.ARTIST
        )

        binding.typeTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val selectedType = types[tab?.position ?: 0]
                viewModel.setSearchType(searchType[selectedType] ?: SearchType.ARTIST)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                // タブが選択解除されたときの処理（必要に応じて実装）
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                // タブが再選択されたときの処理（必要に応じて実装）
            }
        })


        binding.searchResultRecyclerView.run {
            searchHistoryAdapter = SearchHistoryAdapter(emptyList()) { holder, position, item ->
                // アイテムが表示されたときの処理

                onBindSearchHistoryAdapter(holder, position, item)
            }
            searchResultAdapter = SearchAdapter(emptyList()) { holder, position, item ->
                // アイテムが表示されたときの処理

                onBindSearchAdapter(holder, position, item)
            }

            val concatAdapter = ConcatAdapter(searchHistoryAdapter, searchResultAdapter)
            layoutManager = LinearLayoutManager(this@SearchActivity)
            adapter = concatAdapter
        }

        binding.finishButton.setOnClickListener {
            finish()
        }

        binding.searchEditText.addTextChangedListener {
            Log.d("SearchActivity", "Text changed: ${it.toString()}")
            onEditTextChanged(it.toString())
        }
        viewModel.viewModelScope.launch {
            val history =
                HistoryDBManager.loadSearchHistory(this@SearchActivity, HISTORY_ITEM_MAX_COUNT)
            viewModel.setSearchHistory(history)
            viewModel.setSearchMode(false)
            handleHistory()

        }


        observes()


    }

    override fun onDestroy() {
        super.onDestroy()


        mediaController?.release()
        mediaController = null
    }

    fun observes() {
        viewModel.currentSearchType.observe(this) {
            resultSubmit()
        }
        viewModel.isSearchMode.observe(this) { isSearchMode ->
            if (isSearchMode) {
                binding.currentModeTextView.text = "検索"
            } else {
                binding.currentModeTextView.text = "検索履歴"
            }
        }

    }

    fun resultSubmit() {
        searchResultAdapter.submitList(
            when (viewModel.currentSearchType.value ?: SearchType.ARTIST) {
                SearchType.ARTIST -> viewModel.currentArtistResult.value?.map { it.artistId }
                    ?: emptyList()

                SearchType.ALBUM -> viewModel.currentAlbumResult.value?.map { it.albumId }
                    ?: emptyList()

                SearchType.TITLE -> viewModel.currentMusicResult.value?.map { it.id }
                    ?: emptyList()
            }
        )
    }

    fun onBindSearchAdapter(holder: SearchAdapter.SearchViewHolder, position: Int, item: Long) {
        //アイテムがクリックされたときの処理

        val Item = when (viewModel.currentSearchType.value ?: SearchType.ARTIST) {
            SearchType.ARTIST -> viewModel.currentArtistResult.value?.find { it.artistId == item }
            SearchType.ALBUM -> viewModel.currentAlbumResult.value?.find { it.albumId == item }
            SearchType.TITLE -> viewModel.currentMusicResult.value?.find { it.id == item }
        }

        fun onItemInfos(item: Any): Triple<String, String, Uri?> {


            when (Item) {
                is LocalArtistFetcher.MediaStoreArtistSummary -> {
                    val newItem = item as LocalArtistFetcher.MediaStoreArtistSummary
                    return Triple(newItem.artistName ?: "<UNKNOWN>", "", Uri.EMPTY)
                }

                is LocalAlbumFetcher.MediaStoreAlbumSummary -> {
                    val newItem = item as LocalAlbumFetcher.MediaStoreAlbumSummary
                    return Triple(
                        newItem.albumName ?: "<UNKNOWN>",
                        newItem.artist ?: "<UNKNOWN>",
                        newItem.albumArtUri
                    )

                }

                is LocalMusicFetcher.MediaStoreAudioSummary -> {
                    val newItem = item as LocalMusicFetcher.MediaStoreAudioSummary
                    return Triple(
                        newItem.title ?: "<UNKNOWN>",
                        newItem.artist ?: "<UNKNOWN>",
                        newItem.albumArtUri
                    )

                }

                else -> {
                    Log.w("SearchActivity", "Unknown item type: ${Item?.javaClass}")
                    return Triple("<UNKNOWN>", "<UNKNOWN>", Uri.EMPTY)
                }
            }

        }

        val (title, subText, imageUri) = onItemInfos(Item ?: return)

        holder.Title?.text = title
        holder.subText?.text = subText
        holder.Image?.artworkLoad(imageUri)
        holder.container.setOnClickListener { view ->
            popupMenu(Item, view)

        }


    }

    fun popupMenu(item: Any, view: View) {
        val popupMenu = PopupMenu(this, view).also {
            it.menuInflater.inflate(R.menu.search_menu, it.menu)

            it.setOnMenuItemClickListener { menuItem ->
                lifecycleScope.launch {

                    addHistory(item)
                }

                when (menuItem.itemId) {
                    R.id.play -> {
                        // 再生処理
                        clickPlay(item)


                        true
                    }

                    R.id.addPlay -> {
                        // プレイリストに追加する処理

                        clickAddPlay(item)
                        true
                    }

                    R.id.addBlock -> {
                        // ブロックリストに追加する処理
                        clickAddBlock(item)
                        true
                    }

                    else -> false
                }
            }

        }
        popupMenu.show()
    }

    fun clickPlay(item: Any?) {
        Log.d("SearchActivity", "Play clicked for item: ${item?.javaClass}")
        //再生処理
        when (item) {
            is LocalMusicFetcher.MediaStoreAudioSummary -> clickPlay_Music(item)
            is LocalAlbumFetcher.MediaStoreAlbumSummary -> clickPlay_Album(item)
            is LocalArtistFetcher.MediaStoreArtistSummary -> clickPlay_Artist(item)
            else -> Log.w("SearchActivity", "Unknown item type for play action: ${item?.javaClass}")
        }

    }

    fun clickPlay_Music(item: LocalMusicFetcher.MediaStoreAudioSummary) {


        val command =
            SessionCommand(MusicService.CUSTOM_ACTION_LOAD_ONE_MUSIC_BY_ID, Bundle.EMPTY)
        mediaController?.sendCustomCommand(command, Bundle().apply {
            putLong(MusicService.MUSIC_ID, item.id)

        })

    }

    fun clickPlay_Album(item: LocalAlbumFetcher.MediaStoreAlbumSummary) {
        //アルバムの再生処理


        val command = SessionCommand(MusicService.CUSTOM_ACTION_LOAD_ALBUM_BY_ID, Bundle.EMPTY)
        mediaController?.sendCustomCommand(command, Bundle().apply {
            putLong(MusicService.ALBUM_ID, item.albumId)

        })


    }

    fun clickPlay_Artist(item: LocalArtistFetcher.MediaStoreArtistSummary) {
        //アーティストの再生処理

        val command = SessionCommand(MusicService.CUSTOM_ACTION_LOAD_ARTIST_BY_ID, Bundle.EMPTY)
        Log.d("SearchActivity", "Sending play command for artist ID: ${item.artistId}")
        if (mediaController == null) {
            Log.w("SearchActivity", "MediaController is not connected yet.")
            return
        }
        mediaController?.sendCustomCommand(command, Bundle().apply {
            putLong(MusicService.ARTIST_ID, item.artistId)

        })
    }

    fun clickAddPlay(item: Any?) {
        //プレイリストに追加する処理
        when (item) {
            is LocalMusicFetcher.MediaStoreAudioSummary -> clickAddPlay_Music(item)
            is LocalAlbumFetcher.MediaStoreAlbumSummary -> clickAddPlay_Album(item)
            is LocalArtistFetcher.MediaStoreArtistSummary -> clickAddPlay_Artist(item)
            else -> Log.w(
                "SearchActivity",
                "Unknown item type for add play action: ${item?.javaClass}"
            )
        }

    }

    fun clickAddPlay_Music(item: LocalMusicFetcher.MediaStoreAudioSummary) {
        //曲のプレイリストへの追加処理

        lifecycleScope.launch {
            val playlists = DBManager.loadPlaylist(this@SearchActivity)
            val fileItem: FileItem? = item.toMediaItem().toFileItem()
            if (fileItem == null) return@launch
            showPlaylistSelectDialog(
                playlists,
                fileItem,
            ) { playlistId ->
                viewModel.viewModelScope.launch {
                    DBManager.addPlaylistItem(
                        this@SearchActivity.applicationContext,
                        playlistId,
                        fileItem
                    )
                }
            }
        }

    }

    fun clickAddPlay_Album(item: LocalAlbumFetcher.MediaStoreAlbumSummary) {
        //アルバムのプレイリストへの追加処理
        lifecycleScope.launch {
            val playlists = DBManager.loadPlaylist(this@SearchActivity)


            showPlaylistSelectDialog(
                playlists,
                null,
            ) { playlistId ->
                viewModel.viewModelScope.launch {
                    val (selection, selectionArgs) = LocalMusicFetcher.albumID_selection(item.albumId)
                    val queryArgs = LocalMusicFetcher.createQueryArgs(selection, selectionArgs)
                    val musics = LocalMusicFetcher.loadLocalMusicFromAppDir(
                        contentResolver,
                        queryArgs
                    )
                    DBManager.addPlaylistItems(
                        this@SearchActivity,
                        playlistId,
                        musics.mapNotNull { it.toMediaItem().toFileItem() })
                }
            }
        }

    }

    fun clickAddPlay_Artist(item: LocalArtistFetcher.MediaStoreArtistSummary) {
        //アーティストのプレイリストへの追加処理
        lifecycleScope.launch {
            val playlists = DBManager.loadPlaylist(this@SearchActivity)


            showPlaylistSelectDialog(
                playlists,
                null,
            ) { playlistId ->
                viewModel.viewModelScope.launch {
                    val (selection, selectionArgs) = LocalMusicFetcher.artistID_selection(item.artistId)
                    val queryArgs = LocalMusicFetcher.createQueryArgs(selection, selectionArgs)
                    val musics = LocalMusicFetcher.loadLocalMusicFromAppDir(
                        contentResolver,
                        queryArgs
                    )
                    DBManager.addPlaylistItems(
                        this@SearchActivity,
                        playlistId,
                        musics.mapNotNull { it.toMediaItem().toFileItem() })
                }
            }
        }

    }

    fun clickAddBlock(item: Any) {
        //ブロックリストに追加する処理
        when (item) {
            is LocalMusicFetcher.MediaStoreAudioSummary -> clickAddBlock_Music(item)
            is LocalAlbumFetcher.MediaStoreAlbumSummary -> clickAddBlock_Album(item)
            is LocalArtistFetcher.MediaStoreArtistSummary -> clickAddBlock_Artist(item)
            else -> Log.w(
                "SearchActivity",
                "Unknown item type for add block action: ${item.javaClass}"
            )
        }

    }

    fun clickAddBlock_Music(item: LocalMusicFetcher.MediaStoreAudioSummary) {
        //曲のブロックリストへの追加処理
        lifecycleScope.launch {
            val blocklists = DBManager.loadBlocklist(this@SearchActivity)
            val fileItem: FileItem? = item.toMediaItem().toFileItem()
            if (fileItem == null) return@launch
            showBlockSelectDialog(
                blocklists,
                fileItem,
            ) { blocklistId ->
                val bItem = BlocklistItem(blocklistId, fileItem)
                viewModel.viewModelScope.launch {
                    DBManager.upsertBlocklistItem(this@SearchActivity, bItem)
                }
            }
        }


    }

    fun clickAddBlock_Album(item: LocalAlbumFetcher.MediaStoreAlbumSummary) {
        //アルバムのブロックリストへの追加処理
        lifecycleScope.launch {
            val blocklists = DBManager.loadBlocklist(this@SearchActivity)

            showBlockSelectDialog(
                blocklists,
                null,
            ) { blocklistId ->
                viewModel.viewModelScope.launch {
                    val (selection, selectionArgs) = LocalMusicFetcher.albumID_selection(item.albumId)
                    val queryArgs = LocalMusicFetcher.createQueryArgs(selection, selectionArgs)
                    val musics: List<FileItem> = LocalMusicFetcher.loadLocalMusicFromAppDir(
                        contentResolver,
                        queryArgs
                    ).mapNotNull {
                        it.toMediaItem().toFileItem() ?: (return@mapNotNull null)
                    }
                    Log.d("DEBUG", "Loaded musics: ${musics.size}")
                    DBManager.upsertBlockListItems(
                        this@SearchActivity.applicationContext,
                        blocklistId,
                        musics
                    )
                }
            }
        }


    }

    fun clickAddBlock_Artist(item: LocalArtistFetcher.MediaStoreArtistSummary) {
        //アーティストのブロックリストへの追加処理
        lifecycleScope.launch {
            val blocklists = DBManager.loadBlocklist(this@SearchActivity)

            showBlockSelectDialog(
                blocklists,
                null,
            ) { blocklistId ->
                viewModel.viewModelScope.launch {
                    val (selection, selectionArgs) = LocalMusicFetcher.artistID_selection(item.artistId)
                    val queryArgs = LocalMusicFetcher.createQueryArgs(selection, selectionArgs)
                    val musics: List<FileItem> = LocalMusicFetcher.loadLocalMusicFromAppDir(
                        contentResolver,
                        queryArgs
                    ).mapNotNull {
                        it.toMediaItem().toFileItem() ?: (return@mapNotNull null)
                    }
                    DBManager.upsertBlockListItems(
                        this@SearchActivity.applicationContext,
                        blocklistId,
                        musics
                    )
                }
            }
        }

    }


    fun onBindSearchHistoryAdapter(
        holder: SearchHistoryAdapter.SearchHistoryVHolder,
        position: Int,
        item: SearchHistory
    ) {
        //アイテムがクリックされたときの処理

        Log.d("SearchActivity", "Binding history item at position $position: $item")
        holder.deleteButton.setOnClickListener {

            viewModel.viewModelScope.launch {
                HistoryDBManager.deleteSearchHistory(this@SearchActivity, item)
                val updatedHistory =
                    HistoryDBManager.loadSearchHistory(this@SearchActivity, HISTORY_ITEM_MAX_COUNT)
                viewModel.setSearchHistory(updatedHistory)
                searchHistoryAdapter.submitList(updatedHistory)
            }
        }
        lifecycleScope.launch {
            val metadata = loadHistoryMetadata(item)
            withContext(Dispatchers.Main) {
                holder.Title?.text = metadata.title
                holder.subText?.text = metadata.type
                holder.Image?.artworkLoad(metadata.imageUrl)
            }
        }
        holder.container.setOnClickListener {
            val pMenuItem: Any = when (item.itemType) {
                SearchHistory.TYPE_ALBUM -> LocalAlbumFetcher.MediaStoreAlbumSummary(
                    albumId = item.itemID,
                    albumName = null,
                    artist = null,
                    albumArtUri = null
                )

                SearchHistory.TYPE_ARTIST -> LocalArtistFetcher.MediaStoreArtistSummary(
                    artistId = item.itemID,
                    artistName = null,
                )

                SearchHistory.TYPE_SONG -> LocalMusicFetcher.MediaStoreAudioSummary(
                    id = item.itemID,
                    title = null,
                    artist = null,
                    albumId = 0L,
                    albumArtUri = null,
                    album = null,
                    artistId = null,
                    trackNo = null,
                    relativePath = null,
                    displayName = null,
                    uri = null
                )

                else -> {
                    Log.w("SearchActivity", "Unknown item type for history click: ${item.itemType}")
                    return@setOnClickListener
                }
            }
            popupMenu(pMenuItem, it)
        }


    }


    fun onEditTextChanged(text: String) {

        cancelSignal?.cancel()

        viewModel.searchJob?.cancel()
        viewModel.searchJob = viewModel.viewModelScope.launch {
            delay(500) // ユーザーが入力を完了するのを待つ
            if (text.isBlank()) {
                viewModel.clearResult()
                viewModel.setSearchMode(false)
                handleHistory()
                searchHistoryAdapter.submitList(viewModel.searchHistory.value ?: emptyList())
                searchResultAdapter.submitList(emptyList())
            } else {
                viewModel.setSearchMode(true)
                searchHistoryAdapter.submitList(emptyList())

                val (albumSelection, albumSelectionArgs) = LocalAlbumFetcher.albumTitle_selection(
                    text
                )
                val (musicSelection, musicSelectionArgs) = LocalMusicFetcher.title_selection(text)
                val (artistSelection, artistSelectionArgs) = LocalArtistFetcher.artist_selection(
                    text
                )

                val albumQueryArgs =
                    LocalAlbumFetcher.createQueryArgs(albumSelection, albumSelectionArgs)
                val musicQueryArgs =
                    LocalMusicFetcher.createQueryArgs(musicSelection, musicSelectionArgs)
                val artistQueryArgs =
                    LocalArtistFetcher.createQueryArgs(artistSelection, artistSelectionArgs)

                Log.d("SearchActivity", "Album Query Args: $albumQueryArgs")
                Log.d("SearchActivity", "Music Query Args: $musicQueryArgs")
                Log.d("SearchActivity", "Artist Query Args: $artistQueryArgs")

                cancelSignal = CancellationSignal()


                viewModel.setArtistResult(
                    LocalArtistFetcher.loadArtistsFromAppDir(
                        contentResolver,
                        artistQueryArgs,
                        cancelSignal
                    )
                )



                viewModel.setAlbumResult(
                    LocalAlbumFetcher.loadAlbumsFromAppDir(
                        contentResolver,
                        albumQueryArgs,
                        cancelSignal

                    )
                )



                viewModel.setMusicResult(
                    LocalMusicFetcher.loadLocalMusicFromAppDir(
                        contentResolver,
                        musicQueryArgs,
                        cancelSignal
                    )
                )

                resultSubmit()


            }

        }
    }

    fun handleHistory() {

        lifecycleScope.launch {
            HistoryDBManager.loadSearchHistoryInRange(
                this@SearchActivity,
                0,
                HISTORY_ITEM_MAX_COUNT
            ).let { historyList ->
                viewModel.setSearchHistory(historyList)
            }
            searchHistoryAdapter.submitList(viewModel.searchHistory.value ?: emptyList())
        }

    }

    suspend fun addHistory(item: Any) {
        when (item) {
            is LocalMusicFetcher.MediaStoreAudioSummary -> {
                val deleteCount = HistoryDBManager.deleteSearchHistoryByItem(
                    this@SearchActivity,
                    item.id,
                    SearchHistory.TYPE_SONG
                )
                Log.d(
                    "SearchActivity",
                    "Deleted $deleteCount existing history entries for song ID: ${item.id}"
                )
                HistoryDBManager.insertSSongHistory(this, item.id)

            }

            is LocalAlbumFetcher.MediaStoreAlbumSummary -> {
                val deleteCount = HistoryDBManager.deleteSearchHistoryByItem(
                    this@SearchActivity,
                    item.albumId,
                    SearchHistory.TYPE_ALBUM
                )
                Log.d(
                    "SearchActivity",
                    "Deleted $deleteCount existing history entries for album ID: ${item.albumId}"
                )
                HistoryDBManager.insertSAlbumHistory(this, item.albumId)
            }

            is LocalArtistFetcher.MediaStoreArtistSummary -> {
                val deleteCount = HistoryDBManager.deleteSearchHistoryByItem(
                    this@SearchActivity,
                    item.artistId,
                    SearchHistory.TYPE_ARTIST
                )
                Log.d(
                    "SearchActivity",
                    "Deleted $deleteCount existing history entries for artist ID: ${item.artistId}"
                )
                HistoryDBManager.insertSArtistHistory(this, item.artistId)
            }

            else -> Log.w(
                "SearchActivity",
                "Unknown item type for history insertion: ${item.javaClass}"
            )
        }

    }


    fun ImageView.artworkLoad(item: Any?) {
        this.load(item) {

            crossfade(true)
            memoryCachePolicy(CachePolicy.DISABLED)
            diskCachePolicy(CachePolicy.ENABLED)
            placeholder(R.drawable.outline_hide_image_24)
            error(R.drawable.outline_hide_image_24)
            listener(
                onError = { request, result ->
                    Log.e("SearchMusicAdapter", "Coil error: ${request.data}", result.throwable)
                },
                onSuccess = { request, _ ->
                    Log.d("SearchMusicAdapter", "Coil success: ${request.data}")
                }
            )


        }
    }

    suspend fun loadHistoryMetadata(item: SearchHistory): SearchHistoryAdapter.HistoryMetadata {

        return when (item.itemType) {

            SearchHistory.TYPE_ALBUM -> {
                val (selection, selectionArgs) = LocalAlbumFetcher.albumId_selection(item.itemID)
                val queryArgs = LocalAlbumFetcher.createQueryArgs(selection, selectionArgs)
                val album =
                    LocalAlbumFetcher.loadAlbumsFromAppDir(contentResolver, queryArgs).firstOrNull()
                val data = album?.let {

                    SearchHistoryAdapter.HistoryMetadata(
                        id = it.albumId.toString(),
                        type = SearchHistory.TYPE_ALBUM,
                        title = it.albumName ?: "<UNKNOWN>",
                        imageUrl = it.albumArtUri ?: "".toUri()
                    )
                }

                data ?: SearchHistoryAdapter.HistoryMetadata()
            }

            SearchHistory.TYPE_ARTIST -> {
                val (selection, selectionArgs) = LocalArtistFetcher.artistId_selection(item.itemID)
                val queryArgs = LocalArtistFetcher.createQueryArgs(selection, selectionArgs)
                val artist = LocalArtistFetcher.loadArtistsFromAppDir(contentResolver, queryArgs)
                    .firstOrNull()
                val data = artist?.let {
                    SearchHistoryAdapter.HistoryMetadata(
                        id = it.artistId.toString(),
                        type = SearchHistory.TYPE_ARTIST,
                        title = it.artistName ?: "<UNKNOWN>",
                        imageUrl = "".toUri()
                    )
                }
                data ?: SearchHistoryAdapter.HistoryMetadata()
            }

            SearchHistory.TYPE_SONG -> {
                val (selection, selectionArgs) = LocalMusicFetcher.id_selection(item.itemID)
                val queryArgs = LocalMusicFetcher.createQueryArgs(selection, selectionArgs)
                val song = LocalMusicFetcher.loadLocalMusicFromAppDir(contentResolver, queryArgs)
                    .firstOrNull()
                val data = song?.let {
                    SearchHistoryAdapter.HistoryMetadata(
                        id = it.id.toString(),
                        type = SearchHistory.TYPE_SONG,
                        title = it.title ?: "<UNKNOWN>",
                        imageUrl = it.albumArtUri ?: "".toUri()
                    )
                }
                data ?: SearchHistoryAdapter.HistoryMetadata()
            }

            else -> {
                SearchHistoryAdapter.HistoryMetadata()
            }

        }
    }
}


