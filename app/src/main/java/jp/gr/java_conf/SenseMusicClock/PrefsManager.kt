package jp.gr.java_conf.SenseMusicClock

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import jp.gr.java_conf.SenseMusicClock.BackgroundResolver.IMAGEFILE_KEY_LAND_EVENING
import jp.gr.java_conf.SenseMusicClock.BackgroundResolver.IMAGEFILE_KEY_LAND_MORNING
import jp.gr.java_conf.SenseMusicClock.BackgroundResolver.IMAGEFILE_KEY_LAND_NIGHT
import jp.gr.java_conf.SenseMusicClock.BackgroundResolver.IMAGEFILE_KEY_LAND_NOON
import jp.gr.java_conf.SenseMusicClock.BackgroundResolver.IMAGEFILE_KEY_OBLONG_EVENING
import jp.gr.java_conf.SenseMusicClock.BackgroundResolver.IMAGEFILE_KEY_OBLONG_MORNING
import jp.gr.java_conf.SenseMusicClock.BackgroundResolver.IMAGEFILE_KEY_OBLONG_NIGHT
import jp.gr.java_conf.SenseMusicClock.BackgroundResolver.IMAGEFILE_KEY_OBLONG_NOON
import jp.gr.java_conf.SenseMusicClock.BackgroundResolver.NOW_EVENING
import jp.gr.java_conf.SenseMusicClock.BackgroundResolver.NOW_MORNING
import jp.gr.java_conf.SenseMusicClock.BackgroundResolver.NOW_NIGHT
import jp.gr.java_conf.SenseMusicClock.BackgroundResolver.NOW_NOON
import jp.gr.java_conf.SenseMusicClock.BackgroundResolver.ORIENTATION_OBLONG
import jp.gr.java_conf.SenseMusicClock.Music.TargetDirectoryPrefJSONManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

object PrefsManager {

    val mutex = Mutex()


    val PREFS_NAME = "smc_prefs"

    private val MUSIC_DIR_RELATIVE_PATHS_KEY = stringPreferencesKey("music_dir_relative_paths")
    private val VOLUME_ADJUSTMENT = intPreferencesKey("volume_adjustment")
    private val ReLoad_Tracks_KEY = booleanPreferencesKey("reLoad_Tracks")
    private val TILE_TITLE_DISPLAY = booleanPreferencesKey("tile_title_display")
    private val PLAYMODE_LOOP_KEY = booleanPreferencesKey("playMode_loop")
    private val CURRENT_PLAYLIST_ID_KEY = longPreferencesKey("current_playlist_id")
    private val CURRENT_BLOCKLIST_ID_KEY = longPreferencesKey("current_blocklist_id")
    private val SET_ALARM_TIME_KEY = stringPreferencesKey("set_alarm_time")

    private val IS_SHUFFLE_KEY = booleanPreferencesKey("is_shuffle")

    const val IMAGEFILE_KEY_OBLONG_MORNING_KEY= "imageFile_key_oblong_morning"
    const val IMAGEFILE_KEY_OBLONG_NOON_KEY  = "imageFile_key_oblong_noon"
    const val IMAGEFILE_KEY_OBLONG_EVENING_KEY = "imageFile_key_oblong_evening"
    const val IMAGEFILE_KEY_OBLONG_NIGHT_KEY = "imageFile_key_oblong_night"
    const val IMAGEFILE_KEY_LAND_MORNING_KEY = "imageFile_key_land_morning"
    const val IMAGEFILE_KEY_LAND_NOON_KEY  = "imageFile_key_land_noon"
    const val IMAGEFILE_KEY_LAND_EVENING_KEY = "imageFile_key_land_evening"
    const val IMAGEFILE_KEY_LAND_NIGHT_KEY = "imageFile_key_land_night"



    //背景画像
    fun getImageFileKey(orientation: Int, partOfDay: Int, ): String {
        val key = when (orientation) {
            ORIENTATION_OBLONG -> { // oblong
                when (partOfDay) {
                    NOW_MORNING -> IMAGEFILE_KEY_OBLONG_MORNING
                    NOW_NOON -> IMAGEFILE_KEY_OBLONG_NOON
                    NOW_EVENING -> IMAGEFILE_KEY_OBLONG_EVENING
                    NOW_NIGHT -> IMAGEFILE_KEY_OBLONG_NIGHT
                    else -> IMAGEFILE_KEY_OBLONG_NIGHT // default
                }
            }

            else -> { // land
                when (partOfDay) {
                    NOW_MORNING -> IMAGEFILE_KEY_LAND_MORNING
                    NOW_NOON -> IMAGEFILE_KEY_LAND_NOON
                    NOW_EVENING -> IMAGEFILE_KEY_LAND_EVENING
                    NOW_NIGHT -> IMAGEFILE_KEY_LAND_NIGHT
                    else -> IMAGEFILE_KEY_LAND_NIGHT // default
                }
            }
        }

        return key
    }

    suspend fun getImageFilePath(context: Context, key: String, default: String = ""): String {
        if (key !in listOf(
                IMAGEFILE_KEY_OBLONG_MORNING_KEY,
                IMAGEFILE_KEY_OBLONG_NOON_KEY,
                IMAGEFILE_KEY_OBLONG_EVENING_KEY,
                IMAGEFILE_KEY_OBLONG_NIGHT_KEY,
                IMAGEFILE_KEY_LAND_MORNING_KEY,
                IMAGEFILE_KEY_LAND_NOON_KEY,
                IMAGEFILE_KEY_LAND_EVENING_KEY,
                IMAGEFILE_KEY_LAND_NIGHT_KEY
            )
        ) {
            throw IllegalArgumentException("Invalid key: $key")
        }
        return context.getPrefsValue(stringPreferencesKey(key), default)
    }
    suspend fun getImageFilePath(context: Context,orientation: Int, partOfDay: Int , default: String = ""): String {
        val key = getImageFileKey(orientation, partOfDay)
        if (key !in listOf(
                IMAGEFILE_KEY_OBLONG_MORNING_KEY,
                IMAGEFILE_KEY_OBLONG_NOON_KEY,
                IMAGEFILE_KEY_OBLONG_EVENING_KEY,
                IMAGEFILE_KEY_OBLONG_NIGHT_KEY,
                IMAGEFILE_KEY_LAND_MORNING_KEY,
                IMAGEFILE_KEY_LAND_NOON_KEY,
                IMAGEFILE_KEY_LAND_EVENING_KEY,
                IMAGEFILE_KEY_LAND_NIGHT_KEY
            )
        ) {
            throw IllegalArgumentException("Invalid key: $key")
        }
        return context.getPrefsValue(stringPreferencesKey(key), default)
    }
    suspend fun setImageFilePath(context: Context, key: String, value: String) {
        if (key !in listOf(
                IMAGEFILE_KEY_OBLONG_MORNING_KEY,
                IMAGEFILE_KEY_OBLONG_NOON_KEY,
                IMAGEFILE_KEY_OBLONG_EVENING_KEY,
                IMAGEFILE_KEY_OBLONG_NIGHT_KEY,
                IMAGEFILE_KEY_LAND_MORNING_KEY,
                IMAGEFILE_KEY_LAND_NOON_KEY,
                IMAGEFILE_KEY_LAND_EVENING_KEY,
                IMAGEFILE_KEY_LAND_NIGHT_KEY
            )
        ) {
            throw IllegalArgumentException("Invalid key: $key")
        }
   context.setPrefsValue(stringPreferencesKey(key), value)
    }
    suspend fun setImageFilePath(context: Context,orientation: Int, partOfDay: Int , value: String){
        val key = getImageFileKey(orientation, partOfDay)
        if (key !in listOf(
                IMAGEFILE_KEY_OBLONG_MORNING_KEY,
                IMAGEFILE_KEY_OBLONG_NOON_KEY,
                IMAGEFILE_KEY_OBLONG_EVENING_KEY,
                IMAGEFILE_KEY_OBLONG_NIGHT_KEY,
                IMAGEFILE_KEY_LAND_MORNING_KEY,
                IMAGEFILE_KEY_LAND_NOON_KEY,
                IMAGEFILE_KEY_LAND_EVENING_KEY,
                IMAGEFILE_KEY_LAND_NIGHT_KEY
            )
        ) {
            throw IllegalArgumentException("Invalid key: $key")
        }
       context.setPrefsValue(stringPreferencesKey(key),value)
    }








    // 現在のプレイリストID
    fun getCurrentPlaylistIdFlow(context: Context, default: Long = -1): Flow<Long> {
        return context.getPrefsFlow(CURRENT_PLAYLIST_ID_KEY, default)
    }
    suspend fun getCurrentPlaylistId(context: Context, default: Long = -1): Long {
        return context.getPrefsValue(CURRENT_PLAYLIST_ID_KEY, default)
    }
    suspend fun setCurrentPlaylistId(context: Context, value: Long) {
        Log.d("LIST_/PrefsManager", "setCurrentPlaylistId() -> $value")
        context.setPrefsValue(CURRENT_PLAYLIST_ID_KEY, value)
    }
    suspend fun clearCurrentPlaylistId(context: Context) {
        context.clearPrefsValue(CURRENT_PLAYLIST_ID_KEY)
    }

    // 現在のブロックリストID
    fun getCurrentBlocklistIdFlow(context: Context, default: Long = -1): Flow<Long> {
        return context.getPrefsFlow(CURRENT_BLOCKLIST_ID_KEY, default)
    }
    suspend fun getCurrentBlocklistId(context: Context, default: Long = -1): Long {
        return context.getPrefsValue(CURRENT_BLOCKLIST_ID_KEY, default)
    }
    suspend fun setCurrentBlocklistId(context: Context, value: Long) {
        context.setPrefsValue(CURRENT_BLOCKLIST_ID_KEY, value)
    }
    suspend fun clearCurrentBlocklistId(context: Context) {
        context.clearPrefsValue(CURRENT_BLOCKLIST_ID_KEY)
    }








    // 現在の音量
    fun getVolumeAdjustmentFlow(context: Context, default: Int = 0): Flow<Int> {
        return context.getPrefsFlow(VOLUME_ADJUSTMENT, default)
    }

    suspend fun getVolumeAdjustment(context: Context, default: Int = 0): Int {
        return context.getPrefsValue(VOLUME_ADJUSTMENT, default)
    }

    suspend fun setVolumeAdjustment(context: Context, value: Int) {
        context.setPrefsValue(VOLUME_ADJUSTMENT, value)
    }







    // 音楽ディレクトリの相対パス
    fun getMusicDirRelativePathFlow(context: Context, default: String = TargetDirectoryPrefJSONManager.UNKNOWN): Flow<String> {
        return context.getPrefsFlow(MUSIC_DIR_RELATIVE_PATHS_KEY, default)
    }

    suspend fun getMusicDirRelativePath(context: Context, default: String = TargetDirectoryPrefJSONManager.UNKNOWN): String {
        return context.getPrefsValue(MUSIC_DIR_RELATIVE_PATHS_KEY, default)
    }

    suspend fun setMusicDirRelativePath(context: Context, value: String) {
        context.setPrefsValue(MUSIC_DIR_RELATIVE_PATHS_KEY, value)
    }

    suspend fun clearMusicDirRelativePath(context: Context) {
        context.clearPrefsValue(MUSIC_DIR_RELATIVE_PATHS_KEY)
    }







    // トラックの再読み込みフラグ
    fun getReloadTracksFlow(context: Context, default: Boolean = false): Flow<Boolean> {
        Log.d("PrefsManager", "getReloadTracksFlow() -> fetching flow with default=$default")
        return context.getPrefsFlow(ReLoad_Tracks_KEY, default)
    }

    suspend fun getReloadTracks(context: Context, default: Boolean = false): Boolean {
        Log.d("PrefsManager", "getReloadTracks() -> fetching value with default=$default")
        return context.getPrefsValue(ReLoad_Tracks_KEY, default)
    }

    suspend fun setReloadTracks(context: Context, value: Boolean) {
        Log.d("PrefsManager", "setReloadTracks() -> $value")
        context.setPrefsValue(ReLoad_Tracks_KEY, value)
    }
    suspend fun setReloadTracks_reverse_andGet(context: Context): Boolean {
        val newValue = !(getReloadTracks(context))
        setReloadTracks(context, newValue)
        return newValue
    }









    // jacketAdapterでtext plusのlayout使うかフラグ
    fun getTileTitleDisplayFlow(context: Context, default: Boolean = false): Flow<Boolean> {
        return context.getPrefsFlow(TILE_TITLE_DISPLAY, default)
    }

    suspend fun getTileTitleDisplay(context: Context, default: Boolean = false): Boolean {
        return context.getPrefsValue(TILE_TITLE_DISPLAY, default)
    }

    suspend fun setTileTitleDisplay(context: Context, value: Boolean) {
        context.setPrefsValue(TILE_TITLE_DISPLAY, value)
    }








    // 再生モードのループフラグ
    fun getPlayModeLoopFlow(context: Context, default: Boolean = false): Flow<Boolean> {

        return context.getPrefsFlow(PLAYMODE_LOOP_KEY, default)
    }

    suspend fun getPlayModeLoop(context: Context, default: Boolean = false): Boolean {


        return context.getPrefsValue(PLAYMODE_LOOP_KEY, default)
    }

    suspend fun setPlayModeLoop(context: Context, value: Boolean) {
        context.setPrefsValue(PLAYMODE_LOOP_KEY, value)
    }

    // シャッフルするかしないか
    fun getIsShuffleFlow(context: Context, default: Boolean = false): Flow<Boolean> {

        return context.getPrefsFlow(IS_SHUFFLE_KEY, default)
    }

    suspend fun getIsShuffle(context: Context, default: Boolean = false): Boolean {


        return context.getPrefsValue(IS_SHUFFLE_KEY, default)
    }

    suspend fun setIsShuffle(context: Context, value: Boolean) {
        context.setPrefsValue(IS_SHUFFLE_KEY, value)
    }








    // アラームセット時間
    fun getSetAlarmTimeFlow(context: Context): Flow<String> {
        return context.getPrefsFlow(SET_ALARM_TIME_KEY,"" )
    }

    suspend fun getSetAlarmTime(context: Context, default: String = ""): String {
        return context.getPrefsValue(SET_ALARM_TIME_KEY, default)
    }

    suspend fun setSetAlarmTime(context: Context, hour: Int = 0, minute: Int = 0) {


        val value = if (hour in 0..23 && minute in 0..59 && !(hour == 0 && minute == 0)) {
            "$hour:$minute"
        } else {
            ""
        }
        context.setPrefsValue(SET_ALARM_TIME_KEY, value)
    }

    suspend fun clearSetAlarmTime(context: Context) {
        context.dataStore.edit { prefs ->
            prefs.remove(SET_ALARM_TIME_KEY)
        }
    }








    //ここら辺はDataStore用の汎用関数。


    private fun<T> Context.getPrefsFlow(
        key: Preferences.Key<T>,
        default: T
    ): Flow<T> {
        return dataStore.data.map { prefs ->
            prefs[key] ?: default
        }.distinctUntilChanged()

    }

    private suspend fun<T> Context.getPrefsValue(
        key: Preferences.Key<T>,
        default: T
    ): T {
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                dataStore.data.map { prefs ->
                    prefs[key] ?: default
                }.distinctUntilChanged().first()
            }
        }

    }

    private suspend fun <T> Context.setPrefsValue(key: Preferences.Key<T>, value: T) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                // ログ出力：どのキーをどの値で変更したか
                try {
                    Log.d("PrefsManager", "setPrefsValue: key=${key.name}, value=${value}")
                } catch (e: Exception) {
                    // 値の toString() で例外が発生する可能性は低いが念のため
                    Log.d("PrefsManager", "setPrefsValue: key=${key.name}, value=<unprintable>")
                }
                dataStore.edit { prefs ->
                    prefs[key] = value
                }
            }
        }
    }



    private suspend fun <T> Context.clearPrefsValue(key: Preferences.Key<T> ) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                Log.d("PrefsManager", "clearPrefsValue: key=${key.name}")
                dataStore.edit { prefs ->
                    prefs.remove(key)
                }
            }
        }
    }


}