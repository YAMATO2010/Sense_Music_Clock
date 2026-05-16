package jp.gr.java_conf.SenseMusicClock.ui.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.gr.java_conf.SenseMusicClock.R

class SearchAdapter (
    InitialValue: List<Long>,
    private val onBind :(SearchViewHolder, Int, Long,) -> Unit

): ListAdapter<Long, SearchAdapter.SearchViewHolder>(DIFF) {

    init {
        submitList(InitialValue.toList())
    }


    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SearchViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_display_item, parent, false)
        return SearchViewHolder(view)


    }

    override fun onBindViewHolder(
        holder: SearchViewHolder,
        position: Int
    ) {
        val item = getItem(position)
        onBind(holder, position, item)
    }

    class SearchViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        val Title = view.findViewById<TextView>(R.id.ItemTitleView)

        val subText = view.findViewById<TextView>(R.id.ItemArtistView)
        val Image = view.findViewById<ImageView>(R.id.Itemartwork)
        val Button = view.findViewById<ImageButton>(R.id.etcButton)
        val container = view.findViewById<View>(R.id.listItemContainer_display)

    }
    companion object{
        val DIFF = object : DiffUtil.ItemCallback<Long>() {
            override fun areItemsTheSame(oldItem: Long, newItem: Long): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(oldItem: Long, newItem: Long): Boolean {
                return oldItem.equals(newItem)
            }
        }
    }
}