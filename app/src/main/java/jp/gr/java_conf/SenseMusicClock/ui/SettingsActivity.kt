package jp.gr.java_conf.SenseMusicClock.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.preference.Preference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreference
import com.google.gson.Gson
import jp.gr.java_conf.SenseMusicClock.BackgroundResolver

import jp.gr.java_conf.SenseMusicClock.Music.Data.BlockList
import jp.gr.java_conf.SenseMusicClock.Music.Data.DBManager
import jp.gr.java_conf.SenseMusicClock.Music.Data.FileItem
import jp.gr.java_conf.SenseMusicClock.Music.Data.PlayList
import jp.gr.java_conf.SenseMusicClock.Music.LocalMusicFetcher
import jp.gr.java_conf.SenseMusicClock.Music.LocalMusicFetcher.toMediaItem
import jp.gr.java_conf.SenseMusicClock.Music.SleepTimerTimes
import jp.gr.java_conf.SenseMusicClock.Music.StorageAccessHelper
import jp.gr.java_conf.SenseMusicClock.Music.TargetDirectoryPrefJSONManager
import jp.gr.java_conf.SenseMusicClock.Music.sleepTimerTimesToMinutes
import jp.gr.java_conf.SenseMusicClock.Music.sleepTimerTimesToText
import jp.gr.java_conf.SenseMusicClock.MusicService
import jp.gr.java_conf.SenseMusicClock.PrefsManager
import jp.gr.java_conf.SenseMusicClock.R
import jp.gr.java_conf.SenseMusicClock.databinding.SettingsActivityBinding
import jp.gr.java_conf.SenseMusicClock.launchClearBackgroundImageFileDialog
import jp.gr.java_conf.SenseMusicClock.launchSelectBackgroundImageDialog
import jp.gr.java_conf.SenseMusicClock.saveToInternalStorage
import jp.gr.java_conf.SenseMusicClock.showEditTextDialog
import jp.gr.java_conf.SenseMusicClock.showBlockSelectDialog
import jp.gr.java_conf.SenseMusicClock.showPlaylistSelectDialog
import jp.gr.java_conf.SenseMusicClock.toFileItem
import jp.gr.java_conf.SenseMusicClock.ui.list.ListsActivity
import jp.gr.java_conf.SenseMusicClock.utilDialog
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
        private val gson = Gson()

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
                                        val Summarys: List<LocalMusicFetcher.MediaStoreAudioSummary> =
                                            LocalMusicFetcher.SafeLocalMusicFromAppDir(
                                                requireContext().applicationContext.contentResolver,
                                                queryArgs
                                            )

                                        val sortedList: List<FileItem> =
                                            Summarys.sortedBy { summary ->
                                                items.any { item ->
                                                    summary.displayName == item.fileName && summary.relativePath?.contains(
                                                        item.relativePath
                                                    ) ?: false
                                                }

                                            }.mapNotNull { item ->
                                                item.toMediaItem().toFileItem()
                                            }
                                        DBManager.addPlaylistAndItems(
                                            context = requireContext(),
                                            playlistName = DisplayName
                                                ?: "インポートされたプレイリスト",
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

            val pickPref: Preference? = findPreference("action_selectDirectory_add")
            pickPref?.setOnPreferenceClickListener {
                Log.d("SettingsFragment", "action_selectDirectory clicked")
                (activity as? SettingsActivity)?.launchDirectoryPicker()
                true
            }
            val dirDeletePref: Preference? = findPreference("action_selectDirectory_delete")
            dirDeletePref?.setOnPreferenceClickListener {
                lifecycleScope.launch {

                    val dirs = TargetDirectoryPrefJSONManager.getAll(requireContext())

                    context?.utilDialog(
                        "再生ディレクトリの削除",
                        dirs,
                        dirs.toTypedArray()
                    ) { selected ->

                        lifecycleScope.launch {
                            val removed: Boolean =
                                TargetDirectoryPrefJSONManager.remove(selected, requireContext())
                            if (removed) {
                                Toast.makeText(
                                    requireContext(),
                                    "「$selected」を再生ディレクトリから削除しました。",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    requireContext(),
                                    "「$selected」の削除に失敗しました。",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                    }

                }
                true
            }


            val reloadPref: Preference? = findPreference("reLoad_Tracks")
            reloadPref?.setOnPreferenceClickListener {
                lifecycleScope.launch {
                    PrefsManager.setReloadTracks_reverse_andGet(requireContext())
                }
                true


            }

            val sleepTimerPref: Preference? = findPreference("action_sleepTimer")
            sleepTimerPref?.setOnPreferenceClickListener {
                val timerLists = listOf(
                    SleepTimerTimes.OFF,
                    SleepTimerTimes.MIN_5,
                    SleepTimerTimes.MIN_15,
                    SleepTimerTimes.MIN_30,
                    SleepTimerTimes.HOUR_1,
                    SleepTimerTimes.HOUR_2,
                    SleepTimerTimes.HOUR_5
                )
                val timerListTexts = timerLists.map { sleepTimerTimesToText[it] ?: it.name }

                val activity = requireActivity() as AppCompatActivity
                val executor = ContextCompat.getMainExecutor(activity)

                AlertDialog.Builder(activity)
                    .setTitle("スリープタイマー")
                    .setItems(
                        timerListTexts.toTypedArray()
                    ) { _, which ->

                        val selectedTimer = timerLists[which]
                        val minutes =
                            sleepTimerTimesToMinutes[selectedTimer] ?: 0

                        val token = SessionToken(
                            activity,
                            ComponentName(
                                activity,
                                MusicService::class.java
                            )
                        )

                        val controllerFuture =
                            MediaController.Builder(activity, token)
                                .buildAsync()

                        controllerFuture.addListener(
                            {
                                val mediaController = try {
                                    controllerFuture.get()
                                } catch (e: Exception) {
                                    Log.e(
                                        "SettingsFragment/SleepTimer",
                                        "Failed to connect to MusicService",
                                        e
                                    )

                                    Toast.makeText(
                                        activity,
                                        "音楽サービスへの接続に失敗しました。",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    return@addListener
                                }

                                val command = SessionCommand(
                                    MusicService.CUSTOM_ACTION_SLEEP_TIMER_SET,
                                    Bundle.EMPTY
                                )

                                val args =
                                    if (minutes > 0) {
                                        Bundle().apply {
                                            putInt(
                                                MusicService.SLEEP_TIMER_DURATION_MINUTES,
                                                minutes
                                            )
                                        }
                                    } else {
                                        Bundle.EMPTY
                                    }

                                val resultFuture =
                                    mediaController.sendCustomCommand(
                                        command,
                                        args
                                    )

                                resultFuture.addListener(
                                    {
                                        try {
                                            val result = resultFuture.get()

                                            if (
                                                result.resultCode ==
                                                SessionResult.RESULT_SUCCESS
                                            ) {
                                                val message =
                                                    if (minutes > 0) {
                                                        "スリープタイマーを${minutes}分に設定しました。"
                                                    } else {
                                                        "スリープタイマーをオフにしました。"
                                                    }

                                                Toast.makeText(
                                                    activity,
                                                    message,
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                Log.e(
                                                    "SettingsFragment/SleepTimer",
                                                    "Sleep timer command failed: " +
                                                            "resultCode=${result.resultCode}"
                                                )

                                                Toast.makeText(
                                                    activity,
                                                    "スリープタイマーの設定に失敗しました。",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        } catch (e: Exception) {
                                            Log.e(
                                                "SettingsFragment/SleepTimer",
                                                "Failed to send sleep timer command",
                                                e
                                            )

                                            Toast.makeText(
                                                activity,
                                                "スリープタイマーの設定に失敗しました。",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } finally {
                                            mediaController.release()
                                        }
                                    },
                                    executor
                                )
                            },
                            executor
                        )
                    }
                    .setNegativeButton("キャンセル", null)
                    .show()
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
                                    PlayList.CURRENT_REMOVAL_ID,
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
                                PlayList.CURRENT_REMOVAL_ID -> {
                                    Log.d(
                                        "LIST_/SettingsFragment",
                                        "Dummy playlist selected, ignoring"
                                    )
                                    lifecycleScope.launch {

                                        PrefsManager.setCurrentPlaylistId(
                                            requireContext(),
                                            PlayList.CURRENT_REMOVAL_ID
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
                                    PlayList.CURRENT_REMOVAL_ID,
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
                                PlayList.CURRENT_REMOVAL_ID -> {
                                    Log.d(
                                        "LIST_/SettingsFragment",
                                        "Dummy blocklist selected, ignoring"
                                    )
                                    lifecycleScope.launch {

                                        PrefsManager.setCurrentBlocklistId(
                                            requireContext(),
                                            PlayList.CURRENT_REMOVAL_ID
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

            val playAddedAtDeskPref: Preference? = findPreference("action_play_added_at_Desc")
            playAddedAtDeskPref?.setOnPreferenceClickListener {
                lifecycleScope.launch {
                    PrefsManager.setCurrentPlaylistId(requireContext(), PlayList.ADDED_AT_DESC_ID)
                }
                true
            }
            val playDefaultPref: Preference? = findPreference("action_play_default")
            playDefaultPref?.setOnPreferenceClickListener {
                lifecycleScope.launch {
                    PrefsManager.setCurrentPlaylistId(requireContext(), PlayList.CURRENT_REMOVAL_ID)
                }
                true
            }

            val switchSettingsPref: Preference? = findPreference("action_switchSettings")
            switchSettingsPref?.setOnPreferenceClickListener {
                showSwitchSettingsDialog()
                true
            }

            val addSettingsPref: Preference? = findPreference("action_switchSettings_add")
            addSettingsPref?.setOnPreferenceClickListener {
                showSaveSettingsDialog()
                true
            }

            val deleteSettingsPref: Preference? = findPreference("action_switchSettings_delete")
            deleteSettingsPref?.setOnPreferenceClickListener {
                showDeleteSettingsDialog()
                true
            }

        }

        private fun showSaveSettingsDialog() {
            val activity = requireActivity() as? AppCompatActivity ?: return
            activity.showEditTextDialog(
                title = "設定名",
                positiveButtonTitle = "保存",
                initialText = DEFAULT_SETTINGS_PROFILE_NAME
            ) { input ->
                val appContext = requireContext().applicationContext
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            val profile = PrefsManager.getSettingsProfile(appContext)
                            val file = createUniqueSettingsProfileFile(appContext, input)
                            file.writeText(gson.toJson(profile))
                            file
                        }
                    }.onSuccess { file ->
                        Toast.makeText(
                            requireContext(),
                            "設定を「${file.nameWithoutExtension}」として保存しました。",
                            Toast.LENGTH_SHORT
                        ).show()
                    }.onFailure { e ->
                        Log.e("SettingsFragment", "Failed to save settings profile", e)
                        Toast.makeText(
                            requireContext(),
                            "設定の保存に失敗しました。",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        private fun showSwitchSettingsDialog() {
            val files = getSettingsProfileFiles(requireContext())
            if (files.isEmpty()) {
                Toast.makeText(requireContext(), "保存済みの設定がありません。", Toast.LENGTH_SHORT).show()
                return
            }

            requireContext().utilDialog(
                "設定の切り替え",
                files,
                files.map { it.nameWithoutExtension }.toTypedArray()
            ) { file ->
                val appContext = requireContext().applicationContext
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching {
                        val profile = withContext(Dispatchers.IO) {
                            val json = file.readText()
                            gson.fromJson(json, PrefsManager.SettingsProfile::class.java)
                                ?: throw IllegalArgumentException("Invalid settings profile JSON")
                        }
                        PrefsManager.saveSettingsProfile(appContext, profile)
                        profile
                    }.onSuccess { profile ->
                        applySettingsProfileToUi(profile)
                        Toast.makeText(
                            requireContext(),
                            "設定を「${file.nameWithoutExtension}」に切り替えました。",
                            Toast.LENGTH_SHORT
                        ).show()
                    }.onFailure { e ->
                        Log.e("SettingsFragment", "Failed to switch settings profile", e)
                        Toast.makeText(
                            requireContext(),
                            "設定の切り替えに失敗しました。",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        private fun applySettingsProfileToUi(profile: PrefsManager.SettingsProfile) {
            findPreference<SwitchPreference>("tile_title_display")?.isChecked =
                profile.tileTitleDisplay
            findPreference<SwitchPreference>("playMode_loop")?.isChecked = profile.playModeLoop
            findPreference<SwitchPreference>("is_shuffle")?.isChecked = profile.isShuffle
            findPreference<SwitchPreference>("is_widget_background")?.isChecked =
                profile.isWidgetBackground
            findPreference<SwitchPreference>("is_random_background")?.isChecked =
                profile.isRandomBackground
            findPreference<SwitchPreference>("useCurrentShuffleMode_added_at_Desc")?.isChecked =
                profile.useCurrentShuffleModeAddedAtDesc
            findPreference<SwitchPreference>("isBlock_added_at_Desc")?.isChecked =
                profile.isBlockAddedAtDesc
            findPreference<SwitchPreference>("isFilterByDir_added_at_Desc")?.isChecked =
                profile.isFilterByDirAddedAtDesc

            findPreference<SeekBarPreference>("max_load_tracks_added_at_Desc")?.value =
                profile.maxLoadTracksAddedAtDesc
        }

        private fun showDeleteSettingsDialog() {
            val files = getSettingsProfileFiles(requireContext())
            if (files.isEmpty()) {
                Toast.makeText(requireContext(), "保存済みの設定がありません。", Toast.LENGTH_SHORT).show()
                return
            }

            requireContext().utilDialog(
                "設定の削除",
                files,
                files.map { it.nameWithoutExtension }.toTypedArray()
            ) { file ->
                AlertDialog.Builder(requireContext())
                    .setTitle("設定の削除")
                    .setMessage("「${file.nameWithoutExtension}」を削除しますか？")
                    .setPositiveButton("削除") { _, _ ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            val deleted = withContext(Dispatchers.IO) {
                                file.isFile && file.delete()
                            }
                            val message = if (deleted) {
                                "設定を削除しました。"
                            } else {
                                "設定の削除に失敗しました。"
                            }
                            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("キャンセル", null)
                    .show()
            }
        }

        private fun getSettingsProfileFiles(context: Context): List<File> {
            val dir = getSettingsProfileDir(context)
            return dir.listFiles { file ->
                file.isFile && file.extension.equals("json", ignoreCase = true)
            }?.sortedBy { it.nameWithoutExtension } ?: emptyList()
        }

        private fun createUniqueSettingsProfileFile(context: Context, inputName: String): File {
            val dir = getSettingsProfileDir(context)
            val baseName = sanitizeSettingsProfileName(inputName)
            var file = File(dir, "$baseName.json")
            var index = 1
            while (file.exists()) {
                file = File(dir, "$baseName($index).json")
                index++
            }
            return file
        }

        private fun getSettingsProfileDir(context: Context): File {
            return File(context.filesDir, SETTINGS_PROFILE_DIR).apply {
                if (!exists()) mkdirs()
            }
        }

        private fun sanitizeSettingsProfileName(inputName: String): String {
            val trimmedName = inputName.trim().ifBlank { DEFAULT_SETTINGS_PROFILE_NAME }
            val nameWithoutExtension = if (trimmedName.endsWith(".json", ignoreCase = true)) {
                trimmedName.dropLast(".json".length).trim()
            } else {
                trimmedName
            }
            return nameWithoutExtension
                .replace(Regex("""[\\/:*?"<>|]"""), "_")
                .ifBlank { DEFAULT_SETTINGS_PROFILE_NAME }
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

        companion object {
            private const val SETTINGS_PROFILE_DIR = "profiles"
            private const val DEFAULT_SETTINGS_PROFILE_NAME = "Prefs"
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

                    "max_load_tracks_added_at_Desc" -> {
                        PrefsManager.setMaxLoadTracksAddedAtDesk(context, value)
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
                    "max_load_tracks_added_at_Desc" -> PrefsManager.getMaxLoadTracksAddedAtDesk(
                        context
                    )

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
                    "is_widget_background" -> PrefsManager.setWidgetBackground(context, value)
                    "is_random_background" -> PrefsManager.setRandomBackground(context, value)
                    "useCurrentShuffleMode_added_at_Desc" -> PrefsManager.setUseCurrentShuffleModeAddedAtDesc(
                        context,
                        value
                    )

                    "isBlock_added_at_Desc" -> PrefsManager.setIsBlockAddedAtDesc(context, value)
                    "isFilterByDir_added_at_Desc" -> PrefsManager.setIsFilterByDirAddedAtDesc(
                        context,
                        value
                    )

                    else -> {
                        Log.w("MyDataStore", "Unknown key for putBoolean: $key")
                    }
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
                    "is_widget_background" -> PrefsManager.getWidgetBackground(context)
                    "is_random_background" -> PrefsManager.getRandomBackground(context)
                    "useCurrentShuffleMode_added_at_Desc" -> PrefsManager.getUseCurrentShuffleModeAddedAtDesc(
                        context
                    )

                    "isBlock_added_at_Desc" -> PrefsManager.getIsBlockAddedAtDesc(context)
                    "isFilterByDir_added_at_Desc" -> PrefsManager.getIsFilterByDirAddedAtDesc(
                        context
                    )

                    else -> defValue
                }
            }
        }


    }

}
