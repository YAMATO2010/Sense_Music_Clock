package jp.gr.java_conf.SenseMusicClock

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dummyListId
import jp.gr.java_conf.SenseMusicClock.Music.Data.PlayList
import jp.gr.java_conf.SenseMusicClock.Music.StorageAccessHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

            val openImageLauncher =
                registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) {
                        // 永続権限を取得

                        // 内部ストレージに保存
                        val savedFile =
                            context?.saveToInternalStorage(uri, BackgroundResolver.BACKGROUNDS_PATH)
                        Log.d("SettingsFragment", "Picked image saved to $savedFile")
                    }
                }

            preferenceManager.sharedPreferencesName = getString(PrefsManager.SHAREDPREFERENCES_NAME)
            preferenceManager.sharedPreferencesMode = MODE_PRIVATE

            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            val pickPref: Preference? = findPreference("action_selectDirectory")
            pickPref?.setOnPreferenceClickListener {
                Log.d("SettingsFragment", "action_selectDirectory clicked")
                (activity as? SettingsActivity)?.launchDirectoryPicker()
                true
            }

            val reloadPref: Preference? = findPreference(getString(PrefsManager.ReLoad_Tracks_KEY))
            reloadPref?.setOnPreferenceClickListener {
                val pref = preferenceManager.sharedPreferences
                val nowTF = !(pref?.getBoolean(getString(PrefsManager.ReLoad_Tracks_KEY), false) ?: false)

                pref?.edit {
                    putBoolean(getString(PrefsManager.ReLoad_Tracks_KEY), nowTF)
                }
                true


            }

            val pickImageAddPref: Preference? = findPreference("background_add")
            pickImageAddPref?.setOnPreferenceClickListener {
                Log.d("SettingsFragment", "background_add clicked")
                // 画像ファイル選択ランチャーを起動
                openImageLauncher.launch(arrayOf("image/*"))
                true

            }

            val selectImageFilePref: Preference? = findPreference("background_select")
            selectImageFilePref?.setOnPreferenceClickListener {

                requireActivity().launchSelectBackgroundImageDialog()
                true
            }

            val clearImageFilePref: Preference? = findPreference("background_clear")
            clearImageFilePref?.setOnPreferenceClickListener {

                val appCompatActivity = requireActivity() as? AppCompatActivity
                appCompatActivity?.launchClearBackgroundImageFileDialog()
                true

            }


            val playlistPref: Preference? = findPreference("action_playlist")
            playlistPref?.setOnPreferenceClickListener {


                val appCompatActivity = requireActivity() as? AppCompatActivity

                appCompatActivity?.let {

                    viewLifecycleOwner.lifecycleScope.launch {

                        val playlists =
                            listOf(PlayList(dummyListId, "")) + withContext(Dispatchers.IO) {
                                it.loadPlaylist()
                            }
                        if (activity?.isFinishing ?: false || activity?.isDestroyed ?: false) return@launch
                        it.showPlaylistSelectDialog(playlists) { playlistId ->
                            // プレイリストが選択されたときの処理
                            Log.d("SettingsFragment", "Selected playlist ID: $playlistId")

                            val intent = Intent(activity, ListsActivity::class.java).apply {
                                putExtra(
                                    ListsActivity.EXTRA_LIST_TYPE,
                                    ListsActivity.ListType.PLAYLIST.name
                                )
                                putExtra(ListsActivity.EXTRA_LIST_ID, playlistId)
                            }
                            it.startActivity(intent)

                        }
                    }


                }

                true
            }

            val blocklistPref: Preference? = findPreference("action_blocklist")
            blocklistPref?.setOnPreferenceClickListener {


                val appCompatActivity = requireActivity() as? AppCompatActivity
                val blockList = appCompatActivity?.let {
                    lifecycleScope.launch {

                        withContext(Dispatchers.IO) {
                            it
                        }
                    }
                }
                true
            }


        }
    }
}