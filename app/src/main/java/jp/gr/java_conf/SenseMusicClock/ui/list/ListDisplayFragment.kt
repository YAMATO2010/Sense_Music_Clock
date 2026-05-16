package jp.gr.java_conf.SenseMusicClock.ui.list

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import coil.request.CachePolicy
import jp.gr.java_conf.SenseMusicClock.Music.Data.BlockList
import jp.gr.java_conf.SenseMusicClock.Music.Data.DBManager
import jp.gr.java_conf.SenseMusicClock.Music.Data.PlayList
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
import kotlin.getValue


class ListDisplayFragment : Fragment() {


    private val sharedViewModel: ListViewModel by activityViewModels()

    private var _binding: FragmentListDisplayBinding? = null

    private val binding get() = _binding!!

    private lateinit var adapter: ListDisplayAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

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
                        sharedViewModel.viewModelScope.launch {

                            Log.d(
                                "LIST_/ListDisplayFragment/delete",
                                "requested delete for playlist id=${sharedViewModel.listId.value}"
                            )
                            when (sharedViewModel.listType.value) {
                                ListType.PLAYLIST -> DBManager.deletePlaylist(
                                    requireActivity().applicationContext,
                                    sharedViewModel.listId.value
                                        ?: -1L
                                )

                                ListType.BLOCKLIST -> DBManager.deleteBlocklist(
                                    requireActivity().applicationContext,
                                    sharedViewModel.listId.value
                                        ?: -1L
                                )

                                else -> {
                                    Log.d(
                                        "ListDisplayFragment",
                                        "Unknown list type: ${sharedViewModel.listType.value}"
                                    )
                                }

                            }
                        }
                        requireActivity().finish()
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
                        activity.showEditTextDialog(getString(R.string.name_change)) { value ->
                            sharedViewModel.viewModelScope.launch {

                                when (sharedViewModel.listType.value) {
                                    ListType.PLAYLIST -> {
                                        val newPlaylist: PlayList = PlayList(
                                            playlistId = sharedViewModel.listId.value ?: -1L,
                                            playlistName = value,
                                        )
                                        DBManager.upsertPlaylist(requireContext(), newPlaylist)
                                        Log.d(
                                            "LIST_/ListDisplayFragment/rename",
                                            "rename playlist id=${sharedViewModel.listId.value} newName=$value"
                                        )

                                    }

                                    ListType.BLOCKLIST -> {
                                        val newBlocklist: BlockList = BlockList(
                                            blockListID = sharedViewModel.listId.value ?: -1L,
                                            blockListName = value
                                        )
                                        DBManager.upsertBlocklist(requireContext(), newBlocklist)
                                        Log.d(
                                            "LIST_/ListDisplayFragment/rename",
                                            "rename blocklist id=${sharedViewModel.listId.value} newName=$value"
                                        )
                                    }

                                    else -> {
                                        Log.w(
                                            "ListDisplayFragment",
                                            "Unknown list type: ${sharedViewModel.listType.value}"
                                        )
                                    }

                                }
                            }
                        }



                        true
                    }

                    R.id.action_change_set -> {

                        when (sharedViewModel.listType.value) {

                            ListType.PLAYLIST -> {

                                sharedViewModel.viewModelScope.launch {


                                    PrefsManager.setCurrentPlaylistId(
                                        requireContext(),
                                        sharedViewModel.listId.value ?: -1L
                                    )
                                }

                            }

                            ListType.BLOCKLIST -> {

                                sharedViewModel.viewModelScope.launch {

                                    PrefsManager.setCurrentBlocklistId(
                                        requireContext(),
                                        sharedViewModel.listId.value ?: -1L
                                    )
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
            list.observe(requireActivity()) { value ->
                Log.d("LIST_/ListDisplayFragment", "list updated: ${value.size} items")

                value.run {

                    getOrNull(0)?.let {
                        binding.img1.artworkLoad(it.albumArtUri)
                    }
                    getOrNull(1)?.let {
                        binding.img2.artworkLoad(it.albumArtUri)
                    }
                    getOrNull(2)?.let {
                        binding.img3.artworkLoad(it.albumArtUri)
                    }
                    getOrNull(3)?.let {
                        binding.img4.artworkLoad(it.albumArtUri)
                    }
                }
                binding.listItemCount.text = getString(R.string.track_count, value.size)



                adapter.submitList(value)


            }

            listName.observe(requireActivity()) { value ->

                binding.listName.text = value

            }

            listId.observe(requireActivity()) { value ->


            }

        }





        return binding.root

    }

    fun goEdit() {
        val nextFragment = ListEditFragment()
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,android.R.anim.fade_in, android.R.anim.fade_out) // 任意：アニメーション
            .replace(R.id.fragment_container, nextFragment) // 貼り替え
            .addToBackStack(null) // ★重要：これがないと「戻る」ができない
            .commit()


    }

    fun adapterInit() {
        val diff = object : DiffUtil.ItemCallback<MediaItem>() {
            override fun areItemsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean =
                oldItem.mediaId == newItem.mediaId

            override fun areContentsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean =
                oldItem.getRelativePath() == newItem.getRelativePath() && oldItem.getDisplayName() == newItem.getDisplayName()
        }

        adapter = ListDisplayAdapter(
            onBind = { view, item ,pos->

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