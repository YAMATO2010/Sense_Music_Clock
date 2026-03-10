package jp.gr.java_conf.SenseMusicClock.Music.list

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import jp.gr.java_conf.SenseMusicClock.Music.Data.BlocklistItem
import jp.gr.java_conf.SenseMusicClock.Music.Data.FileItem
import jp.gr.java_conf.SenseMusicClock.Music.Data.PlaylistItem
import jp.gr.java_conf.SenseMusicClock.Music.list.ListsActivity.ListType

class ListViewModel : ViewModel() {

    companion object {
        const val nowLoading_listId = Long.MIN_VALUE
    }


    private val _listType: MutableLiveData<ListType?> = MutableLiveData<ListType?>(null)
    val listType: LiveData<ListType?> get() = _listType

    private val _listId: MutableLiveData<Long> = MutableLiveData<Long>(nowLoading_listId)
    val listId: LiveData<Long> get() = _listId

    private val _listName: MutableLiveData<String> = MutableLiveData<String>("")
    val listName: LiveData<String> get() = _listName

    private val _fileItemList: MutableLiveData<List<FileItem>> = MutableLiveData<List<FileItem>>(emptyList<FileItem>())
    val fileItemList: LiveData<List<FileItem>> get() = _fileItemList

    private val _list: MutableLiveData<List<MediaItem>> = MutableLiveData<List<MediaItem>>(emptyList<MediaItem>())
    val list: LiveData<List<MediaItem>> get() = _list

    fun setFileItemList(list: List<FileItem>) {
        _fileItemList.value = list
    }

    fun setFileItemListByListItems(blockListItemList: List<BlocklistItem>) {
        _fileItemList.value = blockListItemList.map { it.fileItem }
    }
    @JvmName("setFileItemListByPlaylistItems")
    fun setFileItemListByListItems(PlayListItemList: List<PlaylistItem>) {
        _fileItemList.value = PlayListItemList.map { it.fileItem }
    }

    fun setlist(list: List<MediaItem>) {
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


}