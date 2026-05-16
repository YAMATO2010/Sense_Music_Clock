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


class TargetDirectoryPrefJSONManager(
    val context: Context,

    ) {

    companion object{
        const val UNKNOWN = "unkonwn//////////UNKNOWN"
    }
    private val gson = Gson()
    private val listType = object : TypeToken<List<String>>() {}.type

    private val mutex = Mutex()

    /**
     * 保存されている全ての相対パスを List\<String\> で返す（順序保持）。
     */

    suspend fun getAll(): List<String> {
        mutex.withLock {


            val json = getJSONString()
            if (getJSONString() == UNKNOWN) return emptyList()
            return try {
                (gson.fromJson<List<String>>(json, listType) ?: emptyList()).toList()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    /**
     * 指定したリストを JSON として保存する。
     * 空文字や空白のみの要素は除外し、最初に出現した順序を保持しつつ重複を除く。
     */

    suspend fun saveAll(paths: List<String>) {
        mutex.withLock {

            val filtered = paths.map { it.trim() }
                .filter { it.isNotEmpty() }
                .fold(LinkedHashSet<String>()) { acc, p -> acc.apply { add(p) } }
                .toList()
            PrefsManager.setMusicDirRelativePath(context,gson.toJson(filtered))
        }
    }

    /**
     * 1要素を追加する。既に存在する場合は何もしない。
     * 追加された場合は true を返す。
     */

    suspend fun add(path: String): Boolean {
        mutex.withLock {

            val p = path.trim()
            if (p.isBlank()) return false
            val current = getAll().toMutableList()
            if (current.contains(p)) return false
            current.add(p)
            saveAll(current)
            return true
        }
    }

    /**
     * 指定したパスを削除する。削除が行われたら true を返す。
     */

    suspend fun remove(path: String): Boolean {
        mutex.withLock {


        val p = path.trim()
        if (p.isBlank()) return false
        val current = getAll().toMutableList()
        val removed = current.removeAll { it == p }
        if (removed) saveAll(current)
        return removed
        }
    }

    /**
     * 全消去
     */

    suspend fun clear() {
        mutex.withLock {
            PrefsManager.clearMusicDirRelativePath(context)


        }
    }

    suspend fun getJSONString(): String? {
        return PrefsManager.getMusicDirRelativePath(context)
    }
}