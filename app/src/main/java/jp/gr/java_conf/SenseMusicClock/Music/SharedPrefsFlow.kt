package jp.gr.java_conf.SenseMusicClock.Music

import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.Flow

object SharedPrefsFlow {
    fun observeString(prefs: SharedPreferences, key: String, default: String? = null): Flow<String?> =
        callbackFlow {
            Log.d("SharedPrefsFlow", "observeString: registering listener for key=$key on prefs=${prefs.hashCode()}")
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
                if (changedKey == key) {
                    val v = prefs.getString(key, default)
                    Log.d("SharedPrefsFlow", "observeString: change detected key=$key value=$v")
                    trySend(v)
                }
            }
            // 初期値を送る
            val initial = prefs.getString(key, default)
            Log.d("SharedPrefsFlow", "observeString: sending initial value for key=$key value=$initial")
            trySend(initial)
            prefs.registerOnSharedPreferenceChangeListener(listener)
            awaitClose {
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
                Log.d("SharedPrefsFlow", "observeString: listener unregistered for key=$key")
            }
        }.distinctUntilChanged()
    fun observeBoolean(prefs: SharedPreferences, key: String, default: Boolean = false): Flow<Boolean?> =
        callbackFlow {
            Log.d("SharedPrefsFlow", "observeBoolean: registering listener for key=$key on prefs=${prefs.hashCode()}")
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
                if (changedKey == key) {
                    val v = prefs.getBoolean(key, default)
                    Log.d("SharedPrefsFlow", "observeBoolean: change detected key=$key value=$v")
                    trySend(v)
                }
            }
            // 初期値を送る
            val initial = prefs.getBoolean(key, default)
            Log.d("SharedPrefsFlow", "observeBoolean: sending initial value for key=$key value=$initial")
            trySend(initial)
            prefs.registerOnSharedPreferenceChangeListener(listener)
            awaitClose {
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
                Log.d("SharedPrefsFlow", "observeBoolean: listener unregistered for key=$key")
            }
        }.distinctUntilChanged()



    fun observeInt(prefs: SharedPreferences, key: String, default: Int = 50): Flow<Int> =
        callbackFlow {
            Log.d("SharedPrefsFlow", "observeInt: registering listener for key=$key on prefs=${prefs.hashCode()}")
            // 安全に読み取るヘルパー。prefs に Int として保存されていない（例: String として保存されている）場合にも対応する
            fun readIntSafe(): Int {
                return try {
                    prefs.getInt(key, default)
                } catch (_: ClassCastException) {
                    // 別の型（主に String）で保存されているケースに対応
                    val s = prefs.getString(key, default.toString())
                    val parsed = s?.toIntOrNull()
                    if (parsed == null) {
                        Log.w("SharedPrefsFlow", "Failed to parse int for key=$key, returning default=$default (raw=$s)")
                        default
                    } else parsed
                }
            }

            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
                if (changedKey == key) {
                    val v = readIntSafe()
                    Log.d("SharedPrefsFlow", "observeInt: change detected key=$key value=$v")
                    trySend(v)
                }
            }
            // 初期値を送る（安全な読み取り）
            val initial = readIntSafe()
            Log.d("SharedPrefsFlow", "observeInt: sending initial value for key=$key value=$initial")
            trySend(initial)
            prefs.registerOnSharedPreferenceChangeListener(listener)
            awaitClose {
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
                Log.d("SharedPrefsFlow", "observeInt: listener unregistered for key=$key")
            }
        }.distinctUntilChanged()
}