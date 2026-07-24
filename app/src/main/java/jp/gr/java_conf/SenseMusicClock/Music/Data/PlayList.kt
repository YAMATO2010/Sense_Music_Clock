package jp.gr.java_conf.SenseMusicClock.Music.Data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
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

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists")
    suspend fun loadAllPlaylists(): List<PlayList>

    @Query("SELECT * FROM playlists WHERE playlistId = :playlistId")
    suspend fun loadPlaylistById(playlistId: Long): PlayList?

    @Query("SELECT EXISTS(SELECT 1  FROM playlists WHERE playlistName  = :playlistName)")
    suspend fun existsPlaylist(playlistName: String): Boolean

    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)


    @Delete
    suspend fun deletePlaylist(playlist: PlayList)


    @Update
    suspend fun updatePlaylist(playlist: PlayList)

    @Upsert
    suspend fun upsertPlaylist(playlist: PlayList): Long

}

@Dao
interface PlaylistItemDao {

    @Query("SELECT * FROM playlistItems WHERE playlistId = :playlistId")
    suspend fun loadItemsForPlaylist(playlistId: Long): List<PlaylistItem>

    @Delete
    suspend fun deletePlaylistItem(playlistItem: PlaylistItem)

    @Query("SELECT MAX('index') FROM playlistItems WHERE playlistId = :playlistId")
    suspend fun getMaxIndexForPlaylist(playlistId: Long): Int?

    @Query("DELETE FROM playlistItems WHERE playlistId = :playlistId")
    suspend fun deleteItemsForPlaylist(playlistId: Long)

    @Update
    suspend fun updatePlaylistItem(playlistItem: PlaylistItem)

    @Upsert
    suspend fun upsertPlaylistItem(playlistItem: PlaylistItem)

    @Upsert
    suspend fun upsertPlaylistItems(playlistItems: List<PlaylistItem>)
}

