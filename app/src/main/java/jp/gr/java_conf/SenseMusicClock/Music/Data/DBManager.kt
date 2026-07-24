package jp.gr.java_conf.SenseMusicClock.Music.Data

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.room.withTransaction
import jp.gr.java_conf.SenseMusicClock.toBlocklistItem
import jp.gr.java_conf.SenseMusicClock.ui.list.ListsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object DBManager {

    private val mutex = Mutex()

    // Interface that exposes no-lock core operations to be used inside withDbLock
    interface NoLockOps {
        suspend fun loadPlaylistNoLock(context: Context): List<PlayList>
        suspend fun loadPlaylistByIdNoLock(context: Context, id: Long): PlayList?
        suspend fun deletePlaylistNoLock(context: Context, playlistId: Long)
        suspend fun loadPlaylistItemNoLock(context: Context, playlistId: Long): List<PlaylistItem>
        suspend fun loadBlocklistNoLock(context: Context): List<BlockList>
        suspend fun loadBlocklistByIDNoLock(context: Context, id: Long): BlockList?
        suspend fun deleteBlocklistNoLock(context: Context, blocklistId: Long)
        suspend fun loadBlocklistItemNoLock(
            context: Context,
            blocklistId: Long
        ): List<BlocklistItem>

        suspend fun upsertPlaylistNoLock(context: Context, value: PlayList): Long
        suspend fun upsertPlaylistItemNoLock(context: Context, value: PlaylistItem)
        suspend fun upsertBlocklistNoLock(context: Context, value: BlockList): Long
        suspend fun upsertBlocklistItemNoLock(context: Context, value: BlocklistItem): Boolean
        suspend fun upsertBlockListItemsNoLock(
            context: Context,
            BlocklistId: Long,
            fileItem: List<FileItem>,
        ): Boolean

        suspend fun replacePlaylistContentNoLock(
            context: Context,
            newItems: List<PlaylistItem>
        ): List<PlaylistItem>

        suspend fun replaceBlocklistContentNoLock(
            context: Context,
            newItems: List<BlocklistItem>
        ): List<BlocklistItem>

        suspend fun addPlaylistItemSingleNoLock(
            context: Context,
            playlistId: Long,
            fileItem: FileItem,
            index: Int? = null
        ): Boolean

        suspend fun addPlaylistItemMultipleNoLock(
            context: Context,
            playlistId: Long,
            fileItem: List<FileItem>,
            index: Int? = null
        ): Boolean

        suspend fun copyPlaylistNoLock(context: Context, playlistId: Long)
        suspend fun copyListContentNoLock(
            context: Context,
            fromListInfo: ListInfo,
            toListInfo: ListInfo
        ): Boolean
    }

    private val noLockOps = object : NoLockOps {
        override suspend fun loadPlaylistNoLock(context: Context) = _loadPlaylist(context)
        override suspend fun loadPlaylistByIdNoLock(context: Context, id: Long) =
            _loadPlaylistById(context, id)

        override suspend fun deletePlaylistNoLock(context: Context, playlistId: Long) =
            _deletePlaylist(context, playlistId)

        override suspend fun loadPlaylistItemNoLock(context: Context, playlistId: Long) =
            _loadPlaylistItem(context, playlistId)

        override suspend fun loadBlocklistNoLock(context: Context) = _loadBlocklist(context)
        override suspend fun loadBlocklistByIDNoLock(context: Context, id: Long) =
            _loadBlocklistByID(context, id)

        override suspend fun deleteBlocklistNoLock(context: Context, blocklistId: Long) =
            _deleteBlocklist(context, blocklistId)

        override suspend fun loadBlocklistItemNoLock(context: Context, blocklistId: Long) =
            _loadBlocklistItem(context, blocklistId)

        override suspend fun upsertPlaylistNoLock(context: Context, value: PlayList) =
            _upsertPlaylist(context, value)

        override suspend fun upsertPlaylistItemNoLock(context: Context, value: PlaylistItem) =
            _upsertPlaylistItem(context, value)

        override suspend fun upsertBlocklistNoLock(context: Context, value: BlockList) =
            _upsertBlocklist(context, value)

        override suspend fun upsertBlocklistItemNoLock(
            context: Context,
            value: BlocklistItem
        ): Boolean =
            _upsertBlocklistItem(context, value)

        override suspend fun upsertBlockListItemsNoLock(
            context: Context,
            BlocklistId: Long,
            fileItem: List<FileItem>,
        ) = _upsertBlockListItems(context, BlocklistId, fileItem)

        override suspend fun replacePlaylistContentNoLock(
            context: Context,
            newItems: List<PlaylistItem>
        ) = _replacePlaylistContent(context, newItems)

        override suspend fun replaceBlocklistContentNoLock(
            context: Context,
            newItems: List<BlocklistItem>
        ) = _replaceBlocklistContent(context, newItems)

        override suspend fun addPlaylistItemSingleNoLock(
            context: Context,
            playlistId: Long,
            fileItem: FileItem,
            index: Int?
        ) = _addPlaylistItemSingle(context, playlistId, fileItem, index)

        override suspend fun addPlaylistItemMultipleNoLock(
            context: Context,
            playlistId: Long,
            fileItem: List<FileItem>,
            index: Int?
        ) = _addPlaylistItemMultiple(context, playlistId, fileItem, index)

        override suspend fun copyPlaylistNoLock(context: Context, playlistId: Long) =
            _copyPlaylist(context, playlistId)

        override suspend fun copyListContentNoLock(
            context: Context,
            fromListInfo: ListInfo,
            toListInfo: ListInfo
        ): Boolean {
            return _copylistContent(context, fromListInfo, toListInfo)
        }
    }

    // Helper to run multiple DB operations under the same lock from caller side
    suspend fun <T> withDbLock(block: suspend (NoLockOps) -> T): T {
        return mutex.withLock { block(noLockOps) }
    }

    // Core (no-lock) implementations - do actual DB I/O on Dispatchers.IO
    private suspend fun _loadPlaylist(context: Context): List<PlayList> {
        val db = AppDataBase.getInstance(context)
        val playlistDao = db.playListDao()
        return withContext(Dispatchers.IO) {
            playlistDao.loadAllPlaylists()
        }
    }

    suspend fun loadPlaylist(context: Context): List<PlayList> {
        Log.d("LIST_/DBManager/loadPlaylist", "called loadPlaylist")
        return mutex.withLock { _loadPlaylist(context) }
    }

    private suspend fun _loadPlaylistById(context: Context, id: Long): PlayList? {
        val db = AppDataBase.getInstance(context)
        val playlistDao = db.playListDao()
        return withContext(Dispatchers.IO) {
            playlistDao.loadPlaylistById(id)
        }
    }

    suspend fun loadPlaylist(context: Context, id: Long): PlayList? {
        Log.d("LIST_/DBManager/loadPlaylistById", "called loadPlaylist id=$id")
        return mutex.withLock { _loadPlaylistById(context, id) }
    }

    private suspend fun _deletePlaylist(context: Context, playlistId: Long) {
        val db = AppDataBase.getInstance(context)
        val playlistDao = db.playListDao()
        withContext(Dispatchers.IO) {
            playlistDao.deletePlaylist(playlistId)
        }
    }

    suspend fun deletePlaylist(context: Context, playlistId: Long) {
        Log.d("LIST_/DBManager/deletePlaylist", "delete playlistId=$playlistId")
        return mutex.withLock { _deletePlaylist(context, playlistId) }
    }

    private suspend fun _loadPlaylistItem(context: Context, playlistId: Long): List<PlaylistItem> {
        val db = AppDataBase.getInstance(context)
        val playlistItemDao = db.playListItemDao()
        return withContext(Dispatchers.IO) {
            playlistItemDao.loadItemsForPlaylist(playlistId).sortedBy { it.index }
        }
    }

    suspend fun loadPlaylistItem(context: Context, playlistId: Long): List<PlaylistItem> {
        Log.d("LIST_/DBManager/loadPlaylistItem", "load items for playlistId=$playlistId")
        return mutex.withLock { _loadPlaylistItem(context, playlistId).sortedBy { it.index } }
    }

    private suspend fun _loadBlocklist(context: Context): List<BlockList> {
        val db = AppDataBase.getInstance(context)
        val blocklistDao = db.blockListDao()
        return withContext(Dispatchers.IO) {
            blocklistDao.loadAllBlocklists()
        }
    }

    suspend fun loadBlocklist(context: Context): List<BlockList> {
        Log.d("LIST_/DBManager/loadBlocklist", "called loadBlocklist")
        return mutex.withLock { _loadBlocklist(context) }
    }

    private suspend fun _loadBlocklistByID(context: Context, id: Long): BlockList? {
        val db = AppDataBase.getInstance(context)
        val blocklistDao = db.blockListDao()
        return withContext(Dispatchers.IO) {
            blocklistDao.loadBlocklistById(id)
        }
    }

    suspend fun loadBlocklistByID(context: Context, id: Long): BlockList? {
        Log.d("LIST_/DBManager/loadBlocklistByID", "load blocklist id=$id")
        return mutex.withLock { _loadBlocklistByID(context, id) }
    }

    private suspend fun _deleteBlocklist(context: Context, blocklistId: Long) {
        val db = AppDataBase.getInstance(context)
        val blocklistDao = db.blockListDao()
        withContext(Dispatchers.IO) {
            blocklistDao.deleteBlocklist(blocklistId)
        }
    }

    suspend fun deleteBlocklist(context: Context, blocklistId: Long) {
        Log.d("LIST_/DBManager/deleteBlocklist", "delete blocklistId=$blocklistId")
        return mutex.withLock { _deleteBlocklist(context, blocklistId) }
    }

    private suspend fun _loadBlocklistItem(
        context: Context,
        blocklistId: Long
    ): List<BlocklistItem> {
        val db = AppDataBase.getInstance(context)
        val blocklistItemDao = db.blockListItemDao()
        return withContext(Dispatchers.IO) {
            blocklistItemDao.loadItemsForBlocklist(blocklistId)
        }
    }

    suspend fun loadBlocklistItem(context: Context, blocklistId: Long): List<BlocklistItem> {
        Log.d("LIST_/DBManager/loadBlocklistItem", "load items for blocklistId=$blocklistId")
        return mutex.withLock { _loadBlocklistItem(context, blocklistId) }
    }

    private suspend fun _upsertPlaylist(context: Context, value: PlayList): Long {
        val db = AppDataBase.getInstance(context)
        val playlistDao = db.playListDao()
        return withContext(Dispatchers.IO) {
            playlistDao.upsertPlaylist(value)
        }
    }

    suspend fun upsertPlaylist(context: Context, value: PlayList): Long {
        Log.d(
            "LIST_/DBManager/upsertPlaylist",
            "upsert playlist: id=${value.playlistId}, name=${value.playlistName}"
        )
        return mutex.withLock { _upsertPlaylist(context, value) }
    }

    private suspend fun _upsertPlaylistItem(context: Context, value: PlaylistItem) {
        val db = AppDataBase.getInstance(context)
        val playlistItemDao = db.playListItemDao()
        withContext(Dispatchers.IO) {
            playlistItemDao.upsertPlaylistItem(value)
        }
    }

    suspend fun upsertPlaylistItem(context: Context, value: PlaylistItem) {
        Log.d(
            "LIST_/DBManager/upsertPlaylistItem",
            "upsert playlistItem: playlistId=${value.playlistId}, index=${value.index}"
        )
        return mutex.withLock { _upsertPlaylistItem(context, value) }
    }

    private suspend fun _upsertBlocklist(context: Context, value: BlockList): Long {
        val db = AppDataBase.getInstance(context)
        val blocklistDao = db.blockListDao()
        return withContext(Dispatchers.IO) {
            blocklistDao.upsertBlockList(value)
        }
    }

    suspend fun upsertBlocklist(context: Context, value: BlockList): Long {
        Log.d(
            "LIST_/DBManager/upsertBlocklist",
            "upsert blocklist: id=${value.blockListID}, name=${value.blockListName}"
        )
        return mutex.withLock { _upsertBlocklist(context, value) }
    }

    private suspend fun _upsertBlocklistItem(context: Context, value: BlocklistItem): Boolean {
        val db = AppDataBase.getInstance(context)
        val blocklistItemDao = db.blockListItemDao()
        val isSuccess: Boolean = try {

            withContext(Dispatchers.IO) {
                blocklistItemDao.upsertBlocklistItem(value)
            }
            true

        } catch (e: Exception) {
            false
        }
        return isSuccess
    }

    suspend fun upsertBlocklistItem(context: Context, value: BlocklistItem) {
        Log.d(
            "LIST_/DBManager/upsertBlocklistItem",
            "upsert blocklistItem: blocklistId=${value.blocklistId}"
        )
        return mutex.withLock { _upsertBlocklistItem(context, value) }
    }

    private suspend fun _replacePlaylistContent(
        context: Context,
        newItems: List<PlaylistItem>
    ): List<PlaylistItem> {
        val db = AppDataBase.getInstance(context)
        if (newItems.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            db.withTransaction {
                val playlistItemDao = db.playListItemDao()
                val playlistId = newItems.first().playlistId
                val inserted: List<PlaylistItem> = playlistItemDao.run {
                    deleteItemsForPlaylist(playlistId)
                    upsertPlaylistItems(newItems)
                    return@run loadItemsForPlaylist(playlistId)
                }
                return@withTransaction inserted
            }
        }
    }

    suspend fun replacePlaylistContent(
        context: Context,
        newItems: List<PlaylistItem>
    ): List<PlaylistItem> {
        Log.d(
            "LIST_/DBManager/replacePlaylistContent",
            "replace playlist content size=${newItems.size} firstPlaylistId=${newItems.firstOrNull()?.playlistId}"
        )
        return mutex.withLock { _replacePlaylistContent(context, newItems) }
    }

    private suspend fun _replaceBlocklistContent(
        context: Context,
        newItems: List<BlocklistItem>
    ): List<BlocklistItem> {
        val db = AppDataBase.getInstance(context)
        if (newItems.isEmpty()) {
            Log.d(
                "LIST_/DBManager/replaceBlocklistContent",
                "replace blocklist content with empty list"
            )
        }
        return withContext(Dispatchers.IO) {
            db.withTransaction {
                val blocklistItemDao = db.blockListItemDao()
                val blocklistId = newItems.first().blocklistId
                val inserted: List<BlocklistItem> = blocklistItemDao.run {
                    deleteItemsForBlocklist(blocklistId)
                    upsertBlocklistItems(newItems)
                    return@run loadItemsForBlocklist(blocklistId)
                }

                return@withTransaction inserted
            }
        }
    }

    suspend fun replaceBlocklistContent(
        context: Context,
        newItems: List<BlocklistItem>
    ): List<BlocklistItem> {
        Log.d(
            "LIST_/DBManager/replaceBlocklistContent",
            "replace blocklist content size=${newItems.size} firstBlocklistId=${newItems.firstOrNull()?.blocklistId}"
        )
        return mutex.withLock { _replaceBlocklistContent(context, newItems) }
    }

    // addPlaylistItem single
    private suspend fun _addPlaylistItemSingle(
        context: Context,
        playlistId: Long,
        fileItem: FileItem,
        index: Int? = null
    ): Boolean {
        val db = AppDataBase.getInstance(context)
        return withContext(Dispatchers.IO) {
            db.withTransaction {
                val items = _loadPlaylistItem(context, playlistId).toMutableList()
                val newItem = PlaylistItem(
                    playlistId = playlistId,
                    fileItem = fileItem,
                    index = index ?: items.size
                )
                items.add(newItem)
                _replacePlaylistContent(context, items)
                return@withTransaction true
            }
        }
    }

    suspend fun addPlaylistItem(
        context: Context,
        playlistId: Long,
        fileItem: FileItem,
        index: Int? = null,
        DoToast: Boolean = true
    ) {
        Log.d(
            "LIST_/DBManager/addPlaylistItemSingle",
            "add single to playlistId=$playlistId file=${fileItem.fileName} index=${index}"
        )
        val isSuccess =
            mutex.withLock { _addPlaylistItemSingle(context, playlistId, fileItem, index) }
        withContext(Dispatchers.Main) {
            if (isSuccess && DoToast) {
                Toast.makeText(context, "プレイリストに追加しました ", Toast.LENGTH_SHORT).show()
            } else if (DoToast) {
                Toast.makeText(context, "プレイリストへの追加に失敗しました ", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    // addPlaylistItem multiple
    private suspend fun _addPlaylistItemMultiple(
        context: Context,
        playlistId: Long,
        fileItem: List<FileItem>,
        index: Int? = null
    ): Boolean {
        val db = AppDataBase.getInstance(context)
        return withContext(Dispatchers.IO) {

            db.withTransaction {
                val oldItems: List<FileItem> =
                    _loadPlaylistItem(context, playlistId).sortedBy { it.index }.map { it.fileItem }
                val newItems = oldItems + fileItem
                val newPlaylistItems = newItems.mapIndexed { index, item ->
                    PlaylistItem(
                        playlistId = playlistId,
                        fileItem = item,
                        index = index
                    )
                }

                _replacePlaylistContent(context, newPlaylistItems)





                return@withTransaction true
            }
        }
    }


    suspend fun addPlaylistItems(
        context: Context,
        playlistId: Long,
        fileItem: List<FileItem>,
        index: Int? = null,
        DoToast: Boolean = true
    ) {
        Log.d(
            "LIST_/DBManager/addPlaylistItemMultiple",
            "add multiple to playlistId=$playlistId count=${fileItem.size} index=${index}"
        )
        val isSuccess =
            mutex.withLock { _addPlaylistItemMultiple(context, playlistId, fileItem, index) }
        withContext(Dispatchers.Main) {
            if (isSuccess && DoToast) {
                Toast.makeText(context, "プレイリストに追加しました ", Toast.LENGTH_SHORT).show()
            } else if (DoToast) {
                Toast.makeText(context, "プレイリストへの追加に失敗しました ", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    suspend fun addPlaylistAndItem(context: Context, playlistName: String, fileItem: FileItem) {
        Log.d(
            "LIST_/DBManager/addPlaylistAndItem",
            "add playlist and item: playlistName=$playlistName file=${fileItem.fileName}"
        )
        val isSuccess = mutex.withLock {
            val playlistId = _upsertPlaylist(context, PlayList(playlistName = playlistName))
            Log.d(
                "LIST_/DBManager/addPlaylistAndItem",
                "upsert playlist result: playlistId=$playlistId"
            )

            return@withLock _addPlaylistItemSingle(context, playlistId, fileItem)
        }
        withContext(Dispatchers.Main) {
            if (isSuccess) {
                Toast.makeText(
                    context,
                    "${playlistName}を作成し、${fileItem.fileName}を追加しました ",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    context,
                    "プレイリストの作成とアイテムの追加に失敗しました ",
                    Toast.LENGTH_SHORT
                )
                    .show()
            }
        }


    }

    suspend fun addPlaylistAndItems(
        context: Context,
        playlistName: String,
        fileItems: List<FileItem>
    ) {
        Log.d(
            "LIST_/DBManager/addPlaylistAndItems",
            "add playlist and item: playlistName=$playlistName "
        )
        val isSuccess = mutex.withLock {
            val playlistId = _upsertPlaylist(context, PlayList(playlistName = playlistName))
            Log.d(
                "LIST_/DBManager/addPlaylistAndItems",
                "upsert playlist result: playlistId=$playlistId"
            )

            return@withLock _addPlaylistItemMultiple(context, playlistId, fileItems)
        }
        withContext(Dispatchers.Main) {
            if (isSuccess) {
                Toast.makeText(
                    context,
                    "${playlistName}を作成し、曲を追加しました ",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    context,
                    "プレイリストの作成とアイテムの追加に失敗しました ",
                    Toast.LENGTH_SHORT
                )
                    .show()
            }
        }


    }

    suspend fun addBlocklistAndItem(context: Context, blocklistName: String, fileItem: FileItem) {
        Log.d(
            "LIST_/DBManager/addBlocklistAndItem",
            "add playlist and item: playlistName=$blocklistName file=${fileItem.fileName}"
        )
        val isSuccess = mutex.withLock {
            val blocklistId = _upsertBlocklist(context, BlockList(blockListName = blocklistName))
            Log.d(
                "LIST_/DBManager/addBlocklistAndItem",
                "upsert blocklist result: blocklistId=$blocklistId"
            )
            val newItem = fileItem.toBlocklistItem(blocklistId)
            return@withLock _upsertBlocklistItem(context, newItem)
        }
        withContext(Dispatchers.Main) {
            if (isSuccess) {
                Toast.makeText(
                    context,
                    "ブロックリスト${blocklistName}を作成し、${fileItem.fileName}を追加しました ",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    context,
                    "ブロックリストの作成とアイテムの追加に失敗しました ",
                    Toast.LENGTH_SHORT
                )
                    .show()
            }
        }


    }

    private suspend fun _upsertBlockListItems(
        context: Context,
        BlocklistId: Long,
        fileItem: List<FileItem>,
    ): Boolean {
        val db = AppDataBase.getInstance(context)
        return withContext(Dispatchers.IO) {
            db.withTransaction {
                val blocklistItemDao = db.blockListItemDao()

                val items = fileItem.map { BlocklistItem(blocklistId = BlocklistId, fileItem = it) }
                blocklistItemDao.upsertBlocklistItems(items)
                return@withTransaction true
            }
        }
    }

    suspend fun upsertBlockListItems(
        context: Context,
        BlocklistId: Long,
        fileItem: List<FileItem>,
        DoToast: Boolean = true
    ) {
        Log.d(
            "LIST_/DBManager/upsertBlockListItems",
            "upsert blocklist items: blocklistId=$BlocklistId count=${fileItem.size}"
        )
        val isSuccess =
            mutex.withLock { _upsertBlockListItems(context, BlocklistId, fileItem) }
        withContext(Dispatchers.Main) {
            if (isSuccess && DoToast) {
                Toast.makeText(context, "ブロックリストに追加しました ", Toast.LENGTH_SHORT).show()
            } else if (DoToast) {
                Toast.makeText(context, "ブロックリストへの追加に失敗しました ", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }


    private suspend fun _copyPlaylist(context: Context, playlistId: Long) {
        val db = AppDataBase.getInstance(context)
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val playlistDao = db.playListDao()
                val playlistItemDao = db.playListItemDao()

                val existingPlaylists = playlistDao.loadPlaylistById(playlistId)

                var copyNumber = 0
                while (playlistDao.existsPlaylist("${existingPlaylists?.playlistName ?: "Unknown Playlist"} - Copy $copyNumber")) {
                    copyNumber++
                }

                val newName =
                    "${existingPlaylists?.playlistName ?: "Unknown Playlist"} - Copy $copyNumber"
                val originalPlaylist =
                    playlistDao.loadPlaylistById(playlistId) ?: return@withTransaction
                val newPlaylistId = playlistDao.upsertPlaylist(
                    originalPlaylist.copy(playlistId = 0L, playlistName = newName)
                )

                val originalItems = playlistItemDao.loadItemsForPlaylist(playlistId)
                val newItems = originalItems.map { it.copy(playlistId = newPlaylistId) }
                playlistItemDao.upsertPlaylistItems(newItems)
            }
        }
    }

    suspend fun copyPlaylist(context: Context, playlistId: Long) {
        Log.d("LIST_/DBManager/copyPlaylist", "copy playlistId=$playlistId")
        return mutex.withLock { _copyPlaylist(context, playlistId) }
    }

    private suspend fun _copylistContent(
        context: Context,
        fromListInfo: ListInfo, toListInfo: ListInfo
    ): Boolean {
        val db = AppDataBase.getInstance(context)
        return withContext(Dispatchers.IO) {
            db.withTransaction {
                val fromItems: List<FileItem> = when (fromListInfo.type) {
                    ListsActivity.ListType.PLAYLIST -> {
                        _loadPlaylistItem(context, fromListInfo.id).sortedBy { it.index }
                            .map { it.fileItem }
                    }

                    ListsActivity.ListType.BLOCKLIST -> {
                        _loadBlocklistItem(context, fromListInfo.id).map { it.fileItem }
                    }


                }

                when (toListInfo.type) {
                    ListsActivity.ListType.PLAYLIST -> {

                        _addPlaylistItemMultiple(context, toListInfo.id, fromItems, index = null)

                    }

                    ListsActivity.ListType.BLOCKLIST -> {

                        _upsertBlockListItems(context, toListInfo.id, fromItems)


                    }

                }
                return@withTransaction true
            }
        }
    }

    suspend fun copylistContent(context: Context, fromListInfo: ListInfo, toListInfo: ListInfo) {
        Log.d(
            "LIST_/DBManager/copylistContent",
            "copy from=${fromListInfo.name}(${fromListInfo.type}) to=${toListInfo.name}(${toListInfo.type})"
        )
        val isSuccess =
            mutex.withLock { _copylistContent(context, fromListInfo, toListInfo) }
        withContext(Dispatchers.Main) {
            if (isSuccess) {
                Toast.makeText(
                    context,
                    "${fromListInfo.name}を${toListInfo.name}にコピーしました ",
                    Toast.LENGTH_SHORT
                )
                    .show()
            } else {
                Toast.makeText(
                    context,
                    "リストの内容のコピーに失敗しました ",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private suspend fun _copyBlocklist(context: Context, blockListId: Long) {
        val db = AppDataBase.getInstance(context)
        val isSuccess: Boolean = withContext(Dispatchers.IO) {

            val isSuccess: Boolean = db.withTransaction {
                val blocklistDao = db.blockListDao()


                val originalBlocklist =
                    _loadBlocklistByID(context, blockListId) ?: return@withTransaction false

                var copyNumber = 0
                while (blocklistDao.existsBlocklist("${originalBlocklist.blockListName} - Copy $copyNumber")) {
                    copyNumber++
                }

                val newName = "${originalBlocklist.blockListName} - Copy $copyNumber"

                val newBlocklistId = _upsertBlocklist(
                    context,
                    originalBlocklist.copy(blockListID = 0L, blockListName = newName)
                )

                val originalItems: List<BlocklistItem> = _loadBlocklistItem(context, blockListId)
                val newItems = originalItems.map { it.copy(blocklistId = newBlocklistId) }
                _replaceBlocklistContent(context, newItems)
                return@withTransaction true
            }
            return@withContext isSuccess
        }
        if (isSuccess) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "ブロックリストをコピーしました ", Toast.LENGTH_SHORT)
                    .show()
            }
        } else {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "ブロックリストのコピーに失敗しました ",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    }

    suspend fun copyBlocklist(context: Context, blockListId: Long) {
        Log.d("LIST_/DBManager/copyBlocklist", "copy blocklistId=$blockListId")
        return mutex.withLock { _copyBlocklist(context, blockListId) }
    }


    data class ListInfo(
        val id: Long,
        val name: String,
        val type: ListsActivity.ListType

    )


}