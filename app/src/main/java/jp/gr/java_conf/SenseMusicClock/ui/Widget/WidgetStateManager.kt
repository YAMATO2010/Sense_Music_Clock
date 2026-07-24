package jp.gr.java_conf.SenseMusicClock.ui.Widget

import android.content.Context
import androidx.core.net.toUri
import jp.gr.java_conf.SenseMusicClock.PrefsManager
import kotlinx.coroutines.flow.Flow

object WidgetStateManager {


    suspend fun getWidgetState(context: Context): WidgetState {
        // Implement logic to retrieve the current widget state
        // For example, you might fetch data from a database or shared preferences
        val title = PrefsManager.getWidgetTitle(context = context)
        val artworkUriString = PrefsManager.getWidgetArtworkUri(context = context)

        val playing = PrefsManager.getWidgetIsPlaying(context = context)

        return WidgetState(
            title = title,
            artwork = artworkUriString.toUri(),
            playing = playing
        )

    }

    suspend fun updateWidgetState(context: Context, newState: WidgetState) {
        // Implement logic to update the widget state
        // For example, you might save data to a database or shared preferences
        PrefsManager.setWidgetState(
            context = context,
            title = newState.title,
            artworkUri = newState.artwork,
            isPlaying = newState.playing,
        )

    }

    fun getWidgetStateFlow(context: Context): Flow<WidgetState> {
        return PrefsManager.getWidgetStateFlow(context)
    }

}
