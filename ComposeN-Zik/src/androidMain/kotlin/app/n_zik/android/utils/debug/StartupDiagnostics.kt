package app.n_zik.android.utils.debug

import java.io.File
import android.content.Context
import android.os.Build
import timber.log.Timber
import app.n_zik.android.BuildConfig
import app.n_zik.android.components.maintenance.countCrashBlocks
import app.n_zik.android.core.profiles.profileDataPrefs
import app.n_zik.android.utils.CaptureCrash
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.logDebugEnabledKey
import app.n_zik.android.utils.logging.FileLoggingTree
import app.n_zik.android.utils.logging.flushThenDelegate

internal fun Context.installStartupDiagnostics() {
    val diagnosticsOwner = getActiveProfile(this)
    val diagnosticPrefs = profileDataPrefs(this, diagnosticsOwner)
    val logEnabled = diagnosticPrefs.getBoolean(logDebugEnabledKey, false)

    val dir = profileDiagnosticsDir(this, diagnosticsOwner).apply { mkdirs() }

    val crashCapture = CaptureCrash(dir.absolutePath, this)
    val guardedCrashCapture = Thread.UncaughtExceptionHandler { thread, error ->
        synchronized(diagnosticFileMutationLock) { crashCapture.uncaughtException(thread, error) }
    }
    Thread.setDefaultUncaughtExceptionHandler(guardedCrashCapture)

    // Retention must run before the diagnostics UI reads the crash log.
    runCatching {
        val crashLogFile = crashLogFile(dir)
        val currentCrashCount =
            if (crashLogFile.exists()) countCrashBlocks(crashLogFile.readText()) else 0
        val decision = crashLogWipeDecision(
            currentCrashCount = currentCrashCount,
            lastSeenCrashCount = diagnosticPrefs.getInt(CRASH_LAST_SEEN_COUNT_KEY, 0),
            cleanBootStreak = diagnosticPrefs.getInt(CRASH_CLEAN_BOOT_STREAK_KEY, 0),
        )
        if (decision.wipe) crashLogFile.delete()
        diagnosticPrefs.edit()
            .putInt(CRASH_CLEAN_BOOT_STREAK_KEY, decision.cleanBootStreak)
            .putInt(CRASH_LAST_SEEN_COUNT_KEY, decision.lastSeenCrashCount)
            .apply()
    }.onFailure {
        Timber.tag("MainApplication").w(it, "Crash log auto-wipe evaluation failed")
    }

    if (logEnabled) {
        val fileLoggingTree = FileLoggingTree(File(dir, "N-Zik_log.txt"))
        Timber.plant(fileLoggingTree)
        // Allow queued diagnostics to reach disk before crash capture ends the process.
        Thread.setDefaultUncaughtExceptionHandler(flushThenDelegate(fileLoggingTree, 2000, guardedCrashCapture))
        Timber.tag("MainApplication").d("Log enabled at ${dir.absolutePath}")
    } else {
        Timber.uprootAll()
        Timber.plant(Timber.DebugTree())
    }

    Timber.tag("Startup").i("=".repeat(50))
    Timber.tag("Startup").i("N-Zik v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) [${BuildConfig.APPLICATION_ID}]")
    Timber.tag("Startup").i("Manufacturer: ${Build.MANUFACTURER}")
    Timber.tag("Startup").i("Device: ${Build.MODEL} (${Build.DEVICE})")
    Timber.tag("Startup").i("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
    Timber.tag("Startup").i("Board: ${Build.BOARD} | Hardware: ${Build.HARDWARE}")
    Timber.tag("Startup").i("Build: ${Build.FINGERPRINT}")
    Timber.tag("Startup").i("=".repeat(50))
}
