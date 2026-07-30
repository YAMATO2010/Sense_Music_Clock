package jp.gr.java_conf.SenseMusicClock.ui.list

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import coil.request.CachePolicy
import jp.gr.java_conf.SenseMusicClock.Music.Data.Blacklists.BlockList
import jp.gr.java_conf.SenseMusicClock.Music.Data.DBManager
import jp.gr.java_conf.SenseMusicClock.Music.Data.Playlists.PlayList
import jp.gr.java_conf.SenseMusicClock.PrefsManager
import jp.gr.java_conf.SenseMusicClock.R
import jp.gr.java_conf.SenseMusicClock.databinding.FragmentListDisplayBinding
import jp.gr.java_conf.SenseMusicClock.getDisplayName
import jp.gr.java_conf.SenseMusicClock.getRelativePath
import jp.gr.java_conf.SenseMusicClock.playListAndBlockListToListInfo
import jp.gr.java_conf.SenseMusicClock.showEditTextDialog
import jp.gr.java_conf.SenseMusicClock.showListSelectDialog
import jp.gr.java_conf.SenseMusicClock.ui.list.ListsActivity.ListType
import kotlinx.coroutines.launch


class ListDisplayFragment : Fragment() {


    private val sharedViewModel: ListViewModel by activityViewModels()

    private var _binding: FragmentListDisplayBinding? = null

    private val binding get() = _binding!!

    private lateinit var adapter: ListDisplayAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentListDisplayBinding.inflate(inflater, container, false)
        adapterInit()

        binding.listName.text = sharedViewModel.listName.value ?: "Unknown"
        binding.listType.text = sharedViewModel.listType.value?.name ?: "Unknown"

        binding.toolbar.run {

            inflateMenu(R.menu.list_display_menu)
            binding.items.layoutManager = LinearLayoutManager(context)




            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_back -> {

                        requireActivity().finish()
                        true
                    }

                    R.id.action_edit -> {
                        goEdit()

                        true
                    }


                    R.id.action_delete -> {
                        val appContext = requireActivity().applicationContext
                        val activity = requireActivity()
                        sharedViewModel.viewModelScope.launch {

                            Log.d(
                                "LIST_/ListDisplayFragment/delete",
                                "requested delete for playlist id=${sharedViewModel.listId.value}"
                            )
                            val message = when (sharedViewModel.listType.value) {
                                ListType.PLAYLIST -> {
                                    DBManager.deletePlaylist(
                                        appContext,
                                        sharedViewModel.listId.value
                                            ?: -1L
                                    )
                                    "プレイリストを削除しました"
                                }

                                ListType.BLOCKLIST -> {
                                    DBManager.deleteBlocklist(
                                        appContext,
                                        sharedViewModel.listId.value
                                            ?: -1L
                                    )
                                    "ブロックリストを削除しました"
                                }

                                else -> {
                                    Log.d(
                                        "ListDisplayFragment",
                                        "Unknown list type: ${sharedViewModel.listType.value}"
                                    )
                                    null
                                }

                            }
                            message?.let {
                                Toast.makeText(appContext, it, Toast.LENGTH_SHORT).show()
                            }
                            activity.finish()
                        }
                        true
                    }

                    R.id.action_copy_list -> {

                        sharedViewModel.viewModelScope.launch {
                            when (sharedViewModel.listType.value) {

                                ListType.PLAYLIST -> {
                                    DBManager.copyPlaylist(
                                        requireActivity().applicationContext,
                                        sharedViewModel.listId.value ?: -1L
                                    )
                                    Log.d(
                                        "LIST_/ListDisplayFragment/copy",
                                        "requested copy for playlist id=${sharedViewModel.listId.value}"
                                    )
                                }

                                ListType.BLOCKLIST -> {
                                    DBManager.copyBlocklist(
                                        requireActivity().applicationContext,
                                        sharedViewModel.listId.value ?: -1L
                                    )
                                    Log.d(
                                        "LIST_/ListDisplayFragment/copy",
                                        "requested copy for blocklist id=${sharedViewModel.listId.value}"
                                    )
                                }

                                else -> {
                                    Log.d(
                                        "ListDisplayFragment",
                                        "Unknown list type: ${sharedViewModel.listType.value}"
                                    )
                                }

                            }
                        }




                        true
                    }

                    R.id.action_copy_Item -> {
                        lifecycleScope.launch {


                            val activity: AppCompatActivity = requireActivity() as AppCompatActivity
                            val playlists = DBManager.loadPlaylist(
                                activity.applicationContext
                            )
                            val blocklists = DBManager.loadBlocklist(
                                activity.applicationContext
                            )

                            val allLists: List<DBManager.ListInfo> =
                                playListAndBlockListToListInfo(playlists, blocklists)

                            activity.showListSelectDialog(allLists) { selectedList ->
                                val currentListInfo = DBManager.ListInfo(
                                    id = sharedViewModel.listId.value ?: -1L,
                                    name = sharedViewModel.listName.value ?: "Unknown",
                                    type = when (sharedViewModel.listType.value) {
                                        ListType.PLAYLIST -> ListType.PLAYLIST
                                        ListType.BLOCKLIST -> ListType.BLOCKLIST
                                        else -> null
                                    } ?: return@showListSelectDialog
                                )

                                sharedViewModel.viewModelScope.launch {

                                    DBManager.copylistContent(
                                        requireActivity().applicationContext,
                                        currentListInfo,
                                        selectedList

                                    )
                                }
                            }

                        }
                        true
                    }

                    R.id.action_change_name -> {


                        val activity: AppCompatActivity = requireActivity() as AppCompatActivity
                        val appContext = requireContext().applicationContext
                        activity.showEditTextDialog(getString(R.string.name_change)) { value ->
                            sharedViewModel.viewModelScope.launch {

                                val message = when (sharedViewModel.listType.value) {
                                    ListType.PLAYLIST -> {
                                        val newPlaylist: PlayList = PlayList(
                                            playlistId = sharedViewModel.listId.value ?: -1L,
                                            playlistName = value,
                                        )
                                        DBManager.upsertPlaylist(appContext, newPlaylist)
                                        Log.d(
                                            "LIST_/ListDisplayFragment/rename",
                                            "rename playlist id=${sharedViewModel.listId.value} newName=$value"
                                        )
                                        "プレイリスト名を変更しました"

                                    }

                                    ListType.BLOCKLIST -> {
                                        val newBlocklist: BlockList = BlockList(
                                            blockListID = sharedViewModel.listId.value ?: -1L,
                                            blockListName = value
                                        )
                                        DBManager.upsertBlocklist(appContext, newBlocklist)
                                        Log.d(
                                            "LIST_/ListDisplayFragment/rename",
                                            "rename blocklist id=${sharedViewModel.listId.value} newName=$value"
                                        )
                                        "ブロックリスト名を変更しました"
                                    }

                                    else -> {
                                        Log.w(
                                            "ListDisplayFragment",
                                            "Unknown list type: ${sharedViewModel.listType.value}"
                                        )
                                        null
                                    }

                                }
                                message?.let {
                                    Toast.makeText(appContext, it, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }



                        true
                    }

                    R.id.action_change_set -> {

                        val appContext = requireContext().applicationContext
                        when (sharedViewModel.listType.value) {

                            ListType.PLAYLIST -> {

                                sharedViewModel.viewModelScope.launch {


                                    PrefsManager.setCurrentPlaylistId(
                                        appContext,
                                        sharedViewModel.listId.value ?: -1L
                                    )
                                    PrefsManager.setReloadTracks_reverse_andGet(appContext)
                                    Toast.makeText(
                                        appContext,
                                        "使用するプレイリストに設定しました",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }

                            }

                            ListType.BLOCKLIST -> {

                                sharedViewModel.viewModelScope.launch {

                                    PrefsManager.setCurrentBlocklistId(
                                        appContext,
                                        sharedViewModel.listId.value ?: -1L
                                    )
                                    PrefsManager.setReloadTracks_reverse_andGet(appContext)
                                    Toast.makeText(
                                        appContext,
                                        "使用するブロックリストに設定しました",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                }

                            }

                            else -> {
                                Log.w(
                                    "LIST_/ListDisplayFragment",
                                    "Unknown list type: ${sharedViewModel.listType.value}"
                                )
                            }

                        }



                        true
                    }

                    else -> false
                }

            }
        }


        sharedViewModel.run {
            list.observe(viewLifecycleOwner) { value ->
                Log.d("LIST_/ListDisplayFragment", "list updated: ${value.size} items")

                val headerImages = listOf(binding.img1, binding.img2, binding.img3, binding.img4)
                headerImages.forEach {
                    it.setImageResource(R.drawable.outline_hide_image_24)
                }
                value.take(4).forEachIndexed { index, item ->
                    headerImages[index].artworkLoad(item.albumArtUri)
                }

                binding.listItemCount.text = getString(R.string.track_count, value.size)



                adapter.submitList(value)


            }

            listName.observe(viewLifecycleOwner) { value ->

                binding.listName.text = value

            }

            listId.observe(viewLifecycleOwner) { value ->


            }

        }





        return binding.root

    }

    override fun onDestroyView() {
        binding.items.adapter = null
        super.onDestroyView()
        _binding = null
    }

    fun goEdit() {
        val nextFragment = ListEditFragment()
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            ) // 任意：アニメーション
            .replace(R.id.fragment_container, nextFragment) // 貼り替え
            .addToBackStack(null) // ★重要：これがないと「戻る」ができない
            .commit()


    }

    fun adapterInit() {
        object : DiffUtil.ItemCallback<MediaItem>() {
            override fun areItemsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean =
                oldItem.mediaId == newItem.mediaId

            override fun areContentsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean =
                oldItem.getRelativePath() == newItem.getRelativePath() && oldItem.getDisplayName() == newItem.getDisplayName()
        }

        adapter = ListDisplayAdapter(
            onBind = { view, item, pos ->

            }
        ).also {
            binding.items.adapter = it
        }




        binding.items.layoutManager = LinearLayoutManager(context)

    }

    fun ImageView.artworkLoad(item: Any?) {
        this.load(item) {

            crossfade(true)
            memoryCachePolicy(CachePolicy.DISABLED)
            diskCachePolicy(CachePolicy.ENABLED)
            placeholder(R.drawable.outline_hide_image_24)
            error(R.drawable.outline_hide_image_24)

        }
    }


}
