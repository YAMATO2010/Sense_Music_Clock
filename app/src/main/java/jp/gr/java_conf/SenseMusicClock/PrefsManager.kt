package jp.gr.java_conf.SenseMusicClock

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

object PrefsManager {


    val SHAREDPREFERENCES_NAME = R.string.SHAREDPREFERENCES_NAME
    val MUSIC_DIR_RELATIVE_PATHS_KEY = R.string.MUSIC_DIR_RELATIVE_PATHS_KEY
    val VOLUME_ADJUSTMENT = R.string.VOLUME_ADJUSTMENT
    val ReLoad_Tracks_KEY = R.string.RELOAD_TRACKS_KEY
    val TILE_TITLE_DISPLAY = R.string.TILE_TITLE_DISPLAY
    val PLAYMODE_LOOP_KEY = R.string.PLAYMODE_LOOP_KEY

    val SET_ALARM_TIME_KEY = R.string.SET_ALARM_TIME_KEY


    fun getSharedPreferences(context: Context): SharedPreferences = context.getSharedPreferences(
        context.getString(SHAREDPREFERENCES_NAME),
        Context.MODE_PRIVATE
    )

    //こいつだけは別のクラスが担当しているので、キーの取得のみここで行う
    fun getMusicDirRelativePathKey(context: Context): String {
        return context.getString(MUSIC_DIR_RELATIVE_PATHS_KEY)
    }


    fun getVolumeAdjustmentKey(context: Context): String {
        return context.getString(VOLUME_ADJUSTMENT)
    }

    fun getVolumeAdjustment(context: Context): Int {
        val prefs = getSharedPreferences(context)
        return prefs.getInt(getVolumeAdjustmentKey(context), 0)
    }

    fun setVolumeAdjustment(context: Context, value: Int) {
        val prefs = getSharedPreferences(context)
        prefs.edit { putInt(getVolumeAdjustmentKey(context), value) }
    }

    fun getReloadTracksKey(context: Context): String {
        return context.getString(ReLoad_Tracks_KEY)
    }

    fun getReloadTracks(context: Context): Boolean {
        val prefs = getSharedPreferences(context)
        return prefs.getBoolean(getReloadTracksKey(context), false)
    }

    fun setReloadTracks(context: Context, value: Boolean) {
        val prefs = getSharedPreferences(context)
        prefs.edit { putBoolean(getReloadTracksKey(context), value) }
    }

    fun getTileTitleDisplayKey(context: Context): String {
        return context.getString(TILE_TITLE_DISPLAY)
    }

    fun getTileTitleDisplay(context: Context): Boolean {
        val prefs = getSharedPreferences(context)
        return prefs.getBoolean(getTileTitleDisplayKey(context), false)
    }

    fun setTileTitleDisplay(context: Context, value: Boolean) {
        val prefs = getSharedPreferences(context)
        prefs.edit { putBoolean(getTileTitleDisplayKey(context), value) }
    }

    fun getPlayModeLoopKey(context: Context): String {
        return context.getString(PLAYMODE_LOOP_KEY)
    }

    fun getPlayModeLoop(context: Context): Boolean {
        val prefs = getSharedPreferences(context)
        return prefs.getBoolean(getPlayModeLoopKey(context), false)
    }

    fun setPlayModeLoop(context: Context, value: Boolean) {
        val prefs = getSharedPreferences(context)
        prefs.edit { putBoolean(getPlayModeLoopKey(context), value) }
    }

    fun getSetAlarmTimeKey(context: Context): String {
        return context.getString(SET_ALARM_TIME_KEY)
    }


    fun getSetAlarmTime(context: Context): String? {
        val prefs = getSharedPreferences(context)
        return prefs.getString(getSetAlarmTimeKey(context), null)
    }

    fun setSetAlarmTime(context: Context, hour: Int = 0, minute: Int = 0) {
        val prefs = getSharedPreferences(context)

        val value = if (hour in 0..23 && minute in 0..59 && !(hour == 0 && minute == 0)) {
            "$hour:$minute"
        } else {
            ""
        }
        prefs.edit { putString(getSetAlarmTimeKey(context), value) }
    }

    fun clearSetAlarmTime(context: Context) {
        val prefs = getSharedPreferences(context)
        prefs.edit { remove(getSetAlarmTimeKey(context)) }
    }


}