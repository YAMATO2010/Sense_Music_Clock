package jp.gr.java_conf.SenseMusicClock.ui.list

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class UtilAdapter<T>(
    private val layoutId: Int,
    private val bind: (View, T) -> Unit,
    diff: DiffUtil.ItemCallback<T>
) : ListAdapter<T, UtilAdapter<T>.VH>(diff){

    private val items = mutableListOf<T>()

    inner class VH(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(layoutId, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) =
        bind(holder.view, getItem(position))



    override fun getItemCount() = items.size

    override fun getItem(position: Int) = items[position]



    fun setItems(newItems: List<T>, commitCallback: (() -> Unit)?) {
        // ListAdapter#submitList の commitCallback は Runnable なので合わせる
        submitList(newItems.toList(), Runnable {
            try {
                commitCallback?.invoke()
            } catch (e: Exception) {
                Log.w("UtilAdapter", "Commit callback threw an exception", e)
            }
        })
    }

    fun getItemPosition(item : T?): Int? {
        if (item == null) return null
        val idx = items.indexOfFirst { it == item }
        return if (idx >= 0) idx else null
    }
}