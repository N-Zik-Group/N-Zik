package app.it.fast4x.rimusic.utils

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.time.LocalDateTime
import kotlin.system.exitProcess
import timber.log.Timber
import android.os.Process

class CaptureCrash (private val LOG_PATH: String, private val context: Context? = null) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        saveCrashLog(throwable)
        Process.killProcess(Process.myPid())
        exitProcess(1)
    }

    private fun saveCrashLog(throwable: Throwable) {
        try {
            val logFile = File(LOG_PATH, "N-Zik_crash_log.txt")
            if (!logFile.exists()) {
                logFile.createNewFile()
            }

            FileWriter(logFile, true).use { writer ->
                val pw = PrintWriter(writer)
                pw.println("${LocalDateTime.now()}:")
                pw.println()
                pw.println(buildDeviceHeader())
                pw.println("Stacktrace:")
                pw.println("=".repeat(50))
                pw.println()
                printFullStackTrace(throwable, pw)
            }
        } catch (e: Exception) {
            Timber.tag("CaptureCrash").e(e, "Failed to save crash log")
        }
    }

    private fun buildDeviceHeader(): String = buildString {
        appendLine("N-Zik Crash Report")
        appendLine("=".repeat(50))
        appendLine()
        if (context != null) {
            appendLine("Package: ${context.applicationContext.packageName}")
        }
        appendLine("Manufacturer: ${Build.MANUFACTURER}")
        appendLine("Device: ${Build.MODEL}")
        appendLine("Device name: ${Build.DEVICE}")
        appendLine("Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("Board: ${Build.BOARD}")
        appendLine("Bootloader: ${Build.BOOTLOADER}")
        appendLine("Fingerprint: ${Build.FINGERPRINT}")
        appendLine("Hardware: ${Build.HARDWARE}")
        appendLine()
    }

    private fun printFullStackTrace(throwable: Throwable, printWriter: PrintWriter) {
        printWriter.println(throwable.toString())
        throwable.stackTrace.forEach { element ->
            printWriter.print("\t $element \n")
        }
        val cause = throwable.cause
        if (cause != null) {
            printWriter.print("Caused by:\t")
            printFullStackTrace(cause, printWriter)
        }
        printWriter.print("\n")
    }
}


