package jp.gr.java_conf.SenseMusicClock.Music


import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import jp.gr.java_conf.SenseMusicClock.R

import jp.gr.java_conf.SenseMusicClock.smoothScrollToPositionWithSkipAnimationCheck
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// --------------------
// Top-level helpers
// --------------------
/**
 * Generate matching keys for a Track so different representations can be matched across lists.
 * Keys order: local id (L:), spotify id (S:), uuid (U:), metadata (M: title|artist|album)
 */
fun keysForTrackTopLevel(t: MediaItem): List<String> {
    val keys = mutableListOf<String>()
    try {


        keys.add("L:${t.mediaId}")
        val tMetadata = t.mediaMetadata

        keys.add("M:${tMetadata.title}|${tMetadata.artist}|${tMetadata.albumTitle}")
    } catch (e: Exception) {
        Log.w("MusicSearcher", "keysForTrackTopLevel failed", e)
    }
    return keys
}

/**
 * Build key->original-index map from a list of IndexedValue<Track>.
 */
fun buildPositionMapFromIndexedTopLevel(indexed: List<IndexedValue<MediaItem>>): Map<String, Int> {
    val m = mutableMapOf<String, Int>()
    for (iv in indexed) {
        val idx = iv.index
        val t = iv.value
        for (k in keysForTrackTopLevel(t)) {
            if (!m.containsKey(k)) m[k] = idx
        }
    }
    return m
}

class MusicSearcherByList(
    private val activity: AppCompatActivity,
    private val recyclerView: RecyclerView,
    private val initialTracks: List<MediaItem> = emptyList(),
    private val editText: EditText,
    private val recyclerJackets: RecyclerView? = null


) {

    private var targetTrackPositions: Map<String, Int> = mutableMapOf()
    private var targetTracks: List<MediaItem> = emptyList()

    private var searchJob: Job? = null

    fun ini() {
        Log.d("MusicSearcher", "ini: initialTracks=${initialTracks.size}")
        // Ensure recyclerView is ready to show a vertical list
        try {
            if (recyclerView.layoutManager == null) {
                recyclerView.layoutManager = LinearLayoutManager(activity)
                Log.d("MusicSearcher", "ini: assigned LinearLayoutManager to recyclerView")
            }
            recyclerView.setHasFixedSize(false)
            recyclerView.visibility = View.VISIBLE
        } catch (e: Exception) {
            Log.w("MusicSearcher", "failed to configure recyclerView", e)
        }
        // 共通検索処理にまとめる
        fun applySearch() {
            searchJob?.cancel()

            searchJob = activity.lifecycleScope.launch {
                delay(500)

                val keyword = editText.text.toString().trim()
                Log.d("MusicSearcher", "applySearch: keyword='${keyword}'")
                if (keyword.isEmpty()) {
                    // 空キーワードなら何も表示しない
                    targetTracks = emptyList()
                    val indexedAll = emptyList<IndexedValue<MediaItem>>()
                    targetTrackPositions = buildPositionMapFromIndexedTopLevel(indexedAll)
                } else {
                    val results = initialTracks.withIndex()
                        .filter { (_, track) ->
                            val metadata = track.mediaMetadata
                            metadata.title?.contains(keyword, ignoreCase = true) ?: false              ||
                                    metadata.artist?.contains(keyword, ignoreCase = true) ?: false     ||
                                    metadata.albumTitle?.contains(keyword, ignoreCase = true) ?: false ||
                                    metadata.extras?.getString("RELATIVE_PATH")?.contains(keyword, ignoreCase = true) ?: false
                        }
                    targetTracks = results.map { it.value }
                    // build mapping from the filtered results (keys -> original index)
                    targetTrackPositions = buildPositionMapFromIndexedTopLevel(results)
                }

                // ログ: mapping のサンプルを出す（最大5件）
                try {
                    val sample = targetTrackPositions.entries.take(5)
                        .joinToString(", ") { (k, v) -> "${k}=>${v}" }
                    Log.d("MusicSearcher", "applySearch: sampleMapping=[$sample]")
                } catch (e: Exception) {
                    android.util.Log.w("MusicSearcher", "sample mapping log failed", e)
                }

                Log.d(
                    "MusicSearcher",
                    "applySearch: results=${targetTracks.size} positions=${targetTrackPositions.size}"
                )

                // adapter を更新（必ず実行）
                val existing = recyclerView.adapter as? SearchMusicAdapter
                if (existing != null) {
                    Log.d(
                        "MusicSearcher",
                        "applySearch: updating existing SearchMusicAdapter with ${targetTracks.size} items"
                    )
                    // ensure update runs on the RecyclerView's UI thread queue to avoid timing/layout races
                    recyclerView.post {
                        Log.d("MusicSearcher", "applySearch: posting setItems to recyclerView")
                        existing.setItems(targetTracks, targetTrackPositions)
                        // fallback check: if submitList/DIFF didn't update the visible itemCount shortly after, recreate adapter
                        recyclerView.postDelayed({
                            try {
                                val count = existing.itemCount
                                val childCount = recyclerView.childCount
                                val w = recyclerView.width
                                val h = recyclerView.height
                                val adapterName =
                                    recyclerView.adapter?.javaClass?.simpleName ?: "null"
                                Log.d(
                                    "MusicSearcher",
                                    "applySearch: post-check adapter.itemCount=$count expected=${targetTracks.size} childCount=$childCount width=$w height=$h adapterClass=$adapterName"
                                )
                                if (targetTracks.isNotEmpty() && count == 0) {
                                    Log.w(
                                        "MusicSearcher",
                                        "applySearch: adapter appears empty after update -> recreating adapter as fallback"
                                    )
                                    recyclerView.adapter = SearchMusicAdapter(
                                        initialItems = targetTracks,
                                        context = activity,
                                        ItemPositionMap = targetTrackPositions,
                                        placeholderRes = R.drawable.default_album_art,
                                        onItemClick = { track ->
                                            val originalPosition2 =
                                                (recyclerView.adapter as? SearchMusicAdapter)?.getOriginalItemPosition(
                                                    track
                                                )
                                            Log.d(
                                                "MusicSearcher",
                                                "fallback onItemClick: clicked=${track.mediaMetadata.title}  resolvedOriginalPos=$originalPosition2"
                                            )

                                            if (recyclerJackets != null) recyclerJackets.post {
                                                if (originalPosition2 != null) {
                                                    activity.lifecycleScope.launch {
                                                        recyclerJackets.smoothScrollToPositionWithSkipAnimationCheck(
                                                            originalPosition2,
                                                            5f
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            } catch (e: Exception) {
                                android.util.Log.w("MusicSearcher", "post-check failed", e)
                            }
                        }, 200L)
                    }
                } else {
                    Log.d(
                        "MusicSearcher",
                        "applySearch: creating new SearchMusicAdapter with ${targetTracks.size} items"
                    )
                    // create adapter with robust onItemClick that posts scrolls to the recyclerJackets UI thread
                    recyclerView.adapter = SearchMusicAdapter(
                        initialItems = targetTracks,
                        context = activity,
                        ItemPositionMap = targetTrackPositions,
                        placeholderRes = R.drawable.default_album_art,
                        onItemClick = { track ->
                            val originalPosition =
                                (recyclerView.adapter as? SearchMusicAdapter)?.getOriginalItemPosition(
                                    track
                                )
                            Log.d(
                                "MusicSearcher",
                                "onItemClick: clicked=${track.mediaMetadata.title} resolvedOriginalPos=$originalPosition"
                            )
                            // visual feedback to confirm click was received

                            // diagnostic: log recyclerJackets state
                            try {
                                val hasRJ = recyclerJackets != null
                                val rjCount = recyclerJackets?.adapter?.itemCount ?: -1
                                Log.d(
                                    "MusicSearcher",
                                    "onItemClick: recyclerJackets_present=$hasRJ recyclerJackets_adapter_count=$rjCount"
                                )
                            } catch (e: Exception) {
                                android.util.Log.w("MusicSearcher", "onItemClick diagnostic failed", e)
                            }
                            if (recyclerJackets != null) {
                                recyclerJackets.post {
                                    if (originalPosition != null) {
                                        activity.lifecycleScope.launch {
                                            recyclerJackets.smoothScrollToPositionWithSkipAnimationCheck(
                                                originalPosition,
                                                5f
                                            )
                                        }
                                    } else {
                                        Log.w(
                                            "MusicSearcher",
                                            "originalPosition is null for clicked track; attempting fallback search by metadata"
                                        )
                                        // fallback: try metadata key
                                        val fallbackIndex =
                                            initialTracks.indexOfFirst {

                                                val metadata_iniList = it.mediaMetadata
                                                val metadata_clicked = track.mediaMetadata
                                                metadata_iniList.title == metadata_clicked.title && metadata_iniList.artist == metadata_clicked.artist && metadata_iniList.albumTitle == metadata_clicked.albumTitle }
                                        Log.d("MusicSearcher", "fallbackIndex=$fallbackIndex")


                                        if (fallbackIndex >= 0) {
                                            activity.lifecycleScope.launch {
                                                recyclerJackets.smoothScrollToPositionWithSkipAnimationCheck(
                                                    fallbackIndex,
                                                    5f
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            }

        }


        // 入力変更でも即時検索（ユーザーが即時反映を期待するため）
        editText.addTextChangedListener {
            applySearch()
        }

        // 初期表示は元のトラック一覧をセット
        applySearch()

    }


}



class SearchMusicAdapter(
    initialItems: List<MediaItem> = emptyList(),
    private val context: Context,
    private var ItemPositionMap: Map<String, Int>,
    private val placeholderRes: Int,
    private val onItemClick: (MediaItem) -> Unit = {}
) : ListAdapter<MediaItem, SearchMusicAdapter.ViewHolder>(DIFF) {

    init {
        submitList(initialItems.toList())
    }

    /**
     * Update items and the position map in-place when adapter already exists.
     */
    fun setItems(newItems: List<MediaItem>, newPositionMap: Map<String, Int>) {
        Log.d("SearchMusicAdapter", "setItems: updating adapter with ${newItems.size} items")
        ItemPositionMap = newPositionMap
        submitList(newItems.toList())
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        val artwork: ImageView = view.findViewById(R.id.resultImageView)

        val title: TextView = view.findViewById(R.id.resultTextView)

        val container: LinearLayout = view.findViewById(R.id.ResultItemWithImageContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v =
            LayoutInflater.from(parent.context).inflate(R.layout.search_result_with_image, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val track = getItem(position)

        val metadata = track.mediaMetadata
        // 再利用時の残存 drawable/リスナを切る
        holder.artwork.setImageDrawable(null)
        holder.artwork.setOnClickListener(null)
        holder.artwork.contentDescription = ""

        holder.artwork.load(metadata.artworkUri) {
            crossfade(true)
            placeholder(R.drawable.default_album_art)
            error(R.drawable.default_album_art)
        }

        holder.artwork.contentDescription = metadata.title
        holder.title.text = metadata.title
        // ensure container and the full itemView are clickable (some layouts may intercept clicks)
        holder.container.isClickable = true
        holder.container.setOnClickListener {
            Log.d("SearchMusicAdapter", "container clicked: ${track.mediaMetadata.title}")
            onItemClick(track)
        }
        // also attach listener to itemView itself to be robust against view-hierarchy click interception
        holder.itemView.isClickable = true
        holder.itemView.setOnClickListener {
            Log.d("SearchMusicAdapter", "itemView clicked: ${track.mediaMetadata.title} ")
            onItemClick(track)
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.artwork.setImageDrawable(null)
        holder.artwork.setOnClickListener(null)
        holder.title.setText(null)
    }


    fun getOriginalItemPosition(track: MediaItem?): Int? {
        if (track == null) return null
        // try keys in order: local id, spotify id, uuid, metadata
        try {
            val keys = keysForTrackTopLevel(track)
            for (k in keys) {
                val found = ItemPositionMap[k]
                if (found != null) return found
            }
        } catch (e: Exception) {
            Log.w("SearchMusicAdapter", "getOriginalItemPosition failed", e)
        }
        Log.d("SearchMusicAdapter", "getOriginalItemPosition: no mapping for track (checked keys)")
        return null
    }


    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MediaItem>() {
            override fun areItemsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean {
                val oldId = oldItem.mediaId
                val newId = newItem.mediaId
                return oldId == newId
            }

            override fun areContentsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean {
                if (oldItem::class != newItem::class) return false

                val oldMetadata = oldItem.mediaMetadata
                val newMetadata = newItem.mediaMetadata

                val commonCriteria = oldMetadata.title.toString() == newMetadata.title.toString() &&
                        oldMetadata.albumTitle.toString() == newMetadata.albumTitle.toString() &&
                        oldMetadata.artist.toString() == newMetadata.artist.toString()
                val localCriteria =
                    oldMetadata.extras?.getString("RELATIVE_PATH") == newMetadata.extras?.getString("RELATIVE_PATH") &&
                            oldMetadata.trackNumber == newMetadata.trackNumber



                return commonCriteria && localCriteria
            }
        }
    }
}
