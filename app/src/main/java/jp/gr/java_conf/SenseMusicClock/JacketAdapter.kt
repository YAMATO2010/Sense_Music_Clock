// kotlin
package jp.gr.java_conf.SenseMusicClock

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class JacketAdapter(
    initialItems: List<Track> = emptyList(),
    private val placeholderRes: Int,
    private val onItemClick: (Track) -> Unit = {}
) : ListAdapter<Track, JacketAdapter.ViewHolder>(DIFF) {

    init {
        submitList(initialItems.toList())
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val artwork: ImageButton = view.findViewById(R.id.artwork)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_square, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val track = getItem(position)

        // 再利用時の残存 drawable/リスナを切る
        holder.artwork.setImageDrawable(null)
        holder.artwork.setOnClickListener(null)
        holder.artwork.contentDescription = ""

        try {
            val bmp = track.albumArt
            if (bmp != null && !bmp.isRecycled) {
                holder.artwork.setImageBitmap(bmp)
            } else {
                holder.artwork.setImageResource(placeholderRes)
            }
        } catch (e: Exception) {
            holder.artwork.setImageResource(placeholderRes)
        }

        holder.artwork.contentDescription = track.title
        holder.artwork.setOnClickListener { onItemClick(track) }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.artwork.setImageDrawable(null)
        holder.artwork.setOnClickListener(null)
    }

    /** 外部から差分更新する際は submitList を使う（内部で DiffUtil が効く） */
    fun setItems(newItems: List<Track>) {
        submitList(newItems.toList())
    }

    fun getItems(): List<Track> = currentList.toList()

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
                return  oldId == newId
            }

            override fun areContentsTheSame(oldItem: Track, newItem: Track): Boolean {
                if (oldItem::class != newItem::class) return false

                val commonCriteria =  oldItem.title == newItem.title &&
                        oldItem.album == newItem.album &&
                        oldItem.artist == newItem.artist
                val localCriteria = if (oldItem is localTrack && newItem is localTrack) {
                        oldItem.path == newItem.path &&
                        oldItem.trackNo == newItem.trackNo

                }else true

                return commonCriteria && localCriteria
            }
        }
    }
}
