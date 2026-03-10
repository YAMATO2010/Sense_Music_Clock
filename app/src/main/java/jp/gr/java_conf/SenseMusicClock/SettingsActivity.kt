package jp.gr.java_conf.SenseMusicClock

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import jp.gr.java_conf.SenseMusicClock.Music.Data.PlayList
import jp.gr.java_conf.SenseMusicClock.Music.StorageAccessHelper
import jp.gr.java_conf.SenseMusicClock.Music.list.ListsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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




            val MyDataStore = MyDataStore(requireContext(), lifecycleScope)
            preferenceManager.preferenceDataStore = MyDataStore
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            val pickPref: Preference? = findPreference("action_selectDirectory")
            pickPref?.setOnPreferenceClickListener {
                Log.d("SettingsFragment", "action_selectDirectory clicked")
                (activity as? SettingsActivity)?.launchDirectoryPicker()
                true
            }

            val reloadPref: Preference? = findPreference("reLoad_Tracks")
            reloadPref?.setOnPreferenceClickListener {
                lifecycleScope.launch {
                    PrefsManager.setReloadTracks_reverse_andGet(requireContext())
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

                val activity = requireActivity() as? AppCompatActivity

                activity?.launchSelectBackgroundImageDialog()

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

    class MyDataStore(
        private val context: Context,
        private val scope: CoroutineScope
    ) : PreferenceDataStore() {

        // --- 保存処理 (XML -> DataStore) ---
        override fun putInt(key: String, value: Int) {
            Log.d("MyDataStore", "putInt called with key=$key, value=$value")
            scope.launch {
                when (key) {
                    "volume_adjustment" -> {
                        PrefsManager.setVolumeAdjustment(context, value)
                    }

                }

            }
        }

        // --- 読み込み処理 (DataStore -> XML) ---
        override fun getInt(key: String, defaultValue: Int): Int {
            // XML側は「同期」を求めるので、runBlocking等で一瞬待つ必要がある
            Log.d("MyDataStore", "getInt called with key=$key, defaultValue=$defaultValue")
            return runBlocking {

                when (key) {
                    "volume_adjustment" -> PrefsManager.getVolumeAdjustment(context)
                    else -> defaultValue
                }
            }
        }

        override fun putBoolean(key: String?, value: Boolean) {
            Log.d("MyDataStore", "putBoolean called with key=$key, value=$value")

            runBlocking {

                when (key) {
                    "tile_title_display" -> PrefsManager.setTileTitleDisplay(context, value)
                    "playMode_loop" -> PrefsManager.setPlayModeLoop(context, value)
                }
            }

        }

        override fun getBoolean(key: String?, defValue: Boolean): Boolean {
            Log.d("MyDataStore", "getBoolean called with key=$key, defValue=$defValue")
            return runBlocking {
                when (key) {
                    "tile_title_display" -> PrefsManager.getTileTitleDisplay(context)
                    "playMode_loop" -> PrefsManager.getPlayModeLoop(context)
                    else -> defValue
                }
            }
        }

        // StringやBooleanなど、他の型も同様に override する
    }

}