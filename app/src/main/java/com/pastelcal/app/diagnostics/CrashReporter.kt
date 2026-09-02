package com.pastelcal.app.diagnostics

import android.content.Context
import java.io.File
import java.time.Instant

object CrashReporter {
    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is PastelCalExceptionHandler) return
        Thread.setDefaultUncaughtExceptionHandler(PastelCalExceptionHandler(context.applicationContext, previous))
    }

    fun readLastCrash(context: Context): String? = crashFile(context).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        crashFile(context).delete()
    }

    private fun crashFile(context: Context): File = File(context.filesDir, "diagnostics/$FILE_NAME")

    private class PastelCalExceptionHandler(
        private val context: Context,
        private val delegate: Thread.UncaughtExceptionHandler?
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            runCatching {
                val file = crashFile(context)
                file.parentFile?.mkdirs()
                file.writeText(buildString {
                    appendLine("PastelCal local crash report")
                    appendLine("Time: ${Instant.now()}")
                    appendLine("Thread: ${thread.name}")
                    appendLine("Exception: ${throwable::class.java.name}: ${throwable.message.orEmpty()}")
                    appendLine()
                    appendLine(throwable.stackTraceToString())
                })
            }
            delegate?.uncaughtException(thread, throwable)
        }
    }
}
