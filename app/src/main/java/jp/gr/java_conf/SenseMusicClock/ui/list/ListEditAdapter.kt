package jp.gr.java_conf.SenseMusicClock.ui.list

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.RecyclerView
import coil.load
import jp.gr.java_conf.SenseMusicClock.R


class ListEditAdapter(
    values: List<MediaItemWithChecked>,
    private val ItemType: LIST_TYPE,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onCheckedChange: (Int, Boolean) -> Unit
) : RecyclerView.Adapter<ListEditAdapter.EditAdapterViewHolder>() {

    private val items = values.toMutableList()

    val currentList: List<MediaItemWithChecked>
        get() = items.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EditAdapterViewHolder {
        return when (viewType) {
            TYPE_PLAY -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.fragment_list_edit_item, parent, false)
                PlayHolder(view)
            }

            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.fragment_list_add_item, parent, false)
                BlockHolder(view)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (ItemType) {
            LIST_TYPE.TYPE_BLOCK -> TYPE_BLOCK
            LIST_TYPE.TYPE_PLAY -> TYPE_PLAY
            LIST_TYPE.TYPE_ETC -> TYPE_ETC
        }
    }

    override fun onBindViewHolder(holder: EditAdapterViewHolder, position: Int) {
        val item = items[position]

        holder.titleTextView.text = item.mediaItem.mediaMetadata.title ?: "Unknown Title"
        holder.artistTextView.text = item.mediaItem.mediaMetadata.artist ?: "Unknown Artist"
        if (holder is PlayHolder) {
            @SuppressLint("ClickableViewAccessibility")
            holder.moveButton.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onStartDrag(holder)
                }
                true
            }
            holder.itemView.setOnLongClickListener {
                onStartDrag(holder)
                true
            }
        } else {
            holder.itemView.setOnLongClickListener(null)
        }

        holder.artWork.load(item.mediaItem.mediaMetadata.artworkUri) {
            crossfade(true)
            memoryCachePolicy(coil.request.CachePolicy.DISABLED)
            diskCachePolicy(coil.request.CachePolicy.ENABLED)
            placeholder(R.drawable.outline_hide_image_24)
            error(R.drawable.outline_hide_image_24)
            listener(
                onError = { _, result ->
                    Log.w("ListEditAdapter", "Failed to load artwork", result.throwable)
                }
            )
        }

        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.isChecked = item.isChecked
        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            val itemPosition = holder.bindingAdapterPosition
            if (itemPosition == RecyclerView.NO_POSITION) return@setOnCheckedChangeListener
            items[itemPosition].isChecked = isChecked
            onCheckedChange(itemPosition, isChecked)
        }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<MediaItemWithChecked>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun setItems(newItems: List<MediaItemWithChecked>, commitCallback: (() -> Unit)?) {
        submitList(newItems.toList())
        try {
            commitCallback?.invoke()
        } catch (_: Exception) {
        }
    }

    fun moveItem(fromPos: Int, toPos: Int): Boolean {
        if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) return false
        if (fromPos !in items.indices || toPos !in items.indices) return false
        if (fromPos == toPos) return false

        val item = items.removeAt(fromPos)
        items.add(toPos, item)
        notifyItemMoved(fromPos, toPos)
        return true
    }

    abstract class EditAdapterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleTextView: TextView = view.findViewById(R.id.ItemTitleView)
        val artistTextView: TextView = view.findViewById(R.id.ItemArtistView)
        val checkBox: CheckBox = view.findViewById(R.id.listCheckBox)
        val artWork: ImageView = view.findViewById(R.id.ITEMArtwork)
    }

    class PlayHolder(view: View) : EditAdapterViewHolder(view) {
        val moveButton: ImageButton = view.findViewById(R.id.moveButton)
    }

    class BlockHolder(view: View) : EditAdapterViewHolder(view)

    enum class LIST_TYPE {
        TYPE_BLOCK,
        TYPE_PLAY,
        TYPE_ETC,
    }

    companion object {
        const val TYPE_BLOCK = 0
        const val TYPE_PLAY = 1
        const val TYPE_ETC = 3
    }

    data class MediaItemWithChecked(
        val mediaItem: MediaItem,
        var isChecked: Boolean = false
    )
}
