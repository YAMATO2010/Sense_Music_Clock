package jp.gr.java_conf.SenseMusicClock.Music.Data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import java.util.Date


@Entity
data class SearchHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Date = Date(),
    val keyword: String
)

@Dao
interface SearchHistoryDao {
    // DAO methods would be defined here
    @Query("SELECT * FROM SearchHistory ORDER BY timestamp DESC")
    suspend fun loadAllSearchHistory(): List<SearchHistory>

    @Query("SELECT * FROM SearchHistory ORDER BY timestamp DESC LIMIT :limit;")
    suspend fun loadSearchHistory(limit : Int): List<SearchHistory>

    @Query("DELETE FROM SearchHistory WHERE timestamp < :beforeDate")
    suspend fun deleteOldSearchHistory(beforeDate: Date)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchHistory(searchHistory: SearchHistory)

    @Delete
    suspend fun deleteSearchHistory(searchHistory: SearchHistory)




}