package jp.gr.java_conf.SenseMusicClock.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import jp.gr.java_conf.SenseMusicClock.Music.Data.SearchHistory
import jp.gr.java_conf.SenseMusicClock.R

class SearchHistoryAdapter (
    InitialValue: List<SearchHistory>,
    private val onBind :(SearchAdapter.SearchViewHolder, Int, SearchHistory,) -> Unit

): ListAdapter<SearchHistory, SearchAdapter.SearchViewHolder>(DIFF) {

    init {
        submitList(InitialValue.toList())
    }


    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SearchAdapter.SearchViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_display_item, parent, false)
        return SearchAdapter.SearchViewHolder(view)


    }

    override fun onBindViewHolder(
        holder: SearchAdapter.SearchViewHolder,
        position: Int
    ) {
        val item = getItem(position)
        onBind(holder, position, item)
    }


    companion object{
        val DIFF = object : DiffUtil.ItemCallback<SearchHistory>() {
            override fun areItemsTheSame(oldItem: SearchHistory, newItem: SearchHistory): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: SearchHistory, newItem: SearchHistory): Boolean {
                return oldItem.equals(newItem)
            }
        }
    }
}

