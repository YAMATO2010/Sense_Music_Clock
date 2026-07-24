package jp.gr.java_conf.SenseMusicClock.Music.Data


import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import jp.gr.java_conf.SenseMusicClock.Music.Data.Blacklists.BlockList
import jp.gr.java_conf.SenseMusicClock.Music.Data.Blacklists.BlocklistDao
import jp.gr.java_conf.SenseMusicClock.Music.Data.Blacklists.BlocklistItem
import jp.gr.java_conf.SenseMusicClock.Music.Data.Blacklists.BlocklistItemDao
import jp.gr.java_conf.SenseMusicClock.Music.Data.Playlists.PlayList
import jp.gr.java_conf.SenseMusicClock.Music.Data.Playlists.PlaylistDao
import jp.gr.java_conf.SenseMusicClock.Music.Data.Playlists.PlaylistItem
import jp.gr.java_conf.SenseMusicClock.Music.Data.Playlists.PlaylistItemDao
import jp.gr.java_conf.SenseMusicClock.Music.Data.SearchHistorys.SearchHistory
import jp.gr.java_conf.SenseMusicClock.Music.Data.SearchHistorys.SearchHistoryDao


@Database(
    entities = [PlayList::class, PlaylistItem::class, BlockList::class, BlocklistItem::class, SearchHistory::class, listeningHistory::class],
    version = 6,
    exportSchema = true
)
@TypeConverters(DateConverters::class)
abstract class AppDataBase : RoomDatabase() {
    abstract fun playListDao(): PlaylistDao
    abstract fun playListItemDao(): PlaylistItemDao
    abstract fun blockListDao(): BlocklistDao
    abstract fun blockListItemDao(): BlocklistItemDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun listeningHistoryDao(): listeningHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDataBase? = null


        fun getInstance(context: Context): AppDataBase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDataBase::class.java,
                    "app_database"
                ).build()

                INSTANCE = instance
                instance
            }
        }
    }


}

