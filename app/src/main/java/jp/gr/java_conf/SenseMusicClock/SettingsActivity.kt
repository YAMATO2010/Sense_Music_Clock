package jp.gr.java_conf.SenseMusicClock

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import jp.gr.java_conf.SenseMusicClock.Music.StorageAccessHelper

class SettingsActivity : AppCompatActivity() {

    private lateinit var storageAccessHelper: StorageAccessHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)



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
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            val pickPref: Preference? = findPreference("action_selectDirectory")
            pickPref?.setOnPreferenceClickListener {
                (activity as? SettingsActivity)?.launchDirectoryPicker()
                true
            }
        }
    }
}