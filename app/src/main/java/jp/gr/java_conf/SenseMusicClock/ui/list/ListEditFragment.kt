package jp.gr.java_conf.SenseMusicClock.ui.list

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.ItemTouchHelper
import jp.gr.java_conf.SenseMusicClock.Music.Data.DBManager
import jp.gr.java_conf.SenseMusicClock.Music.LocalMusicFetcher.toMediaItem
import jp.gr.java_conf.SenseMusicClock.R
import jp.gr.java_conf.SenseMusicClock.databinding.FragmentListEditBinding
import jp.gr.java_conf.SenseMusicClock.getDisplayName
import jp.gr.java_conf.SenseMusicClock.toBlockListItems
import jp.gr.java_conf.SenseMusicClock.toPlaylistItems
import kotlinx.coroutines.launch
import kotlin.getValue

/**
 * A fragment representing a list of Items.
 */
class ListEditFragment : Fragment() {

    private val sharedViewModel: ListViewModel by activityViewModels()

    private var _binding: FragmentListEditBinding? = null

    private val binding get() = _binding!!


    private lateinit var listadapter: ListEditAdapter


    private val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN, // 上下のドラッグを許可
        0 // スワイプ（左右）は今回は使わないので 0
    ) {
        private var fromPos: Int? = null
        private var toPos: Int? = null
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            fromPos = viewHolder.bindingAdapterPosition
            toPos = target.bindingAdapterPosition

            // 1. アダプター内のリストを入れ替える（UI上の反映）
            moveItem(fromPos ?: return false, toPos ?: return false)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            // スワイプ処理が必要ならここに書く
        }

        // ドラッグが終わった（指を離した）タイミングで呼ばれる
        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)

            fromPos = null
            toPos = null
        }
    })


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


    }

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
                    R.id.action_back-> {

                        parentFragmentManager.popBackStack()
                        true
                    }

                    R.id.action_add -> {

                        goAdd()

                        true
                    }

                    R.id.action_save -> {

                        save()
                        parentFragmentManager.popBackStack()

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
        sharedViewModel.list.observe(requireActivity()) { list ->
            adapterSetListByMediaItems(list.map { it.toMediaItem() } ,isChecked = true)

        }




        // Set the adapter


        return binding.root
    }

    fun save() {

        Log.d("ListEditFragment", "Saving list with ${listadapter.currentList.size} items, checked count: ${getCheckedList().size}")
        Log.d("ListEditFragment", "Saving list items: ${getCheckedList().map { it.mediaMetadata.title }}")

        sharedViewModel.viewModelScope.launch {

            when (sharedViewModel.listType.value) {
                ListsActivity.ListType.PLAYLIST -> {
                    val listId = sharedViewModel.listId.value ?: return@launch
                    val newItems = DBManager.replacePlaylistContent(
                        requireContext().applicationContext,
                        getCheckedList().toPlaylistItems(listId) ,
                    )
                    Log.d("ListEditFragment", "Saving list items: ${newItems.map { it.fileItem.fileName }}")
                    sharedViewModel.setFileItemListByListItems(newItems)

                }

                ListsActivity.ListType.BLOCKLIST -> {

                    val listId = sharedViewModel.listId.value ?: return@launch
                    val newItems = DBManager.replaceBlocklistContent(
                        requireContext().applicationContext,
                        getCheckedList().toBlockListItems(listId)
                    )
                    sharedViewModel.setFileItemListByListItems(newItems)

                }
                else -> {
                    Log.e("ListEditFragment", "Unknown list type: ${sharedViewModel.listType.value}")
                }
            }
        }
    }

    fun goAdd() {
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, ListAddFragment())
            .addToBackStack(null)
            .commit()
    }

    fun RecyclerView.adapterInit(listType: ListsActivity.ListType) {

        when (listType) {
            ListsActivity.ListType.PLAYLIST -> {
                itemTouchHelper.attachToRecyclerView(binding.editList)
                this.adapter = ListEditAdapter(
                    sharedViewModel.list.value?.map { it.toMediaItem().toMediaItemWithChecked() } ?: emptyList(),
                    ListEditAdapter.LIST_TYPE.TYPE_PLAY,
                    onStartDrag = { viewHolder ->
                        itemTouchHelper.startDrag(viewHolder)
                    },
                    onCheckedChange = { pos, isChecked ->

                    }
                ).also {
                    listadapter = it
                }
            }

            ListsActivity.ListType.BLOCKLIST -> {
                this.adapter = ListEditAdapter(
                    sharedViewModel.list.value?.map { it.toMediaItem().toMediaItemWithChecked() } ?: emptyList(),
                    ListEditAdapter.LIST_TYPE.TYPE_BLOCK,
                    onStartDrag = { viewHolder ->

                    },
                    onCheckedChange = { pos, isChecked ->

                    }
                ).also {
                    listadapter = it
                }

            }
        }
    }

    fun moveItem(fromPos: Int, toPos: Int) {
        val currentList = listadapter.currentList.toMutableList()
        val item = currentList.removeAt(fromPos)
        currentList.add(toPos, item)
        listadapter.submitList(currentList.toList()) // submitList に新しいリストを渡す


    }

    fun MediaItem.toMediaItemWithChecked(isChecked: Boolean = true) = ListEditAdapter.MediaItemWithChecked(this, isChecked)

    fun adapterSetListByMediaItems(list: List<MediaItem>,isChecked: Boolean = true) {
        val items = list.map {
            ListEditAdapter.MediaItemWithChecked(it,isChecked)
        }
        listadapter.submitList(items.toList())
    }



    fun getCheckedList(): List<MediaItem> {
        return listadapter.currentList.filter { it.isChecked }.map { it.mediaItem }
    }




}