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
import androidx.room.Update
import java.util.Date

@Entity(tableName = "blockLists")
data class BlockList(
    @PrimaryKey(autoGenerate = true) val blockListID  : Long,
    val blockListName : String,
    val deleted : Date? = null
)

@Entity(tableName = "blocklistItems",
    primaryKeys = ["blocklistId", "relativePath", "FileName"],
    foreignKeys = [
        ForeignKey(
            entity = BlockList::class,
            parentColumns = ["blocklistId"],
            childColumns = ["blocklistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["blocklistId"])])
data class BlocklistItem (
    val blocklistId        : Long ,
    @Embedded val fileItem: FileItem

    )



@Dao
interface BlocklistDao {
    // DAO methods would be defined here


    @Query("SELECT * FROM blockLists")
    suspend fun loadAllBlocklists(): List<BlockList>

    @Delete
    suspend fun deleteBlockList(blockList: BlockList)

    @Update
    suspend fun updateBlockList(blockList: BlockList)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockList(blockList: BlockList)
}
@Dao
interface BlocklistItemDao {
    // DAO methods would be defined here

    @Query("SELECT * FROM blocklistItems WHERE blocklistId = :blocklistId")
    suspend fun loadItemsForBlocklist(blocklistId: Long): List<BlocklistItem>

    @Delete
    suspend fun deleteBlocklistItem(blocklistItem: BlocklistItem)

    @Update
    suspend fun updateBlocklistItem(blocklistItem: BlocklistItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlocklistItem(blocklistItem: BlocklistItem)
}