package jp.gr.java_conf.SenseMusicClock.Music

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class ListActivityAdapter<T>(
    private val layoutId: Int,
    private val bind: (View, T) -> Unit,
    diff: DiffUtil.ItemCallback<T>
) : ListAdapter<T, ListActivityAdapter<T>.VH>(diff){

    private val items = mutableListOf<T>()

    inner class VH(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(layoutId, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) =
        bind(holder.view, getItem(position))

    override fun getItemCount() = items.size


}
