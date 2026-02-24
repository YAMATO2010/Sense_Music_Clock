package jp.gr.java_conf.SenseMusicClock

import android.app.Activity
import android.content.Context
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Index
import androidx.room.withTransaction
import jp.gr.java_conf.SenseMusicClock.Music.Data.AppDataBase
import jp.gr.java_conf.SenseMusicClock.Music.Data.BlockList
import jp.gr.java_conf.SenseMusicClock.Music.Data.BlocklistItem
import jp.gr.java_conf.SenseMusicClock.Music.Data.FileItem
import jp.gr.java_conf.SenseMusicClock.Music.Data.PlayList
import jp.gr.java_conf.SenseMusicClock.Music.Data.PlaylistItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.checkerframework.checker.index.qual.Positive
import java.io.File

fun Activity.launchSelectBackgroundImageDialog(
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

            BackgroundResolver.getPrefsKey(selectedOrientation, selectedPartOfDay).let {
                val path = file.name
                PrefsManager.getSharedPreferences(this).edit {
                    putString(it, path)
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


suspend fun Context.loadPlaylist(): List<PlayList> {

    val db = AppDataBase.getInstance(this)
    val playlistDao = db.playListDao()

    return playlistDao.loadAllPlaylists()


}

suspend fun Context.loadPlaylistItem(playlistId: Long): List<PlaylistItem> {

    val db = AppDataBase.getInstance(this)
    val playlistItemDao = db.playListItemDao()

    return playlistItemDao.loadItemsForPlaylist(playlistId)

}

suspend fun Context.loadBlocklist(): List<BlockList> {

    val db = AppDataBase.getInstance(this)
    val blocklistDao = db.blockListDao()

    return blocklistDao.loadAllBlocklists()


}

suspend fun Context.loadBlocklistItem(blocklistId: Long): List<BlocklistItem> {

    val db = AppDataBase.getInstance(this)
    val blocklistItemDao = db.blockListItemDao()

    return blocklistItemDao.loadItemsForBlocklist(blocklistId)

}

suspend fun Context.addPlaylistItem(
    playlistId: Long,
    fileItem: FileItem,
    index: Int? = null,
    DoToast: Boolean = true
) {
    withContext(Dispatchers.IO) {


        val db = AppDataBase.getInstance(this@addPlaylistItem)
        val isSuccess: Boolean = db.withTransaction {
            val playlistItemDao = db.playListItemDao()
            val newIndex =
                index ?: ((playlistItemDao.getMaxIndexForPlaylist(playlistId))?.plus(1)
                    ?: 0) // 現在の最大インデックスに1を加算、プレイリストが空の場合は0から開始
            val newPlaylistItem =
                PlaylistItem(playlistId = playlistId, fileItem = fileItem, index = newIndex)
            playlistItemDao.insertPlaylistItem(newPlaylistItem)
            return@withTransaction true


        }
        withContext(Dispatchers.Main) {

            if (isSuccess && DoToast) {
                Toast.makeText(
                    this@addPlaylistItem,
                    "プレイリストに追加しました ",
                    Toast.LENGTH_SHORT
                ).show()
            } else if (DoToast) {
                Toast.makeText(
                    this@addPlaylistItem,
                    "プレイリストへの追加に失敗しました ",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }


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

fun AppCompatActivity.showEditTextDialog(
    title: String,

    positiveButtonTitle: String = "OK",

    onTextConfirmed: (String) -> Unit
) {


    val view = layoutInflater.inflate(R.layout.edittext_only, null)
    val editText = view.findViewById<EditText>(R.id.edit_text_input)


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


fun AppCompatActivity.showPlaylistSelectDialog(
    playlists: List<PlayList>,
    item: PlaylistItem? = null,
    onPlaylistSelected: (Long) -> Unit,

    ) {
    val dialog = AlertDialog.Builder(this)
        .setTitle("プレイリストを選択")
        .setItems(playlists.map { it.playlistName }.toTypedArray(), { dialog, which ->
            // TODO:アイテム選択時の挙動
            if (item == null) {
                onPlaylistSelected(playlists[which].playlistId)
            } else {
                lifecycleScope.launch {

                    addPlaylistItem(
                        playlistId = playlists[which].playlistId,
                        fileItem = item.fileItem
                    )
                }
            }
        })
        .setNeutralButton("新規作成") { dialog, _ ->


            showEditTextDialog(
                title = "新しいプレイリストの名前",

                onTextConfirmed = { playlistName ->
                    if (playlistName.isNotBlank()) {
                        // プレイリストの作成処理をここに追加
                        val db = AppDataBase.getInstance(this)
                        val playlistDao = db.playListDao()

                        val newPlaylist = PlayList(playlistName = playlistName)
                        // データベースに新しいプレイリストを挿入
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                playlistDao.insertPlaylist(newPlaylist).let {
                                    if (item != null) {
                                        addPlaylistItem(
                                            playlistId = it,
                                            fileItem = item.fileItem,
                                            DoToast = false
                                        )
                                    }
                                }
                            }
                            Toast.makeText(
                                this@showPlaylistSelectDialog,
                                "プレイリスト「$playlistName」を作成しました",
                                Toast.LENGTH_SHORT
                            ).show()
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

                        val db = AppDataBase.getInstance(this)
                        val blocklistDao = db.blockListDao()

                        val newBlockList = BlockList(blockListName = blockListName)
                        // データベースに新しいブロックリストを挿入
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                blocklistDao.insertBlockList(newBlockList)
                            }
                            Toast.makeText(
                                this@showBlockSelectDialog,
                                "ブロックリスト「$blockListName」を作成しました",
                                Toast.LENGTH_SHORT
                            ).show()
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



