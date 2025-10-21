package jp.gr.java_conf.SenseMusicClock

import android.app.Service
import android.content.Intent
import android.os.IBinder

class Musicservice : Service() {



    override fun onBind(intent: Intent?): IBinder? {
        return null
    }


}