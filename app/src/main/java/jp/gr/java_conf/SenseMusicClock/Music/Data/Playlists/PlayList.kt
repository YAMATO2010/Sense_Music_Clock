package jp.gr.java_conf.SenseMusicClock.Music.Data.Playlists

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "playlists")
data class PlayList(
    @PrimaryKey(autoGenerate = true) val playlistId: Long = 0,
    val playlistName: String,
    val deleted: Date? = null
) {
    companion object {

        const val ADDED_AT_DESC_ID = -2L
        const val CURRENT_REMOVAL_ID = -3L
    }
}

