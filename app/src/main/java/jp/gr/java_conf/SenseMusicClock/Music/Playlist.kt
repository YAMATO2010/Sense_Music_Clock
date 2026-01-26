package jp.gr.java_conf.SenseMusicClock.Music

import android.net.Uri
import androidx.room.Entity

@Entity(tableName = "playlists",primaryKeys = ["playlistId", "trackUri"])
data class Playlist (
    val playlistId : Long ,
    val playlistName : String,
    val trackUri     : Uri,
    val trackRelativePath : String,

)