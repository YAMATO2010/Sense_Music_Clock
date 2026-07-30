package jp.gr.java_conf.SenseMusicClock.ui.list

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import jp.gr.java_conf.SenseMusicClock.Music.Data.Blacklists.BlocklistItem
import jp.gr.java_conf.SenseMusicClock.Music.Data.DBManager
import jp.gr.java_conf.SenseMusicClock.Music.Data.FileItem
import jp.gr.java_conf.SenseMusicClock.Music.Data.Playlists.PlaylistItem
import jp.gr.java_conf.SenseMusicClock.Music.LocalMusicFetcher
import jp.gr.java_conf.SenseMusicClock.R
import jp.gr.java_conf.SenseMusicClock.databinding.FragmentListAddBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * A fragment representing a list of Items.
 */
class ListAddFragment : Fragment() {


    private val sharedViewModel: ListViewModel by activityViewModels()

    private var _binding: FragmentListAddBinding? = null

    private val binding get() = _binding!!

    private lateinit var listAdapter: ListAddAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListAddBinding.inflate(inflater, container, false)


        binding.addList.layoutManager = LinearLayoutManager(context)

        sharedViewModel.run {


            listAdapter =
                ListAddAdapter(
                    ListAddAdapter.DIFF_CALLBACK,
                    onChecked = { id, isChecked ->
                        onCheckedByAllMusic(id, isChecked)

                    },
                    onBind = { holder, item, _ ->
                        val isChecked =
                            checkedByAllMusic.contains(item?.id ?: return@ListAddAdapter)
                        holder.checkBox.isChecked = isChecked
                    }
                ).also {

                    binding.addList.adapter = it
                }




            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    allMusicFlow(requireContext().applicationContext.contentResolver).collectLatest { pagingData ->
                        listAdapter.submitData(pagingData)
                    }
                }

            }
        }

        binding.toolbar.run {
            inflateMenu(R.menu.list_add_menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_back -> {
                        parentFragmentManager.popBackStack()
                        true
                    }


                    R.id.action_save -> {
                        if (sharedViewModel.checkedByAllMusic.isEmpty()) {
                            parentFragmentManager.popBackStack()
                            return@setOnMenuItemClickListener true
                        }


                        val appContext = requireContext().applicationContext
                        sharedViewModel.viewModelScope.launch {


                            val (selection, selectionArgs) = LocalMusicFetcher.mediaId_selection(
                                sharedViewModel.checkedByAllMusic.toList()
                            )
                            val queryArgs =
                                LocalMusicFetcher.createQueryArgs(selection, selectionArgs)

                            val checkedItem: List<FileItem> =
                                LocalMusicFetcher.loadLocalMusicFromAppDir(
                                    requireContext().applicationContext.contentResolver,
                                    queryArgs,
                                ).mapNotNull {
                                    it.run {
                                        if (displayName == null || relativePath == null) null
                                        else FileItem(relativePath, displayName)
                                    }
                                }



                            val saveSucceeded = when (sharedViewModel.listType.value) {
                                ListsActivity.ListType.PLAYLIST -> {
                                    Log.d(
                                        "LIST_/ListAddFragment/save",
                                        "saving playlist id=${sharedViewModel.listId.value} checkedItems=${checkedItem.size}"
                                    )
                                    handlePlaylistSave(
                                        sharedViewModel.fileItemList.value ?: return@launch,
                                        checkedItem, sharedViewModel.listId.value ?: return@launch
                                    )
                                }

                                ListsActivity.ListType.BLOCKLIST -> {
                                    Log.d(
                                        "LIST_/ListAddFragment/save",
                                        "saving blocklist id=${sharedViewModel.listId.value} checkedItems=${checkedItem.size}"
                                    )
                                    handleBlocklistSave(
                                        sharedViewModel.fileItemList.value ?: return@launch,
                                        checkedItem, sharedViewModel.listId.value ?: return@launch
                                    )
                                }

                                else -> false
                            }
                            val message = when (sharedViewModel.listType.value) {
                                ListsActivity.ListType.PLAYLIST -> {
                                    if (saveSucceeded) "プレイリストに追加しました" else "プレイリストへの追加に失敗しました"
                                }

                                ListsActivity.ListType.BLOCKLIST -> {
                                    if (saveSucceeded) "ブロックリストに追加しました" else "ブロックリストへの追加に失敗しました"
                                }

                                else -> {
                                    if (saveSucceeded) "リストを保存しました" else "リストの保存に失敗しました"
                                }
                            }
                            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()





                            sharedViewModel.clearCheckedByAllMusic()

                            parentFragmentManager.popBackStack()
                        }




                        true
                    }

                    else -> false
                }
            }


        }










        return binding.root
    }

    suspend fun handlePlaylistSave(
        fileItems: List<FileItem>,
        checkedItems: List<FileItem>,
        listID: Long
    ): Boolean {
        // プレイリストの保存処理を実装

        try {

            val newList: List<PlaylistItem> =
                (fileItems + checkedItems).withIndex().map { (index, fileItem) ->
                    PlaylistItem(
                        playlistId = listID,
                        fileItem = fileItem,
                        index = index
                    )

                }


            sharedViewModel.setFileItemList(
                DBManager.replacePlaylistContent(requireContext().applicationContext, listID, newList)
                    .map { it.fileItem })
        } catch (e: Exception) {
            Log.e("ListAddFragment", "Error saving playlist", e)
            return false
        }

        return true

    }

    suspend fun handleBlocklistSave(
        fileItem: List<FileItem>,
        checkedItems: List<FileItem>,
        listID: Long,
    ): Boolean {
        // ブロックリストの保存処理を実装
        try {


            val newList: List<BlocklistItem> =
                (fileItem + checkedItems).map { fileItem ->
                    BlocklistItem(
                        blocklistId = listID,
                        fileItem = fileItem
                    )

                }

            sharedViewModel.setFileItemList(
                DBManager.replaceBlocklistContent(requireContext().applicationContext, listID, newList)
                    .map { it.fileItem }
            )

        } catch (e: Exception) {
            Log.e("ListAddFragment", "Error saving blocklist", e)
            return false
        }
        return true
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


}
