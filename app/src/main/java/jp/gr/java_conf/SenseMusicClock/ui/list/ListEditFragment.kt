package jp.gr.java_conf.SenseMusicClock.ui.list

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import jp.gr.java_conf.SenseMusicClock.Music.Data.DBManager
import jp.gr.java_conf.SenseMusicClock.Music.LocalMusicFetcher.toMediaItem
import jp.gr.java_conf.SenseMusicClock.R
import jp.gr.java_conf.SenseMusicClock.databinding.FragmentListEditBinding
import jp.gr.java_conf.SenseMusicClock.toBlockListItems
import jp.gr.java_conf.SenseMusicClock.toPlaylistItems
import kotlinx.coroutines.launch

/**
 * A fragment representing a list of Items.
 */
class ListEditFragment : Fragment() {

    private val sharedViewModel: ListViewModel by activityViewModels()

    private var _binding: FragmentListEditBinding? = null

    private val binding get() = _binding!!


    private lateinit var listAdapter: ListEditAdapter


    private val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN, // 上下のドラッグを許可
        0 // スワイプ（左右）は今回は使わないので 0
    ) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val fromPos = viewHolder.bindingAdapterPosition
            val toPos = target.bindingAdapterPosition
            return moveItem(fromPos, toPos)
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            // スワイプ処理が必要ならここに書く
        }

        // ドラッグが終わった（指を離した）タイミングで呼ばれる
        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
        }
    })


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListEditBinding.inflate(inflater, container, false)

        binding.editList.layoutManager = LinearLayoutManager(context)
        binding.toolbar.run {


            inflateMenu(R.menu.list_edit_menu)


            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_back -> {

                        parentFragmentManager.popBackStack()
                        true
                    }

                    R.id.action_add -> {

                        goAdd()

                        true
                    }

                    R.id.action_save -> {

                        save()

                        true
                    }

                    else -> false
                }
            }

        }


        binding.editList.adapterInit(
            sharedViewModel.listType.value ?: ListsActivity.ListType.PLAYLIST
        )
        adapterSetListByMediaItems(
            sharedViewModel.list.value?.map { it.toMediaItem() } ?: emptyList(),
            isChecked = true
        )
        sharedViewModel.list.observe(viewLifecycleOwner) { list ->
            adapterSetListByMediaItems(list.map { it.toMediaItem() }, isChecked = true)

        }


        // Set the adapter


        return binding.root
    }

    fun save() {
        val appContext = requireContext().applicationContext
        val listId = sharedViewModel.listId.value ?: return
        val listType = sharedViewModel.listType.value
        val checkedItems = getCheckedList()

        Log.d(
            "ListEditFragment",
            "Saving list with ${listAdapter.currentList.size} items, checked count: ${getCheckedList().size}"
        )
        Log.d(
            "ListEditFragment",
            "Saving list items: ${getCheckedList().map { it.mediaMetadata.title }}"
        )

        viewLifecycleOwner.lifecycleScope.launch {

            val message = when (listType) {
                ListsActivity.ListType.PLAYLIST -> {
                    val newItems = DBManager.replacePlaylistContent(
                        appContext,
                        listId,
                        checkedItems.toPlaylistItems(listId),
                    )
                    Log.d(
                        "ListEditFragment",
                        "Saving list items: ${newItems.map { it.fileItem.fileName }}"
                    )
                    sharedViewModel.setFileItemListByListItems(newItems)
                    "プレイリストを保存しました"

                }

                ListsActivity.ListType.BLOCKLIST -> {

                    val newItems = DBManager.replaceBlocklistContent(
                        appContext,
                        listId,
                        checkedItems.toBlockListItems(listId)
                    )
                    sharedViewModel.setFileItemListByListItems(newItems)
                    "ブロックリストを保存しました"

                }

                else -> {
                    Log.e(
                        "ListEditFragment",
                        "Unknown list type: $listType"
                    )
                    return@launch
                }
            }
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
            if (isAdded) {
                parentFragmentManager.popBackStack()
            }
        }
    }

    fun goAdd() {
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            .replace(R.id.fragment_container, ListAddFragment())
            .addToBackStack(null)
            .commit()
    }

    fun RecyclerView.adapterInit(listType: ListsActivity.ListType) {

        when (listType) {
            ListsActivity.ListType.PLAYLIST -> {
                itemTouchHelper.attachToRecyclerView(binding.editList)
                this.adapter = ListEditAdapter(
                    sharedViewModel.list.value?.map { it.toMediaItem().toMediaItemWithChecked() }
                        ?: emptyList(),
                    ListEditAdapter.LIST_TYPE.TYPE_PLAY,
                    onStartDrag = { viewHolder ->
                        itemTouchHelper.startDrag(viewHolder)
                    },
                    onCheckedChange = { _, _ ->

                    }
                ).also {
                    listAdapter = it
                }
            }

            ListsActivity.ListType.BLOCKLIST -> {
                this.adapter = ListEditAdapter(
                    sharedViewModel.list.value?.map { it.toMediaItem().toMediaItemWithChecked() }
                        ?: emptyList(),
                    ListEditAdapter.LIST_TYPE.TYPE_BLOCK,
                    onStartDrag = { _ ->

                    },
                    onCheckedChange = { _, _ ->

                    }
                ).also {
                    listAdapter = it
                }

            }
        }
    }

    fun moveItem(fromPos: Int, toPos: Int): Boolean {
        return listAdapter.moveItem(fromPos, toPos)
    }

    fun MediaItem.toMediaItemWithChecked(isChecked: Boolean = true) =
        ListEditAdapter.MediaItemWithChecked(this, isChecked)

    fun adapterSetListByMediaItems(list: List<MediaItem>, isChecked: Boolean = true) {
        val currentItems = if (::listAdapter.isInitialized) {
            listAdapter.currentList
        } else {
            emptyList()
        }
        val items = list.mapIndexed { index, mediaItem ->
            val checked = currentItems.getOrNull(index)?.isChecked ?: isChecked
            ListEditAdapter.MediaItemWithChecked(mediaItem, checked)
        }
        listAdapter.submitList(items.toList())
    }


    fun getCheckedList(): List<MediaItem> {
        return listAdapter.currentList.filter { it.isChecked }.map { it.mediaItem }
    }

    override fun onDestroyView() {
        itemTouchHelper.attachToRecyclerView(null)
        _binding = null
        super.onDestroyView()
    }

}
