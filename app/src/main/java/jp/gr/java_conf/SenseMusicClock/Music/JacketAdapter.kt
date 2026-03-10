package jp.gr.java_conf.SenseMusicClock.Music

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import android.content.Context
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.LinearLayoutManager
import jp.gr.java_conf.SenseMusicClock.PrefsManager
import jp.gr.java_conf.SenseMusicClock.R
import kotlinx.coroutines.launch


class JacketAdapter(
    initialItems: List<MediaItem> = emptyList(),
    private val placeholderRes: Int,
    private val activity: AppCompatActivity,
    private val onItemClick: (MediaItem) -> Unit = {},
    private val onItemLongClick: (MediaItem, View) -> Unit = { _, _ -> }
) : ListAdapter<MediaItem, JacketAdapter.ViewHolder>(DIFF) {


    private var isTextPlus: Boolean = false

    init {
        submitList(initialItems.toList())
        activity.lifecycleScope.launch {

            PrefsManager.getTileTitleDisplayFlow(activity).collect { newValue ->
                isTextPlus = newValue

            }
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val artwork: ImageView = view.findViewById(R.id.artwork)
        val TitleTextView = view.findViewById<android.widget.TextView>(R.id.titleTextView)
        val container = view.findViewById<ConstraintLayout>(R.id.item_square_container)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        if (viewType == TYPE_PLUSTEXT) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_square_textplus, parent, false)
            return ViewHolder(view)

        } else {
            val view =
                LayoutInflater.from(parent.context).inflate(R.layout.item_square, parent, false)
            return ViewHolder(view)

        }

    }

    private var recyclerView: RecyclerView? = null

    override fun onAttachedToRecyclerView(rv: RecyclerView) {
        super.onAttachedToRecyclerView(rv)
        Log.d("LeakCheck", "MyAdapter onAttachedToRecyclerView adapter=$this")

        recyclerView = rv
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        Log.d("LeakCheck", "MyAdapter onDetachedFromRecyclerView adapter=$this")

        super.onDetachedFromRecyclerView(recyclerView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        val params = holder.itemView.layoutParams

        recyclerView?.let {

            if (it.layoutManager is LinearLayoutManager) {
                val lm = it.layoutManager as LinearLayoutManager

                if (lm.orientation == LinearLayoutManager.HORIZONTAL) {
                    // 横スクロール → 高さを基準に正方形
                    params.width = (it.height * 0.9).toInt() // 少し余白を入れる
                    params.height = (it.height * 0.9).toInt()
                } else {
                    // 縦スクロール → 幅を基準に正方形
                    params.width = (it.width * 0.9).toInt()
                    params.height = (it.width * 0.9).toInt()
                }
            }

            holder.itemView.layoutParams = params
        }
        val track = getItem(position)

        // 再利用時の残存 drawable/リスナを切る
        holder.artwork.setImageDrawable(null)
        holder.container.setOnClickListener(null)
        holder.artwork.contentDescription = ""

        holder.artwork.load(track.mediaMetadata.artworkUri) {
            crossfade(true)
            memoryCachePolicy(coil.request.CachePolicy.DISABLED)
            diskCachePolicy(coil.request.CachePolicy.ENABLED)
            placeholder(R.drawable.default_album_art)
            error(R.drawable.default_album_art)


        }

        val title = track.mediaMetadata.title?.toString() ?: "Unknown Title"

        holder.artwork.contentDescription = title
        try {

            holder.TitleTextView.text = title
        } catch (e: Exception) {
            Log.d("JacketAdapter", "曲名入りではないレイアウトなのでスキップ")
        }






        holder.container.setOnClickListener { onItemClick(track) }
        holder.container.setOnLongClickListener {
            onItemLongClick(track, it)
            true
        }
    }


    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.artwork.setImageDrawable(null)
        holder.container.setOnClickListener(null)
    }

    override fun getItemViewType(position: Int): Int {

        //TODO レイアウト切り替え対応



        val layout_type = if (isTextPlus) {
            TYPE_PLUSTEXT

        } else {
            TYPE_NORMAL
        }
        return layout_type
    }

    /** 外部から差分更新する際は submitList を使う（内部で DiffUtil が効く） */
    // 既存のシグネチャを残しつつ、コミット完了コールバック対応のオーバーロードを追加


    fun setItems(newItems: List<MediaItem>, commitCallback: (() -> Unit)?) {
        // ListAdapter#submitList の commitCallback は Runnable なので合わせる
        submitList(newItems.toList(), Runnable {
            try {
                commitCallback?.invoke()
            } catch (_: Exception) {
            }
        })
    }

    fun getItems(): List<MediaItem> = currentList.toList()

    fun getItemPosition(track: MediaItem?): Int? {
        if (track == null) return null
        val idx = currentList.indexOfFirst { item ->
            item.mediaId == track.mediaId


        }
        return if (idx >= 0) idx else null
    }


    companion object {
        private const val TYPE_NORMAL = 0
        private const val TYPE_PLUSTEXT = 1
        private val DIFF = object : DiffUtil.ItemCallback<MediaItem>() {
            override fun areItemsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean {
                val oldId = oldItem.mediaId

                val newId = newItem.mediaId
                return oldId == newId
            }

            override fun areContentsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean {
                if (oldItem::class != newItem::class) return false
                val oldItemMediaMetadata = oldItem.mediaMetadata
                val newItemMediaMetadata = newItem.mediaMetadata
                val oldMetadataExtras = oldItemMediaMetadata.extras
                val newMetadataExtras = newItemMediaMetadata.extras

                val commonCriteria =
                    oldItemMediaMetadata.title.toString() == newItemMediaMetadata.title.toString() &&
                            oldItemMediaMetadata.albumTitle.toString() == newItemMediaMetadata.albumTitle.toString() &&
                            oldItemMediaMetadata.artist.toString() == newItemMediaMetadata.artist.toString()

                val localCriteria =
                    oldMetadataExtras?.getString("RELATIVE_PATH") == newMetadataExtras?.getString("RELATIVE_PATH") &&
                            oldItemMediaMetadata.trackNumber == newItemMediaMetadata.trackNumber



                return commonCriteria && localCriteria
            }
        }
    }
}