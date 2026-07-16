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

@Entity(tableName = "blockLists")
data class BlockList(
    @PrimaryKey(autoGenerate = true) val blockListID  : Long = 0,
    val blockListName : String,
    val deleted : Date? = null
)

@Entity(tableName = "blocklistItems",
    primaryKeys = ["blocklistId", "relativePath", "fileName"],
    foreignKeys = [
        ForeignKey(
            entity = BlockList::class,
            parentColumns = ["blockListID"],
            childColumns = ["blocklistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["blocklistId"])])
data class BlocklistItem (
    val blocklistId        : Long ,
    @Embedded val fileItem: FileItem

    ){
    companion object{
        const val TOPLAYLISTID = -333L
    }
}



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