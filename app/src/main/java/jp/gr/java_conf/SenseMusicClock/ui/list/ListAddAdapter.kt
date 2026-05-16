package jp.gr.java_conf.SenseMusicClock.ui.list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import jp.gr.java_conf.SenseMusicClock.Music.LocalMusicFetcher
import jp.gr.java_conf.SenseMusicClock.R

class ListAddAdapter(
    diffCallback: DiffUtil.ItemCallback<LocalMusicFetcher.MediaStoreAudioSummary>,
    val onChecked : (Long, Boolean) -> Unit,
    val onBind : (AddViewHolder, LocalMusicFetcher.MediaStoreAudioSummary?, Int) -> Unit
) :
    PagingDataAdapter<LocalMusicFetcher.MediaStoreAudioSummary, ListAddAdapter.AddViewHolder>(
        diffCallback
    ) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): AddViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.fragment_list_add_item, parent, false)


        return AddViewHolder(view)
    }

    override fun onBindViewHolder(holder: AddViewHolder, position: Int) {
        val item = getItem(position)
        // Note that item can be null. ViewHolder must support binding a
        // null item as a placeholder.
        holder.titleTextView.text = item?.title ?: "Unknown Title"
        holder.artistTextView.text = item?.artist ?: "Unknown Artist"
        holder.checkBox.isChecked = false
        onBind(holder, item, position)
        holder.checkBox.setOnClickListener { _,->
            if (item != null) {

                onChecked(item.id, holder.checkBox.isChecked)
            }

        }
        holder.artWork.load(item?.albumArtUri) {

            crossfade(true)
            memoryCachePolicy(coil.request.CachePolicy.DISABLED)
            diskCachePolicy(coil.request.CachePolicy.ENABLED)
            placeholder(R.drawable.outline_hide_image_24)
            error(R.drawable.outline_hide_image_24)

        }
    }

    inner class AddViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        val titleTextView: TextView = view.findViewById<TextView>(R.id.ItemTitleView)
        val artistTextView: TextView = view.findViewById<TextView>(R.id.ItemArtistView)
        val checkBox: CheckBox = view.findViewById<CheckBox>(R.id.listCheckBox)

        val artWork : ImageView = view.findViewById<ImageView>(R.id.ITEMArtwork)

    }

    companion object{
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<LocalMusicFetcher.MediaStoreAudioSummary>() {
            override fun areItemsTheSame(
                oldItem: LocalMusicFetcher.MediaStoreAudioSummary,
                newItem: LocalMusicFetcher.MediaStoreAudioSummary
            ): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(
                oldItem: LocalMusicFetcher.MediaStoreAudioSummary,
                newItem: LocalMusicFetcher.MediaStoreAudioSummary
            ): Boolean {
                return oldItem == newItem
            }
        }
    }


}