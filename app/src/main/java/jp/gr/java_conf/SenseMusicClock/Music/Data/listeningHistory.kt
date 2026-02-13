package jp.gr.java_conf.SenseMusicClock.Music.Data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import java.util.Date

@Entity
data class listeningHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Date = Date(),
    @Embedded val fileItem: FileItem
)



@Dao
interface listeningHistoryDao {
    // DAO methods would be defined here

    @Query("SELECT * FROM listeningHistory ORDER BY timestamp DESC")
    suspend fun loadAllListeningHistory(): List<listeningHistory>



    @Query("SELECT * FROM listeningHistory ORDER BY timestamp DESC LIMIT :limit;")
    suspend fun loadListeningHistory(limit : Int): List<listeningHistory>

    @Query("DELETE FROM listeningHistory WHERE timestamp < :beforeDate")
    suspend fun deleteOldListeningHistory(beforeDate: Date)

    @Delete
    suspend fun deleteListeningHistory(listeningHistory: listeningHistory)





}