// kotlin
package jp.gr.java_conf.SenseMusicClock.Music

import MUSIC_DIR_RELATIVE_PATHS_KEY
import SHAREDPREFERENCES_NAME
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class TargetDirectoryManager(
    context: Context,

    private val key: String = MUSIC_DIR_RELATIVE_PATHS_KEY
) {
    private val prefs = context.getSharedPreferences(SHAREDPREFERENCES_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listType = object : TypeToken<List<String>>() {}.type

    /**
     * 保存されている全ての相対パスを List\<String\> で返す（順序保持）。
     */
    @Synchronized
    fun getAll(): List<String> {
        val json = prefs.getString(key, null) ?: return emptyList()
        return try {
            (gson.fromJson<List<String>>(json, listType) ?: emptyList()).toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 指定したリストを JSON として保存する。
     * 空文字や空白のみの要素は除外し、最初に出現した順序を保持しつつ重複を除く。
     */
    @Synchronized
    fun saveAll(paths: List<String>) {
        val filtered = paths.map { it.trim() }
            .filter { it.isNotEmpty() }
            .fold(LinkedHashSet<String>()) { acc, p -> acc.apply { add(p) } }
            .toList()
        prefs.edit().putString(key, gson.toJson(filtered)).apply()
    }

    /**
     * 1要素を追加する。既に存在する場合は何もしない。
     * 追加された場合は true を返す。
     */
    @Synchronized
    fun add(path: String): Boolean {
        val p = path.trim()
        if (p.isEmpty()) return false
        val current = getAll().toMutableList()
        if (current.contains(p)) return false
        current.add(p)
        saveAll(current)
        return true
    }

    /**
     * 指定したパスを削除する。削除が行われたら true を返す。
     */
    @Synchronized
    fun remove(path: String): Boolean {
        val p = path.trim()
        if (p.isEmpty()) return false
        val current = getAll().toMutableList()
        val removed = current.removeAll { it == p }
        if (removed) saveAll(current)
        return removed
    }

    /**
     * 全消去
     */
    @Synchronized
    fun clear() {
        prefs.edit().remove(key).apply()
    }
}