package jp.gr.java_conf.SenseMusicClock.Music.Data.Blacklists

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import jp.gr.java_conf.SenseMusicClock.Music.Data.FileItem

@Entity(
    tableName = "blocklistItems",
    primaryKeys = ["blocklistId", "relativePath", "fileName"],
    foreignKeys = [
        ForeignKey(
            entity = BlockList::class,
            parentColumns = ["blockListID"],
            childColumns = ["blocklistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["blocklistId"])]
)
data class BlocklistItem(
    val blocklistId: Long,
    @Embedded val fileItem: FileItem

) {
    companion object {
        const val TOPLAYLISTID = -333L
    }
}