package com.wpinrui.dovora

import android.app.Application
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DovoraApp : Application() {

    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    override fun onCreate() {
        super.onCreate()
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashLog(thread, throwable)
            } catch (logError: Exception) {
                Log.e("DovoraApp", "Failed to write panic file", logError)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun writeCrashLog(thread: Thread, throwable: Throwable) {
        val baseDir = getExternalFilesDir(null) ?: filesDir
        val panicDir = File(baseDir, "panic")
        if (!panicDir.exists()) {
            panicDir.mkdirs()
        }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val panicFile = File(panicDir, "panic-$timestamp.txt")

        val stackWriter = StringWriter()
        throwable.printStackTrace(PrintWriter(stackWriter))

        panicFile.bufferedWriter().use { writer ->
            writer.appendLine("Thread: ${thread.name}")
            writer.appendLine("Time: $timestamp")
            writer.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})")
            writer.appendLine()
            writer.append(stackWriter.toString())
        }

        Log.i("DovoraApp", "Crash log saved to ${panicFile.absolutePath}")
    }
}
