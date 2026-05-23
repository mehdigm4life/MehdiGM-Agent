package com.gs.agent

import android.app.Application
import android.os.Environment
import android.os.Process
import com.gs.agent.data.repository.SettingsRepository
import com.gs.agent.data.repository.ChatRepository
import com.gs.agent.data.db.AppDatabase
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GsAgentApp : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val chatRepository by lazy { ChatRepository(database.chatDao()) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            dumpLogcat()
            Thread.getDefaultUncaughtExceptionHandler()?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Captures **only** the logcat output that belongs to this process (the app).
     * The dump is written to /storage/emulated/0/AndroidCSProjects/logcat.log.
     * The directory is created automatically if it does not exist.
     */
    private fun dumpLogcat() {
        try {
            val targetDir = File("/storage/emulated/0/AndroidCSProjects")
            if (!targetDir.exists()) targetDir.mkdirs()
            val logFile = File(targetDir, "logcat.log")
            // Get current process id – this filters the logcat to our app only.
            val pid = Process.myPid()
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "threadtime", "--pid", pid.toString()))
            val reader = InputStreamReader(process.inputStream)
            val output = reader.readText()
            val header = "\n=== LOGCAT DUMP @ ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())} (pid=$pid) ===\n"
            FileOutputStream(logFile, true).use { fos ->
                fos.write(header.toByteArray())
                fos.write(output.toByteArray())
            }
        } catch (e: Exception) {
            // Silently ignore any failure while writing the log.
        }
    }

    companion object {
        lateinit var instance: GsAgentApp
            private set
    }
}
