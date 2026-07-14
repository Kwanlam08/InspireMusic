package com.inspiremusic.data

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticLogger {
    private const val MAX_BYTES = 512 * 1024L
    private lateinit var file: File
    private val lock = Any()

    fun initialize(context: Context) {
        if (::file.isInitialized) return
        file = File(context.filesDir, "diagnostics/inspire-music.log").also { it.parentFile?.mkdirs() }
        log("app", "start sdk=${Build.VERSION.SDK_INT} device=${Build.MANUFACTURER}/${Build.MODEL}")
    }

    fun log(tag: String, message: String, error: Throwable? = null) {
        if (!::file.isInitialized) return
        synchronized(lock) {
            runCatching {
                if (file.length() > MAX_BYTES) {
                    val tail = file.readText(Charsets.UTF_8).takeLast((MAX_BYTES / 2).toInt())
                    file.writeText(tail, Charsets.UTF_8)
                }
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
                val suffix = error?.let { " | ${it.javaClass.simpleName}: ${it.message}\n${it.stackTraceToString()}" }.orEmpty()
                file.appendText("$time [$tag] $message$suffix\n", Charsets.UTF_8)
            }
        }
    }

    fun exportText(): String = synchronized(lock) {
        if (!::file.isInitialized || !file.exists()) "No diagnostics recorded." else file.readText(Charsets.UTF_8)
    }
}
