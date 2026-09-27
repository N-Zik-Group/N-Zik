package app.n_zik.android.utils.debug

import java.io.File

/**
 * Pure file helpers for the app's log files.
 *
 * The crash log (`N-Zik_crash_log.txt`) is written by [CaptureCrash] REGARDLESS of the
 * debug switch (the crash handler is always installed at startup), while the debug log
 * (`N-Zik_log.txt`) is only written while "Enable debug logs" is on. The two files are
 * therefore managed independently: toggling debug off purges only the debug log, the
 * crash log survives (the Maintenance sheet and its log dialogs read it unconditionally).
 */

private const val DEBUG_LOG_FILE_NAME = "N-Zik_log.txt"

private const val DEBUG_CRASH_LOG_FILE_NAME = "N-Zik_crash_log.txt"

/** The app log files located in [logsDir] — debug log + crash log (they may not exist yet). */
fun debugLogFiles(logsDir: File): List<File> =
    listOf(
        File(logsDir, DEBUG_LOG_FILE_NAME),
        File(logsDir, DEBUG_CRASH_LOG_FILE_NAME)
    )

/**
 * Deletes the DEBUG log in [logsDir] only — the crash log is deliberately left in place
 * (crash capture is independent of the debug switch, and the Maintenance sheet + log
 * dialogs keep reading it while debug is off).
 *
 * @return the number of files actually deleted (missing file = 0, skipped silently)
 */
fun purgeDebugLogs(logsDir: File): Int =
    debugLogFiles(logsDir).count { it.name == DEBUG_LOG_FILE_NAME && it.delete() }
