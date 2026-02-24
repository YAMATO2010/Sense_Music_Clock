package jp.gr.java_conf.SenseMusicClock

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import jp.gr.java_conf.SenseMusicClock.databinding.ActivityListsBinding

class ListsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityListsBinding

    private var listId: Long = -1

    private var listType: ListType? = null

    enum class ListType {
        PLAYLIST,
        BLOCKLIST
    }

    companion object {
        val EXTRA_LIST_ID = "listId"
        val EXTRA_LIST_TYPE = "listType"

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.list_activity_barmenu, menu)
        return true


    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            // 戻るボタン（←）が押された時
            android.R.id.home -> {
                finish()
                true
            }
            // 作成したメニュー項目が押された時
            R.id.action_add -> {

                true
            }

            R.id.action_delete -> {

                true
            }

            else -> super.onOptionsItemSelected(item)
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListsBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)

        listId = intent.getLongExtra("listId", -1)
        if (listId == -1L) {
            Log.e("ListsActivity", "No list ID provided in intent")
            finish()
            return
        }

        listType = ListType.entries.find { it.name == intent.getStringExtra(EXTRA_LIST_TYPE)?.uppercase() }
        if (listType == null) {
            Log.e(
                "ListsActivity",
                "Invalid list type in intent: ${intent.getStringExtra(EXTRA_LIST_TYPE)}"
            )
            finish()
            return
        } else {


            when (listType) {
                ListType.PLAYLIST -> {
                    // プレイリストの内容を表示するためのコードをここに書く
                    Log.d("ListsActivity", "Displaying playlist with ID: $listId")
                    handlePlaylist()
                }

                ListType.BLOCKLIST -> {
                    // ブロックリストの内容を表示するためのコードをここに書く
                    Log.d("ListsActivity", "Displaying blocklist with ID: $listId")
                    handleBlocklist()
                }

                null -> {
                    // これは理論上起こらないはずですが、念のための安全策
                    Log.e("ListsActivity", "Unexpected null list type___理論上起こらないはずだったのに！！！！！！！！！！！！！！！！！")
                    finish()
                    return
                }
            }
        }


    }

    private fun handlePlaylist() {
        // プレイリストの内容を表示するためのコードをここに書く




    }
    private fun handleBlocklist() {
        // ブロックリストの内容を表示するためのコードをここに書く
    }
}