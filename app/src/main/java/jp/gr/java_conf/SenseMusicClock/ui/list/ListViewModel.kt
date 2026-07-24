package jp.gr.java_conf.SenseMusicClock.ui.list

import android.content.ContentResolver
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import jp.gr.java_conf.SenseMusicClock.Music.AllMusicPagingSource
import jp.gr.java_conf.SenseMusicClock.Music.Data.Blacklists.BlocklistItem
import jp.gr.java_conf.SenseMusicClock.Music.Data.FileItem
import jp.gr.java_conf.SenseMusicClock.Music.Data.Playlists.PlaylistItem
import jp.gr.java_conf.SenseMusicClock.Music.LocalMusicFetcher
import jp.gr.java_conf.SenseMusicClock.ui.list.ListsActivity.ListType
import kotlinx.coroutines.flow.Flow

class ListViewModel : ViewModel() {

    companion object {
        const val nowLoading_listId = Long.MIN_VALUE
    }


    //全体で使用
    private val _listType: MutableLiveData<ListType?> = MutableLiveData<ListType?>(null)
    val listType: LiveData<ListType?> get() = _listType

    private val _listId: MutableLiveData<Long> = MutableLiveData(nowLoading_listId)
    val listId: LiveData<Long> get() = _listId

    private val _listName: MutableLiveData<String> = MutableLiveData("")
    val listName: LiveData<String> get() = _listName

    private val _fileItemList: MutableLiveData<List<FileItem>> =
        MutableLiveData(emptyList())
    val fileItemList: LiveData<List<FileItem>> get() = _fileItemList


    private val _list: MutableLiveData<List<LocalMusicFetcher.MediaStoreAudioSummary>> =
        MutableLiveData(emptyList())

    val list: LiveData<List<LocalMusicFetcher.MediaStoreAudioSummary>> get() = _list


    // Addで使用
    private val _checkedByAllMusic: MutableSet<Long> = mutableSetOf()

    val checkedByAllMusic: Set<Long> get() = _checkedByAllMusic

    private val allMusicRepository = AllMusicRepository(LocalMusicFetcher)

    // 外部（Fragment）には「読み取り専用」として公開


    fun allMusicFlow(resolver: ContentResolver, pageSize: Int = 100) =
        allMusicRepository.getAllMusicPager(resolver, pageSize).cachedIn(viewModelScope)

    fun setFileItemList(list: List<FileItem>) {
        _fileItemList.value = list
    }

    fun onCheckedByAllMusic(id: Long, isChecked: Boolean) {
        if (isChecked) {
            _checkedByAllMusic.add(id)
        } else {
            _checkedByAllMusic.remove(id)
        }
    }

    fun clearCheckedByAllMusic() {
        _checkedByAllMusic.clear()
    }

    fun setFileItemListByListItems(blockListItemList: List<BlocklistItem>) {
        _fileItemList.value = blockListItemList.map { it.fileItem }
    }

    @JvmName("setFileItemListByPlaylistItems")
    fun setFileItemListByListItems(PlayListItemList: List<PlaylistItem>) {
        _fileItemList.value = PlayListItemList.sortedBy { it.index }.map { it.fileItem }
    }

    fun setlist(list: List<LocalMusicFetcher.MediaStoreAudioSummary>) {
        _list.value = list
    }


    fun setListName(name: String) {
        _listName.value = name
    }


    fun setListType(type: ListType?) {
        _listType.value = type
    }

    fun setListId(id: Long) {
        _listId.value = id
    }

    // MusicRepository.kt
    class AllMusicRepository(private val fetcher: LocalMusicFetcher) {
        fun getAllMusicPager(
            resolver: ContentResolver,
            pageSize: Int = 100
        ): Flow<PagingData<LocalMusicFetcher.MediaStoreAudioSummary>> {
            Log.d(
                "LIST_/ListViewModel/AllMusicRepository",
                "Creating Pager for all music with pageSize=$pageSize"
            )
            return Pager(
                config = PagingConfig(pageSize = pageSize, enablePlaceholders = false),
                pagingSourceFactory = { AllMusicPagingSource(fetcher, resolver, limit = pageSize) }
            ).flow
        }
    }


}