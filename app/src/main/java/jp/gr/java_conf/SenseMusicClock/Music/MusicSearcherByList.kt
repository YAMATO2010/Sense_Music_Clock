package jp.gr.java_conf.SenseMusicClock.Music


import android.util.Log
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import jp.gr.java_conf.SenseMusicClock.LocalMusicRepository
import jp.gr.java_conf.SenseMusicClock.R
import jp.gr.java_conf.SenseMusicClock.buildPositionMapFromIndexedTopLevel
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


class MusicSearcherByList(
    private val activity: AppCompatActivity,
    private val recyclerView: RecyclerView,
    private val editText: EditText,
    private val onSearch: (index: Int) -> Unit,


    ) {

    private var targetTrackPositions: Map<String, Int> = mutableMapOf()
    private var targetTracks: List<MediaItem> = emptyList()

    private var searchJob: Job? = null

    fun ini() {

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
                if (keyword.isBlank()) {
                    // 空キーワードなら何も表示しない
                    targetTracks = emptyList()
                    val indexedAll = emptyList<IndexedValue<MediaItem>>()
                    targetTrackPositions = buildPositionMapFromIndexedTopLevel(indexedAll)
                } else {
                    val results = LocalMusicRepository.tracksFlow.value.withIndex()
                        .filter { (_, track) ->
                            val metadata = track.mediaMetadata
                            metadata.title?.contains(keyword, ignoreCase = true) ?: false ||
                                    metadata.artist?.contains(
                                        keyword,
                                        ignoreCase = true
                                    ) ?: false ||
                                    metadata.albumTitle?.contains(
                                        keyword,
                                        ignoreCase = true
                                    ) ?: false ||
                                    metadata.extras?.getString("RELATIVE_PATH")
                                        ?.contains(keyword, ignoreCase = true) ?: false
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
                    Log.w("MusicSearcher", "sample mapping log failed", e)
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

                                            onSearch(originalPosition2 ?: 0)
                                        }
                                    )
                                }
                            } catch (e: Exception) {
                                Log.w("MusicSearcher", "post-check failed", e)
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

                            onSearch(originalPosition ?: 0)
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



