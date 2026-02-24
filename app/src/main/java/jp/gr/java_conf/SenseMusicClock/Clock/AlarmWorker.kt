package jp.gr.java_conf.SenseMusicClock.Clock

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * AlarmWorker stub: alarms are now handled externally; worker is kept for compatibility but performs no playback
 */
class AlarmWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        Log.d("AlarmWorker", "stub doWork invoked — alarms are disabled in-app")
        return Result.success()
    }
}
