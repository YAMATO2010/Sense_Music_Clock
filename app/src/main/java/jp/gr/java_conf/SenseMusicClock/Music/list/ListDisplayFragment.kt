package jp.gr.java_conf.SenseMusicClock.Music.list

import android.media.browse.MediaBrowser
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.activityViewModels
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.DiffUtil
import coil.load
import jp.gr.java_conf.SenseMusicClock.Music.Data.FileItem
import jp.gr.java_conf.SenseMusicClock.R
import jp.gr.java_conf.SenseMusicClock.databinding.FragmentListDisplayBinding
import jp.gr.java_conf.SenseMusicClock.getDisplayName
import jp.gr.java_conf.SenseMusicClock.getRelativePath
import kotlin.getValue


class ListDisplayFragment : Fragment() {


    private val sharedViewModel: ListViewModel by activityViewModels()

    private var _binding: FragmentListDisplayBinding? = null

    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentListDisplayBinding.inflate(inflater, container, false)
















        sharedViewModel.run {
            list.observe(requireActivity()) { value ->
                value.getOrNull(0)?.let {
                    binding.img1.artworkLoad(it.mediaMetadata.artworkUri)
                }
                value.getOrNull(1)?.let {
                    binding.img2.artworkLoad(it.mediaMetadata.artworkUri)
                }
                value.getOrNull(2)?.let {
                    binding.img3.artworkLoad(it.mediaMetadata.artworkUri)
                }
                value.getOrNull(3)?.let {
                    binding.img4.artworkLoad(it.mediaMetadata.artworkUri)
                }

            }

            listName.observe(requireActivity()) { value ->

                binding.listName.text = value

            }

            listId.observe(requireActivity()) { value ->


            }

        }





        return binding.root

    }

    fun adapterInit() {
        val diff = object : DiffUtil.ItemCallback<MediaItem>() {
            override fun areItemsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean =
                oldItem.mediaId == newItem.mediaId

            override fun areContentsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean =
                oldItem.getRelativePath() == newItem.getRelativePath() && oldItem.getDisplayName() == newItem.getDisplayName()
        }

        val adapter = UtilAdapter<MediaItem>(
            R.layout.list_display_item, { view, item ->

                val title = view.findViewById<TextView>(R.id.ItemTitleView)
                val artist = view.findViewById<TextView>(R.id.ItemArtistView)
                val artwork = view.findViewById<ImageView>(R.id.Itemartwork)
                val etcButton = view.findViewById<ImageButton>(R.id.etcButton)
                val container = view.findViewById<ConstraintLayout>(R.id.listItemContainer_display)

                title.text = item.mediaMetadata.title
                artist.text = item.mediaMetadata.artist

                artwork.artworkLoad(item.mediaMetadata.artworkUri)
                container.setOnClickListener {

                }







            }, diff
        )




    }
    fun ImageView.artworkLoad(item : Any?){
        this.load(item){

            crossfade(true)
            memoryCachePolicy(coil.request.CachePolicy.DISABLED)
            diskCachePolicy(coil.request.CachePolicy.ENABLED)
            placeholder(R.drawable.outline_hide_image_24)
            error(R.drawable.outline_hide_image_24)

        }
    }


}