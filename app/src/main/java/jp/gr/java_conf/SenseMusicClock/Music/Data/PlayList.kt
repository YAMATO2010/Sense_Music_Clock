package jp.gr.java_conf.SenseMusicClock.Music.Data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.TypeConverter
import androidx.room.Update
import androidx.room.Upsert
import java.util.Date


@Entity(tableName = "playlists")
data class PlayList(
    @PrimaryKey(autoGenerate = true) val playlistId : Long = 0,
    val playlistName      : String,
    val deleted : Date? = null
)


@Entity(tableName = "playlistItems",
    primaryKeys = ["playlistId", "index"],
    foreignKeys = [
        ForeignKey(
            entity =   PlayList::class,
            parentColumns = ["playlistId"],
            childColumns =  ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["playlistId"])])
data class PlaylistItem (
    val playlistId        : Long  ,
    @Embedded val fileItem: FileItem,
    val index: Int

)


@Dao
interface PlaylistDao {
    // DAO methods would be defined here

    @Query("SELECT * FROM playlists")
    suspend fun loadAllPlaylists(): List<PlayList>

    @Query("SELECT * FROM playlists WHERE playlistId = :playlistId")
    suspend fun loadPlaylistById(playlistId: Long): PlayList?


    @Delete
    suspend fun deletePlaylist(playlist: PlayList)

    @Update
    suspend fun updatePlaylist(playlist: PlayList)

    @Upsert
    suspend fun insertPlaylist(playlist: PlayList): Long

}

@Dao
interface PlaylistItemDao {
    // DAO methods would be defined here

    @Query("SELECT * FROM playlistItems WHERE playlistId = :playlistId")
    suspend fun loadItemsForPlaylist(playlistId: Long): List<PlaylistItem>

    @Delete
    suspend fun deletePlaylistItem(playlistItem: PlaylistItem)

    @Query("SELECT MAX('index') FROM playlistItems WHERE playlistId = :playlistId")
    suspend fun getMaxIndexForPlaylist(playlistId: Long): Int?

    @Update
    suspend fun updatePlaylistItem(playlistItem: PlaylistItem)

    @Upsert
    suspend fun insertPlaylistItem(playlistItem: PlaylistItem)

    @Upsert
    suspend fun insertPlaylistItems(playlistItems: List<PlaylistItem>)
}

