package jp.gr.java_conf.SenseMusicClock.Music.Data.Playlists

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert

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
