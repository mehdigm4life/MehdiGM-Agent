package com.gs.agent

import android.app.Application
import android.os.Environment
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
        // Set a global uncaught exception handler that dumps logcat to a file
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            dumpLogcat()
            // Re‑throw so the system can still handle the crash (shows dialog, etc.)
            Thread.getDefaultUncaughtExceptionHandler()?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Captures the current logcat output and writes it to
     * /storage/emulated/0/AndroidCSProjects/logcat.log .
     * The directory is created automatically if it does not exist.
     */
    private fun dumpLogcat() {
        try {
            val targetDir = File("/storage/emulated/0/AndroidCSProjects")
            if (!targetDir.exists()) targetDir.mkdirs()
            val logFile = File(targetDir, "logcat.log")
            // Capture logcat with timestamps for easier debugging
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "threadtime"))
            val reader = InputStreamReader(process.inputStream)
            val output = reader.readText()
            // Append a header with date/time of the dump
            val header = "\n=== LOGCAT DUMP @ ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())} ===\n"
            FileOutputStream(logFile, true).use { fos ->
                fos.write(header.toByteArray())
                fos.write(output.toByteArray())
            }
        } catch (e: Exception) {
            // If writing fails we silently ignore – we do not want another crash.
        }
    }

    companion object {
        lateinit var instance: GsAgentApp
            private set
    }
}
