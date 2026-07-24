package jp.gr.java_conf.SenseMusicClock.Music.Data.Blacklists

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert

@Dao
interface BlocklistDao {


    @Query("SELECT * FROM blockLists")
    suspend fun loadAllBlocklists(): List<BlockList>

    @Query("SELECT * FROM blockLists WHERE blockListID = :blockListID")
    suspend fun loadBlocklistById(blockListID: Long): BlockList?

    @Query("SELECT EXISTS(SELECT 1  FROM blockLists WHERE blockListName = :blockListName)")
    suspend fun existsBlocklist(blockListName: String): Boolean


    @Query("DELETE FROM blockLists WHERE blockListID = :blockListID")
    suspend fun deleteBlocklist(blockListID: Long)

    @Delete
    suspend fun deleteBlockList(blockList: BlockList)

    @Update
    suspend fun updateBlockList(blockList: BlockList)

    @Upsert
    suspend fun upsertBlockList(blockList: BlockList): Long
}
