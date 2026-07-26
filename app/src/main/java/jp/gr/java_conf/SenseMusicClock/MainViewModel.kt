package jp.gr.java_conf.SenseMusicClock

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Small ViewModel to persist orientation and pending instant-scroll across config changes.
 */
class MainViewModel : ViewModel() {
    /** Last known orientation (Resources.configuration.orientation). Null if not set yet. */
    var lastOrientation: Int? = null

    /** If true, activity should perform an instant (no-animation) scroll to current track once adapter is ready. */


    private val _isHHmm = MutableLiveData<Boolean>(false)

    val isHHmm: LiveData<Boolean>
        get() = _isHHmm

    private val _tracks = MutableStateFlow<List<MediaItem>>(emptyList<MediaItem>())

    val tracks: StateFlow<List<MediaItem>>
        get() = _tracks


    private val _currentJacketsIndex: MutableSharedFlow<Int> = MutableSharedFlow<Int>(
        replay = 0, // 過去のイベントは再送しない
        extraBufferCapacity = 1
    )
    val currentJacketsIndex: SharedFlow<Int>
        get() = _currentJacketsIndex

    var lastIndex: Int? = null
        private set

    private val _sleepTimerEndAtTimeFlow = MutableStateFlow<Long?>(null)
    val sleepTimerEndAtTimeFlow: StateFlow<Long?>
        get() = _sleepTimerEndAtTimeFlow


    private fun setSleepTimerEndAtTime(newTime: Long) {
        _sleepTimerEndAtTimeFlow.value = newTime
    }

    fun setSleepTimerIfNeeded(newTime: Long) {
        if (_sleepTimerEndAtTimeFlow.value != newTime && isValidSleepTimerEndAtTime(newTime)) {
            setSleepTimerEndAtTime(newTime)
        }
    }

    fun isValidSleepTimerEndAtTime(endAtTime: Long? = _sleepTimerEndAtTimeFlow.value): Boolean {
        return endAtTime != null && endAtTime >= System.currentTimeMillis()
    }

    fun clearSleepTimerEndAtTime() {
        _sleepTimerEndAtTimeFlow.value = null
    }


    fun setTracks(newTracks: MutableList<MediaItem>) {
        _tracks.value = newTracks
    }

    fun setCurrentIndex(newIndex: Int) {
        viewModelScope.launch {
            _currentJacketsIndex.emit(newIndex)
            lastIndex = newIndex
        }
    }

    fun clearCurrentIndex() {
        setCurrentIndex(0)
    }

    fun clearTracks() {

        _tracks.value = emptyList<MediaItem>().toMutableList()

    }

    fun reverseIsHHmm() {
        _isHHmm.value = !(_isHHmm.value ?: false)
    }


}
