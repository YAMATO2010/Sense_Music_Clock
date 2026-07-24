package jp.gr.java_conf.SenseMusicClock

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import java.io.File

class BackgroundsImageFileAdapter(
    initialItems: List<File> = emptyList(),
    private val context: Context,
    private val onItemClick: (File) -> Unit = {}

) : ListAdapter<File, BackgroundsImageFileAdapter.ViewHolder>(DIFF) {

    init {
        submitList(initialItems.toList())
    }


    fun setItems(newItems: List<File>) {
        submitList(newItems.toList())
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        val image: ImageView = view.findViewById(R.id.resultImageView)

        val fileName: TextView = view.findViewById(R.id.resultTextView)

        val container: LinearLayout = view.findViewById(R.id.ResultItemWithImageContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v =
            LayoutInflater.from(parent.context)
                .inflate(R.layout.search_result_with_image, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {


        val file = getItem(position)
        // 再利用時の残存 drawable/リスナを切る
        holder.image.setImageDrawable(null)
        holder.image.setOnClickListener(null)
        holder.image.contentDescription = ""
        holder.fileName.setTextColor(context.getColorStateList(R.color.black))

        if (file == File("")) {

            holder.image.load(R.drawable.outline_hide_image_24) {
                crossfade(true)
                placeholder(R.drawable.default_album_art)
                error(R.drawable.outline_hide_image_24)
                allowHardware(false)
                listener(onError = { _, result ->
                    Log.w("BackgroundsImageFileAdapter", "Image load error: ${result.throwable}")
                })
            }


            holder.image.contentDescription = "デフォルト背景"
            holder.fileName.text = "デフォルト背景"
        } else {


            holder.image.load(file) {
                crossfade(true)
                placeholder(R.drawable.default_album_art)
                error(R.drawable.outline_hide_image_24)
                allowHardware(false)
                listener(onError = { _, result ->
                    Log.w("BackgroundsImageFileAdapter", "Image load error: ${result.throwable}")
                })
            }

            holder.image.contentDescription = file.name
            holder.fileName.text = file.name
        }
        // ensure container and the full itemView are clickable (some layouts may intercept clicks)
        holder.container.isClickable = true
        holder.container.setOnClickListener {
            Log.d("SearchMusicAdapter", "container clicked: ${file.name}")
            onItemClick(file)
        }
        // also attach listener to itemView itself to be robust against view-hierarchy click interception
        holder.itemView.isClickable = true
        holder.itemView.setOnClickListener {
            Log.d("SearchMusicAdapter", "itemView clicked: ${file.name} ")
            onItemClick(file)
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.image.setImageDrawable(null)
        holder.image.setOnClickListener(null)
        holder.fileName.text = null
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<File>() {
            override fun areItemsTheSame(oldItem: File, newItem: File): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(oldItem: File, newItem: File): Boolean {

                return oldItem.path == newItem.path
            }
        }
    }


}