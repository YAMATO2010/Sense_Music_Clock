package jp.gr.java_conf.SenseMusicClock.Music

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.textfield.TextInputEditText
import jp.gr.java_conf.SenseMusicClock.R
import jp.gr.java_conf.SenseMusicClock.SpotifyTrack
import jp.gr.java_conf.SenseMusicClock.Track
import jp.gr.java_conf.SenseMusicClock.localTrack
import jp.gr.java_conf.SenseMusicClock.smoothScrollToCenter
import java.util.UUID

// --------------------
// Top-level helpers
// --------------------
/**
 * Generate matching keys for a Track so different representations can be matched across lists.
 * Keys order: local id (L:), spotify id (S:), uuid (U:), metadata (M: title|artist|album)
 */
fun keysForTrackTopLevel(t: Track): List<String> {
    val keys = mutableListOf<String>()
    try {
        if (t is localTrack) keys.add("L:${t.id}")
        if (t is SpotifyTrack) {
            if (t.trackId != null) keys.add("S:${t.trackId}")
        }
        keys.add("U:${t.uuid}")
        keys.add("M:${t.title}|${t.artist}|${t.album}")
    } catch (_: Exception) {}
    return keys
}

/**
 * Build key->original-index map from a list of IndexedValue<Track>.
 */
fun buildPositionMapFromIndexedTopLevel(indexed: List<kotlin.collections.IndexedValue<Track>>): Map<String, Int> {
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
    private val activity: Activity,
    private val recyclerView: RecyclerView,
    private val initialTracks: List<Track> = emptyList(),
    private val editText: EditText,
    private val recyclerJackets : RecyclerView? = null


) {

    private var targetTrackPositions: Map<String, Int> = mutableMapOf()
    private var targetTracks: List<Track> = emptyList()

    fun ini() {
        Log.i("MusicSearcher", "ini: initialTracks=${initialTracks.size}")
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
        fun applySearch(keyword: String) {
            Log.d("MusicSearcher", "applySearch: keyword='${keyword}'")
             if (keyword.isEmpty()) {
                 // 空キーワードなら元のリスト（初期トラック一覧）を表示する
                 targetTracks = initialTracks
                 val indexedAll = initialTracks.withIndex().toList()
                 targetTrackPositions = buildPositionMapFromIndexedTopLevel(indexedAll)
             } else {
                 val results = initialTracks.withIndex()
                     .filter { (_, track) ->
                         track.title.contains(keyword, ignoreCase = true) ||
                                 track.artist.contains(keyword, ignoreCase = true) ||
                                 track.album.contains(keyword, ignoreCase = true) ||
                                 (if (track is localTrack) track.path.contains(keyword, ignoreCase = true) else false)
                     }
                 targetTracks = results.map { it.value }
                 // build mapping from the filtered results (keys -> original index)
                 targetTrackPositions = buildPositionMapFromIndexedTopLevel(results)
             }

            // ログ: mapping のサンプルを出す（最大5件）
            try {
                val sample = targetTrackPositions.entries.take(5).joinToString(", ") { (k, v) -> "${k}=>${v}" }
                Log.d("MusicSearcher", "applySearch: sampleMapping=[$sample]")
            } catch (_: Exception) {}

             Log.d("MusicSearcher", "applySearch: results=${targetTracks.size} positions=${targetTrackPositions.size}")

             // adapter を更新（必ず実行）
             val existing = recyclerView.adapter as? SearchMusicAdapter
             if (existing != null) {
                 Log.d("MusicSearcher", "applySearch: updating existing SearchMusicAdapter with ${targetTracks.size} items")
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
                             val adapterName = recyclerView.adapter?.javaClass?.simpleName ?: "null"
                             Log.d("MusicSearcher", "applySearch: post-check adapter.itemCount=$count expected=${targetTracks.size} childCount=$childCount width=$w height=$h adapterClass=$adapterName")
                             if (targetTracks.isNotEmpty() && count == 0) {
                                Log.w("MusicSearcher", "applySearch: adapter appears empty after update -> recreating adapter as fallback")
                                recyclerView.adapter = SearchMusicAdapter(
                                    initialItems = targetTracks,
                                    context = activity,
                                    ItemPositionMap = targetTrackPositions,
                                    placeholderRes = R.drawable.default_album_art,
                                    onItemClick = { track ->
                                        val originalPosition2 = (recyclerView.adapter as? SearchMusicAdapter)?.getOriginalItemPosition(track)
                                        Log.d("MusicSearcher", "fallback onItemClick: clicked=${track.title} uuid=${track.uuid} resolvedOriginalPos=$originalPosition2")

                                        if (recyclerJackets != null) recyclerJackets.post { if (originalPosition2 != null) recyclerJackets.smoothScrollToCenter(originalPosition2,5f) }
                                    }
                                )
                             }
                         } catch (_: Exception) {}
                     }, 200L)
                 }
              } else {
                 Log.d("MusicSearcher", "applySearch: creating new SearchMusicAdapter with ${targetTracks.size} items")
                // create adapter with robust onItemClick that posts scrolls to the recyclerJackets UI thread
                recyclerView.adapter = SearchMusicAdapter(
                    initialItems = targetTracks,
                    context = activity,
                    ItemPositionMap = targetTrackPositions,
                    placeholderRes = R.drawable.default_album_art,
                    onItemClick = { track ->
                        val originalPosition = (recyclerView.adapter as? SearchMusicAdapter)?.getOriginalItemPosition(track)
                        Log.d("MusicSearcher", "onItemClick: clicked=${track.title} uuid=${track.uuid} resolvedOriginalPos=$originalPosition")
                        // visual feedback to confirm click was received

                        // diagnostic: log recyclerJackets state
                        try {
                            val hasRJ = recyclerJackets != null
                            val rjCount = recyclerJackets?.adapter?.itemCount ?: -1
                            Log.d("MusicSearcher", "onItemClick: recyclerJackets_present=$hasRJ recyclerJackets_adapter_count=$rjCount")
                        } catch (_: Exception) {}
                         if (recyclerJackets != null) {
                             recyclerJackets.post {
                                 if (originalPosition != null) {
                                     recyclerJackets.smoothScrollToCenter(originalPosition,5f)
                                 } else {
                                    Log.w("MusicSearcher", "originalPosition is null for clicked track; attempting fallback search by metadata")
                                    // fallback: try metadata key
                                    val fallbackIndex = initialTracks.indexOfFirst { it.title == track.title && it.artist == track.artist && it.album == track.album }
                                    Log.d("MusicSearcher", "fallbackIndex=$fallbackIndex")

                                    try {
                                        val hasRJ2 = recyclerJackets != null
                                        val rjCount2 = recyclerJackets?.adapter?.itemCount ?: -1
                                        Log.d("MusicSearcher", "onItemClick:fallback: recyclerJackets_present=$hasRJ2 recyclerJackets_adapter_count=$rjCount2")
                                    } catch (_: Exception) {}
                                     if (fallbackIndex >= 0) recyclerJackets.smoothScrollToCenter(fallbackIndex,5f)
                                 }
                             }
                         }
                     }
                )
              }

         }



         // 入力変更でも即時検索（ユーザーが即時反映を期待するため）
         editText.addTextChangedListener {
             applySearch(it?.toString() ?: "")
         }

         // 初期表示は元のトラック一覧をセット
        applySearch("")

     }



 }








class SearchMusicAdapter(
    initialItems: List<Track> = emptyList(),
    private val context: Context,
    private var ItemPositionMap: Map<String, Int>,
    private val placeholderRes: Int,
    private val onItemClick: (Track) -> Unit = {}
) : ListAdapter<Track, SearchMusicAdapter.ViewHolder>(DIFF) {

    init {
        submitList(initialItems.toList())
    }

    /**
     * Update items and the position map in-place when adapter already exists.
     */
    fun setItems(newItems: List<Track>, newPositionMap: Map<String, Int>) {
        Log.d("SearchMusicAdapter", "setItems: updating adapter with ${newItems.size} items")
        ItemPositionMap = newPositionMap
        submitList(newItems.toList())
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        val artwork: ImageView = view.findViewById(R.id.resultAlbumArtImageView)

        val title : TextView = view.findViewById(R.id.resultTrackTitleTextView)

        val container : LinearLayout = view.findViewById(R.id.musicResultItemContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.search_music_result, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
         val track = getItem(position)

         // 再利用時の残存 drawable/リスナを切る
         holder.artwork.setImageDrawable(null)
         holder.artwork.setOnClickListener(null)
         holder.artwork.contentDescription = ""

         holder.artwork.load(track.albumArtUri){
             crossfade(true)
             placeholder(R.drawable.default_album_art)
             error(R.drawable.default_album_art)
         }

         holder.artwork.contentDescription = track.title
         holder.title.text = track.title
         // ensure container and the full itemView are clickable (some layouts may intercept clicks)
         holder.container.isClickable = true
         holder.container.setOnClickListener {
            Log.i("SearchMusicAdapter", "container clicked: ${track.title} uuid=${track.uuid}")
            onItemClick(track)
         }
         // also attach listener to itemView itself to be robust against view-hierarchy click interception
         holder.itemView.isClickable = true
         holder.itemView.setOnClickListener {
            Log.i("SearchMusicAdapter", "itemView clicked: ${track.title} uuid=${track.uuid}")
            onItemClick(track)
         }
     }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.artwork.setImageDrawable(null)
        holder.artwork.setOnClickListener(null)
        holder.title.setText(null)
    }



    fun getOriginalItemPosition(track: Track?): Int? {
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
        private val DIFF = object : DiffUtil.ItemCallback<Track>() {
            override fun areItemsTheSame(oldItem: Track, newItem: Track): Boolean {
                val oldId = when (oldItem) {
                    is localTrack -> oldItem.id
                    is SpotifyTrack -> oldItem.trackId
                    else -> null
                }
                val newId = when (newItem) {
                    is localTrack -> newItem.id
                    is SpotifyTrack -> newItem.trackId
                    else -> null
                }
                return oldId == newId
            }

            override fun areContentsTheSame(oldItem: Track, newItem: Track): Boolean {
                if (oldItem::class != newItem::class) return false

                val commonCriteria = oldItem.title == newItem.title &&
                        oldItem.album == newItem.album &&
                        oldItem.artist == newItem.artist
                val localCriteria = if (oldItem is localTrack && newItem is localTrack) {
                    oldItem.path == newItem.path &&
                            oldItem.trackNo == newItem.trackNo

                } else true

                return commonCriteria && localCriteria
            }
        }
    }
}
