package jp.gr.java_conf.SenseMusicClock

import android.app.Activity
import android.content.Context
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.recyclerview.widget.RecyclerView
import jp.gr.java_conf.SenseMusicClock.Music.Data.AppDataBase
import jp.gr.java_conf.SenseMusicClock.Music.Data.BlockList
import jp.gr.java_conf.SenseMusicClock.Music.Data.BlocklistItem
import jp.gr.java_conf.SenseMusicClock.Music.Data.PlayList
import jp.gr.java_conf.SenseMusicClock.Music.Data.PlaylistItem
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
                getSharedPreferences(getString(SHAREDPREFERENCES_NAME), Context.MODE_PRIVATE).edit {
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
    backgroundsImageFileView.layoutManager =  androidx.recyclerview.widget.LinearLayoutManager(this)
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
    val customView = inflater.inflate(R.layout.recycle_view_dialog, null,false)


    val backgroundsImageFileView = customView.findViewById<RecyclerView>(R.id.backgroundFiles_forClear)

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


suspend fun Context.loadPlaylist(): List<PlayList> {

    val db = AppDataBase.getInstance(this)
    val playlistDao = db.playListDao()

    return playlistDao.loadAllPlaylists()


}

suspend fun Context.loadPlaylistItem(playlistId : Long): List<PlaylistItem> {

    val db = AppDataBase.getInstance(this)
    val playlistItemDao = db.playListItemDao()

    return playlistItemDao.loadItemsForPlaylist(playlistId)

}
suspend fun Context.loadBlocklist(): List<BlockList> {

    val db = AppDataBase.getInstance(this)
    val blocklistDao = db.blockListDao()

    return blocklistDao.loadAllBlocklists()


}

suspend fun Context.loadBlocklistItem(blocklistId : Long): List<BlocklistItem> {

    val db = AppDataBase.getInstance(this)
    val blocklistItemDao = db.blockListItemDao()

    return blocklistItemDao.loadItemsForBlocklist(blocklistId)

}

fun Context.showPlaylistSelectDialog(
    playlists: List<PlayList>,
    onPlaylistSelected: (Long) -> Unit
) {
    val dialog = AlertDialog.Builder(this)
        .setTitle("プレイリストを選択")
        .setItems(playlists.map { it.playlistName }.toTypedArray(), { dialog, which ->
            // TODO:アイテム選択時の挙動
            onPlaylistSelected(playlists[which].playlistId)
        })
        .setNegativeButton("キャンセル", {
                dialog, _ ->
                dialog.dismiss()
        })
        .create()
    dialog.show()

}

fun Context.showBlockSelectDialog(
    blockList: List<BlockList>,
    onBlockSelected: (Long) -> Unit
) {
    val dialog = AlertDialog.Builder(this)
        .setTitle("プレイリストを選択")
        .setItems(blockList.map { it.blockListName }.toTypedArray(), { dialog, which ->
            // TODO:アイテム選択時の挙動
            onBlockSelected(blockList[which].blockListID)
        })
        .setNegativeButton("キャンセル", {
                dialog, _ ->
            dialog.dismiss()
        })
        .create()
    dialog.show()


}



