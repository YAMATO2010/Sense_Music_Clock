package jp.gr.java_conf.SenseMusicClock.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import jp.gr.java_conf.SenseMusicClock.BackgroundResolver
import jp.gr.java_conf.SenseMusicClock.DUMMY_PLAYLIST_REMOVAL_ID
import jp.gr.java_conf.SenseMusicClock.Music.BottomController
import jp.gr.java_conf.SenseMusicClock.Music.Data.BlockList
import jp.gr.java_conf.SenseMusicClock.Music.Data.DBManager
import jp.gr.java_conf.SenseMusicClock.Music.Data.FileItem
import jp.gr.java_conf.SenseMusicClock.Music.Data.PlayList
import jp.gr.java_conf.SenseMusicClock.Music.LocalMusicFetcher
import jp.gr.java_conf.SenseMusicClock.Music.LocalMusicFetcher.toMediaItem
import jp.gr.java_conf.SenseMusicClock.Music.StorageAccessHelper
import jp.gr.java_conf.SenseMusicClock.PrefsManager
import jp.gr.java_conf.SenseMusicClock.R
import jp.gr.java_conf.SenseMusicClock.databinding.SettingsActivityBinding
import jp.gr.java_conf.SenseMusicClock.launchClearBackgroundImageFileDialog
import jp.gr.java_conf.SenseMusicClock.launchSelectBackgroundImageDialog
import jp.gr.java_conf.SenseMusicClock.saveToInternalStorage
import jp.gr.java_conf.SenseMusicClock.showBlockSelectDialog
import jp.gr.java_conf.SenseMusicClock.showPlaylistSelectDialog
import jp.gr.java_conf.SenseMusicClock.toFileItem
import jp.gr.java_conf.SenseMusicClock.ui.list.ListsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private var storageAccessHelper: StorageAccessHelper? = null


    private lateinit var binding: SettingsActivityBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SettingsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
        if (!(storageAccessHelper?.hasReadAudioPermission() ?: true)) {
            storageAccessHelper?.ensureReadAudioPermission()
            // 権限許可後にユーザがすぐ選ぶフローが必要なら、onPermissionGranted 内で launch を呼ぶ設計が必要
            return
        }
        storageAccessHelper?.launchPickDirectory(initialUri)
    }

    override fun onStart() {
        super.onStart()

        binding.bottomController.initialize(this)
    }

    override fun onStop() {
        super.onStop()
        binding.bottomController.release()
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

            val openM3ULauncher =
                registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) {
                        Log.d("SettingsFragment", "Picked M3U file: $uri")
                        // ここで M3U ファイルの処理を行う（例: プレイリストの読み込み）

                        lifecycleScope.launch {

                            val DisplayName = loadM3U_RPathAndName(uri)

                            try {
                                context?.contentResolver?.openInputStream(uri)?.bufferedReader()
                                    ?.use { reader ->
                                        val lines = reader.readLines()

                                        val items: List<FileItem> = lines.mapNotNull {
                                            if (it.first() == '#') {
                                                // コメント行は無視
                                                null
                                            } else {
                                                val name = it.substringAfterLast('/')
                                                val rPath = it.substringBeforeLast('/')
                                                val relative: String = File(rPath).normalize().path
                                                FileItem(
                                                    relative,
                                                    name
                                                )

                                            }
                                        }
                                        val (selection, selectionArgs) = LocalMusicFetcher.path_selection_RPath_LIKE(
                                            items
                                        )
                                        val queryArgs = LocalMusicFetcher.createQueryArgs(
                                            selection,
                                            selectionArgs
                                        )
                                        val Summarys : List<LocalMusicFetcher.MediaStoreAudioSummary> = LocalMusicFetcher.SafeLocalMusicFromAppDir(requireContext().applicationContext.contentResolver, queryArgs)

                                        val sortedList : List<FileItem> = Summarys.sortedBy { summary ->
                                            items.any{ item ->  summary.displayName == item.fileName && summary.relativePath?.contains(item.relativePath) ?: false }

                                        }.mapNotNull { item ->
                                            item.toMediaItem().toFileItem()
                                        }
                                        DBManager.addPlaylistAndItems(
                                            context = requireContext(),
                                            playlistName = DisplayName ?: "インポートされたプレイリスト",
                                            sortedList
                                        )

                                        // ここで lines を解析してプレイリストに変換する処理を実装
                                    }
                            } catch (e: Exception) {
                                Log.e("SettingsFragment/M3U", "Failed to read M3U file", e)
                            }
                        }
                    } else {
                        Log.d("SettingsFragment", "M3U file pick cancelled")
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


            val playlistPref: Preference? = findPreference("action_playList")
            playlistPref?.setOnPreferenceClickListener {


                val appCompatActivity = requireActivity() as? AppCompatActivity

                appCompatActivity?.let {

                    viewLifecycleOwner.lifecycleScope.launch {

                        val playlists =
                            listOf(
                                PlayList(
                                    DUMMY_PLAYLIST_REMOVAL_ID,
                                    "デフォルト",
                                )
                            ) + withContext(
                                Dispatchers.IO
                            ) {
                                DBManager.loadPlaylist(requireContext())
                            }
                        Log.d(
                            "LIST_/SettingsFragment/loadPlaylists",
                            "Loaded playlists count=${playlists.size}"
                        )
                        if (activity?.isFinishing ?: false || activity?.isDestroyed ?: false) return@launch
                        it.showPlaylistSelectDialog(playlists) { playlistId ->
                            // プレイリストが選択されたときの処理
                            Log.d("SettingsFragment", "Selected playlist ID: $playlistId")
                            when (playlistId) {
                                DUMMY_PLAYLIST_REMOVAL_ID -> {
                                    Log.d(
                                        "LIST_/SettingsFragment",
                                        "Dummy playlist selected, ignoring"
                                    )
                                    lifecycleScope.launch {

                                        PrefsManager.setCurrentPlaylistId(
                                            requireContext(),
                                            DUMMY_PLAYLIST_REMOVAL_ID
                                        )
                                    }

                                    return@showPlaylistSelectDialog
                                }

                                else -> {
                                    val intent =
                                        Intent(activity, ListsActivity::class.java).apply {
                                            putExtra(
                                                ListsActivity.EXTRA_LIST_TYPE,
                                                ListsActivity.ListType.PLAYLIST.name
                                            )
                                            putExtra(ListsActivity.EXTRA_LIST_ID, playlistId)

                                        }
                                    Log.d(
                                        "LIST_/SettingsFragment/launchList",
                                        "Launching ListsActivity for playlist ID: $playlistId"
                                    )
                                    it.startActivity(intent)
                                }
                            }

                        }
                    }


                }

                true
            }

            val blocklistPref: Preference? = findPreference("action_blockList")
            blocklistPref?.setOnPreferenceClickListener {


                val appCompatActivity = requireActivity() as? AppCompatActivity
                appCompatActivity?.let {

                    viewLifecycleOwner.lifecycleScope.launch {

                        val blocklists =
                            listOf(
                                BlockList(
                                    DUMMY_PLAYLIST_REMOVAL_ID,
                                    "ブロック無しモード",
                                )
                            ) + withContext(
                                Dispatchers.IO
                            ) {
                                DBManager.loadBlocklist(requireContext())
                            }
                        Log.d(
                            "LIST_/SettingsFragment/loadPlaylists",
                            "Loaded playlists count=${blocklists.size}"
                        )
                        if (activity?.isFinishing ?: false || activity?.isDestroyed ?: false) return@launch
                        it.showBlockSelectDialog(blocklists) { blocklistId ->
                            // プレイリストが選択されたときの処理
                            Log.d("SettingsFragment", "Selected playlist ID: $blocklistId")
                            when (blocklistId) {
                                DUMMY_PLAYLIST_REMOVAL_ID -> {
                                    Log.d(
                                        "LIST_/SettingsFragment",
                                        "Dummy blocklist selected, ignoring"
                                    )
                                    lifecycleScope.launch {

                                        PrefsManager.setCurrentBlocklistId(
                                            requireContext(),
                                            DUMMY_PLAYLIST_REMOVAL_ID
                                        )
                                    }

                                    return@showBlockSelectDialog
                                }

                                else -> {
                                    val intent =
                                        Intent(activity, ListsActivity::class.java).apply {
                                            putExtra(
                                                ListsActivity.EXTRA_LIST_TYPE,
                                                ListsActivity.ListType.BLOCKLIST.name
                                            )
                                            putExtra(ListsActivity.EXTRA_LIST_ID, blocklistId)

                                        }
                                    Log.d(
                                        "LIST_/SettingsFragment/launchList",
                                        "Launching ListsActivity for playlist ID: $blocklistId"
                                    )
                                    it.startActivity(intent)
                                }
                            }

                        }
                    }


                }
                true
            }

            val loadPlaylistPref: Preference? = findPreference("action_load_playList")
            loadPlaylistPref?.setOnPreferenceClickListener {
                openM3ULauncher.launch(
                    arrayOf(
                        "application/x-mpegurl",
                        "audio/x-mpegurl",
                        "application/vnd.apple.mpegurl"
                    )
                )


                true
            }


        }

        suspend fun loadM3U_RPathAndName(uri: Uri): String? {
            return withContext(Dispatchers.IO) {

                val projection = arrayOf(
                    MediaStore.Files.FileColumns.DISPLAY_NAME,

                    )
                context?.contentResolver?.query(uri, projection, null, null, null)?.use {
                    if (it.moveToFirst()) {
                        val nameIdx =
                            it.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)

                        val name = if (nameIdx != -1) it.getString(nameIdx) else null

                        Log.d("SettingsFragment/loadM3U_RPathAndName", "name=$name,")
                        name
                    } else {
                        Log.w(
                            "SettingsFragment/loadM3U_RPathAndName",
                            "Cursor is empty for uri=$uri"
                        )
                        null
                    }

                }
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        storageAccessHelper = null
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
                    "is_shuffle" -> PrefsManager.setIsShuffle(context, value)
                }
            }

        }

        override fun getBoolean(key: String?, defValue: Boolean): Boolean {
            Log.d("MyDataStore", "getBoolean called with key=$key, defValue=$defValue")
            return runBlocking {
                when (key) {
                    "tile_title_display" -> PrefsManager.getTileTitleDisplay(context)
                    "playMode_loop" -> PrefsManager.getPlayModeLoop(context)
                    "is_shuffle" -> PrefsManager.getIsShuffle(context)
                    else -> defValue
                }
            }
        }


    }

}