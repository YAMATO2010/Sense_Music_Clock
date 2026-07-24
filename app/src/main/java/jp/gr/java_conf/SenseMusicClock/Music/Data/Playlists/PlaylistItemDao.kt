package jp.gr.java_conf.SenseMusicClock.Music.Data.Playlists

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert


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

