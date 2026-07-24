package jp.gr.java_conf.SenseMusicClock.Music.Data.SearchHistorys

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import java.util.Date

@Dao
interface SearchHistoryDao {

    @Query("SELECT * FROM SearchHistory ORDER BY timestamp DESC")
    suspend fun loadAllSearchHistory(): List<SearchHistory>

    @Query("SELECT * FROM SearchHistory ORDER BY timestamp DESC LIMIT :limit;")
    suspend fun loadSearchHistory(limit: Int): List<SearchHistory>

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