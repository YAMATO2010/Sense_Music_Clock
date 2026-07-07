// kotlin
package jp.gr.java_conf.SenseMusicClock.Music


import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import jp.gr.java_conf.SenseMusicClock.PrefsManager

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


object TargetDirectoryPrefJSONManager{
    const val UNKNOWN = "unkonwn//////////UNKNOWN"


    private val gson = Gson()
    private val listType = object : TypeToken<List<String>>() {}.type

    private val mutex = Mutex()


    /**
     * 保存されている全ての相対パスを List\<String\> で返す（順序保持）。
     */

    private suspend fun _getAll(context: Context): List<String> {


        val json = getJSONString(context)
        if (json == UNKNOWN) return emptyList()
        return try {
            (gson.fromJson<List<String>>(json, listType) ?: emptyList()).toList()
        } catch (_: Exception) {
            emptyList()
        }

    }

    suspend fun getAll(context: Context): List<String> {
        return mutex.withLock {
            _getAll(context)
        }
    }

    /**
     * 指定したリストを JSON として保存する。
     * 空文字や空白のみの要素は除外し、最初に出現した順序を保持しつつ重複を除く。
     */

    private suspend fun _saveAll(paths: List<String>,context: Context) {


        val filtered = paths.map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()

        PrefsManager.setMusicDirRelativePath(context, gson.toJson(filtered))

    }

    suspend fun saveAll(paths: List<String>,context: Context) {
        mutex.withLock {
            _saveAll(paths,context)
        }
    }

    /**
     * 1要素を追加する。既に存在する場合は何もしない。
     * 追加された場合は true を返す。
     */

    suspend fun _add(path: String,context: Context): Boolean {
        val p = path.trim()
        if (p.isBlank()) return false
        val current = _getAll(context).toMutableList()
        if (current.contains(p)) return false
        current.add(p)
        _saveAll(current,context)
        return true

    }

    suspend fun add(path: String,context: Context): Boolean {
        return mutex.withLock {
            _add(path,context)
        }
    }

    /**
     * 指定したパスを削除する。削除が行われたら true を返す。
     */

    private suspend fun _remove(path: String,context: Context): Boolean {


        val p = path.trim()
        if (p.isBlank()) return false
        val current = _getAll(context)
        val updated = current.filterNot { it == p }

        // 要素が減っていれば、変更があったと判断して保存
        return if (updated.size < current.size) {
            _saveAll(updated, context)
            true
        } else {
            false
        }
    }

    suspend fun remove(path: String,context: Context): Boolean {
        return mutex.withLock {
            _remove(path,context)
        }
    }

    /**
     * 全消去
     */

    private suspend fun _clear(context: Context) {
        PrefsManager.clearMusicDirRelativePath(context)

    }

    suspend fun clear(context: Context) {
        mutex.withLock {
            _clear(context)
        }
    }

    suspend fun getJSONString(context: Context): String {
        return PrefsManager.getMusicDirRelativePath(context)
    }
}