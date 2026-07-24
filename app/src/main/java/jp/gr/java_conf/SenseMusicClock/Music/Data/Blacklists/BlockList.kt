package jp.gr.java_conf.SenseMusicClock.Music.Data.Blacklists

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "blockLists")
data class BlockList(
    @PrimaryKey(autoGenerate = true) val blockListID: Long = 0,
    val blockListName: String,
    val deleted: Date? = null
)
