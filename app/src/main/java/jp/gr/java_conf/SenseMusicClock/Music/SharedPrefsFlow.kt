package jp.gr.java_conf.SenseMusicClock.Music

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.Flow

object SharedPrefsFlow {
    fun observeString(prefs: SharedPreferences, key: String, default: String? = null): Flow<String?> =
        callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
                if (changedKey == key) {
                    trySend(prefs.getString(key, default))
                }
            }
            // 初期値を送る
            trySend(prefs.getString(key, default))
            prefs.registerOnSharedPreferenceChangeListener(listener)
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }.distinctUntilChanged()
}