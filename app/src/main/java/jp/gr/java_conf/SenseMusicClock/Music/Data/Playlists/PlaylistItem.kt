package jp.gr.java_conf.SenseMusicClock.Music.Data.Playlists

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import jp.gr.java_conf.SenseMusicClock.Music.Data.FileItem

@Entity(
    tableName = "playlistItems",
    primaryKeys = ["playlistId", "index"],
    foreignKeys = [
        ForeignKey(
            entity = PlayList::class,
            parentColumns = ["playlistId"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["playlistId"])]
)
data class PlaylistItem(
    val playlistId: Long,
    @Embedded val fileItem: FileItem,
    val index: Int

)
