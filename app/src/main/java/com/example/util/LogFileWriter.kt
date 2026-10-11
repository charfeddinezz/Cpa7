package com.example.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ring-buffer file logging: every in-app log line is also appended to rotating
 * session files (3 × 1MB) so crashes/restarts never lose the post-mortem trail.
 */
object LogFileWriter {

    private const val TAG = "LogFileWriter"
    private const val DIR_NAME = "session_logs"
    private const val MAX_BYTES = 1024L * 1024L
    private const val KEEP_FILES = 3

    @Volatile
    private var logDir: File? = null

    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val dayFmt = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    fun init(context: Context) {
        try {
            val d = File(context.applicationContext.filesDir, DIR_NAME)
            if (!d.exists()) d.mkdirs()
            logDir = d
            append("info", "— session started ${dayFmt.format(Date())} —", null)
        } catch (e: Exception) {
            Log.w(TAG, "init failed: ${e.message}")
        }
    }

    @Synchronized
    fun append(level: String, message: String, taskName: String? = null) {
        try {
            val dir = logDir ?: return
            var current = File(dir, "session-0.txt")
            if (!current.exists()) {
                try { current.createNewFile() } catch (_: Exception) { return }
            }
            if (current.length() > MAX_BYTES) {
                rotate(dir)
                current = File(dir, "session-0.txt")
                try { current.createNewFile() } catch (_: Exception) { return }
            }
            val task = taskName?.takeIf { it.isNotBlank() }?.let { " [$it]" } ?: ""
            val line = "${timeFmt.format(Date())} [${level.uppercase(Locale.US)}]$task $message\n"
            current.appendText(line)
        } catch (_: Exception) {
        }
    }

    @Synchronized
    private fun rotate(dir: File) {
        try {
            File(dir, "session-${KEEP_FILES - 1}.txt").delete()
            for (i in KEEP_FILES - 2 downTo 0) {
                val src = File(dir, "session-$i.txt")
                if (src.exists()) src.renameTo(File(dir, "session-${i + 1}.txt"))
            }
        } catch (_: Exception) {
        }
    }

    fun latestFile(): File? {
        return try {
            val f = File(logDir ?: return null, "session-0.txt")
            if (f.exists()) f else null
        } catch (_: Exception) { null }
    }

    fun listFiles(): List<File> {
        return try {
            val dir = logDir ?: return emptyList()
            (0 until KEEP_FILES).mapNotNull {
                val f = File(dir, "session-$i.txt")
                if (f.exists()) f else null
            }
        } catch (_: Exception) { emptyList() }
    }
}
