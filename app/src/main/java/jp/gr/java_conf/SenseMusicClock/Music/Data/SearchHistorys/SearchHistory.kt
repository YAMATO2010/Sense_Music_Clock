package jp.gr.java_conf.SenseMusicClock.Music.Data.SearchHistorys

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import java.util.Date


@Entity
data class SearchHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Date = Date(),
    val itemID: Long,
    val itemType: String
) {
    companion object {
        const val TYPE_ARTIST = "artist"
        const val TYPE_ALBUM = "album"
        const val TYPE_SONG = "song"
        const val TYPE_UNKNOWN = "unknown"
    }
}

