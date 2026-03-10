package jp.gr.java_conf.SenseMusicClock.Music.list

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.viewModelScope
import jp.gr.java_conf.SenseMusicClock.LocalMusicRepository
import jp.gr.java_conf.SenseMusicClock.Music.Data.AppDataBase
import jp.gr.java_conf.SenseMusicClock.Music.Data.BlocklistItem
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.list_activity_barmenu, menu)
        return true


    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            // 戻るボタン（←）が押された時
            android.R.id.home -> {
                finish()
                true
            }
            // 作成したメニュー項目が押された時
            R.id.action_add -> {

                true
            }

            R.id.action_delete -> {

                true
            }

            else -> super.onOptionsItemSelected(item)
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListsBinding.inflate(layoutInflater)

        setContentView(binding.root)
        val listId = viewModel.listId
        val listType = viewModel.listType






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

        val db = AppDataBase.getInstance(this)
        val playlistItemDao = db.playListItemDao()
        val playlistDao = db.playListDao()


        viewModel.run {
            fileItemList.observe(this@ListsActivity) { list ->
                viewModelScope.launch {

                    val items = LocalMusicRepository.loadPlaylistMusic(
                        this@ListsActivity, list.toPlaylistItems(listId.value ?: -1L)
                    )
                    setlist(items)

                }
            }


            viewModelScope.launch {

                setFileItemListByListItems(
                    playlistItemDao.loadItemsForPlaylist(
                        listId.value ?: -1L
                    ).moveDuplicatesToBack()
                )
                setListName(
                    playlistDao.loadPlaylistById(
                        listId.value ?: -1L
                    )?.playlistName ?: "Unknown Playlist"
                )


            }
        }


    }

    private fun handleBlocklist() {
        // ブロックリストのためのコードをここに書く

        val db = AppDataBase.getInstance(this)
        val blocklistItemDao = db.blockListItemDao()
        val blocklistDao = db.blockListDao()


        viewModel.run {


            fileItemList.observe(this@ListsActivity) { list ->
                viewModelScope.launch {

                    val items = LocalMusicRepository.loadPlaylistMusic(
                        this@ListsActivity, list.toPlaylistItems(
                            BlocklistItem.TOPLAYLISTID
                        )
                    )
                    setlist(items)

                }
            }

            viewModelScope.launch {
                setFileItemListByListItems(
                    blocklistItemDao.loadItemsForBlocklist(
                        viewModel.listId.value ?: -1L
                    )
                )
                setListName(
                    blocklistDao.loadBlocklistById(
                        viewModel.listId.value ?: -1L
                    )?.blockListName ?: "Unknown Blocklist"
                )
            }
        }
    }
}