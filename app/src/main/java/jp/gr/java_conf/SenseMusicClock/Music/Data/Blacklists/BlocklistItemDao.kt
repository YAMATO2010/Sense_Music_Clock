package jp.gr.java_conf.SenseMusicClock.Music.Data.Blacklists

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert

@Dao
interface BlocklistItemDao {

    @Query("SELECT * FROM blocklistItems WHERE blocklistId = :blocklistId")
    suspend fun loadItemsForBlocklist(blocklistId: Long): List<BlocklistItem>

    @Query("DELETE FROM blocklistItems WHERE blocklistId = :blockListID")
    suspend fun deleteItemsForBlocklist(blockListID: Long)


    @Delete
    suspend fun deleteBlocklistItem(blocklistItem: BlocklistItem)

    @Update
    suspend fun updateBlocklistItem(blocklistItem: BlocklistItem)

    @Upsert
    suspend fun upsertBlocklistItem(blocklistItem: BlocklistItem)

    @Upsert
    suspend fun upsertBlocklistItems(blocklistItems: List<BlocklistItem>)


}