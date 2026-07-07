package jp.gr.java_conf.SenseMusicClock


import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.recyclerview.widget.RecyclerView
import jp.gr.java_conf.SenseMusicClock.Music.Data.AppDataBase
import jp.gr.java_conf.SenseMusicClock.Music.Data.BlockList
import jp.gr.java_conf.SenseMusicClock.Music.Data.BlocklistItem
import jp.gr.java_conf.SenseMusicClock.Music.Data.DBManager
import jp.gr.java_conf.SenseMusicClock.Music.Data.FileItem
import jp.gr.java_conf.SenseMusicClock.Music.Data.PlayList
import jp.gr.java_conf.SenseMusicClock.Music.Data.PlaylistItem
import jp.gr.java_conf.SenseMusicClock.ui.list.ListEditAdapter
import jp.gr.java_conf.SenseMusicClock.ui.list.ListsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


val app_dir = ContentValues().apply {
    put(MediaStore.Audio.Media.DISPLAY_NAME, ".nomedia") // フォルダ名
    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/SMC")// 保存先の相対パス
    put(MediaStore.Audio.Media.IS_PENDING, 0)
    put(MediaStore.Audio.Media.MIME_TYPE, "Audio/mpeg") // ファイルタイプ
}




const val IDENTIFIER_INITIAL_INDEX_PROBLEM = "  ///IDENTIFIER_INITIAL_INDEX_PROBLEM"
const val MAX_SCROLL_DISTANCE_FOR_ANIMATION = 30


val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = PrefsManager.PREFS_NAME,
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, PrefsManager.PREFS_NAME))
    })


fun keysForTrackTopLevel(t: MediaItem): List<String> {
    val keys = mutableListOf<String>()
    try {


        keys.add("L:${t.mediaId}")
        val tMetadata = t.mediaMetadata

        keys.add("M:${tMetadata.title}|${tMetadata.artist}|${tMetadata.albumTitle}")
    } catch (e: Exception) {
        Log.w("MusicSearcher", "keysForTrackTopLevel failed", e)
    }
    return keys
}

/**
 * Build key->original-index map from a list of IndexedValue<Track>.
 */
fun buildPositionMapFromIndexedTopLevel(indexed: List<IndexedValue<MediaItem>>): Map<String, Int> {
    val m = mutableMapOf<String, Int>()
    for (iv in indexed) {
        val idx = iv.index
        val t = iv.value
        for (k in keysForTrackTopLevel(t)) {
            if (!m.containsKey(k)) m[k] = idx
        }
    }
    return m
}

fun List<PlaylistItem>.moveDuplicatesToBack(): List<PlaylistItem> {
    val seen = mutableSetOf<Int>()
    val uniqueItems = mutableListOf<PlaylistItem>()
    val duplicateItems = mutableListOf<PlaylistItem>()



    for (item in this) {
        val key = item.index
        if (seen.contains(key)) {
            val newIndex = seen.toList().max() + 1
            val newItem = item.copy(index = newIndex)
            Log.d("moveDuplicatesToBack", "Duplicate found: $item, moving to index $newIndex")
            seen.add(newIndex)
            duplicateItems.add(newItem)

        } else {
            seen.add(key)
            uniqueItems.add(item)
        }
    }
    return uniqueItems + duplicateItems

}


fun List<MediaItem>.sortedByPlaylistItems(playlistItemList: List<PlaylistItem>): List<MediaItem> {

    val playlistItems = playlistItemList.moveDuplicatesToBack().sortedBy { it.index }
    val Items = this.toMutableList()
    val sortedList = mutableListOf<MediaItem>()
    for (path in playlistItems) {
        val fileItem = path.fileItem
        val mediaItem =
            Items.find { it.isSamePath(fileItem) }
        if (mediaItem != null) {
            sortedList.add(mediaItem)
            Items.remove(mediaItem)
        } else {
            Log.w("sortedByPlaylistItems", "MediaItem not found for path: $path")
        }

    }
    return sortedList

}

fun AppCompatActivity.launchSelectBackgroundImageDialog(
    originFileList: List<File> = getAllFile_inInternalStorage(
        BackgroundResolver.BACKGROUNDS_PATH
    )
) {


    val displayFileList = listOf<File>(File("")) + originFileList
    val inflater = layoutInflater
    val customView = inflater.inflate(R.layout.background_select_dialog, null)


    val backgroundsImageFileView = customView.findViewById<RecyclerView>(R.id.backgroundFiles)
    val PartOfDay_radioGroup = customView.findViewById<RadioGroup>(R.id.PartOfDay_radioButtongroup)
    val orientation_radioGroup =
        customView.findViewById<RadioGroup>(R.id.orientation_radioButtongroup)
    val adapter =
        BackgroundsImageFileAdapter(displayFileList, context = this, onItemClick = { file ->

            val selectedRadioBtnId_partOfDay = PartOfDay_radioGroup.checkedRadioButtonId
            val selectedPartOfDay = when (selectedRadioBtnId_partOfDay) {
                R.id.radioButtonMorning -> BackgroundResolver.NOW_MORNING
                R.id.radioButtonNoon -> BackgroundResolver.NOW_NOON
                R.id.radioButtonEvening -> BackgroundResolver.NOW_EVENING
                R.id.radioButtonNight -> BackgroundResolver.NOW_NIGHT
                else -> {
                    Toast.makeText(this, "時間帯を選択してください", Toast.LENGTH_SHORT).show()
                    return@BackgroundsImageFileAdapter
                }
            }
            val selectedRadioBtnId_orientation = orientation_radioGroup.checkedRadioButtonId
            val selectedOrientation = when (selectedRadioBtnId_orientation) {
                R.id.radioButtonOblong -> BackgroundResolver.ORIENTATION_OBLONG
                R.id.radioButtonLand -> BackgroundResolver.ORIENTATION_LAND
                else -> {
                    Toast.makeText(this, "画像の向きを選択してください", Toast.LENGTH_SHORT).show()
                    return@BackgroundsImageFileAdapter
                }
            }
            val partOfday_str = when (selectedPartOfDay) {
                BackgroundResolver.NOW_MORNING -> "朝"
                BackgroundResolver.NOW_NOON -> "昼"
                BackgroundResolver.NOW_EVENING -> "夕"
                BackgroundResolver.NOW_NIGHT -> "夜"
                else -> ""
            }
            val orientation_str = when (selectedOrientation) {
                BackgroundResolver.ORIENTATION_OBLONG -> "縦"
                BackgroundResolver.ORIENTATION_LAND -> "横"
                else -> ""
            }

            PrefsManager.getImageFileKey(selectedOrientation, selectedPartOfDay).let {
                val path = file.name
                this.lifecycleScope.launch {
                    PrefsManager.setImageFilePath(this@launchSelectBackgroundImageDialog, it, path)
                }
                if (path == "") {
                    Toast.makeText(
                        this,
                        "デフォルト画像を背景画像に設定　\n時間帯：${partOfday_str}   /   向き：${orientation_str}",
                        Toast.LENGTH_SHORT
                    ).show()


                } else {
                    Toast.makeText(
                        this,
                        "${path}を背景画像に設定　\n時間帯：${partOfday_str}   /   向き：${orientation_str}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }


        })

    backgroundsImageFileView.adapter = adapter
    backgroundsImageFileView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
    val dialog = AlertDialog.Builder(this)

    dialog.setView(customView)
        .setNegativeButton("キャンセル") { dialog, _ ->
            // キャンセルボタンを押したときの処理
            dialog.dismiss()
        }
        .setOnDismissListener {
            backgroundsImageFileView.adapter = null
        }
        .create()
    dialog.show()

}

fun MediaItem.toFileItem(): FileItem? {
    val relativePath = this.getRelativePath() ?: return null
    val fileName = this.getDisplayName() ?: return null
    return FileItem(relativePath = relativePath, fileName = fileName)
}

fun AppCompatActivity.launchClearBackgroundImageFileDialog(
    originFileList: List<File> = getAllFile_inInternalStorage(
        BackgroundResolver.BACKGROUNDS_PATH
    )
) {


    val inflater = layoutInflater
    val customView = inflater.inflate(R.layout.recycle_view_dialog, null, false)


    val backgroundsImageFileView =
        customView.findViewById<RecyclerView>(R.id.backgroundFiles_forClear)

    val adapter =
        BackgroundsImageFileAdapter(originFileList, context = this, onItemClick = { file ->


            file.delete()
            Toast.makeText(
                this,
                "${file.name}をアプリから削除しました",
                Toast.LENGTH_SHORT
            ).show()


        })

    backgroundsImageFileView.adapter = adapter

    backgroundsImageFileView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
    val dialog = AlertDialog.Builder(this)

    dialog
        .setTitle("背景画像ファイルの削除")
        .setView(customView)
        .setNegativeButton("キャンセル") { dialog, _ ->
            // キャンセルボタンを押したときの処理
            dialog.dismiss()
        }
        .setOnDismissListener {
            backgroundsImageFileView.adapter = null
        }
        .create()
    val show = dialog.show()


    this.lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
        override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
            show.dismiss()
        }
    })
}

fun MediaMetadata.getRelativePath(): String? {
    return this.extras?.getString(LocalMusicRepository.EXTRA_RELATIVE_PATH)
}

fun MediaMetadata.getDisplayName(): String? {
    return this.extras?.getString(LocalMusicRepository.EXTRA_DISPLAY_NAME)
}

fun MediaMetadata.getDataPath(): String? {
    return this.extras?.getString(LocalMusicRepository.EXTRA_DATA_PATH)
}

fun MediaMetadata.getAlbumId(): Long? {
    return this.extras?.getLong(LocalMusicRepository.EXTRA_ALBUM_ID)
}

fun MediaMetadata.getArtistId(): Long? {
    return this.extras?.getLong(LocalMusicRepository.EXTRA_ARTIST_ID)
}


fun MediaItem.getRelativePath(): String? {

    return this.mediaMetadata.getRelativePath()
}

fun MediaItem.getDisplayName(): String? {
    return this.mediaMetadata.getDisplayName()
}

fun MediaItem.getDataPath(): String? {
    return this.mediaMetadata.getDataPath()
}

fun MediaItem.getAlbumId(): Long? {
    return this.mediaMetadata.getAlbumId()
}

fun MediaItem.getArtistId(): Long? {
    return this.mediaMetadata.getArtistId()
}


fun List<MediaItem>.filterByBlocklist(blocklistItems: List<BlocklistItem>): List<MediaItem> {
    return this.filter { mediaItem ->

        !blocklistItems.any { mediaItem.isSamePath(it.fileItem) }
    }
}


@JvmName("toPlaylistItemsFromMediaItems")
fun List<MediaItem>.toPlaylistItems(playlistId: Long): List<PlaylistItem> {
    return this.mapIndexedNotNull { index, mediaItem ->
        val relativePath = mediaItem.getRelativePath() ?: return@mapIndexedNotNull null
        val fileName = mediaItem.getDisplayName() ?: return@mapIndexedNotNull null
        val fileItem = FileItem(relativePath = relativePath, fileName = fileName)
        PlaylistItem(
            playlistId = playlistId,
            fileItem = fileItem,
            index = index
        )
    }

}

@JvmName("toBlockListItemsFromMediaItems")
fun List<MediaItem>.toBlockListItems(blocklistId: Long): List<BlocklistItem> {
    return this.mapNotNull { mediaItem ->
        val relativePath = mediaItem.getRelativePath() ?: return@mapNotNull null
        val fileName = mediaItem.getDisplayName() ?: return@mapNotNull null
        val fileItem = FileItem(relativePath = relativePath, fileName = fileName)
        BlocklistItem(
            blocklistId = blocklistId,
            fileItem = fileItem,
        )
    }
}

fun List<FileItem>.toPlaylistItems(playlistId: Long): List<PlaylistItem> {
    return this.mapIndexed { index, fileItem ->
        fileItem.toPlaylistItem(playlistId, index)
    }
}

fun FileItem.toPlaylistItem(playlistId: Long = 0, index: Int = 0): PlaylistItem {
    return PlaylistItem(
        playlistId = playlistId,
        fileItem = this,
        index = index
    )
}

fun List<FileItem>.toBlocklistItems(blocklistId: Long): List<BlocklistItem> {
    return this.map { fileItem ->
        fileItem.toBlocklistItem(blocklistId)
    }
}

fun FileItem.toBlocklistItem(blocklistId: Long = 0): BlocklistItem {
    return BlocklistItem(
        blocklistId = blocklistId,
        fileItem = this,
    )
}


fun getExtra_MediaItem(mediaItem: MediaItem, key: String): String? {
    return mediaItem.mediaMetadata.extras?.getString(key)
}

fun MediaItem.CreateFileItem(playlistId: Long = 0, index: Int = 0): FileItem? {
    val relativePath = this.mediaMetadata.getRelativePath() ?: return null
    val fileName = this.mediaMetadata.getDisplayName() ?: return null


    val fileItem = FileItem(relativePath = relativePath, fileName = fileName)
    return fileItem

}


//EditTextだけのシンプルなダイアログを表示する関数
fun AppCompatActivity.showEditTextDialog(
    title: String,

    positiveButtonTitle: String = "OK",

    onTextConfirmed: (String) -> Unit
) {

    val editText = EditText(this)
    editText.setPadding(10, 0, 10, 0)


    AlertDialog.Builder(this)
        .setTitle(title)
        .setView(editText)
        .setPositiveButton(positiveButtonTitle) { dialog, _ ->
            val inputText = editText.text.toString()
            onTextConfirmed(inputText)
            dialog.dismiss()
        }
        .setNegativeButton("キャンセル") { dialog, _ ->
            dialog.cancel()
        }
        .show()
}


/*
だいぶ前に書いたコードなので推測ですが
おそらくitemがnullのときはプレイリストの選択だけを行い、
itemが非nullのときはプレイリストの選択と同時にそのアイテムを選択したプレイリストに追加する挙動を意図していると思われます。
nullの場合、というのはプレイリストを選択し、listActivityなどでそのプレイリストの内容を表示する場合で、
非nullの場合はMainActivityで曲を選択して「この曲をプレイリストに追加」みたいな操作をしたときに、
その曲をどのプレイリストに追加するかを選ぶためのダイアログになるのではないでしょうか。
 */

fun AppCompatActivity.showPlaylistSelectDialog(
    playlists: List<PlayList>,
    item: FileItem? = null,
    onPlaylistSelected: (Long) -> Unit,

    ) {
    val dialog = AlertDialog.Builder(this)
        .setTitle("プレイリストを選択")
        .setItems(playlists.map { it.playlistName }.toTypedArray(), { dialog, which ->
            // TODO:アイテム選択時の挙動
            if (item == null) {
                onPlaylistSelected(playlists[which].playlistId)
            } else {

                // Log which playlist was selected for adding item
                Log.d(
                    "LIST_/ShowPlaylistSelectDialog/add",
                    "selected playlistId=${playlists[which].playlistId} to add item file=${item.fileName}"
                )

                lifecycleScope.launch {
                    DBManager.addPlaylistItem(
                        this@showPlaylistSelectDialog,
                        playlistId = playlists[which].playlistId,
                        fileItem = item
                    )
                    Log.d(
                        "LIST_/ShowPlaylistSelectDialog/addResult",
                        "added item to playlistId=${playlists[which].playlistId}"
                    )
                }
            }
        })
        .setNeutralButton("新規作成") { dialog, _ ->


            showEditTextDialog(
                title = "新しいプレイリストの名前",

                onTextConfirmed = { playlistName ->
                    if (playlistName.isNotBlank()) {


                        val newPlaylist = PlayList(playlistName = playlistName)
                        // データベースに新しいプレイリストを挿入
                        lifecycleScope.launch {
                            Log.d(
                                "LIST_/ShowPlaylistSelectDialog/create",
                                "creating playlist name=$playlistName"
                            )
                            if (item == null) {
                                val newId =
                                    DBManager.upsertPlaylist(
                                        this@showPlaylistSelectDialog.applicationContext,
                                        newPlaylist
                                    )
                                Log.d(
                                    "LIST_/ShowPlaylistSelectDialog/createResult",
                                    "created playlist id=$newId name=$playlistName"
                                )
                                Toast.makeText(
                                    this@showPlaylistSelectDialog,
                                    "プレイリスト「$playlistName」を作成しました",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {

                                DBManager.addPlaylistAndItem(
                                    this@showPlaylistSelectDialog.applicationContext,
                                    playlistName,
                                    item
                                )
                            }


                            dialog.dismiss() // ダイアログを閉じる
                        }
                    } else {
                        Toast.makeText(this, "プレイリスト名は空にできません", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            )
            dialog.dismiss()


        }
        .setNegativeButton("キャンセル", { dialog, _ ->
            dialog.dismiss()
        })
        .create()
    dialog.show()

}


fun AppCompatActivity.showBlockSelectDialog(
    blockList: List<BlockList>,
    item: FileItem? = null,
    onBlockSelected: (Long) -> Unit
) {
    val dialog = AlertDialog.Builder(this)
        .setTitle("プレイリストを選択")
        .setItems(blockList.map { it.blockListName }.toTypedArray(), { dialog, which ->
            // TODO:アイテム選択時の挙動
            onBlockSelected(blockList[which].blockListID)
        })
        .setNeutralButton("新規作成") { dialog, _ ->
            showEditTextDialog(
                title = "新しいブロックリストの名前",

                onTextConfirmed = { blockListName ->
                    if (blockListName.isNotBlank()) {
                        // ブロックリストの作成処理をここに追加


                        val newBlockList = BlockList(blockListName = blockListName)
                        // データベースに新しいブロックリストを挿入
                        lifecycleScope.launch {
                            if (item == null) {
                                Log.d(
                                    "LIST_/ShowBlockSelectDialog/create",
                                    "creating blocklist name=$blockListName"
                                )
                                val newId =
                                    DBManager.upsertBlocklist(
                                        this@showBlockSelectDialog,
                                        newBlockList
                                    )
                                Log.d(
                                    "LIST_/ShowBlockSelectDialog/createResult",
                                    "created blocklist id=$newId name=$blockListName"
                                )
                                Toast.makeText(
                                    this@showBlockSelectDialog,
                                    "ブロックリスト「$blockListName」を作成しました",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                DBManager.addBlocklistAndItem(
                                    this@showBlockSelectDialog.applicationContext,
                                    blockListName,
                                    item
                                )

                            }


                            dialog.dismiss() // ダイアログを閉じる
                        }
                    } else {
                        Toast.makeText(this, "ブロックリスト名は空にできません", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            )
            dialog.dismiss()
        }
        .setNegativeButton("キャンセル", { dialog, _ ->
            dialog.dismiss()
        })
        .create()
    dialog.show()


}

fun AppCompatActivity.showListSelectDialog(
    lists: List<DBManager.ListInfo>,
    onListSelected: (DBManager.ListInfo) -> Unit
) {

    val dialog = AlertDialog.Builder(this)
        .setTitle("リストを選択")
        .setItems(lists.map { it.name }.toTypedArray(), { dialog, which ->
            onListSelected(lists[which])
        })
        .setNegativeButton("キャンセル", { dialog, _ ->
            dialog.dismiss()
        })
        .create()
    dialog.show()
}

fun <T> Context.utilDialog(
    title: String,
    initItems: List<T>,
    initialStrings: Array<String>,
    onSelected: (T) -> Unit
) {

    val dialog = AlertDialog.Builder(this)
        .setTitle(title)
        .setItems(initialStrings, { _, index ->
            Log.d("utilDialog", "Selected index: $index, item: ${initItems.getOrNull(index)}")
            if (index < 0 || index >= initItems.size) {
                Toast.makeText(this, "無効な選択です", Toast.LENGTH_SHORT).show()
                return@setItems
            } else {


                onSelected(initItems[index])
            }

        })
        .setNegativeButton("キャンセル", { dialog, _ ->
            dialog.dismiss()
        })
        .create()
    dialog.show()


}

fun BlockList.toListInfo(): DBManager.ListInfo {
    return DBManager.ListInfo(
        id = this.blockListID,
        name = this.blockListName,
        type = ListsActivity.ListType.BLOCKLIST
    )

}

fun PlayList.toListInfo(): DBManager.ListInfo {
    return DBManager.ListInfo(
        id = this.playlistId,
        name = this.playlistName,
        type = ListsActivity.ListType.PLAYLIST
    )
}

fun playListAndBlockListToListInfo(
    playLists: List<PlayList>,
    blockLists: List<BlockList>
): List<DBManager.ListInfo> {
    val listInfo = mutableListOf<DBManager.ListInfo>()
    playLists.forEach { listInfo.add(it.toListInfo()) }
    blockLists.forEach { listInfo.add(it.toListInfo()) }
    return listInfo
}

fun MediaItem.isSamePath(MediaItem2: MediaItem): Boolean {
    val path1 = this.getRelativePath()
    val path2 = MediaItem2.getRelativePath()
    val name1 = this.getDisplayName()
    val name2 = MediaItem2.getDisplayName()
    return path1 == path2 && name1 == name2
}

fun MediaItem.isSamePath(Item2: FileItem): Boolean {
    val path1 = this.getRelativePath()
    val path2 = Item2.relativePath
    val name1 = this.getDisplayName()
    val name2 = Item2.fileName
    return path1 == path2 && name1 == name2
}



