package jp.gr.java_conf.SenseMusicClock.ui.list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView

import android.widget.TextView

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import jp.gr.java_conf.SenseMusicClock.Music.JacketAdapter.ViewHolder
import jp.gr.java_conf.SenseMusicClock.Music.LocalMusicFetcher
import jp.gr.java_conf.SenseMusicClock.R

class ListDisplayAdapter(

    val onBind: (DisplayViewHolder, LocalMusicFetcher.MediaStoreAudioSummary?, Int) -> Unit
) : ListAdapter<LocalMusicFetcher.MediaStoreAudioSummary, ListDisplayAdapter.DisplayViewHolder>(
    DIFF_CALLBACK
) {


    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): DisplayViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_display_item, parent, false)



        return DisplayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DisplayViewHolder, position: Int) {
        val item = getItem(position)
        // Note that item can be null. ViewHolder must support binding a
        // null item as a placeholder.
        holder.titleTextView.text = item?.title ?: "Unknown Title"
        holder.artistTextView.text = item?.artist ?: "Unknown Artist"
        holder.itemArtwork.load(item?.albumArtUri) {

            crossfade(true)
            memoryCachePolicy(coil.request.CachePolicy.DISABLED)
            diskCachePolicy(coil.request.CachePolicy.ENABLED)
            placeholder(R.drawable.outline_hide_image_24)
            error(R.drawable.outline_hide_image_24)

        }
    }

    inner class DisplayViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        val titleTextView: TextView = view.findViewById<TextView>(R.id.ItemTitleView)
        val artistTextView: TextView = view.findViewById<TextView>(R.id.ItemArtistView)


        val itemArtwork: ImageView = view.findViewById(R.id.Itemartwork)

    }

    companion object {
        val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<LocalMusicFetcher.MediaStoreAudioSummary>() {
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