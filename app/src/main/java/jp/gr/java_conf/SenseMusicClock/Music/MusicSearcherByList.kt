/*
package jp.gr.java_conf.SenseMusicClock.Music

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.Switch
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import jp.gr.java_conf.SenseMusicClock.Music.JacketAdapter.Companion.DIFF
import jp.gr.java_conf.SenseMusicClock.R
import jp.gr.java_conf.SenseMusicClock.SpotifyTrack
import jp.gr.java_conf.SenseMusicClock.Track
import jp.gr.java_conf.SenseMusicClock.localTrack

class MusicSearcherByList(
    private val context: Context,
    private val recyclerView: RecyclerView,
    private val initialTracks: List<Track> = emptyList(),
    private val editText: TextInputEditText,


) {

    inner class SearchAdapter(
        initialItems: List<Track> = emptyList(),
        private val placeholderRes: Int,
        private val onItemClick: (Track) -> Unit = {}
    ): ListAdapter<Track, JacketAdapter.ViewHolder>(DIFF){

    }
    fun build() {
        editText.addTextChangedListener {


            val keyword = editText.text.toString()

            val targetTracks = if (keyword.isEmpty()) {
                emptyList()
            } else {
                initialTracks.filter { track ->
                    track.title.contains(keyword, ignoreCase = true) ||
                            track.artist.contains(keyword, ignoreCase = true) ||
                            track.album.contains(keyword, ignoreCase = true) ||
                            (if (track is localTrack) {
                                track.path.contains(keyword, ignoreCase = true)
                            } else {
                                false
                            })
                }
            }
        }
    }
}

class SearchMusicAdapter(
    initialItems: List<Track> = emptyList(),
    private val placeholderRes: Int,
    private val onItemClick: (Track) -> Unit = {}
) : ListAdapter<Track, JacketAdapter.ViewHolder>(DIFF) {

    init {
        submitList(initialItems.toList())
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
       val
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
    // 既存のシグネチャを残しつつ、コミット完了コールバック対応のオーバーロードを追加


    fun setItems(newItems: List<Track>, commitCallback: (() -> Unit)?) {
        // ListAdapter#submitList の commitCallback は Runnable なので合わせる
        submitList(newItems.toList(), Runnable {
            try {
                commitCallback?.invoke()
            } catch (_: Exception) {
            }
        })
    }

    fun getItems(): List<Track> = currentList.toList()

    fun getItemPosition(track: Track?): Int? {
        if (track == null) return null
        val idx = currentList.indexOfFirst { item ->
            when {
                item is localTrack && track is localTrack -> item.id == track.id
                item is SpotifyTrack && track is SpotifyTrack -> item.trackId == track.trackId
                else -> false
            }
        }
        return if (idx >= 0) idx else null
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


 */