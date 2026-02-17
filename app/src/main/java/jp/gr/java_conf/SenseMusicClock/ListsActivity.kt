package jp.gr.java_conf.SenseMusicClock

import android.os.Bundle
import android.view.Menu
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import jp.gr.java_conf.SenseMusicClock.databinding.ActivityListsBinding

class ListsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityListsBinding

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        return super.onCreateOptionsMenu(menu)


    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListsBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)

        val listId = intent.getLongExtra("listId", -1)
        val isPlaylist = intent.getBooleanExtra("isPlaylist", false)


    }
}