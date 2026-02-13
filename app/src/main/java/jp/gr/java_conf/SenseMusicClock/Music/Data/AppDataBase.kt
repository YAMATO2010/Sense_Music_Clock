package jp.gr.java_conf.SenseMusicClock.Music.Data

//TODO RoomDatabaseの原型。作成しても大丈夫と確信した時のみにコメントアウト解除して使うこと。

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/*
@Database(entities = [PlayList::class, PlaylistItem::class, BlockList::class, BlocklistItem::class], version = 1 , exportSchema = false)
abstract class AppDataBase : RoomDatabase(){
    abstract fun playListDao(): PlaylistDao
    abstract fun playListItemDao(): PlaylistItemDao
    abstract fun blockListDao(): BlocklistDao
    abstract fun blockListItemDao(): BlocklistItemDao


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

 */