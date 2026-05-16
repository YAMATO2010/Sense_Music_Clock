package jp.gr.java_conf.SenseMusicClock.Music.Data

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
    private suspend fun _loadSearchHistoryInRange(context: Context, startIndex: Int, count: Int): List<SearchHistory> {
        val db = AppDataBase.getInstance(context)
        val dao = db.searchHistoryDao()

        return dao.loadSearchHistoryInRange(startIndex, count)

    }
    suspend fun loadSearchHistoryInRange(context: Context, startIndex: Int, count: Int): List<SearchHistory> {
        return mutex.withLock {
            _loadSearchHistoryInRange(context, startIndex, count)
        }
    }

    private suspend fun _insertSearchHistory(context: Context, history: SearchHistory) {
        val db = AppDataBase.getInstance(context)
        val dao = db.searchHistoryDao()


        dao.insertSearchHistory(history)

    }

    suspend fun insertSearchHistory(context: Context, history: SearchHistory) {
        mutex.withLock {
            _insertSearchHistory(context, history)
        }
    }

    suspend fun insertSearchHistory(context: Context, itemID: Long, itemType: String) {
        val history = SearchHistory(itemID = itemID, itemType = itemType)
        insertSearchHistory(context, history)
    }

    suspend fun insertSSongHistory(context: Context, songID: Long) {
        insertSearchHistory(context, songID, SearchHistory.TYPE_SONG)
    }
    suspend fun insertSAlbumHistory(context: Context, albumID: Long) {
        insertSearchHistory(context, albumID, SearchHistory.TYPE_ALBUM)
    }
    suspend fun insertSArtistHistory(context: Context, artistId: Long) {
        insertSearchHistory(context, artistId, SearchHistory.TYPE_ARTIST)
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


    private suspend fun _clearSearchHistory(context: Context) {
        val db = AppDataBase.getInstance(context)
        val dao = db.searchHistoryDao()


        dao.deleteAllSearchHistory()

    }

    suspend fun clearSearchHistory(context: Context) {
        mutex.withLock {
            _clearSearchHistory(context)
        }
    }


}