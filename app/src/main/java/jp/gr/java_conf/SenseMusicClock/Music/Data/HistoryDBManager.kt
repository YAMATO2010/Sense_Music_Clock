package jp.gr.java_conf.SenseMusicClock.Music.Data

import ads_mobile_sdk.re
import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Date

object HistoryDBManager {

    private val mutex = Mutex()


    private suspend fun _loadSearchHistory(context: Context, limit: Int): List<SearchHistory> {
        val db = AppDataBase.getInstance(context)
        val dao = db.searchHistoryDao()

        return dao.loadSearchHistory(limit)
    }

    private suspend fun _loadSearchHistory(context: Context): List<SearchHistory> {
        val db = AppDataBase.getInstance(context)
        val dao = db.searchHistoryDao()

        return dao.loadAllSearchHistory()

    }

    suspend fun loadSearchHistory(context: Context, limit: Int): List<SearchHistory> {
        return mutex.withLock {
            _loadSearchHistory(context, limit)
        }
    }

    suspend fun loadSearchHistory(context: Context): List<SearchHistory> {
        return mutex.withLock {
            _loadSearchHistory(context)
        }

    }

    private suspend fun _loadSearchHistoryInRange(
        context: Context,
        startIndex: Int,
        count: Int
    ): List<SearchHistory> {
        val db = AppDataBase.getInstance(context)
        val dao = db.searchHistoryDao()

        return dao.loadSearchHistoryInRange(startIndex, count)

    }

    suspend fun loadSearchHistoryInRange(
        context: Context,
        startIndex: Int,
        count: Int
    ): List<SearchHistory> {
        return mutex.withLock {
            _loadSearchHistoryInRange(context, startIndex, count)
        }
    }

    private suspend fun _insertSearchHistory(
        context: Context,
        history: SearchHistory
    ): SearchHistory {
        val db = AppDataBase.getInstance(context)
        val dao = db.searchHistoryDao()


        dao.insertSearchHistory(history)
        return history

    }

    suspend fun insertSearchHistory(context: Context, history: SearchHistory): SearchHistory {
        mutex.withLock {
            return _insertSearchHistory(context, history)
        }
    }

    suspend fun insertSearchHistory(
        context: Context,
        itemID: Long,
        itemType: String
    ): SearchHistory {
        val history = SearchHistory(itemID = itemID, itemType = itemType)
        return insertSearchHistory(context, history)
    }

    suspend fun insertSSongHistory(context: Context, songID: Long): SearchHistory {
        return insertSearchHistory(context, songID, SearchHistory.TYPE_SONG)
    }

    suspend fun insertSAlbumHistory(context: Context, albumID: Long): SearchHistory {
        return insertSearchHistory(context, albumID, SearchHistory.TYPE_ALBUM)
    }

    suspend fun insertSArtistHistory(context: Context, artistId: Long): SearchHistory {
        return insertSearchHistory(context, artistId, SearchHistory.TYPE_ARTIST)
    }


    private suspend fun _deleteSearchHistory(context: Context, history: SearchHistory) {
        val db = AppDataBase.getInstance(context)
        val dao = db.searchHistoryDao()


        dao.deleteSearchHistory(history)

    }

    suspend fun deleteSearchHistory(context: Context, history: SearchHistory) {
        mutex.withLock {
            _deleteSearchHistory(context, history)
        }
    }

    private suspend fun _deleteOldSearchHistory(context: Context, date: Date) {
        val db = AppDataBase.getInstance(context)
        val dao = db.searchHistoryDao()

        dao.deleteOldSearchHistory(date)

    }

    suspend fun deleteOldSearchHistory(context: Context, date: Date) {
        mutex.withLock {
            _deleteOldSearchHistory(context, date)
        }
    }


    private suspend fun _deleteSearchHistory(context: Context) {
        val db = AppDataBase.getInstance(context)
        val dao = db.searchHistoryDao()


        dao.deleteAllSearchHistory()

    }

    suspend fun deleteSearchHistory(context: Context) {
        mutex.withLock {
            _deleteSearchHistory(context)
        }
    }

    private suspend fun _deleteSearchHistoryByItem(
        context: Context,
        itemID: Long,
        itemType: String
    ): Int {
        val db = AppDataBase.getInstance(context)
        val dao = db.searchHistoryDao()
        return dao.deleteSearchHistoryByItem(itemID, itemType)
    }

    suspend fun deleteSearchHistoryByItem(context: Context, itemID: Long, itemType: String): Int {
        mutex.withLock {
            return _deleteSearchHistoryByItem(context, itemID, itemType)
        }
    }


}