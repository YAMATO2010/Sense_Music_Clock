package jp.gr.java_conf.SenseMusicClock

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.gr.java_conf.SenseMusicClock.ui.SettingsActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class AppLanguageTest {
    @Test
    fun settingsLanguageSurvivesRecreationWithoutChangingMusicSettings() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val originalSettings = runBlocking { PrefsManager.getSettingsProfile(context) }

        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            var originalLocales = AppCompatDelegate.getApplicationLocales()
            scenario.onActivity { originalLocales = AppCompatDelegate.getApplicationLocales() }
            try {
                for ((language, expectedTitle) in listOf("en" to "Sleep timer", "ja" to "スリープタイマー")) {
                    scenario.onActivity { activity ->
                        val fragment = activity.supportFragmentManager
                            .findFragmentById(R.id.settings) as SettingsActivity.SettingsFragment
                        assertTrue(fragment.findPreference<ListPreference>("app_language")!!
                            .callChangeListener(language))
                    }
                    instrumentation.waitForIdleSync()
                    scenario.recreate()
                    scenario.onActivity { activity ->
                        val fragment = activity.supportFragmentManager
                            .findFragmentById(R.id.settings) as SettingsActivity.SettingsFragment
                        assertEquals(language, AppCompatDelegate.getApplicationLocales()[0]?.language)
                        assertEquals(language, fragment.findPreference<ListPreference>("app_language")!!.value)
                        assertEquals(expectedTitle, fragment.findPreference<Preference>("action_sleepTimer")!!.title)
                        assertFalse(fragment.findPreference<ListPreference>("app_language")!!
                            .callChangeListener("unsupported"))
                    }
                    assertEquals(originalSettings, runBlocking { PrefsManager.getSettingsProfile(context) })
                }
                scenario.onActivity { activity ->
                    val fragment = activity.supportFragmentManager
                        .findFragmentById(R.id.settings) as SettingsActivity.SettingsFragment
                    assertTrue(fragment.findPreference<ListPreference>("app_language")!!
                        .callChangeListener("system"))
                }
                instrumentation.waitForIdleSync()
                scenario.recreate()
                scenario.onActivity { activity ->
                    assertTrue(AppCompatDelegate.getApplicationLocales().isEmpty)
                    val fragment = activity.supportFragmentManager
                        .findFragmentById(R.id.settings) as SettingsActivity.SettingsFragment
                    assertEquals("system", fragment.findPreference<ListPreference>("app_language")!!.value)
                }
            } finally {
                instrumentation.runOnMainSync {
                    AppCompatDelegate.setApplicationLocales(originalLocales)
                }
                instrumentation.waitForIdleSync()
            }
        }
    }

    @Test
    fun translatedLabelsKeepTheirArgumentsAndStablePreferenceKeys() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for ((language, expectedMessage) in listOf("ja" to "スリープタイマーを15分に設定しました。", "en" to "Sleep timer set to 15 minutes.")) {
            val configuration = Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language))
            }
            val localized = context.createConfigurationContext(configuration)
            assertEquals(expectedMessage, localized.getString(R.string.sleep_timer_set, 15))
            assertEquals("volume_adjustment", localized.getString(R.string.VOLUME_ADJUSTMENT))
            assertEquals("smc_prefs", localized.getString(R.string.SHAREDPREFERENCES_NAME))
            assertTrue(localized.getString(R.string.profile_saved, "My settings").contains("My settings"))
            assertEquals(
                if (language == "ja") "15分" else "15 minutes",
                localized.getString(sleepTimerTimesToTextRes.getValue(
                    jp.gr.java_conf.SenseMusicClock.Music.SleepTimerTimes.MIN_15
                ))
            )
        }
    }
}
