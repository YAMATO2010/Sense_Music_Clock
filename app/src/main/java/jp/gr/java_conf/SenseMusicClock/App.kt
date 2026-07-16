package jp.gr.java_conf.SenseMusicClock

import android.app.Application
import android.os.StrictMode
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class App : Application() {

override fun onCreate() {
    super.onCreate()

    Log.d("CrashTest", "Application onCreate")

    val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        Log.e("CrashTest", "Uncaught exception!", throwable)

        try {
            saveCrashLog(throwable)
            Log.d("CrashTest", "saveCrashLog finished")
        } catch (e: Exception) {
            Log.e("CrashTest", "saveCrashLog failed", e)
        }

        defaultHandler?.uncaughtException(thread, throwable)
    }
}

    private fun saveCrashLog(throwable: Throwable) {
        // 例外のスタックトレースを文字列に変換
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        val stackTraceString = sw.toString()

        // 日時とエラー内容を整形
        val timeStamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        val logText = """
            === CRASH LOG ===
            Date: $timeStamp
            Device: ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})
            Exception: ${throwable.localizedMessage}
            
            $stackTraceString
            ==================
            
        """.trimIndent()

        // 保存先：アプリ専用のストレージ（Context.filesDir / `files/crash_logs.txt`）
        // 追記モード（append = true）で開くので、普段使いで何回クラッシュしても後ろに溜まっていきます
        val logFile = File(filesDir, "crash_logs$timeStamp.txt")

        FileOutputStream(logFile, true).use { stream ->
            stream.write(logText.toByteArray())
        }
    }



}