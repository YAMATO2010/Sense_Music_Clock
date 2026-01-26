package jp.gr.java_conf.SenseMusicClock

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import jp.gr.java_conf.SenseMusicClock.Music.StorageAccessHelper

class SettingsActivity : AppCompatActivity() {

    private lateinit var storageAccessHelper: StorageAccessHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

        // StorageAccessHelper は Activity の onCreate で作成しておく
        storageAccessHelper = StorageAccessHelper(
            activity = this,
            onDirectoryPicked = { rel, uri ->
                // 設定画面で選択された後の処理はここで行う（ログだけ出しておく）
                Log.d("SettingsActivity", "directory picked: rel=$rel uri=$uri")
            },
            onPermissionGranted = {
                Log.d("SettingsActivity", "read audio permission granted")
            },
            onPermissionDenied = {
                Log.w("SettingsActivity", "read audio permission denied")
            }
        )

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    // SettingsFragment から呼ばれるランチャー
    fun launchDirectoryPicker(initialUri: Uri? = null) {
        // 必要なら権限チェックを挟む
        if (!storageAccessHelper.hasReadAudioPermission()) {
            storageAccessHelper.ensureReadAudioPermission()
            // 権限許可後にユーザがすぐ選ぶフローが必要なら、onPermissionGranted 内で launch を呼ぶ設計が必要
            return
        }
        storageAccessHelper.launchPickDirectory(initialUri)
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            // 明示的に名前付き SharedPreferences を使う（XML の属性に依存せず確実に同じ prefs を使用する）
            preferenceManager.sharedPreferencesName = getString(SHAREDPREFERENCES_NAME)
            preferenceManager.sharedPreferencesMode = android.content.Context.MODE_PRIVATE

            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            val pickPref: Preference? = findPreference("action_selectDirectory")
            pickPref?.setOnPreferenceClickListener {
                Log.d("SettingsFragment", "action_selectDirectory clicked")
                (activity as? SettingsActivity)?.launchDirectoryPicker()
                true
            }

            val reloadPref: Preference? = findPreference(getString(ReLoad_Tracks_KEY))
            reloadPref?.setOnPreferenceClickListener {
                val pref = preferenceManager.sharedPreferences
                val nowTF = !(pref?.getBoolean(getString(ReLoad_Tracks_KEY),false) ?: false)

                pref?.edit {
                    putBoolean(getString(ReLoad_Tracks_KEY), nowTF)
                }
                true


            }


        }
    }
}