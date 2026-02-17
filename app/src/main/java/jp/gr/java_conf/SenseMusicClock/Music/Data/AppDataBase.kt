package jp.gr.java_conf.SenseMusicClock.Music.Data

//TODO RoomDatabaseの原型。作成しても大丈夫と確信した時のみにコメントアウト解除して使うこと。

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters


@Database(
    entities = [PlayList::class, PlaylistItem::class, BlockList::class, BlocklistItem::class, SearchHistory::class, listeningHistory::class],
    version = 2,
    exportSchema = false
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

        //TODO  ここのfallbackToDestructiveMigration()を公開前に絶対治しとけよ！！！！！！！！！
        fun getInstance(context: Context): AppDataBase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDataBase::class.java,
                    "app_database"
                ).fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }


}

