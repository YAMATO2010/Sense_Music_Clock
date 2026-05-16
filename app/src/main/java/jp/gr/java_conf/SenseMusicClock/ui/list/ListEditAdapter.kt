package jp.gr.java_conf.SenseMusicClock.ui.list

import android.annotation.SuppressLint
import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import coil.load
import jp.gr.java_conf.SenseMusicClock.Music.LocalMusicFetcher
import jp.gr.java_conf.SenseMusicClock.ui.list.placeholder.PlaceholderContent.PlaceholderItem
import jp.gr.java_conf.SenseMusicClock.R


/**
 * [RecyclerView.Adapter] that can display a [PlaceholderItem].
 * TODO: Replace the implementation with code for your data type.
 */
class ListEditAdapter(
    values: List<MediaItemWithChecked>,
    private val ItemType: LIST_TYPE,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onCheckedChange: (Int, Boolean) -> Unit

) : ListAdapter<ListEditAdapter.MediaItemWithChecked, ListEditAdapter.EditAdapterViewHolder>(DIFF) {




    init {


        submitList(values.toList())


    }

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
                return BlockHolder(view)
            }

        }


    }

    override fun getItemViewType(position: Int): Int {

        //TODO レイアウト切り替え対応

        return when (ItemType) {
            LIST_TYPE.TYPE_BLOCK -> TYPE_BLOCK
            LIST_TYPE.TYPE_PLAY -> TYPE_PLAY

            LIST_TYPE.TYPE_ETC -> TYPE_ETC
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

    override fun onBindViewHolder(holder: EditAdapterViewHolder, position: Int) {
        val item = getItem(position)

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
        }

        holder.artWork.load(item.mediaItem.mediaMetadata.artworkUri) {

            crossfade(true)
            memoryCachePolicy(coil.request.CachePolicy.DISABLED)
            diskCachePolicy(coil.request.CachePolicy.ENABLED)
            placeholder(R.drawable.outline_hide_image_24)
            error(R.drawable.outline_hide_image_24)

        }

        holder.checkBox.setOnCheckedChangeListener { view, isChecked ->
            val position = holder.bindingAdapterPosition
            val currentItem = getItem(position)
            currentItem.isChecked = isChecked
            onCheckedChange(position, isChecked)
        }

    }


    override fun getItemCount(): Int = currentList.size

    fun setItems(newItems: List<MediaItemWithChecked>, commitCallback: (() -> Unit)?) {
        submitList(newItems.toList(), {
            try {
                commitCallback?.invoke()
            } catch (_: Exception) {
            }
        })
    }

    abstract class EditAdapterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleTextView: TextView = view.findViewById<TextView>(R.id.ItemTitleView)
        val artistTextView: TextView = view.findViewById<TextView>(R.id.ItemArtistView)
        val checkBox: CheckBox = view.findViewById<CheckBox>(R.id.listCheckBox)

        val artWork : ImageView = view.findViewById<ImageView>(R.id.ITEMArtwork)
    }

    class PlayHolder(view: View) : EditAdapterViewHolder(view) {
        val moveButton: ImageButton = view.findViewById<ImageButton>(R.id.moveButton)

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
        private val DIFF = object : DiffUtil.ItemCallback<MediaItemWithChecked>() {
            override fun areItemsTheSame(
                oldItem: MediaItemWithChecked,
                newItem: MediaItemWithChecked
            ): Boolean {
                val oldId = oldItem.mediaItem.mediaId

                val newId = newItem.mediaItem.mediaId
                return oldId == newId
            }

            override fun areContentsTheSame(
                oldItem: MediaItemWithChecked,
                newItem: MediaItemWithChecked
            ): Boolean {
                if (oldItem::class != newItem::class) return false
                val oldItemMediaMetadata = oldItem.mediaItem.mediaMetadata
                val newItemMediaMetadata = newItem.mediaItem.mediaMetadata
                val oldMetadataExtras = oldItemMediaMetadata.extras
                val newMetadataExtras = newItemMediaMetadata.extras

                val commonCriteria =
                    oldItemMediaMetadata.title.toString() == newItemMediaMetadata.title.toString() &&
                            oldItemMediaMetadata.albumTitle.toString() == newItemMediaMetadata.albumTitle.toString() &&
                            oldItemMediaMetadata.artist.toString() == newItemMediaMetadata.artist.toString()

                val localCriteria =
                    oldMetadataExtras?.getString(LocalMusicFetcher.EXTRA_RELATIVE_PATH) == newMetadataExtras?.getString(
                        LocalMusicFetcher.EXTRA_RELATIVE_PATH
                    ) && oldMetadataExtras?.getString(LocalMusicFetcher.EXTRA_DISPLAY_NAME) == newMetadataExtras?.getString(
                        LocalMusicFetcher.EXTRA_DISPLAY_NAME
                    ) &&
                            oldItemMediaMetadata.trackNumber == newItemMediaMetadata.trackNumber



                return commonCriteria && localCriteria
            }
        }


    }

    data class MediaItemWithChecked(
        val mediaItem: MediaItem,
        var isChecked: Boolean = false
    )

}