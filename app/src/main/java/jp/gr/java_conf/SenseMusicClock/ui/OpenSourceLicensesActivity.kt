package jp.gr.java_conf.SenseMusicClock.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import jp.gr.java_conf.SenseMusicClock.ui.ui.theme.SenseMusicClockTheme

class OpenSourceLicensesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SenseMusicClockTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val libraries by produceLibraries()

                    Column(modifier = Modifier.fillMaxSize()) {

                        LibrariesContainer(
                            libraries = libraries,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = { finish() }, modifier = Modifier.weight(0.1f).padding(8.dp)) {
                            Text(text = "閉じる")
                        }



                    }
                }

            }
        }
    }
}

