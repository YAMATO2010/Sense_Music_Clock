package jp.gr.java_conf.SenseMusicClock

import android.app.Application
import android.os.StrictMode
import android.util.Log

class App : Application() {
/*
    override fun onCreate() {
        super.onCreate()
        Log.d("LeakCheck", "App onCreate - enabling StrictMode")
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build()
        )
    }

 */


}