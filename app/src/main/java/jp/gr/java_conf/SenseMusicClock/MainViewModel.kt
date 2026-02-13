package jp.gr.java_conf.SenseMusicClock

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem

/**
 * Small ViewModel to persist orientation and pending instant-scroll across config changes.
 */
class MainViewModel : ViewModel() {
    /** Last known orientation (Resources.configuration.orientation). Null if not set yet. */
    var lastOrientation: Int? = null

    /** If true, activity should perform an instant (no-animation) scroll to current track once adapter is ready. */
    var pendingInstantScroll: Boolean = false

    private  val _tracks  =  MutableLiveData<MutableList<MediaItem>>(mutableListOf())

    val tracks: LiveData<MutableList<MediaItem>>
        get() = _tracks


    fun setTracks(newTracks: MutableList<MediaItem>) {
        _tracks.value = newTracks
    }

    fun clearTracks() {

        _tracks.value = emptyList<MediaItem>().toMutableList()

    }

}

