package jp.gr.java_conf.SenseMusicClock.ui.list

import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import jp.gr.java_conf.SenseMusicClock.Music.Data.DBManager
import jp.gr.java_conf.SenseMusicClock.Music.Data.FileItem
import jp.gr.java_conf.SenseMusicClock.Music.LocalMusicFetcher
import jp.gr.java_conf.SenseMusicClock.R
import jp.gr.java_conf.SenseMusicClock.databinding.ActivityListsBinding
import jp.gr.java_conf.SenseMusicClock.moveDuplicatesToBack
import jp.gr.java_conf.SenseMusicClock.toPlaylistItems
import kotlinx.coroutines.launch

class ListsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityListsBinding

    private val viewModel: ListViewModel by viewModels()


    enum class ListType {
        PLAYLIST,
        BLOCKLIST
    }

    companion object {
        val EXTRA_LIST_ID = "listId"
        val EXTRA_LIST_TYPE = "listType"

    }


    override fun onRestart() {
        super.onRestart()
        lifecycleScope.launch {
            val listId = intent.getLongExtra(EXTRA_LIST_ID, -1L)
            val oldList = viewModel.fileItemList.value ?: emptyList()


            val newList: List<FileItem> =


                when (viewModel.listType.value) {
                    ListType.PLAYLIST -> {
                        DBManager.loadPlaylistItem(
                            this@ListsActivity,
                            listId
                        ).map {
                            it.fileItem
                        }

                    }

                    ListType.BLOCKLIST -> {
                        DBManager.loadBlocklistItem(
                            this@ListsActivity,
                            listId
                        ).map {
                            it.fileItem
                        }
                    }

                    null -> emptyList()
                }




            if (oldList.equals(newList)) {
                Log.d("ListsActivity", "No changes detected in the list items.")
            } else {
                Log.d(
                    "LIST_/ListsActivity/onRestart",
                    "detected list change old=${oldList.size} new=${newList.size}"
                )
                Log.d("ListsActivity", "Changes detected in the list items. Updating...")
                viewModel.setFileItemList(newList)
            }

        }
    }

    override fun onStart() {
        super.onStart()

        binding.bottomController.initialize(this)
    }

    override fun onStop() {
        super.onStop()
        binding.bottomController.initialize(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListsBinding.inflate(layoutInflater)

        setContentView(binding.root)

        val listId = viewModel.listId
        val listType = viewModel.listType

        Log.d(
            "ListsActivity",
            "Received intent with listId: ${
                intent.getLongExtra(
                    "listId",
                    -1
                )
            }, listType: ${intent.getStringExtra(EXTRA_LIST_TYPE)}"
        )


        if (savedInstanceState == null) { // 二重追加防止
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add(R.id.fragment_container, ListDisplayFragment()) // add または replace
            }
        }







        viewModel.setListId(intent.getLongExtra("listId", -1))
        if (listId.value == -1L) {
            Log.e("ListsActivity", "No list ID provided in intent")
            finish()
            return
        }

        viewModel.setListType(ListType.entries.find {
            it.name == intent.getStringExtra(
                EXTRA_LIST_TYPE
            )?.uppercase()
        })
        if (listType.value == null) {
            Log.e(
                "ListsActivity",
                "Invalid list type in intent: ${intent.getStringExtra(EXTRA_LIST_TYPE)}"
            )
            finish()
            return
        } else {


            when (listType.value) {
                ListType.PLAYLIST -> {
                    // プレイリストの内容を表示するためのコードをここに書く
                    Log.d("ListsActivity", "Displaying playlist with ID: $listId")
                    handlePlaylist()
                }

                ListType.BLOCKLIST -> {
                    // ブロックリストの内容を表示するためのコードをここに書く
                    Log.d("ListsActivity", "Displaying blocklist with ID: $listId")
                    handleBlocklist()
                }

                null -> {
                    // これは理論上起こらないはずですが、念のための安全策
                    Log.e(
                        "ListsActivity",
                        "Unexpected null list type___理論上起こらないはずだったのに！！！！！！！！！！！！！！！！！"
                    )
                    finish()
                    return
                }
            }
        }


    }

    private fun handlePlaylist() {
        // プレイリストのためのコードをここに書く


        viewModel.run {
            observes()


            viewModelScope.launch {
                val loadedItems =
                    DBManager.loadPlaylistItem(this@ListsActivity, listId.value ?: -1L)
                        .moveDuplicatesToBack()
                Log.d(
                    "LIST_/ListsActivity/handlePlaylist",
                    "loaded playlist items count=${loadedItems.size} for id=${listId.value}"
                )
                setFileItemListByListItems(loadedItems)
                val name =
                    DBManager.loadPlaylist(this@ListsActivity, listId.value ?: -1L)?.playlistName
                        ?: "Unknown Playlist"
                Log.d(
                    "LIST_/ListsActivity/handlePlaylist",
                    "playlist name for id=${listId.value} -> $name"
                )
                setListName(name)

            }
        }


    }

    private fun handleBlocklist() {
        // ブロックリストのためのコードをここに書く


        viewModel.run {
            observes()

            viewModelScope.launch {
                val loadedItems =
                    DBManager.loadBlocklistItem(this@ListsActivity, viewModel.listId.value ?: -1L)
                Log.d(
                    "LIST_/ListsActivity/handleBlocklist",
                    "loaded blocklist items count=${loadedItems.size} for id=${viewModel.listId.value}"
                )
                setFileItemListByListItems(loadedItems)
                val name = DBManager.loadBlocklistByID(
                    this@ListsActivity,
                    viewModel.listId.value ?: -1L
                )?.blockListName ?: "Unknown Blocklist"
                Log.d(
                    "LIST_/ListsActivity/handleBlocklist",
                    "blocklist name for id=${viewModel.listId.value} -> $name"
                )
                setListName(name)
            }
        }
    }

    fun observes() {
        viewModel.run {


            fileItemList.observe(this@ListsActivity) { list ->
                if (list.isEmpty()) {
                    setlist(emptyList())
                    return@observe
                }
                viewModelScope.launch {
                    val (selection, selectionArgs) = LocalMusicFetcher.playlist_selection(
                        list.toPlaylistItems(
                            listId.value ?: -1L
                        )
                    )
                    Log.d(
                        "LIST_/ListsActivity/observes",
                        "fileItemList firstOrNull=${list.firstOrNull()?.fileName} secondOrNull=${
                            list.getOrNull(
                                1
                            )?.fileName
                        } thirdOrNull=${list.getOrNull(2)?.fileName} "
                    )
                    val queryArgs = LocalMusicFetcher.createQueryArgs(selection, selectionArgs)

                    val items = LocalMusicFetcher.SafeLocalMusicFromAppDir(
                        this@ListsActivity.contentResolver,
                        queryArgs
                    )
                    Log.d(
                        "LIST_/ListsActivity/observes",
                        "fileItemList firstOrNull=${items.firstOrNull()?.title} secondOrNull=${
                            items.getOrNull(
                                1
                            )?.title
                        } thirdOrNull=${items.getOrNull(2)?.title}"
                    )
                    val useItems = items.sortedBy { item ->
                        val index =
                            list.indexOfFirst { it.relativePath == item.relativePath && it.fileName == item.displayName }
                        if (index == -1) Int.MAX_VALUE else index
                    }
                    Log.d(
                        "LIST_/ListsActivity/observes",
                        "fileItemList after sorting firstOrNull=${useItems.firstOrNull()?.title} secondOrNull=${
                            useItems.getOrNull(
                                1
                            )?.title
                        } thirdOrNull=${useItems.getOrNull(2)?.title}"
                    )
                    setlist(useItems)
                }
            }

        }


    }
}