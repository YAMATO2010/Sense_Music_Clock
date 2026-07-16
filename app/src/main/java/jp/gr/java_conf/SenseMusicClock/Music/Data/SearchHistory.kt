package jp.gr.java_conf.SenseMusicClock.Music.Data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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
){
    companion object{
        const val TYPE_ARTIST = "artist"
        const val TYPE_ALBUM = "album"
        const val TYPE_SONG = "song"
        const val TYPE_UNKNOWN = "unknown"
    }
}

@Dao
interface SearchHistoryDao {

    @Query("SELECT * FROM SearchHistory ORDER BY timestamp DESC")
    suspend fun loadAllSearchHistory(): List<SearchHistory>

    @Query("SELECT * FROM SearchHistory ORDER BY timestamp DESC LIMIT :limit;")
    suspend fun loadSearchHistory(limit : Int): List<SearchHistory>

    @Query("SELECT * FROM SearchHistory ORDER BY timestamp DESC LIMIT :count OFFSET :startIndex")
    suspend fun loadSearchHistoryInRange(startIndex: Int, count: Int): List<SearchHistory>


    @Query("DELETE FROM SearchHistory WHERE timestamp < :Date")
    suspend fun deleteOldSearchHistory(Date: Date)

    @Query("DELETE FROM SearchHistory ")
    suspend fun deleteAllSearchHistory(): Int

    @Query("DELETE FROM SearchHistory WHERE itemID = :itemID AND itemType = :itemType")
    suspend fun deleteSearchHistoryByItem(itemID: Long, itemType: String): Int

    @Upsert
    suspend fun insertSearchHistory(searchHistory: SearchHistory)

    @Delete
    suspend fun deleteSearchHistory(searchHistory: SearchHistory)








}