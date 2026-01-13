/*
package jp.gr.java_conf.SenseMusicClock.Music.forSpotify


import android.content.Context
import android.util.Log

import jp.gr.java_conf.SenseMusicClock.SpotifyTrack
import


class SpotifyAdapterImpl(
    private val clientId: String,
    private val redirectUri: String
) : SpotifyAdapter {

    private var spotifyAppRemote: SpotifyAppRemote? = null
    private var playerStateSub: Subscription<PlayerState>? = null

    override fun connect(context: Context, onConnected: (Boolean, Throwable?) -> Unit) {
        val params = ConnectionParams.Builder(clientId)
            .setRedirectUri(redirectUri)
            .showAuthView(true)
            .build()

        SpotifyAppRemote.connect(context, params, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                spotifyAppRemote = appRemote
                Log.d("SpotifyAdapter", "connected")
                onConnected(true, null)
            }

            override fun onFailure(throwable: Throwable) {
                Log.e("SpotifyAdapter", "connect failed", throwable)
                onConnected(false, throwable)
            }
        })
    }

    override fun disconnect() {
        spotifyAppRemote?.let {
            SpotifyAppRemote.disconnect(it)
        }
        spotifyAppRemote = null
    }

    override fun isConnected(): Boolean = spotifyAppRemote?.isConnected == true

    override fun playSpotifyUri(spotifyUri: String) {
        spotifyAppRemote?.playerApi?.play(spotifyUri)
    }

    override fun pause() {
        spotifyAppRemote?.playerApi?.pause()
    }

    override fun resume() {
        spotifyAppRemote?.playerApi?.resume()
    }

    override fun subscribeToPlayerState(onState: (SpotifyTrack) -> Unit) {
        val remote = spotifyAppRemote ?: return
        playerStateSub = remote.playerApi.subscribeToPlayerState().setEventCallback { playerState: PlayerState ->
            val t: Track? = playerState.track
            val spotifyTrack = SpotifyTrack(
                trackId = t?.uri,
                title = t?.name,
                artist = t?.artist?.name,
                album = t?.album?.name,
                albumArt = t?.imageUri?.raw
            )
            onState(spotifyTrack)
        }
    }

    override fun unsubscribeFromPlayerState() {
        playerStateSub?.cancel()
        playerStateSub = null
    }
}


 */