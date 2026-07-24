package jp.gr.java_conf.SenseMusicClock.ui.search

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.gr.java_conf.SenseMusicClock.Music.Data.SearchHistory
import jp.gr.java_conf.SenseMusicClock.R

class SearchHistoryAdapter(
    InitialValue: List<SearchHistory>,
    private val onBind: (SearchHistoryVHolder, Int, SearchHistory) -> Unit

) : ListAdapter<SearchHistory, SearchHistoryAdapter.SearchHistoryVHolder>(DIFF) {

    init {
        submitList(InitialValue.toList())
    }


    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SearchHistoryVHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.search_history_item, parent, false)
        return SearchHistoryVHolder(view)


    }

    override fun onBindViewHolder(
        holder: SearchHistoryVHolder,
        position: Int
    ) {
        val item = getItem(position)
        onBind(holder, position, item)
    }


    class SearchHistoryVHolder(view: View) : RecyclerView.ViewHolder(view) {

        val Title = view.findViewById<TextView>(R.id.ItemTitleView)

        val subText = view.findViewById<TextView>(R.id.ItemArtistView)
        val Image = view.findViewById<ImageView>(R.id.ItemArtwork)
        val deleteButton = view.findViewById<ImageView>(R.id.history_delete_button)

        val container = view.findViewById<View>(R.id.listItemContainer_display)

    }

    data class HistoryMetadata(
        val id: String = "",
        val type: String = SearchHistory.TYPE_UNKNOWN,
        val title: String = "UNKNOWN",
        val imageUrl: Uri = Uri.EMPTY,
    )

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<SearchHistory>() {
            override fun areItemsTheSame(oldItem: SearchHistory, newItem: SearchHistory): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(
                oldItem: SearchHistory,
                newItem: SearchHistory
            ): Boolean {
                return oldItem.equals(newItem)
            }
        }
    }
}

