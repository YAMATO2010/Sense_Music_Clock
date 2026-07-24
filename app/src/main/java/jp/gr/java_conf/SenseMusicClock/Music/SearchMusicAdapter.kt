package jp.gr.java_conf.SenseMusicClock.Music

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import jp.gr.java_conf.SenseMusicClock.R
import jp.gr.java_conf.SenseMusicClock.keysForTrackTopLevel

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
            LayoutInflater.from(parent.context)
                .inflate(R.layout.search_result_with_image, parent, false)
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
            onItemClick(track)
        }
        // also attach listener to itemView itself to be robust against view-hierarchy click interception
        holder.itemView.isClickable = true
        holder.itemView.setOnClickListener {
            onItemClick(track)
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.artwork.setImageDrawable(null)
        holder.artwork.setOnClickListener(null)
        holder.title.text = null
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
                    oldMetadata.extras?.getString("RELATIVE_PATH") == newMetadata.extras?.getString(
                        "RELATIVE_PATH"
                    ) &&
                            oldMetadata.trackNumber == newMetadata.trackNumber



                return commonCriteria && localCriteria
            }
        }
    }
}
