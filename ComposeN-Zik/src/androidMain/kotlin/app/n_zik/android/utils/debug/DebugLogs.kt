package app.n_zik.android.utils.debug

import java.io.File

/**
 * Pure file helpers for the debug log files.
 *
 * When debug logging is enabled (Misc settings > "Enable debug logs"), the app writes
 * `N-Zik_log.txt` and `N-Zik_crash_log.txt` inside the `logs` directory. These helpers
 * centralize the file names so the hamburger menu toggle purges exactly the same files
 * the Misc settings toggle deletes.
 */

private const val DEBUG_LOG_FILE_NAME = "N-Zik_log.txt"

private const val DEBUG_CRASH_LOG_FILE_NAME = "N-Zik_crash_log.txt"

/** The debug log files located in [logsDir] (they may not exist yet). */
fun debugLogFiles(logsDir: File): List<File> =
    listOf(
        File(logsDir, DEBUG_LOG_FILE_NAME),
        File(logsDir, DEBUG_CRASH_LOG_FILE_NAME)
    )

/**
 * Deletes the debug log files in [logsDir].
 *
 * @return the number of files actually deleted (missing files are skipped silently)
 */
fun purgeDebugLogs(logsDir: File): Int =
    debugLogFiles(logsDir).count { it.delete() }
