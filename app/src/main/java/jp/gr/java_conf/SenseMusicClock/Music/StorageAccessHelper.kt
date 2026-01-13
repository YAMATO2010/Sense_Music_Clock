package jp.gr.java_conf.SenseMusicClock.Music

import MUSIC_DIR_RELATIVE_PATHS_KEY
import SHAREDPREFERENCES_NAME
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class StorageAccessHelper(
    private val activity: AppCompatActivity,
    private val onDirectoryPicked: (relativePath: String?, uri: Uri?) -> Unit,
    private val onPermissionGranted: () -> Unit = {},
    private val onPermissionDenied: () -> Unit = {}

) {
    private val prefs by lazy { activity.getSharedPreferences(SHAREDPREFERENCES_NAME, Context.MODE_PRIVATE) }

    private val perm: String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private val pickDirLauncher: ActivityResultLauncher<Uri?> =
        activity.registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                // 必要なら永続パーミッションを取る（コメントアウトして任意に切替可能）
                // val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                // try { activity.contentResolver.takePersistableUriPermission(uri, flags) } catch (_: SecurityException) { }


                val rel = parseTreeUriToRelativePath(uri)
                TargetDirectoryManager(activity).add(rel ?: return@registerForActivityResult)

                onDirectoryPicked(rel, uri)
            } else {
                onDirectoryPicked(null, null)
            }
        }

    private val requestPermissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                onPermissionGranted()
            } else {
                onPermissionDenied()
            }
        }


    fun ensureReadAudioPermission() {


        if (ContextCompat.checkSelfPermission(activity, perm) == PackageManager.PERMISSION_GRANTED) {
            onPermissionGranted()
            return
        }


            requestPermissionLauncher.launch(perm)

    }

    fun hasReadAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(activity, perm) == PackageManager.PERMISSION_GRANTED
    }



    fun launchPickDirectory(initialUri: Uri? = null) {
        pickDirLauncher.launch(initialUri)
    }

    fun getSavedRelativePath(): String? = prefs.getString("music_dir_relative_path", null)
    fun getSavedUri(): Uri? = prefs.getString("music_dir_uri", null)?.let { Uri.parse(it) }

    /**
     * SAF の tree Uri から DocumentsContract.getTreeDocumentId を使って
     * MediaStore の RELATIVE_PATH に相当するパス（例: "Music/SMC"）を試みて抽出する。
     * - primary: の場合はコロン以降を返す
     * - それ以外のボリュームは "volumeId:パス" 形式で返す（必要に応じてアプリ側で調整）
     */
    private fun parseTreeUriToRelativePath(uri: Uri): String? {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(uri) // 例: "primary:Music/SMC"
            val parts = docId.split(":", limit = 2)
            if (parts.size == 2) {
                val storageId = parts[0]
                var pathPart = parts[1].removePrefix("/").trimEnd('/')
                if (storageId == "primary") {
                    // MediaStore の RELATIVE_PATH はボリューム名を含めない場合が多い
                    pathPart.takeIf { it.isNotEmpty() }
                } else {
                    // 他ボリュームの場合は必要に応じて "volumeId:パス" 形式を使う
                    "$storageId:$pathPart"
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}