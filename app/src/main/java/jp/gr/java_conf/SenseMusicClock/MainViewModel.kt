package jp.gr.java_conf.SenseMusicClock

import androidx.lifecycle.ViewModel

/**
 * Small ViewModel to persist orientation and pending instant-scroll across config changes.
 */
class MainViewModel : ViewModel() {
    /** Last known orientation (Resources.configuration.orientation). Null if not set yet. */
    var lastOrientation: Int? = null

    /** If true, activity should perform an instant (no-animation) scroll to current track once adapter is ready. */
    var pendingInstantScroll: Boolean = false
}

